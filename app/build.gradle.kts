plugins { alias(libs.plugins.android.application); alias(libs.plugins.kotlin.android); alias(libs.plugins.kotlin.compose); alias(libs.plugins.kotlin.serialization); alias(libs.plugins.ksp); alias(libs.plugins.hilt) }

android { namespace = "app.tplay"; compileSdk = 36
    defaultConfig { applicationId = "app.tplay"; minSdk = 26; targetSdk = 36; versionCode = (project.findProperty("versionCode") as String?)?.toInt() ?: 1; versionName = (project.findProperty("versionName") as String?) ?: "1.0"; testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner" }
    signingConfigs {
        create("release") {
            val path = System.getenv("TPLAY_KEYSTORE_PATH")
            if (path != null) {
                storeFile = file(path)
                storePassword = System.getenv("TPLAY_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("TPLAY_KEY_ALIAS") ?: "tplay"
                keyPassword = System.getenv("TPLAY_KEYSTORE_PASSWORD")
            }
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = if (System.getenv("TPLAY_KEYSTORE_PATH") != null) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
        }
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_21; targetCompatibility = JavaVersion.VERSION_21 }
    kotlinOptions { jvmTarget = "21" }
    buildFeatures { compose = true; buildConfig = true }
}

dependencies {
    implementation(platform(libs.compose.bom)); implementation("androidx.window:window:1.4.0"); implementation(libs.compose.material3); implementation("androidx.compose.material:material-icons-extended"); implementation("androidx.hilt:hilt-navigation-compose:1.2.0"); implementation("androidx.documentfile:documentfile:1.0.1"); implementation(libs.compose.ui); implementation(libs.activity.compose); implementation(libs.lifecycle.runtime); implementation(libs.navigation.compose)
    implementation(libs.media3.exoplayer); implementation(libs.media3.ui); implementation(libs.media3.session); implementation(libs.room.runtime); implementation(libs.room.ktx); ksp(libs.room.compiler); implementation(libs.datastore); implementation(libs.work); implementation(libs.hilt.android); ksp(libs.hilt.compiler); implementation(libs.okhttp); implementation(libs.serialization.json); implementation(libs.coil.compose); implementation(libs.junrar); implementation(libs.biometric); implementation(libs.coroutines)
    testImplementation(libs.junit); testImplementation(libs.mockk); testImplementation(libs.turbine); testImplementation(libs.coroutines); testImplementation(libs.coroutines.test); testImplementation(libs.mockwebserver)
    androidTestImplementation(libs.androidx.test)
}
