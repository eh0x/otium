package app.otium.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Migration scaffolding. When the schema version is raised, add the Migration to
 * [OtiumDatabase.migrations] and a test that opens the previous exported schema and migrates it.
 */
@RunWith(RobolectricTestRunner::class)
class SchemaMigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        OtiumDatabase::class.java,
    )

    private val schemaDir = File("schemas/${OtiumDatabase::class.java.name}")

    @Test
    fun `exported schema exists for every version`() {
        for (version in 1..OtiumDatabase.VERSION) {
            assertTrue("missing schema $version.json", File(schemaDir, "$version.json").exists())
        }
    }

    @Test
    fun `current version matches the latest exported schema`() {
        val latest = schemaDir.listFiles { file -> file.extension == "json" }
            .orEmpty()
            .maxOf { it.nameWithoutExtension.toInt() }

        assertEquals(OtiumDatabase.VERSION, latest)
    }

    @Test
    fun `database opens from the exported version 1 schema`() {
        helper.createDatabase("migration-test", 1).close()

        helper.runMigrationsAndValidate(
            "migration-test",
            OtiumDatabase.VERSION,
            true,
            *OtiumDatabase.migrations,
        )
            .close()
    }
}
