plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
android {
    namespace = "com.jarvis.ai"
    compileSdk = 34
    defaultConfig {
        applicationId = "com.jarvis.ai"
        minSdk = 26; targetSdk = 34; versionCode = 1; versionName = "1.0"
        ndk { abiFilters += "arm64-v8a" }
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
    androidResources { noCompress += listOf("html") }
}
dependencies {
    implementation("com.google.mediapipe:tasks-genai:0.10.27")
}