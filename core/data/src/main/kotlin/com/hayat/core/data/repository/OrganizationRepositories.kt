package com.hayat.core.data.repository

import androidx.room.withTransaction
import com.hayat.core.common.IdGenerator
import com.hayat.core.common.TimeProvider
import com.hayat.core.common.escapeLike
import com.hayat.core.common.normalizeQuery
import com.hayat.core.data.mapper.toEntity
import com.hayat.core.data.mapper.toModel
import com.hayat.core.database.HayatDatabase
import com.hayat.core.database.dao.CategoryDao
import com.hayat.core.database.dao.MaintenanceDao
import com.hayat.core.database.dao.RoutineDao
import com.hayat.core.database.dao.SearchDao
import com.hayat.core.database.dao.TagDao
import com.hayat.core.database.dao.TaskDao
import com.hayat.core.database.dao.TemplateDao
import com.hayat.core.database.entity.CategoryEntity
import com.hayat.core.database.entity.RoutineActivityLogEntity
import com.hayat.core.database.entity.SubtaskEntity
import com.hayat.core.database.entity.TagEntity
import com.hayat.core.database.entity.TaskTagCrossRef
import com.hayat.core.database.entity.TemplateEntity
import com.hayat.core.database.entity.TemplateItemEntity
import com.hayat.core.domain.repository.CategoryRepository
import com.hayat.core.domain.repository.DataMaintenanceRepository
import com.hayat.core.domain.repository.RoutineRepository
import com.hayat.core.domain.repository.SearchRepository
import com.hayat.core.domain.repository.TagRepository
import com.hayat.core.domain.repository.TaskRepository
import com.hayat.core.domain.repository.TemplateRepository
import com.hayat.core.model.ActivityStatus
import com.hayat.core.model.Category
import com.hayat.core.model.RoutineTemplate
import com.hayat.core.model.SearchResult
import com.hayat.core.model.SearchResultType
import com.hayat.core.model.Tag
import com.hayat.core.model.Task
import com.hayat.core.model.TaskStatus
import com.hayat.core.time.RecurrenceCodec
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject

class RoomCategoryRepository @Inject constructor(private val dao: CategoryDao, private val time: TimeProvider) : CategoryRepository {
    override fun observeCategories() = dao.observeAll().map { l -> l.map { it.toModel() } }
    override suspend fun upsert(category: Category) {
        val now = time.now()
        dao.upsert(CategoryEntity(category.id, category.name.trim(), category.systemKey, category.color, category.icon, category.position, now, now))
    }
    override suspend fun delete(id: String) = dao.deleteUserCategory(id)
}

class RoomTagRepository @Inject constructor(private val dao: TagDao, private val ids: IdGenerator, private val time: TimeProvider) : TagRepository {
    override fun observeTags() = dao.observeAll().map { l -> l.map { it.toModel() } }
    override suspend fun getOrCreate(name: String): Tag {
        val clean = name.trim().take(40)
        dao.findByName(clean)?.let { return it.toModel() }
        dao.insert(TagEntity(ids.newId(), clean, time.now()))
        return dao.findByName(clean)!!.toModel()
    }
}

class RoomTaskRepository @Inject constructor(private val dao: TaskDao) : TaskRepository {
    override fun observeTasks() = dao.observeAll().map { l -> l.map { it.toModel() } }
    override fun observeTask(id: String) = dao.observe(id).map { it?.toModel() }
    override suspend fun getTask(id: String) = dao.get(id)?.toModel()
    override suspend fun save(task: Task) = dao.saveWithDetails(
        task.toEntity(),
        task.subtasks.mapIndexed { i, s -> SubtaskEntity(s.id, task.id, s.title.trim(), s.isDone, i) },
        task.tags.map { TaskTagCrossRef(task.id, it.id) },
    )
    override suspend fun setStatusCompleted(id: String, completed: Boolean, at: Instant) =
        dao.setStatus(id, if (completed) TaskStatus.COMPLETED else TaskStatus.OPEN, if (completed) at else null, at)
    override suspend fun setArchived(ids: Collection<String>, archived: Boolean, at: Instant) = dao.setArchived(ids.toList(), archived, at)
    override suspend fun softDelete(ids: Collection<String>, at: Instant) = dao.softDelete(ids.toList(), at)
    override suspend fun restore(ids: Collection<String>, at: Instant) = dao.restore(ids.toList(), at)
    override suspend fun setSubtaskDone(subtaskId: String, done: Boolean, at: Instant) { dao.setSubtaskDone(subtaskId, done); dao.touchBySubtask(subtaskId, at) }
}

