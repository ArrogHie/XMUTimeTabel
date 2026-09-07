package com.lingion.sleepy.data.jw

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JwXmuParser 单测 — 覆盖 schedule/xmu_schedule_20261.json 展示的真实响应形态:
 * queryXspkjg.do 返回 {"pkjgList":[...]}, 每行含 KCMC/XQ/KSJCDM/JSJCDM/JSXM/JASMC/ZCBH/ZCMC。
 * 断言无损还原(周次位图→周次语义)。
 */
class JwXmuParserTest {

    private fun parse(json: String): List<JwCourse> = JwXmuParser(json).generateCourseList()

    /** 按「上课周次集合」生成 ZCBH 位图(第 i 位=1 表示第 i 周上课)。 */
    private fun weeksToMask(weeks: Collection<Int>, total: Int = 20): String =
        (1..total).joinToString("") { if (it in weeks) "1" else "0" }

    @Test
    fun `每周课程全周解析`() {
        val json = """{"pkjgList":[{
            "KCDM":"130130010045","KCMC":"UNIX系统程序设计","BJMC":"01",
            "JSXM":"王连生","JASMC":"西部片区2号楼106",
            "XQ":3,"KSJCDM":5,"JSJCDM":6,
            "ZCBH":"${weeksToMask((1..16).toList())}","ZCMC":"1-16周"
        }]}"""
        val list = parse(json)
        assertEquals(1, list.size)
        val c = list[0]
        assertEquals("UNIX系统程序设计", c.name)
        assertEquals("王连生", c.teacher)
        assertEquals("西部片区2号楼106", c.room)
        assertEquals(3, c.day)
        assertEquals(5, c.startNode)
        assertEquals(6, c.endNode)
        assertEquals(1, c.startWeek)
        assertEquals(16, c.endWeek)
        assertEquals(0, c.type)
    }

    @Test
    fun `双周课程压缩为 type 2`() {
        val weeks = (2..16 step 2).toList()
        val json = """{"pkjgList":[{
            "KCMC":"UNIX系统程序设计","JSXM":"王连生","JASMC":"文宣楼（4号楼）B311",
            "XQ":3,"KSJCDM":1,"JSJCDM":4,
            "ZCBH":"${weeksToMask(weeks)}","ZCMC":"2-16双周"
        }]}"""
        val c = parse(json).single()
        assertEquals(2, c.startWeek)
        assertEquals(16, c.endWeek)
        assertEquals(2, c.type)   // 双周
    }

    @Test
    fun `单周课程压缩为 type 1`() {
        val weeks = (5..15 step 2).toList()
        val json = """{"pkjgList":[{
            "KCMC":"汇编语言程序设计A","JSXM":"闵小平,鞠颖","JASMC":"文宣楼（4号楼）A405",
            "XQ":5,"KSJCDM":1,"JSJCDM":4,
            "ZCBH":"${weeksToMask(weeks)}","ZCMC":"5-15单周"
        }]}"""
        val c = parse(json).single()
        assertEquals(5, c.startWeek)
        assertEquals(15, c.endWeek)
        assertEquals(1, c.type)   // 单周
    }

    @Test
    fun `同课同时间多教室合并为一条`() {
        // 样例: 同一门课(同 code/星期/节次/周次位图)因教室不同为两行 → 合并 1 条, room 双教室
        val even = (2..16 step 2).toList()
        val json = """{"pkjgList":[
            {"KCDM":"130130010045","KCMC":"UNIX系统程序设计","JSXM":"王连生","JASMC":"文宣楼（4号楼）B311","XQ":3,"KSJCDM":1,"JSJCDM":4,"ZCBH":"${weeksToMask(even)}"},
            {"KCDM":"130130010045","KCMC":"UNIX系统程序设计","JSXM":"王连生","JASMC":"文宣楼（4号楼）B308","XQ":3,"KSJCDM":1,"JSJCDM":4,"ZCBH":"${weeksToMask(even)}"}
        ]}"""
        val list = parse(json)
        assertEquals(1, list.size)
        assertEquals("文宣楼（4号楼）B311 / 文宣楼（4号楼）B308", list.single().room)
    }

