package app.otium.architecture

import com.lemonappdev.konsist.api.Konsist
import com.lemonappdev.konsist.api.declaration.KoFileDeclaration
import com.lemonappdev.konsist.api.ext.list.withNameEndingWith
import com.lemonappdev.konsist.api.verify.assertFalse
import com.lemonappdev.konsist.api.verify.assertTrue
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Executable form of the architecture rules in docs/STYLE_GUIDE.md section 3, which follow
 * https://developer.android.com/topic/architecture/recommendations.
 *
 * Several rules concern the UI layer, which arrives in Phase 3. Until then they check nothing
 * (see [verifyEach]) and start applying as soon as matching code exists.
 */
class ArchitectureTest {

    private val appSources = Konsist.scopeFromProduction(moduleName = "app")
    private val domainSources = Konsist.scopeFromProduction(moduleName = "domain")

    private fun KoFileDeclaration.importsFrom(vararg prefixes: String) =
        imports.any { import -> prefixes.any { import.name.startsWith(it) } }

    /** Runs [rule] against each element, passing trivially while there are none yet. */
    private fun <T> verifyEach(elements: List<T>, rule: (T) -> Boolean) {
        elements.forEach { assertTrue("Rule violated by: $it", rule(it)) }
    }

    // --- Layering ---

    @Test
    fun `the domain module sees no Android or framework types`() {
        val files = domainSources.files
        assertTrue("domain sources were not found", files.isNotEmpty())

        files.assertFalse {
            it.importsFrom(
                "android.",
                "androidx.",
                "dagger.",
                "javax.inject",
                "retrofit2.",
                "okhttp3.",
            )
        }
    }

    @Test
    fun `the UI layer does not reach into data or storage and network libraries`() {
        val ui = appSources.files.filter { it.packagee?.name?.startsWith("app.otium.ui") == true }

        verifyEach(ui) {
            !it.importsFrom(
                "app.otium.data.",
                "androidx.room.",
                "retrofit2.",
                "okhttp3.",
                "android.database.sqlite.",
            )
        }
    }

    @Test
    fun `the data layer does not depend on the UI layer`() {
        val data = appSources.files.filter {
            it.packagee?.name?.startsWith("app.otium.data") == true
        }
        assertTrue("data sources were not found", data.isNotEmpty())

        data.assertFalse { it.importsFrom("app.otium.ui.") }
    }

    @Test
    fun `Room entities and DAOs stay in the data layer`() {
        appSources.classes()
            .filter { cls -> cls.hasAnnotationWithName("Entity") }
            .assertTrue { it.resideInPackage("app.otium.data.local.entity..") }

        appSources.classes()
            .filter { cls -> cls.hasAnnotationWithName("Dao") }
            .assertTrue { it.resideInPackage("app.otium.data.local.dao..") }
    }

    @Test
    fun `raw color values appear only in the theme palette`() {
        val rawColor = Regex("""0x[Ff]{2}[0-9A-Fa-f]{6}|Color\(0x""")
        val offenders = appSources.files
            .filter { it.packagee?.name?.startsWith("app.otium.ui") == true }
            .filter { it.name != "Color" }
            .filter { rawColor.containsMatchIn(it.text) }

        assertTrue("Raw colors outside Color.kt: ${offenders.map { it.path }}", offenders.isEmpty())
    }

    // --- ViewModels (recommendations: screen level, no AndroidViewModel, no lifecycle types) ---

    private val viewModels get() = appSources.classes().withNameEndingWith("ViewModel")

    @Test
    fun `ViewModels live in the UI layer and are Hilt ViewModels`() {
        verifyEach(viewModels) {
            it.resideInPackage("app.otium.ui..") && it.hasAnnotationWithName("HiltViewModel")
        }
    }

    @Test
    fun `ViewModels do not use AndroidViewModel`() {
        verifyEach(viewModels) { !it.hasParentWithName("AndroidViewModel") }
    }

    @Test
    fun `ViewModels hold no Android framework or lifecycle types`() {
        verifyEach(viewModels) { viewModel ->
            val file = viewModel.containingFile
            !file.importsFrom(
                "android.",
                "androidx.lifecycle.Lifecycle",
                "androidx.lifecycle.AndroidViewModel",
                "androidx.compose.ui.",
                "androidx.compose.material",
            )
        }
    }

    @Test
    fun `ViewModels expose immutable state and keep mutable flows private`() {
        val mutableFlow = Regex("Mutable(State|Shared)Flow")

        verifyEach(viewModels.flatMap { it.properties() }) { property ->
            !mutableFlow.containsMatchIn(property.text) || property.hasPrivateModifier
        }
    }

    // --- Activities and naming ---

    @Test
    fun `the app is a single activity app`() {
        val activities = appSources.classes().filter { it.hasParentWithName("ComponentActivity") }

        assertTrue(
            "expected one activity, found ${activities.map { it.name }}",
            activities.size <= 1,
        )
    }

    @Test
    fun `implementations have meaningful names instead of an Impl suffix`() {
        appSources.classes().assertFalse { it.name.endsWith("Impl") }
    }
}
