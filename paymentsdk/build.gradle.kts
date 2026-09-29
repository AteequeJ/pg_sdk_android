import com.vanniktech.maven.publish.AndroidSingleVariantLibrary
import com.vanniktech.maven.publish.SonatypeHost
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kotlin.parcelize)
    alias(libs.plugins.maven.publish)
}

android {
    namespace = "com.pgsdk"
    compileSdk = 35

    defaultConfig {
        minSdk = 24

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")

        // Public build-time metadata consumers can rely on (not secrets).
        buildConfigField("String", "SDK_VERSION", "\"${project.findProperty("sdkVersionName") ?: "1.0.0"}\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Logging is compiled out of release builds at the call site via PGLogger's
            // isDebuggable check driven by BuildConfig.DEBUG of the *host* app, and can be
            // force-disabled by merchants via PGConfig(enableLogging = false).
        }
        debug {
            isMinifyEnabled = false
        }
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
        }
    }
}

kotlin {
    jvmToolchain(17)
}

tasks.withType<KotlinCompile>().configureEach {
    compilerOptions {
        freeCompilerArgs.add("-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi")
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.recyclerview)
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.livedata.ktx)

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)

    implementation(libs.retrofit.core)
    implementation(libs.retrofit.kotlinx.serialization.converter)
    implementation(libs.okhttp.core)
    implementation(libs.okhttp.logging.interceptor)

    implementation(libs.androidx.security.crypto)

    testImplementation(libs.junit4)
    testImplementation(libs.mockk)
    testImplementation(libs.turbine)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.arch.core.testing)
    testImplementation(libs.androidx.test.core)

    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.mockk.android)
}

// ---------------------------------------------------------------------------
// Maven publishing (com.vanniktech.maven.publish):
//   ./gradlew :paymentsdk:publishToMavenLocal       local AAR in ~/.m2
//   ./gradlew :paymentsdk:publishToMavenCentral     upload to the Central Portal
//     (then press "Publish" at central.sonatype.com → Deployments), or
//   ./gradlew :paymentsdk:publishAndReleaseToMavenCentral   upload + release
//
// Central credentials and the GPG key come from ~/.gradle/gradle.properties
// (mavenCentralUsername / mavenCentralPassword, signing.* or
// signingInMemoryKey*) — never from this repo.
// ---------------------------------------------------------------------------
mavenPublishing {
    configure(AndroidSingleVariantLibrary(variant = "release", sourcesJar = true, publishJavadocJar = true))
    publishToMavenCentral(SonatypeHost.CENTRAL_PORTAL)
    // Central rejects unsigned uploads; local publishes (sync_native.sh) work without a key.
    if (providers.gradleProperty("signingInMemoryKey").isPresent ||
        providers.gradleProperty("signing.keyId").isPresent
    ) {
        signAllPublications()
    }

    coordinates(
        groupId = "io.github.ateequej",
        artifactId = "paymentsdk",
        version = project.findProperty("sdkVersionName") as String? ?: "1.0.0"
    )

    pom {
        name.set("PG Android Payment SDK")
        description.set("Modular native Android Payment Gateway SDK (UPI / Card / Net Banking).")
        url.set("https://github.com/AteequeJ/pg_sdk_android")
        licenses {
            license {
                name.set("The Apache License, Version 2.0")
                url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
            }
        }
        developers {
            developer {
                id.set("AteequeJ")
                name.set("Ateeque Jamadar")
                url.set("https://github.com/AteequeJ")
            }
        }
        scm {
            url.set("https://github.com/AteequeJ/pg_sdk_android")
            connection.set("scm:git:https://github.com/AteequeJ/pg_sdk_android.git")
            developerConnection.set("scm:git:ssh://git@github.com/AteequeJ/pg_sdk_android.git")
        }
    }
}
