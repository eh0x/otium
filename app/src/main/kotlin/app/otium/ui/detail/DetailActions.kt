package app.otium.ui.detail

import androidx.compose.runtime.Immutable

/** Everything the detail screen can ask for. Defaults do nothing, which keeps tests short. */
@Immutable
data class DetailActions(
    val onBack: () -> Unit = {},
    val onProgressConfirmed: (Int) -> Unit = {},
    val onMarkFinished: () -> Unit = {},
    val onTotalPagesConfirmed: (Int) -> Unit = {},
    val onEdit: () -> Unit = {},
    val onDelete: () -> Unit = {},
    val onNoteSaved: (NoteDraft) -> Unit = {},
    val onNoteDeleted: (Long) -> Unit = {},
    val onErrorShown: () -> Unit = {},
)
