package com.aki.tasktimer.di

import android.content.Context
import com.aki.tasktimer.data.db.TaskTimerDatabase
import com.aki.tasktimer.data.db.dao.SessionDao
import com.aki.tasktimer.data.db.dao.SyncQueueDao
import com.aki.tasktimer.data.prefs.SettingsRepository
import com.aki.tasktimer.data.prefs.TokenCipher
import com.aki.tasktimer.data.repository.PresetRepository
import com.aki.tasktimer.data.repository.SessionRepository
import com.aki.tasktimer.sync.NotionApi
import com.aki.tasktimer.sync.SyncRepository
import com.aki.tasktimer.sync.SyncTrigger
import com.aki.tasktimer.sync.WorkManagerSyncTrigger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * 手動 DI のコンテナ（docs/02_ARCHITECTURE.md 2 章）。
 * **Hilt は入れない。** 単一モジュールの個人アプリにはこれで足りる。
 *
 * by lazy にしているのは、DB ファイルのオープンをアプリ起動の同期処理から外すため。
 * Application.onCreate を重くすると起動が目に見えて遅くなる。
 */
class AppContainer(context: Context) {

    private val appContext = context.applicationContext

    /** 画面に紐づかない仕事（起動時の送信キック等）用。プロセスが生きている間ずっと有効。 */
    val applicationScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val database: TaskTimerDatabase by lazy { TaskTimerDatabase.build(appContext) }

    // Worker が直接 DAO を使うので公開する。画面からは Repository 経由で触ること。
    val sessionDao: SessionDao by lazy { database.sessionDao() }
    val syncQueueDao: SyncQueueDao by lazy { database.syncQueueDao() }

    val settingsRepository: SettingsRepository by lazy {
        SettingsRepository(appContext, TokenCipher())
    }

    val syncTrigger: SyncTrigger by lazy { WorkManagerSyncTrigger(appContext) }

    val notionApi: NotionApi by lazy { NotionApi() }

    val sessionRepository: SessionRepository by lazy {
        SessionRepository(
            sessionDao = sessionDao,
            isSyncEnabled = { settingsRepository.isSyncEnabled() },
            syncTrigger = syncTrigger,
        )
    }

    val presetRepository: PresetRepository by lazy { PresetRepository(database.presetDao()) }

    val syncRepository: SyncRepository by lazy {
        SyncRepository(syncQueueDao, sessionDao, syncTrigger)
    }
}
