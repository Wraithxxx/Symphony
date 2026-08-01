plugins {
    alias(libs.plugins.android.app)
    alias(libs.plugins.android.kotlin)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
}

val releaseSigningValues = mapOf(
    "storeFile" to System.getenv("SIGNING_KEYSTORE_FILE"),
    "storePassword" to System.getenv("SIGNING_KEYSTORE_PASSWORD"),
    "keyAlias" to System.getenv("SIGNING_KEY_ALIAS"),
    "keyPassword" to System.getenv("SIGNING_KEY_PASSWORD"),
)
val hasAnyReleaseSigningValue = releaseSigningValues.values.any { !it.isNullOrBlank() }
val hasCompleteReleaseSigning = releaseSigningValues.values.all { !it.isNullOrBlank() }
check(!hasAnyReleaseSigningValue || hasCompleteReleaseSigning) {
    "Release signing configuration is incomplete. Provide all four signing values or none."
}

android {
    namespace = "io.github.wraithxxx.symphony"
    compileSdk = libs.versions.compile.sdk.get().toInt()

    defaultConfig {
        applicationId = "io.github.wraithxxx.symphony"
        minSdk = libs.versions.min.sdk.get().toInt()
        targetSdk = libs.versions.target.sdk.get().toInt()

        versionCode = 1
        versionName = "2026.08.01"
        versionName = System.getenv("APP_VERSION_NAME") ?: versionName

        vectorDrawables {
            useSupportLibrary = true
        }
    }

    signingConfigs {
        if (hasCompleteReleaseSigning) {
            register("release") {
                storeFile = rootProject.file(releaseSigningValues.getValue("storeFile")!!)
                storePassword = releaseSigningValues.getValue("storePassword")
                keyAlias = releaseSigningValues.getValue("keyAlias")
                keyPassword = releaseSigningValues.getValue("keyPassword")
            }
        }
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.findByName("release")
            ndk {
                debugSymbolLevel = "FULL"
            }
        }
        create("nightly") {
            initWith(getByName("release"))
        }
        create("canary") {
            initWith(getByName("release"))
            applicationIdSuffix = ".canary"
        }
        getByName("debug") {
            applicationIdSuffix = (findProperty("appDebugSuffix") as String?)
                ?: System.getenv("APP_DEBUG_SUFFIX")
                ?: ".debug"
            versionNameSuffix = "-debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs += "-opt-in=kotlin.RequiresOptIn"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes.add("/META-INF/{AL2.0,LGPL2.1}")
        }
    }

    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    testOptions {
        unitTests.all {
            it.useJUnitPlatform()
        }
    }

    room {
        schemaDirectory("$projectDir/room-schemas")
    }
}

dependencies {
    implementation(libs.activity.compose)
    implementation(libs.coil)
    implementation(libs.compose.material.icons.extended)
    implementation(libs.compose.material3)
    implementation(libs.compose.navigation)
    implementation(libs.compose.ui)
    implementation(libs.core)
    implementation(libs.core.splashscreen)
    implementation(libs.fuzzywuzzy)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.lifecycle.runtime)
    implementation(libs.media)
    implementation(libs.media3.exoplayer)
    implementation(libs.okhttp3)
    ksp(libs.room.compiler)
    implementation(libs.room.ktx)
    implementation(libs.room.runtime)
    implementation(libs.startup.runtime)
    implementation(libs.taglib)

    testImplementation(libs.junit.jupiter)
}
