package com.lingion.sleepy.data.jw

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * 厦门大学教务(金智 gsapp wdkbapp 微服务)课表解析器。
 *
 * source 是 wdkcb/queryXspkjg.do 的 JSON 响应全文(WebView 内 XMU_FETCH_JS 或
 * [XmuAutoLoginClient] 取回), 结构: { "pkjgList": [ ...排课记录 ] }。
 *
 * 排课记录字段映射(与 schedule/fetch_xmu_schedule.py 的 build_output 同源):
 *   KCDM  课程代码
 *   KCMC  课程名     → name
 *   KCYWMC 英文课名  (仅归档)
 *   BJMC/BJDM 教学班 (仅归档)
 *   JSXM  教师       → teacher(多个教师逗号分隔)
 *   JASMC 教室(可空) → room
 *   XQ    星期(1..7) → day
 *   KSJCDM/JSJCDM 起止节次 → startNode/endNode
 *   ZCBH  周次位图: 第 i 位(i 从 0 起)为 '1' 表示第 i+1 周上课
 *   ZCMC  周次文本   (仅归档)
 *
 * 多教室合并: 教务把「同一门课 × 同一星期 × 同一节次 × 同一周次」因多个可用教室
 * 拆成多行(样例: UNIX 双周 1-4 节同时给出 B311/B308 两行)。若逐行转 JwCourse,
 * 网格会出现同一时段多张重叠卡。解析先按 (KCDM|KCMC、XQ、KSJCDM、JSJCDM、ZCBH)
 * 分组合并: 教室按非空去重用「 / 」连接、教师按逗号去重后用「, 」连接, 其余取首行,
 * 合并后再走周次位图拆段, 每组合并结果对应一条「星期 × 节次 × 周次」的 JwCourse。
 *
 * 周次位图压缩: 单个连续段 type=0(每周); 整段等差 2(全奇/全偶)压缩成单周(type=1)/
 * 双周(type=2); 其余拆成多个连续段。
 */
class JwXmuParser(source: String) : JwParser(source) {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /** 合并键(分组合并前使用原始行字段, 避免拆周次后无法聚合)。 */
    private data class MergeKey(
        val courseCode: String,
        val courseName: String,
        val day: Int,
        val startNode: Int,
        val endNode: Int,
        val weekMask: String,
    )

    override fun generateCourseList(): List<JwCourse> {
        val root = try {
            json.parseToJsonElement(source).jsonObject
        } catch (e: Exception) {
            return emptyList()
        }
        // 主形态 {"pkjgList": [...]}; 预留 datas.pkjgList 兼容
        val rows = root["pkjgList"]?.jsonArray
            ?: root["datas"]?.jsonObject?.get("pkjgList")?.jsonArray
            ?: return emptyList()

        // ① 原样解析并按键分组合并(同课同时间多教室 → 一条)
        val byKey = linkedMapOf<MergeKey, MutableList<kotlinx.serialization.json.JsonObject>>()
        for (el in rows) {
            val o = el.jsonObject
            fun str(k: String): String = o[k]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
            fun int(k: String): Int? = str(k).toIntOrNull()

            val name = str("KCMC")
            if (name.isBlank()) continue
            val day = int("XQ") ?: continue
            val startNode = int("KSJCDM") ?: continue
            val endNode = int("JSJCDM") ?: startNode
            val key = MergeKey(
                courseCode = str("KCDM"),
                courseName = name,
                day = day,
                startNode = startNode,
                endNode = endNode,
                weekMask = str("ZCBH"),
            )
            byKey.getOrPut(key) { mutableListOf() }.add(o)
        }

        // ② 每组聚合字段 → 拆周次 → JwCourse
        val result = mutableListOf<JwCourse>()
        for ((key, group) in byKey) {
            val rooms = group.mapNotNull { r ->
                r["JASMC"]?.jsonPrimitive?.contentOrNull?.trim()?.takeIf { it.isNotBlank() }
            }.distinct()
            val teachers = group.flatMap { r ->
                val raw = r["JSXM"]?.jsonPrimitive?.contentOrNull ?: ""
                raw.split(',', '，')
                    .map { it.trim() }
                    .filter { it.isNotBlank() }
            }.distinct()

            val room = rooms.joinToString(" / ")
            val teacher = teachers.joinToString(", ")
            for ((sw, ew, type) in weekRuns(key.weekMask)) {
                result += JwCourse(
                    name = key.courseName,
                    room = room,
                    teacher = teacher,
                    day = key.day.coerceIn(1, 7),
                    startNode = key.startNode.coerceAtLeast(1),
                    endNode = key.endNode.coerceAtLeast(key.startNode),
                    startWeek = sw,
                    endWeek = ew,
                    type = type,
                )
            }
        }
        return result
    }

    /** ZCBH 位图 → 连续段列表 [(startWeek, endWeek, type)], 规则见类注释。 */
    private fun weekRuns(skzc: String): List<Triple<Int, Int, Int>> {
        val weeks = skzc.mapIndexedNotNull { i, c -> if (c == '1') i + 1 else null }
        if (weeks.isEmpty()) return emptyList()

        // 拆连续段
        val runs = mutableListOf<Pair<Int, Int>>()
        var start = weeks[0]
        var prev = weeks[0]
        for (w in weeks.drop(1)) {
            if (w == prev + 1) {
                prev = w
            } else {
                runs += start to prev
                start = w
                prev = w
            }
        }
        runs += start to prev

        if (runs.size == 1) {
            return listOf(Triple(runs[0].first, runs[0].second, 0))
        }
        // 整段单/双周(等差 step=2)
        if (weeks.size >= 2 && (1 until weeks.size).all { weeks[it] - weeks[it - 1] == 2 }) {
            val type = if (weeks.first() % 2 == 1) 1 else 2
            return listOf(Triple(weeks.first(), weeks.last(), type))
        }
        return runs.map { Triple(it.first, it.second, 0) }
    }

    override fun confidence(): Int =
        if (source.contains("pkjgList")) 100 else 0

    override fun matchedFeatures(): List<String> =
        if (source.contains("pkjgList")) listOf("pkjgList") else emptyList()
}
