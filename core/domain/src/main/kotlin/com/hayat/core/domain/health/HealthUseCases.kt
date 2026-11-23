package com.hayat.core.domain.health

import com.hayat.core.common.AppResult
import com.hayat.core.common.IdGenerator
import com.hayat.core.common.Limits
import com.hayat.core.common.TimeProvider
import com.hayat.core.common.ValidationError
import com.hayat.core.common.ValidationReason
import com.hayat.core.common.appCatching
import com.hayat.core.common.toFailureOrNull
import com.hayat.core.common.validate
import com.hayat.core.domain.reminder.ReminderLinker
import com.hayat.core.domain.reminder.ReminderScheduler
import com.hayat.core.domain.reminder.ReminderSyncContributor
import com.hayat.core.domain.reminder.ReminderTargetHandler
import com.hayat.core.domain.repository.FitnessRepository
import com.hayat.core.domain.repository.MealRepository
import com.hayat.core.domain.repository.MedicineRepository
import com.hayat.core.domain.repository.PreferencesRepository
import com.hayat.core.domain.repository.WaterRepository
import com.hayat.core.model.DateRule
import com.hayat.core.model.DoseStatus
import com.hayat.core.model.LinkedReminderSpec
import com.hayat.core.model.MealLog
import com.hayat.core.model.MealPlan
import com.hayat.core.model.MealStatus
import com.hayat.core.model.Medicine
import com.hayat.core.model.MedicineDose
import com.hayat.core.model.MedicineDoseLog
import com.hayat.core.model.OccurrenceStatus
import com.hayat.core.model.Priority
import com.hayat.core.model.ReminderCategory
import com.hayat.core.model.ReminderOccurrence
import com.hayat.core.model.ReminderStyle
import com.hayat.core.model.ReminderTargetType
import com.hayat.core.model.Schedule
import com.hayat.core.model.SleepEntry
import com.hayat.core.model.TimeRule
import com.hayat.core.model.WaterDay
import com.hayat.core.model.WaterGoal
import com.hayat.core.model.WaterLog
import com.hayat.core.model.WeekdaySet
import com.hayat.core.model.WorkoutPlan
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/** Rule for weekday-limited daily schedules: all days → Daily, otherwise Weekly. */
fun WeekdaySet.toDateRule(): DateRule = if (this == WeekdaySet.ALL || isEmpty) DateRule.Daily() else DateRule.Weekly(this)

// ===================================================================== water

object WaterCalculator {
    const val DEFAULT_GOAL_ML = 2000
    const val MAX_SINGLE_LOG_ML = 3000

    /** Goal effective on [date]: the latest goal whose effectiveFrom <= date. */
    fun goalFor(date: LocalDate, goals: List<WaterGoal>): Int =
        goals.filter { !it.effectiveFrom.isAfter(date) }.maxByOrNull { it.effectiveFrom }?.dailyMl
            ?: goals.minByOrNull { it.effectiveFrom }?.dailyMl
            ?: DEFAULT_GOAL_ML

    fun days(from: LocalDate, to: LocalDate, logs: List<WaterLog>, goals: List<WaterGoal>): List<WaterDay> {
        val totals = logs.groupBy { it.date }.mapValues { (_, l) -> l.sumOf { it.amountMl } }
        return generateSequence(from) { it.plusDays(1) }.takeWhile { !it.isAfter(to) }
            .map { WaterDay(it, totals[it] ?: 0, goalFor(it, goals)) }.toList()
    }

    fun validateAmount(ml: Int): List<ValidationError> = validate { range(ml, "amount", 1.0, MAX_SINGLE_LOG_ML.toDouble(), required = true) }
}

class WaterUseCases @Inject constructor(
    private val water: WaterRepository,
    private val time: TimeProvider,
    private val ids: IdGenerator,
) {
    suspend fun add(amountMl: Int, at: Instant = time.now(), note: String = ""): AppResult<WaterLog> {
        WaterCalculator.validateAmount(amountMl).toFailureOrNull()?.let { return it }
        val log = WaterLog(ids.newId(), amountMl, at, at.atZone(time.zone()).toLocalDate(), note.take(Limits.SHORT_TEXT_MAX))
        return appCatching { water.add(log); log }
    }

    suspend fun update(log: WaterLog): AppResult<Unit> {
        WaterCalculator.validateAmount(log.amountMl).toFailureOrNull()?.let { return it }
        return appCatching { water.update(log) }
    }

    suspend fun delete(id: String) = appCatching { water.delete(id) }

    suspend fun setGoal(ml: Int): AppResult<Unit> {
        validate { range(ml, "goal", 250.0, 10_000.0, required = true) }.toFailureOrNull()?.let { return it }
        return appCatching { water.setGoal(ml, time.today()) }
    }
}

