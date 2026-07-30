// ルートには何も実装しない。プラグインの「宣言だけ」して、適用は app モジュールで行う。
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
}
