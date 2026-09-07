package com.lingion.sleepy.util

/**
 * 远端 release 信息(纯数据,不含 Android 依赖,可单测)。
 *
 * [isUpdateAvailable] 由 [parseReleaseJson] 根据版本比较 + force flag 算出。
 * 调用方(UpdateManager)据它决定弹窗 vs Toast。
 */
data class UpdateInfo(
    val version: String,
    val changelog: String,
    val downloadUrl: String,
    val isUpdateAvailable: Boolean
)

private const val FORCE_FLAG = "SLEEPY_FORCE_UPDATE=true"

/** 更新源仓库 — 迁移仓库/换源只改这里。 */
private const val REPO_FULL = "ArrogHie/XMUTimeTabel"
private const val GITHUB = "https://github.com"
private const val GITHUB_API = "https://api.github.com"

/**
 * 实测可用的代理前缀(2026-09-07 验证), 代理格式 = 前缀 + 目标 URL 原样。
 * 通过: gh-proxy.com — APK 资产下载 200 全尺寸; api.github.com JSON 代理 200。
 * 未通过(不接入): relv.xgheaven.com.cn(DNS 不存在)、gitclone.com(500/502/404)、
 * gh.qdp.qzz.io(TLS 握手失败)。
 */
private const val PROXY_PREFIX = "https://gh-proxy.com/"

private fun apiLatest(): String = "$GITHUB_API/repos/$REPO_FULL/releases/latest"
private fun releaseDlPrefix(): String = "$GITHUB/$REPO_FULL/releases/download/"

/**
 * 拉取 release 信息的候选地址(调用方逐个尝试): 直连优先, 镜像代理兜底。
 * 镜像代理返回同一份 releases/latest JSON, 解析路径与直连完全相同。
 */
fun updateInfoUrlCandidates(): List<String> =
    listOf(apiLatest(), "$PROXY_PREFIX${apiLatest()}")

/**
 * APK 下载候选地址(调用方逐个尝试): 代理优先(加速), GitHub 直连兜底。
 * 仅当 [directUrl] 是 github release 下载地址时生成代理候选; 其它地址原样返回。
 */
fun updateAssetUrlCandidates(directUrl: String): List<String> = when {
    directUrl.startsWith(releaseDlPrefix()) -> listOf("$PROXY_PREFIX$directUrl", directUrl)
    else -> listOf(directUrl)
}

/**
 * 解析 GitHub releases/latest 的 JSON 为 [UpdateInfo](纯函数,无 IO)。
 *
 * [abi] 形如 "arm64-v8a" / "armeabi-v7a" / "x86_64",用于挑对应 asset。
 * 找不到对应 asset 时 downloadUrl 返回空串(调用方更新下载候选时自行兜底)。
 * downloadUrl 保留 browser_download_url 的 GitHub 直连原值; 代理改写统一放到
 * 调用方按 [updateAssetUrlCandidates] 生成候选(镜像是否可用属运行时网络事实,
 * 解析层不掺和)。
 */
fun parseReleaseJson(json: String, currentVersion: String, abi: String): UpdateInfo {
    val release = org.json.JSONObject(json)
    val version = release.optString("tag_name").removePrefix("v")
    val body = release.optString("body")
    val assetName = "app-$abi-release.apk"
    val downloadUrl = release.optJSONArray("assets")?.let { assets ->
        (0 until assets.length()).map { assets.getJSONObject(it) }
            .firstOrNull { it.optString("name") == assetName }
            ?.optString("browser_download_url").orEmpty()
    } ?: ""
    val force = body.contains(FORCE_FLAG)
    val isUpdateAvailable = force ||
        VersionUtils.compare(version.ifBlank { "0" }, currentVersion) > 0
    return UpdateInfo(version, body, downloadUrl, isUpdateAvailable)
}
