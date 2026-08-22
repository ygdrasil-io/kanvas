package org.graphiks.kanvas.gpu.renderer.payloads

import java.nio.ByteBuffer
import java.nio.ByteOrder

/** One normalized RGBA color owned by a registered-uniform payload factory. */
data class GPUUniformColor(
    val red: Float,
    val green: Float,
    val blue: Float,
    val alpha: Float,
) {
    init {
        require(listOf(red, green, blue, alpha).all { it.isFinite() && it in 0f..1f }) {
            "Registered uniform colors must be finite and normalized to [0, 1]."
        }
    }
}

/** Product-owned bytes for one closed native registered-uniform program ABI. */
class GPURegisteredUniformPayload private constructor(
    val program: GPURegisteredUniformProgram,
    bytes: ByteArray,
) {
    private val ownedBytes = bytes.copyOf()

    val bytes: ByteArray
        get() = ownedBytes.copyOf()

    init {
        require(ownedBytes.size == program.uniformByteSize) {
            "${program.wireId} requires ${program.uniformByteSize} uniform bytes."
        }
    }

    companion object {
        internal fun create(
            program: GPURegisteredUniformProgram,
            bytes: ByteArray,
        ): GPURegisteredUniformPayload = GPURegisteredUniformPayload(program, bytes)
    }
}

/** Typed constructors for the six native registered-uniform program ABIs. */
object GPURegisteredUniformPayloads {
    fun solidColor(color: GPUUniformColor): GPURegisteredUniformPayload =
        payload(GPURegisteredUniformProgram.SolidColor) { putColor(color) }

    fun linearGradient(
        startX: Float,
        startY: Float,
        endX: Float,
        endY: Float,
        start: GPUUniformColor,
        end: GPUUniformColor,
    ): GPURegisteredUniformPayload {
        requireFinite("startX", startX)
        requireFinite("startY", startY)
        requireFinite("endX", endX)
        requireFinite("endY", endY)
        return payload(GPURegisteredUniformProgram.LinearGradient) {
            putFloat4(startX, startY, 0f, 0f)
            putFloat4(endX, endY, 0f, 0f)
            putColor(start)
            putColor(end)
        }
    }

    fun radialGradient(
        centerX: Float,
        centerY: Float,
        radius: Float,
        start: GPUUniformColor,
        end: GPUUniformColor,
    ): GPURegisteredUniformPayload {
        requireFinite("centerX", centerX)
        requireFinite("centerY", centerY)
        require(radius.isFinite() && radius > 0f) { "Radial gradient radius must be finite and positive." }
        return payload(GPURegisteredUniformProgram.RadialGradient) {
            putFloat4(centerX, centerY, radius, 0f)
            putColor(start)
            putColor(end)
        }
    }

    fun sweepGradient(
        centerX: Float,
        centerY: Float,
        startAngle: Float,
        endAngle: Float,
        start: GPUUniformColor,
        end: GPUUniformColor,
    ): GPURegisteredUniformPayload {
        requireFinite("centerX", centerX)
        requireFinite("centerY", centerY)
        requireFinite("startAngle", startAngle)
        requireFinite("endAngle", endAngle)
        return payload(GPURegisteredUniformProgram.SweepGradient) {
            putFloat4(centerX, centerY, 0f, 0f)
            putFloat4(startAngle, endAngle, 0f, 0f)
            putColor(start)
            putColor(end)
        }
    }

    fun colorMatrix(
        inputColor: GPUUniformColor,
        coefficients: List<Float>,
    ): GPURegisteredUniformPayload {
        require(coefficients.size == 20) { "ColorMatrix requires exactly 20 coefficients." }
        require(coefficients.all { it.isFinite() }) { "ColorMatrix coefficients must be finite." }
        return payload(GPURegisteredUniformProgram.ColorMatrix) {
            putColor(inputColor)
            coefficients.forEach(::putFloat)
        }
    }

    fun simpleRuntimeEffect(color: GPUUniformColor): GPURegisteredUniformPayload =
        payload(GPURegisteredUniformProgram.SimpleRuntimeEffect) { putColor(color) }

    private fun payload(
        program: GPURegisteredUniformProgram,
        write: ByteBuffer.() -> Unit,
    ): GPURegisteredUniformPayload {
        val buffer = ByteBuffer.allocate(program.uniformByteSize).order(ByteOrder.LITTLE_ENDIAN)
        buffer.write()
        return GPURegisteredUniformPayload.create(program, buffer.array())
    }

    private fun ByteBuffer.putColor(color: GPUUniformColor) {
        putFloat4(color.red, color.green, color.blue, color.alpha)
    }

    private fun ByteBuffer.putFloat4(a: Float, b: Float, c: Float, d: Float) {
        putFloat(a)
        putFloat(b)
        putFloat(c)
        putFloat(d)
    }

    private fun requireFinite(label: String, value: Float) {
        require(value.isFinite()) { "$label must be finite." }
    }
}
