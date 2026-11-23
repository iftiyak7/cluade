package com.hayat.core.database.dao

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.Junction
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Transaction
import androidx.room.Upsert
import com.hayat.core.database.entity.CategoryEntity
import com.hayat.core.database.entity.RoutineActivityEntity
import com.hayat.core.database.entity.RoutineActivityLogEntity
import com.hayat.core.database.entity.RoutineEntity
import com.hayat.core.database.entity.SubtaskEntity
import com.hayat.core.database.entity.TagEntity
import com.hayat.core.database.entity.TaskEntity
import com.hayat.core.database.entity.TaskTagCrossRef
import com.hayat.core.database.entity.TemplateEntity
import com.hayat.core.database.entity.TemplateItemEntity
import com.hayat.core.model.TaskStatus
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.time.LocalDate

@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories ORDER BY position, name")
    fun observeAll(): Flow<List<CategoryEntity>>
    @Upsert suspend fun upsert(entity: CategoryEntity)
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertIgnore(list: List<CategoryEntity>)
    @Query("DELETE FROM categories WHERE id = :id AND system_key IS NULL") suspend fun deleteUserCategory(id: String)
}

@Dao
interface TagDao {
    @Query("SELECT * FROM tags ORDER BY name") fun observeAll(): Flow<List<TagEntity>>
    @Query("SELECT * FROM tags WHERE name = :name COLLATE NOCASE LIMIT 1") suspend fun findByName(name: String): TagEntity?
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insert(tag: TagEntity): Long
}

data class TaskWithDetails(
    @Embedded val task: TaskEntity,
    @Relation(parentColumn = "id", entityColumn = "task_id") val subtasks: List<SubtaskEntity>,
    @Relation(parentColumn = "id", entityColumn = "id", associateBy = Junction(TaskTagCrossRef::class, parentColumn = "task_id", entityColumn = "tag_id"))
    val tags: List<TagEntity>,
)

@Dao
interface TaskDao {
    @Transaction
    @Query("SELECT * FROM tasks WHERE deleted_at IS NULL ORDER BY due_date IS NULL, due_date, due_time, created_at")
    fun observeAll(): Flow<List<TaskWithDetails>>

    @Transaction @Query("SELECT * FROM tasks WHERE id = :id AND deleted_at IS NULL") fun observe(id: String): Flow<TaskWithDetails?>
    @Transaction @Query("SELECT * FROM tasks WHERE id = :id AND deleted_at IS NULL") suspend fun get(id: String): TaskWithDetails?

    @Upsert suspend fun upsertTask(task: TaskEntity)
    @Query("DELETE FROM subtasks WHERE task_id = :taskId") suspend fun clearSubtasks(taskId: String)
    @Insert suspend fun insertSubtasks(list: List<SubtaskEntity>)
    @Query("DELETE FROM task_tags WHERE task_id = :taskId") suspend fun clearTags(taskId: String)
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertTags(list: List<TaskTagCrossRef>)

    @Transaction
    suspend fun saveWithDetails(task: TaskEntity, subtasks: List<SubtaskEntity>, tags: List<TaskTagCrossRef>) {
        upsertTask(task)
        clearSubtasks(task.id); if (subtasks.isNotEmpty()) insertSubtasks(subtasks)
        clearTags(task.id); if (tags.isNotEmpty()) insertTags(tags)
    }

    @Query("UPDATE tasks SET status = :status, completed_at = :completedAt, updated_at = :at WHERE id = :id")
    suspend fun setStatus(id: String, status: TaskStatus, completedAt: Instant?, at: Instant)
    @Query("UPDATE tasks SET is_archived = :archived, updated_at = :at WHERE id IN (:ids)") suspend fun setArchived(ids: List<String>, archived: Boolean, at: Instant)
    @Query("UPDATE tasks SET deleted_at = :at, updated_at = :at WHERE id IN (:ids)") suspend fun softDelete(ids: List<String>, at: Instant)
    @Query("UPDATE tasks SET deleted_at = NULL, updated_at = :at WHERE id IN (:ids)") suspend fun restore(ids: List<String>, at: Instant)
    @Query("UPDATE subtasks SET is_done = :done WHERE id = :id") suspend fun setSubtaskDone(id: String, done: Boolean)
    @Query("UPDATE tasks SET updated_at = :at WHERE id = (SELECT task_id FROM subtasks WHERE id = :subtaskId)") suspend fun touchBySubtask(subtaskId: String, at: Instant)
}

