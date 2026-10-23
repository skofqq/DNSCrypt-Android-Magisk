# Libraries bring their own rules (kotlinx.serialization keeps generated serializers,
# libsu its shell classes). Kept here: what R8 broke in testing, and readable traces.

# Room builds WorkManager's database through the no-arg constructor by reflection;
# without this the app dies at startup in androidx.startup.InitializationProvider.
-keep class * extends androidx.room.RoomDatabase { <init>(); }

-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
