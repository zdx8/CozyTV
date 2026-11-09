import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("org.jetbrains.kotlin.kapt")
}

android {
    namespace = "com.tvlive.tv"
    compileSdk = 36

    defaultConfig {
        minSdk = 23
        buildConfigField("String", "VERSION_NAME", "\"2.0.0-embedded\"")
        buildConfigField("boolean", "INCLUDE_PRIVATE_SUBS", "false")
        consumerProguardFiles("consumer-rules.pro")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.fragment:fragment-ktx:1.5.7")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.2")

    implementation("androidx.media3:media3-exoplayer:1.7.0")
    implementation("androidx.media3:media3-exoplayer-hls:1.7.0")
    implementation("androidx.media3:media3-exoplayer-dash:1.7.0")
    implementation("androidx.media3:media3-exoplayer-rtsp:1.7.0")
    implementation("androidx.media3:media3-ui:1.7.0")
    implementation("androidx.media3:media3-session:1.7.0")
    implementation("androidx.media3:media3-common:1.7.0")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")

    implementation("com.google.zxing:core:3.5.3")

    implementation("androidx.room:room-runtime:2.7.0")
    implementation("androidx.room:room-ktx:2.7.0")
    kapt("androidx.room:room-compiler:2.7.0")
}
