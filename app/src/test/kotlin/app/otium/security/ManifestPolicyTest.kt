package app.otium.security

import android.content.Context
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.ComponentInfo
import android.content.pm.PackageManager
import android.content.pm.ProviderInfo
import android.content.pm.ServiceInfo
import androidx.test.core.app.ApplicationProvider
import app.otium.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.xmlpull.v1.XmlPullParser

/**
 * The privacy promise, checked against the merged manifest: what the app may ask for and what
 * other apps may reach. A library that adds a permission or an exported component fails here, so
 * the change is a decision, not a side effect of a dependency bump.
 *
 * Robolectric reads the merged debug manifest. Debug adds only the test tooling listed below, so
 * the rules hold for release too.
 */
@RunWith(RobolectricTestRunner::class)
class ManifestPolicyTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Suppress("DEPRECATION") // The int-flags overload is the one every supported SDK has.
    private val info = context.packageManager.getPackageInfo(
        context.packageName,
        PackageManager.GET_PERMISSIONS or PackageManager.GET_ACTIVITIES or
            PackageManager.GET_SERVICES or PackageManager.GET_RECEIVERS or
            PackageManager.GET_PROVIDERS,
    )

    @Test
    fun `asks only for the permissions the privacy policy names`() {
        assertEquals(
            "Update the privacy policy and Play's data safety form before changing this list",
            ALLOWED_PERMISSIONS,
            info.requestedPermissions.orEmpty().toSet(),
        )
    }

    @Test
    fun `nothing of ours is reachable from other apps except the launcher activity`() {
        val exported = components()
            .filter { it.exported && it.name.startsWith(OWN_PACKAGE) }
            .map { it.name }

        assertEquals(listOf("app.otium.MainActivity"), exported)
    }

    @Test
    fun `exported library components are guarded by a permission`() {
        val unguarded = components()
            .filter { it.exported && !it.name.startsWith(OWN_PACKAGE) }
            .filter { it.name !in DEBUG_ONLY_COMPONENTS && permissionOf(it) == null }
            .map { it.name }

        assertEquals(emptyList<String>(), unguarded)
    }

    @Test
    fun `providers are private and share files only by explicit grant`() {
        val exported = info.providers.orEmpty().filter { it.exported }.map { it.name }

        assertEquals(emptyList<String>(), exported)
    }

    @Test
    fun `Android backup is off and cleartext traffic is not allowed`() {
        val flags = context.applicationInfo.flags

        assertFalse("allowBackup must stay false", flags and ApplicationInfo.FLAG_ALLOW_BACKUP != 0)
        assertFalse(
            "cleartext traffic must stay off",
            flags and ApplicationInfo.FLAG_USES_CLEARTEXT_TRAFFIC != 0,
        )
    }

    @Test
    fun `the network security config allows https only and trusts only system authorities`() {
        val cleartext = mutableListOf<String?>()
        val anchors = mutableListOf<String?>()
        context.resources.getXml(R.xml.network_security_config).use { xml ->
            while (xml.next() != XmlPullParser.END_DOCUMENT) {
                if (xml.eventType != XmlPullParser.START_TAG) continue
                when (xml.name) {
                    "base-config", "domain-config" ->
                        cleartext += xml.getAttributeValue(null, "cleartextTrafficPermitted")

                    "certificates" -> anchors += xml.getAttributeValue(null, "src")
                }
            }
        }

        assertTrue("the config must have a base-config", cleartext.isNotEmpty())
        assertEquals("cleartext must be off everywhere", cleartext.map { "false" }, cleartext)
        assertEquals("only the system's authorities", listOf("system"), anchors)
    }

    private fun components(): List<ComponentInfo> = info.activities.orEmpty().toList() +
        info.services.orEmpty() + info.receivers.orEmpty() + info.providers.orEmpty()

    private fun permissionOf(component: ComponentInfo): String? = when (component) {
        is ActivityInfo -> component.permission
        is ServiceInfo -> component.permission
        is ProviderInfo -> component.readPermission ?: component.writePermission
        else -> null
    }

    private companion object {
        const val OWN_PACKAGE = "app.otium."

        /** Each one is something the privacy policy and the data safety form explain. */
        val ALLOWED_PERMISSIONS = setOf(
            // Book lookups and the Drive backup.
            "android.permission.INTERNET",
            // Barcode scanning and cover photos; asked for in context.
            "android.permission.CAMERA",
            // The notice after repeated backup failures.
            "android.permission.POST_NOTIFICATIONS",
            // WorkManager: waiting for a network and running scheduled work after a reboot.
            "android.permission.ACCESS_NETWORK_STATE",
            "android.permission.RECEIVE_BOOT_COMPLETED",
            "android.permission.WAKE_LOCK",
            "android.permission.FOREGROUND_SERVICE",
            // AndroidX core: keeps the app's own dynamic receivers unexported.
            "app.otium.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION",
        )

        /**
         * Tooling merged into the debug manifest only: the Compose UI test host and the preview
         * launcher from `ui-tooling` (both `debugImplementation`; absent from release).
         */
        val DEBUG_ONLY_COMPONENTS = setOf(
            "androidx.activity.ComponentActivity",
            "androidx.compose.ui.tooling.PreviewActivity",
        )
    }
}