/** Water reminder derived from preferences (interval within waking hours). */
class WaterReminderContributor @Inject constructor(
    private val prefs: PreferencesRepository,
    private val linker: ReminderLinker,
    private val time: TimeProvider,
) : ReminderSyncContributor {
    override suspend fun sync(now: Instant) {
        val p = prefs.current()
        val enabled = p.water.reminderEnabled && ReminderCategory.WATER in p.enabledReminderCategories
        if (!enabled) { linker.remove(ReminderTargetType.WATER, TARGET_ID); return }
        linker.upsert(
            LinkedReminderSpec(
                targetType = ReminderTargetType.WATER,
                targetId = TARGET_ID,
                title = "water",
                category = ReminderCategory.WATER,
                priority = Priority.LOW,
                schedules = listOf(Schedule(DateRule.Daily(), TimeRule.Every(p.water.reminderIntervalMinutes.coerceIn(15, 480), p.wakeTime, p.sleepTime), START)),
            ),
        )
    }
    companion object {
        const val TARGET_ID = "water"
        private val START = LocalDate.of(2020, 1, 1)
    }
}

/** "Done" on a water reminder logs one default glass. */
class WaterReminderHandler @Inject constructor(private val prefs: PreferencesRepository, private val water: WaterUseCases) : ReminderTargetHandler {
    override val targetType = ReminderTargetType.WATER
    override suspend fun onDone(targetId: String, occurrence: ReminderOccurrence) {
        water.add(prefs.current().water.defaultGlassMl)
    }
}

// ===================================================================== medicine

object MedicineValidator {
    fun validate(m: Medicine): List<ValidationError> = validate {
        text(m.name, "name", maxLength = Limits.TITLE_MAX)
        text(m.dose, "dose", required = false, maxLength = 40)
        text(m.unit, "unit", required = false, maxLength = 40)
        text(m.notes, "notes", required = false, maxLength = Limits.DESCRIPTION_MAX)
        range(m.stock, "stock", 0.0, 100_000.0)
        range(m.refillThreshold, "refillThreshold", 0.0, 100_000.0)
        m.endDate?.let { e -> require(!e.isBefore(m.startDate), "endDate", ValidationReason.END_BEFORE_START) }
        require(m.schedules.map { it.time }.distinct().size == m.schedules.size, "schedule", ValidationReason.DUPLICATE)
        m.schedules.forEach {
            require(!it.weekdays.isEmpty, "weekdays", ValidationReason.EMPTY_SELECTION)
            range(it.quantity, "quantity", 0.0, 1000.0)
        }
    }
}

object MedicinePlanner {
    /** Dose slots on [date] for active medicines, in time order, joined with logs. */
    fun dosesFor(date: LocalDate, medicines: List<Medicine>, logs: List<MedicineDoseLog>): List<MedicineDose> {
        val byKey = logs.associateBy { Triple(it.medicineId, it.scheduledDate, it.scheduledTime) }
        return medicines.filter { it.isActiveOn(date) }
            .flatMap { m -> m.schedules.filter { date.dayOfWeek in it.weekdays }.map { s -> MedicineDose(m, s, date, byKey[Triple(m.id, date, s.time)]) } }
            .sortedWith(compareBy<MedicineDose> { it.schedule.time }.thenBy { it.medicine.name })
    }

    /** Logged-dose share over a period: taken / (all scheduled slots up to now). */
    fun adherence(doses: List<MedicineDose>): Float {
        if (doses.isEmpty()) return 0f
        return doses.count { it.log?.status == DoseStatus.TAKEN }.toFloat() / doses.size
    }
}

