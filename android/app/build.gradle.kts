plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Endereço do site (GitHub Pages). O GitHub preenche sozinho ao gerar o APK.
val webUrl = (project.findProperty("webUrl") as String?) ?: "https://exemplo.github.io/caixa-comum/"
val buildNumber = ((project.findProperty("versionCode") as String?) ?: "1").toInt()

android {
    namespace = "br.caixacomum.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "br.caixacomum.app"
        minSdk = 26
        targetSdk = 34
        versionCode = buildNumber
        versionName = "1.$buildNumber"
        buildConfigField("String", "WEB_URL", "\"$webUrl\"")
    }

    buildFeatures { buildConfig = true }

    // Assinatura fixa: permite instalar versões novas por cima da antiga
    signingConfigs {
        create("fixa") {
            storeFile = file("caixa.keystore")
            storePassword = "caixacomum"
            keyAlias = "caixa"
            keyPassword = "caixacomum"
        }
    }

    buildTypes {
        getByName("debug") { signingConfig = signingConfigs.getByName("fixa") }
        getByName("release") {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("fixa")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}
