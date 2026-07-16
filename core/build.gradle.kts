// :core は Android 非依存の純 Kotlin(JVM) ライブラリ。
// SSH 接続オーケストレーション・ドメインモデル・TOFU ロジックを提供し、
// 将来の :wear など他モジュールと共有できるようにする。
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    // モジュール間で共有する DTO のシリアライズ用。api で :app へ推移的に提供する。
    api(libs.kotlinx.serialization.json)
    // SSH 接続。sshj は純 JVM ライブラリなのでコア側に置ける。
    api(libs.sshj)
    // セッション状態を Flow で公開するため。
    api(libs.kotlinx.coroutines.core)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}

tasks.withType<Test> {
    useJUnit()
}
