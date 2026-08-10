plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.kapt")
}

val cliplexKleidiAiEnabled = providers.gradleProperty("cliplexKleidiAi")
    .map { rawValue ->
        require(rawValue.equals("true", ignoreCase = true) || rawValue.equals("false", ignoreCase = true)) {
            "cliplexKleidiAi must be true or false"
        }
        rawValue.toBoolean()
    }
    .orElse(false)
    .get()

android {
    namespace = "com.jacksonkasi.cliplex"
    compileSdk = 36
    ndkVersion = "27.2.12479018"

    defaultConfig {
        applicationId = "com.jacksonkasi.cliplex"
        minSdk = 29
        targetSdk = 36
        versionCode = 2
        versionName = "1.0.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("boolean", "KLEIDIAI_INTEGRATION_ENABLED", cliplexKleidiAiEnabled.toString())
        buildConfigField("String", "WHISPER_COMMIT", "\"a8d002cfd879315632a579e73f0148d06959de36\"")
        buildConfigField("String", "KLEIDIAI_VERSION", "\"v1.9.0\"")
        vectorDrawables.useSupportLibrary = true
        ndk.abiFilters += "arm64-v8a"

        externalNativeBuild {
            cmake {
                cppFlags += listOf("-std=c++17", "-O3", "-fvisibility=hidden")
                arguments += listOf(
                    "-DANDROID_STL=c++_shared",
                    "-DBUILD_SHARED_LIBS=OFF",
                    "-DWHISPER_BUILD_TESTS=OFF",
                    "-DWHISPER_BUILD_EXAMPLES=OFF",
                    "-DWHISPER_BUILD_SERVER=OFF",
                    "-DGGML_NATIVE=OFF",
                    "-DCLIPLEX_ENABLE_KLEIDIAI=${if (cliplexKleidiAiEnabled) "ON" else "OFF"}",
                )
            }
        }
    }

    // The known-good sample is a debug-only diagnostic asset and is never shipped in submission builds.
    sourceSets.getByName("debug").assets.srcDir(rootProject.file("benchmarks/samples"))
    androidResources.noCompress += "bin"

    flavorDimensions += "permissionMode"
    productFlavors {
        create("safe") {
            dimension = "permissionMode"
            buildConfigField("boolean", "OVERLAY_SUPPORTED", "false")
            buildConfigField("boolean", "SMART_TUTOR_SUPPORTED", "false")
        }
        create("overlay") {
            dimension = "permissionMode"
            versionNameSuffix = "-overlay"
            buildConfigField("boolean", "OVERLAY_SUPPORTED", "true")
            buildConfigField("boolean", "SMART_TUTOR_SUPPORTED", "true")
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            buildConfigField("boolean", "BENCHMARK_ENABLED", "true")
        }

        release {
            isDebuggable = false
            isJniDebuggable = false
            isMinifyEnabled = true
            isShrinkResources = true
            buildConfigField("boolean", "BENCHMARK_ENABLED", "false")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }

        // Release-optimized and installable without exposing a permanent production signing key.
        // This is the direct-distribution build used for Devpost judging. Play Store publishing must
        // use the release build type with the maintainer's private upload key.
        create("submission") {
            initWith(getByName("release"))
            matchingFallbacks += listOf("release")
            signingConfig = signingConfigs.getByName("debug")
            isDebuggable = false
            isJniDebuggable = false
            isMinifyEnabled = true
            isShrinkResources = true
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        // Compress native libraries in the direct-download APK. Android extracts them at install
        // time, which materially lowers the challenge artifact's download size.
        jniLibs.useLegacyPackaging = true
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }

    testOptions.unitTests.isIncludeAndroidResources = true
}

kapt {
    arguments { arg("room.schemaLocation", "$projectDir/schemas") }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2025.02.00"))
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.7")
    implementation("androidx.datastore:datastore-preferences:1.1.4")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.1")
    implementation("com.google.mlkit:translate:17.0.3")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.media:media:1.7.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("androidx.room:room-runtime:2.8.4")
    implementation("androidx.room:room-ktx:2.8.4")
    kapt("androidx.room:room-compiler:2.8.4")

    // LiteRT-LM is intentionally isolated to the full overlay flavor. The safe submission flavor
    // keeps the deterministic lesson-grounded tutor and avoids shipping ~60 MB of unused runtime.
    add("overlayImplementation", "com.google.ai.edge.litertlm:litertlm-android:0.15.0")

    debugImplementation("androidx.compose.ui:ui-tooling")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.1")
    testImplementation("androidx.test:core:1.6.1")
    testImplementation("org.robolectric:robolectric:4.14.1")
    androidTestImplementation(platform("androidx.compose:compose-bom:2025.02.00"))
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
}
