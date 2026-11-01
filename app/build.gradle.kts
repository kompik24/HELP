plugins { id("com.android.application") }
android {
    namespace = "ru.kompik24.help"
    compileSdk = 35
    defaultConfig {
        applicationId = "ru.kompik24.help"
        minSdk = 24
        targetSdk = 28
        versionCode = 1
        versionName = "1.0.0"
        buildConfigField("String", "API_BASE_URL", "\"https://YOUR-HELP-SERVER.example/\"")
    }
    buildFeatures { buildConfig = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
