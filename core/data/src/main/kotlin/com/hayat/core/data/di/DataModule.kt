package com.hayat.core.data.di

import com.hayat.core.common.HayatDispatchers
import com.hayat.core.common.IdGenerator
import com.hayat.core.common.SystemTimeProvider
import com.hayat.core.common.TimeProvider
import com.hayat.core.common.UuidIdGenerator
import com.hayat.core.data.repository.*
import com.hayat.core.domain.health.MealReminderHandler
import com.hayat.core.domain.health.MedicineReminderHandler
import com.hayat.core.domain.health.SleepReminderContributor
import com.hayat.core.domain.health.WaterReminderContributor
import com.hayat.core.domain.health.WaterReminderHandler
import com.hayat.core.domain.productivity.EventReminderContributor
import com.hayat.core.domain.productivity.HabitReminderHandler
import com.hayat.core.domain.reminder.ReminderSyncContributor
import com.hayat.core.domain.reminder.ReminderTargetHandler
import com.hayat.core.domain.repository.*
import com.hayat.core.domain.routine.RoutineReminderHandler
import com.hayat.core.domain.task.TaskReminderHandler
import com.hayat.core.domain.worship.PrayerReminderContributor
import com.hayat.core.domain.worship.PrayerReminderHandler
import com.hayat.core.model.ReminderTargetType
import dagger.Binds
import dagger.MapKey
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoMap
import dagger.multibindings.IntoSet
import javax.inject.Singleton

@MapKey
annotation class TargetTypeKey(val value: ReminderTargetType)

@Module
@InstallIn(SingletonComponent::class)
abstract class DataModule {
    @Binds abstract fun time(impl: SystemTimeProvider): TimeProvider
    @Binds abstract fun ids(impl: UuidIdGenerator): IdGenerator

    @Binds @Singleton abstract fun categories(impl: RoomCategoryRepository): CategoryRepository
    @Binds @Singleton abstract fun announcements(impl: com.hayat.core.data.repository.RoomAnnouncementRepository): com.hayat.core.domain.announcement.AnnouncementRepository
    @Binds @Singleton abstract fun tags(impl: RoomTagRepository): TagRepository
    @Binds @Singleton abstract fun tasks(impl: RoomTaskRepository): TaskRepository
    @Binds @Singleton abstract fun routines(impl: RoomRoutineRepository): RoutineRepository
    @Binds @Singleton abstract fun templates(impl: RoomTemplateRepository): TemplateRepository
    @Binds @Singleton abstract fun search(impl: RoomSearchRepository): SearchRepository
    @Binds @Singleton abstract fun maintenance(impl: RoomDataMaintenanceRepository): DataMaintenanceRepository
    @Binds @Singleton abstract fun reminders(impl: RoomReminderRepository): ReminderRepository
    @Binds @Singleton abstract fun occurrences(impl: RoomOccurrenceRepository): OccurrenceRepository
    @Binds @Singleton abstract fun deliveryLog(impl: RoomDeliveryLogRepository): DeliveryLogRepository
    @Binds @Singleton abstract fun water(impl: RoomWaterRepository): WaterRepository
    @Binds @Singleton abstract fun medicine(impl: RoomMedicineRepository): MedicineRepository
    @Binds @Singleton abstract fun meals(impl: RoomMealRepository): MealRepository
    @Binds @Singleton abstract fun sleep(impl: RoomSleepRepository): SleepRepository
    @Binds @Singleton abstract fun fitness(impl: RoomFitnessRepository): FitnessRepository
    @Binds @Singleton abstract fun ibadah(impl: RoomIbadahRepository): IbadahRepository
    @Binds @Singleton abstract fun habits(impl: RoomHabitRepository): HabitRepository
    @Binds @Singleton abstract fun study(impl: RoomStudyRepository): StudyRepository
    @Binds @Singleton abstract fun finance(impl: RoomFinanceRepository): FinanceRepository
    @Binds @Singleton abstract fun events(impl: RoomEventRepository): EventRepository
    @Binds @Singleton abstract fun notes(impl: RoomNoteRepository): NoteRepository

    // Reminder action handlers per target type.
    @Binds @IntoMap @TargetTypeKey(ReminderTargetType.TASK) abstract fun taskHandler(h: TaskReminderHandler): ReminderTargetHandler
    @Binds @IntoMap @TargetTypeKey(ReminderTargetType.ROUTINE_ACTIVITY) abstract fun routineHandler(h: RoutineReminderHandler): ReminderTargetHandler
    @Binds @IntoMap @TargetTypeKey(ReminderTargetType.MEDICINE) abstract fun medicineHandler(h: MedicineReminderHandler): ReminderTargetHandler
    @Binds @IntoMap @TargetTypeKey(ReminderTargetType.WATER) abstract fun waterHandler(h: WaterReminderHandler): ReminderTargetHandler
    @Binds @IntoMap @TargetTypeKey(ReminderTargetType.MEAL) abstract fun mealHandler(h: MealReminderHandler): ReminderTargetHandler
    @Binds @IntoMap @TargetTypeKey(ReminderTargetType.HABIT) abstract fun habitHandler(h: HabitReminderHandler): ReminderTargetHandler
    @Binds @IntoMap @TargetTypeKey(ReminderTargetType.PRAYER) abstract fun prayerHandler(h: PrayerReminderHandler): ReminderTargetHandler

    // Computed reminders refreshed on every reconciliation.
    @Binds @IntoSet abstract fun waterContributor(c: WaterReminderContributor): ReminderSyncContributor
    @Binds @IntoSet abstract fun sleepContributor(c: SleepReminderContributor): ReminderSyncContributor
    @Binds @IntoSet abstract fun prayerContributor(c: PrayerReminderContributor): ReminderSyncContributor
    @Binds @IntoSet abstract fun eventContributor(c: EventReminderContributor): ReminderSyncContributor

    companion object {
        @Provides @Singleton fun dispatchers() = HayatDispatchers()
    }
}
