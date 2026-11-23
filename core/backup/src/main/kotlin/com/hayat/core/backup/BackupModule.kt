package com.hayat.core.backup

import com.hayat.core.domain.security.BackupService
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class BackupModule {
    @Binds abstract fun backup(impl: HayatBackupService): BackupService
}
