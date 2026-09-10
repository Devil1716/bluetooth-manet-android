package com.devil1716.bluetoothmanet.update

import android.content.pm.PackageInstaller
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppUpdaterTest {
    @Test
    fun parseReleasePrefersDebugApkAsset() {
        val payload = """
            {
              "tag_name": "v1.3.6",
              "assets": [
                {"name": "notes.txt", "browser_download_url": "https://example/notes.txt"},
                {"name": "app-debug.apk", "browser_download_url": "https://example/app-debug.apk"}
              ]
            }
        """.trimIndent()
        val release = AppUpdater.parseRelease(payload)
        assertEquals("v1.3.6", release.tagName)
        assertEquals("1.3.6", release.versionName)
        assertEquals("https://example/app-debug.apk", release.apkUrl)
    }

    @Test
    fun installerConflictMapsToSignatureRecovery() {
        assertEquals(
            UpdatePhase.SIGNATURE_CONFLICT,
            AppUpdater.installerFailurePhase(PackageInstaller.STATUS_FAILURE_INCOMPATIBLE)
        )
        assertEquals(
            UpdatePhase.SIGNATURE_CONFLICT,
            AppUpdater.installerFailurePhase(PackageInstaller.STATUS_FAILURE_CONFLICT)
        )
        assertEquals(
            UpdatePhase.READY,
            AppUpdater.installerFailurePhase(PackageInstaller.STATUS_FAILURE_ABORTED)
        )
    }

    @Test
    fun bannerShowsMessengerStyleCta() {
        val available = UpdateUi(phase = UpdatePhase.AVAILABLE, availableVersion = "1.3.6", message = "Mesh v1.3.6 is available.")
        assertTrue(available.bannerVisible)
        assertEquals("Update", available.primaryLabel)

        val ready = available.copy(phase = UpdatePhase.READY, dismissed = false)
        assertEquals("Install", ready.primaryLabel)

        val dismissed = available.copy(dismissed = true)
        assertTrue(!dismissed.bannerVisible)
    }

    @Test
    fun conflictCopyExplainsUninstallOnce() {
        val text = AppUpdater.signatureConflictMessage("1.3.6")
        assertTrue(text.contains("different signing key"))
        assertTrue(text.contains("uninstall Mesh"))
    }
}
