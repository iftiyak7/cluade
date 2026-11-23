package com.hayat.core.security

import com.hayat.core.domain.security.AppLockManager
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class SecurityModule {
    @Binds abstract fun wrapper(impl: KeystoreSecretWrapper): SecretWrapper
    @Binds abstract fun appLock(impl: PinLockManager): AppLockManager
}
