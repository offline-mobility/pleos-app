import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "io.github.offlinemobility.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "io.github.offlinemobility.app"
        minSdk = 28
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Rescue server base URL (docs/GATEWAY_API.md). Blank = online features off.
        // Must be https:// (debug builds also accept http://10.0.2.2 for a local server).
        // Rescue server address, set per build with -PserverUrl=https://... (empty: online features off).
        // A string resource, not a BuildConfig constant: Kotlin inlines Java constants at compile
        // time, so an incremental build could keep the address of the previous build.
        resValue("string", "server_url", (project.findProperty("serverUrl") ?: "").toString())
    }

    // Release signing key stays on the developer machine (keystore.properties and *.jks are git-ignored).
    val signingProps = rootProject.file("keystore.properties").takeIf { it.exists() }?.let { file ->
        Properties().apply { file.inputStream().use(::load) }
    }
    signingConfigs {
        if (signingProps != null) {
            create("release") {
                storeFile = rootProject.file("app").resolve(signingProps.getProperty("storeFile"))
                storePassword = signingProps.getProperty("storePassword")
                keyAlias = signingProps.getProperty("keyAlias")
                keyPassword = signingProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        // Debug build with release shrinking, to reproduce R8-only problems with logs available.
        create("minifiedDebug") {
            initWith(getByName("debug"))
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            matchingFallbacks += listOf("debug")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            isDebuggable = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig = signingConfigs.findByName("release")
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

/**
 * Ships the country's 민방위대피소 inside the app so shelters can be found with no connection.
 * The single source is the server's snapshot (server/data, refreshed with `npm run update-civil-shelters`).
 */
abstract class CopyCivilShelters : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val source: RegularFileProperty

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun copy() {
        val target = outputDir.get().asFile.resolve("civil-shelters.json")
        target.parentFile.mkdirs()
        source.get().asFile.copyTo(target, overwrite = true)
    }
}

val copyCivilShelters = tasks.register<CopyCivilShelters>("copyCivilShelters") {
    source.set(rootProject.layout.projectDirectory.file("server/data/civil-shelters.json"))
    outputDir.set(layout.buildDirectory.dir("generated/civilShelters"))
}

androidComponents {
    onVariants { variant ->
        variant.sources.assets?.addGeneratedSourceDirectory(copyCivilShelters, CopyCivilShelters::outputDir)
    }
}

dependencies {
    implementation(libs.vehicle.sdk)
    implementation(libs.tts.sdk)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.zxing.core)

    debugImplementation(libs.androidx.compose.ui.tooling)
    testImplementation(libs.junit)
    testImplementation(libs.json)
}

