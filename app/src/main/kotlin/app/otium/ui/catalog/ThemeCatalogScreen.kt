package app.otium.ui.catalog

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.otium.domain.model.BookStatus
import app.otium.ui.components.BookCover
import app.otium.ui.components.OtiumProgressBar
import app.otium.ui.components.OtiumTopBar
import app.otium.ui.components.OtiumWordmark
import app.otium.ui.components.ShelfRow
import app.otium.ui.components.StatusChip
import app.otium.ui.theme.OtiumTheme
import app.otium.ui.theme.PaperBackground
import app.otium.ui.theme.otiumColors

private const val SWATCHES_PER_ROW = 3
private val SHELF_TITLES = listOf("The Hobbit", "Dune", "Cien años de soledad", "Middlemarch")
private val PROGRESS_SAMPLES = listOf(0f, 0.42f, 1f)

/**
 * Developer-facing catalog of every theme token and shared component, for checking light and
 * dark at a glance. Shown instead of the app content in debug builds until the real screens
 * exist. Labels are literals on purpose: this is a tool, not a user-facing screen.
 */
@Composable
fun ThemeCatalogScreen(modifier: Modifier = Modifier) {
    PaperBackground(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .verticalScroll(rememberScrollState())
                .padding(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            OtiumWordmark(modifier = Modifier.padding(horizontal = 16.dp))
            OtiumTopBar(title = "Library")
            Section("Colors") { ColorSwatches() }
            Section("Typography") { TypeSamples() }
            Section("Status") { StatusSamples() }
            Section("Progress") { ProgressSamples() }
            ShelfSample()
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier.padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(text = title, style = MaterialTheme.typography.headlineSmall)
        content()
    }
}

@Composable
private fun ColorSwatches() {
    val scheme = MaterialTheme.colorScheme
    val otium = MaterialTheme.otiumColors
    val swatches = listOf(
        "background" to scheme.background,
        "surface" to scheme.surface,
        "sheet" to otium.sheet,
        "ink" to scheme.onBackground,
        "muted" to otium.muted,
        "divider" to otium.divider,
        "accent" to otium.accent,
        "primary (gold)" to scheme.primary,
        "primaryContainer" to scheme.primaryContainer,
        "error" to scheme.error,
        "cover text" to otium.cover.text,
        "cover board" to otium.cover.boards.first(),
    )
    swatches.chunked(SWATCHES_PER_ROW).forEach { row ->
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            row.forEach { (name, color) -> Swatch(name, color, Modifier.weight(1f)) }
        }
    }
}

@Composable
private fun Swatch(name: String, color: Color, modifier: Modifier = Modifier) {
    val outline = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .background(color, MaterialTheme.shapes.small)
                .border(outline, MaterialTheme.shapes.small),
        )
        Text(text = name, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun TypeSamples() {
    val type = MaterialTheme.typography
    val styles: List<Pair<String, TextStyle>> = listOf(
        "displaySmall" to type.displaySmall,
        "headlineLarge" to type.headlineLarge,
        "headlineMedium" to type.headlineMedium,
        "headlineSmall" to type.headlineSmall,
        "titleLarge" to type.titleLarge,
        "titleMedium" to type.titleMedium,
        "bodyLarge" to type.bodyLarge,
        "bodyMedium" to type.bodyMedium,
        "labelLarge" to type.labelLarge,
        "labelSmall" to type.labelSmall,
    )
    styles.forEach { (name, style) ->
        Text(text = "$name  A quiet room at the end of the day", style = style)
    }
}

@Composable
private fun StatusSamples() {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        BookStatus.entries.forEach { StatusChip(status = it) }
    }
}

@Composable
private fun ProgressSamples() {
    PROGRESS_SAMPLES.forEach { OtiumProgressBar(progress = it) }
}

@Composable
private fun ShelfSample() {
    ShelfRow {
        SHELF_TITLES.forEach { title -> BookCover(title = title, modifier = Modifier.weight(1f)) }
    }
}

@Preview(name = "Light", widthDp = 411, heightDp = 1800)
@Composable
private fun ThemeCatalogLightPreview() {
    OtiumTheme(darkTheme = false) { ThemeCatalogScreen() }
}

@Preview(name = "Dark", widthDp = 411, heightDp = 1800)
@Composable
private fun ThemeCatalogDarkPreview() {
    OtiumTheme(darkTheme = true) { ThemeCatalogScreen() }
}
