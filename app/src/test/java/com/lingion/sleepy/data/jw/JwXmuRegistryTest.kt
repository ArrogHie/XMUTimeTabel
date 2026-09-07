package com.lingion.sleepy.data.jw

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 厦大专属注册表 / 协议常量 / 常量数据快速断言。
 */
class JwXmuRegistryTest {

    @Test
    fun `parserFor xmu 返回 JwXmuParser`() {
        assertTrue(JwParserRegistry.parserFor(JwProtocol.TYPE_XMU, """{"pkjgList":[]}""") is JwXmuParser)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `parserFor 未知协议抛异常`() {
        JwParserRegistry.parserFor("zf", """{"x":1}""")
    }

    @Test
    fun `协议常量与显示名`() {
        assertEquals("xmu", JwProtocol.TYPE_XMU)
        assertEquals("厦门大学教务", JwProtocol.displayName(JwProtocol.TYPE_XMU))
        assertEquals("xmu", JwProtocol.category(JwProtocol.TYPE_XMU))
    }

    @Test
    fun `厦大单学校条目指向 xmu 协议`() {
        val s = XmuJw.XMU_SCHOOL
        assertEquals("厦门大学", s.name)
        assertEquals("xmu", s.type)
        assertTrue(s.url.startsWith("https://jw.xmu.edu.cn"))
        assertTrue(s.isSupported)
    }

    @Test
    fun `兜底节次表覆盖 1 到 11 节`() {
        assertEquals(11, XmuJw.FALLBACK_PERIOD.size)
        assertEquals("08:00" to "08:45", XmuJw.FALLBACK_PERIOD[1])
        assertEquals("19:10" to "19:55", XmuJw.FALLBACK_PERIOD[9])
        assertEquals("21:00" to "21:45", XmuJw.FALLBACK_PERIOD[11])
    }

    @Test
    fun `按月份推断当前学年秋季学期`() {
        assertEquals("20261", XmuJw.inferSemesterCode(LocalDate.of(2026, 9, 7)))
        assertEquals("20251", XmuJw.inferSemesterCode(LocalDate.of(2026, 3, 1)))
        assertEquals("20271", XmuJw.inferSemesterCode(LocalDate.of(2027, 12, 1)))
    }

    @Test
    fun `推断学年码对四位年份安全`() {
        // 年份超出四位时返回 null(走 UI 手动选学期兜底)
        assertNull(XmuJw.inferSemesterCode(LocalDate.of(10_000, 9, 1)))
    }

    @Test
    fun `AES 密码加密可被服务器侧解密约定校验`() {
        // 不能访问教务私钥; 只验证输出为 16 字节 IV 前缀随机 + base64、长度合理且稳定解码
        val salt = "0123456789abcdef"   // 16 字节, 模拟 pwdEncryptSalt
        val enc1 = XmuAutoLoginClient.encryptPassword("pass123".toByteArray(Charsets.UTF_8), salt)
        val enc2 = XmuAutoLoginClient.encryptPassword("pass123".toByteArray(Charsets.UTF_8), salt)
        // IV 随机 → 两次结果不同
        assertTrue(enc1 != enc2)
        val decoded = java.util.Base64.getDecoder().decode(enc1)
        // 密文 = 明文(64 随机 + 6 密码 = 70B, PKCS7 → 80B)
        assertEquals(80, decoded.size)
    }
}
