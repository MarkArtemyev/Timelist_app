package com.example.todotimerapp2

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.example.todotimerapp2.data.TodoEntity
import com.example.todotimerapp2.ui.rememberRemainingText
import kotlin.math.roundToInt


class MainActivity : ComponentActivity() {
    private val vm: TodoViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        setContent {
            val askNotifPermission = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission()
            ) { /* ignore */ }

            LaunchedEffect(Unit) {
                if (Build.VERSION.SDK_INT >= 33) {
                    askNotifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }

            MaterialTheme(
                colorScheme = dynamicDarkColorScheme()
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    TodoScreen(vm)
                }
            }
        }
    }
}

@Composable
private fun dynamicDarkColorScheme() = darkColorScheme(
    primary = Color(0xFF90CAF9),
    secondary = Color(0xFFCE93D8),
    tertiary = Color(0xFFA5D6A7),
    background = Color(0xFF121212),
    surface = Color(0xFF1E1E1E),
    surfaceVariant = Color(0xFF2C2C2C),
    error = Color(0xFFCF6679)
)

private val DayLabels = listOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс")

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun TodoScreen(vm: TodoViewModel) {
    val todosFromVm by vm.state.collectAsState()
    val selectedDay by vm.selectedDay.collectAsState()
    val focusManager = LocalFocusManager.current
    val haptic = LocalHapticFeedback.current

    var list by remember(todosFromVm) { mutableStateOf(todosFromVm) }
    val tabAreas = remember { mutableStateMapOf<Int, Rect>() }
    var hoveredTab by remember { mutableStateOf<Int?>(null) }
    var draggingId by remember { mutableStateOf<Long?>(null) }
    var dragOffset by remember { mutableStateOf(Offset.Zero) }
    var newTitle by remember { mutableStateOf("") }
    var expandedAddTask by remember { mutableStateOf(false) }

    // Статистика для текущего дня
    val completedCount = list.count { it.isDone }
    val totalCount = list.size
    val hasActiveTasks = list.any { !it.isDone }

    Scaffold(
        contentWindowInsets = WindowInsets.systemBars,
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Column {
                            Text("Todo + Таймер", fontWeight = FontWeight.Bold)
                            if (totalCount > 0) {
                                Text(
                                    "$completedCount из $totalCount завершено",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )

                PrimaryTabRow(
                    selectedTabIndex = selectedDay - 1,
                    containerColor = MaterialTheme.colorScheme.surface
                ) {
                    DayLabels.forEachIndexed { idx, label ->
                        val day = idx + 1
                        val isSelected = day == selectedDay
                        val isHover = hoveredTab == day && draggingId != null

                        val tabColor by animateColorAsState(
                            targetValue = when {
                                isHover -> MaterialTheme.colorScheme.primary
                                isSelected -> MaterialTheme.colorScheme.primary
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            animationSpec = tween(200)
                        )

                        Tab(
                            selected = isSelected,
                            onClick = {
                                vm.setDay(day)
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            },
                            text = {
                                Text(
                                    label,
                                    color = tabColor,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            modifier = Modifier.onGloballyPositioned { coords ->
                                val p = coords.positionInRoot()
                                val s = coords.size
                                tabAreas[day] = Rect(p.x, p.y, p.x + s.width, p.y + s.height)
                            }
                        )
                    }
                }

                // Прогресс бар
                if (totalCount > 0) {
                    LinearProgressIndicator(
                        progress = completedCount.toFloat() / totalCount,
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.tertiary
                    )
                }
            }
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { expandedAddTask = !expandedAddTask },
                containerColor = MaterialTheme.colorScheme.primary,
                modifier = Modifier.imePadding()
            ) {
                Icon(
                    imageVector = if (expandedAddTask) Icons.Default.Close else Icons.Default.Add,
                    contentDescription = "Добавить задачу"
                )
            }
        }
    ) { padding ->

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Поле добавления задачи
            AnimatedVisibility(
                visible = expandedAddTask,
                enter = fadeIn() + scaleIn(),
                exit = fadeOut() + scaleOut()
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp)
                            .imePadding(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = newTitle,
                            onValueChange = { newTitle = it },
                            placeholder = { Text("Новая задача…") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            colors = TextFieldDefaults.colors(
                                unfocusedContainerColor = Color.Transparent,
                                focusedContainerColor = Color.Transparent
                            )
                        )
                        Spacer(Modifier.width(8.dp))
                        IconButton(
                            onClick = {
                                val text = newTitle.trim()
                                if (text.isNotEmpty()) {
                                    vm.add(text)
                                    newTitle = ""
                                    expandedAddTask = false
                                    focusManager.clearFocus()
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                }
                            },
                            colors = IconButtonDefaults.iconButtonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            ),
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(Icons.Default.Send, "Добавить")
                        }
                    }
                }
            }

            // Список задач
            if (list.isEmpty()) {
                EmptyStateView(hasActiveTasks)
            } else {
                TodoList(
                    list = list,
                    draggingId = draggingId,
                    dragOffset = dragOffset,
                    tabAreas = tabAreas,
                    vm = vm,
                    haptic = haptic,
                    onListUpdate = { list = it },
                    onDragStart = { id, offset ->
                        draggingId = id
                        dragOffset = offset
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    },
                    onDragEnd = { todo ->
                        val dropDay = tabAreas.entries
                            .firstOrNull { it.value.contains(dragOffset) }
                            ?.key
                        if (dropDay != null) {
                            vm.dropToDay(todo.id, dropDay)
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        } else {
                            vm.commitReorder(list)
                        }
                        draggingId = null
                        hoveredTab = null
                    },
                    onDragCancel = {
                        draggingId = null
                        hoveredTab = null
                    },
                    onHoverTab = { hoveredTab = it }
                )
            }
        }
    }
}

