package com.example.todotimerapp2.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "todos")
data class TodoEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val note: String = "",
    val isDone: Boolean = false,
    val endTimeMillis: Long? = null,
    val dayOfWeek: Int = 1,
    val position: Int = Int.MAX_VALUE,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
