package app.otium.ui.add.scan

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.otium.R
import app.otium.ui.add.AddDestination
import app.otium.ui.common.messageRes
import app.otium.ui.components.OtiumTopBar
import app.otium.ui.theme.PaperBackground

private val FRAME_WIDTH = 280.dp
private val FRAME_HEIGHT = 160.dp

/**
 * Connects the scan screen to its ViewModel, the camera permission and the camera itself.
 * [onEnterIsbn] is the way out when the camera cannot be used.
 */
@Composable
fun ScanScreen(
    onBack: () -> Unit,
    onEnterIsbn: () -> Unit,
    onDestination: (AddDestination) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ScanViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val currentOnDestination by rememberUpdatedState(onDestination)
    LaunchedEffect(state.destination) {
        state.destination?.let {
            currentOnDestination(it)
            viewModel.onNavigated()
        }
    }
    val haptics = LocalHapticFeedback.current
    LaunchedEffect(state.reads) {
        if (state.reads > 0) haptics.performHapticFeedback(HapticFeedbackType.Confirm)
    }
    val camera = rememberCameraPermission()
    val context = LocalContext.current
    val actions = remember(viewModel, onBack, onEnterIsbn, camera) {
        ScanActions(
            onBack = onBack,
            onEnterIsbn = onEnterIsbn,
            onAllowCamera = camera.request,
            onOpenSettings = { context.openAppSettings() },
            onScanAgain = viewModel::onScanAgain,
            onSaveForLater = viewModel::onSaveForLater,
            onEnterManually = viewModel::onEnterManually,
        )
    }
    ScanContent(state = state, actions = actions, access = camera.access, modifier = modifier) {
        CameraPreview(analyzing = it, modifier = Modifier.fillMaxSize())
    }
}

/**
 * The scan screen as a pure function of its state. [preview] draws the camera picture and is
 * told whether frames should be decoded; tests pass a stand-in.
 */
@Composable
fun ScanContent(
    state: ScanUiState,
    actions: ScanActions,
    access: CameraAccess,
    modifier: Modifier = Modifier,
    preview: @Composable (analyzing: Boolean) -> Unit,
) {
    PaperBackground(modifier = modifier.fillMaxSize()) {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                OtiumTopBar(
                    title = stringResource(R.string.scan_title),
                    navigationIcon = {
                        IconButton(onClick = actions.onBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.detail_back),
                            )
                        }
                    },
                )
            },
        ) { padding ->
            Box(modifier = Modifier.padding(padding).fillMaxSize()) {
                if (access == CameraAccess.GRANTED) {
                    preview(state.phase == ScanPhase.SCANNING)
                    Viewfinder(modifier = Modifier.align(Alignment.Center))
                    ScanStatus(state, actions, modifier = Modifier.align(Alignment.BottomCenter))
                } else {
                    NoCamera(access, actions, modifier = Modifier.align(Alignment.Center))
                }
            }
        }
    }
}

/** A quiet frame showing where the barcode should go. */
@Composable
private fun Viewfinder(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(FRAME_WIDTH, FRAME_HEIGHT)
            .border(2.dp, MaterialTheme.colorScheme.primary, MaterialTheme.shapes.medium),
    )
}

/** What happens with the read: a hint while scanning, progress, or a failure and the ways out. */
@Composable
private fun ScanStatus(state: ScanUiState, actions: ScanActions, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        shape = MaterialTheme.shapes.large,
        tonalElevation = 2.dp,
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .semantics { liveRegion = LiveRegionMode.Polite },
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            when (state.phase) {
                ScanPhase.SCANNING -> {
                    Text(
                        stringResource(R.string.scan_hint),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    TextButton(onClick = actions.onEnterIsbn) {
                        Text(stringResource(R.string.scan_enter_isbn))
                    }
                }

                ScanPhase.LOOKING_UP -> {
                    Text(
                        text = stringResource(R.string.scan_looking_up, state.isbn13.orEmpty()),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }

                ScanPhase.FAILED -> ScanFailure(state, actions)
            }
        }
    }
}

@Composable
private fun ScanFailure(state: ScanUiState, actions: ScanActions) {
    state.error?.let {
        Text(
            text = stringResource(it.messageRes()),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.error,
        )
    }
    Button(onClick = actions.onScanAgain, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.scan_again))
    }
    if (state.canSaveForLater) {
        OutlinedButton(onClick = actions.onSaveForLater, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.isbn_save_for_later))
        }
    }
    TextButton(onClick = actions.onEnterManually) {
        Text(stringResource(R.string.add_enter_manually))
    }
}

/** Why the camera cannot be used, what would fix it, and the way to carry on without it. */
@Composable
private fun NoCamera(access: CameraAccess, actions: ScanActions, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val message = when (access) {
            CameraAccess.BLOCKED -> R.string.scan_camera_settings
            CameraAccess.NO_CAMERA -> R.string.scan_no_camera
            else -> R.string.scan_camera_rationale
        }
        Text(stringResource(message), style = MaterialTheme.typography.bodyLarge)
        when (access) {
            CameraAccess.ASK -> Button(onClick = actions.onAllowCamera) {
                Text(stringResource(R.string.scan_camera_allow))
            }

            CameraAccess.BLOCKED -> Button(onClick = actions.onOpenSettings) {
                Text(stringResource(R.string.scan_open_settings))
            }

            else -> Unit
        }
        OutlinedButton(onClick = actions.onEnterIsbn) {
            Text(stringResource(R.string.scan_enter_isbn))
        }
    }
}

/** The camera permission as the screen needs it: the current [access] and a way to ask. */
private class CameraPermission(val access: CameraAccess, val request: () -> Unit)

/**
 * Asks for the camera once when the screen first opens, and checks again whenever the screen
 * resumes, so turning access on in the settings takes effect on return.
 */
@Composable
private fun rememberCameraPermission(): CameraPermission {
    val context = LocalContext.current
    val activity = LocalActivity.current
    val hasCamera = remember(context) {
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)
    }
    var granted by remember { mutableStateOf(context.hasCameraPermission()) }
    var blocked by rememberSaveable { mutableStateOf(false) }
    var asked by rememberSaveable { mutableStateOf(false) }
    val launcher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
            granted = ok
            // Refused without the system offering to explain again: only settings can help now.
            blocked = !ok && activity != null &&
                !ActivityCompat.shouldShowRequestPermissionRationale(
                    activity,
                    Manifest.permission.CAMERA,
                )
        }
    LaunchedEffect(hasCamera) {
        if (hasCamera && !granted && !asked) {
            asked = true
            launcher.launch(Manifest.permission.CAMERA)
        }
    }
    LifecycleResumeEffect(context) {
        granted = context.hasCameraPermission()
        if (granted) blocked = false
        onPauseOrDispose {}
    }
    val access = when {
        !hasCamera -> CameraAccess.NO_CAMERA
        granted -> CameraAccess.GRANTED
        blocked -> CameraAccess.BLOCKED
        else -> CameraAccess.ASK
    }
    return remember(access) {
        CameraPermission(access) { launcher.launch(Manifest.permission.CAMERA) }
    }
}

private fun Context.hasCameraPermission() =
    ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
        PackageManager.PERMISSION_GRANTED

private fun Context.openAppSettings() = startActivity(
    Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.fromParts("package", packageName, null),
    )
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
)
