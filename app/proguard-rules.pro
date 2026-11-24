# junrar and slf4j (optional logger) are used by reflection or optional bindings
-dontwarn org.slf4j.**
-dontwarn com.github.junrar.**
-keep class com.github.junrar.** { *; }
# Worker classes are created by WorkManager through reflection
-keep class app.tplay.work.PipelineWorker { *; }
-keep class app.tplay.work.CleanupWorker { *; }
# libtorrent4j: its native library calls these Java classes by name (SWIG), so they must keep their names and members
-keep class org.libtorrent4j.** { *; }
-dontwarn org.libtorrent4j.**
