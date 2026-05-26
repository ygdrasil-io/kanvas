package org.skia.pipeline

import kotlin.math.max
import kotlin.math.min

data class PixelBuffer(val width: Int, val height: Int, val argb8888: IntArray)

enum class CpuVectorMode {
    Auto,
    Disabled,
    Force,
}

data class CpuPipelineExecutionOptions(val vectorMode: CpuVectorMode = CpuVectorMode.Auto)

sealed interface CpuExecutionResult {
    data class Success(
        val pixels: PixelBuffer,
        val kernelId: String = "cpu.scalar",
        val diagnostics: List<String> = emptyList(),
    ) : CpuExecutionResult

    data class LegacyFallback(val reason: String) : CpuExecutionResult
}

object CpuScalarPipelineExecutor {
    fun execute(
        ir: KanvasPipelineIR,
        width: Int,
        height: Int,
        options: CpuPipelineExecutionOptions = CpuPipelineExecutionOptions(),
    ): CpuExecutionResult {
        if (width <= 0 || height <= 0) {
            return CpuExecutionResult.LegacyFallback("Invalid target dimensions")
        }
        ir.fallbackPlan?.let { return CpuExecutionResult.LegacyFallback("Explicit fallback: ${it.reason}") }

        val colorOp = ir.ops.firstOrNull { it is PipelineOp.ConstantColor } as? PipelineOp.ConstantColor
        val gradientOp = ir.ops.firstOrNull { it is PipelineOp.LinearGradient } as? PipelineOp.LinearGradient
        val blend = ir.ops.firstOrNull { it is PipelineOp.BlendMode } as? PipelineOp.BlendMode
        if (blend?.mode != "SrcOver") {
            return CpuExecutionResult.LegacyFallback("Unsupported blend mode for scalar pilot: ${blend?.mode}")
        }
        if (colorOp == null && gradientOp == null) {
            return CpuExecutionResult.LegacyFallback("Unsupported shader family for scalar pilot")
        }

        val pixels = IntArray(width * height)
        if (colorOp != null) {
            val packed = pack(colorOp.color)
            val vectorAttempt = CpuVectorSolidRectKernel.tryFillSrcOverClear(width, height, packed, pixels, options.vectorMode)
            if (vectorAttempt.usedVector) {
                return CpuExecutionResult.Success(
                    pixels = PixelBuffer(width = width, height = height, argb8888 = pixels),
                    kernelId = vectorAttempt.kernelId,
                    diagnostics = vectorAttempt.diagnostics,
                )
            }
            fillSolidSrcOverClearScalar(pixels, packed)
            return CpuExecutionResult.Success(
                pixels = PixelBuffer(width = width, height = height, argb8888 = pixels),
                kernelId = "cpu.scalar.solid_src_over_clear",
                diagnostics = vectorAttempt.diagnostics,
            )
        }
        for (y in 0 until height) {
            for (x in 0 until width) {
                val p = requireNotNull(gradientOp).payload
                val src = pack(sampleLinearGradient(p, x.toFloat(), y.toFloat()))
                pixels[y * width + x] = srcOver(src, 0x00000000)
            }
        }
        return CpuExecutionResult.Success(
            pixels = PixelBuffer(width = width, height = height, argb8888 = pixels),
            kernelId = "cpu.scalar.linear_gradient_src_over_clear",
        )
    }

    fun legacySolidRect(width: Int, height: Int, color: Rgba): PixelBuffer {
        val packed = pack(color)
        return PixelBuffer(width, height, IntArray(width * height) { srcOver(packed, 0x00000000) })
    }

    fun legacyLinearGradientRect(width: Int, height: Int, payload: LinearGradientPayload): PixelBuffer {
        val pixels = IntArray(width * height)
        for (y in 0 until height) {
            for (x in 0 until width) {
                pixels[y * width + x] = srcOver(pack(sampleLinearGradient(payload, x.toFloat(), y.toFloat())), 0x00000000)
            }
        }
        return PixelBuffer(width, height, pixels)
    }

    private fun sampleLinearGradient(payload: LinearGradientPayload, x: Float, y: Float): Rgba {
        val dx = payload.end.x - payload.start.x
        val dy = payload.end.y - payload.start.y
        val denom = dx * dx + dy * dy
        if (denom <= 1e-8f) return payload.startColor
        val t = (((x - payload.start.x) * dx) + ((y - payload.start.y) * dy)) / denom
        val clamped = t.coerceIn(0f, 1f)
        return lerp(payload.startColor, payload.endColor, clamped)
    }

    private fun lerp(a: Rgba, b: Rgba, t: Float): Rgba {
        val s = 1f - t
        return Rgba(
            r = a.r * s + b.r * t,
            g = a.g * s + b.g * t,
            b = a.b * s + b.b * t,
            a = a.a * s + b.a * t,
        )
    }

    private fun pack(c: Rgba): Int {
        fun q(v: Float): Int = (min(1f, max(0f, v)) * 255f + 0.5f).toInt()
        val a = q(c.a)
        val r = q(c.r)
        val g = q(c.g)
        val b = q(c.b)
        return (a shl 24) or (r shl 16) or (g shl 8) or b
    }

    private fun fillSolidSrcOverClearScalar(dst: IntArray, src: Int) {
        for (i in dst.indices) {
            dst[i] = srcOver(src, 0x00000000)
        }
    }

    private fun srcOver(src: Int, dst: Int): Int {
        val sa = (src ushr 24) and 0xFF
        val sr = (src ushr 16) and 0xFF
        val sg = (src ushr 8) and 0xFF
        val sb = src and 0xFF
        val da = (dst ushr 24) and 0xFF
        val dr = (dst ushr 16) and 0xFF
        val dg = (dst ushr 8) and 0xFF
        val db = dst and 0xFF
        val invSa = 255 - sa
        val outA = sa + ((da * invSa + 127) / 255)
        val outR = sr + ((dr * invSa + 127) / 255)
        val outG = sg + ((dg * invSa + 127) / 255)
        val outB = sb + ((db * invSa + 127) / 255)
        return (outA shl 24) or (outR shl 16) or (outG shl 8) or outB
    }
}
