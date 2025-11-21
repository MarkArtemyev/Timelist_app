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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.core.view.WindowCompat
import com.example.todotimerapp2.data.TodoEntity
import com.example.todotimerapp2.ui.rememberRemainingText

// ---- Activity / App shell ----

class MainActivity : ComponentActivity() {

    private val vm: TodoViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        setContent {
            TodoApp(vm = vm)
        }
    }
}

@Composable
private fun TodoApp(vm: TodoViewModel) {
    val context = LocalContext.current

    val askNotifPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        // ничего не делаем
    }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33) {
            askNotifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    val colorScheme = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            null
        } else {
            appFallbackDarkScheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme ?: dynamicDarkColorScheme(context)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            TodoScreen(vm = vm)
        }
    }
}

private val appFallbackDarkScheme = darkColorScheme(
    primary = Color(0xFF90CAF9),
    secondary = Color(0xFFCE93D8),
    tertiary = Color(0xFFA5D6A7),
    background = Color(0xFF121212),
    surface = Color(0xFF1E1E1E),
    surfaceVariant = Color(0xFF2C2C2C),
    error = Color(0xFFCF6679)
)

private val DAY_LABELS = listOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс")

// ---- Drag-state holder ----

private class TodoDragState {
    val tabAreas = mutableStateMapOf<Int, Rect>()
    var draggingId by mutableStateOf<Long?>(null)
    var dragOffset by mutableStateOf(Offset.Zero)
    var hoveredTabId by mutableStateOf<Int?>(null)

    fun reset() {
        draggingId = null
        dragOffset = Offset.Zero
        hoveredTabId = null
    }
}

// ---- Screen ----

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun TodoScreen(
    vm: TodoViewModel,
    modifier: Modifier = Modifier
) {
    val todosFromVm by vm.state.collectAsState()
    val selectedDay by vm.selectedDay.collectAsState()

    val focusManager = LocalFocusManager.current
    val haptic = LocalHapticFeedback.current

    var localListOverride by remember { mutableStateOf<List<TodoEntity>?>(null) }
    val listToShow = localListOverride ?: todosFromVm

    val dragState = remember { TodoDragState() }

    val completedCount = listToShow.count { it.isDone }
    val totalCount = listToShow.size
    val hasActiveTasks = listToShow.any { !it.isDone }

    // --- ВОЗВРАЩЕНО: Стейты для добавления и редактирования ---
    var newTitle by remember { mutableStateOf("") }
    var newNote by remember { mutableStateOf("") }
    var taskToEdit by remember { mutableStateOf<TodoEntity?>(null) }
    // ---

    var expandedAddTask by remember { mutableStateOf(false) }

    // --- ВОЗВРАЩЕНО: Диалог редактирования ---
    if (taskToEdit != null) {
        EditTaskDialog(
            task = taskToEdit!!,
            onDismiss = { taskToEdit = null },
            onConfirm = { id, title, note ->
                vm.update(id, title, note)
                taskToEdit = null
            }
        )
    }
    // ---

    Scaffold(
        modifier = modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets.systemBars,
        topBar = {
            TodoTopBar(
                selectedDay = selectedDay,
                completedCount = completedCount,
                totalCount = totalCount,
                dragState = dragState,
                onDaySelected = { day ->
                    vm.setDay(day)
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                }
            )
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
    ) { paddingValues ->

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            AnimatedVisibility(
                visible = expandedAddTask,
                enter = fadeIn() + scaleIn(),
                exit = fadeOut() + scaleOut()
            ) {
                // --- ИЗМЕНЕНО: Вызов `NewTaskForm` ---
                NewTaskForm(
                    title = newTitle,
                    onTitleChange = { newTitle = it },
                    note = newNote,
                    onNoteChange = { newNote = it },
                    onSubmit = {
                        val title = newTitle.trim()
                        val note = newNote.trim()
                        if (title.isNotEmpty()) {
                            vm.add(title, note) // Передаем оба поля
                            newTitle = ""
                            newNote = ""
                            expandedAddTask = false
                            focusManager.clearFocus()
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        }
                    }
                )
                // ---
            }

            if (listToShow.isEmpty()) {
                EmptyStateView(hasActiveTasks = hasActiveTasks)
            } else {
                TodoList(
                    list = listToShow,
                    draggingId = dragState.draggingId,
                    dragOffset = dragState.dragOffset,
                    tabAreas = dragState.tabAreas,
                    vm = vm,
                    haptic = haptic,
                    onListUpdate = { updated ->
                        localListOverride = updated
                    },
                    onDragStart = { id, offset ->
                        localListOverride = todosFromVm
                        dragState.draggingId = id
                        dragState.dragOffset = offset
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    },
                    onDrag = { offset ->
                        dragState.dragOffset = offset
                    },
                    onDragEnd = { todo ->
                        val dropDay = dragState.tabAreas.entries
                            .firstOrNull { (_, rect) -> rect.contains(dragState.dragOffset) }
                            ?.key

                        if (dropDay != null) {
                            vm.dropToDay(todo.id, dropDay)
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        } else {
                            localListOverride?.let { vm.commitReorder(it) }
                        }

                        dragState.reset()
                        localListOverride = null
                    },
                    onDragCancel = {
                        dragState.reset()
                        localListOverride = null
                    },
                    onHoverTab = { dayOrNull ->
                        dragState.hoveredTabId = dayOrNull
                    },
                    // --- ВОЗВРАЩЕНО: Передача клика ---
                    onItemClick = { task ->
                        if (dragState.draggingId == null) {
                            taskToEdit = task
                        }
                    }
                    // ---
                )
            }
        }
    }
}

