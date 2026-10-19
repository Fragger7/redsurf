# RedSurf R8 rules (performance salvage, 2026-10-19).
#
# Optimize and shrink, but never rename: crash logs (diag/CrashLog.kt), logcat stack traces and
# the code's own Log tags stay readable without a mapping file. The repo is public, so there is
# nothing to hide by obfuscating.
-dontobfuscate

# Libraries in use ship their own consumer rules (Room, WorkManager, OkHttp, Media3, Coil,
# coroutines). Add a rule here only for a real, observed R8 failure - with a comment saying which.

# Keep file names and line numbers in stack traces (crash log, logcat) - names alone aren't
# enough to find a crash in a 1,000-line composable.
-keepattributes SourceFile,LineNumberTable
