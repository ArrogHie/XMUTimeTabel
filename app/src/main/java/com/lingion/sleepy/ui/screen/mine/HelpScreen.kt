package com.lingion.sleepy.ui.screen.mine

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.lingion.sleepy.R
import com.lingion.sleepy.ui.theme.SleepyTheme
import com.lingion.sleepy.util.MarkdownBlocks

/**
 * 「我的-帮助」二级页 (用户 2026-09-09 指令)。
 *
 * 内容 = docs/HELP.md, 内嵌为 [HELP_MARKDOWN](docs/ 不进 APK), 由 MarkdownBlocks
 * 解析成确定性的块结构后显式排版 — 与更新日志弹窗同一套渲染语义, 无第三方依赖。
 * 每条 `## 标题` 渲染为一张问答卡: 标题 + 正文段落。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HelpScreen(onBack: () -> Unit) {
    val colors = SleepyTheme.colors
    // 解析一次即可 — 常量内容不会变
    val entries = remember { parseHelpEntries(HELP_MARKDOWN) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.help_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = colors.background,
                    titleContentColor = colors.onBackground,
                    navigationIconContentColor = colors.onBackground
                )
            )
        },
        containerColor = colors.background
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp)
        ) {
            items(entries) { entry ->
                HelpEntryCard(question = entry.first, answer = entry.second)
            }
        }
    }
}

/** docs/HELP.md 的 `## 标题` + 紧随正文 → (问题, 回答) 对。internal 供纯 JVM 单测。 */
internal fun parseHelpEntries(markdown: String): List<Pair<String, String>> {
    val entries = mutableListOf<Pair<String, String>>()
    var question: String? = null
    val answer = StringBuilder()

    fun flush() {
        val q = question ?: return
        entries += q to answer.toString().trim()
        answer.clear()
    }

    for (block in MarkdownBlocks.parse(markdown)) {
        when (block) {
            is MarkdownBlocks.Block.Heading -> {
                flush()
                question = block.text
            }
            is MarkdownBlocks.Block.Paragraph -> {
                if (answer.isNotEmpty()) answer.append('\n')
                answer.append(block.text)
            }
            is MarkdownBlocks.Block.Bullet -> {
                if (answer.isNotEmpty()) answer.append('\n')
                answer.append(block.items.joinToString("\n") { "· $it" })
            }
        }
    }
    flush()
    return entries
}

@Composable
private fun HelpEntryCard(question: String, answer: String) {
    val colors = SleepyTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(colors.surfaceContainer)
            .padding(16.dp)
    ) {
        Text(
            text = question,
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
            color = colors.onSurface
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = helpInlineAnnotated(answer, colors.onSurfaceVariant, colors.primary),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onSurfaceVariant
        )
    }
}

/** 行内 **粗体** / `代码` / [链接](url) → AnnotatedString(对齐更新日志渲染)。 */
private fun helpInlineAnnotated(
    text: String,
    textColor: androidx.compose.ui.graphics.Color,
    accentColor: androidx.compose.ui.graphics.Color
) = buildAnnotatedString {
    for (span in MarkdownBlocks.parseInline(text)) {
        when (span) {
            is MarkdownBlocks.Inline.Text -> append(span.text)
            is MarkdownBlocks.Inline.Bold -> {
                val start = length
                append(span.text)
                addStyle(SpanStyle(fontWeight = FontWeight.Bold, color = textColor), start, length)
            }
            is MarkdownBlocks.Inline.Code -> {
                val start = length
                append(span.text)
                addStyle(SpanStyle(fontFamily = FontFamily.Monospace, color = textColor), start, length)
            }
            is MarkdownBlocks.Inline.Link -> {
                val start = length
                append(span.text)
                addStyle(SpanStyle(color = accentColor, textDecoration = TextDecoration.Underline), start, length)
            }
        }
    }
}
