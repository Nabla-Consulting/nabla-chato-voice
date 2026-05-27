import java.util.Properties
import java.time.LocalDate
import java.time.format.DateTimeFormatter

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

// Auto-increment build number: format YYYY.MM.DD.N (resets N each day)
val today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy.MM.dd"))
val versionProps = Properties().also { props ->
    val f = rootProject.file("version.properties")
    if (f.exists()) props.load(f.inputStream())
}
val lastDate = versionProps.getProperty("BUILD_DATE", "")
val lastSeq  = versionProps.getProperty("BUILD_SEQ",  "0").toInt()
val todaySeq = if (lastDate == today) lastSeq + 1 else 1
versionProps["BUILD_DATE"] = today
versionProps["BUILD_SEQ"]  = todaySeq.toString()
rootProject.file("version.properties").writer().use { versionProps.store(it, null) }
// versionCode as int: YYMMDDNN (e.g. 26052701) — fits in Int, Play Store ordered
val buildNumber = (today.replace(".", "").substring(2) + todaySeq.toString().padStart(2, '0')).toInt()
val buildName   = "$today.$todaySeq"  // human-readable e.g. 2026.05.27.1

android {
    namespace = "com.nabla.chatovoice"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.nabla.chatovoice"
        minSdk = 26
        targetSdk = 34
        versionCode = buildNumber
        versionName = "0.1+$buildName"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
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

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.8"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
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
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.ktx)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.hilt.android)
    implementation(libs.hilt.navigation.compose)
    ksp(libs.hilt.compiler)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.coroutines.android)

    debugImplementation(libs.compose.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.coroutines.test)
}