data class RoutineWithActivities(
    @Embedded val routine: RoutineEntity,
    @Relation(parentColumn = "id", entityColumn = "routine_id") val activities: List<RoutineActivityEntity>,
)

@Dao
interface RoutineDao {
    @Transaction @Query("SELECT * FROM routines WHERE deleted_at IS NULL ORDER BY is_archived, name") fun observeAll(): Flow<List<RoutineWithActivities>>
    @Transaction @Query("SELECT * FROM routines WHERE id = :id AND deleted_at IS NULL") fun observe(id: String): Flow<RoutineWithActivities?>
    @Transaction @Query("SELECT * FROM routines WHERE id = :id AND deleted_at IS NULL") suspend fun get(id: String): RoutineWithActivities?
    @Transaction @Query("SELECT r.* FROM routines r JOIN routine_activities a ON a.routine_id = r.id WHERE a.id = :activityId") suspend fun findByActivity(activityId: String): RoutineWithActivities?

    @Upsert suspend fun upsertRoutine(entity: RoutineEntity)
    @Upsert suspend fun upsertActivities(list: List<RoutineActivityEntity>)
    @Query("DELETE FROM routine_activities WHERE routine_id = :routineId AND id NOT IN (:keep)") suspend fun deleteActivitiesExcept(routineId: String, keep: List<String>)
    @Query("DELETE FROM routine_activities WHERE routine_id = :routineId") suspend fun deleteAllActivities(routineId: String)

    /** Upserts activities (keeping ids so completion logs survive edits) and removes deleted ones. */
    @Transaction
    suspend fun save(routine: RoutineEntity, activities: List<RoutineActivityEntity>) {
        upsertRoutine(routine)
        if (activities.isEmpty()) deleteAllActivities(routine.id) else {
            deleteActivitiesExcept(routine.id, activities.map { it.id })
            upsertActivities(activities)
        }
    }

    @Query("UPDATE routines SET is_paused = :paused, updated_at = :at WHERE id = :id") suspend fun setPaused(id: String, paused: Boolean, at: Instant)
    @Query("UPDATE routines SET is_archived = :archived, updated_at = :at WHERE id = :id") suspend fun setArchived(id: String, archived: Boolean, at: Instant)
    @Query("UPDATE routines SET deleted_at = :at, updated_at = :at WHERE id = :id") suspend fun softDelete(id: String, at: Instant)

    @Query("SELECT * FROM routine_activity_logs WHERE date BETWEEN :from AND :to") fun observeLogs(from: LocalDate, to: LocalDate): Flow<List<RoutineActivityLogEntity>>
    @Upsert suspend fun upsertLog(log: RoutineActivityLogEntity)
    @Query("SELECT * FROM routine_activity_logs WHERE activity_id = :activityId AND date = :date") suspend fun findLog(activityId: String, date: LocalDate): RoutineActivityLogEntity?
    @Query("DELETE FROM routine_activity_logs WHERE activity_id = :activityId AND date = :date") suspend fun deleteLog(activityId: String, date: LocalDate)
}

data class TemplateWithItems(
    @Embedded val template: TemplateEntity,
    @Relation(parentColumn = "id", entityColumn = "template_id") val items: List<TemplateItemEntity>,
)

@Dao
interface TemplateDao {
    @Transaction @Query("SELECT * FROM templates ORDER BY name") fun observeAll(): Flow<List<TemplateWithItems>>
    @Upsert suspend fun upsert(t: TemplateEntity)
    @Query("DELETE FROM template_items WHERE template_id = :id") suspend fun clearItems(id: String)
    @Insert suspend fun insertItems(items: List<TemplateItemEntity>)
    @Query("DELETE FROM templates WHERE id = :id") suspend fun delete(id: String)
    @Transaction suspend fun save(t: TemplateEntity, items: List<TemplateItemEntity>) { upsert(t); clearItems(t.id); if (items.isNotEmpty()) insertItems(items) }
}
