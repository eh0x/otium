package app.otium.ui.add.search

import androidx.compose.runtime.Immutable
import app.otium.domain.model.BookCandidate

/** Everything the title search screen can ask for. Defaults do nothing, which keeps tests short. */
@Immutable
data class TitleSearchActions(
    val onBack: () -> Unit = {},
    val onQueryChange: (String) -> Unit = {},
    val onSearch: () -> Unit = {},
    val onResultChosen: (BookCandidate) -> Unit = {},
    val onEnterManually: () -> Unit = {},
    val onErrorShown: () -> Unit = {},
)
