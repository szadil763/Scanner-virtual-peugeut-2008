plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "br.com.monitor2008"
    compileSdk = 34

    defaultConfig {
        applicationId = "br.com.monitor2008"
        minSdk = 26
        targetSdk = 34
        // Cada build do GitHub ganha um número novo, mostrado na tela Diagnóstico.
        val build = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1
        versionCode = build
        versionName = "0.1.$build"
    }

    // Chave de debug fixa e pública (não é segredo): mantém a mesma assinatura em todo APK gerado,
    // assim uma versão nova instala por cima da anterior sem desinstalar.
    signingConfigs {
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    // Dois APKs com o mesmo app: "multimidia" (completo) e "celular", sem o acesso a notificações
    // (mini player da música dos outros apps). Instalado fora da Play Store, o Google Play Protect
    // bloqueia no celular apps que pedem esse acesso (foto do dono, 0.1.31). Ver docs/INSTALACAO.md.
    flavorDimensions += "aparelho"
    productFlavors {
        create("multimidia") { dimension = "aparelho" }
        create("celular") { dimension = "aparelho" }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
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

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.09.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.6")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    // Android Auto (telas de modelo). Ver docs/ANDROID-AUTO.md.
    implementation("androidx.car.app:app:1.4.0")
    // Rádio pela internet (aba Mídias): player e sessão de mídia. Ver docs/MIDIAS.md.
    implementation("androidx.media3:media3-exoplayer:1.4.1")
    implementation("androidx.media3:media3-exoplayer-hls:1.4.1")
    implementation("androidx.media3:media3-session:1.4.1")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
}
