package com.aki.tasktimer.block

import android.content.Context
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.util.Log
import android.view.Display
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.aki.tasktimer.ui.component.EmergencyExitConfirm
import com.aki.tasktimer.ui.component.EmergencyExitHotspot
import com.aki.tasktimer.ui.theme.Ink
import com.aki.tasktimer.ui.theme.OnInk
import com.aki.tasktimer.ui.theme.OnInkMuted
import com.aki.tasktimer.ui.theme.TaskTimerTheme
import com.aki.tasktimer.ui.theme.Warn

/**
 * 覆い（docs/01_SPEC.md 5.3）。他のアプリの上に被さる全画面の窓。
 *
 * - `TYPE_APPLICATION_OVERLAY` で、ホーム画面や他アプリの上に出る（ロック画面の上には出せない）
 * - 表示・非表示は addView / removeView で行う。自アプリが前面のときは必ず外す
 * - 右上に非常口（30 秒長押し）。**必ず実装し、ブロックを有効にする前にテストする**
 * - addView は権限が無いと例外になるので必ず握る。覆いが出せなくても他は動き続ける
 *
 * ComposeView をサービスの窓に載せるには LifecycleOwner と SavedStateRegistryOwner が要る。
 * Activity が無いのでここで小さな代役を用意している。
 */
class OverlayView(
    context: Context,
    private val onTap: () -> Unit,
    private val onEmergencyExit: () -> Unit,
) {

    // createWindowContext は「どの画面に出すか」が決まった Context からしか呼べない。
    // サービスや Application の Context は画面と結び付いていないので、先に既定の画面を指定する。
    private val windowContext = context.applicationContext
        .createDisplayContext(
            context.applicationContext.getSystemService(DisplayManager::class.java)
                .getDisplay(Display.DEFAULT_DISPLAY),
        )
        .createWindowContext(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, null)
    private val windowManager = windowContext.getSystemService(WindowManager::class.java)

    private var view: ComposeView? = null
    private var owner: OverlayLifecycleOwner? = null

    val isShowing: Boolean get() = view != null

    fun show() {
        if (view != null) return
        val newOwner = OverlayLifecycleOwner()
        val composeView = ComposeView(windowContext).apply {
            setViewTreeLifecycleOwner(newOwner)
            setViewTreeSavedStateRegistryOwner(newOwner)
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                TaskTimerTheme {
                    OverlayContent(onTap = onTap, onEmergencyExit = onEmergencyExit)
                }
            }
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            // 画面の端まで（ステータスバーの裏まで）覆う。FLAG_NOT_FOCUSABLE は付けない（戻るキーも握る）。
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            fitInsetsTypes = 0
        }
        try {
            newOwner.onShow()
            windowManager.addView(composeView, params)
            view = composeView
            owner = newOwner
        } catch (e: Exception) {
            // 権限が無い（SYSTEM_ALERT_WINDOW 未許可）など。覆い無しで続行する。
            Log.w(TAG, "覆いを表示できませんでした", e)
            newOwner.onHide()
        }
    }

    fun hide() {
        val v = view ?: return
        view = null
        try {
            windowManager.removeViewImmediate(v)
        } catch (e: Exception) {
            Log.w(TAG, "覆いを外せませんでした", e)
        }
        owner?.onHide()
        owner = null
    }

    /** ComposeView が要求する最小限のライフサイクル。表示中は RESUMED、外したら DESTROYED。 */
    private class OverlayLifecycleOwner : LifecycleOwner, SavedStateRegistryOwner {
        private val registry = LifecycleRegistry(this)
        private val controller = SavedStateRegistryController.create(this)

        override val lifecycle: Lifecycle get() = registry
        override val savedStateRegistry: SavedStateRegistry get() = controller.savedStateRegistry

        fun onShow() {
            controller.performRestore(null)
            registry.currentState = Lifecycle.State.RESUMED
        }

        fun onHide() {
            registry.currentState = Lifecycle.State.DESTROYED
        }
    }

    private companion object {
        const val TAG = "OverlayView"
    }
}

@Composable
private fun OverlayContent(onTap: () -> Unit, onEmergencyExit: () -> Unit) {
    var confirming by remember { mutableStateOf(false) }
    val line = remember { OverlayMessages.random() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Ink.copy(alpha = 0.97f))
            .clickable(onClick = onTap),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 32.dp),
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = OverlayMessages.HEADLINE,
                color = Warn,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = line,
                color = OnInk,
                fontSize = 15.sp,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(40.dp))
            Text(
                text = "── タップして戻る ──",
                color = OnInkMuted,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 2.sp,
            )
        }

        EmergencyExitHotspot(
            onTriggered = { confirming = true },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding(),
        )

        if (confirming) {
            EmergencyExitConfirm(
                onConfirm = onEmergencyExit,
                onCancel = { confirming = false },
            )
        }
    }
}
