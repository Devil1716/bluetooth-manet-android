# Project-specific ProGuard / R8 rules for the release APK.

-keep class com.devil1716.bluetoothmanet.** { *; }
-keep class com.devil1716.bluetoothmanet.crypto.** { *; }
-keep class com.devil1716.bluetoothmanet.bluetooth.** { *; }
-keep class com.devil1716.bluetoothmanet.bluetooth.gatt.** { *; }
-keep class com.devil1716.bluetoothmanet.ui.** { *; }
-keep class com.devil1716.bluetoothmanet.update.** { *; }
-keep class com.devil1716.bluetoothmanet.mesh.domain.** { *; }
-keep class com.devil1716.bluetoothmanet.routing.** { *; }
-keep class com.devil1716.bluetoothmanet.di.** { *; }

-keep class androidx.room.** { *; }
-dontwarn androidx.room.paging.**
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-keepclassmembers class * {
    @androidx.room.* <fields>;
}

-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

-keepattributes Signature, InnerClasses, EnclosingMethod, RuntimeVisibleAnnotations, AnnotationDefault
