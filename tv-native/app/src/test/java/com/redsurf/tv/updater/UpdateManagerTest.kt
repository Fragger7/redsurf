package com.redsurf.tv.updater

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * UpdateManager.isNewerVersion is the fix for the bug observed live on the target device
 * (see docs/plans/PHASE_0.md #0.6b): comparing tags with plain string inequality treated a
 * locally-built "v1.0.0" as different from - and therefore "newer" than - the published
 * "v0.17.3", so the app tried to downgrade itself on every launch.
 */
class UpdateManagerTest {

    @Test
    fun patchBump_isNewer() {
        assertTrue(UpdateManager.isNewerVersion("v0.17.5", "v0.17.4"))
    }

    @Test
    fun minorBump_isNewer() {
        assertTrue(UpdateManager.isNewerVersion("v0.18.0", "v0.17.5"))
    }

    @Test
    fun majorBump_isNewer() {
        assertTrue(UpdateManager.isNewerVersion("v1.0.0", "v0.17.5"))
    }

    @Test
    fun sameVersion_isNotNewer() {
        assertFalse(UpdateManager.isNewerVersion("v0.17.4", "v0.17.4"))
    }

    @Test
    fun olderRemote_isNotNewer() {
        // Exact regression scenario observed on the Chromecast: a locally-built dev build
        // reporting v1.0.0 must never be "updated" to the lower published tag v0.17.3.
        assertFalse(UpdateManager.isNewerVersion("v0.17.3", "v1.0.0"))
    }

    @Test
    fun missingTrailingComponent_comparesAsZero() {
        assertFalse(UpdateManager.isNewerVersion("v1.0", "v1.0.0"))
        assertTrue(UpdateManager.isNewerVersion("v1.0.1", "v1.0"))
    }

    @Test
    fun malformedTag_isNotNewer() {
        assertFalse(UpdateManager.isNewerVersion("not-a-version", "v0.17.4"))
        assertFalse(UpdateManager.isNewerVersion("v0.17.4", "not-a-version"))
        assertFalse(UpdateManager.isNewerVersion("", "v0.17.4"))
    }
}
