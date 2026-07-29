# 04. 環境構築ガイド（Android 開発が初めての人向け）

Web 開発の経験がある前提で、Android 特有のところだけ丁寧に書く。
所要時間はダウンロード待ちを含めて **1〜2 時間**。

---

## 0. Web 開発との対応表（先に頭を作る）

| Android | Web で言うと |
|---|---|
| Android Studio | VS Code ＋ ビルド環境が同梱されたもの。実質必須 |
| Gradle | npm + webpack。**初回は依存の解決に 5〜15 分かかる。フリーズではない** |
| `libs.versions.toml` | `package.json` の dependencies |
| AGP（Android Gradle Plugin） | ビルドツールチェーン本体 |
| SDK / Platform Tools | Node のバージョン + CLI ツール群 |
| APK | ビルド成果物。`dist/` に出る bundle 相当 |
| ADB | 実機と通信するための CLI。`adb` コマンド |

**一番違うところ**：Web と違ってホットリロードが弱い。
コードを変えてから実機に反映されるまで **10〜60 秒**かかる。これが Android 開発の体感速度。
（Compose のプレビューと Live Edit を使えば UI だけは速く回せる）

---

## 1. Android Studio のインストール

1. https://developer.android.com/studio から **Android Studio** をダウンロード
2. インストーラを実行。途中の選択肢は**すべてデフォルトのまま**でよい
3. 初回起動時にセットアップウィザードが走る
   - 「Standard」を選ぶ
   - SDK のダウンロードが始まる（数 GB。時間がかかる）
   - ライセンス同意を求められたら全部 Accept

**確認**：起動して "Welcome to Android Studio" の画面が出れば OK。

---

## 2. 必要な SDK を追加する

デフォルトでは最新の SDK が入っていない場合がある。

1. Welcome 画面 → 右上の **⚙️ → SDK Manager**
   （プロジェクトを開いている場合は `Tools → SDK Manager`）
2. **SDK Platforms** タブ
   - 右下の「Show Package Details」にチェック
   - **Android 16 (API 36)** を探してチェック
     - `Android SDK Platform 36`
     - `Google APIs Intel x86_64 System Image`（エミュレータを使うなら）
3. **SDK Tools** タブ
   - `Android SDK Build-Tools`（最新）
   - `Android SDK Platform-Tools` ← **これが `adb`。必須**
   - `Android SDK Command-line Tools`
4. Apply → ダウンロード

---

## 3. Pixel 9a を開発者モードにする

**スマホ側の操作。**

1. **設定 → デバイス情報**
2. **ビルド番号** を **7 回連続タップ**
   （「あと 3 回でデベロッパーになれます」というトーストが出る）
3. PIN / パターンを求められたら入力
4. 「デベロッパーになりました！」と出れば成功

次に USB デバッグを有効化：

5. **設定 → システム → 開発者向けオプション**
6. **USB デバッグ** を ON
7. （あると便利）**USB 経由でインストール** も ON

---

## 4. PC と Pixel 9a を繋ぐ

1. USB ケーブルで接続
   - **データ転送に対応したケーブルを使うこと。** 充電専用ケーブルだと認識しない。
     ここでハマる人が非常に多い
2. スマホに「USB デバッグを許可しますか？」というダイアログが出る
   → **「このパソコンからのＵＳＢデバッグを常に許可する」にチェック → 許可**
3. スマホの通知から「USB の使用目的」を **「ファイル転送」** に変更
   （「充電のみ」だと認識しないことがある）

**確認**：PC のターミナル（PowerShell / コマンドプロンプト）で

```
adb devices
```

```
List of devices attached
XXXXXXXXXXXX    device
```

と出れば成功。

- `unauthorized` と出る → スマホ側のダイアログをまだ許可していない
- 何も出ない → ケーブルを疑う。別のケーブル・別のポートを試す
- `adb` が見つからない → 環境変数 PATH に platform-tools を追加する
  （Windows のデフォルト位置：`C:\Users\<ユーザー名>\AppData\Local\Android\Sdk\platform-tools`）

### ワイヤレスデバッグ（おすすめ）

ケーブルが煩わしければ、開発者向けオプションの **「ワイヤレス デバッグ」** を使うと
Wi-Fi 経由で繋げる。PC とスマホが同じ Wi-Fi にいる必要がある。

```
adb pair <スマホに表示された IP:ポート>   # ペア設定コードを入力
adb connect <IP:5555>
```

---

## 5. プロジェクトを作る

**この作業は Claude Code にやらせてよい。** 自分でやる場合：

1. Android Studio → **New Project**
2. **Empty Activity**（Compose 版。"Empty Views Activity" ではないので注意）
3. 設定
   - Name: `TaskTimer`
   - Package name: `com.aki.tasktimer`
   - Save location: **このフォルダの中に `app/` を作る**
   - Minimum SDK: **API 34 (Android 14)**
   - Build configuration language: **Kotlin DSL (build.gradle.kts)**
4. Finish

初回は Gradle の同期に **5〜15 分**かかる。下部の進捗バーが動いていれば正常。

---

## 6. 実機で動かす

1. Android Studio 上部のデバイス選択ドロップダウンで **Pixel 9a** を選ぶ
2. ▶️（Run）ボタン、または `Shift + F10`
3. 初回ビルドは数分。2 回目以降は数十秒

スマホにアプリが自動でインストールされて起動すれば成功。

### コマンドラインからやる場合

```bash
./gradlew installDebug     # ビルドして実機にインストール
./gradlew assembleDebug    # APK を作るだけ
./gradlew test             # ユニットテスト（実機不要・速い）
```

Windows は `gradlew.bat` を使う（Git Bash なら `./gradlew` のままで動く）。

---

## 7. 覚えておくと救われる ADB コマンド

**Phase 5（オーバーレイブロック）を作るとき、これを知らないと詰む。**

```bash
# アプリを強制終了する ← ★オーバーレイで画面が固まったときの脱出手段
adb shell am force-stop com.aki.tasktimer

# アプリをアンインストール
adb uninstall com.aki.tasktimer

# ログを見る（Web の console.log 相当）
adb logcat | grep TaskTimer

# 画面をキャプチャ
adb exec-out screencap -p > screen.png
```

**Phase 5 に入る前に `force-stop` が効くことを必ず確認しておくこと。**

---

## 8. よくあるトラブル

| 症状 | 原因と対処 |
|---|---|
| `adb devices` に何も出ない | ケーブルが充電専用。データ転送対応のものに変える |
| `unauthorized` | スマホ側の許可ダイアログを見落としている。ケーブルを挿し直す |
| Gradle sync が終わらない | 初回は 15 分かかることがある。ネットワークも確認 |
| `SDK location not found` | `local.properties` に SDK パスがない。Android Studio で開き直すと自動生成される |
| ビルドは通るのにインストールできない | 開発者向けオプションの「USB 経由でインストール」を ON にする |
| `INSTALL_FAILED_UPDATE_INCOMPATIBLE` | 署名が違う。一度アンインストールしてから入れ直す |
| Compose のプレビューが表示されない | Build → Rebuild Project |
| 変更が反映されない | Build → Clean Project → Rebuild Project |

---

## 9. `local.properties` は Git に入れない

プロジェクト直下の `local.properties` には PC 固有の SDK パスが入る。
`.gitignore` に含まれているはずだが、念のため確認すること。
