import Foundation
import CryptoKit

/// AES-128-CBC + PKCS7 — 对齐厦大 ids 密码加密
enum AesCrypto {
    /// 随机 N 字符（与 Android 端一致用可打印字母数字）
    static func randomAlphanumeric(_ n: Int) -> String {
        let chars = Array("abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789")
        return String((0..<n).map { _ in chars.randomElement()! })
    }

    /// 生成 16 字符 IV
    static func randomIV() -> String { randomAlphanumeric(16) }

    /// PKCS7 填充
    static func pkcs7Pad(_ data: Data, blockSize: Int) -> Data {
        let pad = blockSize - (data.count % blockSize)
        return data + Data(repeating: UInt8(pad), count: pad)
    }

    /// AES-128-CBC 加密；key/iv 为 UTF-8 字符串
    /// - Returns: Base64 密文
    static func encryptBase64(plain: String, key: String, iv: String) -> String? {
        guard let keyData = key.data(using: .utf8), keyData.count == 16,
              let ivData = iv.data(using: .utf8), ivData.count == 16,
              let plainData = plain.data(using: .utf8)
        else { return nil }

        let padded = pkcs7Pad(plainData, blockSize: 16)
        guard let cipher = AES128.cbcEncrypt([UInt8](padded), key: [UInt8](keyData), iv: [UInt8](ivData)) else {
            return nil
        }
        return Data(cipher).base64EncodedString()
    }

    /// 厦大密码明文 = 64 随机字符 + 真实密码
    static func xmuPasswordPlain(password: String) -> (plain: String, iv: String) {
        let iv = randomIV()
        let plain = randomAlphanumeric(64) + password
        return (plain, iv)
    }
}

/// Minimal AES-128 (ECB block + CBC mode)
enum AES128 {
    private static let sbox: [UInt8] = [
        0x63,0x7c,0x77,0x7b,0xf2,0x6b,0x6f,0xc5,0x30,0x01,0x67,0x2b,0xfe,0xd7,0xab,0x76,
        0xca,0x82,0xc9,0x7d,0xfa,0x59,0x47,0xf0,0xad,0xd4,0xa2,0xaf,0x9c,0xa4,0x72,0xc0,
        0xb7,0xfd,0x93,0x26,0x36,0x3f,0xf7,0xcc,0x34,0xa5,0xe5,0xf1,0x71,0xd8,0x31,0x15,
        0x04,0xc7,0x23,0xc3,0x18,0x96,0x05,0x9a,0x07,0x12,0x80,0xe2,0xeb,0x27,0xb2,0x75,
        0x09,0x83,0x2c,0x1a,0x1b,0x6e,0x5a,0xa0,0x52,0x3b,0xd6,0xb3,0x29,0xe3,0x2f,0x84,
        0x53,0xd1,0x00,0xed,0x20,0xfc,0xb1,0x5b,0x6a,0xcb,0xbe,0x39,0x4a,0x4c,0x58,0xcf,
        0xd0,0xef,0xaa,0xfb,0x43,0x4d,0x33,0x85,0x45,0xf9,0x02,0x7f,0x50,0x3c,0x9f,0xa8,
        0x51,0xa3,0x40,0x8f,0x92,0x9d,0x38,0xf5,0xbc,0xb6,0xda,0x21,0x10,0xff,0xf3,0xd2,
        0xcd,0x0c,0x13,0xec,0x5f,0x97,0x44,0x17,0xc4,0xa7,0x7e,0x3d,0x64,0x5d,0x19,0x73,
        0x60,0x81,0x4f,0xdc,0x22,0x2a,0x90,0x88,0x46,0xee,0xb8,0x14,0xde,0x5e,0x0b,0xdb,
        0xe0,0x32,0x3a,0x0a,0x49,0x06,0x24,0x5c,0xc2,0xd3,0xac,0x62,0x91,0x95,0xe4,0x79,
        0xe7,0xc8,0x37,0x6d,0x8d,0xd5,0x4e,0xa9,0x6c,0x56,0xf4,0xea,0x65,0x7a,0xae,0x08,
        0xba,0x78,0x25,0x2e,0x1c,0xa6,0xb4,0xc6,0xe8,0xdd,0x74,0x1f,0x4b,0xbd,0x8b,0x8a,
        0x70,0x3e,0xb5,0x66,0x48,0x03,0xf6,0x0e,0x61,0x35,0x57,0xb9,0x86,0xc1,0x1d,0x9e,
        0xe1,0xf8,0x98,0x11,0x69,0xd9,0x8e,0x94,0x9b,0x1e,0x87,0xe9,0xce,0x55,0x28,0xdf,
        0x8c,0xa1,0x89,0x0d,0xbf,0xe6,0x42,0x68,0x41,0x99,0x2d,0x0f,0xb0,0x54,0xbb,0x16
    ]