// ---- Top bar + add card ----

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TodoTopBar(
    selectedDay: Int,
    completedCount: Int,
    totalCount: Int,
    dragState: TodoDragState,
    onDaySelected: (Int) -> Unit
) {
    Column {
        TopAppBar(
            title = {
                Column {
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
            val hoveringTabId = dragState.hoveredTabId
            val currentDraggingId = dragState.draggingId

            DAY_LABELS.forEachIndexed { index, label ->
                val day = index + 1
                val isSelected = day == selectedDay
                val isHover = hoveringTabId == day && currentDraggingId != null

                val tabColor by animateColorAsState(
                    targetValue = when {
                        isHover -> MaterialTheme.colorScheme.primary
                        isSelected -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    animationSpec = tween(160),
                    label = "TabColorAnim"
                )

                Tab(
                    selected = isSelected,
                    onClick = { onDaySelected(day) },
                    text = {
                        Text(
                            text = label,
                            color = tabColor,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    },
                    modifier = Modifier.onGloballyPositioned { coords ->
                        val position = coords.positionInRoot()
                        val size = coords.size
                        dragState.tabAreas[day] = Rect(
                            left = position.x,
                            top = position.y,
                            right = position.x + size.width,
                            bottom = position.y + size.height
                        )
                    }
                )
            }
        }

        if (totalCount > 0) {
            LinearProgressIndicator(
                progress = completedCount.toFloat() / totalCount,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp),
                color = MaterialTheme.colorScheme.tertiary
            )
        }
    }
}

@Composable
private fun NewTaskForm(
    title: String,
    onTitleChange: (String) -> Unit,
    note: String,
    onNoteChange: (String) -> Unit,
    onSubmit: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
                .imePadding(),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = title,
                    onValueChange = onTitleChange,
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
                    onClick = onSubmit,
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    ),
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(Icons.Default.Send, contentDescription = "Добавить")
                }
            }
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = note,
                onValueChange = onNoteChange,
                placeholder = { Text("Описание (необязательно)...") },
                modifier = Modifier.fillMaxWidth(),
                colors = TextFieldDefaults.colors(
                    unfocusedContainerColor = Color.Transparent,
                    focusedContainerColor = Color.Transparent
                ),
                maxLines = 3
            )
        }
    }
}

// ---- Empty state ----

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

