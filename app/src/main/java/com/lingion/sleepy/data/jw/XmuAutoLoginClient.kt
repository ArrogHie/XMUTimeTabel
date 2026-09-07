package com.lingion.sleepy.data.jw

import android.util.Log
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.text.Regex
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * 厦门大学统一身份认证「账号 + 密码」自动登录抓课表(纯 HttpURLConnection, 不依赖 WebView)。
 *
 * 时序与 schedule/fetch_xmu_schedule.py 一致(该脚本已用真实账号验证):
 *  1) GET ids.xmu.edu.cn/authserver/login?type=userNameLogin&service=门户回调
 *     → 提取 pwdEncryptSalt / execution / lt
 *  2) 密码 AES-128-CBC 加密(salt 作密钥、随机 16 字符作 IV、PKCS7 填充,
 *     明文 = 随机 64 字符 + 真实密码)
 *  3) POST 回「带查询串的同一登录 URL」(裸路径会触发 ids flow 校验 500)
 *  4) 跟随 302, 校验最终落到 jw.xmu.edu.cn; GET /new/index.html 巩固门户会话
 *  5) GET wdkbapp 入口 index.do 初始化应用会话(否则数据接口 403)
 *  6) POST modules/xskcb/kfdxnxqcx.do(学期列表)→ wdkcb/queryXspkjg.do(课表)
 *     → wdkcb/queryXsskjc.do(节次时间字典)
 *
 * 安全约定:
 *  - 密码只在进程内存中存在一个请求周期, 不落盘、不进日志;
 *  - ids 若出现图形验证码 / 双因素登录, 本通道会以 [LoginException] 失败并提示改用
 *    WebView 手动登录通道。
 */
