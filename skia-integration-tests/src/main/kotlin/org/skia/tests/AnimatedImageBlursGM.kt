package org.skia.tests

import org.skia.core.SkCanvas
import org.graphiks.math.SkColorSetARGB
import org.skia.foundation.SkImageFilters
import org.skia.foundation.SkPaint
import org.skia.foundation.SkRRect
import org.graphiks.math.SkISize
import org.graphiks.math.SkRect
import org.skia.tools.SkRandom

/**
 * Port of upstream Skia's `gm/animatedimageblurs.cpp::AnimatedImageBlurs`
 * (the first of three GMs declared in that file ; the other two —
 * `AnimatedTiledImageBlur` and `AnimatedBackdropBlur` — already live as
 * separate ports / are deferred).
 *
 * The GM lays out 30 randomly-sized rounded rectangles, each drawn into
 * its own `saveLayer` whose paint carries an [SkImageFilters.Blur]
 * sigma. Despite the name there is **no animated image decoding** — the
 * "animated" qualifier refers to the per-frame blur sigma + position
 * animation in `onAnimate`. Since `fLastTime` is initialised to `0` and
 * `onAnimate` early-returns on the first call (`if (0.0f != fLastTime)`),
 * the first rendered frame uses the freshly-`init()`-ed node parameters
 * with `fBlur = fBlurOffset`. We snapshot exactly that t=0 frame, which
 * matches what the DM reference PNG (`animated-image-blurs_888.png`) was
 * rendered from.
 *
 * **Determinism.** [SkRandom] is the bit-compatible Skia LCG ;
 * seeded with `0` (default), the 30 calls to `init()` consume the same
 * sequence of `nextRangeF` values as upstream's
 * `SkRandom fRand` and therefore produce the same sizes / positions /
 * blur offsets at t=0.
 *
 * **Adaptations** :
 *  - No animation : we draw the t=0 snapshot only.
 */
public class AnimatedImageBlursGM : GM() {

    init {
        // Matches `AnimatedImageBlurs::AnimatedImageBlurs()`.
        setBGColor(SkColorSetARGB(0xFF, 0xCC, 0xCC, 0xCC))
    }

    override fun getName(): String = "animated-image-blurs"
    override fun getISize(): SkISize = SkISize.Make(kWidth.toInt(), kHeight.toInt())

    private val nodes: Array<Node> = Array(kNumNodes) { Node() }
    private val rand: SkRandom = SkRandom()

    override fun onOnceBeforeDraw() {
        for (i in 0 until kNumNodes) {
            nodes[i].init(rand)
        }
    }

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return

        val paint = SkPaint().apply { isAntiAlias = true }

        for (i in 0 until kNumNodes) {
            val n = nodes[i]
            val layerPaint = SkPaint().apply {
                imageFilter = SkImageFilters.Blur(n.sigma, n.sigma, null)
            }

            c.saveLayer(null, layerPaint)
            // The rect is outset to block the circle case (matches upstream).
            val rect = SkRect.MakeLTRB(
                n.posX - n.size - 0.5f,
                n.posY - n.size - 0.5f,
                n.posX + n.size + 0.5f,
                n.posY + n.size + 0.5f,
            )
            val rrect = SkRRect.MakeRectXY(rect, n.size, n.size)
            c.drawRRect(rrect, paint)
            c.restore()
        }
    }

    /**
     * Mirrors the inner `AnimatedImageBlurs::Node` struct. Only the
     * fields needed by the t=0 snapshot path are populated ; the
     * direction / speed bookkeeping is preserved to keep the
     * [SkRandom] consumption sequence in lock-step with upstream.
     */
    private class Node {
        var size: Float = 0f
        var posX: Float = 0f
        var posY: Float = 0f
        var dirX: Float = 1f
        var dirY: Float = 0f
        var blurOffset: Float = 0f
        var sigma: Float = 0f
        var speed: Float = 0f

        fun init(rand: SkRandom) {
            size = rand.nextRangeF(10f, 60f)
            posX = rand.nextRangeF(size, kWidth - size)
            posY = rand.nextRangeF(size, kHeight - size)
            dirX = rand.nextRangeF(-1f, 1f)
            dirY = kotlin.math.sqrt(1f - dirX * dirX)
            if (rand.nextBool()) {
                dirY = -dirY
            }
            blurOffset = rand.nextRangeF(0f, kBlurMax)
            sigma = blurOffset
            speed = rand.nextRangeF(20f, 60f)
        }
    }

    private companion object {
        const val kWidth: Float = 512f
        const val kHeight: Float = 512f
        const val kNumNodes: Int = 30
        const val kBlurMax: Float = 7f
    }
}
