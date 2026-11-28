package com.memora.app.ui.components

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.statusBarsIgnoringVisibility
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.ViewCompat
import androidx.compose.ui.unit.Dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.memora.app.ui.NoteStatusBarPolicy
import kotlinx.coroutines.flow.collect

/** The top gap is INSIDE the document; it must not shrink when the system bar hides. */
@Composable
fun stableNoteStatusInset(): Dp {
    val view = LocalView.current
    val insets = ViewCompat.getRootWindowInsets(view)
    val statusBarHeightPx =
        insets?.getInsetsIgnoringVisibility(WindowInsetsCompat.Type.statusBars())?.top
            ?: run {
                val resourceId = view.resources.getIdentifier("status_bar_height", "dimen", "android")
                if (resourceId != 0) view.resources.getDimensionPixelSize(resourceId) else 0
            }
    return with(LocalDensity.current) { statusBarHeightPx.toDp() }
}

private tailrec fun Context.findHostActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findHostActivity()
    else -> null
}

/** F-09: hide only after reaching the top edge; show on returning to the beginning. */
@Composable
fun NoteStatusBarEffect(scroll: ScrollState) {
    val activity = LocalContext.current.findHostActivity() ?: return
    val view = LocalView.current
    val density = LocalDensity.current
    val hideAt = with(density) { 68.dp.roundToPx() }
    val restoreAt = with(density) { 12.dp.roundToPx() }
    val controller = remember(activity, view) { WindowCompat.getInsetsController(activity.window, view) }

    DisposableEffect(activity, view) {
        onDispose { controller.show(WindowInsetsCompat.Type.statusBars()) }
    }

    LaunchedEffect(scroll, hideAt, restoreAt, controller) {
        val policy = NoteStatusBarPolicy(hideAt, restoreAt)
        var previouslyHidden: Boolean? = null
        snapshotFlow { scroll.value }.collect { position ->
            val hidden = policy.update(position)
            if (previouslyHidden != hidden) {
                if (hidden) controller.hide(WindowInsetsCompat.Type.statusBars())
                else controller.show(WindowInsetsCompat.Type.statusBars())
                previouslyHidden = hidden
            }
        }
    }
}

/** The first list item is the initial scrollable top inset; do not change the viewport. */
@Composable
fun NoteStatusBarEffect(scroll: LazyListState) {
    val activity = LocalContext.current.findHostActivity() ?: return
    val view = LocalView.current
    val density = LocalDensity.current
    val hideAt = with(density) { 68.dp.roundToPx() }
    val restoreAt = with(density) { 12.dp.roundToPx() }
    val controller = remember(activity, view) { WindowCompat.getInsetsController(activity.window, view) }
    DisposableEffect(activity, view) {
        onDispose { controller.show(WindowInsetsCompat.Type.statusBars()) }
    }
    LaunchedEffect(scroll, hideAt, restoreAt, controller) {
        val policy = NoteStatusBarPolicy(hideAt, restoreAt)
        var lastHidden: Boolean? = null
        snapshotFlow { scroll.firstVisibleItemIndex * 100_000 + scroll.firstVisibleItemScrollOffset }
            .collect { position ->
                val hidden = policy.update(position)
                if (lastHidden != hidden) {
                    if (hidden) controller.hide(WindowInsetsCompat.Type.statusBars())
                    else controller.show(WindowInsetsCompat.Type.statusBars())
                    lastHidden = hidden
                }
            }
    }
}
