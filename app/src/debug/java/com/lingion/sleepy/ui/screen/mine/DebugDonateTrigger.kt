package com.lingion.sleepy.ui.screen.mine

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.VolunteerActivism
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lingion.sleepy.ui.theme.SleepyTheme

/**
 * debug 专用「手动触发赞赏弹窗」按钮 — 仅 `src/debug` 源集存在。
 *
 * 用户 2026-09-09 指令: 调试用应用内按钮, 不依赖 `adb shell am broadcast`。
 * release 构建编译 `src/release` 里的同名空实现 → 正式包既无按钮代码也无该文案
 * (比 BuildConfig.DEBUG 运行时判断更彻底: 字符串常量不会残留在 dex 字符串表)。
 */
@Composable
fun DebugDonateTrigger() {
    FilledTonalButton(
        onClick = { requestDonatePromptDebug() },
        modifier = Modifier.fillMaxWidth().height(SleepyTheme.Buttons.regularHeight),
        shape = SleepyTheme.Buttons.shape
    ) {
        Icon(Icons.Outlined.VolunteerActivism, contentDescription = null)
        Spacer(modifier = Modifier.width(8.dp))
        Text("DEBUG: 触发赞赏弹窗")
    }
}
