package org.graphiks.kanvas.gpu.renderer.recording

import java.security.MessageDigest
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import org.graphiks.kanvas.gpu.renderer.passes.GPUDrawPacketID
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameBufferRef

class GPUPreparedTextDrawUniformPlanTest {
    @Test
    fun `public plan rejects a first slice whose offset is nonzero`() {
        val bytes = ByteArray(512)

        assertFailsWith<IllegalArgumentException> {
            plan(bytes = bytes, offsets = listOf(256L))
        }
    }

    @Test
    fun `public plan rejects an aligned hole between ordered slices`() {
        val bytes = ByteArray(768)

        assertFailsWith<IllegalArgumentException> {
            plan(bytes = bytes, offsets = listOf(0L, 512L))
        }
    }

    @Test
    fun `public plan rejects trailing bytes beyond the canonical slab size`() {
        val bytes = ByteArray(257)

        assertFailsWith<IllegalArgumentException> {
            plan(bytes = bytes, offsets = listOf(0L))
        }
    }

    @Test
    fun `public plan rejects nonzero bytes in trailing slice padding`() {
        val bytes = ByteArray(256).also { it[48] = 1 }

        assertFailsWith<IllegalArgumentException> {
            plan(bytes = bytes, offsets = listOf(0L))
        }
    }

    @Test
    fun `empty draw uniform plans remain refused by public and builder contracts`() {
        assertFailsWith<IllegalArgumentException> {
            GPUPreparedTextDrawUniformBufferPlan(
                bufferRef = GPUFrameBufferRef("buffer.prepared-text.draw-uniforms:empty"),
                alignmentBytes = 256L,
                logicalSliceSizeBytes = PREPARED_TEXT_DRAW_UNIFORM_LOGICAL_BYTES,
                byteSize = 0L,
                contentHash = sha256(ByteArray(0)),
                slices = emptyList(),
                uploadBytes = ByteArray(0),
            )
        }
        assertIs<GPUPreparedTextDrawUniformPlanResult.Refused>(
            buildPreparedTextDrawUniformBufferPlan(
                inputs = emptyList(),
                frameIdentity = "frame:empty",
                alignmentBytes = 256L,
                maxBufferSize = 1024L,
            ),
        )
    }

    private fun plan(
        bytes: ByteArray,
        offsets: List<Long>,
    ): GPUPreparedTextDrawUniformBufferPlan =
        GPUPreparedTextDrawUniformBufferPlan(
            bufferRef = GPUFrameBufferRef("buffer.prepared-text.draw-uniforms:test"),
            alignmentBytes = 256L,
            logicalSliceSizeBytes = PREPARED_TEXT_DRAW_UNIFORM_LOGICAL_BYTES,
            byteSize = bytes.size.toLong(),
            contentHash = sha256(bytes),
            slices = offsets.mapIndexed { index, offset ->
                GPUPreparedTextDrawUniformSlice(
                    packetId = GPUDrawPacketID("packet.draw-uniform.$index"),
                    offsetBytes = offset,
                    sizeBytes = PREPARED_TEXT_DRAW_UNIFORM_LOGICAL_BYTES,
                    contentHash = sha256(
                        bytes.copyOfRange(
                            offset.toInt(),
                            Math.addExact(
                                offset,
                                PREPARED_TEXT_DRAW_UNIFORM_LOGICAL_BYTES,
                            ).toInt(),
                        ),
                    ),
                )
            },
            uploadBytes = bytes,
        )

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
}
