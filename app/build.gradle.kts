import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

val updateManifestUrl = providers.gradleProperty("cozytvUpdateUrl")
    .orElse(providers.environmentVariable("COZYTV_UPDATE_URL"))
    .getOrElse("https://api.github.com/repos/zdx8/CozyTV/releases/latest")
val versionCodeOverride = providers.gradleProperty("cozytvVersionCode")
    .orElse(providers.environmentVariable("COZYTV_VERSION_CODE"))
    .orNull
    ?.toIntOrNull()
val versionNameOverride = providers.gradleProperty("cozytvVersionName")
    .orElse(providers.environmentVariable("COZYTV_VERSION_NAME"))
    .orNull
    ?.trim()
    ?.takeIf(String::isNotEmpty)
val textureVideoForScreenshots = providers.gradleProperty("cozytvTextureVideo")
    .orElse(providers.environmentVariable("COZYTV_TEXTURE_VIDEO"))
    .map(String::toBoolean)
    .getOrElse(false)

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "app.cozytv"
    compileSdk = 36

    defaultConfig {
        applicationId = "app.cozytv"
        minSdk = 23
        targetSdk = 36
        versionCode = versionCodeOverride ?: 9
        versionName = versionNameOverride ?: "0.4.0"
        buildConfigField(
            "String",
            "UPDATE_MANIFEST_URL",
            "\"${updateManifestUrl.replace("\\", "\\\\").replace("\"", "\\\"")}\"",
        )
        buildConfigField(
            "boolean",
            "TEXTURE_VIDEO_FOR_SCREENSHOTS",
            textureVideoForScreenshots.toString(),
        )
    }

    // Release signing credentials come from `keystore.properties` in the
    // repository root (gitignored, see keystore.properties.example).
    // If the file is missing the build still works — the release APK is
    // simply left unsigned, which tools/release.sh refuses to publish.
    signingConfigs {
        create("release") {
            val keystoreProps = Properties().apply {
                val f = rootProject.file("keystore.properties")
                if (f.exists()) f.inputStream().use { load(it) }
            }
            if (keystoreProps.isNotEmpty()) {
                // `storeFile` is written by hand, so a bare relative path
                // is ambiguous: Gradle resolves it against this module,
                // people write it relative to the repository root. Try
                // both and fall back to the module-relative one so the
                // error message still names a concrete file.
                val rawStoreFile = keystoreProps.getProperty("storeFile")?.trim()
                if (!rawStoreFile.isNullOrEmpty()) {
                    val candidates = listOf(file(rawStoreFile), rootProject.file(rawStoreFile))
                    storeFile = candidates.firstOrNull { it.exists() } ?: candidates.first()
                }
                storePassword = keystoreProps.getProperty("storePassword", "")
                keyAlias = keystoreProps.getProperty("keyAlias", "cozytv-release")
                keyPassword = keystoreProps.getProperty("keyPassword", "")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            // Sign with the real release key when keystore.properties is
            // present. Without it the build still succeeds but produces
            // an unsigned APK — the release script refuses to publish
            // an unsigned one.
            if (signingConfigs.findByName("release")?.storeFile != null) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(project(":tvlive"))
    val composeBom = platform("androidx.compose:compose-bom:2025.11.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.2")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.2")
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.media3:media3-exoplayer:1.7.0")
    implementation("androidx.media3:media3-exoplayer-hls:1.7.0")
    implementation("androidx.media3:media3-ui:1.7.0")
    implementation("com.google.zxing:core:3.5.3")

    testImplementation("junit:junit:4.13.2")
}

