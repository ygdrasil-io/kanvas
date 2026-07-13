package org.graphiks.kanvas.codec.jpeg

import org.graphiks.kanvas.codec.Codec
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.function.ThrowingSupplier
import org.skia.foundation.SkColorType
import java.util.Random

/**
 * Deterministic malformed-input coverage for the bounded JPEG document API.
 * This is intentionally a small reproducible corpus mutator, not a time- or
 * coverage-dependent fuzzer: a failure identifies both the source process and
 * the exact mutation class in the JUnit report.
 */
class JpegFuzzTest {

    @Test
    fun `fuzz outcome assertion rejects a result without bitmap or diagnostic`() {
        assertThrows(AssertionError::class.java) {
            assertDecodeOutcome(JpegDecodeResult(bitmap = null, diagnostic = null), "synthetic")
        }
    }

    @Test
    fun `fixed seed mutations never escape bounded JPEG diagnostics`() {
        val random = Random(0x4A50_4547L)
        val mutations = JpegConformanceFixtures.streams().flatMap { stream ->
            mutationsFor(stream.bytes, random).map { mutation -> "${stream.name}: ${mutation.name}" to mutation.bytes }
        }

        assertTrue(mutations.isNotEmpty())
        for ((name, bytes) in mutations) {
            val opened = assertDoesNotThrow(
                ThrowingSupplier { JpegDocument.open(bytes, JpegLimits.DEFAULT) },
                name,
            )
            assertTrue(opened.document != null || opened.diagnostic != null, name)
            opened.document?.let { document ->
                val decoded = assertDoesNotThrow(
                    ThrowingSupplier { document.decode(JpegDecodeRequest(SkColorType.kRGBA_8888, null)) },
                    name,
                )
                assertDecodeOutcome(decoded, name)
            }
        }
    }

    private fun assertDecodeOutcome(decoded: JpegDecodeResult, name: String) {
        assertTrue(decoded.bitmap != null || decoded.diagnostic != null, name)
        decoded.diagnostic?.let { diagnostic ->
            assertTrue(diagnostic.code.isNotBlank(), "$name must report a diagnostic code")
            assertFalse(diagnostic.result == Codec.Result.kSuccess, "$name must not diagnose success")
        }
    }

    private fun mutationsFor(source: ByteArray, random: Random): List<Mutation> = buildList {
        add(Mutation("marker-length", corruptLength(source)))
        add(Mutation("entropy-byte", corruptEntropy(source, random)))
        add(Mutation("segment-order", swapFirstHeaderSegments(source)))
        corruptDriOrDac(source, random)?.let { add(Mutation("DRI-or-DAC", it)) }
        for (offset in setOf(0, 1, 2, source.size / 2, (source.size - 1).coerceAtLeast(0))) {
            add(Mutation("truncation-$offset", source.copyOf(offset)))
        }
    }

    private fun corruptLength(source: ByteArray): ByteArray {
        val copy = source.copyOf()
        val offset = headerSegments(copy).firstOrNull()?.start ?: return copy
        copy[offset + 2] = 0
        copy[offset + 3] = 1
        return copy
    }

    private fun corruptEntropy(source: ByteArray, random: Random): ByteArray {
        val copy = source.copyOf()
        val sos = firstMarker(copy, 0xDA) ?: return copy
        val payloadLength = u16(copy, sos + 2)
        val entropyStart = sos + 2 + payloadLength
        if (entropyStart < copy.lastIndex) {
            val offset = entropyStart + random.nextInt((copy.lastIndex - entropyStart).coerceAtLeast(1))
            copy[offset] = (copy[offset].toInt() xor (1 shl random.nextInt(8))).toByte()
        }
        return copy
    }

    private fun swapFirstHeaderSegments(source: ByteArray): ByteArray {
        val segments = headerSegments(source)
        if (segments.size < 2) return source.copyOf()
        val first = segments[0]
        val second = segments[1]
        return ByteArray(source.size).also { output ->
            source.copyInto(output, endIndex = first.start)
            source.copyInto(output, destinationOffset = first.start, startIndex = second.start, endIndex = second.endExclusive)
            source.copyInto(output, destinationOffset = first.start + second.length, startIndex = first.start, endIndex = first.endExclusive)
            source.copyInto(output, destinationOffset = second.endExclusive, startIndex = second.endExclusive)
        }
    }

    private fun corruptDriOrDac(source: ByteArray, random: Random): ByteArray? {
        val marker = firstMarker(source, 0xDD) ?: firstMarker(source, 0xCC) ?: return null
        val payloadLength = u16(source, marker + 2)
        if (payloadLength <= 2) return null
        return source.copyOf().also { copy ->
            val payloadOffset = marker + 4 + random.nextInt(payloadLength - 2)
            copy[payloadOffset] = (copy[payloadOffset].toInt() xor 0x7F).toByte()
        }
    }

    private fun headerSegments(bytes: ByteArray): List<HeaderSegment> {
        if (bytes.size < 4 || bytes[0] != 0xFF.toByte() || bytes[1] != 0xD8.toByte()) return emptyList()
        val segments = mutableListOf<HeaderSegment>()
        var offset = 2
        while (offset + 3 < bytes.size && bytes[offset] == 0xFF.toByte()) {
            val marker = bytes[offset + 1].toInt() and 0xFF
            if (marker == 0xDA || marker == 0xD9 || marker in 0xD0..0xD7) break
            val length = u16(bytes, offset + 2)
            val endExclusive = offset + 2 + length
            if (length < 2 || endExclusive > bytes.size) break
            segments += HeaderSegment(offset, endExclusive)
            offset = endExclusive
        }
        return segments
    }

    private fun firstMarker(bytes: ByteArray, expected: Int): Int? = bytes.indices.firstOrNull { offset ->
        offset + 3 < bytes.size && bytes[offset] == 0xFF.toByte() && (bytes[offset + 1].toInt() and 0xFF) == expected
    }

    private fun u16(bytes: ByteArray, offset: Int): Int =
        ((bytes[offset].toInt() and 0xFF) shl 8) or (bytes[offset + 1].toInt() and 0xFF)

    private data class Mutation(val name: String, val bytes: ByteArray)
    private data class HeaderSegment(val start: Int, val endExclusive: Int) {
        val length: Int get() = endExclusive - start
    }
}
