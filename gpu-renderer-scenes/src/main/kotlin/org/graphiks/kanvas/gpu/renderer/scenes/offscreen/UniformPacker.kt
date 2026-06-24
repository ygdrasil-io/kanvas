package org.graphiks.kanvas.gpu.renderer.scenes.offscreen

import org.graphiks.kanvas.gpu.renderer.scenes.commands.SceneColor
import org.graphiks.kanvas.gpu.renderer.wgsl.BitmapShaderClampEntryPoint
import org.graphiks.kanvas.gpu.renderer.wgsl.BitmapShaderSnippetSourceHash
import org.graphiks.kanvas.gpu.renderer.wgsl.SimpleRTEntryPoint
import org.graphiks.kanvas.gpu.renderer.wgsl.SimpleRTSourceHash
import java.nio.ByteBuffer
import java.nio.ByteOrder

object UniformPacker {

    /** Snippet identity wired into the bitmap uniform ABI (BitmapShaderSnippet, M17). */
    const val bitmapSnippetSourceHash: String = BitmapShaderSnippetSourceHash
    const val bitmapSnippetEntryPoint: String = BitmapShaderClampEntryPoint

    /** Snippet identity wired into the runtime-effect uniform ABI (SimpleRTWgsl, M21). */
    const val simpleRtSnippetSourceHash: String = SimpleRTSourceHash
    const val simpleRtSnippetEntryPoint: String = SimpleRTEntryPoint

    fun solidColorBytes(color: SceneColor): ByteArray {
        val buf = ByteBuffer.allocate(16).order(ByteOrder.LITTLE_ENDIAN)
        buf.putFloat(color.r)
        buf.putFloat(color.g)
        buf.putFloat(color.b)
        buf.putFloat(color.a)
        return buf.array()
    }

    /**
     * Packs the bitmap sampling uniform for the [BitmapShaderClampEntryPoint] ABI of
     * [BitmapShaderSnippetSourceHash]. Procedural texture data stays in the renderer wrapper
     * until M26 delivers real decoded textures; this packs the color/tint uniform only.
     */
    fun bitmapBytes(color: SceneColor): ByteArray = solidColorBytes(color)

    /**
     * Packs the SimpleRT `gColor` uniform (vec4f@0:16) for the [SimpleRTEntryPoint] ABI of
     * [SimpleRTSourceHash]. This is the real registered runtime-effect descriptor uniform.
     */
    fun simpleRtBytes(gColor: SceneColor): ByteArray = solidColorBytes(gColor)

    fun linearGradientBytes(
        startX: Float, startY: Float, endX: Float, endY: Float,
        startColor: SceneColor, endColor: SceneColor,
    ): ByteArray {
        val buf = ByteBuffer.allocate(64).order(ByteOrder.LITTLE_ENDIAN)
        buf.putFloat(startX); buf.putFloat(startY); buf.putFloat(0f); buf.putFloat(0f)
        buf.putFloat(endX); buf.putFloat(endY); buf.putFloat(0f); buf.putFloat(0f)
        buf.putFloat(startColor.r); buf.putFloat(startColor.g); buf.putFloat(startColor.b); buf.putFloat(startColor.a)
        buf.putFloat(endColor.r); buf.putFloat(endColor.g); buf.putFloat(endColor.b); buf.putFloat(endColor.a)
        return buf.array()
    }

    fun radialGradientBytes(
        centerX: Float, centerY: Float, radius: Float,
        startColor: SceneColor, endColor: SceneColor,
    ): ByteArray {
        val buf = ByteBuffer.allocate(48).order(ByteOrder.LITTLE_ENDIAN)
        buf.putFloat(centerX); buf.putFloat(centerY); buf.putFloat(radius); buf.putFloat(0f)
        buf.putFloat(startColor.r); buf.putFloat(startColor.g); buf.putFloat(startColor.b); buf.putFloat(startColor.a)
        buf.putFloat(endColor.r); buf.putFloat(endColor.g); buf.putFloat(endColor.b); buf.putFloat(endColor.a)
        return buf.array()
    }

