package org.graphiks.kanvas.gpu.renderer.scenes.offscreen

import org.graphiks.kanvas.gpu.renderer.scenes.commands.SceneColor
import java.nio.ByteBuffer
import java.nio.ByteOrder

object UniformPacker {

    fun solidColorBytes(color: SceneColor): ByteArray {
        val buf = ByteBuffer.allocate(16).order(ByteOrder.LITTLE_ENDIAN)
        buf.putFloat(color.r)
        buf.putFloat(color.g)
        buf.putFloat(color.b)
        buf.putFloat(color.a)
        return buf.array()
    }

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
