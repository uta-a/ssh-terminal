package com.uta.tunnel.core.ssh

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * [SshKeyUtil.derivePublicKey] の既知ベクタ検証。
 * 期待値は ssh-keygen で生成した鍵ペアの `.pub` と `ssh-keygen -lf` の出力。
 */
class SshKeyUtilTest {

    private val ed25519Pem = """
        -----BEGIN OPENSSH PRIVATE KEY-----
        b3BlbnNzaC1rZXktdjEAAAAABG5vbmUAAAAEbm9uZQAAAAAAAAABAAAAMwAAAAtzc2gtZW
        QyNTUxOQAAACAr+1/6dI7Oc2D68oiK7o7GNgdthj9YGblzFGzkyAXLxwAAAJAISmrzCEpq
        8wAAAAtzc2gtZWQyNTUxOQAAACAr+1/6dI7Oc2D68oiK7o7GNgdthj9YGblzFGzkyAXLxw
        AAAEA27TGQMbmONa/AiI6E64XGiT1e5HHl0tA4JsOXrbYJAiv7X/p0js5zYPryiIrujsY2
        B22GP1gZuXMUbOTIBcvHAAAAC3Rlc3RAaXJ0b29sAQI=
        -----END OPENSSH PRIVATE KEY-----
    """.trimIndent()

    private val encryptedEd25519Pem = """
        -----BEGIN OPENSSH PRIVATE KEY-----
        b3BlbnNzaC1rZXktdjEAAAAACmFlczI1Ni1jdHIAAAAGYmNyeXB0AAAAGAAAABAB+62bQt
        2q78yZoC4DyykNAAAAGAAAAAEAAAAzAAAAC3NzaC1lZDI1NTE5AAAAIHLdwo1hXQX27mhZ
        YXDzcWhWqpMwp3qy6mhOVIMPmRZwAAAAkPOS/bySPm+2czAzMCXp2lH83LqDwdcLGiJ/Ie
        XIYsr0qVdb2hmtL2joqpAqKnVrTg6Fk7AKitkdOYo22KMhrrYHX6PFPpn45sLS7QFjuGIt
        +Q0deEMkG/K+SsPvUwxc/1Z2/rgW03Po8UqF8K95XZdqwJGGXGs7UAGjv0CcF+j1JOBBVW
        rTKT5Z1ZNNZShuqw==
        -----END OPENSSH PRIVATE KEY-----
    """.trimIndent()

    @Test
    fun ed25519_matchesSshKeygenOutput() {
        val info = SshKeyUtil.derivePublicKey(ed25519Pem, null)
        assertEquals("ssh-ed25519", info.keyType)
        assertEquals(
            "ssh-ed25519 AAAAC3NzaC1lZDI1NTE5AAAAICv7X/p0js5zYPryiIrujsY2B22GP1gZuXMUbOTIBcvH",
            info.authorizedKey,
        )
        assertEquals("SHA256:W8lqpKNBEt9P847a+3v9hfrT14WYa49+J17sf1z0EYc", info.fingerprint)
    }

    @Test
    fun encryptedEd25519_withPassphrase_matchesSshKeygenOutput() {
        val info = SshKeyUtil.derivePublicKey(encryptedEd25519Pem, "secret123")
        assertEquals(
            "ssh-ed25519 AAAAC3NzaC1lZDI1NTE5AAAAIHLdwo1hXQX27mhZYXDzcWhWqpMwp3qy6mhOVIMPmRZw",
            info.authorizedKey,
        )
        assertEquals("SHA256:i1RqkVNcH6PYFpSIPz+YlgirNByzsJcW3KvLR/WpPQg", info.fingerprint)
    }

    @Test
    fun encryptedKey_withWrongPassphrase_throws() {
        assertThrows(Exception::class.java) {
            SshKeyUtil.derivePublicKey(encryptedEd25519Pem, "wrong")
        }
    }

    @Test
    fun garbage_throws() {
        assertThrows(Exception::class.java) {
            SshKeyUtil.derivePublicKey("not a key", null)
        }
    }
}