    @Test
    fun `多教室行教师按逗号去重合并`() {
        val even = (2..16 step 2).toList()
        val json = """{"pkjgList":[
            {"KCDM":"C1","KCMC":"实验课","JSXM":"张三,李四","JASMC":"实验室A","XQ":2,"KSJCDM":5,"JSJCDM":6,"ZCBH":"${weeksToMask(even)}"},
            {"KCDM":"C1","KCMC":"实验课","JSXM":"张三","JASMC":"实验室B","XQ":2,"KSJCDM":5,"JSJCDM":6,"ZCBH":"${weeksToMask(even)}"}
        ]}"""
        val list = parse(json)
        assertEquals(1, list.size)
        assertEquals("实验室A / 实验室B", list.single().room)
        assertEquals("张三, 李四", list.single().teacher)
    }

    @Test
    fun `不同课程或不同时段不误合并`() {
        val even = (2..16 step 2).toList()
        val odd = (1..15 step 2).toList()
        val json = """{"pkjgList":[
            {"KCDM":"A","KCMC":"课A","JASMC":"教室1","XQ":1,"KSJCDM":1,"JSJCDM":2,"ZCBH":"${weeksToMask(even)}"},
            {"KCDM":"A","KCMC":"课A","JASMC":"教室2","XQ":1,"KSJCDM":3,"JSJCDM":4,"ZCBH":"${weeksToMask(even)}"},
            {"KCDM":"A","KCMC":"课A","JASMC":"教室3","XQ":1,"KSJCDM":1,"JSJCDM":2,"ZCBH":"${weeksToMask(odd)}"},
            {"KCDM":"B","KCMC":"课B","JASMC":"教室4","XQ":1,"KSJCDM":1,"JSJCDM":2,"ZCBH":"${weeksToMask(even)}"}
        ]}"""
        assertEquals(4, parse(json).size)
    }

    @Test
    fun `教室为空或缺失仍产出课程`() {
        val json = """{"pkjgList":[{
            "KCMC":"乒乓球(基础班1)","JSXM":"林香菜","JASMC":null,
            "XQ":3,"KSJCDM":7,"JSJCDM":8,
            "ZCBH":"${weeksToMask((1..16).toList())}"
        }]}"""
        val c = parse(json).single()
        assertEquals("乒乓球(基础班1)", c.name)
        assertEquals("", c.room)
        assertEquals(7, c.startNode)
        assertEquals(8, c.endNode)
    }

    @Test
    fun `非等差多段周次拆为多个连续段`() {
        // 第 1-2 周 + 第 5-7 周 → 两段 type=0
        val json = """{"pkjgList":[{
            "KCMC":"分段课","JSXM":"教师","XQ":1,"KSJCDM":3,"JSJCDM":4,
            "ZCBH":"${weeksToMask(listOf(1, 2, 5, 6, 7))}"
        }]}"""
        val list = parse(json)
        assertEquals(2, list.size)
        assertEquals(listOf(1, 5), list.map { it.startWeek })
        assertEquals(listOf(2, 7), list.map { it.endWeek })
        assertTrue(list.all { it.type == 0 })
    }

    @Test
    fun `晚课到第11节保留`() {
        val json = """{"pkjgList":[{
            "KCMC":"学科实践（三）","JSXM":"蔡炳跃","JASMC":"西部片区4号楼205",
            "XQ":4,"KSJCDM":9,"JSJCDM":11,
            "ZCBH":"${weeksToMask((1..16).toList())}"
        }]}"""
        val c = parse(json).single()
        assertEquals(9, c.startNode)
        assertEquals(11, c.endNode)
        assertEquals(3, c.endNode - c.startNode + 1)
    }

    @Test
    fun `位图为空或关键字段缺失则跳过该行`() {
        val json = """{"pkjgList":[
            {"KCMC":"有课","XQ":1,"KSJCDM":1,"JSJCDM":2,"ZCBH":""},
            {"KCMC":"","XQ":1,"KSJCDM":1,"JSJCDM":2,"ZCBH":"${weeksToMask((1..16).toList())}"},
            {"KCMC":"无星期","KSJCDM":1,"JSJCDM":2,"ZCBH":"${weeksToMask((1..16).toList())}"},
            {"KCMC":"无节次","XQ":1,"JSJCDM":2,"ZCBH":"${weeksToMask((1..16).toList())}"}
        ]}"""
        assertEquals(0, parse(json).size)
    }

    @Test
    fun `非 pkjgList JSON 返回空`() {
        assertEquals(0, parse("""{"datas":{}}""").size)
        assertEquals(0, parse("not-json").size)
    }
}
