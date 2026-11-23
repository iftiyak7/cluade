package com.hayat.app

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.work.Configuration
import com.hayat.core.common.ApplicationScope
import com.hayat.core.domain.repository.PreferencesRepository
import com.hayat.core.security.AppLockController
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class HayatApplication : Application(), Configuration.Provider {
    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var initializer: AppInitializer
    @Inject lateinit var lockController: AppLockController
    @Inject lateinit var preferences: PreferencesRepository
    @Inject @ApplicationScope lateinit var scope: CoroutineScope

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).setMinimumLoggingLevel(android.util.Log.ERROR).build()

    override fun onCreate() {
        super.onCreate()
        scope.launch { initializer.initialize() }
        val prefs = preferences.preferences.stateIn(scope, SharingStarted.Eagerly, null)
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStop(owner: LifecycleOwner) = lockController.onBackground()
            override fun onStart(owner: LifecycleOwner) {
                val lock = prefs.value?.appLock ?: return
                lockController.onForeground(lock.enabled, lock.timeoutSeconds)
            }
        })
    }
}