class MedicineUseCases @Inject constructor(
    private val medicines: MedicineRepository,
    private val linker: ReminderLinker,
    private val scheduler: ReminderScheduler,
    private val time: TimeProvider,
) {
    suspend fun save(medicine: Medicine): AppResult<Medicine> {
        val clean = medicine.copy(name = medicine.name.trim(), dose = medicine.dose.trim(), unit = medicine.unit.trim(), updatedAt = time.now())
        MedicineValidator.validate(clean).toFailureOrNull()?.let { return it }
        return appCatching {
            medicines.save(clean)
            syncReminder(linker, clean)
            clean
        }
    }

    suspend fun setArchived(id: String, archived: Boolean) = appCatching {
        medicines.setArchived(id, archived, time.now())
        medicines.getMedicine(id)?.let { syncReminder(linker, it) }
    }

    suspend fun delete(id: String) = appCatching {
        linker.remove(ReminderTargetType.MEDICINE, id)
        linker.remove(ReminderTargetType.MEDICINE_REFILL, id)
        medicines.softDelete(id, time.now())
    }

    /** In-app dose logging. Completes the matching reminder occurrence so it is not delivered again. */
    suspend fun setDoseStatus(dose: MedicineDose, status: DoseStatus?, note: String = ""): AppResult<Unit> = appCatching {
        val updated = medicines.setDoseStatus(dose.medicine.id, dose.schedule.id, dose.date, dose.schedule.time, status, note, time.now())
        when (status) {
            DoseStatus.TAKEN -> scheduler.completeForTarget(ReminderTargetType.MEDICINE, dose.medicine.id, dose.date, dose.schedule.time)
            DoseStatus.SKIPPED -> scheduler.completeForTarget(ReminderTargetType.MEDICINE, dose.medicine.id, dose.date, dose.schedule.time, OccurrenceStatus.SKIPPED)
            else -> Unit
        }
        updated?.let { syncRefill(linker, it, time) }
    }

    companion object {
        suspend fun syncReminder(linker: ReminderLinker, m: Medicine) {
            if (!m.reminderEnabled || m.isArchived || m.schedules.isEmpty()) {
                linker.remove(ReminderTargetType.MEDICINE, m.id)
                return
            }
            linker.upsert(
                LinkedReminderSpec(
                    targetType = ReminderTargetType.MEDICINE,
                    targetId = m.id,
                    title = m.name,
                    description = listOf(m.dose, m.unit).filter { it.isNotBlank() }.joinToString(" "),
                    category = ReminderCategory.MEDICINE,
                    priority = Priority.HIGH,
                    requiresCompletion = true,
                    schedules = m.schedules.groupBy { it.weekdays }.map { (days, list) ->
                        Schedule(days.toDateRule(), TimeRule.At(list.map { it.time }), m.startDate, m.endDate)
                    },
                ),
            )
        }

        /** Creates a one-time refill reminder (next 10:00) when stock reaches the threshold; removes it otherwise. */
        suspend fun syncRefill(linker: ReminderLinker, m: Medicine, time: TimeProvider) {
            if (!m.needsRefill || m.isArchived) {
                linker.remove(ReminderTargetType.MEDICINE_REFILL, m.id)
                return
            }
            val now = time.zonedNow()
            val tenToday = now.toLocalDate().atTime(10, 0)
            val at = if (now.toLocalDateTime().isBefore(tenToday)) tenToday else now.toLocalDateTime().plusMinutes(1).withSecond(0).withNano(0)
            linker.upsert(
                LinkedReminderSpec(
                    targetType = ReminderTargetType.MEDICINE_REFILL,
                    targetId = m.id,
                    title = m.name,
                    description = "refill",
                    category = ReminderCategory.MEDICINE,
                    schedules = listOf(Schedule(DateRule.Once, TimeRule.At(at.toLocalTime()), at.toLocalDate())),
                ),
            )
        }
    }
}

