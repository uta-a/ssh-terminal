package com.uta.tunnel.ssh

import java.security.Security

/**
 * sshj を Android で動かすための BouncyCastle プロバイダ調整。
 *
 * Android には "BC" という名前の縮小版 BouncyCastle（`com.android.org.bouncycastle`、
 * 一部アルゴリズム欠落）が同梱されている。sshj は "BC" プロバイダを本物とみなして参照するため、
 * これを削除し、sshj が推移的に持ち込む本物の BouncyCastle（`org.bouncycastle`）を最優先で登録する。
 *
 * アプリ起動時に一度だけ [ensureBouncyCastle] を呼ぶ。
 */
object SshSecurity {
    @Volatile private var done = false

    fun ensureBouncyCastle() {
        if (done) return
        synchronized(this) {
            if (done) return
            runCatching {
                Security.removeProvider("BC")
                Security.insertProviderAt(org.bouncycastle.jce.provider.BouncyCastleProvider(), 1)
            }
            done = true
        }
    }
}
