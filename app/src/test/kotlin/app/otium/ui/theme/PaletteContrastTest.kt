package app.otium.ui.theme

import kotlin.math.pow
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * WCAG 2.x contrast guard for the Earth & Ink palette. The design asks for low-contrast
 * surfaces and shadows only: text and controls must still reach AA (4.5:1 for text, 3:1 for
 * UI components and graphics). Fails with every offending pair listed.
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
        Requirement("body text on background", p.onBackground, p.background, TEXT_AAA),
        Requirement("body text on surface", p.onSurface, p.surface, TEXT_AAA),
        Requirement("body text on raised surface", p.onSurface, p.surfaceContainerHighest, TEXT_AA),
        Requirement("text on variant surface", p.onSurfaceVariant, p.surfaceVariant, TEXT_AA),
        Requirement("on primary (Sage)", p.onPrimary, p.primary, TEXT_AA),
        Requirement("Sage as text on background", p.primary, p.background, TEXT_AA),
        Requirement("Sage as graphic on surface", p.primary, p.surface, GRAPHIC),
        Requirement("on primary container", p.onPrimaryContainer, p.primaryContainer, TEXT_AA),
        Requirement("on secondary (Terra)", p.onSecondary, p.secondary, TEXT_AA),
        Requirement("Terra as text on background", p.secondary, p.background, TEXT_AA),
        Requirement("Terra as graphic on surface", p.secondary, p.surface, GRAPHIC),
        Requirement(
            "on secondary container",
            p.onSecondaryContainer,
            p.secondaryContainer,
            TEXT_AA,
        ),
        Requirement("on tertiary", p.onTertiary, p.tertiary, TEXT_AA),
        Requirement("muted ink as text on background", p.mutedInk, p.background, TEXT_AA),
        Requirement("muted ink as text on surface", p.mutedInk, p.surface, TEXT_AA),
        Requirement("outline on background", p.outline, p.background, GRAPHIC),
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
