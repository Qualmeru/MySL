# R8 rules for release builds, on top of what the libraries ship (Glance keeps ActionCallbacks,
# WorkManager keeps workers, and AAPT keeps everything named in the manifest).

# Glance saves each widget's GlanceAppWidget class name and finds widgets by it later
# (getGlanceIds). A renamed class wouldn't match what an older version saved, so after an update
# redraws would find no widgets and the minute ticks would stop. Keep the names stable.
-keepnames class * extends androidx.glance.appwidget.GlanceAppWidget
# AGP 9's stricter R8 no longer keeps a constructor along with a class name, and
# scripts/check-release-dex.sh then finds the widgets' constructors gone. Keep them.
-keepclassmembers class * extends androidx.glance.appwidget.GlanceAppWidget {
    <init>();
}

# Glance creates ActionCallbacks (the widget's ↻) by reflection. Its own rule keeps the class but
# not its methods, and R8's full mode, seeing the class never created in code, strips onAction:
# tapping ↻ then crashes with AbstractMethodError. Keep the members too.
-keep class * implements androidx.glance.appwidget.action.ActionCallback {
    <init>();
    *;
}

# Readable stack traces in crash reports.
-keepattributes SourceFile,LineNumberTable
