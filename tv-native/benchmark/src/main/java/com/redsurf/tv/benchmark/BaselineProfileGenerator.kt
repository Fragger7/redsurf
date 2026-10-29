package com.redsurf.tv.benchmark

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import org.junit.Rule
import org.junit.Test

/**
 * Records the profile for the paths that janked in PERFORMANCE_MAX.md: cold start, the guide
 * (20 DOWN), the category column (LEFT, 10 DOWN), preview, fullscreen and Back.
 *
 * Assumes the TV already has a playlist loaded; the app cold-launches onto Live TV with the
 * last-watched channel focused (AGENTS.md, cold-launch resume). Run with:
 *   ./gradlew :app:generateBaselineProfile
 */
class BaselineProfileGenerator {
    @get:Rule val rule = BaselineProfileRule()

    @Test
    fun generate() = rule.collect(packageName = PACKAGE) {
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        pressHome()
        startActivityAndWait()
        device.waitForIdle()
        Thread.sleep(6_000) // paging load + focus claim on the resumed channel

        repeat(20) { device.pressDPadDown(); Thread.sleep(120) }   // guide / channel rows
        device.pressDPadLeft(); Thread.sleep(400)                   // categories column
        repeat(10) { device.pressDPadDown(); Thread.sleep(120) }
        device.pressDPadRight(); Thread.sleep(400)                  // back to channels
        device.pressDPadCenter(); Thread.sleep(3_000)               // preview
        device.pressDPadCenter(); Thread.sleep(3_000)               // fullscreen
        device.pressBack(); Thread.sleep(1_500)                     // back to browse
    }

    private companion object { const val PACKAGE = "com.redsurf.tv" }
}
