package com.uta.tunnel.core.ssh

import com.uta.tunnel.core.model.HostAddress
import com.uta.tunnel.core.model.HostKeyEntry

/**
 * 提示されたホスト鍵が既知の鍵と一致しないときに投げる。UI は MITM 警告を出す。
 *
 * [presentedKeyType] と [presentedFingerprint] は、ユーザーが承認したときに
 * [HostKeyStore.save] で上書きする新エントリの材料になる（[presentedEntry]）。
 */
class HostKeyChangedException(
    val address: HostAddress,
    val expected: HostKeyEntry,
    val presentedKeyType: String,
    val presentedFingerprint: String,
) : Exception(
    "ホスト鍵が変化しました: $address 期待=${expected.fingerprint} 提示=$presentedFingerprint",
) {
    /** ユーザーが承認した場合に保存する新しいエントリ。 */
    val presentedEntry: HostKeyEntry
        get() = HostKeyEntry(address, presentedKeyType, presentedFingerprint)
}

/** TOFU 照合の結果。 */
sealed interface TofuResult {
    /** 初回接続。フィンガープリントを保存済み。 */
    data class FirstSeen(val entry: HostKeyEntry) : TofuResult

    /** 既知の鍵と一致。 */
    data object Trusted : TofuResult
}

/**
 * Trust On First Use のホスト鍵検証ロジック（Android 非依存・純ロジック）。
 *
 * - 未登録なら保存して [TofuResult.FirstSeen]（＝受理）
 * - 既知と一致すれば [TofuResult.Trusted]（＝受理）
 * - 既知と不一致なら [HostKeyChangedException]（＝拒否）。ユーザーが承認した場合のみ
 *   呼び出し側が [HostKeyStore.save] で上書きする。
 *
 * フィンガープリント計算は sshj 側（`:app`/接続層）で行い、結果文字列を渡す。
 */
class TofuHostKeyVerifier(private val store: HostKeyStore) {

    fun verify(address: HostAddress, keyType: String, fingerprint: String): TofuResult {
        val known = store.find(address)
        return when {
            known == null -> {
                val entry = HostKeyEntry(address, keyType, fingerprint)
                store.save(entry)
                TofuResult.FirstSeen(entry)
            }
            known.fingerprint == fingerprint -> TofuResult.Trusted
            else -> throw HostKeyChangedException(address, known, keyType, fingerprint)
        }
    }
}
