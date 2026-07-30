package com.aki.tasktimer.di

import android.content.Context
import com.aki.tasktimer.data.db.TaskTimerDatabase
import com.aki.tasktimer.data.repository.PresetRepository
import com.aki.tasktimer.data.repository.SessionRepository

/**
 * 手動 DI のコンテナ（docs/02_ARCHITECTURE.md 2 章）。
 * **Hilt は入れない。** 単一モジュールの個人アプリにはこれで足りる。
 *
 * by lazy にしているのは、DB ファイルのオープンをアプリ起動の同期処理から外すため。
 * Application.onCreate を重くすると起動が目に見えて遅くなる。
 */
class AppContainer(context: Context) {

    private val appContext = context.applicationContext

    private val database: TaskTimerDatabase by lazy { TaskTimerDatabase.build(appContext) }

    val sessionRepository: SessionRepository by lazy { SessionRepository(database.sessionDao()) }

    val presetRepository: PresetRepository by lazy { PresetRepository(database.presetDao()) }
}
