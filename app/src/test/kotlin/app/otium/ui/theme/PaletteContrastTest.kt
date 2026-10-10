package app.otium.ui.theme

import kotlin.math.pow
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * WCAG 2.x contrast guard for the Classical palette. Hairlines and surfaces are deliberately
 * low-contrast; text and controls must still reach AA (4.5:1 for text, 3:1 for graphics such as
 * the accent strokes and glyphs). Fails with every offending pair listed.
 */
class PaletteContrastTest {

    private fun channel(value: Int): Double {
        val c = value / 255.0
        return if (c <= 0.03928) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
    }

    private fun luminance(argb: Long): Double {
        val r = channel(((argb shr 16) and 0xFF).toInt())
        val g = channel(((argb shr 8) and 0xFF).toInt())
        val b = channel((argb and 0xFF).toInt())
        return 0.2126 * r + 0.7152 * g + 0.0722 * b
    }

    private fun contrast(a: Long, b: Long): Double {
        val first = luminance(a)
        val second = luminance(b)
        return (maxOf(first, second) + 0.05) / (minOf(first, second) + 0.05)
    }

    private data class Requirement(
        val name: String,
        val foreground: Long,
        val background: Long,
        val minimum: Double,
    )

    private fun requirements(p: OtiumPaletteSpec) = listOf(
        Requirement("ink on background", p.onBackground, p.background, TEXT_AAA),
        Requirement("ink on surface", p.onSurface, p.surface, TEXT_AAA),
        Requirement("ink on sheet", p.onSurface, p.sheet, TEXT_AAA),
        Requirement("muted on background", p.muted, p.background, TEXT_AA),
        Requirement("muted on surface", p.muted, p.surface, TEXT_AA),
        Requirement("muted on sheet", p.muted, p.sheet, TEXT_AA),
        Requirement("gold text on background", p.primary, p.background, TEXT_AA),
        Requirement("gold text on surface", p.primary, p.surface, TEXT_AA),
        Requirement("gold text on sheet", p.primary, p.sheet, TEXT_AA),
        Requirement(
            "gold text on selected chip",
            p.onPrimaryContainer,
            p.primaryContainer,
            TEXT_AA,
        ),
        Requirement("on primary", p.onPrimary, p.primary, TEXT_AA),
        Requirement("accent graphic on background", p.accent, p.background, GRAPHIC),
        Requirement("accent graphic on sheet", p.accent, p.sheet, GRAPHIC),
        Requirement("muted graphic on background", p.muted, p.background, GRAPHIC),
        Requirement("toast text (background on ink)", p.background, p.onBackground, TEXT_AA),
        Requirement("error on background", p.error, p.background, TEXT_AA),
        Requirement("on error", p.onError, p.error, TEXT_AA),
    )

    private fun verify(label: String, palette: OtiumPaletteSpec) {
        val failures = requirements(palette).mapNotNull { requirement ->
            val ratio = contrast(requirement.foreground, requirement.background)
            if (ratio < requirement.minimum) {
                "%s: %.2f:1 (needs %.1f:1)".format(requirement.name, ratio, requirement.minimum)
            } else {
                null
            }
        }

        assertTrue("$label palette fails WCAG:\n${failures.joinToString("\n")}", failures.isEmpty())
    }

    @Test
    fun `light palette meets WCAG AA`() = verify("light", LightPalette)

    @Test
    fun `dark palette meets WCAG AA`() = verify("dark", DarkPalette)

    @Test
    fun `cover text is readable on every generated board`() {
        val failures = CoverBoards.mapNotNull { board ->
            val ratio = contrast(COVER_TEXT, board)
            if (ratio < TEXT_AA) "board %08X: %.2f:1".format(board, ratio) else null
        }

        assertTrue("cover boards fail WCAG:\n${failures.joinToString("\n")}", failures.isEmpty())
    }

    @Test
    fun `dark theme is a warm dark rather than pure black`() {
        assertTrue(DarkPalette.background != 0xFF000000L)
        val red = (DarkPalette.background shr 16) and 0xFF
        val blue = DarkPalette.background and 0xFF
        assertTrue("dark background should lean warm (red above blue)", red > blue)
    }

    private companion object {
        const val TEXT_AAA = 7.0
        const val TEXT_AA = 4.5
        const val GRAPHIC = 3.0
    }
}
