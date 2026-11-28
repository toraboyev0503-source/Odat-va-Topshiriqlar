plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.kapt")
}

fun String.asBuildConfigString(): String =
    "\"" + replace("\\", "\\\\").replace("\"", "\\\"") + "\""

val billingVerificationUrl = providers.gradleProperty("MEMORA_BILLING_VERIFICATION_URL")
    .orElse(providers.environmentVariable("MEMORA_BILLING_VERIFICATION_URL"))
    .getOrElse("")
    .trim()
val debugEntitlement = providers.gradleProperty("MEMORA_DEBUG_ENTITLEMENT")
    .orElse(providers.environmentVariable("MEMORA_DEBUG_ENTITLEMENT"))
    .getOrElse("true")
    .toBooleanStrictOrNull() ?: true

android {
    namespace = "com.memora.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.memora.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 70
        versionName = "0.9.49"

        buildConfigField("boolean", "PERF_INSTRUMENTED", "false")

        buildConfigField(
            "String",
            "BILLING_VERIFICATION_URL",
            billingVerificationUrl.asBuildConfigString()
        )

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    signingConfigs {
        // Stable TEST key: all GitHub debug APKs from 0.9.4 onward are signed with the same
        // certificate, so they can update one another without uninstalling. This key is for
        // test/debug builds only and is never used for the Play Store release.
        getByName("debug") {
            storeFile = file("keystore/memora-test-debug.jks")
            storePassword = "memora-test-only"
            keyAlias = "memora-test"
            keyPassword = "memora-test-only"
        }

        val keystorePath = System.getenv("MEMORA_KEYSTORE_PATH")
        val keystorePassword = System.getenv("MEMORA_KEYSTORE_PASSWORD")
        val keyAlias = System.getenv("MEMORA_KEY_ALIAS")
        val keyPassword = System.getenv("MEMORA_KEY_PASSWORD")
        if (
            !keystorePath.isNullOrBlank() &&
            !keystorePassword.isNullOrBlank() &&
            !keyAlias.isNullOrBlank() &&
            !keyPassword.isNullOrBlank()
        ) {
            create("releaseSigning") {
                storeFile = file(keystorePath)
                storePassword = keystorePassword
                this.keyAlias = keyAlias
                this.keyPassword = keyPassword
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            signingConfig = signingConfigs.getByName("debug")
            buildConfigField("boolean", "DEBUG_ENTITLEMENT", debugEntitlement.toString())
            buildConfigField("boolean", "BILLING_BACKEND_REQUIRED", "false")
        }
        // Non-debuggable, R8-optimized probe. Keep package and certificate identical to
        // previously installed GitHub debug builds so existing notes are never wiped.
        create("performanceProbe") {
            initWith(getByName("release"))
            isDebuggable = false
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-probe"
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
            buildConfigField("boolean", "DEBUG_ENTITLEMENT", debugEntitlement.toString())
            buildConfigField("boolean", "BILLING_BACKEND_REQUIRED", "false")
            buildConfigField("boolean", "PERF_INSTRUMENTED", "true")
        }
        release {
            isDebuggable = false
            isMinifyEnabled = true
            isShrinkResources = true
            buildConfigField("boolean", "DEBUG_ENTITLEMENT", "false")
            buildConfigField("boolean", "BILLING_BACKEND_REQUIRED", "true")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfigs.findByName("releaseSigning")?.let { signingConfig = it }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    sourceSets {
        getByName("androidTest").assets.srcDir("$projectDir/schemas")
        getByName("androidTest").assets.srcDir("$projectDir/build/generated-room-schemas")
    }

    lint {
        abortOnError = true
        checkReleaseBuilds = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

kapt {
    arguments {
        arg("room.schemaLocation", "$projectDir/build/generated-room-schemas")
        arg("room.incremental", "true")
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2026.06.01")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.core:core:1.17.0")
    implementation("androidx.metrics:metrics-performance:1.0.0")
    implementation("androidx.profileinstaller:profileinstaller:1.4.1")
    implementation("androidx.core:core-splashscreen:1.2.0")
    implementation("androidx.exifinterface:exifinterface:1.4.1")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.10.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.10.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0")
    implementation("androidx.navigation:navigation-compose:2.9.8")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-text")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.material3:material3")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    implementation("androidx.room:room-runtime:2.8.4")
    implementation("androidx.room:room-ktx:2.8.4")
    implementation("androidx.room:room-paging:2.8.4")
    implementation("androidx.paging:paging-runtime-ktx:3.4.2")
    implementation("androidx.paging:paging-compose:3.4.2")
    kapt("androidx.room:room-compiler:2.8.4")

    implementation("androidx.datastore:datastore-preferences:1.2.1")
    implementation("androidx.biometric:biometric:1.1.0")
    implementation("androidx.work:work-runtime-ktx:2.11.2")
    implementation("androidx.fragment:fragment-ktx:1.8.5")
    implementation("androidx.documentfile:documentfile:1.1.0")
    implementation("androidx.camera:camera-core:1.4.2")
    implementation("androidx.camera:camera-camera2:1.4.2")
    implementation("androidx.camera:camera-lifecycle:1.4.2")
    implementation("androidx.camera:camera-view:1.4.2")
    implementation("com.android.billingclient:billing:9.1.0")
    implementation("com.google.mlkit:text-recognition:16.0.1")
    implementation("com.google.android.gms:play-services-mlkit-document-scanner:16.0.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")

    androidTestImplementation("androidx.room:room-testing:2.8.4")
    androidTestImplementation("androidx.test:runner:1.7.0")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
}

tasks.matching { it.name == "bundleRelease" || it.name == "assembleRelease" }.configureEach {
    doFirst {
        require(billingVerificationUrl.startsWith("https://")) {
            "MEMORA_BILLING_VERIFICATION_URL must be a production HTTPS endpoint"
        }
        require(android.signingConfigs.findByName("releaseSigning") != null) {
            "Release signing environment variables are missing"
        }
    }
}
