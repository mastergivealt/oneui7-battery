plugins { id("com.android.application") }

android {
    namespace = "dev.claude.oneui7pill"
    compileSdk = 34
    defaultConfig {
        applicationId = "dev.claude.oneui7pill"
        minSdk = 26
        targetSdk = 34
        versionCode = 9
        versionName = "0.7.0"
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    // Xposed API is provided by the framework at runtime (Vector/LSPosed) -> compileOnly
    compileOnly(project(":xposedstub"))
}
