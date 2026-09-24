package com.aki.tasktimer.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.aki.tasktimer.TaskTimerApp
import com.aki.tasktimer.data.db.dao.SessionDao
import com.aki.tasktimer.data.db.dao.SyncQueueDao
import com.aki.tasktimer.data.db.entity.toDomain
import com.aki.tasktimer.data.model.Session
import com.aki.tasktimer.data.model.SyncOperation
import com.aki.tasktimer.data.prefs.SettingsRepository
import com.aki.tasktimer.domain.planFinish
import java.time.ZoneId
import kotlinx.coroutines.delay

/**
 * 送信待ち行列を上から順に Notion へ送る。
 *
 * 動き：
 * 1. 同期 OFF、またはトークン無しなら何もせず終わる（行列はそのまま残る）
 * 2. PENDING の先頭を取り、CREATE なら「進行中」ページを作る、FINISH なら「完了」に書き換える
 * 3. 成功 → DONE。トークン誤りなど直さないと通らないもの → FAILED（次の行へ進む）。
 *    通信エラーやレート制限 → 回数を数えて Worker ごと再実行（WorkManager が間隔を空ける）
 * 4. 行列が空になったら終了
 *
 * 1 件の失敗で全体を止めない。ただし順序は守る：Retryable で止まった行より後ろは送らない
 * （前タスクの完了より先に次タスクの進行中が出ると、Notion 上で順序が狂う）。
 */
class NotionSyncWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    private val container = (applicationContext as TaskTimerApp).container
    private val settings: SettingsRepository get() = container.settingsRepository
    private val queueDao: SyncQueueDao get() = container.syncQueueDao
    private val sessionDao: SessionDao get() = container.sessionDao
    private val api: NotionApi get() = container.notionApi

    override suspend fun doWork(): Result {
        if (!settings.isSyncEnabled()) return Result.success()
        val token = settings.getToken() ?: return Result.success()
        val databaseId = settings.getDatabaseId()
        val zone = ZoneId.systemDefault()

        while (true) {
            val item = queueDao.nextPending() ?: return Result.success()
            val session = sessionDao.getById(item.sessionId)?.toDomain()
            if (session == null) {
                queueDao.markFailed(item.id, "対応する記録が端末にありません（id=${item.sessionId}）")
                continue
            }

            val result = when (item.operation) {
                SyncOperation.CREATE -> create(token, databaseId, session, zone)
                SyncOperation.FINISH -> finish(token, databaseId, session, zone)
            }

            when (result) {
                is NotionResult.Ok -> queueDao.markDone(item.id)
                is NotionResult.ClientError -> queueDao.markFailed(item.id, result.message)
                is NotionResult.Retryable -> {
                    queueDao.bumpRetry(item.id, result.message)
                    if (item.retryCount + 1 >= MAX_RETRY) {
                        // 諦めて FAILED にし、後ろの行に進む（設定画面の「再試行」で戻せる）。
                        queueDao.markFailed(item.id, "再試行の上限（$MAX_RETRY 回）に達しました: ${result.message}")
                    } else {
                        // WorkManager に間隔を空けて再実行してもらう。この行より後ろは送らない。
                        return Result.retry()
                    }
                }
            }
            // Notion は平均 3 リクエスト/秒まで。起動直後に行列が溜まっていても超えないよう間を置く。
            delay(REQUEST_INTERVAL_MS)
        }
    }

    /** 「進行中」ページを作り、ページ id を記録に紐づける。 */
    private suspend fun create(token: String, databaseId: String, session: Session, zone: ZoneId): NotionResult {
        // すでにページを持っているなら作らない。id を控えた直後に送信が打ち切られると行列は
        // PENDING のまま残り、次に起きたときに同じ開始をもう一度送って 2 枚目ができる。
        // 通信を伴わない歯止めなので、下の照会が通らない場所（圏外・照会失敗）でも効く。
        session.notionPageId?.let { return NotionResult.Ok(it) }

        // 先に Notion へ聞く（ADR 0002）。端末が寝て送信が打ち切られると、Notion にはページが
        // できているのにアプリは id を控えられない。そのまま作り直すと 2 枚目ができ、1 枚目が
        // 「進行中」のまま取り残される。実際に 2026-09 に 7 件溜まった。
        adopt(token, databaseId, session)?.let { return NotionResult.Ok(it) }

        val result = api.createPage(token, NotionPayload.createRunningPage(databaseId, session, zone))
        if (result is NotionResult.Ok) queueDao.setNotionPageId(session.id, result.pageId)
        return result
    }

    /**
     * 開始時刻が一致する「進行中」ページが Notion にすでにあれば、それを自分のものとして引き取る。
     * 照会できなかった／無かったときは null（呼び出し側は今までどおり作る）。
     */
    private suspend fun adopt(token: String, databaseId: String, session: Session): String? {
        val pages = api.queryRunningPages(token, databaseId) ?: return null
        val pageId = pages.firstOrNull { NotionPayload.sameSession(it, session) }?.id
            ?: return null
        queueDao.setNotionPageId(session.id, pageId)
        return pageId
    }

    /**
     * 「完了」に書き換える。日をまたいでいれば 2 日目以降のページも作る。
     *
     * ページ id を持っていない場合は Notion に聞き、それでも見つからなければ
     * 全区間を「完了」で新しく作る。終了だけ送れないのはもったいない。
     *
     * 途中で Retryable になった場合は行ごと再実行になる。PATCH は同じ値を書き直すだけなので
     * 何度やっても結果は同じ。POST 済みの区間をもう一度作ると重複ページになるが、
     * v1 では許容する（計画書「気をつけること」）。
     */
    private suspend fun finish(token: String, databaseId: String, session: Session, zone: ZoneId): NotionResult {
        val endedAt = session.endedAt
            ?: return NotionResult.ClientError(0, "まだ終わっていない記録です（id=${session.id}）")
        val plan = planFinish(session.startedAt, endedAt, zone)
            ?: return NotionResult.ClientError(0, "終了が開始より前です（id=${session.id}）")

        // 開始時の送信が打ち切られていると notionPageId は空だが、Notion 側にはページがある。
        // ここでも一度聞いて、あればそれを完了に書き換える（新しく作ると取り残しが残る）。
        val pageId = session.notionPageId ?: adopt(token, databaseId, session)
        val firstResult: NotionResult = if (pageId != null) {
            api.updatePage(token, pageId, NotionPayload.finishPage(session, plan.patchSegment, zone))
        } else {
            api.createPage(
                token,
                NotionPayload.createCompletedPage(databaseId, session, plan.patchSegment, zone),
            ).also { result ->
                if (result is NotionResult.Ok) queueDao.setNotionPageId(session.id, result.pageId)
            }
        }
        if (firstResult !is NotionResult.Ok) return firstResult

        for (segment in plan.extraSegments) {
            delay(REQUEST_INTERVAL_MS)
            val result = api.createPage(
                token,
                NotionPayload.createCompletedPage(databaseId, session, segment, zone),
            )
            if (result !is NotionResult.Ok) return result
        }
        return firstResult
    }

    private companion object {
        const val REQUEST_INTERVAL_MS = 350L
        const val MAX_RETRY = 15
    }
}