class XmuAutoLoginClient(
    private val account: String,
    password: String,
) {
    private val passwordBytes = password.toByteArray(Charsets.UTF_8)

    class LoginException(message: String, val kind: Kind) : Exception(message) {
        enum class Kind { NETWORK, INVALID_CREDENTIALS, CAPTCHA_REQUIRED, SERVICE }
    }

    private val cookies = CookieJar()

    /** 顶层便捷入口(账号密码通道): 先登录, 再抓课表。调用方在协程里直接拿结果。 */
    suspend fun fetchSchedule(): XmuFetchResult = withContext(Dispatchers.IO) {
        login()
        fetchFromEstablishedSession()
    }

    // ------------------------------------------------------------------ 主流程(会话已就绪)
    private fun fetchFromEstablishedSession(): XmuFetchResult {
        val xh = account
        // 巩固门户会话(与 login() 成功后一致; 手动登录通道用户已到过门户, 幂等无害)
        try {
            request(XmuJw.JW_BASE + "/new/index.html", method = "GET")
        } catch (e: Exception) {
            Log.w(TAG, "new/index.html 预热失败(继续)", e)
        }

        // ① 初始化课表微应用会话(GET 入口; 否则后续 .do 接口 403)
        request(XmuJw.JW_BASE + XmuJw.APP_ENTRY_PATH, method = "GET")

        // ② 学期列表(失败不阻塞, 走兜底推断)
        var semesterCode: String? = null
        try {
            val termBody = postForm(XmuJw.GSAPP_API_BASE + "/modules/xskcb/kfdxnxqcx.do", "")
            val rows = JSONObject(termBody)
                .optJSONObject("datas")?.optJSONObject("kfdxnxqcx")?.optJSONArray("rows")
            if (rows != null && rows.length() > 0) {
                semesterCode = rows.getJSONObject(0).optString("XNXQDM").ifBlank { null }
            }
        } catch (e: Exception) {
            Log.w(TAG, "kfdxnxqcx 学期列表解析失败(走兜底推断)", e)
        }
        if (semesterCode.isNullOrBlank()) {
            semesterCode = XmuJw.inferSemesterCode(java.time.LocalDate.now())
        }
        if (semesterCode.isNullOrBlank()) {
            throw LoginException("无法确定当前学年学期", LoginException.Kind.SERVICE)
        }

        // ③ 课表主体(排课结果)
        val courseBody = postForm(
            XmuJw.GSAPP_API_BASE + "/wdkcb/queryXspkjg.do",
            "XH=" + enc(xh) + "&XNXQDM=" + enc(semesterCode),
        )

        // ④ 节次时间字典(DM → KSSJ~JSSJ; 失败不阻塞, 前端用厦大作息兜底)
        val periods = mutableListOf<Triple<Int, String, String>>()
        try {
            val pBody = postForm(
                XmuJw.GSAPP_API_BASE + "/wdkcb/queryXsskjc.do",
                "XH=" + enc(xh) + "&XNXQDM=" + enc(semesterCode),
            )
            val arr = JSONObject(pBody).optJSONArray("data") ?: JSONObject(pBody).optJSONArray("rows")
            if (arr != null) {
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    val dm = o.optInt("DM", -1)
                    val ks = fmtHm(o.opt("KSSJ"))
                    val js = fmtHm(o.opt("JSSJ"))
                    if (dm >= 1 && ks != null && js != null) {
                        periods += Triple(dm, ks, js)
                    }
                }
                periods.sortBy { it.first }
            }
        } catch (e: Exception) {
            Log.w(TAG, "queryXsskjc 节次时间解析失败(前端用兜底作息)", e)
        }

        Log.i(TAG, "自动登录抓取成功 semester=$semesterCode periods=${periods.size}")
        return XmuFetchResult(courseJson = courseBody, periods = periods, semesterCode = semesterCode)
    }

    // ------------------------------------------------------------------ 登录
    private fun login() {
        val loginUrl = XmuJw.IDP_BASE + "/authserver/login?type=userNameLogin&service=" + XmuJw.encode(XmuJw.PORTAL_SERVICE)
        val page = request(loginUrl, method = "GET").body

        val salt = field(page, "pwdEncryptSalt")
        val execution = field(page, "execution")
        val lt = Regex("""name="lt" id="lt" value="([^"]*)"""").find(page)?.groupValues?.get(1) ?: ""
        if (salt == null || execution == null) {
            // salt/execution 缺失说明登录页形态已变(常见于引入图形验证码/新流程)
            throw LoginException(
                "登录页缺少加密参数, 教务可能已要求图形验证码, 请改用「手动登录」",
                LoginException.Kind.CAPTCHA_REQUIRED,
            )
        }

        val form = buildString {
            append("username=").append(enc(account))
            append("&password=").append(enc(encryptPassword(passwordBytes, salt)))
            append("&captcha=")
            append("&lt=").append(enc(lt))
            append("&execution=").append(enc(execution))
            append("&_eventId=submit")
            append("&cllt=userNameLogin")
            append("&dllt=generalLogin")
            append("&rmShown=1")
        }
        // 必须 POST 回带查询串的同一 URL; 裸路径 /authserver/login 会触发 flow 校验 500
        val resp = request(
            loginUrl,
            method = "POST",
            body = form,
            headers = mapOf(
                "Content-Type" to "application/x-www-form-urlencoded",
                "Referer" to loginUrl,
                "Origin" to XmuJw.IDP_BASE,
            ),
        )
        if (!resp.finalUrl.contains("jw.xmu.edu.cn")) {
            // 未跳到门户 → 账号密码错误(ids 在响应里给 showErrorTip)
            val tip = Regex("""id="showErrorTip"[^>]*>\s*([^<]{0,120})""")
                .find(resp.body)?.groupValues?.get(1)?.trim()
            throw LoginException(
                "登录未成功: ${tip?.ifBlank { "请检查学号与密码" }}",
                LoginException.Kind.INVALID_CREDENTIALS,
            )
        }
        // 请求一次门户首页, 确保门户会话 cookie 完整建立
        request(XmuJw.JW_BASE + "/new/index.html", method = "GET")
    }

    // ------------------------------------------------------------------ 请求
    private class Resp(
        val status: Int,
        val body: String,
        val finalUrl: String,
        val headers: Map<String, List<String>>,
    )

    private fun request(
        urlStr: String,
        method: String,
        body: String? = null,
        headers: Map<String, String> = emptyMap(),
    ): Resp {
        var currentUrl = urlStr
        var currentMethod = method
        var currentBody = body
        var hops = 0
        while (hops++ < 8) {
            val conn = try {
                (URL(currentUrl).openConnection() as HttpURLConnection).apply {
                    connectTimeout = TIMEOUT_MS
                    readTimeout = TIMEOUT_MS
                    instanceFollowRedirects = false
                    requestMethod = currentMethod
                    setRequestProperty("User-Agent", UA)
                    setRequestProperty("Accept", "*/*")
                    setRequestProperty("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
                    val cookie = cookies.headerFor(hostOf(currentUrl))
                    if (cookie.isNotBlank()) setRequestProperty("Cookie", cookie)
                    headers.forEach { (k, v) -> setRequestProperty(k, v) }
                }
            } catch (e: IOException) {
                throw wrapNetwork(e)
            }
            if (currentMethod == "POST" && currentBody != null) {
                try {
                    conn.doOutput = true
                    conn.outputStream.use { it.write(currentBody.toByteArray(Charsets.UTF_8)) }
                } catch (e: IOException) {
                    conn.disconnect()
                    throw wrapNetwork(e)
                }
            }
            val status = try {
                conn.responseCode
            } catch (e: IOException) {
                conn.disconnect()
                throw wrapNetwork(e)
            }
            val respHeaders = conn.headerFields
            val setCookies = respHeaders["Set-Cookie"] ?: respHeaders["set-cookie"] ?: emptyList()
            if (setCookies.isNotEmpty()) {
                cookies.store(hostOf(currentUrl), setCookies)
            }
            val location = respHeaders["Location"]?.firstOrNull()
                ?: respHeaders["location"]?.firstOrNull()

            if (status in 300..399 && !location.isNullOrBlank()) {
                val next = URL(URL(currentUrl), location).toString()
                // 301/302/303 改 GET 并丢弃 body(与 python requests 行为一致); 307/308 保留
                if (status != 307 && status != 308) {
                    currentMethod = "GET"
                    currentBody = null
                }
                currentUrl = next
                conn.disconnect()
                continue
            }

            val bodyText = readBody(conn)
            val finalUrl = conn.url.toString()
            conn.disconnect()

            if (status >= 400) {
                throw LoginException(
                    "教务服务返回 HTTP $status, 请稍后重试或改用「手动登录」",
                    LoginException.Kind.SERVICE,
                )
            }
            return Resp(status, bodyText, finalUrl, respHeaders)
        }
        throw LoginException("登录跳转次数过多, 请改用「手动登录」", LoginException.Kind.SERVICE)
    }

    private fun readBody(conn: HttpURLConnection): String {
        return try {
            val stream: InputStream? = if (conn.responseCode >= 400) conn.errorStream else conn.inputStream
            stream ?: return ""
            stream.use { input ->
                val buf = ByteArrayOutputStream()
                val chunk = ByteArray(8192)
                while (true) {
                    val n = input.read(chunk)
                    if (n < 0) break
                    buf.write(chunk, 0, n)
                }
                buf.toString("UTF-8")
            }
        } catch (e: IOException) {
            throw wrapNetwork(e)
        }
    }

    private fun wrapNetwork(e: IOException): LoginException {
        val msg = e.message ?: e.javaClass.simpleName
        val err = LoginException("网络请求失败: $msg", LoginException.Kind.NETWORK)
        err.initCause(e)
        return err
    }

    private fun hostOf(url: String): String = URL(url).host

    private fun postForm(url: String, form: String): String =
        request(
            url,
            method = "POST",
            body = form,
            headers = mapOf(
                "Content-Type" to "application/x-www-form-urlencoded; charset=UTF-8",
                "X-Requested-With" to "XMLHttpRequest",
                "Referer" to XmuJw.JW_BASE + "/new/index.html",
                "Origin" to XmuJw.JW_BASE,
            ),
        ).body

    // ------------------------------------------------------------------ 工具
    private fun field(html: String, name: String): String? {
        val byName = Regex("""name="${Regex.escape(name)}"[^>]*value="([^"]*)"""")
        val byId = Regex("""id="${Regex.escape(name)}"[^>]*value="([^"]*)"""")
        val m = byName.find(html) ?: byId.find(html)
        return m?.groupValues?.get(1)
    }

    private fun enc(s: String): String = XmuJw.encode(s)

    private fun fmtHm(v: Any?): String? {
        if (v == null || v == JSONObject.NULL) return null
        return when (v) {
            is Number -> "%04d".format(v.toLong()).let { "${it.substring(0, 2)}:${it.substring(2, 4)}" }
            else -> {
                val s = v.toString().trim()
                if (s.contains(":")) {
                    val p = s.split(":")
                    if (p.size == 2 && p[0].length == 2 && p[1].length == 2) return s
                }
                val digits = s.filter { it.isDigit() }
                if (digits.length != 3 && digits.length != 4) return null
                val d = digits.padStart(4, '0')
                "${d.substring(0, 2)}:${d.substring(2, 4)}"
            }
        }
    }

    companion object {
        private const val TAG = "XmuAutoLogin"
        private const val TIMEOUT_MS = 25_000
        private val UA: String = XmuJw.DESKTOP_UA
        private const val AES_CHARS = "ABCDEFGHJKMNPQRSTWXYZabcdefhijkmnprstwxyz2345678"

        /** 与 ids 登录页 encryptPassword() 等价: AES-CBC 加密「随机64字符 + 明文密码」。 */
        internal fun encryptPassword(rawPassword: ByteArray, salt: String): String {
            if (salt.toByteArray(Charsets.UTF_8).size != 16) {
                throw IllegalArgumentException("pwdEncryptSalt 非 16 字节, 登录流程可能已变更")
            }
            val iv = randStr(16)
            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
            cipher.init(
                Cipher.ENCRYPT_MODE,
                SecretKeySpec(salt.toByteArray(Charsets.UTF_8), "AES"),
                IvParameterSpec(iv.toByteArray(Charsets.UTF_8)),
            )
            val enc = cipher.doFinal(randStr(64).toByteArray(Charsets.UTF_8) + rawPassword)
            return Base64.getEncoder().encodeToString(enc)
        }

        private fun randStr(n: Int): String =
            buildString { repeat(n) { append(AES_CHARS.random()) } }
    }
}

/** 简易内存 Cookie 罐(按域名后缀路由)。 */
private class CookieJar {
    private data class C(val name: String, val value: String, val domain: String)

    private val all = LinkedHashMap<String, C>()

    fun headerFor(host: String): String {
        val h = host.lowercase()
        val parts = all.values.filter { h == it.domain || h.endsWith("." + it.domain) }
        return parts.joinToString("; ") { it.name + "=" + it.value }
    }

    fun store(host: String, setCookieHeaders: List<String>) {
        for (raw in setCookieHeaders) {
            val first = raw.substringBefore(';').trim()
            val idx = first.indexOf('=')
            if (idx <= 0) continue
            val name = first.substring(0, idx).trim()
            val value = first.substring(idx + 1).trim()
            if (name.isEmpty()) continue
            var domain = host.lowercase()
            val dm = Regex("""(?i)\bdomain=([^;]+)""").find(raw)?.groupValues?.get(1)
                ?.trim()?.lowercase()?.removePrefix(".")
            if (!dm.isNullOrBlank()) domain = dm
            val key = "$name@$domain"
            // 删除标记: Expires 早于现在 / Max-Age=0
            val deleted = Regex("""(?i)max-age=0|expires=[^;]*\b(?:1970|0001)\b""").containsMatchIn(raw)
            if (deleted) all.remove(key) else all[key] = C(name, value, domain)
        }
    }
}
