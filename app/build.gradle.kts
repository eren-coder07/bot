import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Release signing: taken from environment variables (set by the CI workflow) or
// from keystore.properties in the project root (for local builds). If neither is
// present the release build falls back to the debug key so it always installs.
val keystoreProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
fun signingValue(env: String, prop: String): String? =
    System.getenv(env)?.takeIf { it.isNotBlank() } ?: keystoreProps.getProperty(prop)

val releaseStoreFile = signingValue("RELEASE_STORE_FILE", "storeFile")

android {
    namespace = "com.payloadtunnel"
    compileSdk = 34
    ndkVersion = "26.3.11579264"

    defaultConfig {
        applicationId = "com.payloadtunnel"
        minSdk = 24
        targetSdk = 34
        versionCode = (System.getenv("VERSION_CODE")?.toIntOrNull() ?: 1)
        versionName = System.getenv("VERSION_NAME") ?: "1.0.0"

        vectorDrawables { useSupportLibrary = true }

        // Server + secrets come from CI (GitHub secrets) with local-dev defaults.
        buildConfigField("String", "SERVER_URL", "\"${System.getenv("SERVER_URL") ?: "http://216.158.234.252:8080"}\"")
        buildConfigField("String", "APP_SECRET", "\"${System.getenv("APP_SECRET") ?: "change-me-shared-app-secret"}\"")
        buildConfigField("String", "CERT_PIN_SHA256", "\"${System.getenv("CERT_PIN_SHA256") ?: ""}\"")
        buildConfigField("String", "START_APP_ID", "\"${System.getenv("START_APP_ID") ?: "210964446"}\"")
        buildConfigField("String", "EXPECTED_SIG", "\"${System.getenv("EXPECTED_SIG") ?: ""}\"")

        ndk {
            abiFilters += listOf("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
        }
        externalNativeBuild {
            ndkBuild {
                // hev-socks5-tunnel registers its JNI methods on this class.
                cFlags += listOf(
                    "-DPKGNAME=com/payloadtunnel/vpn",
                    "-DCLSNAME=TProxyNative",
                )
                arguments += listOf("APP_PLATFORM=android-24", "REV_ID=2.20.0")
                // Only the JNI library; upstream's Android.mk also defines a CLI executable.
                targets += "hev-socks5-tunnel"
            }
        }
    }

    externalNativeBuild {
        ndkBuild {
            path = file("src/main/jni/Android.mk")
        }
    }

    signingConfigs {
        if (releaseStoreFile != null && file(releaseStoreFile).exists()) {
            create("release") {
                storeFile = file(releaseStoreFile)
                storePassword = signingValue("RELEASE_STORE_PASSWORD", "storePassword")
                keyAlias = signingValue("RELEASE_KEY_ALIAS", "keyAlias")
                keyPassword = signingValue("RELEASE_KEY_PASSWORD", "keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
        debug {
            isMinifyEnabled = false
        }
    }

    if (project.hasProperty("screenshots")) {
        sourceSets["test"].java.srcDir("src/screenshotTest/java")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }
    testOptions {
        unitTests.isReturnDefaultValues = true
        unitTests.isIncludeAndroidResources = true
        unitTests.all {
            // Screenshot rendering (Robolectric) only when asked: ./gradlew ... -Pscreenshots
            if (project.hasProperty("screenshots")) {
                it.systemProperty("roborazzi.test.record", "true")
                it.filter.includeTestsMatching("*ScreenshotTest*")
            } else {
                it.filter.excludeTestsMatching("*ScreenshotTest*")
            }
        }
    }
    packaging {
        resources {
            excludes += setOf("/META-INF/{AL2.0,LGPL2.1}", "META-INF/versions/**")
        }
        jniLibs {
            useLegacyPackaging = true
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.3")
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui-text-google-fonts:1.6.8")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
    implementation("com.github.mwiede:jsch:0.2.26")
    implementation("com.startapp:inapp-sdk:5.2.0")
    implementation("androidx.fragment:fragment-ktx:1.8.2")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")

    // Screenshot rendering tools, only resolved with -Pscreenshots (not needed for the APK).
    if (project.hasProperty("screenshots")) {
        testImplementation("org.robolectric:robolectric:4.13")
        testImplementation("io.github.takahirom.roborazzi:roborazzi:1.20.0")
        testImplementation("io.github.takahirom.roborazzi:roborazzi-compose:1.20.0")
        testImplementation("androidx.compose.ui:ui-test-junit4")
        debugImplementation("androidx.compose.ui:ui-test-manifest")
    }
}
