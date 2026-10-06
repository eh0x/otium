package app.otium.ui.add.isbn

import androidx.compose.runtime.Immutable

/** Everything the ISBN entry screen can ask for. Defaults do nothing, which keeps tests short. */
@Immutable
data class IsbnEntryActions(
    val onBack: () -> Unit = {},
    val onInputChange: (String) -> Unit = {},
    val onLookUp: () -> Unit = {},
    val onSaveForLater: () -> Unit = {},
    val onEnterManually: () -> Unit = {},
)
