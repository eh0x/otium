package app.otium.ui.add.confirm

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import app.otium.R
import app.otium.domain.model.CoverChoice
import app.otium.ui.components.BookCover
import coil3.compose.rememberAsyncImagePainter
import java.io.File

private val PREVIEW_WIDTH = 140.dp
private val OPTION_WIDTH = 64.dp
private const val CAMERA_DIRECTORY = "camera"

/**
 * The cover the book will get, the covers the services offer, and ways to use a photo instead.
 * Photos are only referenced here; they are stored when the book is saved.
 */
@Composable
internal fun CoverSection(
    title: String,
    state: ConfirmUiState,
    onCoverSelected: (CoverChoice) -> Unit,
    onCameraDenied: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val pickers = rememberCoverPickers(
        onPicked = { onCoverSelected(CoverChoice.Picked(it)) },
        onCameraDenied = onCameraDenied,
    )
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val model = state.cover.previewModel(state.currentCoverPath)
        BookCover(
            title = title,
            painter = model?.let { rememberAsyncImagePainter(it) },
            modifier = Modifier.width(PREVIEW_WIDTH),
        )
        if (state.remoteCovers.size > 1) {
            RemoteCoverOptions(state.remoteCovers, state.cover, title, onCoverSelected)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            TextButton(onClick = pickers.pickFromGallery) {
                Text(stringResource(R.string.confirm_cover_gallery))
            }
            TextButton(onClick = pickers.takePhoto) {
                Text(stringResource(R.string.confirm_cover_camera))
            }
            TextButton(onClick = { onCoverSelected(CoverChoice.None) }) {
                Text(stringResource(R.string.confirm_cover_remove))
            }
        }
    }
}

@Composable
private fun RemoteCoverOptions(
    urls: List<String>,
    selected: CoverChoice,
    title: String,
    onCoverSelected: (CoverChoice) -> Unit,
) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        itemsIndexed(urls, key = { _, url -> url }) { index, url ->
            val choice = CoverChoice.Remote(url)
            val isSelected = choice == selected
            val label = stringResource(R.string.confirm_cover_option, index + 1)
            val outline = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent
            BookCover(
                title = title,
                painter = rememberAsyncImagePainter(url),
                modifier = Modifier
                    .width(OPTION_WIDTH)
                    .border(width = 2.dp, color = outline, shape = MaterialTheme.shapes.small)
                    .clickable(role = Role.RadioButton) { onCoverSelected(choice) }
                    .semantics(mergeDescendants = true) {
                        contentDescription = label
                        this.selected = isSelected
                    },
            )
        }
    }
}

/** What the preview loads: a remote URL, a picked photo's URI, or the book's stored file. */
private fun CoverChoice.previewModel(currentPath: String?): Any? = when (this) {
    CoverChoice.None -> null
    CoverChoice.Unchanged -> currentPath?.let(::File)
    is CoverChoice.Remote -> url
    is CoverChoice.Picked -> uri
}

/** Starts the system photo picker or the camera app, reporting the photo's URI. */
private class CoverPickers(val pickFromGallery: () -> Unit, val takePhoto: () -> Unit)

@Composable
private fun rememberCoverPickers(
    onPicked: (String) -> Unit,
    onCameraDenied: () -> Unit,
): CoverPickers {
    val context = LocalContext.current
    val currentOnPicked by rememberUpdatedState(onPicked)
    val currentOnDenied by rememberUpdatedState(onCameraDenied)
    // Survives the camera app taking over the screen, and process death while it is open.
    var pendingPhoto by rememberSaveable { mutableStateOf<String?>(null) }

    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) {
        if (it != null) currentOnPicked(it.toString())
    }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { taken ->
        pendingPhoto?.takeIf { taken }?.let(currentOnPicked)
        pendingPhoto = null
    }
    val launchCamera = {
        val uri = context.newPhotoUri()
        pendingPhoto = uri.toString()
        camera.launch(uri)
    }
    val permission =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) launchCamera() else currentOnDenied()
        }

    return CoverPickers(
        pickFromGallery = {
            gallery.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
            )
        },
        takePhoto = {
            val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
            if (granted) launchCamera() else permission.launch(Manifest.permission.CAMERA)
        },
    )
}

/**
 * A fresh file in the shared camera folder (see res/xml/file_paths.xml) for the camera app.
 * `CameraPhotoFiles` deletes these by folder and name, so keep both in step with it.
 */
private fun Context.newPhotoUri() = FileProvider.getUriForFile(
    this,
    "$packageName.covers",
    File(File(cacheDir, CAMERA_DIRECTORY).apply { mkdirs() }, "cover-${System.nanoTime()}.jpg"),
)
