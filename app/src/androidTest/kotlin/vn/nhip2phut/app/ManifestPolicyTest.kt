package vn.nhip2phut.app

import android.Manifest
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.xmlpull.v1.XmlPullParser

@RunWith(AndroidJUnit4::class)
class ManifestPolicyTest {
    @Suppress("DEPRECATION")
    @Test
    fun mergedManifestKeepsTheOfflinePermissionAndComponentAllowlist() {
        val context = ApplicationProvider.getApplicationContext<Nhip2PhutApplication>()
        val packageInfo = context.packageManager.getPackageInfo(
            context.packageName,
            PackageManager.GET_PERMISSIONS or
                PackageManager.GET_ACTIVITIES or
                PackageManager.GET_RECEIVERS,
        )

        assertEquals(
            setOf(Manifest.permission.POST_NOTIFICATIONS, Manifest.permission.RECEIVE_BOOT_COMPLETED),
            packageInfo.requestedPermissions.orEmpty().toSet(),
        )
        assertEquals(
            listOf(MainActivity::class.java.name),
            packageInfo.activities.orEmpty().filter { it.exported }.map { it.name },
        )
        assertTrue(packageInfo.receivers.orEmpty().all { !it.exported })

        val applicationFlags = packageInfo.applicationInfo?.flags ?: 0
        assertFalse(applicationFlags and ApplicationInfo.FLAG_ALLOW_BACKUP != 0)
        assertFalse(applicationFlags and ApplicationInfo.FLAG_USES_CLEARTEXT_TRAFFIC != 0)
    }

    @Test
    fun backupAndDeviceTransferRulesExcludeTheRootDomain() {
        val context = ApplicationProvider.getApplicationContext<Nhip2PhutApplication>()
        val parser = context.resources.getXml(R.xml.data_extraction_rules)
        val rootExclusions = mutableSetOf<String>()
        var section: String? = null

        while (parser.eventType != XmlPullParser.END_DOCUMENT) {
            when (parser.eventType) {
                XmlPullParser.START_TAG -> when (parser.name) {
                    "cloud-backup", "device-transfer" -> section = parser.name
                    "exclude" -> if (
                        parser.getAttributeValue(null, "domain") == "root" &&
                        parser.getAttributeValue(null, "path") == "."
                    ) {
                        section?.let(rootExclusions::add)
                    }
                }

                XmlPullParser.END_TAG -> if (parser.name == section) section = null
            }
            parser.next()
        }

        assertEquals(setOf("cloud-backup", "device-transfer"), rootExclusions)
    }
}
