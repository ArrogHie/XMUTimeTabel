import Foundation

/// 厦大统一身份认证自动登录 — 对齐 Android XmuAutoLoginClient 时序
final class XmuAutoLoginClient {
    struct FetchResult {
        var courseJson: String
        var periods: [(Int, String, String)]
        var semesterCode: String
    }

    enum LoginError: Error, LocalizedError {
        case network(String)
        case invalidCredentials(String)
        case captchaRequired(String)
        case service(String)

        var errorDescription: String? {
            switch self {
            case .network(let m): return m
            case .invalidCredentials(let m): return m
            case .captchaRequired(let m): return m
            case .service(let m): return m
            }
        }
    }

    private let account: String
    private let password: String
    private var cookies: [String: String] = [:] // host -> cookie header value pieces
    private let session: URLSession

    init(account: String, password: String) {
        self.account = account
        self.password = password
        let config = URLSessionConfiguration.default
        config.timeoutIntervalForRequest = 20
        config.timeoutIntervalForResource = 40
        config.httpShouldSetCookies = false
        session = URLSession(configuration: config)
    }

    func fetchSchedule(completion: @escaping (Result<FetchResult, Error>) -> Void) {
        DispatchQueue.global(qos: .userInitiated).async {
            do {
                try self.login()
                let result = try self.fetchFromSession()
                DispatchQueue.main.async { completion(.success(result)) }
            } catch {
                DispatchQueue.main.async { completion(.failure(error)) }
            }
        }
    }

    // MARK: - Login

    private func login() throws {
        let loginUrl = XmuJw.idpBase + "/authserver/login?type=userNameLogin&service=" + XmuJw.encode(XmuJw.portalService)
        let page = try request(urlString: loginUrl, method: "GET").body

        let salt = field(page, "pwdEncryptSalt")
        let execution = field(page, "execution")
        let lt = regexValue(page, pattern: "name=\"lt\" id=\"lt\" value=\"([^\"]*)\"") ?? ""

        guard let salt, let execution else {
            throw LoginError.service("登录页缺少加密参数，教务登录流程可能已变更")
        }

        let (plain, iv) = AesCrypto.xmuPasswordPlain(password: password)
        guard let encPwd = AesCrypto.encryptBase64(plain: plain, key: salt, iv: iv) else {
            throw LoginError.service("密码加密失败")
        }

        let form = [
            "username=\(XmuJw.encode(account))",
            "password=\(XmuJw.encode(encPwd))",
            "captcha=",
            "lt=\(XmuJw.encode(lt))",
            "execution=\(XmuJw.encode(execution))",
            "_eventId=submit",
            "cllt=userNameLogin",
            "dllt=generalLogin",
            "rmShown=1"
        ].joined(separator: "&")

        let resp = try request(
            urlString: loginUrl, method: "POST", body: form,
            headers: [
                "Content-Type": "application/x-www-form-urlencoded",
                "Referer": loginUrl,
                "Origin": XmuJw.idpBase
            ],
            failOnHttpError: false
        )

        let tip = extractLoginTip(resp.body)
        if resp.status >= 400 {
            throw classify(status: resp.status, tip: tip)
        }
        if !resp.finalUrl.contains("jw.xmu.edu.cn") {
            throw classify(status: resp.status, tip: tip)
        }
        _ = try? request(urlString: XmuJw.jwBase + "/new/index.html", method: "GET")
    }

    private func fetchFromSession() throws -> FetchResult {
        _ = try? request(urlString: XmuJw.jwBase + "/new/index.html", method: "GET")
        _ = try request(urlString: XmuJw.jwBase + XmuJw.appEntryPath, method: "GET")

        var semesterCode: String? = nil
        if let termBody = try? postForm(url: XmuJw.gsappApiBase + "/modules/xskcb/kfdxnxqcx.do", body: "") {
            semesterCode = parseSemester(from: termBody)
        }
        if semesterCode == nil || semesterCode!.isEmpty {
            semesterCode = XmuJw.inferSemesterCode()
        }
        guard let code = semesterCode, !code.isEmpty else {
            throw LoginError.service("无法确定当前学年学期")
        }

        let courseBody = try postForm(
            url: XmuJw.gsappApiBase + "/wdkcb/queryXspkjg.do",
            body: "XH=\(XmuJw.encode(account))&XNXQDM=\(XmuJw.encode(code))"
        )

        var periods: [(Int, String, String)] = []
        if let pBody = try? postForm(
            url: XmuJw.gsappApiBase + "/wdkcb/queryXsskjc.do",
            body: "XH=\(XmuJw.encode(account))&XNXQDM=\(XmuJw.encode(code))"
        ) {
            periods = parsePeriods(from: pBody)
        }

        return FetchResult(courseJson: courseBody, periods: periods, semesterCode: code)
    }

