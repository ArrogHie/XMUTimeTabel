package com.lingion.sleepy.ui.screen.mine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 「我的-帮助」内容漂移闸门 (用户 2026-09-09 指令)。
 *
 * HelpScreen 渲染的是内嵌常量 [HELP_MARKDOWN](docs/ 不打进 APK), 而用户要求
 * 内容取自仓库根 docs/HELP.md。两处一旦漂移, 用户看到的帮助就不是文档里的内容,
 * 且纯 JVM 测试无 Compose 无法察觉。这里逐字节(归一化换行)比对二者, 改文案时
 * 必须同步 docs/HELP.md 与 HelpContent.kt, 否则本测试失败。
 */
class HelpContentTest {

    private val helpFile: File = sequenceOf(
        File("docs/HELP.md"),
        File("../docs/HELP.md")
    ).first { it.isFile }

    private fun normalize(s: String) = s.replace("\r\n", "\n").trim()

    @Test
    fun embedded_markdown_matches_docs_help_md() {
        assertEquals(
            "HelpContent.HELP_MARKDOWN 与 docs/HELP.md 不一致 — 改文案时请同步两处",
            normalize(helpFile.readText()),
            normalize(HELP_MARKDOWN)
        )
    }

    @Test
    fun help_markdown_has_question_sections() {
        // 每个 `## ` 标题都会渲染成一张问答卡; 空内容页没有意义
        val headings = HELP_MARKDOWN.lines().count { it.startsWith("## ") }
        assertTrue("帮助内容应至少包含一个 `## 标题` 问答项, 实际 $headings", headings >= 1)
    }

    @Test
    fun every_heading_becomes_one_entry_with_its_body() {
        val entries = parseHelpEntries(HELP_MARKDOWN)
        // 每个 `## ` 标题 = 一张卡
        assertEquals(HELP_MARKDOWN.lines().count { it.startsWith("## ") }, entries.size)
        // 每张卡的问题非空、答案非空(docs/HELP.md 每条标题下都有正文)
        assertTrue("存在空的问答项: $entries", entries.all { it.first.isNotBlank() && it.second.isNotBlank() })
        // 首条与文档首行一致
        assertEquals("无法登录", entries.first().first)
    }

    @Test
    fun parses_headings_without_blank_line_between() {
        // docs/HELP.md 是 `## 标题\n正文` 紧挨着的紧凑格式, 解析必须按标题切卡
        val entries = parseHelpEntries("## A\nbody a\n## B\nbody b\n")
        assertEquals(listOf("A" to "body a", "B" to "body b"), entries)
    }
}
