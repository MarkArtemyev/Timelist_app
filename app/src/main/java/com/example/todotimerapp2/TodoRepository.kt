package com.example.todotimerapp2

import android.content.Context
import androidx.room.withTransaction
import com.example.todotimerapp2.data.AppDatabase
import com.example.todotimerapp2.data.TodoEntity
import com.example.todotimerapp2.timer.TimerScheduler
import kotlinx.coroutines.flow.Flow

class TodoRepository(private val context: Context) {
    private val db = AppDatabase.get(context)
    private val dao = db.todoDao()

    fun observeByDay(dayOfWeek: Int): Flow<List<TodoEntity>> = dao.observeByDay(dayOfWeek)

    suspend fun add(title: String, note: String = "", dayOfWeek: Int) {
        if (title.isBlank()) return
        val pos = dao.maxPosition(dayOfWeek) + 1
        dao.insert(
            TodoEntity(
                title = title.trim(),
                note = note.trim(),
                dayOfWeek = dayOfWeek,
                position = pos
            )
        )
    }

    suspend fun toggleDone(todo: TodoEntity) {
        val updated = todo.copy(isDone = !todo.isDone, updatedAt = System.currentTimeMillis())
        dao.update(updated)
        if (updated.isDone) {
            updated.endTimeMillis?.let { TimerScheduler.cancel(context, updated.id) }
            dao.update(updated.copy(endTimeMillis = null, updatedAt = System.currentTimeMillis()))
        }
    }

    suspend fun delete(todo: TodoEntity) {
        dao.delete(todo)
        TimerScheduler.cancel(context, todo.id)
    }

    suspend fun setTimer(todo: TodoEntity, durationMinutes: Long) {
        if (durationMinutes <= 0) return
        val end = System.currentTimeMillis() + durationMinutes * 60_000L
        val updated = todo.copy(endTimeMillis = end, updatedAt = System.currentTimeMillis())
        dao.update(updated)
        TimerScheduler.schedule(context, updated.id, end, updated.title)
    }

    suspend fun clearTimer(todo: TodoEntity) {
        TimerScheduler.cancel(context, todo.id)
        dao.update(todo.copy(endTimeMillis = null, updatedAt = System.currentTimeMillis()))
    }

    /** Переставить элементы внутри дня по новому порядку id -> позиция */
    suspend fun reorderWithinDay(day: Int, orderedIds: List<Long>) {
        db.withTransaction {
            val now = System.currentTimeMillis()
            val items = orderedIds.mapIndexed { index, id ->
                val e = dao.getById(id) ?: return@withTransaction
                e.copy(position = index, updatedAt = now)
            }
            dao.updateAll(items.filterNotNull())
        }
    }

    /** Перенести задачу в другой день (в конец того дня) */
    suspend fun moveToDay(todoId: Long, newDay: Int) {
        val e = dao.getById(todoId) ?: return
        val newPos = dao.maxPosition(newDay) + 1
        dao.update(
            e.copy(dayOfWeek = newDay, position = newPos, updatedAt = System.currentTimeMillis())
        )
    }
}
