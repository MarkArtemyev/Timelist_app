package com.example.todotimerapp2.ui

import androidx.compose.runtime.*
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.seconds

@Composable
fun rememberRemainingText(endTimeMillis: Long?): String {
    if (endTimeMillis == null) return "—"
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }

    LaunchedEffect(endTimeMillis) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1.seconds)
        }
    }

    val diff = endTimeMillis - now
    if (diff <= 0) return "00:00"
    val totalSec = diff / 1000
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) "%02d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
}
