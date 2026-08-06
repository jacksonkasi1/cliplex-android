plugins {
 id("com.android.application")
 id("org.jetbrains.kotlin.android")
 id("org.jetbrains.kotlin.plugin.compose")
 id("org.jetbrains.kotlin.kapt")
}

android {
 namespace = "com.learnthis"
 compileSdk = 36
 ndkVersion = "27.2.12479018"

 defaultConfig {
 applicationId = "com.learnthis"
 minSdk = 29
 targetSdk = 36
 versionCode = 1
 versionName = "1.0.0-alpha01"
 testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
 vectorDrawables.useSupportLibrary = true
 ndk.abiFilters += "arm64-v8a"
 buildConfigField("boolean", "WHISPER_BUNDLED", rootProject.file("models/ggml-base-q5_1.bin").isFile.toString())

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
 "-DGGML_CPU_KLEIDIAI=ON"
 )
 }
 }
 }

 sourceSets.getByName("main").assets.srcDir(rootProject.file("models"))
 androidResources.noCompress += "bin"

 flavorDimensions += "permissionMode"
 productFlavors {
 create("safe") {
 dimension = "permissionMode"
 buildConfigField("boolean", "OVERLAY_SUPPORTED", "false")
 }
 create("overlay") {
 dimension = "permissionMode"
 applicationIdSuffix = ".overlay"
 versionNameSuffix = "-overlay"
 buildConfigField("boolean", "OVERLAY_SUPPORTED", "true")
 }
 }

 buildTypes {
 debug {
 applicationIdSuffix = ".debug"
 versionNameSuffix = "-debug"
 buildConfigField("boolean", "BENCHMARK_ENABLED", "true")
 }
 release {
 isMinifyEnabled = true
 isShrinkResources = true
 buildConfigField("boolean", "BENCHMARK_ENABLED", "false")
 proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
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
 kotlinOptions { jvmTarget = "17" }
 buildFeatures {
 compose = true
 buildConfig = true
 }
 packaging.resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
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
 implementation("androidx.room:room-runtime:2.7.0")
 implementation("androidx.room:room-ktx:2.7.0")
 implementation("androidx.core:core-ktx:1.15.0")
 implementation("androidx.media:media:1.7.0")
 implementation("com.squareup.okhttp3:okhttp:4.12.0")
 kapt("androidx.room:room-compiler:2.7.0")

 debugImplementation("androidx.compose.ui:ui-tooling")
 testImplementation("junit:junit:4.13.2")
 testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.1")
 testImplementation("androidx.test:core:1.6.1")
 testImplementation("org.robolectric:robolectric:4.14.1")
 androidTestImplementation(platform("androidx.compose:compose-bom:2025.02.00"))
 androidTestImplementation("androidx.test.ext:junit:1.2.1")
 androidTestImplementation("androidx.test:runner:1.6.2")
}
