package org.graphiks.kanvas.gpu.renderer.materials

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class CanonicalIdentityEncoderTest {
    @Test
    fun `field boundaries names types counts and domains are unambiguous`() {
        val first = CanonicalIdentityEncoder("domain=a")
            .text("first", "value\nsecond=forged|separator")
            .text("second", "tail")
            .digestIdentity()
        val shiftedBoundary = CanonicalIdentityEncoder("domain=a")
            .text("first", "value")
            .text("second", "forged|separator\nsecond=tail")
            .digestIdentity()
        val changedName = CanonicalIdentityEncoder("domain=a")
            .text("renamed", "value\nsecond=forged|separator")
            .text("second", "tail")
            .digestIdentity()
        val changedType = CanonicalIdentityEncoder("domain=a")
            .bytes("first", "value\nsecond=forged|separator".encodeToByteArray())
            .text("second", "tail")
            .digestIdentity()
        val changedCount = CanonicalIdentityEncoder("domain=a")
            .texts("facts", listOf("a\nb=c", "d"))
            .digestIdentity()
        val changedListBoundary = CanonicalIdentityEncoder("domain=a")
            .texts("facts", listOf("a", "b=c\nd"))
            .digestIdentity()
        val changedDomain = CanonicalIdentityEncoder("domain=b")
            .text("first", "value\nsecond=forged|separator")
            .text("second", "tail")
            .digestIdentity()

        listOf(
            shiftedBoundary,
            changedName,
            changedType,
            changedCount,
            changedListBoundary,
            changedDomain,
        ).forEach { identity ->
            assertNotEquals(first, identity)
            assertTrue(identity.matches(Regex("sha256:[0-9a-f]{64}")))
        }
        assertEquals(
            first,
            CanonicalIdentityEncoder("domain=a")
                .text("first", "value\nsecond=forged|separator")
                .text("second", "tail")
                .digestIdentity(),
        )
    }
}