// ---- List + item ----

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
    onDrag: (Offset) -> Unit,
    onDragEnd: (TodoEntity) -> Unit,
    onDragCancel: () -> Unit,
    onHoverTab: (Int?) -> Unit,
    onItemClick: (TodoEntity) -> Unit // --- ВОЗВРАЩЕНО ---
) {
    data class Bounds(val l: Float, val t: Float, val r: Float, val b: Float)

    val itemBounds = remember { mutableStateMapOf<Long, Bounds>() }

    val currentList by rememberUpdatedState(list)
    val currentOnListUpdate by rememberUpdatedState(onListUpdate)
    val currentDragOffset by rememberUpdatedState(dragOffset)
    val currentDraggingId by rememberUpdatedState(draggingId)
    val currentOnDrag by rememberUpdatedState(onDrag)
    val currentOnHoverTab by rememberUpdatedState(onHoverTab)
    val currentOnItemClick by rememberUpdatedState(onItemClick) // --- ВОЗВРАЩЕНО ---

    fun indexOf(id: Long) = currentList.indexOfFirst { it.id == id }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        itemsIndexed(
            items = currentList,
            key = { _, todo -> todo.id }
        ) { _, todo ->
            val isDragging = currentDraggingId == todo.id
            val bounds = itemBounds[todo.id]

            val yOffset = if (isDragging && bounds != null) {
                val centerOfItem = (bounds.t + bounds.b) / 2f
                currentDragOffset.y - centerOfItem
            } else {
                0f
            }

            val elevation by animateDpAsState(
                targetValue = if (isDragging) 8.dp else 2.dp,
                animationSpec = tween(160),
                label = "ItemElevation"
            )

            TodoItemRow(
                todo = todo,
                onToggle = {
                    vm.toggle(todo)
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                },
                onDelete = { vm.delete(todo) },
                onStartTimer = { minutes -> vm.startTimer(todo, minutes) },
                onStopTimer = { vm.stopTimer(todo) },
                onClick = { currentOnItemClick(todo) }, // --- ВОЗВРАЩЕНО ---
                dragging = isDragging,
                modifier = Modifier
                    .graphicsLayer {
                        translationY = yOffset
                    }
                    .zIndex(if (isDragging) 1f else 0f)
                    .shadow(elevation, MaterialTheme.shapes.medium)
                    .animateItemPlacement()
                    .onGloballyPositioned { layoutCoordinates ->
                        val position = layoutCoordinates.positionInRoot()
                        val size = layoutCoordinates.size
                        itemBounds[todo.id] = Bounds(
                            l = position.x,
                            t = position.y,
                            r = position.x + size.width,
                            b = position.y + size.height
                        )
                    }
                    .pointerInput(tabAreas, itemBounds) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = {
                                val b = itemBounds[todo.id]
                                    ?: return@detectDragGesturesAfterLongPress
                                val center = Offset(
                                    x = (b.l + b.r) / 2f,
                                    y = (b.t + b.b) / 2f
                                )
                                onDragStart(todo.id, center)
                            },
                            onDragEnd = { onDragEnd(todo) },
                            onDragCancel = onDragCancel
                        ) { change, dragAmount ->
                            change.consume()

                            if (currentDraggingId != todo.id) return@detectDragGesturesAfterLongPress

                            val newOffset = currentDragOffset + dragAmount
                            currentOnDrag(newOffset)

                            val hovered = tabAreas.entries
                                .firstOrNull { (_, rect) -> rect.contains(newOffset) }
                                ?.key
                            currentOnHoverTab(hovered)

                            val myIndex = indexOf(todo.id)
                            if (myIndex == -1) return@detectDragGesturesAfterLongPress

                            val myCenterY = newOffset.y

                            if (myIndex > 0) {
                                val prev = currentList[myIndex - 1]
                                val prevBounds = itemBounds[prev.id]
                                if (prevBounds != null) {
                                    val prevCenterY = (prevBounds.t + prevBounds.b) / 2f
                                    if (myCenterY < prevCenterY) {
                                        val mutable = currentList.toMutableList()
                                        mutable.removeAt(myIndex)
                                        mutable.add(myIndex - 1, todo)
                                        currentOnListUpdate(mutable)
                                        return@detectDragGesturesAfterLongPress
                                    }
                                }
                            }

                            if (myIndex < currentList.lastIndex) {
                                val next = currentList[myIndex + 1]
                                val nextBounds = itemBounds[next.id]
                                if (nextBounds != null) {
                                    val nextCenterY = (nextBounds.t + nextBounds.b) / 2f
                                    if (myCenterY > nextCenterY) {
                                        val mutable = currentList.toMutableList()
                                        mutable.removeAt(myIndex)
                                        mutable.add(myIndex + 1, todo)
                                        currentOnListUpdate(mutable)
                                    }
                                }
                            }
                        }
                    }
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
    onClick: () -> Unit, // --- ВОЗВРАЩЕНО ---
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
        animationSpec = tween(160),
        label = "ItemContainerColor"
    )

    val isTimerActive = todo.endTimeMillis != null
    val isOverdue = isTimerActive && timeLeft.contains("-")

    Card(
        // --- ВОЗВРАЩЕНО: `clickable` ---
        modifier = modifier
            .fillMaxWidth()
            .clickable(
                enabled = !dragging,
                onClick = onClick
            ),
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
                androidx.compose.material3.Checkbox(
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
                    // --- ВОЗВРАЩЕНО: Отображение `note` ---
                    if (todo.note.isNotBlank()) {
                        Text(
                            text = todo.note,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.alpha(if (todo.isDone) 0.5f else 0.7f),
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    // ---
                }

                if (isTimerActive) {
                    Surface(
                        shape = CircleShape,
                        color = if (isOverdue) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.tertiaryContainer
                        },
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Text(
                            text = timeLeft,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            color = if (isOverdue) {
                                MaterialTheme.colorScheme.onError
                            } else {
                                MaterialTheme.colorScheme.onTertiaryContainer
                            }
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
                        Icon(Icons.Default.Timer, contentDescription = null, modifier = Modifier.size(18.dp))
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
                        Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
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
            icon = { Icon(Icons.Default.Warning, contentDescription = null) },
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

// ---- Timer dialog ----

@Composable
private fun SetTimerDialog(
    onDismiss: () -> Unit,
    onConfirm: (Long) -> Unit
) {
    var minutesText by remember { mutableStateOf("25") }
    val presets = listOf(5, 10, 15, 25, 45, 60)

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Timer, contentDescription = null) },
        title = { Text("Установить таймер") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = minutesText,
                    onValueChange = { raw ->
                        minutesText = raw.filter { ch -> ch.isDigit() }.take(4)
                    },
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

// --- НОВАЯ ФУНКЦИЯ: Диалог редактирования ---
@Composable
private fun EditTaskDialog(
    task: TodoEntity,
    onDismiss: () -> Unit,
    onConfirm: (id: Long, title: String, note: String) -> Unit
) {
    var title by remember(task) { mutableStateOf(task.title) }
    var note by remember(task) { mutableStateOf(task.note) }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Edit, null) },
        title = { Text("Редактировать задачу") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Задача") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Описание") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    maxLines = 8
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (title.isNotBlank()) {
                        onConfirm(task.id, title.trim(), note.trim())
                    }
                },
                enabled = title.isNotBlank()
            ) {
                Text("Сохранить")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Отмена")
            }
        }
    )
}