    // MARK: - HTTP

    private struct Resp {
        var status: Int
        var body: String
        var finalUrl: String
    }

    private func postForm(url: String, body: String) throws -> String {
        try request(
            urlString: url, method: "POST", body: body,
            headers: ["Content-Type": "application/x-www-form-urlencoded"]
        ).body
    }

    private func request(
        urlString: String, method: String, body: String? = nil,
        headers: [String: String] = [:], failOnHttpError: Bool = true
    ) throws -> Resp {
        var currentUrl = urlString
        var currentMethod = method
        var currentBody = body
        var hops = 0

        while hops < 8 {
            hops += 1
            guard let url = URL(string: currentUrl) else {
                throw LoginError.network("非法 URL: \(currentUrl)")
            }
            var req = URLRequest(url: url)
            req.httpMethod = currentMethod
            req.setValue(XmuJw.desktopUA, forHTTPHeaderField: "User-Agent")
            req.setValue("*/*", forHTTPHeaderField: "Accept")
            req.setValue("zh-CN,zh;q=0.9,en;q=0.8", forHTTPHeaderField: "Accept-Language")
            if let cookie = cookieHeader(for: url) {
                req.setValue(cookie, forHTTPHeaderField: "Cookie")
            }
            for (k, v) in headers { req.setValue(v, forHTTPHeaderField: k) }
            if currentMethod == "POST", let b = currentBody {
                req.httpBody = Data(b.utf8)
            }

            let sem = DispatchSemaphore(value: 0)
            var resultData: Data?
            var resultResp: URLResponse?
            var resultErr: Error?
            session.dataTask(with: req) { data, resp, err in
                resultData = data
                resultResp = resp
                resultErr = err
                sem.signal()
            }.resume()
            sem.wait()

            if let err = resultErr {
                throw LoginError.network(err.localizedDescription)
            }
            guard let http = resultResp as? HTTPURLResponse else {
                throw LoginError.network("无效响应")
            }
            storeCookies(from: http, url: url)
            let bodyText = resultData.flatMap { String(data: $0, encoding: .utf8) } ?? ""

            if (300...399).contains(http.statusCode) {
                if let loc = http.value(forHTTPHeaderField: "Location"), !loc.isEmpty {
                    let next = URL(string: loc, relativeTo: url)?.absoluteString ?? loc
                    if http.statusCode != 307 && http.statusCode != 308 {
                        currentMethod = "GET"
                        currentBody = nil
                    }
                    currentUrl = next
                    continue
                }
            }

            if failOnHttpError && http.statusCode >= 400 {
                throw LoginError.service("HTTP \(http.statusCode)")
            }
            return Resp(status: http.statusCode, body: bodyText, finalUrl: http.url?.absoluteString ?? currentUrl)
        }
        throw LoginError.network("重定向次数过多")
    }

    private func cookieHeader(for url: URL) -> String? {
        let host = url.host ?? ""
        // share cookies across xmu.edu.cn
        var parts: [String] = []
        for (h, v) in cookies {
            if host.hasSuffix("xmu.edu.cn") || h.hasSuffix("xmu.edu.cn") {
                if host == h || host.hasSuffix(h) || h.hasSuffix(host) {
                    parts.append(v)
                }
            }
        }
        return parts.isEmpty ? nil : parts.joined(separator: "; ")
    }

