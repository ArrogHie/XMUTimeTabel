package com.lingion.sleepy.ui.screen.mine

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lingion.sleepy.R
import com.lingion.sleepy.ui.theme.SleepyTheme
import com.lingion.sleepy.util.AppPrefs
import com.lingion.sleepy.util.DonateReminder
import java.time.LocalDate

private const val GITHUB_REPO_URL = "https://github.com/ArrogHie/XMUTimeTabel"

/**
 * debug 版手动触发赞赏弹窗的请求计数(文件级一次性状态, 同 generalSettingsRequest 惯例)。
 * 每次 [requestDonatePromptDebug] 自增; DonatePromptHost 观察到变化即绕过"满 5 天/已关闭"
 * 判定强制弹窗, 供真机调试。release 构建里没有入口调用它, 值恒为 0 → 行为与正式版一致。
 */
internal val donatePromptDebugRequest = androidx.compose.runtime.mutableIntStateOf(0)

/** 手动触发一次赞赏提醒弹窗(仅 debug 入口调用)。 */
fun requestDonatePromptDebug() {
    donatePromptDebugRequest.value = donatePromptDebugRequest.value + 1
}

/** 消费 debug 触发请求(置 0), 避免旋转/重组后重复弹出。 */
internal fun consumeDonatePromptDebug() {
    donatePromptDebugRequest.value = 0
}

/**
 * 赞赏 / Star 提醒弹窗 (用户 2026-09-09 指令)。
 *
 * 触发: 使用满 [DonateReminder.MIN_USED_DAYS] 天后弹一次(判定在 [DonatePromptHost])。
 * 四个动作:
 *   - 去 GitHub 点 Star → 浏览器打开仓库, 视为已响应 → 关闭
 *   - 赞赏作者 → 打开 App 内赞赏页(二维码) → 视为已响应 → 关闭
 *   - 下次提醒 → 推迟 [DonateReminder.SNOOZE_DAYS] 天
 *   - 再也不要显示 → 永久关闭
 *
 * 两个"跳转"动作都算用户已看到并响应, 关闭后同样按推迟处理 —— 避免用户点完 Star
 * 返回 App 又立刻被同一个弹窗拦住。
 */
@Composable
fun DonatePromptDialog(
    usedDays: Long,
    onOpenDonatePage: () -> Unit,
    onDismissLater: () -> Unit,
    onNeverShow: () -> Unit,
) {
    val colors = SleepyTheme.colors
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismissLater,
        titleContentColor = colors.onSurface,
        containerColor = colors.surfaceContainerHigh,
        title = {
            Text(
                stringResource(R.string.donate_prompt_title),
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
            )
        },
        text = {
            Text(
                stringResource(R.string.donate_prompt_message, usedDays.toInt()),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant
            )
        },
        confirmButton = {
            Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.End) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(GITHUB_REPO_URL)))
                        onDismissLater()
                    }) {
                        Text(stringResource(R.string.donate_prompt_github))
                    }
                    TextButton(onClick = {
                        onOpenDonatePage()
                        onDismissLater()
                    }) {
                        Text(stringResource(R.string.donate_prompt_donate))
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onNeverShow) {
                        Text(stringResource(R.string.donate_prompt_never))
                    }
                    TextButton(onClick = onDismissLater) {
                        Text(stringResource(R.string.donate_prompt_later))
                    }
                }
            }
        }
    )
}

/**
 * 赞赏提醒宿主 — 挂在 AppRoot 外层(同 StartupUpdatePrompt), 冷启动后判定一次。
 *
 * @param onOpenDonatePage 打开 App 内赞赏页
 * @param debugRequest debug 触发计数: 值变化时强制弹一次(绕过天数/关闭判定)
 */
@Composable
fun DonatePromptHost(
    onOpenDonatePage: () -> Unit,
    debugRequest: Int = 0,
) {
    val context = LocalContext.current
    var visible by remember { mutableStateOf(false) }
    var usedDays by remember { mutableStateOf(DonateReminder.MIN_USED_DAYS) }

    // 首次启动记录使用起始日; 之后每次冷启动判定一次是否该弹
    androidx.compose.runtime.LaunchedEffect(debugRequest) {
        val today = LocalDate.now().toEpochDay()
        val firstDay = AppPrefs.markFirstUseDay(context, today)
        val forceShow = debugRequest > 0
        if (forceShow) consumeDonatePromptDebug()
        val should = forceShow || DonateReminder.shouldShow(
            firstUseDay = firstDay,
            today = today,
            snoozeUntil = AppPrefs.getDonateSnoozeUntil(context),
            dismissed = AppPrefs.isDonateDismissed(context),
        )
        if (should) {
            usedDays = (today - firstDay + 1).coerceAtLeast(1L)
            visible = true
        }
    }

    if (visible) {
        DonatePromptDialog(
            usedDays = usedDays,
            onOpenDonatePage = onOpenDonatePage,
            onDismissLater = {
                AppPrefs.snoozeDonatePrompt(context)
                visible = false
            },
            onNeverShow = {
                AppPrefs.dismissDonateForever(context)
                visible = false
            },
        )
    }
}
