plugins {
    alias(libs.plugins.agp.app)
    alias(libs.plugins.kgp.android)
    alias(libs.plugins.kgp.serialization)
    alias(libs.plugins.compose.plugin)
    alias(libs.plugins.hilt.plugin)
    alias(libs.plugins.ksp)
}

android {
    namespace = "kz.tcloud.dcinv"
    compileSdk = 35

    defaultConfig {
        applicationId = "kz.tcloud.dcinv"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Backend base URL — see docs/mobile-api-guide.md. Overridable per build type below.
        buildConfigField("String", "API_BASE_URL", "\"https://qr-dc.t-cloud.kz\"")
        buildConfigField("String", "KEYCLOAK_BASE_URL", "\"https://sso-ttc.t-cloud.kz\"")
        buildConfigField("String", "KEYCLOAK_REALM", "\"prod-v1\"")
        buildConfigField("String", "OIDC_CLIENT_ID", "\"dcinv-mobile\"")
        buildConfigField("String", "OIDC_REDIRECT_URI", "\"kz.tcloud.dcinv:/oauth/callback\"")

        // Consumed by AppAuth's RedirectUriReceiverActivity in the manifest.
        manifestPlaceholders["appAuthRedirectScheme"] = "kz.tcloud.dcinv"
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.material)

    // Compose
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    implementation(libs.compose.activity)
    implementation(libs.compose.viewmodel)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.navigation.compose)
    debugImplementation(libs.compose.ui.tooling)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    // Networking
    implementation(libs.retrofit)
    implementation(libs.retrofit.serialization)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)

    // Storage
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    implementation(libs.datastore.preferences)

    // WorkManager
    implementation(libs.work.runtime.ktx)

    // Auth & security
    implementation(libs.appauth)
    implementation(libs.androidx.security.crypto)

    // Camera + scanning
    implementation(libs.camera.core)
    implementation(libs.camera.camera2)
    implementation(libs.camera.lifecycle)
    implementation(libs.camera.view)
    implementation(libs.mlkit.barcode)

    // Test
    testImplementation(libs.junit)
    testImplementation(libs.coroutines.test)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.test.manifest)
}
