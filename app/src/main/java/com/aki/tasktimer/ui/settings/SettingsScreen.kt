package com.aki.tasktimer.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aki.tasktimer.BuildConfig
import com.aki.tasktimer.TaskTimerApp
import com.aki.tasktimer.ui.component.PrimaryButton
import com.aki.tasktimer.ui.component.SecondaryButton
import com.aki.tasktimer.ui.theme.Ink
import com.aki.tasktimer.ui.theme.InkVariant
import com.aki.tasktimer.ui.theme.OnInk
import com.aki.tasktimer.ui.theme.OnInkMuted
import com.aki.tasktimer.ui.theme.Warn

/**
 * 設定画面。今は Notion 連携だけ。延長プリセット・権限などは Phase 7 でここに足す。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val app = LocalContext.current.applicationContext as TaskTimerApp
    val viewModel: SettingsViewModel = viewModel {
        SettingsViewModel(app.container.settingsRepository, app.container.syncRepository)
    }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.message) {
        val message = uiState.message ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        viewModel.dismissMessage()
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("設定", color = OnInk) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "戻る", tint = OnInk)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Ink),
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            SectionTitle("Notion 連携")

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("タイムログに自動で記録する", color = OnInk, fontSize = 14.sp)
                    if (!uiState.hasToken) {
                        Text("トークンを保存すると ON にできます", color = OnInkMuted, fontSize = 12.sp)
                    }
                }
                Switch(
                    checked = uiState.syncEnabled,
                    onCheckedChange = viewModel::setSyncEnabled,
                    enabled = uiState.hasToken || uiState.syncEnabled,
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
            SectionTitle("トークン")

            if (uiState.tokenUnreadable) {
                Text(
                    text = "保存していたトークンが読めなくなりました。もう一度貼り付けてください。",
                    color = Warn,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            } else if (uiState.hasToken) {
                Text(
                    text = "保存済み（••••••••）",
                    color = OnInkMuted,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }
            OutlinedTextField(
                value = uiState.tokenInput,
                onValueChange = viewModel::setTokenInput,
                label = { Text(if (uiState.hasToken) "新しいトークンで置き換える" else "Notion のインテグレーショントークン") },
                placeholder = { Text("ntn_…") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row {
                PrimaryButton(
                    text = "保存",
                    onClick = viewModel::saveToken,
                    enabled = uiState.tokenInput.isNotBlank(),
                    modifier = Modifier.weight(1f),
                )
                if (uiState.hasToken) {
                    Spacer(modifier = Modifier.width(8.dp))
                    SecondaryButton(
                        text = "削除",
                        onClick = viewModel::clearToken,
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            SectionTitle("データベース ID")

            OutlinedTextField(
                value = uiState.databaseIdInput,
                onValueChange = viewModel::setDatabaseIdInput,
                label = { Text("⏱️ DB_タイムログ の ID") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(8.dp))
            SecondaryButton(
                text = "保存",
                onClick = viewModel::saveDatabaseId,
                enabled = uiState.databaseIdInput.trim().isNotEmpty() && uiState.databaseIdInput.trim() != uiState.databaseId,
            )

            Spacer(modifier = Modifier.height(24.dp))
            SectionTitle("送信状況")

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(3.dp))
                    .background(InkVariant)
                    .padding(12.dp),
            ) {
                Text(
                    text = "未送信 ${uiState.pendingCount} 件 / 失敗 ${uiState.failedCount} 件",
                    color = if (uiState.failedCount > 0) Warn else OnInk,
                    fontSize = 14.sp,
                    fontFamily = FontFamily.Monospace,
                )
                uiState.failedItems.firstOrNull()?.lastError?.let { error ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = error, color = OnInkMuted, fontSize = 12.sp)
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row {
                SecondaryButton(
                    text = "再試行",
                    onClick = viewModel::retryFailed,
                    enabled = uiState.failedCount > 0,
                    modifier = Modifier.weight(1f),
                )
                Spacer(modifier = Modifier.width(8.dp))
                SecondaryButton(
                    text = "今すぐ送信",
                    onClick = viewModel::syncNow,
                    enabled = uiState.syncEnabled && uiState.pendingCount > 0,
                    modifier = Modifier.weight(1f),
                )
            }

            if (BuildConfig.DEBUG) {
                Spacer(modifier = Modifier.height(32.dp))
                SectionTitle("開発用")
                Text(
                    text = "「昨日 23:50 〜 今日 0:20」の完了済み記録を作って送ります。Notion に 2 行出れば日またぎの分割が動いています。履歴にも残ります。",
                    color = OnInkMuted,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                SecondaryButton(
                    text = "日またぎのテスト記録を作る",
                    onClick = viewModel::insertCrossMidnightTest,
                    enabled = uiState.syncEnabled,
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        color = OnInkMuted,
        fontSize = 10.sp,
        letterSpacing = 2.sp,
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(bottom = 12.dp),
    )
}
