plugins {
    id("com.android.application")
}

android {
    namespace = "io.github.dai2010.focuseed.android"
    compileSdk = 35

    defaultConfig {
        applicationId = "io.github.dai2010.focuseed"
        minSdk = 29
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(project(":shared"))
}
