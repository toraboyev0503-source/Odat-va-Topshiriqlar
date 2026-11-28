package com.memora.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBarsIgnoringVisibility
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.vector.ImageVector
import kotlinx.coroutines.flow.collect
import androidx.compose.runtime.snapshotFlow
import com.memora.app.ui.NoteChromeScrollPolicy

/**
 * Full Canvas policy (F-01...F-08): the document is drawn under the status bar, but
 * controls remain below the system icons. All padding for the initial title belongs
 * to the scrollable document, NOT to the viewport or navigation host.
 */
object FullCanvasNotePolicy {
    const val SCROLL_THRESHOLD_DP = 16
    const val CHROME_SHOW_MS = 170
    const val CHROME_HIDE_MS = 140
    const val ACTION_DIAMETER_DP = 48
}

/** Detect real scroll direction with accumulated movement rather than per-frame jitter. */
@Composable
fun rememberNoteChromeVisibility(scroll: ScrollState): MutableState<Boolean> {
    val state = remember(scroll) { mutableStateOf(true) }
    val thresholdPx = with(LocalDensity.current) { FullCanvasNotePolicy.SCROLL_THRESHOLD_DP.dp.toPx() }
    LaunchedEffect(scroll, thresholdPx) {
        val policy = NoteChromeScrollPolicy(thresholdPx)
        snapshotFlow { scroll.value }.collect { current ->
            policy.update(current)?.let { state.value = it }
        }
    }
    return state
}

/** Reader uses virtualized blocks; the same direction policy works with list scroll. */
@Composable
fun rememberNoteChromeVisibility(scroll: LazyListState): MutableState<Boolean> {
    val state = remember(scroll) { mutableStateOf(true) }
    val thresholdPx = with(LocalDensity.current) { FullCanvasNotePolicy.SCROLL_THRESHOLD_DP.dp.toPx() }
    LaunchedEffect(scroll, thresholdPx) {
        val policy = NoteChromeScrollPolicy(thresholdPx)
        snapshotFlow { scroll.firstVisibleItemIndex * 100_000 + scroll.firstVisibleItemScrollOffset }
            .collect { current -> policy.update(current)?.let { state.value = it } }
    }
    return state
}

/**
 * Observes taps without consuming pointer changes. This is especially important in
 * Editor: one tap must still place the cursor, select text, and open the IME.
 */
fun Modifier.showNoteChromeOnTap(show: () -> Unit): Modifier = this.pointerInput(Unit) {
    awaitEachGesture {
        val first = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        val start = first.position
        var isTap = true
        var pressed = true
        while (pressed) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            val currentPointer = event.changes.firstOrNull { it.id == first.id }
            if (currentPointer == null) {
                isTap = false
                break
            }
            if ((currentPointer.position - start).getDistance() > viewConfiguration.touchSlop) isTap = false
            if (event.changes.size > 1) isTap = false
            pressed = currentPointer.pressed
        }
        if (isTap) show()
    }
}

@Composable
fun FloatingNoteControl(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.size(FullCanvasNotePolicy.ACTION_DIAMETER_DP.dp),
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = CircleShape,
        shadowElevation = 2.dp,
        tonalElevation = 1.dp
    ) {
        IconButton(onClick = onClick, modifier = Modifier.size(FullCanvasNotePolicy.ACTION_DIAMETER_DP.dp)) {
            Icon(icon, contentDescription = description, modifier = Modifier.size(21.dp))
        }
    }
}

/** Top inset applies only to floating controls. No fixed top bar in the document. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun FloatingNoteChrome(
    visible: Boolean,
    backLabel: String,
    onBack: () -> Unit,
    actions: @Composable RowScope.() -> Unit
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(FullCanvasNotePolicy.CHROME_SHOW_MS)),
        exit = fadeOut(tween(FullCanvasNotePolicy.CHROME_HIDE_MS))
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = stableNoteStatusInset()).padding(start = 16.dp, end = 16.dp, top = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FloatingNoteControl(Icons.Rounded.ArrowBack, backLabel, onBack)
            Spacer(Modifier.weight(1f))
            actions()
        }
    }
}
