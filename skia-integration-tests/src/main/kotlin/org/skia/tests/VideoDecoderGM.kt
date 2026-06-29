package org.skia.tests

import org.graphiks.kanvas.codec.SkVideoDecoder
import org.skia.core.SkCanvas
import org.skia.foundation.stream.SkMemoryStream
import org.graphiks.math.SkISize

/**
 * R-final.S — **STUB.FFMPEG** consumer GM. Iso-aligned port of
 * upstream's `gm/video_decoder.cpp` (which streams successive frames
 * out of a local `.webm` / `.mp4` resource via FFmpeg-backed
 * [SkVideoDecoder] and stitches them into a single grid).
 *
 * The body is a one-liner that touches [SkVideoDecoder.MakeFromStream]
 * so the surface stays compile-pinned. [VideoDecoderTest] is
 * `@Disabled` because the dispatch throws `STUB.FFMPEG`.
 */
public class VideoDecoderGM : GM() {

    override fun getName(): String = "video-decoder"
    override fun getISize(): SkISize = SkISize.Make(400, 300)

    override fun onDraw(canvas: SkCanvas?) {
        // Touch the stubbed dispatch — throws STUB.FFMPEG at runtime.
        SkVideoDecoder.MakeFromStream(SkMemoryStream(ByteArray(0)))
    }
}
