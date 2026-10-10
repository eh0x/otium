package app.otium.ui.theme

import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

/** Durations and easing for the whole app. Fades stay when motion is reduced; movement does not. */
object Motion {
    /** Screen changes, such as opening a book. */
    const val SCREEN_FADE_MILLIS = 220

    /** Dialogs and menus. */
    const val DIALOG_FADE_MILLIS = 150

    /** The scrim behind sheets and dialogs. */
    const val SCRIM_FADE_MILLIS = 200

    /** Bottom sheets rising into place. */
    const val SHEET_ENTER_MILLIS = 280

    /** Toasts sliding up. */
    const val TOAST_ENTER_MILLIS = 220

    /** How long a toast stays before it dismisses itself. */
    const val TOAST_VISIBLE_MILLIS = 2400L

    /** The switch knob sliding across. */
    const val SWITCH_MILLIS = 180

    /** Progress fills growing to a new value. */
    const val PROGRESS_MILLIS = 400

    /** How far a sheet travels as it enters. */
    val SheetEnterOffset = 40.dp

    /** A quick start that settles gently: cubic-bezier(.2, .8, .2, 1). */
    val SettleEasing: Easing = CubicBezierEasing(0.2f, 0.8f, 0.2f, 1f)
}

/** The system "remove animations" setting sets the animator duration scale to zero. */
internal fun isMotionReduced(animatorDurationScale: Float): Boolean = animatorDurationScale == 0f

/**
 * True when the user turned animations off in system settings. Follows changes while the
 * screen is showing. Use it to skip movement (see [otiumMovement]); fades stay.
 */
@Composable
fun rememberReducedMotion(): Boolean {
    val context = LocalContext.current
    val resolver = context.contentResolver

    fun read() = isMotionReduced(
        Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f),
    )

    var reduced by remember { mutableStateOf(read()) }
    DisposableEffect(resolver) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                reduced = read()
            }
        }
        resolver.registerContentObserver(
            Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE),
            false,
            observer,
        )
        onDispose { resolver.unregisterContentObserver(observer) }
    }
    return reduced
}

/** A gentle fade. Kept when motion is reduced, since it moves nothing on screen. */
fun <T> otiumFade(durationMillis: Int = Motion.SCREEN_FADE_MILLIS): TweenSpec<T> =
    tween(durationMillis = durationMillis, easing = LinearEasing)

/** Movement: slides, growing fills, sliding knobs. Instant when motion is reduced. */
fun <T> otiumMovement(
    reducedMotion: Boolean,
    durationMillis: Int,
    easing: Easing = Motion.SettleEasing,
): TweenSpec<T> = tween(durationMillis = if (reducedMotion) 0 else durationMillis, easing = easing)
