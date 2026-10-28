import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

// Release signing comes from environment variables (CI) or from a git-ignored keystore.properties
// at the project root (local builds). Without either, release builds come out unsigned.
val keystoreProps = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}
fun signingValue(env: String, key: String): String? = System.getenv(env) ?: keystoreProps.getProperty(key)
val releaseStoreFile = signingValue("RELEASE_STORE_FILE", "storeFile")

android {
    namespace = "com.controlefinanceiro.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "io.github.michaelplanziloti.controlefinanceiro"
        minSdk = 26
        targetSdk = 34
        // CI passes these from the release tag; local builds keep the defaults.
        versionCode = (findProperty("versionCode") as String?)?.toInt() ?: 1
        versionName = (findProperty("versionName") as String?) ?: "0.1.0"
    }

    signingConfigs {
        if (releaseStoreFile != null) {
            create("release") {
                storeFile = rootProject.file(releaseStoreFile)
                storePassword = signingValue("RELEASE_STORE_PASSWORD", "storePassword")
                keyAlias = signingValue("RELEASE_KEY_ALIAS", "keyAlias")
                keyPassword = signingValue("RELEASE_KEY_PASSWORD", "keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("release")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
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
    }

    // The demo account (real spreadsheet data) lives in the git-ignored src/demoSeed folder.
    // Without it, and always in release, the no-op DemoSeed from src/noDemoSeed is used.
    val hasDemoSeed = file("src/demoSeed/java").exists()
    sourceSets["debug"].java.srcDir(if (hasDemoSeed) "src/demoSeed/java" else "src/noDemoSeed/java")
    sourceSets["release"].java.srcDir("src/noDemoSeed/java")
    // The Google Sheets script ships inside the app so Ajustes can copy it for the user.
    sourceSets["main"].assets.srcDir(rootProject.file("sheets"))
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.work.runtime.ktx)

    testImplementation(libs.junit)
    // Android ships org.json; plain JVM unit tests need the real library.
    testImplementation(libs.org.json)
}
