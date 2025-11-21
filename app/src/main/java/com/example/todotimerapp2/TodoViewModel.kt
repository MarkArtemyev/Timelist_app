package com.example.todotimerapp2

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.todotimerapp2.data.TodoEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

class TodoViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = TodoRepository(app)

    private val _selectedDay = MutableStateFlow(LocalDate.now().dayOfWeek.value)
    val selectedDay = _selectedDay

    val state = selectedDay
        .flatMapLatest { day -> repo.observeByDay(day) }
        .map { it.sortedWith(compareBy<TodoEntity> { it.isDone }.thenBy { it.position }) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setDay(day: Int) { _selectedDay.value = day.coerceIn(1, 7) }

    // --- ИЗМЕНЕНИЕ: `add` теперь принимает `note` ---
    fun add(title: String, note: String) = viewModelScope.launch {
        repo.add(title, note, dayOfWeek = selectedDay.value)
    }

    fun toggle(todo: TodoEntity) = viewModelScope.launch { repo.toggleDone(todo) }
    fun delete(todo: TodoEntity) = viewModelScope.launch { repo.delete(todo) }
    fun startTimer(todo: TodoEntity, minutes: Long) = viewModelScope.launch { repo.setTimer(todo, minutes) }
    fun stopTimer(todo: TodoEntity) = viewModelScope.launch { repo.clearTimer(todo) }

    fun commitReorder(newOrder: List<TodoEntity>) = viewModelScope.launch {
        repo.reorderWithinDay(selectedDay.value, newOrder.map { it.id })
    }

    fun dropToDay(todoId: Long, newDay: Int) = viewModelScope.launch {
        repo.moveToDay(todoId, newDay)
    }

    // --- ИЗМЕНЕНИЕ: Новая функция `update` ---
    fun update(id: Long, title: String, note: String) = viewModelScope.launch {
        repo.update(id, title, note)
    }
}