package org.graphiks.kanvas.skia.gm.image

import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm

/**
 * Port of Skia's `gm/video_decoder.cpp` (400x300).
 *
 * STUB: Requires `VideoDecoder.MakeFromStream` with FFmpeg-backed video decoding.
 * The VideoDecoder dispatch throws STUB.FFMPEG at runtime.
 * @see https://github.com/google/skia/blob/main/gm/video_decoder.cpp
 */
class VideoDecoderGm : SkiaGm {
    override val name = "videodecoder"
    override val renderFamily = RenderFamily.IMAGE
    override val renderCost = RenderCost.BLOCKING
    override val minSimilarity = 0.0
    override val width = 400
    override val height = 300

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        // STUB: VideoDecoder requires FFmpeg
    }
}
