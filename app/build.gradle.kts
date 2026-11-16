plugins { id("com.android.application") }
android {
    namespace = "com.ivan.pocketassistant"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.ivan.pocketassistant"
        minSdk = 31
        targetSdk = 35
        versionCode = 1
        versionName = "0.1"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
dependencies {
    implementation("ai.picovoice:porcupine-android:4.0.2")
    testImplementation("junit:junit:4.13.2")
}
