package com.lingion.sleepy.util

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class UpdateInfoParseTest {

    private val sampleBody = """{"tag_name":"v1.0.32","body":"## v1.0.32\n\n修复 bug","assets":[
        {"name":"app-arm64-v8a-release.apk","browser_download_url":"https://example.com/a.apk"},
        {"name":"app-armeabi-v7a-release.apk","browser_download_url":"https://example.com/b.apk"}]}"""

    @Test
    fun parses_version_changelog_url_from_github_json() {
        val info = parseReleaseJson(sampleBody, "1.0.31", "arm64-v8a")
        assertEquals("1.0.32", info.version)
        assertEquals("## v1.0.32\n\n修复 bug", info.changelog)
        assertEquals("https://example.com/a.apk", info.downloadUrl)
        assertTrue(info.isUpdateAvailable)
    }

    @Test
    fun older_remote_version_is_not_an_update() {
        val info = parseReleaseJson(sampleBody, "1.0.33", "arm64-v8a")
        assertFalse(info.isUpdateAvailable)
    }

    @Test
    fun same_version_with_force_flag_is_update() {
        val body = sampleBody.replace("修复 bug", "修复 bug SLEEPY_FORCE_UPDATE=true")
        val info = parseReleaseJson(body, "1.0.32", "arm64-v8a")
        assertTrue(info.isUpdateAvailable)
    }

    @Test
    fun picks_correct_abi_asset() {
        val info = parseReleaseJson(sampleBody, "1.0.31", "armeabi-v7a")
        assertEquals("https://example.com/b.apk", info.downloadUrl)
    }

    @Test
    fun missing_asset_for_abi_returns_blank_url() {
        val noX86 = """{"tag_name":"v2.0.0","body":"x","assets":[
            {"name":"app-arm64-v8a-release.apk","browser_download_url":"https://example.com/a.apk"}]}"""
        val info = parseReleaseJson(noX86, "1.0.0", "x86_64")
        assertEquals("", info.downloadUrl)
    }

    // ─── 更新源候选列表(2026-09-07: 单一镜像不可靠, 改候选逐个尝试) ───

    @Test
    fun info_candidates_prefer_direct_then_gh_proxy() {
        val urls = updateInfoUrlCandidates()
        assertEquals(2, urls.size)
        // 直连 API 在前
        assertTrue(urls[0].startsWith("https://api.github.com/repos/ArrogHie/XMUTimeTabel/releases/latest"))
        // 代理 = 前缀 + 直连 URL
        assertEquals("https://gh-proxy.com/${urls[0]}", urls[1])
    }

    @Test
    fun github_download_asset_candidates_are_direct_then_proxy() {
        val direct = "https://github.com/ArrogHie/XMUTimeTabel/releases/download/v1.0.47/app-arm64-v8a-release.apk"
        val urls = updateAssetUrlCandidates(direct)
        assertEquals(listOf(direct, "https://gh-proxy.com/$direct"), urls)
    }

    @Test
    fun non_github_asset_url_has_single_candidate() {
        // 非 github release 下载地址(测试样例/自定义源)不做代理改写, 单候选直取
        val foreign = "https://example.com/a.apk"
        assertEquals(listOf(foreign), updateAssetUrlCandidates(foreign))
    }

    @Test
    fun parse_keeps_direct_github_download_url() {
        // downloadUrl 保留直连原值, 代理候选由 updateAssetUrlCandidates 生成
        val githubAssetBody = """{"tag_name":"v1.0.47","body":"x","assets":[
            {"name":"app-arm64-v8a-release.apk","browser_download_url":"https://github.com/ArrogHie/XMUTimeTabel/releases/download/v1.0.47/app-arm64-v8a-release.apk"}]}"""
        val info = parseReleaseJson(githubAssetBody, "1.0.46", "arm64-v8a")
        assertEquals(
            "https://github.com/ArrogHie/XMUTimeTabel/releases/download/v1.0.47/app-arm64-v8a-release.apk",
            info.downloadUrl
        )
    }

    @Test
    fun blank_download_url_yields_single_direct_candidate() {
        // 找不到对应 ABI asset → downloadUrl 空 → 单候选空串(下载层直接报错, 不产生幽灵代理 URL)
        assertEquals(listOf(""), updateAssetUrlCandidates(""))
    }

    @Test
    fun mirror_release_page_provides_changelog_when_api_body_is_missing() {
        val page = """
            <div class="markdown-body px-3">
              <h2>v1.1.3</h2>
              <p>修复 &amp; 优化</p>
              <ul><li>课程表刷新</li><li>更新提示</li></ul>
              <div class="nested"><p>附加说明</p></div>
            </div>
        """.trimIndent()

        val notes = parseMirrorPage(page)

        assertTrue(notes.contains("## v1.1.3"))
        assertTrue(notes.contains("修复 & 优化"))
        assertTrue(notes.contains("- 课程表刷新"))
        assertTrue(notes.contains("附加说明"))
    }

    @Test
    fun mirror_release_page_url_keeps_version_tag() {
        assertEquals(
            "https://gh-proxy.com/https://github.com/ArrogHie/XMUTimeTabel/releases/tag/v1.1.3",
            updateMirrorReleasePageUrl("v1.1.3")
        )
    }
}
