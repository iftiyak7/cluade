package com.hayat.core.database

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): HayatDatabase =
        Room.databaseBuilder(context, HayatDatabase::class.java, HayatDatabase.NAME)
            .addMigrations(*Migrations.ALL)
            .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
            .build()

    @Provides fun categoryDao(db: HayatDatabase) = db.categoryDao()
    @Provides fun tagDao(db: HayatDatabase) = db.tagDao()
    @Provides fun taskDao(db: HayatDatabase) = db.taskDao()
    @Provides fun routineDao(db: HayatDatabase) = db.routineDao()
    @Provides fun templateDao(db: HayatDatabase) = db.templateDao()
    @Provides fun reminderDao(db: HayatDatabase) = db.reminderDao()
    @Provides fun occurrenceDao(db: HayatDatabase) = db.occurrenceDao()
    @Provides fun deliveryLogDao(db: HayatDatabase) = db.deliveryLogDao()
    @Provides fun waterDao(db: HayatDatabase) = db.waterDao()
    @Provides fun medicineDao(db: HayatDatabase) = db.medicineDao()
    @Provides fun mealDao(db: HayatDatabase) = db.mealDao()
    @Provides fun sleepDao(db: HayatDatabase) = db.sleepDao()
    @Provides fun fitnessDao(db: HayatDatabase) = db.fitnessDao()
    @Provides fun ibadahDao(db: HayatDatabase) = db.ibadahDao()
    @Provides fun habitDao(db: HayatDatabase) = db.habitDao()
    @Provides fun studyDao(db: HayatDatabase) = db.studyDao()
    @Provides fun financeDao(db: HayatDatabase) = db.financeDao()
    @Provides fun eventDao(db: HayatDatabase) = db.eventDao()
    @Provides fun noteDao(db: HayatDatabase) = db.noteDao()
    @Provides fun searchDao(db: HayatDatabase) = db.searchDao()
    @Provides fun backupDao(db: HayatDatabase) = db.backupDao()
    @Provides fun maintenanceDao(db: HayatDatabase) = db.maintenanceDao()
}