/** Notification "Done" = the user confirms the dose was taken. Dismissing is not handled here. */
class MedicineReminderHandler @Inject constructor(
    private val medicines: MedicineRepository,
    private val linker: ReminderLinker,
    private val time: TimeProvider,
) : ReminderTargetHandler {
    override val targetType = ReminderTargetType.MEDICINE
    override suspend fun onDone(targetId: String, occurrence: ReminderOccurrence) = record(targetId, occurrence, DoseStatus.TAKEN)
    override suspend fun onSkip(targetId: String, occurrence: ReminderOccurrence) = record(targetId, occurrence, DoseStatus.SKIPPED)
    override suspend fun onMissed(targetId: String, occurrence: ReminderOccurrence) {
        val m = medicines.getMedicine(targetId) ?: return
        val slot = m.schedules.firstOrNull { it.time == occurrence.localTime } ?: return
        // Only record MISSED when the user has not logged anything for that slot.
        val existing = medicines.observeDoseLogsOnce(occurrence.localDate).any { it.medicineId == targetId && it.scheduledTime == slot.time }
        if (!existing) medicines.setDoseStatus(targetId, slot.id, occurrence.localDate, slot.time, DoseStatus.MISSED, "", time.now())
    }

    private suspend fun record(id: String, occurrence: ReminderOccurrence, status: DoseStatus) {
        val m = medicines.getMedicine(id) ?: return
        val slot = m.schedules.firstOrNull { it.time == occurrence.localTime }
        val updated = medicines.setDoseStatus(id, slot?.id, occurrence.localDate, occurrence.localTime, status, "", time.now())
        updated?.let { MedicineUseCases.syncRefill(linker, it, time) }
    }
}

private suspend fun MedicineRepository.observeDoseLogsOnce(date: LocalDate): List<MedicineDoseLog> =
    observeDoseLogs(date, date).first()

// ===================================================================== meals

class MealUseCases @Inject constructor(
    private val meals: MealRepository,
    private val linker: ReminderLinker,
    private val time: TimeProvider,
    private val ids: IdGenerator,
) {
    suspend fun savePlan(plan: MealPlan): AppResult<MealPlan> {
        val clean = plan.copy(name = plan.name.trim(), updatedAt = time.now())
        validate {
            text(clean.name, "name", maxLength = Limits.TITLE_MAX)
            text(clean.description, "description", required = false, maxLength = Limits.DESCRIPTION_MAX)
            range(clean.calories, "calories", 0.0, 10_000.0)
            require(!clean.weekdays.isEmpty, "weekdays", ValidationReason.EMPTY_SELECTION)
        }.toFailureOrNull()?.let { return it }
        return appCatching { meals.savePlan(clean); syncReminder(linker, clean); clean }
    }

    suspend fun deletePlan(id: String) = appCatching {
        linker.remove(ReminderTargetType.MEAL, id)
        meals.deletePlan(id, time.now())
    }

    suspend fun log(plan: MealPlan?, date: LocalDate, status: MealStatus, description: String, calories: Int?, mealType: com.hayat.core.model.MealType = plan?.mealType ?: com.hayat.core.model.MealType.CUSTOM): AppResult<MealLog> {
        validate { range(calories, "calories", 0.0, 10_000.0); text(description, "description", required = false, maxLength = Limits.SHORT_TEXT_MAX) }.toFailureOrNull()?.let { return it }
        val log = MealLog(ids.newId(), plan?.id, date, mealType, description.trim().ifEmpty { plan?.name.orEmpty() }, calories ?: plan?.calories, status, time.now())
        return appCatching { meals.saveLog(log); log }
    }

    companion object {
        suspend fun syncReminder(linker: ReminderLinker, plan: MealPlan) {
            if (!plan.reminderEnabled || plan.isArchived) { linker.remove(ReminderTargetType.MEAL, plan.id); return }
            linker.upsert(
                LinkedReminderSpec(
                    targetType = ReminderTargetType.MEAL, targetId = plan.id, title = plan.name, description = plan.description,
                    category = ReminderCategory.MEAL,
                    schedules = listOf(Schedule(plan.weekdays.toDateRule(), TimeRule.At(plan.time), LocalDate.of(2020, 1, 1))),
                ),
            )
        }
    }
}

class MealReminderHandler @Inject constructor(private val meals: MealRepository, private val useCases: MealUseCases) : ReminderTargetHandler {
    override val targetType = ReminderTargetType.MEAL
    override suspend fun onDone(targetId: String, occurrence: ReminderOccurrence) {
        val plan = meals.getPlan(targetId) ?: return
        useCases.log(plan, occurrence.localDate, MealStatus.EATEN, "", null)
    }
    override suspend fun onSkip(targetId: String, occurrence: ReminderOccurrence) {
        val plan = meals.getPlan(targetId) ?: return
        useCases.log(plan, occurrence.localDate, MealStatus.SKIPPED, "", null)
    }
}

// ===================================================================== sleep

