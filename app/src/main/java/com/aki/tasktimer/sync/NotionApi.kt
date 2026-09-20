package com.aki.tasktimer.sync

import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

/**
 * Notion API 呼び出しの結果。例外を外に投げず、必ずこの 3 択に畳む。
 * 送信側（Worker）は「成功 / 直さないと通らない / 待てば通るかも」の 3 つだけ考えればよい。
 */
sealed interface NotionResult {
    /** 成功。[pageId] は作成・更新したページの id。 */
    data class Ok(val pageId: String) : NotionResult

    /** 400 / 401 / 403 / 404 など。トークンや DB ID を直さない限り何度送っても同じ。 */
    data class ClientError(val code: Int, val message: String) : NotionResult

    /** 429（レート制限）/ 5xx / 通信エラー。間を空けて再送すれば通る見込みがある。 */
    data class Retryable(val message: String) : NotionResult
}

/**
 * Notion API の薄いラッパ（docs/06 4 章）。ページの作成と更新しかしない。
 *
 * OkHttp を使う理由：ページ更新は HTTP の PATCH で、Android 同梱の HttpURLConnection は
 * PATCH を受け付けない（ProtocolException）。
 */
class NotionApi(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build(),
    private val baseUrl: String = "https://api.notion.com/v1",
) {

    suspend fun createPage(token: String, body: JSONObject): NotionResult =
        execute(
            Request.Builder()
                .url("$baseUrl/pages")
                .post(body.toString().toRequestBody(JSON))
                .headers(token)
                .build(),
        )

    /**
     * 「進行中」のページを一覧する。
     *
     * 照会そのものに失敗したら null を返す。呼び出し側は「分からなかった」として
     * 通常どおり作りに行く（照会が理由で記録が残らないほうが困る）。
     */
    suspend fun queryRunningPages(token: String, databaseId: String): List<NotionPayload.PageRef>? =
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url("$baseUrl/databases/$databaseId/query")
                .post(NotionPayload.runningPagesQuery().toString().toRequestBody(JSON))
                .headers(token)
                .build()
            runCatching {
                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) NotionPayload.parsePageRefs(response.body?.string().orEmpty()) else null
                }
            }.getOrNull()
        }

    suspend fun updatePage(token: String, pageId: String, body: JSONObject): NotionResult =
        execute(
            Request.Builder()
                .url("$baseUrl/pages/$pageId")
                .patch(body.toString().toRequestBody(JSON))
                .headers(token)
                .build(),
        )

    private fun Request.Builder.headers(token: String): Request.Builder =
        header("Authorization", "Bearer $token")
            .header("Notion-Version", NOTION_VERSION)
            .header("Content-Type", "application/json")

    private suspend fun execute(request: Request): NotionResult = withContext(Dispatchers.IO) {
        try {
            client.newCall(request).execute().use { response ->
                val text = response.body?.string().orEmpty()
                when {
                    response.isSuccessful -> {
                        val id = runCatching { JSONObject(text).optString("id") }.getOrDefault("")
                        if (id.isBlank()) {
                            NotionResult.Retryable("応答にページ id がありません")
                        } else {
                            NotionResult.Ok(id)
                        }
                    }
                    response.code == 429 || response.code >= 500 ->
                        NotionResult.Retryable("HTTP ${response.code}: ${summarize(text)}")
                    else ->
                        NotionResult.ClientError(response.code, "HTTP ${response.code}: ${summarize(text)}")
                }
            }
        } catch (e: IOException) {
            NotionResult.Retryable("通信エラー: ${e.message ?: e.javaClass.simpleName}")
        }
    }

    /** Notion のエラー本文は JSON で message を持つ。設定画面に出すので短くする。 */
    private fun summarize(body: String): String {
        val message = runCatching { JSONObject(body).optString("message") }.getOrDefault("")
        val text = message.ifBlank { body }
        return if (text.length > MAX_ERROR_LENGTH) text.take(MAX_ERROR_LENGTH) + "…" else text
    }

    private companion object {
        val JSON = "application/json; charset=utf-8".toMediaType()

        // docs/06 4 章と同じ。MacroDroid で実績のある版に固定する。
        const val NOTION_VERSION = "2022-06-28"
        const val MAX_ERROR_LENGTH = 300
    }
}
