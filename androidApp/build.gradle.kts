plugins {
    id("com.android.application")
}

val releaseKeystorePath = System.getenv("FOCUSEED_ANDROID_KEYSTORE_PATH")
val releaseKeystorePassword = System.getenv("FOCUSEED_ANDROID_KEYSTORE_PASSWORD")
val releaseKeystoreType = System.getenv("FOCUSEED_ANDROID_KEYSTORE_TYPE") ?: "pkcs12"
val releaseKeyAlias = System.getenv("FOCUSEED_ANDROID_KEY_ALIAS")
val releaseKeyPassword = System.getenv("FOCUSEED_ANDROID_KEY_PASSWORD")
val hasReleaseSigning = listOf(
    releaseKeystorePath,
    releaseKeystorePassword,
    releaseKeyAlias,
    releaseKeyPassword
).all { !it.isNullOrBlank() }

android {
    namespace = "io.github.dai2010.focuseed.android"
    compileSdk = 35

    defaultConfig {
        applicationId = "io.github.dai2010.focuseed"
        minSdk = 29
        targetSdk = 35
        versionCode = 2
        versionName = "0.1.1"
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = file(releaseKeystorePath!!)
                storePassword = releaseKeystorePassword
                storeType = releaseKeystoreType
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

tasks.matching { it.name == "assembleRelease" || it.name == "packageRelease" }.configureEach {
    doFirst {
        check(hasReleaseSigning) {
            "Android release signing secrets are required for a directly updatable APK."
        }
    }
}

dependencies {
    implementation(project(":shared"))
}
