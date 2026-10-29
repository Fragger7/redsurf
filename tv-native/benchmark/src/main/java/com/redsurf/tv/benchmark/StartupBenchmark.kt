package com.redsurf.tv.benchmark

import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import org.junit.Rule
import org.junit.Test

/** Cold start with no AOT vs. with the baseline profile compiled. */
class StartupBenchmark {
    @get:Rule val rule = MacrobenchmarkRule()

    @Test fun startupNone() = run(CompilationMode.None())

    @Test fun startupPartial() = run(
        CompilationMode.Partial(baselineProfileMode = BaselineProfileMode.Require)
    )

    private fun run(mode: CompilationMode) = rule.measureRepeated(
        packageName = "com.redsurf.tv",
        metrics = listOf(StartupTimingMetric()),
        compilationMode = mode,
        startupMode = StartupMode.COLD,
        iterations = 5,
    ) {
        pressHome()
        startActivityAndWait()
    }
}
