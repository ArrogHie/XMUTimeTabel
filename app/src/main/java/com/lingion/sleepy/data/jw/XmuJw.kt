package com.lingion.sleepy.data.jw

import java.net.URLEncoder
import java.time.LocalDate

/**
 * 厦门大学教务(金智 gsapp/wdkbapp 微服务)登录与课表接口常量、纯函数。
 *
 * 端点与时序与仓库内 schedule/fetch_xmu_schedule.py 保持一致(该脚本已用真实账号
 * 验证过完整链路)。单学校版本下"学校"收敛为 [XMU_SCHOOL] 单一条目, 不再读
 * schools.json 目录。
 */
object XmuJw {

    /** 统一身份认证(ids) */
    const val IDP_BASE = "https://ids.xmu.edu.cn"

    /** 教务门户(jw) */
    const val JW_BASE = "https://jw.xmu.edu.cn"

    /**
     * 门户回调地址 — 登录成功跳回教务门户首页。保持原始未编码形态, 由调用方
     * (XmuAutoLoginClient)整体 encode 一次作为统一身份认证的 service 参数
     * (与 schedule/fetch_xmu_schedule.py 的 PORTAL_SERVICE 一致)。
     */
    val PORTAL_SERVICE: String =
        JW_BASE + "/login?service=" + JW_BASE + "/new/index.html"

    /** 教务登录入口(WebView 打开此地址; CAS 302 到统一身份认证) */
    const val LOGIN_URL = JW_BASE + "/login"

    /**
     * 统一使用的桌面版 Chrome UA。
     *
     * 厦大统一身份认证 / 教务门户按 UA 渲染: 手机 UA 会落到移动版页面, 与
     * schedule/fetch_xmu_schedule.py 验证过的桌面版接口时序不符 → 登录后抓取失败。
     * 手动登录 WebView 与自动登录客户端必须共用同一桌面 UA(与脚本一致)。
     */
    const val DESKTOP_UA = ("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
        + "(KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36")

    /** 课表微应用入口路径 — 抓课前必须先 GET 一次初始化应用会话, 否则数据接口 403 */
    const val APP_ENTRY_PATH = "/gsapp/sys/wdkbapp/*default/index.do?EMAP_LANG=zh&THEME=cherry"

    /** 金智 gsapp 微应用 API 基础路径 */
    const val GSAPP_API_BASE = JW_BASE + "/gsapp/sys/wdkbapp"

    /** 单学校版本的用户可见条目 — 替代原 181 所 schools.json 目录。 */
    val XMU_SCHOOL: JwSchoolInfo = JwSchoolInfo(
        sortKey = "X",
        name = "厦门大学",
        url = LOGIN_URL,
        type = JwProtocol.TYPE_XMU,
        aliases = listOf("xmu"),
        sortKeyFull = "xiamendaxue",
    )

    /**
     * 节次时段兜底表(厦门大学作息; wdkcb/queryXsskjc.do 未返回时使用)。
     * 与 schedule/fetch_xmu_schedule.py 的 _FALLBACK_PERIOD 同源。
     */
    val FALLBACK_PERIOD: Map<Int, Pair<String, String>> = mapOf(
        1 to ("08:00" to "08:45"),
        2 to ("08:55" to "09:40"),
        3 to ("10:10" to "10:55"),
        4 to ("11:05" to "11:50"),
        5 to ("14:30" to "15:15"),
        6 to ("15:25" to "16:10"),
        7 to ("16:40" to "17:25"),
        8 to ("17:35" to "18:20"),
        9 to ("19:10" to "19:55"),
        10 to ("20:05" to "20:50"),
        11 to ("21:00" to "21:45"),
    )

    fun encode(s: String): String = URLEncoder.encode(s, "UTF-8")

    /**
     * 按当前日期推断当前学年学期码: 9 月起为当年秋季("20261"), 其余月份回退到
     * 上一学年秋季。仅在接口学期列表拿不到时兜底使用。
     */
    fun inferSemesterCode(now: LocalDate): String? {
        val cur = if (now.monthValue >= 9) now.year else now.year - 1
        return if (cur in 1000..9999) "${cur}1" else null
    }
}

/**
 * 课表抓取统一载荷 — WebView 手动登录通道(XMU_FETCH_JS)与账号密码自动登录通道
 * ([XmuAutoLoginClient])共用, 交给 [JwImportViewModel.parseHtml] 解析后进入
 * 配置确认与落库流程。
 */
data class XmuFetchResult(
    /** wdkcb/queryXspkjg.do 的 JSON 全文(喂 [JwXmuParser]) */
    val courseJson: String,
    /** 节次时间字典 [(node, 开始 HH:mm, 结束 HH:mm)]; 为空时用厦大作息兜底 */
    val periods: List<Triple<Int, String, String>>,
    /** 学年学期码, 如 20261(供确认页展示, 不参与解析) */
    val semesterCode: String,
)
