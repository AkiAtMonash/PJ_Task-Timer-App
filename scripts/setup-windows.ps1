# TaskTimer — Windows セットアップ（PowerShell に貼って実行するだけ）
#
# やること：
#   1. ネットワークと前提コマンドの確認
#   2. Android Studio を入れる（winget 経由。すでにあれば飛ばす）
#   3. このリポジトリを取ってくる（すでにあれば最新に更新する）
#   4. 作業ブランチに切り替える
#
# 途中で失敗したら、その場で止まって理由を出す。
# 直したあと**もう一度そのまま実行すれば続きから進む**（何度流しても壊れない）。
#
# ここで自動化できないこと：
#   Android Studio の初回起動ウィザード（SDK のダウンロードとライセンス同意）は
#   画面をクリックするしかない。最後に手順を出す。

$ErrorActionPreference = 'Stop'

$RepoUrl = 'https://github.com/AkiAtMonash/PJ_Task-Timer-App'
$Branch = 'claude/project-visibility-check-0kqoeg'
$Parent = Join-Path $HOME 'Documents'
$RepoDir = Join-Path $Parent 'PJ_Task-Timer-App'
$ProjectDir = Join-Path $RepoDir 'task-timer-app'

function Step($n, $msg) { Write-Host "`n[$n] $msg" -ForegroundColor Cyan }
function Ok($msg) { Write-Host "    OK: $msg" -ForegroundColor Green }
function Warn($msg) { Write-Host "    ! $msg" -ForegroundColor Yellow }

function Fail($msg) {
    Write-Host "`n    失敗: $msg" -ForegroundColor Red
    Write-Host "    直したら、このスクリプトをもう一度そのまま実行してください。" -ForegroundColor Red
    Write-Host "    途中まで終わったところは飛ばして続きから進みます。`n" -ForegroundColor Red
    exit 1
}

# winget や git は PowerShell のコマンドレットではないので、
# $ErrorActionPreference='Stop' では止まってくれない。終了コードを自分で見る。
# （前の版はこれを見ておらず、失敗しても「OK」と出してしまっていた）
function Run($what, [scriptblock]$block) {
    & $block
    if ($LASTEXITCODE -ne 0) { Fail "$what （終了コード $LASTEXITCODE）" }
}

# ---------------------------------------------------------------
Step 1 'ネットワークと前提コマンド'

# 前回ここで転んだ。1.4GB 落とし始めてから切れると時間を丸ごと損するので先に見る
try {
    [System.Net.Dns]::GetHostEntry('github.com') | Out-Null
    Ok 'github.com の名前解決'
} catch {
    Write-Host "    ! github.com の名前を解決できません。" -ForegroundColor Yellow
    Write-Host "      ブラウザで https://github.com が開けるか確認してください。" -ForegroundColor Yellow
    Write-Host "      Wi-Fi の切断・VPN・DNS の不調が原因のことが多いです。" -ForegroundColor Yellow
    Fail 'ネットワークに繋がっていません'
}

try {
    [System.Net.Dns]::GetHostEntry('dl.google.com') | Out-Null
    Ok 'dl.google.com の名前解決'
} catch {
    Fail 'Google のダウンロードサーバに繋がりません（Android Studio が落とせません）'
}

if (-not (Get-Command winget -ErrorAction SilentlyContinue)) {
    Warn 'winget がありません。Microsoft Store の「アプリ インストーラー」を入れてください。'
    Warn 'または https://developer.android.com/studio から手動で入れて、[3] から手でやってください。'
    Fail 'winget が無い'
}
Ok 'winget'

if (-not (Get-Command git -ErrorAction SilentlyContinue)) {
    Warn 'git がありません。入れます…'
    Run 'git のインストール' { winget install --id Git.Git -e --accept-source-agreements --accept-package-agreements }
    Warn 'PowerShell を一度閉じて開き直してから、もう一度実行してください。'
    exit 0
}
Ok 'git'

# ---------------------------------------------------------------
Step 2 'Android Studio'

$installed = $false
try {
    winget list --id Google.AndroidStudio --exact 2>$null | Out-String | Select-String 'Google.AndroidStudio' | Out-Null
    $installed = ($LASTEXITCODE -eq 0)
} catch { $installed = $false }

if ($installed) {
    Ok 'すでに入っています'
} else {
    Write-Host '    入れています。1.4GB あるので時間がかかります…' -ForegroundColor White
    Write-Host '    （途中で切れたら、このスクリプトをもう一度実行すれば落とし直します）' -ForegroundColor DarkGray
    Run 'Android Studio のインストール' {
        winget install --id Google.AndroidStudio -e --accept-source-agreements --accept-package-agreements
    }
    Ok 'インストール完了'
}

# ---------------------------------------------------------------
Step 3 'リポジトリ'

if (Test-Path (Join-Path $RepoDir '.git')) {
    Ok "すでにあります: $RepoDir"
    Run 'リポジトリの取得' { git -C $RepoDir fetch origin }
} else {
    if (Test-Path $RepoDir) {
        Warn "$RepoDir がありますが Git リポジトリではありません。"
        Fail '名前を変えるか消してから、もう一度実行してください'
    }
    if (-not (Test-Path $Parent)) { New-Item -ItemType Directory -Path $Parent | Out-Null }
    Run 'clone' { git clone $RepoUrl $RepoDir }
    Ok "取得しました: $RepoDir"
}

Run 'ブランチの切り替え' { git -C $RepoDir checkout $Branch }
Run 'ブランチの更新' { git -C $RepoDir pull origin $Branch }
Ok "ブランチ: $Branch"

if (-not (Test-Path $ProjectDir)) {
    Fail "$ProjectDir がありません。取得がうまくいっていません"
}
Ok "プロジェクト: $ProjectDir"

# ---------------------------------------------------------------
Step 4 '古い local.properties の掃除'

# 以前このファイルが Git に入っていて、他人の PC のパスが固定で書かれていた。
# 残っていると「SDK location not found」の原因になる。
# 消しておけば Android Studio が開いたときに正しいパスで作り直す。
$LocalProps = Join-Path $ProjectDir 'local.properties'
if (Test-Path $LocalProps) {
    $content = Get-Content $LocalProps -Raw
    if ($content -notmatch [regex]::Escape($env:USERNAME)) {
        Remove-Item $LocalProps
        Ok '他の PC のパスが入っていたので消しました（Android Studio が作り直します）'
    } else {
        Ok 'この PC 用の設定になっています'
    }
} else {
    Ok 'ありません（Android Studio が作ります）'
}

# ---------------------------------------------------------------
Write-Host "`n────────────────────────────────────────────" -ForegroundColor Cyan
Write-Host " 自動でやれるところは全部終わりました" -ForegroundColor Cyan
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
