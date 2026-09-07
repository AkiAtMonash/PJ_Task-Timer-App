package com.aki.tasktimer.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aki.tasktimer.data.model.SyncQueueItem
import com.aki.tasktimer.data.prefs.SettingsRepository
import com.aki.tasktimer.sync.SyncRepository
import java.time.ZoneId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsUiState(
    val syncEnabled: Boolean = false,
    val hasToken: Boolean = false,
    /** 保存はされているが鍵が失われて読めない。再入力を促す。 */
    val tokenUnreadable: Boolean = false,
    val databaseId: String = SettingsRepository.DEFAULT_DATABASE_ID,
    val tokenInput: String = "",
    val databaseIdInput: String = SettingsRepository.DEFAULT_DATABASE_ID,
    val pendingCount: Int = 0,
    val failedCount: Int = 0,
    val failedItems: List<SyncQueueItem> = emptyList(),
    /** 操作の結果を 1 行で知らせる。null なら何も出さない。 */
    val message: String? = null,
)

class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
    private val syncRepository: SyncRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                settingsRepository.observe(),
                syncRepository.observePendingCount(),
                syncRepository.observeFailedCount(),
                syncRepository.observeFailed(),
            ) { settings, pending, failed, failedItems ->
                Snapshot(settings.enabled, settings.hasToken, settings.databaseId, pending, failed, failedItems)
            }.collect { s ->
                _uiState.update { state ->
                    state.copy(
                        syncEnabled = s.enabled,
                        hasToken = s.hasToken,
                        databaseId = s.databaseId,
                        // 入力欄は保存値で初期化するが、編集中の値は上書きしない。
                        databaseIdInput = if (state.databaseIdInput == state.databaseId) s.databaseId else state.databaseIdInput,
                        pendingCount = s.pending,
                        failedCount = s.failed,
                        failedItems = s.failedItems,
                    )
                }
            }
        }
        // 保存済みトークンが読めるか確かめる。読めなければ Repository が消してくれるので、
        // ここでは「再入力してください」を出すためのフラグだけ立てる。
        viewModelScope.launch {
            val hadToken = settingsRepository.observe().first().hasToken
            if (hadToken && settingsRepository.getToken() == null) {
                _uiState.update { it.copy(tokenUnreadable = true) }
            }
        }
    }

    private data class Snapshot(
        val enabled: Boolean,
        val hasToken: Boolean,
        val databaseId: String,
        val pending: Int,
        val failed: Int,
        val failedItems: List<SyncQueueItem>,
    )

    fun setTokenInput(value: String) = _uiState.update { it.copy(tokenInput = value) }

    fun saveToken() {
        val token = _uiState.value.tokenInput.trim()
        if (token.isEmpty()) return
        viewModelScope.launch {
            runCatching { settingsRepository.saveToken(token) }
                .onSuccess {
                    _uiState.update { it.copy(tokenInput = "", tokenUnreadable = false, message = "トークンを保存しました") }
                }
                .onFailure { e -> _uiState.update { it.copy(message = "保存できませんでした: ${e.message}") } }
        }
    }

    fun clearToken() {
        viewModelScope.launch {
            settingsRepository.clearToken()
            _uiState.update { it.copy(tokenUnreadable = false, message = "トークンを削除し、連携を OFF にしました") }
        }
    }

    fun setSyncEnabled(enabled: Boolean) {
        val state = _uiState.value
        if (enabled && !state.hasToken) {
            _uiState.update { it.copy(message = "先にトークンを保存してください") }
            return
        }
        viewModelScope.launch {
            settingsRepository.setSyncEnabled(enabled)
            if (enabled) syncRepository.requestSync()
        }
    }

    fun setDatabaseIdInput(value: String) = _uiState.update { it.copy(databaseIdInput = value) }

    fun saveDatabaseId() {
        val value = _uiState.value.databaseIdInput
        viewModelScope.launch {
            runCatching { settingsRepository.setDatabaseId(value) }
                .onSuccess { _uiState.update { it.copy(message = "データベース ID を保存しました") } }
                .onFailure { e -> _uiState.update { it.copy(message = "保存できませんでした: ${e.message}") } }
        }
    }

    fun retryFailed() {
        viewModelScope.launch {
            syncRepository.retryAllFailed()
            _uiState.update { it.copy(message = "失敗した分を送り直します") }
        }
    }

    fun syncNow() {
        syncRepository.requestSync()
        _uiState.update { it.copy(message = "送信を開始しました") }
    }

    fun insertCrossMidnightTest() {
        viewModelScope.launch {
            runCatching {
                syncRepository.insertCrossMidnightTestSession(System.currentTimeMillis(), ZoneId.systemDefault())
            }.onSuccess {
                _uiState.update { it.copy(message = "日またぎのテスト記録を作りました。Notion に 2 行出るか確認してください") }
            }.onFailure { e ->
                _uiState.update { it.copy(message = "作れませんでした: ${e.message}") }
            }
        }
    }

    fun dismissMessage() = _uiState.update { it.copy(message = null) }
}
