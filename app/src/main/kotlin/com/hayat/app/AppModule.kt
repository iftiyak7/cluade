package com.hayat.app

import com.hayat.core.common.ApplicationScope
import com.hayat.core.domain.announcement.AppConfig
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides @Singleton @ApplicationScope
    fun applicationScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Provides @Named("appVersion")
    fun appVersion(): String = BuildConfig.VERSION_NAME

    @Provides @Singleton
    fun appConfig(): AppConfig = object : AppConfig {
        override val feedUrl: String = BuildConfig.FEED_URL.ifBlank { BuildConfig.SERVER_URL.takeIf { it.isNotBlank() }?.let { "$it/api/v1/announcements" }.orEmpty() }
        override val defaultServerUrl: String = BuildConfig.SERVER_URL
        override val versionCode: Int = BuildConfig.VERSION_CODE
        // The device language, independent of Hayat's per-app language override.
        override val systemLanguage: String get() = android.content.res.Resources.getSystem().configuration.locales.get(0)?.language.orEmpty()
    }
}
