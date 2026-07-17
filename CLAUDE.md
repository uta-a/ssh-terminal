# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## プロジェクト概要

Android 向けの SSH ターミナルクライアント「**Tunnel**」（`com.uta.tunnel`。Kotlin / Jetpack Compose,
Material 3 / Material You）。termux 風の没入型端末を、下タブ（ホスト / セッション / 設定）で包む構成。
**送信・接続ロジックを UI 非依存の `:core` に分離**し、将来の Wear OS 連携（`:wear`）と共有する前提で設計している。

## ビルド・テスト

```sh
# :core のロジックを JVM 単体テスト（Android SDK 不要）
./gradlew :core:test
# 単一テストクラス
./gradlew :core:test --tests "com.uta.tunnel.core.ssh.TofuHostKeyVerifierTest"

# デバッグ APK（Android SDK 必須）→ app/build/outputs/apk/debug/app-debug.apk
./gradlew :app:assembleDebug
```

- Gradle 実行には JDK 21。`:core`/`:app` は Java 17 ターゲットで、settings.gradle.kts の foojay
  リゾルバが JDK 17 ツールチェーンを自動 DL する。
- `:app` ビルドには Android SDK が必要（`local.properties` の `sdk.dir`、gitignore 済み）。
  この環境では `C:\Users\utaaa\android-sdk` に導入済み。
- 実機導入は `C:\Users\utaaa\android-sdk\platform-tools\adb.exe install -r app-debug.apk`。
- minSdk 26 / compile・targetSdk 35。`buildConfig = true`（`BuildConfig.DEBUG` を参照するため）。

### この環境（Windows）での注意
- ビルドは PowerShell で `Set-Location "<repo>"; .\gradlew.bat :app:assembleDebug`。Bash ツールも併用可（別々の構文）。
- **スクリーンショットは必ず Bash の `adb exec-out screencap -p > file.png`**。PowerShell の `>` は
  UTF-16 BOM を混入させ PNG を破損させる。
- **デバッグビルドは生体認証をバイパス**する（`BiometricGate` の `BuildConfig.DEBUG` 分岐）。
  adb 主導のテストがロック画面で止まらないための意図的挙動。

## アーキテクチャ

2 モジュール構成。**`:core` は Android 非依存の純 Kotlin(JVM) ライブラリ**（`com.uta.tunnel.core`）で、
Android 依存は全て `:app`（`com.uta.tunnel`）に閉じ込める。これは Wear 共有とテスト容易性の生命線。

### `:core`（Android 依存禁止 = `android.*` の import 禁止）
- `ssh/SshShellSession.kt` — sshj による接続・PTY shell・stdout リーダ。TOFU 検証を `TofuHostKeyVerifier`
  ＋ `HostKeyStore` で行う（現状 `InMemoryHostKeyStore` = 起動ごとにリセット。永続 TOFU は未実装）。
- `session/SessionManager.kt` — ドロワーが見るセッション一覧レジストリ（`StateFlow<List<SessionInfo>>` ＋ `activeId`）。
- `model/` — `ConnectionProfile`/`AuthMethod`（秘密は参照キーのみ保持）、`HostAddress`、`HostKeyEntry`、`SessionState`。

### `:app`
- **手動 DI**：`TunnelApp`（Application）→ `AppContainer` が `sessionManager`/`hostKeyStore`/`sessionController`/
  `profileRepository`/`settingsStore` を保持。**新しい依存は `AppContainer` に足す**。ViewModel は
  `VmHelper.containerViewModel {}` で注入（Hilt は使わない）。
- `session/SessionController.kt` — **複数 SSH セッションのライフサイクル管理の中核**。
  `mutableStateMapOf<SessionId, Session>`（各 Session が自分の `EmulatorHost`＋`SshShellSession`＋`SshTransport` を持つ）
  と `activeId` を保持。UI はアクティブセッションの `host`/`state`/`label`/`busy` を購読。
  同一ホストへの複数接続可（`uniqueLabel` で「name (2)」連番）。生存中は `SshForegroundService`（常駐通知）を起動。
- `terminal/` — 端末描画スタック（後述「Level B」）。
- `data/` — Room（`TunnelDatabase`/`ProfileEntity`/`ProfileDao`/`ProfileRepository`）で接続プロファイル永続化。
  秘密は `security/SecretStore.kt`（Android Keystore の AES-GCM）で暗号化し同じ行に保持（平文は保存しない）。
  `SettingsStore.kt` は DataStore（生体認証 ON/OFF 等）。
- `ui/` — Compose 画面（`screens/`）＋ `MainActivity` の NavHost。`BiometricGate` が起動/復帰でロック。
- `ssh/SshSecurity.kt` — 後述の BouncyCastle 差し替え。

### 端末描画（"Level B"）とデータフロー
Termux `terminal-emulator` は**画面バッファの解析にのみ使用**し、**描画は自作の Compose Canvas**（`TerminalCanvas`）。
ローカル PTY は使わないため JNI（`libtermux.so` 等）は packaging で除外している。

- 受信：SSH stdout → `SshShellSession` の onOutput → `SshTransport.deliver`（**メインスレッドへマーシャリング**）
  → `EmulatorHost.feed` → `TerminalEmulator.append` → frame カウンタ更新 → Canvas 再描画。
- 送信：Compose の透明 `BasicTextField`／補助キー行 → `EmulatorHost.sendBytes/sendCodePoint/sendKeyCode`
  → `TerminalOutput.write` → `Transport.send`（**単一スレッド executor へオフロード**＝`NetworkOnMainThread` 回避）。
- `Transport` は抽象（`terminal/Transport.kt`）。本番は `SshTransport`、PoC 用に `LocalEchoTransport`。

## 重要な制約・約束

- **`:core` に Android 依存を入れない**（`android.*` import 禁止）。
- **スレッド規約**：`TerminalEmulator.append`/`resize` と Compose 状態更新は必ずメインスレッド。
  SSH の send/resize/close は executor へオフロード。リーダスレッドからのコールバックは `main.post` で戻す。
- **SSH 利用前に `SshSecurity.ensureBouncyCastle()` を呼ぶ**。Android 同梱の縮小版 "BC" プロバイダを削除し、
  sshj が持ち込む本物の BouncyCastle を最優先登録する（`TunnelApp.onCreate` と `SessionController.connect` で実行）。
- **アクティブセッションの源は一本化**する。`SessionController.activeId`（UI 駆動）と `SessionManager.activeId`
  （ドロワー Flow）を connect/setActive/削除時に同期させること。
- **Room のスキーマ変更は Migration 必須**。`AppContainer` の `Room.databaseBuilder(...).addMigrations(...)` に登録する
  （既存プロファイルを破壊しない。`fallbackToDestructiveMigration` は使わない）。
- 生体認証は `FragmentActivity` 必須（`MainActivity` が継承）。`BiometricGate` はロック画面を content の**上に重ねる**
  （content を再マウントせず端末状態を保持するため）。
- `assets/fonts/` の JetBrainsMono Nerd Font ＋ Noto Sans Symbols 2 を 3 段フォールバックで使う（記号/アイコン字形）。

## Git / 規約
- コミットは **Conventional Commits・日本語**。**1 機能 / 1 修正ごと**。
- フォーム・認証・秘密（Keystore/暗号）・権限・入力検証・依存更新に触れる変更はセキュリティレビューを行い、
  リスクや欠落バリデーションを明示する。
