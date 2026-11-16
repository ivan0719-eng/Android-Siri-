plugins { id("com.android.application") }
android {
    namespace = "com.ivan.pocketassistant"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.ivan.pocketassistant"
        minSdk = 31
        targetSdk = 35
        versionCode = 2
        versionName = "0.2"
        ndk { abiFilters += "arm64-v8a" }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
dependencies {
    implementation(files("libs/sherpa-onnx-2.0.1.aar"))
    testImplementation("junit:junit:4.13.2")
}
