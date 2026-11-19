# Zayaan Authenticator ProGuard / R8 Rules

# Keep Firestore data models and Kotlin metadata for reflection-based deserialization
-keepattributes Signature,InnerClasses,EnclosingMethod,RuntimeVisibleAnnotations
-keep @androidx.annotation.Keep class * { *; }
-keep class com.example.data.model.** { *; }

# Keep BouncyCastle Argon2 and crypto providers intact
-keep class org.bouncycastle.crypto.generators.Argon2BytesGenerator { *; }
-keep class org.bouncycastle.crypto.params.Argon2Parameters** { *; }
-keep class org.bouncycastle.util.encoders.** { *; }
-dontwarn org.bouncycastle.**

# Keep ZXing QR decoder classes
-keep class com.google.zxing.** { *; }
-dontwarn com.google.zxing.**

# Keep Room database & generated implementations
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**

# Keep Jetpack Credentials & Biometric
-keep class androidx.credentials.** { *; }
-keep class com.google.android.libraries.identity.googleid.** { *; }
-keep class androidx.biometric.** { *; }
