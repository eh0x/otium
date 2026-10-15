package app.otium.ui.components

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * True while the app lock hides the screens. Drawing nothing is not enough for a dialog, a sheet
 * or a menu: each is a window of its own, above the screen, so it would stay readable (and a half
 * typed note or passphrase with it) over the lock screen. Anything that opens a window must not
 * compose it while this is true; its caller's state is kept, so it returns after unlocking.
 */
val LocalContentHidden = staticCompositionLocalOf { false }
