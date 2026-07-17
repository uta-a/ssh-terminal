import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

// リリース署名の設定。鍵とパスワードは keystore.properties（gitignore 済み）から読む。
// 手元に無い環境（CI・他マシン）では release も署名なしでビルドできるよう、存在しなければ
// signingConfig を付けない。
val keystorePropsFile = rootProject.file("keystore.properties")
val keystoreProps = Properties().apply {
    if (keystorePropsFile.exists()) keystorePropsFile.inputStream().use { load(it) }
}

android {
    namespace = "com.uta.tunnel"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.uta.tunnel"
        minSdk = 26
        targetSdk = 35
        versionCode = 3
        versionName = "0.2.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables { useSupportLibrary = true }
    }

    signingConfigs {
        if (keystoreProps.isNotEmpty()) {
            create("release") {
                storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
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

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            // sshj / BouncyCastle が同梱する重複メタデータを除去。
            excludes += "/META-INF/versions/**"
            excludes += "/META-INF/*.SF"
            excludes += "/META-INF/*.DSA"
            excludes += "/META-INF/*.RSA"
        }
        jniLibs {
            // terminal-emulator の JNI は TerminalSession（ローカル PTY）専用。
            // 本アプリは SSH バイトで TerminalEmulator を駆動し PTY を使わないため不要。
            excludes += "**/libtermux.so"
            excludes += "**/liblocal-socket.so"
        }
    }
}

dependencies {
    implementation(project(":core"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.service)
    implementation(libs.androidx.activity.compose)
    implementation(libs.kotlinx.coroutines.android)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.navigation.compose)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.androidx.datastore.preferences)

    // 秘密（鍵・パスワード）の生体認証ゲート。
    implementation(libs.androidx.biometric)
    // biometric 経由の古い fragment(1.2.x) を新しめに引き上げ、ActivityResult との衝突を回避。
    implementation(libs.androidx.fragment)

    // Termux 端末エミュレータ（解析のみ利用。描画は自作）。
    implementation(libs.termux.terminal.emulator)

    // sshj が runtime スコープで持ち込む BouncyCastle を、Android 用のプロバイダ差し替えで
    // コード参照するためコンパイル依存として明示する。
    implementation(libs.bouncycastle.bcprov)

    debugImplementation(libs.androidx.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.junit)
}