    private func storeCookies(from http: HTTPURLResponse, url: URL) {
        guard let fields = http.allHeaderFields as? [String: String] else { return }
        let host = url.host ?? ""
        // HTTPURLResponse merges Set-Cookie sometimes; also check values(forHTTPHeaderField:)
        var setCookies: [String] = []
        if let all = http.allHeaderFields["Set-Cookie"] as? String {
            setCookies = [all]
        } else if let arr = http.allHeaderFields["Set-Cookie"] as? [String] {
            setCookies = arr
        }
        // try case variants
        if setCookies.isEmpty {
            for (k, v) in http.allHeaderFields {
                if let ks = k as? String, ks.lowercased() == "set-cookie" {
                    if let vs = v as? String { setCookies.append(vs) }
                    if let va = v as? [String] { setCookies.append(contentsOf: va) }
                }
            }
        }
        var merged = cookies[host] ?? ""
        for sc in setCookies {
            let pair = sc.split(separator: ";").first.map(String.init)?.trimmingCharacters(in: .whitespaces) ?? ""
            if pair.isEmpty { continue }
            let name = pair.split(separator: "=").first.map(String.init) ?? ""
            if !merged.isEmpty {
                // replace existing name
                let parts = merged.split(separator: ";").map { $0.trimmingCharacters(in: .whitespaces) }
                var dict: [String: String] = [:]
                for p in parts {
                    let n = p.split(separator: "=").first.map(String.init) ?? p
                    dict[n] = p
                }
                dict[name] = pair
                merged = dict.values.joined(separator: "; ")
            } else {
                merged = pair
            }
        }
        if !merged.isEmpty {
            cookies[host] = merged
            // also share to other xmu hosts
            cookies["ids.xmu.edu.cn"] = merged
            cookies["jw.xmu.edu.cn"] = merged
        }
        _ = fields
    }

    // MARK: - Helpers

    private func field(_ html: String, _ name: String) -> String? {
        regexValue(html, pattern: "id=\"\(name)\"[^>]*value=\"([^\"]*)\"")
            ?? regexValue(html, pattern: "name=\"\(name)\"[^>]*value=\"([^\"]*)\"")
            ?? regexValue(html, pattern: "value=\"([^\"]*)\"[^>]*id=\"\(name)\"")
    }

    private func regexValue(_ text: String, pattern: String) -> String? {
        guard let re = try? NSRegularExpression(pattern: pattern) else { return nil }
        let range = NSRange(text.startIndex..., in: text)
        guard let m = re.firstMatch(in: text, range: range),
              let r = Range(m.range(at: 1), in: text) else { return nil }
        return String(text[r])
    }

    private func extractLoginTip(_ html: String) -> String {
        regexValue(html, pattern: "id=\"errorMsg\"[^>]*>([^<]*)")?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
    }

    private func classify(status: Int, tip: String) -> LoginError {
        if tip.contains("验证码") || tip.contains("captcha") {
            return .invalidCredentials("登录被拒绝，请核对学号与密码")
        }
        if tip.contains("密码") || tip.contains("用户名") || tip.contains("账号") {
            return .invalidCredentials(tip.isEmpty ? "账号或密码错误" : tip)
        }
        if !tip.isEmpty {
            return .invalidCredentials(tip)
        }
        return .invalidCredentials("登录失败（HTTP \(status)），请检查账号密码")
    }

    private func parseSemester(from json: String) -> String? {
        guard let data = json.data(using: .utf8),
              let root = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
              let datas = root["datas"] as? [String: Any],
              let k = datas["kfdxnxqcx"] as? [String: Any],
              let rows = k["rows"] as? [[String: Any]],
              let first = rows.first
        else { return nil }
        if let code = first["XNXQDM"] as? String, !code.isEmpty { return code }
        return nil
    }

    private func parsePeriods(from json: String) -> [(Int, String, String)] {
        guard let data = json.data(using: .utf8),
              let root = try? JSONSerialization.jsonObject(with: data) as? [String: Any]
        else { return [] }
        let arr = (root["data"] as? [[String: Any]]) ?? (root["rows"] as? [[String: Any]]) ?? []
        var out: [(Int, String, String)] = []
        for o in arr {
            let dm = (o["DM"] as? Int) ?? (o["DM"] as? NSNumber)?.intValue ?? -1
            let ks = fmtHM(o["KSSJ"])
            let js = fmtHM(o["JSSJ"])
            if dm >= 1, let ks, let js {
                out.append((dm, ks, js))
            }
        }
        return out.sorted { $0.0 < $1.0 }
    }

    private func fmtHM(_ any: Any?) -> String? {
        if let s = any as? String {
            // maybe "08:00:00" or "0800"
            if s.contains(":") {
                let parts = s.split(separator: ":")
                if parts.count >= 2 {
                    return String(format: "%02d:%02d", Int(parts[0]) ?? 0, Int(parts[1]) ?? 0)
                }
            }
            if s.count == 4, s.allSatisfy(\.isNumber) {
                return "\(s.prefix(2)):\(s.suffix(2))"
            }
            return s
        }
        if let n = any as? Int {
            let h = n / 100, m = n % 100
            return String(format: "%02d:%02d", h, m)
        }
        return nil
    }
}
