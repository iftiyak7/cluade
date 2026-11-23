package com.hayat.core.security

import com.hayat.core.domain.security.PinCheck
import com.hayat.core.testing.FakeTimeProvider
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import javax.crypto.AEADBadTagException

class SecurityTest {
    /** Test double: XOR "wrapping" keeps the stored value different from the raw hash. */
    private class FakeWrapper : SecretWrapper {
        var broken = false
        override fun wrap(plain: ByteArray) = plain.map { (it.toInt() xor 0x5A).toByte() }.toByteArray()
        override fun unwrap(wrapped: ByteArray): ByteArray { check(!broken); return wrap(wrapped) }
    }
    private class MemoryStore : PinRecordStore {
        var rec: PinRecord? = null
        override fun read() = rec
        override fun write(record: PinRecord?) { rec = record }
    }

    private val time = FakeTimeProvider()
    private val store = MemoryStore()
    private val wrapper = FakeWrapper()
    private val core = PinLockCore(store, wrapper, time, iterations = 1_000)

    @Test fun `pin verification, wrong attempts and lockout`() = runTest {
        assertEquals(PinCheck.NotSet, core.verify("1234".toCharArray()))
        core.setPin("2580".toCharArray())
        assertTrue(core.hasPin())
        assertFalse("raw hash is never stored", store.rec!!.wrappedHash.contentEquals(Crypto.pbkdf2("2580".toCharArray(), store.rec!!.salt, 1_000)))
        assertEquals(PinCheck.Correct, core.verify("2580".toCharArray()))
        repeat(4) { assertTrue(core.verify("0000".toCharArray()) is PinCheck.Wrong) }
        val locked = core.verify("0000".toCharArray())
        assertTrue(locked is PinCheck.LockedOut)
        // Even the correct PIN is refused during lockout.
        assertTrue(core.verify("2580".toCharArray()) is PinCheck.LockedOut)
        time.advance(Duration.ofSeconds(31))
        assertEquals(PinCheck.Correct, core.verify("2580".toCharArray()))
        assertEquals(0, store.rec!!.failedAttempts)
    }

    @Test fun `lost device key never unlocks`() = runTest {
        core.setPin("2580".toCharArray())
        wrapper.broken = true
        assertTrue(core.verify("2580".toCharArray()) is PinCheck.Wrong)
    }

    @Test fun `aes gcm round trip and tamper detection`() {
        val key = Crypto.randomBytes(32); val iv = Crypto.randomBytes(12)
        val aad = "header".toByteArray()
        val ct = Crypto.aesGcmEncrypt(key, iv, "সালাম".toByteArray(), aad)
        assertArrayEquals("সালাম".toByteArray(), Crypto.aesGcmDecrypt(key, iv, ct, aad))
        ct[0] = (ct[0].toInt() xor 1).toByte()
        try { Crypto.aesGcmDecrypt(key, iv, ct, aad); throw AssertionError("tamper not detected") } catch (_: AEADBadTagException) {}
    }

    @Test fun `lock controller times out in background`() {
        val c = AppLockController(time)
        c.initialize(lockEnabled = true); assertTrue(c.locked.value)
        c.unlock(); assertFalse(c.locked.value)
        c.onBackground(); time.advance(Duration.ofSeconds(30)); c.onForeground(true, 60); assertFalse(c.locked.value)
        c.onBackground(); time.advance(Duration.ofSeconds(61)); c.onForeground(true, 60); assertTrue(c.locked.value)
    }
}
