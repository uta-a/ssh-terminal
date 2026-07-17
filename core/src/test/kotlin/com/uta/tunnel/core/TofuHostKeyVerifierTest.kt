package com.uta.tunnel.core

import com.uta.tunnel.core.model.HostAddress
import com.uta.tunnel.core.ssh.HostKeyChangedException
import com.uta.tunnel.core.ssh.InMemoryHostKeyStore
import com.uta.tunnel.core.ssh.TofuHostKeyVerifier
import com.uta.tunnel.core.ssh.TofuResult
import org.junit.Assert.assertEquals
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
}
