# R8 rules for the release build.
#
# App code: all UI is built in code (no layout inflation, no reflection), and activities, services and
# providers are kept by the rules AAPT generates from the manifest. Nothing extra is needed for it.

# GraphHopper 1.0 is kept whole. The offline router only runs when the navigation pack is installed, so a
# shrinking mistake would surface late, on the car; parts of GraphHopper also build objects from config names.
-keep class com.graphhopper.** { *; }

# Mapsforge renderer, render themes and the SVG library they use for symbols.
-keep class org.mapsforge.** { *; }
-keep class com.caverock.androidsvg.** { *; }

# Desktop-only APIs referenced by GraphHopper's optional parts (elevation TIFF reading, logging bindings,
# Jackson config loading). They are never reached on Android.
-dontwarn java.awt.**
-dontwarn javax.imageio.**
-dontwarn javax.xml.bind.**
-dontwarn javax.annotation.**
-dontwarn org.slf4j.impl.**
-dontwarn com.fasterxml.jackson.**
-dontwarn org.apache.xmlgraphics.**
-dontwarn org.apache.commons.io.**
# GraphHopper's Helper.getTotalGcCount / getUsedMBAfterGC log JVM memory during import and CH preparation.
# The app only loads prepared graphs, so these are never called on Android (which has no java.lang.management).
-dontwarn java.lang.management.**

# Keep line numbers for readable crash reports; rename the source file attribute.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
