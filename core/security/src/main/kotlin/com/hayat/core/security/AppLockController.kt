package com.hayat.core.security

import com.hayat.core.common.TimeProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * In-memory session lock state. The app starts locked when app lock is enabled; it re-locks
 * after it has been in the background for longer than the configured timeout.
 */
@Singleton
class AppLockController @Inject constructor(private val time: TimeProvider) {
    private val _locked = MutableStateFlow(true)
    val locked: StateFlow<Boolean> = _locked.asStateFlow()
    private var backgroundedAt: Instant? = null

    fun unlock() { _locked.value = false; backgroundedAt = null }
    fun lockNow() { _locked.value = true }

    fun onBackground() { backgroundedAt = time.now() }

    fun onForeground(lockEnabled: Boolean, timeoutSeconds: Int) {
        if (!lockEnabled) { _locked.value = false; return }
        val since = backgroundedAt ?: return
        if (Duration.between(since, time.now()).seconds >= timeoutSeconds) _locked.value = true
        backgroundedAt = null
    }

    /** Called once at startup with the persisted setting. */
    fun initialize(lockEnabled: Boolean) { _locked.value = lockEnabled }
}
