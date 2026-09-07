package com.lingion.sleepy

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 关于页致谢 (about_license_body) 漂移回归测试。
 *
 * 规则: 新增 parser / 学校落地后必须在 zh-rCN / zh-rTW / en / ja / es 5 语同步追加致谢条目
 * (避免 HFUT 那次 en 漏写的历史漂移)。致谢条目用上游项目名 + author/license
 * 三元组作为最小识别单位, 实际文案与语序由 strings.xml 维护者负责。
 *
 * v1.0.50 用户令: 致谢按"跨校普适项目 / 单校项目"两类组织, 单校卡展开看明细。
 * 测试闸门覆盖每条 token 必须出现在每语 about_license_body 中 (与 LicenseScreen.kt
 * 的 attributionEntries + perSchoolEntries 同源)。"宁可错谢不可放过" = 一旦调研
 * 触达仓库, 必入 strings.xml 6 语 + LicenseScreen.kt + 本测试, 三处一致。
 */
class AboutLicenseAttributionTest {

    private val basePath: File = sequenceOf(
        File("app/src/main/res"),
        File("src/main/res")
    ).first { it.isDirectory }

    private fun readString(locale: String, key: String): String {
        val f = File(basePath, "$locale/strings.xml")
        val text = f.readText()
        val regex = Regex("""<string\s+name="$key"[^>]*>(.*?)</string>""", RegexOption.DOT_MATCHES_ALL)
        return regex.find(text)?.groupValues?.get(1)?.trim().orEmpty()
    }

    /** 单条致谢条目的最小识别 token。任何一个 token 缺失则视为漏致谢。 */
    private data class Attribution(val project: String, val licenseOrAuthor: String)

    // ----- 跨校普适项目 (Foundational) token 集 -----

    private val FOUNDATIONAL_ATTRIBUTIONS = listOf(
        Attribution("WakeUp", "Apache-2.0"),
        Attribution("WakeupSchedule_BUPT", "Apache-2.0"),
        Attribution("WakeupSchedule_Kotlin", "Apache-2.0"),
    )

    // ----- 单校项目 (PerSchool) token 集: 每校 + 该校仓库 token -----
    // 厦门大学专属版无单校第三方仓库(gsapp/wdkbapp 时序为项目自行调研), 列表为空。

    private val PER_SCHOOL_ATTRIBUTIONS = listOf<Attribution>()

    private fun checkAll(locale: String, atts: List<Attribution>) {
        val body = readString(locale, "about_license_body")
        assertTrue("locale=$locale 缺少 about_license_body 字符串", body.isNotBlank())
        for (a in atts) {
            assertTrue("locale=$locale 致谢漏写 ${a.project} (body=\"$body\")",
                body.contains(a.project))
            if (a.licenseOrAuthor.isNotEmpty()) {
                assertTrue("locale=$locale 致谢漏写 ${a.project} 的 license/author 标记 ${a.licenseOrAuthor}",
                    body.contains(a.licenseOrAuthor))
            }
        }
    }

    /** 全部发布语言: 致谢漂移曾只查 3 语, en/es/ja 漏整批 B 档致谢而闸门放行 */
    private val ALL_RELEASED_LOCALES = listOf(
        "values", "values-zh-rCN", "values-zh-rTW", "values-en", "values-ja", "values-es"
    )

    @Test
    fun `all released locales list foundational attributions`() {
        for (locale in ALL_RELEASED_LOCALES) {
            checkAll(locale, FOUNDATIONAL_ATTRIBUTIONS)
        }
    }

    @Test
    fun `all released locales list per-school attributions`() {
        for (locale in ALL_RELEASED_LOCALES) {
            checkAll(locale, PER_SCHOOL_ATTRIBUTIONS)
        }
    }

    /** 自检: 统计 token 总数与跨校/单校分类 (commit 前打印日志, 漂移检测助手) */
    @Test
    fun `attribution coverage summary`() {
        val total = FOUNDATIONAL_ATTRIBUTIONS.size + PER_SCHOOL_ATTRIBUTIONS.size
        println("[ATTRIBUTION] foundational=${FOUNDATIONAL_ATTRIBUTIONS.size}, per-school=${PER_SCHOOL_ATTRIBUTIONS.size}, total=$total")
        assertTrue("必须覆盖至少 3 条致谢 token", total >= 3)
    }
}
