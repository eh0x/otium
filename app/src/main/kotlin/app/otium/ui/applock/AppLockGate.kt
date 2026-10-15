package app.otium.ui.applock

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.withResumed
import app.otium.R
import app.otium.applock.LockState
import app.otium.ui.components.LocalContentHidden
import app.otium.ui.components.OtiumButton
import app.otium.ui.components.OtiumDialog
import app.otium.ui.components.OtiumMark
import app.otium.ui.theme.PaperBackground
import app.otium.ui.theme.otiumColors

/**
 * Shows [content] only while the gate is unlocked. Locked or still checking, the content stays
 * composed, so every screen keeps its place, but it is not drawn, not read by TalkBack, cannot
 * take focus, and the screen above it takes every touch. Dialogs, sheets and menus are windows of
 * their own, so they are told to stay out through [LocalContentHidden].
 *
 * The system prompt opens by itself once per prompt request, and only while the activity is
 * resumed, as the prompt needs.
 */
@Composable
fun AppLockGate(
    gate: AppLockGateState,
    onUnlock: () -> Unit,
    onNoticeShown: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val state = gate.state
    val hidden = state != LockState.UNLOCKED
    val focusManager = LocalFocusManager.current
    LaunchedEffect(hidden) { if (hidden) focusManager.clearFocus(force = true) }

    Box(modifier = modifier.fillMaxSize()) {
        Box(modifier = if (hidden) Modifier.hiddenContent() else Modifier) {
            // Windows of their own (dialogs, sheets, menus) ignore the modifiers above.
            CompositionLocalProvider(LocalContentHidden provides hidden) { content() }
        }
        when (state) {
            LockState.CHECKING ->
                PaperBackground(modifier = Modifier.fillMaxSize().blockTouches()) {}

            LockState.LOCKED -> {
                AutoPrompt(gate.promptRequest, onUnlock)
                AppLockScreen(onUnlock = onUnlock, message = gate.unlockMessage)
            }

            LockState.UNLOCKED -> Unit
        }
    }
    if (gate.turnedOffNotice && !hidden) TurnedOffNotice(onNoticeShown)
}

/** The lock screen: the mark, a line saying the library is locked, and the way to unlock. */
@Composable
fun AppLockScreen(onUnlock: () -> Unit, modifier: Modifier = Modifier, message: String? = null) {
    PaperBackground(modifier = modifier.fillMaxSize().blockTouches()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(horizontal = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            OtiumMark(size = 56.dp)
            Text(
                text = stringResource(R.string.app_lock_locked_title),
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                text = message ?: stringResource(R.string.app_lock_locked_note),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.otiumColors.muted,
                textAlign = TextAlign.Center,
            )
            OtiumButton(
                text = stringResource(R.string.app_lock_unlock),
                onClick = onUnlock,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun AutoPrompt(promptRequest: Int, onUnlock: () -> Unit) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val currentOnUnlock by rememberUpdatedState(onUnlock)
    LaunchedEffect(promptRequest) {
        if (promptRequest > 0) lifecycle.withResumed { currentOnUnlock() }
    }
}

@Composable
private fun TurnedOffNotice(onDismiss: () -> Unit) {
    OtiumDialog(
        title = stringResource(R.string.app_lock_turned_off_title),
        body = stringResource(R.string.app_lock_turned_off_body),
        onDismissRequest = onDismiss,
        actions = { OtiumButton(text = stringResource(R.string.app_lock_ok), onClick = onDismiss) },
    )
}

/** Not drawn, not announced, and not reachable by keyboard focus. */
private fun Modifier.hiddenContent(): Modifier = this
    .alpha(0f)
    .clearAndSetSemantics {}
    .focusProperties { onEnter = { cancelFocusChange() } }
    .focusGroup()

/** Takes every touch, so nothing reaches the hidden screens below. */
private fun Modifier.blockTouches(): Modifier = pointerInput(Unit) {
    awaitPointerEventScope {
        while (true) awaitPointerEvent().changes.forEach { it.consume() }
    }
}
