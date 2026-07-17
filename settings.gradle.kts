pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

// JDK 17 ツールチェーンが未導入でも自動ダウンロードできるようにする。
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // Termux terminal-emulator（TerminalEmulator 解析クラス）は JitPack で配布。
        maven { url = uri("https://jitpack.io") }
    }
}

rootProject.name = "tunnel"
include(":app")
include(":core")