@Composable
private fun EmptyStateView(hasActiveTasks: Boolean) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                imageVector = if (hasActiveTasks) Icons.Default.CheckCircle else Icons.Default.EventNote,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
            )
            Text(
                text = if (hasActiveTasks) "Все задачи выполнены! 🎉" else "На этот день ничего",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "Нажмите + чтобы добавить задачу",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TodoList(
    list: List<TodoEntity>,
    draggingId: Long?,
    dragOffset: Offset,
    tabAreas: Map<Int, Rect>,
    vm: TodoViewModel,
    haptic: androidx.compose.ui.hapticfeedback.HapticFeedback,
    onListUpdate: (List<TodoEntity>) -> Unit,
    onDragStart: (Long, Offset) -> Unit,
    onDragEnd: (TodoEntity) -> Unit,
    onDragCancel: () -> Unit,
    onHoverTab: (Int?) -> Unit
) {
    data class Bounds(val l: Float, val t: Float, val r: Float, val b: Float)
    val itemBounds = remember { mutableStateMapOf<Long, Bounds>() }

    fun indexOf(id: Long) = list.indexOfFirst { it.id == id }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        itemsIndexed(list, key = { _, it -> it.id }) { _, todo ->
            val isDragging = draggingId == todo.id

            val bounds = itemBounds[todo.id]
            val yOffset = if (isDragging && bounds != null) {
                val centerOfItem = (bounds.t + bounds.b) / 2f
                (dragOffset.y - centerOfItem).roundToInt()
            } else 0

            val elevation by animateDpAsState(
                targetValue = if (isDragging) 8.dp else 2.dp,
                animationSpec = tween(200)
            )

            TodoItemRow(
                todo = todo,
                onToggle = {
                    vm.toggle(todo)
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                },
                onDelete = { vm.delete(todo) },
                onStartTimer = { m -> vm.startTimer(todo, m) },
                onStopTimer = { vm.stopTimer(todo) },
                modifier = Modifier
                    .offset { IntOffset(0, yOffset) }
                    .shadow(elevation, MaterialTheme.shapes.medium)
                    .animateItemPlacement()
                    .onGloballyPositioned { layout ->
                        val p = layout.positionInRoot()
                        val s = layout.size
                        itemBounds[todo.id] = Bounds(p.x, p.y, p.x + s.width, p.y + s.height)
                    }
                    .pointerInput(list, tabAreas) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = {
                                val b = itemBounds[todo.id] ?: return@detectDragGesturesAfterLongPress
                                onDragStart(todo.id, Offset((b.l + b.r) / 2f, (b.t + b.b) / 2f))
                            },
                            onDragEnd = { onDragEnd(todo) },
                            onDragCancel = onDragCancel
                        ) { change, drag ->
                            change.consume()
                            if (draggingId != todo.id) return@detectDragGesturesAfterLongPress

                            val newOffset = dragOffset + drag
                            onDragStart(todo.id, newOffset)

                            onHoverTab(tabAreas.entries.firstOrNull { it.value.contains(newOffset) }?.key)

                            val myIdx = indexOf(todo.id)
                            val myCenterY = newOffset.y

                            if (myIdx > 0) {
                                val prev = list[myIdx - 1]
                                val pb = itemBounds[prev.id] ?: return@detectDragGesturesAfterLongPress
                                if (myCenterY < (pb.t + pb.b) / 2f) {
                                    val m = list.toMutableList()
                                    m.removeAt(myIdx)
                                    m.add(myIdx - 1, todo)
                                    onListUpdate(m)
                                    return@detectDragGesturesAfterLongPress
                                }
                            }
                            if (myIdx < list.lastIndex) {
                                val next = list[myIdx + 1]
                                val nb = itemBounds[next.id] ?: return@detectDragGesturesAfterLongPress
                                if (myCenterY > (nb.t + nb.b) / 2f) {
                                    val m = list.toMutableList()
                                    m.removeAt(myIdx)
                                    m.add(myIdx + 1, todo)
                                    onListUpdate(m)
                                    return@detectDragGesturesAfterLongPress
                                }
                            }
                        }
                    },
                dragging = isDragging
            )
        }
    }
}

