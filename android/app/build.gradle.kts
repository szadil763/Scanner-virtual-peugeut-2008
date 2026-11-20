plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.gms.google-services")
}

// Chave de assinatura: 2 quando o CI passa o arquivo da chave nova (segredo), 1 quando não (debug.keystore).
val chaveArquivo: String? = System.getenv("SIGNING_STORE_FILE")?.takeIf { it.isNotBlank() && file(it).exists() }
val chaveGeracao: Int = if (chaveArquivo != null) 2 else 1

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
        // Reconhecimento offline da palavra "Monitor" (Vosk) tem biblioteca nativa: só ARM, como os
        // multimídias e celulares; deixa o APK ~20 MB menor que com x86.
        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a") }
        buildConfigField("int", "CHAVE_ASSINATURA", chaveGeracao.toString())
    }

    // Assinatura. Chave 2 (segredo do GitHub Actions, ver docs/ATUALIZACAO.md): o CI passa o arquivo e as
    // senhas por variáveis de ambiente. Sem elas (build local ou antes de criar os segredos), vale a chave 1,
    // a debug.keystore pública do repositório. A geração vai para BuildConfig.CHAVE_ASSINATURA e para as
    // notas da release ("chave: N"): o app avisa que precisa reinstalar quando a chave muda.
    signingConfigs {
        getByName("debug") {
            if (chaveArquivo != null) {
                storeFile = file(chaveArquivo)
                storePassword = System.getenv("SIGNING_STORE_PASSWORD")
                keyAlias = System.getenv("SIGNING_KEY_ALIAS")
                keyPassword = System.getenv("SIGNING_KEY_PASSWORD")
            } else {
                storeFile = file("debug.keystore")
                storePassword = "android"
                keyAlias = "androiddebugkey"
                keyPassword = "android"
            }
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
    // Firebase: login com Google e banco em nuvem (backup e acesso por conta). Ver docs/FIREBASE.md.
    implementation(platform("com.google.firebase:firebase-bom:33.5.1"))
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-database")
    implementation("com.google.android.gms:play-services-auth:21.2.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    // Android Auto (telas de modelo). Ver docs/ANDROID-AUTO.md.
    implementation("androidx.car.app:app:1.4.0")
    // Rádio pela internet (aba Mídias): player e sessão de mídia. Ver docs/MIDIAS.md.
    // Ler o km do painel numa foto (reconhecimento de texto no aparelho, sem internet).
    implementation("com.google.mlkit:text-recognition:16.0.0")
    // Palavra "Monitor" para chamar o comando de voz, reconhecida no próprio aparelho, sem internet.
    implementation("com.alphacephei:vosk-android:0.3.47@aar")
    implementation("net.java.dev.jna:jna:5.13.0@aar")
    implementation("androidx.media3:media3-exoplayer:1.4.1")
    implementation("androidx.media3:media3-exoplayer-hls:1.4.1")
    implementation("androidx.media3:media3-session:1.4.1")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
}
