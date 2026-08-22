package org.graphiks.kanvas.gpu.evidence.compare

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.graphiks.kanvas.gpu.evidence.catalog.ComparisonPolicy

class EvidenceComparatorTest {
    private val policy = ComparisonPolicy(1, 100.0, 7, "strict")

    @Test fun `identical rgba passes with deterministic empty diff`() {
        val pixels = byteArrayOf(0, 0, 0, 0, 20, 30, 40, 50)
        val first = EvidenceComparator().compare(pixels, pixels, 2, 1, policy)
        val second = EvidenceComparator().compare(pixels, pixels, 2, 1, policy)
        assertTrue(first.passed)
        assertEquals(100.0, first.similarityPercent)
        assertEquals(0, first.differingPixels)
        assertContentEquals(first.diffRgba, second.diffRgba)
        assertContentEquals(ByteArray(8), first.diffRgba)
    }

    @Test fun `one channel inside tolerance matches and outside tolerance differs`() {
        val expected = byteArrayOf(10, 20, 30, 40)
        val inside = byteArrayOf(11, 20, 30, 40)
        val outside = byteArrayOf(12, 20, 30, 40)
        assertTrue(EvidenceComparator().compare(inside, expected, 1, 1, policy).passed)
        assertFalse(EvidenceComparator().compare(outside, expected, 1, 1, policy).passed)
    }

    @Test fun `transparent pixels compare all channels`() {
        val expected = byteArrayOf(0, 0, 0, 0)
        val actual = byteArrayOf(2, 0, 0, 0)
        assertFalse(EvidenceComparator().compare(actual, expected, 1, 1, policy).passed)
    }

    @Test fun `mismatched dimensions and byte count are rejected`() {
        val comparator = EvidenceComparator()
        kotlin.test.assertFailsWith<IllegalArgumentException> {
            comparator.compare(ByteArray(4), ByteArray(4), 2, 1, policy)
        }
        kotlin.test.assertFailsWith<IllegalArgumentException> {
            comparator.compare(ByteArray(4), ByteArray(8), 1, 1, policy)
        }
    }
}