@Composable
private fun TodoItemRow(
    todo: TodoEntity,
    onToggle: () -> Unit,
    onDelete: () -> Unit,
    onStartTimer: (Long) -> Unit,
    onStopTimer: () -> Unit,
    modifier: Modifier = Modifier,
    dragging: Boolean = false
) {
    val timeLeft = rememberRemainingText(todo.endTimeMillis)
    var showTimerDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    val containerColor by animateColorAsState(
        targetValue = when {
            dragging -> MaterialTheme.colorScheme.primaryContainer
            todo.isDone -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            else -> MaterialTheme.colorScheme.surfaceVariant
        },
        animationSpec = tween(200)
    )

    val isTimerActive = todo.endTimeMillis != null
    val isOverdue = isTimerActive && timeLeft.contains("-")

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = containerColor)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Checkbox(
                    checked = todo.isDone,
                    onCheckedChange = { onToggle() }
                )

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = todo.title,
                        fontWeight = FontWeight.SemiBold,
                        textDecoration = if (todo.isDone) TextDecoration.LineThrough else null,
                        modifier = Modifier.alpha(if (todo.isDone) 0.5f else 1f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (todo.note.isNotBlank()) {
                        Text(
                            text = todo.note,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.alpha(0.7f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                if (isTimerActive) {
                    Surface(
                        shape = CircleShape,
                        color = if (isOverdue)
                            MaterialTheme.colorScheme.error
                        else
                            MaterialTheme.colorScheme.tertiaryContainer,
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Text(
                            text = timeLeft,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            color = if (isOverdue)
                                MaterialTheme.colorScheme.onError
                            else
                                MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                if (!isTimerActive) {
                    FilledTonalButton(
                        onClick = { showTimerDialog = true },
                        modifier = Modifier.height(36.dp)
                    ) {
                        Icon(Icons.Default.Timer, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Таймер", style = MaterialTheme.typography.labelMedium)
                    }
                } else {
                    Button(
                        onClick = onStopTimer,
                        modifier = Modifier.height(36.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Icon(Icons.Default.Stop, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Стоп", style = MaterialTheme.typography.labelMedium)
                    }
                }

                Spacer(Modifier.width(8.dp))

                IconButton(
                    onClick = { showDeleteDialog = true },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Удалить",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }

    if (showTimerDialog) {
        SetTimerDialog(
            onDismiss = { showTimerDialog = false },
            onConfirm = { minutes ->
                showTimerDialog = false
                onStartTimer(minutes)
            }
        )
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            icon = { Icon(Icons.Default.Warning, null) },
            title = { Text("Удалить задачу?") },
            text = { Text("Это действие нельзя отменить") },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteDialog = false
                        onDelete()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Удалить")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Отмена")
                }
            }
        )
    }
}

@Composable
private fun SetTimerDialog(
    onDismiss: () -> Unit,
    onConfirm: (Long) -> Unit
) {
    var minutesText by remember { mutableStateOf("25") }
    val presets = listOf(5, 10, 15, 25, 45, 60)

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Timer, null) },
        title = { Text("Установить таймер") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = minutesText,
                    onValueChange = { minutesText = it.filter { ch -> ch.isDigit() }.take(4) },
                    label = { Text("Минуты") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Text(
                    "Быстрый выбор:",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    presets.forEach { preset ->
                        FilterChip(
                            selected = minutesText == preset.toString(),
                            onClick = { minutesText = preset.toString() },
                            label = { Text("$preset мин") },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val m = minutesText.toLongOrNull() ?: 0L
                    if (m > 0) onConfirm(m)
                }
            ) {
                Text("Старт")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Отмена")
            }
        }
    )
}