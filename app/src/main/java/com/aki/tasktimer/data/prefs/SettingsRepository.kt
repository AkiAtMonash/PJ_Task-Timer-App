package com.aki.tasktimer.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** 設定画面に見せる Notion 連携の状態。トークンの平文はここに載せない。 */
data class SyncSettings(
    val enabled: Boolean,
    val databaseId: String,
    val hasToken: Boolean,
)

// DataStore はプロセスに 1 つ。トップレベルで宣言するのが公式の作法。
private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/**
 * アプリ設定の保存（DataStore Preferences）。SharedPreferences は使わない（docs/02 2 章）。
 *
 * トークンは [TokenCipher] で暗号化した文字列だけを保存する。
 */
class SettingsRepository(
    context: Context,
    private val cipher: TokenCipher,
) {

    private val dataStore = context.applicationContext.settingsDataStore

    fun observe(): Flow<SyncSettings> = dataStore.data.map { prefs ->
        SyncSettings(
            enabled = prefs[KEY_SYNC_ENABLED] ?: false,
            databaseId = prefs[KEY_DATABASE_ID] ?: DEFAULT_DATABASE_ID,
            hasToken = !prefs[KEY_TOKEN_ENC].isNullOrBlank(),
        )
    }

    suspend fun isSyncEnabled(): Boolean = observe().first().enabled

    suspend fun getDatabaseId(): String = observe().first().databaseId

    /**
     * 復号したトークン。無い、または読めない（鍵が失われた）なら null。
     * 読めない場合は保存値も消して、設定画面が「再入力」を促せるようにする。
     */
    suspend fun getToken(): String? {
        val stored = dataStore.data.first()[KEY_TOKEN_ENC] ?: return null
        if (stored.isBlank()) return null
        val plain = cipher.decrypt(stored)
        if (plain == null) clearToken()
        return plain
    }

    suspend fun saveToken(token: String) {
        val trimmed = token.trim()
        require(trimmed.isNotEmpty()) { "トークンが空です" }
        val encrypted = cipher.encrypt(trimmed)
        dataStore.edit { it[KEY_TOKEN_ENC] = encrypted }
    }

    suspend fun clearToken() {
        dataStore.edit {
            it.remove(KEY_TOKEN_ENC)
            // トークンが無いのに ON のままだと、行列が溜まるだけで何も起きない。
            it[KEY_SYNC_ENABLED] = false
        }
    }

    suspend fun setSyncEnabled(enabled: Boolean) {
        dataStore.edit { it[KEY_SYNC_ENABLED] = enabled }
    }

    suspend fun setDatabaseId(databaseId: String) {
        val cleaned = databaseId.trim().replace("-", "")
        require(cleaned.isNotEmpty()) { "データベース ID が空です" }
        dataStore.edit { it[KEY_DATABASE_ID] = cleaned }
    }

    companion object {
        /** 既存の「⏱️ DB_タイムログ」（docs/06 2 章）。設定画面で変えられる。 */
        const val DEFAULT_DATABASE_ID = "18a0bc4f73378145ae19d00b3921f39b"

        private val KEY_SYNC_ENABLED = booleanPreferencesKey("sync_enabled")
        private val KEY_DATABASE_ID = stringPreferencesKey("notion_database_id")
        private val KEY_TOKEN_ENC = stringPreferencesKey("notion_token_enc")
    }
}
