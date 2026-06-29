package org.skia.tests

import org.graphiks.kanvas.codec.SkCodec
import org.skia.core.SkCanvas
import org.skia.foundation.SkBitmap
import org.skia.foundation.SkImage
import org.skia.foundation.SkImageInfo
import org.graphiks.math.SkISize

/**
 * Port of Skia's
 * [`gm/animated_gif.cpp`](https://github.com/google/skia/blob/main/gm/animated_gif.cpp)
 * `animatedGif` GM.
 *
 * Decodes the multi-frame GIF at `images/test640x479.gif` (the upstream
 * default for `--animatedGif`) and lays out one tile per frame across
 * the top row. The bottom row repeats the first frame in the upstream
 * "live animation" slot — the GM's `onAnimate` driver isn't wired into
 * `:kanvas-skia`'s test harness, so we statically pick frame 0 there.
 *
 * **Surface tested.** This GM is the canonical consumer for the
 * R-final.5 `SkCodec` multi-frame surface :
 *  - [SkCodec.getFrameCount] — returns the number of decoded frames.
 *  - [SkCodec.getFrameInfo] — per-frame `requiredFrame` / `durationMs`.
 *  - [SkCodec.getPixels] with [SkCodec.Options]`(frameIndex, priorFrame)`.
 *
 * The GM honours the upstream `priorFrame` optimisation : if the
 * required frame's pixels are already on-hand (cached in `frames[]`),
 * we feed them through [SkCodec.Options.priorFrame] so the codec
 * shortcuts the dependency-chain reconstruction. kanvas-skia's
 * The GIF codec pre-composes every frame at construction time, so the
 * hint is informational on this back-end — but the upstream-faithful
 * call site keeps the GM portable across codec implementations.
 *
 * Reference image: `animatedGif.png`, 2560 × 958.
 */
public class AnimatedGifGM : GM() {

    private var codec: SkCodec? = null
    private var frameInfos: List<SkCodec.FrameInfo> = emptyList()
    private val frames: MutableList<SkBitmap?> = mutableListOf()

    override fun getName(): String = "animatedGif"

    override fun getISize(): SkISize {
        if (initCodec()) {
            val info = codec!!.getInfo()
            // Wide enough for one tile per frame.
            val w = info.width * frameInfos.size
            // Tall enough to show the row of frames + an animating
            // version (we draw frame 0 in this slot — see [onDraw]).
            val h = info.height * 2
            return SkISize.Make(w, h)
        }
        return SkISize.Make(640, 480)
    }

    private fun initCodec(): Boolean {
        if (codec != null) return true
        val data = org.skia.tools.ToolUtils.GetResourceAsData("images/test640x479.gif")
            ?: return false
        codec = SkCodec.MakeFromData(data.toByteArray()) ?: return false
        frameInfos = codec!!.getFrameInfo()
        // Initialise the per-frame bitmap cache.
        repeat(frameInfos.size) { frames += null }
        return true
    }

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        if (!initCodec()) return
        val cd = codec!!

        c.save()
        for (frameIndex in frameInfos.indices) {
            drawFrame(c, frameIndex)
            c.translate(cd.getInfo().width.toFloat(), 0f)
        }
        c.restore()

        // Bottom row : the upstream `onAnimate` driver picks the frame
        // that matches the elapsed time. The kanvas-skia harness has no
        // animation clock, so we draw frame 0 to deterministically fill
        // the slot (matches the GM's first-render output before any
        // animation tick).
        c.save()
        c.translate(0f, cd.getInfo().height.toFloat())
        drawFrame(c, 0)
        c.restore()
    }

    private fun drawFrame(canvas: SkCanvas, frameIndex: Int) {
        val cd = codec ?: return
        if (frameIndex >= frames.size) return
        var bm = frames[frameIndex]
        if (bm == null) {
            val info: SkImageInfo = cd.getInfo()
            bm = SkBitmap(info.width, info.height, info.colorSpace, info.colorType)

            var priorFrame = SkCodec.kNoFrame
            val required = frameInfos[frameIndex].requiredFrame
            if (required != SkCodec.kNoFrame) {
                val requiredBm = frames.getOrNull(required)
                if (requiredBm != null) {
                    // Seed the destination with the required frame's
                    // pixels — kanvas-skia's GIF codec pre-composes
                    // frames so this is informational, but the upstream
                    // contract is honoured.
                    System.arraycopy(requiredBm.pixels, 0, bm.pixels, 0, requiredBm.pixels.size)
                    priorFrame = required
                }
            }

            val opts = SkCodec.Options(frameIndex = frameIndex, priorFrame = priorFrame)
            val res = cd.getPixels(info, bm, opts)
            if (res != SkCodec.Result.kSuccess) {
                return
            }
            frames[frameIndex] = bm
        }
        canvas.drawImage(SkImage.Make(bm), 0f, 0f)
    }
}
