package com.lingion.sleepy.data.jw

/**
 * 教务协议类型 — 厦门大学专属版。
 *
 * 原通用版(WakeupSchedule_BUPT 衍生)支持约 27 个协议族/181 所学校, 本版收敛为
 * 单一协议 TYPE_XMU: 厦门大学统一身份认证(ids.xmu.edu.cn) + 金智 gsapp 微服务
 * 课表接口(jw.xmu.edu.cn/gsapp/sys/wdkbapp, wdkcb/queryXspkjg.do)。
 * 登录/抓取时序与 schedule/fetch_xmu_schedule.py 保持一致(真实账号已验证)。
 */
object JwProtocol {

    /** 厦门大学 — 统一身份认证 + gsapp wdkbapp 微服务课表 */
    const val TYPE_XMU = "xmu"

    /** 协议显示名(用于 UI 提示) */
    fun displayName(type: String?): String = when (type) {
        TYPE_XMU -> "厦门大学教务"
        else -> type ?: ""
    }

    /** 协议大类(单协议下恒为 xmu; 保留函数便于 UI 分类逻辑) */
    fun category(type: String?): String = when (type) {
        TYPE_XMU -> "xmu"
        else -> "other"
    }
}
