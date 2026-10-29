---
name: redsurf-build-test
description: >-
  Use this skill when you need to compile-check or run unit tests for RedSurf.
---

# RedSurf Build and Test

## When to Use

Use this skill whenever you need to compile-check Kotlin code or run JVM unit tests for the RedSurf Android TV app (`tv-native/`).

## Exact Commands

Set the environment and run Gradle from `tv-native/` in each shell process:

```bash
export JAVA_HOME="$HOME/.local/opt/jdk17/Contents/Home" ANDROID_HOME="$HOME/Library/Android/sdk"
cd tv-native && ./gradlew :app:compileDebugKotlin        # compile check
cd tv-native && ./gradlew :app:testDebugUnitTest         # unit tests
```

## Cold Build Duration

A cold compile (`./gradlew :app:compileDebugKotlin`) takes approximately 1 minute and 3 seconds (~64 seconds total). Subsequent incremental builds and test runs take around 7–10 seconds.

## Reading Pass / Fail Output

### Compile Check (`compileDebugKotlin`)
- **Pass:** The output ends with `BUILD SUCCESSFUL` (e.g., `BUILD SUCCESSFUL in 1m 3s`).
- **Fail:** Gradle outputs compilation errors prefixed with `e: <file>:<line>:<col>` and ends with `BUILD FAILED`.

### Unit Tests (`testDebugUnitTest`)
- **Pass:** The output ends with `BUILD SUCCESSFUL` (e.g., `BUILD SUCCESSFUL in 7s`). Detailed counts are found in `tv-native/app/build/reports/tests/testDebugUnitTest/index.html` (e.g., `73 tests, 0 failures, 0 ignored`).
- **Fail:** Gradle outputs test failure details and ends with `BUILD FAILED`, pointing to the HTML report for detailed stack traces.
