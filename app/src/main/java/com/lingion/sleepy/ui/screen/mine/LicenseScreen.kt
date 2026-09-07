package com.lingion.sleepy.ui.screen.mine

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lingion.sleepy.R
import com.lingion.sleepy.ui.theme.SleepyTheme

/**
 * 开源许可与致谢子页 (v1.0.46 用户令: 从关于页长卡分离; v1.0.50 用户令:
 * 致谢按"学校 / 跨校项目"两类组织, 单校卡展开看明细)。
 *
 * 关于页原样塞 GPL 正文 + 全部教务适配致谢, B 档 + 179 校 audit 落地后
 * 致谢条目越滚越长, 关于页被拖成一屏读不完。拆为独立二级页:
 *   - 关于页留一行入口 (标题+副题)
 *   - 本页承载全部内容 — 许可证区块 + 致谢区块 + 顶层卡列表
 *   - 顶层卡分两类:
 *       跨校项目 (Foundational) = WakeUp / WakeupSchedule_BUPT / cqu.js 等
 *         通用跨校适配参考, 单卡不可展开
 *       单校项目 (PerSchool) = 1 学校 1 卡, 默认收起, 用户点击展开看该校所
 *         参考的全部学生维护 GitHub 项目
 *   - 展开/收起状态用 mutableStateMapOf 按卡片 id 维护, 进入页面不重置
 *   - 布局与 HolidaySettingsScreen 同款: Scaffold + TopAppBar 返回 + LazyColumn
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LicenseScreen(onBack: () -> Unit) {
    val colors = SleepyTheme.colors
    val expanded = remember { mutableStateMapOf<String, Boolean>() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.license_page_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.back))
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
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ---- 许可证区块 ----
            item {
                LicenseCard {
                    Text(
                        text = stringResource(R.string.license_gpl_section),
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                        color = colors.onSurface
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.license_gpl_body),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurfaceVariant
                    )
                }
            }

            // ---- 致谢导语区块 (可折叠: 默认收起, 点击展开看 about_license_body 全文) ----
            item {
                val bodyExpanded = expanded["__body__"] == true
                LicenseCard {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { expanded["__body__"] = !bodyExpanded },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.license_attribution_section),
                                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                                color = colors.onSurface
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = stringResource(R.string.license_attribution_note),
                                style = MaterialTheme.typography.bodyMedium,
                                color = colors.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = { expanded["__body__"] = !bodyExpanded }) {
                            Icon(
                                imageVector = if (bodyExpanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                                contentDescription = if (bodyExpanded) "collapse" else "expand",
                                tint = colors.onSurfaceVariant
                            )
                        }
                    }
                    AnimatedVisibility(visible = bodyExpanded) {
                        Column(modifier = Modifier.padding(top = 8.dp)) {
                            Text(
                                text = stringResource(R.string.about_license_body),
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // ---- 跨校普适项目 (Foundational) ----
            item {
                SectionHeader(stringResource(R.string.license_foundational_section))
            }
            items(
                items = attributionEntries,
                key = { it.id }
            ) { entry ->
                AttributionCard(
                    title = entry.title,
                    subtitle = entry.meta,
                    description = entry.usage,
                    expanded = false,
                    onToggle = {}
                )
            }

            // ---- 按学校致谢 (PerSchool, 可展开) ----
            if (perSchoolEntries.isNotEmpty()) {
                item {
                    SectionHeader(stringResource(R.string.license_perschool_section))
                }
                items(
                    items = perSchoolEntries,
                    key = { it.id }
                ) { entry ->
                    AttributionCard(
                        title = entry.title,
                        subtitle = null,
                        description = null,
                        expanded = expanded[entry.id] == true,
                        onToggle = { expanded[entry.id] = !(expanded[entry.id] ?: false) },
                        expandedContent = {
                            Column {
                                entry.usage.split("\n").forEach { line ->
                                    Text(
                                        text = line,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = colors.onSurfaceVariant,
                                        modifier = Modifier.padding(vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    )
                }
            }
        }
    }
}

/** 一条顶层致谢卡: 跨校项目=不可展开, 单校=可展开。 */
@Composable
private fun AttributionCard(
    title: String,
    subtitle: String?,
    description: String?,
    expanded: Boolean,
    onToggle: () -> Unit,
    expandedContent: (@Composable () -> Unit)? = null
) {
    val colors = SleepyTheme.colors
    LicenseCard {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = expandedContent != null) { onToggle() },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = colors.onSurface
                )
                if (!subtitle.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.primary
                    )
                }
            }
            if (expandedContent != null) {
                IconButton(onClick = onToggle) {
                    Icon(
                        imageVector = if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                        contentDescription = if (expanded) "collapse" else "expand",
                        tint = colors.onSurfaceVariant
                    )
                }
            }
        }
        if (!expanded && !description.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant
            )
        }
        if (expanded && expandedContent != null) {
            Spacer(modifier = Modifier.height(6.dp))
            AnimatedVisibility(visible = expanded) {
                expandedContent()
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    val colors = SleepyTheme.colors
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
        color = colors.primary,
        modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 4.dp)
    )
}

@Composable
private fun LicenseCard(content: @Composable () -> Unit) {
    val colors = SleepyTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(colors.surfaceContainer)
            .padding(16.dp)
    ) {
        content()
    }
}

/**
 * 顶层致谢条目 (跨校项目卡)。description 为可见文字段, 与原 BATCH_A/B/C/BATCH_D
 * 致谢条目等价, 用于满足 AboutLicenseAttributionTest 的串级漂移测试。
 * 项目名 / 作者 / license 名字符串是通用标识不翻译; description 文本是说明,
 * 与 strings.xml 的 about_license_body 必须保持一致 (写卡 = 用户界面补强)。
 */
private data class AttributionEntry(
    val id: String,
    val title: String,
    val meta: String,
    val usage: String
)

/** 跨校普适项目 (单卡, 不可展开)。厦门大学专属版仅保留仍被引用的上游实现。 */
private val attributionEntries: List<AttributionEntry> = listOf(
    AttributionEntry(
        "foundational-wakeup", "WakeUp 课程表 (YZune)", "Apache-2.0",
        "JwCourse 中间结构语义的参考实现 (课程导入数据模型来源)"
    ),
    AttributionEntry(
        "foundational-wakeup-bupt", "WakeupSchedule_BUPT (dIT8Zv)", "Apache-2.0",
        "JwParser / JwParserRegistry 解析架构与学校目录登记方式的参考"
    ),
    AttributionEntry(
        "foundational-wakeup-kotlin", "WakeupSchedule_Kotlin (YZune)", "Apache-2.0",
        "周次位图解析 (SKZC 单/双周压缩) 思路的参考"
    ),
)

/**
 * 按学校聚合: 每校一条卡, 展开后看到该校所参考的所有 GitHub 项目。
 * 厦门大学专属版: 厦大 gsapp/wdkbapp 课表接口时序为本项目自行调研验证
 * (schedule/fetch_xmu_schedule.py), 无单校第三方仓库, 故列表留空。
 */
private data class PerSchoolEntry(val id: String, val title: String, val usage: String)

private val perSchoolEntries: List<PerSchoolEntry> = emptyList()
