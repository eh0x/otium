package app.otium.ui.theme

import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext

private const val FADE_MILLIS = 400

/** The system "remove animations" setting sets the animator duration scale to zero. */
internal fun isMotionReduced(animatorDurationScale: Float): Boolean = animatorDurationScale == 0f

/**
 * True when the user turned animations off in system settings. Follows changes while the
 * screen is showing. Use it to skip or shorten motion (see [otiumFade]).
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

/** Slow, gentle fade. Instant when motion is reduced. */
fun <T> otiumFade(reducedMotion: Boolean): TweenSpec<T> =
    tween(durationMillis = if (reducedMotion) 0 else FADE_MILLIS)
