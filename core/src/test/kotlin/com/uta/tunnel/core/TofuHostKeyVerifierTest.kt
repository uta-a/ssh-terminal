package com.uta.tunnel.core

import com.uta.tunnel.core.model.HostAddress
import com.uta.tunnel.core.model.HostKeyEntry
import com.uta.tunnel.core.ssh.HostKeyChangedException
import com.uta.tunnel.core.ssh.InMemoryHostKeyStore
import com.uta.tunnel.core.ssh.TofuHostKeyVerifier
import com.uta.tunnel.core.ssh.TofuResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class TofuHostKeyVerifierTest {
    private val addr = HostAddress("example.com", 22)

    @Test
    fun firstConnection_savesFingerprintAndAccepts() {
        val store = InMemoryHostKeyStore()
        val verifier = TofuHostKeyVerifier(store)

        val result = verifier.verify(addr, "ssh-ed25519", "SHA256:AAA")

        assertTrue(result is TofuResult.FirstSeen)
        assertEquals("SHA256:AAA", store.find(addr)?.fingerprint)
    }

    @Test
    fun sameFingerprint_isTrusted() {
        val store = InMemoryHostKeyStore()
        val verifier = TofuHostKeyVerifier(store)
        verifier.verify(addr, "ssh-ed25519", "SHA256:AAA")

        val result = verifier.verify(addr, "ssh-ed25519", "SHA256:AAA")

        assertEquals(TofuResult.Trusted, result)
    }

    @Test
    fun changedFingerprint_throws() {
        val store = InMemoryHostKeyStore()
        val verifier = TofuHostKeyVerifier(store)
        verifier.verify(addr, "ssh-ed25519", "SHA256:AAA")

        try {
            verifier.verify(addr, "ssh-ed25519", "SHA256:BBB")
            fail("ホスト鍵変化で例外が投げられるべき")
        } catch (e: HostKeyChangedException) {
            assertEquals("SHA256:AAA", e.expected.fingerprint)
            assertEquals("SHA256:BBB", e.presentedFingerprint)
        }
    }

    /** 承認 UI が上書き保存に使う新エントリ（提示された keyType を含む）を例外が運ぶこと。 */
    @Test
    fun changedFingerprint_exceptionCarriesPresentedEntry() {
        val store = InMemoryHostKeyStore()
        val verifier = TofuHostKeyVerifier(store)
        verifier.verify(addr, "ssh-ed25519", "SHA256:AAA")

        try {
            verifier.verify(addr, "ssh-rsa", "SHA256:BBB")
            fail("ホスト鍵変化で例外が投げられるべき")
        } catch (e: HostKeyChangedException) {
            assertEquals("ssh-rsa", e.presentedKeyType)
            assertEquals(HostKeyEntry(addr, "ssh-rsa", "SHA256:BBB"), e.presentedEntry)
        }
    }

    /** 承認しない限り既知の鍵は書き換わらないこと（拒否＝現状維持）。 */
    @Test
    fun changedFingerprint_doesNotOverwriteStoredEntry() {
        val store = InMemoryHostKeyStore()
        val verifier = TofuHostKeyVerifier(store)
        verifier.verify(addr, "ssh-ed25519", "SHA256:AAA")

        runCatching { verifier.verify(addr, "ssh-ed25519", "SHA256:BBB") }

        assertEquals("SHA256:AAA", store.find(addr)?.fingerprint)
    }

    @Test
    fun store_listsAndDeletesEntries() {
        val store = InMemoryHostKeyStore()
        val other = HostAddress("example.com", 2222)
        store.save(HostKeyEntry(addr, "ssh-ed25519", "SHA256:AAA"))
        store.save(HostKeyEntry(other, "ssh-rsa", "SHA256:BBB"))

        assertEquals(2, store.list().size)

        store.delete(addr)

        assertEquals(listOf(HostKeyEntry(other, "ssh-rsa", "SHA256:BBB")), store.list())
        assertNull(store.find(addr))
    }

    /** 削除後は次回接続が再び TOFU 初回扱い（＝無条件受理）に戻ること。 */
    @Test
    fun deletedHost_isFirstSeenAgain() {
        val store = InMemoryHostKeyStore()
        val verifier = TofuHostKeyVerifier(store)
        verifier.verify(addr, "ssh-ed25519", "SHA256:AAA")

        store.delete(addr)

        assertTrue(verifier.verify(addr, "ssh-ed25519", "SHA256:BBB") is TofuResult.FirstSeen)
    }
}