object SleepCalculator {
    fun validate(entry: SleepEntry): List<ValidationError> = validate {
        require(entry.wakeAt.isAfter(entry.bedAt), "wakeAt", ValidationReason.END_BEFORE_START)
        require(Duration.between(entry.bedAt, entry.wakeAt) <= Duration.ofHours(24), "wakeAt", ValidationReason.OUT_OF_RANGE)
        range(entry.quality, "quality", 1.0, 5.0)
        text(entry.note, "note", required = false, maxLength = Limits.SHORT_TEXT_MAX)
    }

    fun averageMinutes(entries: List<SleepEntry>): Int =
        if (entries.isEmpty()) 0 else (entries.sumOf { it.duration.toMinutes() } / entries.size).toInt()
}

/** Bedtime reminder and wake-up alarm, derived from preferences. */
class SleepReminderContributor @Inject constructor(
    private val prefs: PreferencesRepository,
    private val linker: ReminderLinker,
) : ReminderSyncContributor {
    override suspend fun sync(now: Instant) {
        val p = prefs.current()
        val start = LocalDate.of(2020, 1, 1)
        if (p.bedtimeReminderEnabled && ReminderCategory.SLEEP in p.enabledReminderCategories) {
            linker.upsert(
                LinkedReminderSpec(
                    ReminderTargetType.SLEEP_BEDTIME, "bedtime", "bedtime", category = ReminderCategory.SLEEP,
                    schedules = listOf(Schedule(DateRule.Daily(), TimeRule.At(p.sleepTime.minusMinutes(p.bedtimeReminderLeadMinutes.toLong())), start)),
                ),
            )
        } else linker.remove(ReminderTargetType.SLEEP_BEDTIME, "bedtime")
        if (p.wakeAlarmEnabled) {
            linker.upsert(
                LinkedReminderSpec(
                    ReminderTargetType.SLEEP_WAKE, "wake", "wake", category = ReminderCategory.SLEEP, style = ReminderStyle.ALARM,
                    priority = Priority.HIGH,
                    schedules = listOf(Schedule(DateRule.Daily(), TimeRule.At(p.wakeTime), start)),
                ),
            )
        } else linker.remove(ReminderTargetType.SLEEP_WAKE, "wake")
    }
}

// ===================================================================== fitness

class FitnessUseCases @Inject constructor(
    private val fitness: FitnessRepository,
    private val linker: ReminderLinker,
    private val time: TimeProvider,
) {
    suspend fun savePlan(plan: WorkoutPlan): AppResult<WorkoutPlan> {
        val clean = plan.copy(name = plan.name.trim(), exercises = plan.exercises.mapIndexed { i, e -> e.copy(position = i, planId = plan.id, name = e.name.trim()) }, updatedAt = time.now())
        validate {
            text(clean.name, "name", maxLength = Limits.TITLE_MAX)
            require(!(clean.reminderEnabled && (clean.time == null || clean.weekdays.isEmpty)), "schedule", ValidationReason.REQUIRED)
            clean.exercises.forEach {
                text(it.name, "exercise", maxLength = Limits.TITLE_MAX)
                range(it.sets, "sets", 1.0, 100.0); range(it.reps, "reps", 1.0, 1000.0)
                range(it.durationSeconds, "duration", 1.0, 24.0 * 3600); range(it.restSeconds, "rest", 0.0, 3600.0)
            }
        }.toFailureOrNull()?.let { return it }
        return appCatching {
            fitness.savePlan(clean)
            val t = clean.time
            if (clean.reminderEnabled && t != null && !clean.isArchived) {
                linker.upsert(LinkedReminderSpec(ReminderTargetType.WORKOUT, clean.id, clean.name, category = ReminderCategory.EXERCISE,
                    schedules = listOf(Schedule(clean.weekdays.toDateRule(), TimeRule.At(t), LocalDate.of(2020, 1, 1)))))
            } else linker.remove(ReminderTargetType.WORKOUT, clean.id)
            clean
        }
    }

    suspend fun deletePlan(id: String) = appCatching {
        linker.remove(ReminderTargetType.WORKOUT, id)
        fitness.deletePlan(id, time.now())
    }
}

/** Helper to map a minute count into hours + minutes for display. */
fun Int.toHoursMinutes(): Pair<Int, Int> = this / 60 to this % 60

fun LocalTime.minutesOfDay(): Int = toSecondOfDay() / 60
