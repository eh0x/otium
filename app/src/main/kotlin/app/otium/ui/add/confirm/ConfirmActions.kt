package app.otium.ui.add.confirm

import androidx.compose.runtime.Immutable
import app.otium.domain.model.CoverChoice

/** Everything the confirm/edit screen can ask for. Defaults do nothing, which keeps tests short. */
@Immutable
data class ConfirmActions(
    val onBack: () -> Unit = {},
    val onFormChange: (BookForm) -> Unit = {},
    val onCoverSelected: (CoverChoice) -> Unit = {},
    val onSave: () -> Unit = {},
    val onOpenBook: (Long) -> Unit = {},
    val onDuplicateDismissed: () -> Unit = {},
    val onErrorShown: () -> Unit = {},
)
