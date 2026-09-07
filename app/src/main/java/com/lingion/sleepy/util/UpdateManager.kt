package com.lingion.sleepy.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.content.FileProvider
import com.lingion.sleepy.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlin.coroutines.coroutineContext

/**
 * 拉 GitHub/国内代理 release 信息、下载 APK、清理旧 APK。不含 UI 状态。
 *
 * 网络策略(2026-09-07 实测调整): 单一镜像不可靠(gh.qdp.qzz.io TLS 失败 /
 * relv DNS 不存在 / gitclone 网关错误), 改为「候选地址列表逐个尝试」:
 *   - 信息检查: GitHub API 直连 → gh-proxy 代理的同一 API JSON
 *   - APK 下载: gh-proxy 加速 → GitHub 直连
 * 某个候选失败(超时/断连/HTTP 错)立即换下一个, 全挂才报错。
 */
object UpdateManager {
    private const val TAG = "UpdateManager"

    private fun currentAbiAsset(): String = when {
        Build.SUPPORTED_ABIS.any { it == "arm64-v8a" } -> "app-arm64-v8a-release.apk"
        Build.SUPPORTED_ABIS.any { it == "armeabi-v7a" } -> "app-armeabi-v7a-release.apk"
        Build.SUPPORTED_ABIS.any { it == "x86_64" } -> "app-x86_64-release.apk"
        else -> "app-arm64-v8a-release.apk"
    }

    private fun currentAbi(): String = currentAbiAsset()
        .removePrefix("app-").removeSuffix("-release.apk")

    /**
     * 只拉 release 信息,不下载。按候选逐个尝试: GitHub API 直连失败(国内常见)
     * → gh-proxy 代理的同一 JSON; 代理也失败才抛错。
     */
    suspend fun fetchUpdateInfo(context: Context): UpdateInfo = withContext(Dispatchers.IO) {
        val abi = currentAbi()
        val candidates = updateInfoUrlCandidates()
        var lastError: Throwable? = null
        for (url in candidates) {
            try {
                val json = readText(url)
                val info = parseReleaseJson(json, BuildConfig.VERSION_NAME, abi)
                if (info.version.isBlank()) throw IllegalStateException("empty version")
                return@withContext info
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                lastError = e
                Log.w(TAG, "update info source failed: $url", e)
            }
        }
        throw lastError ?: IllegalStateException(
            context.getString(com.lingion.sleepy.R.string.error_no_version_found)
        )
    }

    /**
     * 下载 APK 到 cacheDir,带进度回调(0-100)。
     * 候选列表: 代理加速 → GitHub 直连。协程 cancel 时删半截文件。
     */
    suspend fun downloadApk(
        context: Context, info: UpdateInfo, onProgress: (Int) -> Unit
    ): File = withContext(Dispatchers.IO) {
        val target = File(context.cacheDir, "sleepy-update-${currentAbiAsset()}")
        val candidates = updateAssetUrlCandidates(info.downloadUrl)
        var lastError: Throwable? = null
        for (url in candidates) {
            try {
                downloadOnce(url, target, onProgress)
                if (!target.isFile || target.length() == 0L)
                    throw IllegalStateException(context.getString(com.lingion.sleepy.R.string.error_empty_download))
                return@withContext target
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                lastError = e
                Log.w(TAG, "update download failed: $url", e)
                target.delete()
            }
        }
        throw lastError ?: IllegalStateException(
            context.getString(com.lingion.sleepy.R.string.error_empty_download)
        )
    }

    private suspend fun downloadOnce(
        url: String, target: File, onProgress: (Int) -> Unit
    ) {
        val conn = request(url)
        val total = conn.contentLengthLong.coerceAtLeast(1L)
        try {
            conn.inputStream.use { input ->
                target.outputStream().use { output ->
                    val buf = ByteArray(8 * 1024)
                    var downloaded = 0L
                    while (true) {
                        coroutineContext.ensureActive()
                        val n = input.read(buf)
                        if (n < 0) break
                        output.write(buf, 0, n)
                        downloaded += n
                        onProgress((downloaded * 100 / total).toInt().coerceIn(0, 100))
                    }
                }
            }
        } catch (e: Exception) {
            target.delete()
            throw e
        } finally {
            conn.disconnect()
        }
    }

    /** 启动时清理 cacheDir 中旧安装包。 */
    fun cleanOldApk(context: Context) {
        context.cacheDir.listFiles { it.name.startsWith("sleepy-update-") }
            ?.forEach { it.delete() }
    }

    fun install(context: Context, file: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(intent)
    }

    private fun readText(url: String): String {
        val conn = request(url)
        return try { conn.inputStream.bufferedReader().use { it.readText() } }
        finally { conn.disconnect() }
    }

    private fun request(url: String): HttpURLConnection {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 15_000
        conn.readTimeout = 60_000
        conn.instanceFollowRedirects = true
        conn.setRequestProperty("User-Agent", "Sleepy/${BuildConfig.VERSION_NAME}")
        conn.setRequestProperty("Accept", "application/json,text/html,*/*")
        if (conn.responseCode !in 200..299) {
            conn.disconnect()
            throw IllegalStateException("HTTP ${conn.responseCode}")
        }
        return conn
    }
}
