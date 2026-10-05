import java.util.Properties

plugins { id("com.android.application") }
// Release signing is read from an ignored keystore.properties; see keystore.properties.example.
val keystoreFile = rootProject.file("keystore.properties")
val keystore = Properties().apply { if (keystoreFile.isFile) keystoreFile.inputStream().use(::load) }
android {
    namespace = "dev.shutterclick"
    compileSdk = 37
    defaultConfig {
        applicationId = "dev.shutterclick"
        minSdk = 33
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"
        buildConfigField("boolean", "CIQ_SIMULATOR", "false")
    }
    buildFeatures { buildConfig = true }
    signingConfigs {
        if (keystoreFile.isFile) create("release") {
            storeFile = rootProject.file(keystore.getProperty("storeFile"))
            storePassword = keystore.getProperty("storePassword")
            keyAlias = keystore.getProperty("keyAlias")
            keyPassword = keystore.getProperty("keyPassword")
        }
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release")
        }
        getByName("debug") {
            buildConfigField("boolean", "CIQ_SIMULATOR", providers.gradleProperty("ciqSimulator").orElse("false").get())
        }
    }
    sourceSets.getByName("debug").manifest.srcFile(
        if (providers.gradleProperty("ciqSimulator").orElse("false").get() == "true")
            "src/simulator/AndroidManifest.xml" else "src/debug/AndroidManifest.xml"
    )
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
}
dependencies {
    implementation(project(":core"))
    implementation("com.garmin.connectiq:ciq-companion-app-sdk:2.4.0@aar")
}
