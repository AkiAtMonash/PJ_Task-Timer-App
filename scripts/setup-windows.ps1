# TaskTimer — Windows セットアップ（PowerShell に貼って実行するだけ）
#
# やること：
#   1. Android Studio を入れる（winget 経由。すでにあれば飛ばす）
#   2. このリポジトリを取ってくる（すでにあれば最新に更新する）
#   3. 作業ブランチに切り替える
#
# ここで自動化できないこと：
#   Android Studio の初回起動ウィザード（SDK のダウンロードとライセンス同意）は
#   画面をクリックするしかない。スクリプトの最後に手順を出す。
#
# 使い方：PowerShell を開いて、このファイルの中身を全部貼り付けて Enter。

$ErrorActionPreference = 'Stop'

$RepoUrl = 'https://github.com/AkiAtMonash/PJ_Task-Timer-App'
$Branch = 'claude/project-visibility-check-0kqoeg'
$Parent = Join-Path $HOME 'Documents'
$RepoDir = Join-Path $Parent 'PJ_Task-Timer-App'
$ProjectDir = Join-Path $RepoDir 'task-timer-app'

function Step($n, $msg) { Write-Host "`n[$n] $msg" -ForegroundColor Cyan }
function Ok($msg) { Write-Host "    OK: $msg" -ForegroundColor Green }
function Warn($msg) { Write-Host "    ! $msg" -ForegroundColor Yellow }

# ---------------------------------------------------------------
Step 1 '前提コマンドの確認'

if (-not (Get-Command winget -ErrorAction SilentlyContinue)) {
    Warn 'winget がありません。Microsoft Store から「アプリ インストーラー」を入れてください。'
    Warn 'または https://developer.android.com/studio から手動で Android Studio を入れて、'
    Warn 'このスクリプトの [2] を飛ばして [3] から手でやってください。'
    exit 1
}
Ok 'winget'

if (-not (Get-Command git -ErrorAction SilentlyContinue)) {
    Warn 'git がありません。入れます…'
    winget install --id Git.Git -e --accept-source-agreements --accept-package-agreements
    Warn 'git を入れました。PowerShell を一度閉じて開き直してから、もう一度実行してください。'
    exit 1
}
Ok 'git'

# ---------------------------------------------------------------
Step 2 'Android Studio'

$studio = winget list --id Google.AndroidStudio 2>$null | Select-String 'Google.AndroidStudio'
if ($studio) {
    Ok 'すでに入っています'
} else {
    Write-Host '    入れています。数 GB あるので時間がかかります…'
    winget install --id Google.AndroidStudio -e --accept-source-agreements --accept-package-agreements
    Ok 'インストール完了'
}

# ---------------------------------------------------------------
Step 3 'リポジトリ'

if (Test-Path (Join-Path $RepoDir '.git')) {
    Ok "すでにあります: $RepoDir"
    git -C $RepoDir fetch origin
} else {
    if (-not (Test-Path $Parent)) { New-Item -ItemType Directory -Path $Parent | Out-Null }
    if (Test-Path $RepoDir) {
        Warn "$RepoDir がありますが Git リポジトリではありません。"
        Warn '名前を変えるか消してから、もう一度実行してください。'
        exit 1
    }
    git clone $RepoUrl $RepoDir
    Ok "取得しました: $RepoDir"
}

git -C $RepoDir checkout $Branch
git -C $RepoDir pull origin $Branch
Ok "ブランチ: $Branch"

# ---------------------------------------------------------------
Step 4 '古い local.properties の掃除'

# 以前このファイルが Git に入っていて、他人の PC のパスが書かれていた。
# 残っていると「SDK location not found」の原因になる。
# 消しておけば Android Studio が開いたときに正しいパスで作り直す。
$LocalProps = Join-Path $ProjectDir 'local.properties'
if (Test-Path $LocalProps) {
    $content = Get-Content $LocalProps -Raw
    $expected = ($env:LOCALAPPDATA -replace '\\', '\\\\')
    if ($content -notmatch [regex]::Escape($env:USERNAME)) {
        Remove-Item $LocalProps
        Ok '他の PC のパスが入っていたので消しました（Android Studio が作り直します）'
    } else {
        Ok 'このPC用の設定になっています'
    }
} else {
    Ok 'ありません（Android Studio が作ります）'
}

# ---------------------------------------------------------------
Write-Host "`n────────────────────────────────────────────" -ForegroundColor Cyan
Write-Host " ここから先は画面の操作が必要です" -ForegroundColor Cyan
Write-Host "────────────────────────────────────────────`n" -ForegroundColor Cyan

Write-Host @"
1. Android Studio を起動する

2. 初回だけセットアップウィザードが出る
   - 「Standard」を選ぶ
   - SDK のダウンロードが始まる（数 GB・時間がかかる）
   - ライセンスは全部 Accept

3. Welcome 画面で「Open」を押して、次のフォルダを選ぶ
   （リポジトリの直下ではなく、その中の task-timer-app）

     $ProjectDir

4. 右下で Gradle の同期が走る。初回は 15 分かかることがある

5. 同期が終わったら、上のメニューから
     Build → Make Project

6. 結果を Claude に伝える
   - 通った → 実機確認に進む
   - エラー → 赤い文字をそのままコピーして貼る

"@ -ForegroundColor White
