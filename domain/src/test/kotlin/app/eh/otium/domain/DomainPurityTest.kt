package app.eh.otium.domain

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the layering rule that :domain stays pure Kotlin/JVM.
 * Fails if the test runtime classpath gains an Android, Room, Retrofit or DI artifact.
 */
class DomainPurityTest {

    private val forbiddenGroups = listOf(
        "com.android",
        "com.google.android",
        "com.google.dagger",
        "androidx",
        "javax.inject",
        "jakarta.inject",
        "com.squareup.retrofit2",
        "com.squareup.okhttp3",
    )

    @Test
    fun classpathHasNoAndroidOrFrameworkDependencies() {
        val offenders = System.getProperty("java.class.path")
            .split(File.pathSeparator)
            .filter { entry -> forbiddenGroups.any { group -> entry.contains("/$group") } }

        assertTrue("Forbidden dependencies in :domain: $offenders", offenders.isEmpty())
    }
}
