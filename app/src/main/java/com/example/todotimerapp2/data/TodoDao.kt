package com.example.todotimerapp2.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface TodoDao {
    @Query("""
        SELECT * FROM todos
        WHERE dayOfWeek = :day
        ORDER BY isDone ASC, position ASC, updatedAt DESC
    """)
    fun observeByDay(day: Int): Flow<List<TodoEntity>>

    @Insert
    suspend fun insert(todo: TodoEntity): Long

    @Update
    suspend fun update(todo: TodoEntity)

    @Delete
    suspend fun delete(todo: TodoEntity)

    @Query("SELECT * FROM todos WHERE id = :id")
    suspend fun getById(id: Long): TodoEntity?

    // Макс позиция для дня (чтобы добавлять в конец)
    @Query("SELECT IFNULL(MAX(position), -1) FROM todos WHERE dayOfWeek = :day")
    suspend fun maxPosition(day: Int): Int

    // Массовое обновление позиций (в транзакции делаем через репозиторий)
    @Update
    suspend fun updateAll(items: List<TodoEntity>)
}
