# junrar and slf4j (optional logger) are used by reflection or optional bindings
-dontwarn org.slf4j.**
-dontwarn com.github.junrar.**
-keep class com.github.junrar.** { *; }
# Worker classes are created by WorkManager through reflection
-keep class app.tplay.work.PipelineWorker { *; }
-keep class app.tplay.work.CleanupWorker { *; }
