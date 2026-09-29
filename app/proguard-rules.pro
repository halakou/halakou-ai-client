# halakou Enterprise ProGuard & R8 Obfuscation Rules

# 1. General Optimization & Obfuscation
-repackageclasses 'com.example.halakou.internal'
-allowaccessmodification
-dontusemixedcaseclassnames
-dontskipnonpubliclibraryclasses
-verbose

# 2. Strip Logging and Debug code in release builds
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
}

# 3. Room Database Keep Rules
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**
-keepclassmembers class * extends androidx.room.RoomDatabase {
    <methods>;
}

# 4. Network & JSON Model Serialization
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# 5. OkHttp & Security (Certificate Pinner, SSL)
-dontwarn okhttp3.**
-dontwarn okio.**
-keepattributes Signature
-keepattributes *Annotation*
-keepclassmembers class okhttp3.internal.publicsuffix.PublicSuffixDatabase {
    *;
}

# 6. Cryptography & Hardware KeyStore Protection
-keep class com.example.halakou.data.security.KeyStoreManager { *; }
-keep class com.example.halakou.data.security.AdminManager {
    public boolean verifyAndUnlock(java.lang.String);
    public kotlinx.coroutines.flow.StateFlow getIsAdmin();
}
-keep class com.example.halakou.domain.billing.BillingManager { *; }

