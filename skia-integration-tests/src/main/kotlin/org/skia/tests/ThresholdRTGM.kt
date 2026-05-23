package org.skia.tests

import org.graphiks.math.SkColor4f
import org.graphiks.math.SkISize
import org.graphiks.math.SkMatrix
import org.graphiks.math.SkRect
import org.skia.core.SkCanvas
import org.skia.effects.runtime.SkRuntimeEffect
import org.skia.effects.runtime.SkRuntimeEffectBuilder
import org.skia.effects.runtime.effects.SkBuiltinShaderEffectsChildren
import org.skia.foundation.SkBitmap
import org.skia.foundation.SkColorType
import org.skia.foundation.SkImage
import org.skia.foundation.SkPaint
import org.skia.foundation.SkSamplingOptions
import org.skia.tools.ToolUtils
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Port of Skia's `gm/runtimeshader.cpp::ThresholdRT` (`threshold_rt` GM,
 * 256 × 256, also a benchmark with animation flag).
 *
 * Draws four quadrants:
 *  - (0, 0)     : the blended result of two images via a smooth threshold.
 *  - (256, 0)   : the threshold map itself (greyscale blobs).
 *  - (0, 256)   : the "before" image (mandrill_256.png).
 *  - (256, 256) : the "after"  image (dog.jpg).
 *
 * The threshold map is generated procedurally — random radial blobs blurred
 * together into an alpha-8 surface — then the blend uses a smooth step at the
 * cutoff boundary. Cutoff animates in upstream; here we freeze it at 0.5
 * (mid-way), and slope = 10 (sharp transition).
 *
 * **Adaptation**: images are loaded via [ToolUtils.GetResourceAsImage].
 * If unavailable, synthetic solid-colour images are substituted so the
 * test can still execute.
 *
 * C++ original: `gm/runtimeshader.cpp:129-190`.
 */
public class ThresholdRTGM : GM() {

    override fun getName(): String = "threshold_rt"
    override fun getISize(): SkISize = SkISize.Make(512, 512)

    private val size = 256

    private val effect: SkRuntimeEffect by lazy {
        val res = SkRuntimeEffect.MakeForShader(SkBuiltinShaderEffectsChildren.THRESHOLD_RT_SKSL)
        requireNotNull(res.effect) { "ThresholdRTGM SkSL failed to compile: ${res.errorText}" }
    }

    /** Synthetic radial-blob threshold map (256 × 256 alpha-8 look-alike rendered as RGBA). */
    private fun makeThreshold(): org.skia.foundation.SkShader {
        val w = size
        val h = size
        val bmp = SkBitmap(w, h)
        // Place 25 random blobs (seeded, deterministic).
        var rngState = 0xDEADBEEFL
        fun nextF(): Float {
            rngState = rngState * 6364136223846793005L + 1442695040888963407L
            return ((rngState ushr 33) and 0xFFFFFFFFL).toFloat() / 0xFFFFFFFFL.toFloat()
        }
        val rad = 50f
        val sigma = 16f
        // Accumulate in a float buffer then blur with a simple Gaussian box approximation.
        val alphaF = FloatArray(w * h)
        repeat(25) {
            val cx = nextF() * w
            val cy = nextF() * h
            for (py in 0 until h) {
                for (px in 0 until w) {
                    val dx = px - cx
                    val dy = py - cy
                    val dist = sqrt(dx * dx + dy * dy)
                    val v = (1f - (dist / rad).coerceIn(0f, 1f))
                    alphaF[py * w + px] = (alphaF[py * w + px] + v).coerceAtMost(1f)
                }
            }
        }
        // Simple box-blur approximation for the Gaussian.
        val blurRadius = (sigma * 2).toInt().coerceAtLeast(1)
        val blurred = FloatArray(w * h)
        for (py in 0 until h) {
            for (px in 0 until w) {
                var sum = 0f
                var count = 0
                for (ky in -blurRadius..blurRadius) {
                    for (kx in -blurRadius..blurRadius) {
                        val nx = (px + kx).coerceIn(0, w - 1)
                        val ny = (py + ky).coerceIn(0, h - 1)
                        sum += alphaF[ny * w + nx]
                        count++
                    }
                }
                blurred[py * w + px] = sum / count
            }
        }
        // Write as greyscale RGBA.
        for (py in 0 until h) {
            for (px in 0 until w) {
                val a = (blurred[py * w + px] * 255f).toInt().coerceIn(0, 255)
                bmp.setPixel(px, py, (0xFF shl 24) or (a shl 16) or (a shl 8) or a)
            }
        }
        return bmp.asImage().makeShader(SkSamplingOptions.Default)
    }

    private fun makeImageShader(path: String, fallback: Int): org.skia.foundation.SkShader {
        val img = ToolUtils.GetResourceAsImage(path)
        return if (img != null) {
            val scale = SkMatrix.MakeScale(
                size.toFloat() / img.width.toFloat(),
                size.toFloat() / img.height.toFloat(),
            )
            img.makeShader(SkSamplingOptions.Default, scale)
        } else {
            // Solid-colour fallback.
            val bmp = SkBitmap(size, size)
            for (py in 0 until size) for (px in 0 until size) bmp.setPixel(px, py, fallback)
            bmp.asImage().makeShader(SkSamplingOptions.Default)
        }
    }

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        val thresholdShader = makeThreshold()
        val beforeShader = makeImageShader("images/mandrill_256.png", 0xFF8B4513.toInt())
        val afterShader = makeImageShader("images/dog.jpg", 0xFF4169E1.toInt())

        val builder = SkRuntimeEffectBuilder(effect)
        // Freeze at cutoff=0.5, slope=10.
        builder.uniform("cutoff").set(0.5f)
        builder.uniform("slope").set(10f)
        builder.child("before_map").set(beforeShader)
        builder.child("after_map").set(afterShader)
        builder.child("threshold_map").set(thresholdShader)

        val paint = SkPaint()
        paint.shader = builder.makeShader()
        c.drawRect(SkRect.MakeLTRB(0f, 0f, 256f, 256f), paint)

        // Debug quadrants: threshold / before / after.
        paint.shader = thresholdShader
        c.save()
        c.translate(256f, 0f)
        c.drawRect(SkRect.MakeLTRB(0f, 0f, 256f, 256f), paint)
        c.restore()

        paint.shader = beforeShader
        c.save()
        c.translate(0f, 256f)
        c.drawRect(SkRect.MakeLTRB(0f, 0f, 256f, 256f), paint)
        c.restore()

        paint.shader = afterShader
        c.save()
        c.translate(256f, 256f)
        c.drawRect(SkRect.MakeLTRB(0f, 0f, 256f, 256f), paint)
        c.restore()
    }
}
