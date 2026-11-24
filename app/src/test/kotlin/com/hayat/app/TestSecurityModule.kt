package com.hayat.app

import com.hayat.core.security.SecretWrapper
import com.hayat.core.security.SecurityModule
import com.hayat.core.security.PinLockManager
import com.hayat.core.domain.security.AppLockManager
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn

/** AndroidKeyStore is not available under Robolectric; tests wrap secrets with a reversible in-memory transform. */
@Module
@TestInstallIn(components = [SingletonComponent::class], replaces = [SecurityModule::class])
abstract class TestSecurityModule {
    @Binds abstract fun appLock(impl: PinLockManager): AppLockManager

    companion object {
        @Provides fun wrapper(): SecretWrapper = object : SecretWrapper {
            override fun wrap(plain: ByteArray) = plain.map { (it.toInt() xor 0x33).toByte() }.toByteArray()
            override fun unwrap(wrapped: ByteArray) = wrap(wrapped)
        }
    }
}