class RoomRoutineRepository @Inject constructor(private val dao: RoutineDao, private val ids: IdGenerator) : RoutineRepository {
    override fun observeRoutines() = dao.observeAll().map { l -> l.map { it.toModel() } }
    override fun observeRoutine(id: String) = dao.observe(id).map { it?.toModel() }
    override suspend fun getRoutine(id: String) = dao.get(id)?.toModel()
    override suspend fun findActivityRoutine(activityId: String) = dao.findByActivity(activityId)?.toModel()
    override suspend fun save(routine: com.hayat.core.model.Routine) = dao.save(routine.toEntity(), routine.activities.map { it.toEntity() })
    override suspend fun setPaused(id: String, paused: Boolean, at: Instant) = dao.setPaused(id, paused, at)
    override suspend fun setArchived(id: String, archived: Boolean, at: Instant) = dao.setArchived(id, archived, at)
    override suspend fun softDelete(id: String, at: Instant) = dao.softDelete(id, at)
    override fun observeLogs(from: LocalDate, to: LocalDate) = dao.observeLogs(from, to).map { l -> l.map { it.toModel() } }
    override suspend fun setLog(activityId: String, date: LocalDate, status: ActivityStatus?, note: String, at: Instant) {
        if (status == null) { dao.deleteLog(activityId, date); return }
        val existing = dao.findLog(activityId, date)
        dao.upsertLog(RoutineActivityLogEntity(existing?.id ?: ids.newId(), activityId, date, status, at, note.take(200)))
    }
}

class RoomTemplateRepository @Inject constructor(private val dao: TemplateDao, private val ids: IdGenerator, private val time: TimeProvider) : TemplateRepository {
    override fun observeUserTemplates() = dao.observeAll().map { l -> l.map { it.toModel() } }
    override suspend fun saveUserTemplate(template: RoutineTemplate) = dao.save(
        TemplateEntity(template.id, template.name, template.description, template.kind, RecurrenceCodec.encode(template.dateRule), time.now()),
        template.items.mapIndexed { i, it -> TemplateItemEntity(ids.newId(), template.id, it.title, it.startTime, it.durationMinutes, i) },
    )
    override suspend fun deleteUserTemplate(id: String) = dao.delete(id)
}

class RoomSearchRepository @Inject constructor(private val dao: SearchDao) : SearchRepository {
    override suspend fun search(query: String, limitPerType: Int): List<SearchResult> {
        val q = query.normalizeQuery()
        if (q.isEmpty()) return emptyList()
        val rows = dao.search("%" + q.escapeLike() + "%", limitPerType * SearchResultType.entries.size)
        return rows.mapNotNull { r ->
            val type = runCatching { SearchResultType.valueOf(r.type) }.getOrNull() ?: return@mapNotNull null
            SearchResult(type, r.id, r.title, r.snippet)
        }.distinctBy { it.type to it.id }
            .groupBy { it.type }.flatMap { (_, l) -> l.take(limitPerType) }
    }
}

class RoomDataMaintenanceRepository @Inject constructor(
    private val db: HayatDatabase,
    private val maintenance: MaintenanceDao,
) : DataMaintenanceRepository {
    override suspend fun purgeSoftDeleted(before: Instant): Int = maintenance.purgeSoftDeleted(before) + maintenance.purgeOccurrences(before.minusSeconds(60L * 24 * 3600 * 60))
    override suspend fun deleteAllUserData() { db.withTransaction { db.backupDao().clearAll() } }
}
