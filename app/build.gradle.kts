import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

// Load debug secrets from secrets.properties (gitignored Ã¢â‚¬â€ never committed)
val secretsFile = rootProject.file("app/secrets.properties")
val secretsProps = Properties()
if (secretsFile.exists()) {
    secretsFile.inputStream().use { secretsProps.load(it) }
}

android {
    namespace = "com.nabla.chatovoice"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.nabla.chatovoice"
        minSdk = 26
        targetSdk = 34
        versionCode = 2026052801
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // APK naming: <namespace>-debug.apk for debug, <namespace>.apk for release
    applicationVariants.all {
        val variant = this
        variant.outputs.all {
            val output = this as com.android.build.gradle.internal.api.BaseVariantOutputImpl
            val ns = android.namespace ?: applicationId
            output.outputFileName = if (variant.buildType.name == "debug") "$ns-debug.apk" else "$ns.apk"
        }
    }

    buildTypes {
        debug {
            // Debug-only secrets from secrets.properties (gitignored)
            buildConfigField("String", "DEBUG_GATEWAY_TOKEN", "\"${secretsProps["DEBUG_GATEWAY_TOKEN"] ?: ""}\"")
            buildConfigField("String", "DEBUG_AZURE_SPEECH_KEY", "\"${secretsProps["DEBUG_AZURE_SPEECH_KEY"] ?: ""}\"")
            buildConfigField("String", "DEBUG_AZURE_SPEECH_REGION", "\"${secretsProps["DEBUG_AZURE_SPEECH_REGION"] ?: ""}\"")
            buildConfigField("String", "DEBUG_GATEWAY_URL", "\"${secretsProps["DEBUG_GATEWAY_URL"] ?: ""}\"")
        }
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // Release build has no debug secrets
            buildConfigField("String", "DEBUG_GATEWAY_TOKEN", "\"\"")
            buildConfigField("String", "DEBUG_AZURE_SPEECH_KEY", "\"\"")
            buildConfigField("String", "DEBUG_AZURE_SPEECH_REGION", "\"\"")
            buildConfigField("String", "DEBUG_GATEWAY_URL", "\"\"")
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

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
        }
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.8"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            // Azure Speech SDK ships duplicate license files
            excludes += "/META-INF/DEPENDENCIES"
            excludes += "/META-INF/LICENSE"
            excludes += "/META-INF/LICENSE.txt"
            excludes += "/META-INF/NOTICE"
            excludes += "/META-INF/NOTICE.txt"
            // MSAL / OpenTelemetry duplicate resources
            excludes += "/META-INF/jersey-module-version"
            excludes += "META-INF/io.netty.versions.properties"
            excludes += "META-INF/INDEX.LIST"
            pickFirsts += "META-INF/io.opentelemetry*"
            pickFirsts += "META-INF/services/io.opentelemetry*"
        }
    }
}

dependencies {
    implementation(libs.core.ktx)
    implementation(libs.activity.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.ktx)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.hilt.android)
    implementation(libs.hilt.navigation.compose)
    ksp(libs.hilt.compiler)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.coroutines.android)
    // Azure Cognitive Services Speech SDK Ã¯Â¿Â½ conversation transcription with diarization
    implementation("com.microsoft.cognitiveservices.speech:client-sdk:1.44.0")
    // Microsoft MSAL -- automatic Graph token for OneDrive/Obsidian
    implementation("com.microsoft.identity.client:msal:5.+")
    // Markwon -- markdown rendering in TextView (used via AndroidView in Compose)
    implementation("io.noties.markwon:core:4.6.2")
    implementation("io.noties.markwon:ext-tables:4.6.2")

    debugImplementation(libs.compose.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation("androidx.test:core:1.5.0")
    testImplementation("androidx.test:core-ktx:1.5.0")
}
