import org.jetbrains.kotlin.gradle.dsl.JvmTarget



plugins {

    alias(libs.plugins.kotlinMultiplatform)

    alias(libs.plugins.androidApplication)

    alias(libs.plugins.composeMultiplatform)

    alias(libs.plugins.composeCompiler)

    alias(libs.plugins.kotlinSerialization)

}



val definesFile = rootProject.file("supabase-config.local.json")

fun defineValue(key: String): String {

    if (!definesFile.exists()) {

        return ""

    }

    val pattern = """"$key"\s*:\s*"([^"]*)"""".toRegex()

    return pattern.find(definesFile.readText())?.groupValues?.get(1)?.trim().orEmpty()

}



val supabaseUrl = defineValue("SUPABASE_URL")

val supabaseAnonKey = defineValue("SUPABASE_ANON_KEY")

val tocBackendUrl = defineValue("TOC_BACKEND_URL")



kotlin {

    androidTarget {

        compilerOptions {

            jvmTarget.set(JvmTarget.JVM_11)

        }

    }



    listOf(

        iosArm64(),

        iosSimulatorArm64(),

    ).forEach { iosTarget ->

        iosTarget.binaries.framework {

            baseName = "ComposeApp"

            isStatic = true

        }

    }



    sourceSets {

        androidMain.dependencies {

            implementation(compose.preview)

            implementation(libs.androidx.activity.compose)

            implementation(libs.androidx.core.ktx)

            implementation(libs.osmdroid.android)

            implementation(libs.ktor.client.okhttp)

        }

        commonMain.dependencies {

            implementation(compose.runtime)

            implementation(compose.foundation)

            implementation(compose.material3)

            implementation(compose.ui)

            implementation(compose.components.resources)

            implementation(compose.components.uiToolingPreview)

            implementation(libs.kotlinx.coroutines.core)

            implementation(libs.kotlinx.serialization.json)

            implementation(libs.ktor.client.core)

            implementation(libs.ktor.client.content.negotiation)

            implementation(libs.ktor.serialization.kotlinx.json)

        }

        iosMain.dependencies {

            implementation(libs.ktor.client.darwin)

        }

    }

}



android {

    namespace = "it.ansmi.tocsar"

    compileSdk = libs.versions.android.compileSdk.get().toInt()



    defaultConfig {

        applicationId = "it.ansmi.tocsar"

        minSdk = libs.versions.android.minSdk.get().toInt()

        targetSdk = libs.versions.android.targetSdk.get().toInt()

        // Formato come toc_app: 1.0.01, 1.0.02, ...

        versionCode = 10056

        versionName = "1.0.56"

        buildConfigField("String", "SUPABASE_URL", "\"$supabaseUrl\"")

        buildConfigField("String", "SUPABASE_ANON_KEY", "\"$supabaseAnonKey\"")

        buildConfigField("String", "TOC_BACKEND_URL", "\"$tocBackendUrl\"")

    }

    packaging {

        resources {

            excludes += "/META-INF/{AL2.0,LGPL2.1}"

        }

    }

    buildTypes {

        getByName("release") {

            isMinifyEnabled = false

            // Firma debug finché non c’è un keystore di release dedicato

            signingConfig = signingConfigs.getByName("debug")

        }

    }

    buildFeatures {

        buildConfig = true

    }

    compileOptions {

        sourceCompatibility = JavaVersion.VERSION_11

        targetCompatibility = JavaVersion.VERSION_11

    }

}



dependencies {

    debugImplementation(compose.uiTooling)

}


