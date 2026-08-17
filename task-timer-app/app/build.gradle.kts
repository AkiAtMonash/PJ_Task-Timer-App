plugins {
    // AGP 9.0 は Kotlin Gradle Plugin を内部で適用済み。
    // ここで org.jetbrains.kotlin.android を重ねると
    // "Cannot add extension with name 'kotlin'" で落ちる。書かないのが正解。
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.aki.tasktimer"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.aki.tasktimer"
        minSdk = 34
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        release {
            // 個人利用。R8 の難読化はリフレクション絡みが壊れる原因になるだけなので入れない。
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }
}

ksp {
    // マイグレーションを書くときに前バージョンのスキーマ JSON が必要になるので出力しておく。
    // 出力先: app/schemas/com.aki.tasktimer.data.db.TaskTimerDatabase/1.json
    arg("room.schemaLocation", "$projectDir/schemas")
}

kotlin {
    compilerOptions {
        // compileOptions（Java 17）と揃えないと
        // "Inconsistent JVM-target compatibility" でビルドが落ちる。
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)

    implementation(libs.androidx.room.runtime)
    ksp(libs.androidx.room.compiler)

    implementation(libs.kotlinx.coroutines.core)

    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
}
