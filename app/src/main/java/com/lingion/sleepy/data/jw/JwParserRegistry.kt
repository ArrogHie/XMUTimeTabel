package com.lingion.sleepy.data.jw

/**
 * 教务协议 → 解析器工厂。
 *
 * 厦门大学专属版: 单协议分发(TYPE_XMU → JwXmuParser)。
 * 原通用版的多协议优先级表 / 候选兜底裁决(selectBest)已删除 — 单学校下不需要
 * "猜协议", 学号+登录态确定的教务只有一种。
 */
object JwParserRegistry {

    private val FACTORIES: Map<String, (String) -> JwParser> = linkedMapOf(
        JwProtocol.TYPE_XMU to ::JwXmuParser,
    )

    /** 显式分发: type 已知时按 FACTORIES 表单派。未在表内的 type 抛 IllegalArgumentException。 */
    fun parserFor(type: String, source: String): JwParser =
        FACTORIES[type]?.invoke(source)
            ?: throw IllegalArgumentException("教务协议 $type 暂不支持")
}