    private static let rcon: [UInt8] = [0x00,0x01,0x02,0x04,0x08,0x10,0x20,0x40,0x80,0x1b,0x36]

    private static func xtime(_ a: UInt8) -> UInt8 {
        let hi = a & 0x80
        let b = a << 1
        return hi != 0 ? b ^ 0x1b : b
    }

    private static func expandKey(_ key: [UInt8]) -> [[UInt8]] {
        var w = key
        for i in 4..<44 {
            var t = [w[(i-1)*4], w[(i-1)*4+1], w[(i-1)*4+2], w[(i-1)*4+3]]
            if i % 4 == 0 {
                t = [sbox[Int(t[1])] ^ rcon[i/4], sbox[Int(t[2])], sbox[Int(t[3])], sbox[Int(t[0])]]
            }
            w.append(contentsOf: (0..<4).map { w[(i-4)*4 + $0] ^ t[$0] })
        }
        var roundKeys: [[UInt8]] = []
        for r in 0..<11 {
            roundKeys.append(Array(w[(r*16)..<(r*16+16)]))
        }
        return roundKeys
    }

    private static func addRoundKey(_ s: inout [UInt8], _ rk: [UInt8]) {
        for i in 0..<16 { s[i] ^= rk[i] }
    }

    private static func subBytes(_ s: inout [UInt8]) {
        for i in 0..<16 { s[i] = sbox[Int(s[i])] }
    }

    private static func shiftRows(_ s: inout [UInt8]) {
        var t = s
        // column-major in state as s[c*4+r]
        for r in 1..<4 {
            for c in 0..<4 {
                t[c*4 + r] = s[((c + r) % 4) * 4 + r]
            }
        }
        s = t
    }

    private static func mixColumns(_ s: inout [UInt8]) {
        for c in 0..<4 {
            let i = c * 4
            let a0 = s[i], a1 = s[i+1], a2 = s[i+2], a3 = s[i+3]
            s[i]   = xtime(a0) ^ (xtime(a1) ^ a1) ^ a2 ^ a3
            s[i+1] = a0 ^ xtime(a1) ^ (xtime(a2) ^ a2) ^ a3
            s[i+2] = a0 ^ a1 ^ xtime(a2) ^ (xtime(a3) ^ a3)
            s[i+3] = (xtime(a0) ^ a0) ^ a1 ^ a2 ^ xtime(a3)
        }
    }

    static func encryptBlock(_ block: [UInt8], roundKeys: [[UInt8]]) -> [UInt8] {
        var s = block
        addRoundKey(&s, roundKeys[0])
        for r in 1..<10 {
            subBytes(&s)
            shiftRows(&s)
            mixColumns(&s)
            addRoundKey(&s, roundKeys[r])
        }
        subBytes(&s)
        shiftRows(&s)
        addRoundKey(&s, roundKeys[10])
        return s
    }

    static func cbcEncrypt(_ data: [UInt8], key: [UInt8], iv: [UInt8]) -> [UInt8]? {
        guard key.count == 16, iv.count == 16, data.count % 16 == 0 else { return nil }
        let rk = expandKey(key)
        var out: [UInt8] = []
        var prev = iv
        var i = 0
        while i < data.count {
            let block = Array(data[i..<i+16])
            let xored = zip(block, prev).map { $0 ^ $1 }
            let enc = encryptBlock(xored, roundKeys: rk)
            out.append(contentsOf: enc)
            prev = enc
            i += 16
        }
        return out
    }
}
