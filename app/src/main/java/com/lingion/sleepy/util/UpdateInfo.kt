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
 * 镜像 API 没有返回 release body 时的最后一道日志兜底。
 * 这个页面仍然是 GitHub release 页, 但通过同一个镜像访问, 不要求 github.com 直连。
 */
fun updateMirrorReleasePageUrl(version: String): String =
    "$PROXY_PREFIX$GITHUB/$REPO_FULL/releases/tag/v${version.removePrefix("v")}"

/**
 * APK 下载候选地址(调用方逐个尝试): GitHub 直连优先, 镜像代理兜底。
 * 仅当 [directUrl] 是 github release 下载地址时生成代理候选; 其它地址原样返回。
 */
fun updateAssetUrlCandidates(directUrl: String): List<String> = when {
    directUrl.startsWith(releaseDlPrefix()) -> listOf(directUrl, "$PROXY_PREFIX$directUrl")
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
    val body = release.optString("body", "")
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

/**
 * 从镜像返回的 GitHub release HTML 中提取正文。
 *
 * 大多数镜像会原样转发 releases/latest API 的 body；少数镜像只转发版本/资产字段，
 * 这时再读取 release 页面，避免用户只能看到“更新日志在 GitHub”。解析只依赖
 * GitHub release 页面稳定的 markdown-body 容器，失败时返回空串，由 UI 显示兜底文案。
 */
fun parseMirrorPage(pageHtml: String): String {
    val open = Regex("""<div\b[^>]*class="[^"]*markdown-body[^"]*"[^>]*>""")
        .find(pageHtml) ?: return ""
    val contentStart = open.range.last + 1
    val token = Regex("""</?div\b[^>]*>""", RegexOption.IGNORE_CASE)
    var depth = 1
    var contentEnd = pageHtml.length
    for (match in token.findAll(pageHtml, contentStart)) {
        if (match.value.startsWith("</", ignoreCase = true)) {
            depth--
            if (depth == 0) {
                contentEnd = match.range.first
                break
            }
        } else {
            depth++
        }
    }
    if (depth != 0) return ""
    return htmlToMarkdown(pageHtml.substring(contentStart, contentEnd)).trim()
}

/** 将 release 页面正文中常见的 HTML 标签转换为 Markdown。 */
private fun htmlToMarkdown(html: String): String {
    var value = html
        .replace(Regex("""<script\b[^>]*>.*?</script>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)), "")
        .replace(Regex("""<style\b[^>]*>.*?</style>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)), "")
    value = Regex("""<h([1-6])[^>]*>(.*?)</h\1>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
        .replace(value) { match ->
            "\n\n${"#".repeat(match.groupValues[1].toInt())} ${match.groupValues[2].trim()}\n\n"
        }
    value = Regex("""<li[^>]*>(.*?)</li>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
        .replace(value) { match -> "\n- ${match.groupValues[1].trim()}" }
    value = Regex("""</?[uo]l[^>]*>""", RegexOption.IGNORE_CASE).replace(value, "\n")
    value = Regex("""<p[^>]*>(.*?)</p>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
        .replace(value) { match -> "\n\n${match.groupValues[1].trim()}\n\n" }
    value = Regex("""<(strong|b)[^>]*>(.*?)</\1>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
        .replace(value) { match -> "**${match.groupValues[2].trim()}**" }
    value = Regex("""<(em|i)[^>]*>(.*?)</\1>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
        .replace(value) { match -> "*${match.groupValues[2].trim()}*" }
    value = Regex("""<code[^>]*>(.*?)</code>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
        .replace(value) { match -> "`${match.groupValues[1].trim()}`" }
    value = Regex("""<a\b[^>]*href="([^"]*)"[^>]*>(.*?)</a>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
        .replace(value) { match -> "[${match.groupValues[2].trim()}](${match.groupValues[1]})" }
    value = Regex("""<br\s*/?>""", RegexOption.IGNORE_CASE).replace(value, "\n")
    value = Regex("""<[^>]+>""").replace(value, "")
    return decodeHtmlEntities(value).replace(Regex("""\n{3,}"""), "\n\n").trim()
}

private fun decodeHtmlEntities(value: String): String = value
    .replace("&amp;", "&")
    .replace("&lt;", "<")
    .replace("&gt;", ">")
    .replace("&quot;", "\"")
    .replace("&#39;", "'")
    .replace("&apos;", "'")
    .replace("&nbsp;", " ")
