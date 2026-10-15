plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "me.rain.git"
    compileSdk = 35

    defaultConfig {
        applicationId = "me.rain.git"
        minSdk = 30
        targetSdk = 35
        versionCode = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1
        versionName = "1.2.0"
    }

    signingConfigs {
        val signingPath = System.getenv("RAINGIT_SIGNING_STORE_FILE")
        if (!signingPath.isNullOrBlank()) {
            create("ciRelease") {
                storeFile = file(signingPath)
                storePassword = System.getenv("RAINGIT_SIGNING_STORE_PASSWORD")
                keyAlias = System.getenv("RAINGIT_SIGNING_KEY_ALIAS")
                keyPassword = System.getenv("RAINGIT_SIGNING_KEY_PASSWORD")
            }
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            isShrinkResources = false
            signingConfig = signingConfigs.findByName("ciRelease")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
    packaging {
        resources.excludes += setOf("META-INF/DEPENDENCIES", "META-INF/NOTICE*", "META-INF/LICENSE*")
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2025.08.01")
    implementation(composeBom)
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.9.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.eclipse.jgit:org.eclipse.jgit:6.10.1.202505221210-r")
    implementation("org.slf4j:slf4j-nop:2.0.16")
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.5")
}
