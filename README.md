# Tunnel

Android 向けの SSH ターミナルクライアント（`com.uta.tunnel`）。Kotlin / Jetpack Compose・Material 3 で、
termux 風の没入型端末を下タブ（ホスト / セッション / 設定）で包んだ構成です。

## 主な機能

- **SSH シェル接続** — sshj による PTY シェル。パスワード / 秘密鍵認証に対応。
- **複数セッション** — 同一ホストへの複数接続を含め、セッションを並行して保持・切り替え。
  接続中はフォアグラウンドサービス（常駐通知）で維持し、切断後は同じ接続先へ再接続可能。
- **自作ターミナル描画** — 画面バッファの解析に Termux の `terminal-emulator` を使い、描画は Compose Canvas で自前実装。
  ローカル PTY は使わないため JNI は同梱しません。
- **アドレス帳** — 接続プロファイルを Room に永続化。秘密情報は Android Keystore の AES-GCM で暗号化して保存し、平文は残しません。
- **TOFU ホスト鍵検証** — 既知ホスト一覧を画面から確認・削除可能。
- **生体認証ロック** — 起動・復帰時にロック（設定で ON/OFF）。
- **配色テーマ** — アプリ全体に適用され、明暗モードを切り替え可能。

## モジュール構成

| モジュール | 内容 |
| --- | --- |
| `:core` | Android 非依存の純 Kotlin(JVM) ライブラリ。SSH 接続・セッション管理・モデル。 |
| `:app`  | Android 実装。Compose UI、端末描画、Room / DataStore / Keystore、DI（手動）。 |

将来的な Wear OS 連携を見据え、送信・接続ロジックは UI 非依存の `:core` に分離しています。

## ビルド

```sh
# :core のロジックを JVM 単体テスト（Android SDK 不要）
./gradlew :core:test

# デバッグ APK → app/build/outputs/apk/debug/app-debug.apk
./gradlew :app:assembleDebug
```

- Gradle 実行には JDK 21。`:core` / `:app` は Java 17 ターゲット（ツールチェーンは foojay リゾルバが自動取得）。
- `:app` のビルドには Android SDK が必要（`local.properties` の `sdk.dir`、gitignore 済み）。
- minSdk 26 / compile・targetSdk 35。
- リリース署名は `keystore.properties`（gitignore 済み）から読み込みます。存在しない環境では署名なしでビルドされます。

## 開発

コントリビューションの指針・アーキテクチャの詳細は [CLAUDE.md](CLAUDE.md) を参照してください。
