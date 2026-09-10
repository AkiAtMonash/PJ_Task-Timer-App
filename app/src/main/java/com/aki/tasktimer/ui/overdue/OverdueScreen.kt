package com.aki.tasktimer.ui.overdue

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.OutlinedTextField
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aki.tasktimer.domain.elapsedMinutes
import com.aki.tasktimer.domain.overrunRate
import com.aki.tasktimer.domain.totalRequiredMinutes
import com.aki.tasktimer.ui.component.EmergencyExitConfirm
import com.aki.tasktimer.ui.component.EmergencyExitHotspot
import com.aki.tasktimer.ui.component.GoalRow
import com.aki.tasktimer.ui.component.Pill
import com.aki.tasktimer.ui.component.PrimaryButton
import com.aki.tasktimer.ui.component.SecondaryButton
import com.aki.tasktimer.ui.component.TagChip
import com.aki.tasktimer.ui.theme.OnInk
import com.aki.tasktimer.ui.theme.OnInkMuted
import com.aki.tasktimer.ui.theme.Outline
import com.aki.tasktimer.ui.theme.Warn

/**
 * 超過画面（docs/01_SPEC.md 4.4）。このアプリの心臓部。
 *
 * - 延長ボタンは「選択」するだけ。画面下部の「合計所要時間」1 か所だけが書き換わる
 * - 超過率の分母は当初の予定（延長で膨らませない）。150% 超で警告色
 * - 右上に非常口（30 秒長押し）
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OverdueScreen(
    state: OverdueUiState,
    onExtendMinutes: (Int) -> Unit,
    onCustomInput: (String) -> Unit,
    onExtend: () -> Unit,
    onSwitchTask: () -> Unit,
    onEmergencyExit: () -> Unit,
    onDismissError: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var confirmingExit by remember { mutableStateOf(false) }

    Scaffold(modifier = modifier.fillMaxSize()) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize()) {
            val session = state.session
            if (session != null) {
                val elapsed = elapsedMinutes(session.startedAt, state.now)
                val rate = overrunRate(elapsed, session.plannedMinutes)
                val total = totalRequiredMinutes(elapsed, state.selectedMinutes ?: 0)

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .padding(horizontal = 16.dp, vertical = 24.dp),
                ) {
                  // 2 つの出口（延長・中断）は必ず画面内に置く。上の説明側だけスクロールさせる。
                  // 横向きだと縦が足りず、下のボタンが画面外に出て逃げ道が押せなくなるため。
                  Column(
                      modifier = Modifier
                          .weight(1f)
                          .verticalScroll(rememberScrollState()),
                  ) {
                    Text(
                        text = "予定時間を超過",
                        color = Warn,
                        fontSize = 12.sp,
                        letterSpacing = 3.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TagChip(tag = session.tag)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = session.name,
                            color = OnInk,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    if (session.goal.isNotBlank()) {
                        GoalRow(
                            goal = session.goal,
                            textColor = OnInk,
                            fontSize = 16.sp,
                            modifier = Modifier.padding(top = 12.dp),
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = "経過 ${elapsed}分 / 予定 ${session.plannedMinutes}分",
                            color = OnInkMuted,
                            fontSize = 13.sp,
                            fontFamily = FontFamily.Monospace,
                        )
                        Text(
                            text = "${rate}%",
                            color = if (rate >= 150) Warn else OnInk,
                            fontSize = 56.sp,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = FontFamily.Monospace,
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider(color = Outline)
                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "あと何分やる？",
                        color = OnInk,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        state.presets.forEach { preset ->
                            // 押したら即延長。押す前に総量が分かるよう「計○○分」を併記する
                            // （docs/01_SPEC.md 4.4-1、2026-09-09 に 1 タップ化）。
                            Pill(
                                text = "+${preset.minutes}",
                                subText = "計 ${totalRequiredMinutes(elapsed, preset.minutes)}分",
                                onClick = { onExtendMinutes(preset.minutes) },
                            )
                        }
                        // 一番右：自由入力。プリセットに無い分数を使いたいとき用。
                        // ここだけは毎文字で延長できないので、下の「延長する」で確定させる。
                        OutlinedTextField(
                            value = state.customInput,
                            onValueChange = onCustomInput,
                            placeholder = { Text("自由", fontSize = 13.sp) },
                            suffix = { Text("分", fontSize = 13.sp, color = OnInkMuted) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Number,
                                imeAction = ImeAction.Done,
                            ),
                            keyboardActions = KeyboardActions(onDone = { onExtend() }),
                            textStyle = LocalTextStyle.current.copy(fontSize = 14.sp),
                            modifier = Modifier
                                .width(104.dp)
                                .height(60.dp),
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                  }

                    // 自由入力を打ったときだけ押せる。プリセットは押した時点で延長済み。
                    PrimaryButton(
                        text = if (state.selectedMinutes != null) "延長する（計 ${total}分）" else "延長する",
                        onClick = onExtend,
                        enabled = state.selectedMinutes != null && !state.isSaving,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    SecondaryButton(
                        text = "タスクを終了する",
                        onClick = onSwitchTask,
                        enabled = !state.isSaving,
                    )
                }
            } else {
                Text(
                    text = "進行中のタスクがありません",
                    color = OnInkMuted,
                    modifier = Modifier.align(Alignment.Center),
                )
            }

            EmergencyExitHotspot(
                onTriggered = { confirmingExit = true },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding(),
            )

            if (confirmingExit) {
                EmergencyExitConfirm(
                    onConfirm = onEmergencyExit,
                    onCancel = { confirmingExit = false },
                )
            }
        }
    }

    state.error?.let { error ->
        AlertDialog(
            onDismissRequest = onDismissError,
            title = { Text("エラー") },
            text = { Text(error) },
            confirmButton = { TextButton(onClick = onDismissError) { Text("閉じる") } },
        )
    }
}