    fun sweepGradientBytes(
        centerX: Float, centerY: Float, startAngle: Float, endAngle: Float,
        startColor: SceneColor, endColor: SceneColor,
    ): ByteArray {
        val buf = ByteBuffer.allocate(64).order(ByteOrder.LITTLE_ENDIAN)
        buf.putFloat(centerX); buf.putFloat(centerY); buf.putFloat(0f); buf.putFloat(0f)
        buf.putFloat(startAngle); buf.putFloat(endAngle); buf.putFloat(0f); buf.putFloat(0f)
        buf.putFloat(startColor.r); buf.putFloat(startColor.g); buf.putFloat(startColor.b); buf.putFloat(startColor.a)
        buf.putFloat(endColor.r); buf.putFloat(endColor.g); buf.putFloat(endColor.b); buf.putFloat(endColor.a)
        return buf.array()
    }

    fun blurBytes(color: SceneColor, centerX: Float, centerY: Float, radius: Float): ByteArray {
        val buf = ByteBuffer.allocate(48).order(ByteOrder.LITTLE_ENDIAN)
        buf.putFloat(color.r); buf.putFloat(color.g); buf.putFloat(color.b); buf.putFloat(color.a)
        buf.putFloat(centerX); buf.putFloat(centerY); buf.putFloat(0f); buf.putFloat(0f)
        buf.putFloat(radius); buf.putFloat(0f); buf.putFloat(0f); buf.putFloat(0f)
        return buf.array()
    }

    fun colorMatrixBytes(color: SceneColor, kind: Int): ByteArray {
        val buf = ByteBuffer.allocate(96).order(ByteOrder.LITTLE_ENDIAN)
        buf.putFloat(color.r); buf.putFloat(color.g); buf.putFloat(color.b); buf.putFloat(color.a)
        when (kind) {
            1 -> {
                buf.putFloat(0.3f); buf.putFloat(0.3f); buf.putFloat(0.3f); buf.putFloat(0f)
                buf.putFloat(0.6f); buf.putFloat(0.6f); buf.putFloat(0.6f); buf.putFloat(0f)
                buf.putFloat(0.1f); buf.putFloat(0.1f); buf.putFloat(0.1f); buf.putFloat(0f)
                buf.putFloat(0f); buf.putFloat(0f); buf.putFloat(0f); buf.putFloat(1f)
                buf.putFloat(0f); buf.putFloat(0f); buf.putFloat(0f); buf.putFloat(0f)
            }
            2 -> {
                buf.putFloat(0.213f); buf.putFloat(0.715f); buf.putFloat(0.072f); buf.putFloat(0f)
                buf.putFloat(0.213f); buf.putFloat(0.715f); buf.putFloat(0.072f); buf.putFloat(0f)
                buf.putFloat(0.213f); buf.putFloat(0.715f); buf.putFloat(0.072f); buf.putFloat(0f)
                buf.putFloat(0f); buf.putFloat(0f); buf.putFloat(0f); buf.putFloat(1f)
                buf.putFloat(0f); buf.putFloat(0f); buf.putFloat(0f); buf.putFloat(0f)
            }
            else -> {
                buf.putFloat(1f); buf.putFloat(0f); buf.putFloat(0f); buf.putFloat(0f)
                buf.putFloat(0f); buf.putFloat(1f); buf.putFloat(0f); buf.putFloat(0f)
                buf.putFloat(0f); buf.putFloat(0f); buf.putFloat(1f); buf.putFloat(0f)
                buf.putFloat(0f); buf.putFloat(0f); buf.putFloat(0f); buf.putFloat(1f)
                buf.putFloat(0f); buf.putFloat(0f); buf.putFloat(0f); buf.putFloat(0f)
            }
        }
        return buf.array()
    }

    fun strokeBytes(color: SceneColor, capJoin: Int, centerX: Float, centerY: Float, halfW: Float, halfH: Float): ByteArray {
        val buf = ByteBuffer.allocate(48).order(ByteOrder.LITTLE_ENDIAN)
        buf.putFloat(color.r); buf.putFloat(color.g); buf.putFloat(color.b); buf.putFloat(color.a)
        buf.putFloat(capJoin.toFloat()); buf.putFloat(4f); buf.putFloat(centerX); buf.putFloat(centerY)
        buf.putFloat(8f); buf.putFloat(4f); buf.putFloat(0f); buf.putFloat(0f)
        return buf.array()
    }
}
