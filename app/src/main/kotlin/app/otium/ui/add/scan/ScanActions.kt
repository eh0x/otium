package app.otium.ui.add.scan

import androidx.compose.runtime.Immutable

/** Everything the scan screen can ask for. Defaults do nothing, which keeps tests short. */
@Immutable
data class ScanActions(
    val onBack: () -> Unit = {},
    val onEnterIsbn: () -> Unit = {},
    val onAllowCamera: () -> Unit = {},
    val onOpenSettings: () -> Unit = {},
    val onScanAgain: () -> Unit = {},
    val onSaveForLater: () -> Unit = {},
    val onEnterManually: () -> Unit = {},
)

/** Whether the camera can be used, and if not, what the user can do about it. */
enum class CameraAccess {
    GRANTED,

    /** Not granted yet; asking (again) may work. */
    ASK,

    /** Refused for good; only the app settings can change it. */
    BLOCKED,

    /** The device has no camera at all. */
    NO_CAMERA,
}
