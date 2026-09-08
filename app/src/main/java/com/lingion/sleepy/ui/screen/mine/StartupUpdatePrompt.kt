package com.lingion.sleepy.ui.screen.mine

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.lingion.sleepy.R
import com.lingion.sleepy.util.AppPrefs
import com.lingion.sleepy.util.UpdateInfo
import com.lingion.sleepy.util.UpdateManager
import com.lingion.sleepy.util.UpdateNotifier
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * 冷启动更新提示。它放在 AppRoot 外层, 所以无论当前停留在哪个页面都会显示。
 * “稍后提醒”只关闭本次弹窗; “忽略本次更新”才会把版本写入偏好设置。
 */
@Composable
fun StartupUpdatePrompt() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val available by UpdateNotifier.updateAvailable.collectAsState()
    var state by remember { mutableStateOf<UpdateUiState>(UpdateUiState.Idle) }
    var downloadJob by remember { mutableStateOf<Job?>(null) }

    LaunchedEffect(available?.version) {
        val info = available
        if (info != null && info.isUpdateAvailable && state is UpdateUiState.Idle) {
            state = UpdateUiState.UpdateAvailable(
                info.version,
                info.changelog,
                info.downloadUrl
            )
        } else if (info == null && state is UpdateUiState.UpdateAvailable) {
            state = UpdateUiState.Idle
        }
    }

    fun dismissForLater() {
        UpdateNotifier.dismissCurrent()
        state = UpdateUiState.Idle
    }

    fun ignoreCurrentUpdate() {
        val version = available?.version
            ?: (state as? UpdateUiState.UpdateAvailable)?.version
            ?: (state as? UpdateUiState.Failed)?.version.orEmpty()
        AppPrefs.setIgnoredUpdateVersion(context, version)
        UpdateNotifier.dismissCurrent()
        state = UpdateUiState.Idle
    }

    fun startDownload(version: String, changelog: String, url: String) {
        // 先摘掉启动提示源, 防止下载过程中 StateFlow 变化导致弹窗重复打开。
        UpdateNotifier.dismissCurrent()
        val info = UpdateInfo(version, changelog, url, true)
        state = UpdateUiState.Downloading(0)
        downloadJob = scope.launch {
            runCatching {
                UpdateManager.downloadApk(context, info) { progress ->
                    state = UpdateUiState.Downloading(progress)
                }
            }.onSuccess { file ->
                state = UpdateUiState.Installing
                UpdateManager.install(context, file)
            }.onFailure { error ->
                if (error is kotlinx.coroutines.CancellationException) {
                    state = UpdateUiState.UpdateAvailable(version, changelog, url)
                } else {
                    state = UpdateUiState.Failed(
                        error.message ?: context.getString(R.string.error_unknown),
                        version,
                        changelog,
                        url
                    )
                }
            }
        }
    }

    UpdateChangelogDialog(
        state = state,
        onDismiss = { dismissForLater() },
        onDownload = { version, changelog, url -> startDownload(version, changelog, url) },
        onCancelDownload = { downloadJob?.cancel() },
        onRetry = { version, changelog, url -> startDownload(version, changelog, url) },
        isStartupPrompt = true,
        onIgnore = { ignoreCurrentUpdate() },
        onRemindLater = { dismissForLater() }
    )
}
