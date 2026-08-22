package org.graphiks.kanvas.gpu.renderer.payloads

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class GPURegisteredUniformPayloadTest {
    @Test
    fun `linear gradient owns the 64 byte product ABI`() {
        val payload = GPURegisteredUniformPayloads.linearGradient(
            startX = 1f,
            startY = 2f,
            endX = 9f,
            endY = 10f,
            start = GPUUniformColor(1f, 0f, 0f, 1f),
            end = GPUUniformColor(0f, 0f, 1f, 1f),
        )

        assertEquals(GPURegisteredUniformProgram.LinearGradient, payload.program)
        assertEquals(64, payload.bytes.size)
        assertContentEquals(
            floatArrayOf(1f, 2f, 0f, 0f, 9f, 10f, 0f, 0f),
            payload.bytes.asLittleEndianFloats().copyOfRange(0, 8),
        )
    }

    @Test
    fun `all native programs expose their exact ABI sizes`() {
        val red = GPUUniformColor(1f, 0f, 0f, 1f)
        val blue = GPUUniformColor(0f, 0f, 1f, 1f)
        val payloads = listOf(
            GPURegisteredUniformPayloads.solidColor(red),
            GPURegisteredUniformPayloads.linearGradient(0f, 1f, 2f, 3f, red, blue),
            GPURegisteredUniformPayloads.radialGradient(4f, 5f, 6f, red, blue),
            GPURegisteredUniformPayloads.sweepGradient(4f, 5f, -1f, 2f, red, blue),
            GPURegisteredUniformPayloads.colorMatrix(red, List(20) { it.toFloat() }),
            GPURegisteredUniformPayloads.simpleRuntimeEffect(blue),
        )

        assertEquals(
            listOf(
                GPURegisteredUniformProgram.SolidColor,
                GPURegisteredUniformProgram.LinearGradient,
                GPURegisteredUniformProgram.RadialGradient,
                GPURegisteredUniformProgram.SweepGradient,
                GPURegisteredUniformProgram.ColorMatrix,
                GPURegisteredUniformProgram.SimpleRuntimeEffect,
            ),
            payloads.map { it.program },
        )
        assertEquals(payloads.map { it.program.uniformByteSize }, payloads.map { it.bytes.size })
    }

    @Test
    fun `radial and sweep payloads encode typed fields in little endian order`() {
        val color = GPUUniformColor(0.1f, 0.2f, 0.3f, 0.4f)
        val radial = GPURegisteredUniformPayloads.radialGradient(1f, 2f, 3f, color, color)
        val sweep = GPURegisteredUniformPayloads.sweepGradient(4f, 5f, 6f, 7f, color, color)

        assertContentEquals(
            floatArrayOf(1f, 2f, 3f, 0f, 0.1f, 0.2f, 0.3f, 0.4f),
            radial.bytes.asLittleEndianFloats().copyOfRange(0, 8),
        )
        assertContentEquals(
            floatArrayOf(4f, 5f, 0f, 0f, 6f, 7f, 0f, 0f),
            sweep.bytes.asLittleEndianFloats().copyOfRange(0, 8),
        )
    }

    @Test
    fun `payload bytes are defensively owned`() {
        val payload = GPURegisteredUniformPayloads.solidColor(GPUUniformColor(1f, 0f, 0f, 1f))
        val bytes = payload.bytes
        bytes[0] = 0

        assertEquals(1f, payload.bytes.asLittleEndianFloats().first())
    }

    @Test
    fun `colors and numeric fields require finite normalized values`() {
        assertFailsWith<IllegalArgumentException> { GPUUniformColor(Float.NaN, 0f, 0f, 1f) }
        assertFailsWith<IllegalArgumentException> { GPUUniformColor(1.1f, 0f, 0f, 1f) }
        assertFailsWith<IllegalArgumentException> {
            GPURegisteredUniformPayloads.linearGradient(Float.POSITIVE_INFINITY, 0f, 1f, 1f, color(), color())
        }
        assertFailsWith<IllegalArgumentException> {
            GPURegisteredUniformPayloads.radialGradient(0f, 0f, 0f, color(), color())
        }
        assertFailsWith<IllegalArgumentException> {
            GPURegisteredUniformPayloads.sweepGradient(0f, 0f, 0f, Float.NaN, color(), color())
        }
    }

    @Test
    fun `color matrix requires exactly 20 finite coefficients`() {
        assertFailsWith<IllegalArgumentException> {
            GPURegisteredUniformPayloads.colorMatrix(color(), List(19) { 0f })
        }
        assertFailsWith<IllegalArgumentException> {
            GPURegisteredUniformPayloads.colorMatrix(color(), List(20) { if (it == 19) Float.NaN else 0f })
        }
        val payload = GPURegisteredUniformPayloads.colorMatrix(color(), List(20) { it.toFloat() })
        assertEquals(96, payload.bytes.size)
        assertContentEquals(
            floatArrayOf(0.2f, 0.3f, 0.4f, 0.5f, 0f, 1f, 2f, 3f),
            payload.bytes.asLittleEndianFloats().copyOfRange(0, 8),
        )
    }

    private fun color() = GPUUniformColor(0.2f, 0.3f, 0.4f, 0.5f)
}

private fun ByteArray.asLittleEndianFloats(): FloatArray {
    val buffer = ByteBuffer.wrap(this).order(ByteOrder.LITTLE_ENDIAN)
    return FloatArray(size / Float.SIZE_BYTES) { buffer.float }
}
