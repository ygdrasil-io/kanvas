package org.skia.dm

import org.skia.core.SkCanvas
import org.skia.core.SkPicture
import org.skia.core.SkPictureRecorder
import org.skia.math.SkColor
import org.skia.math.SkISize
import org.skia.math.SkRect
import org.skia.tests.GM

/**
 * "Via" sink mirroring Skia DM's `dm/DMSrcSink.cpp::ViaPicture`.
 *
 * Records the source GM into an [SkPicture] via [SkPictureRecorder],
 * then dispatches to a [backingSink] (defaults to [RasterSinkF16])
 * with a small adapter [GM] that simply replays the recorded picture
 * on each `onDraw`. This validates the entire `record → SkRecord →
 * playback` pipeline end-to-end on every GM consumed by the runner —
 * a faithful picture playback should produce the **same pixels** as
 * a direct render through the same backing sink.
 *
 * Tag is composed as `"pic-<backingTag>"` so reports diff cleanly
 * against upstream's `--config pic-8888` / `--config pic-f16`.
 *
 * @param backingSink the raster sink the recorded picture replays
 *   into. Defaults to [RasterSinkF16] so a `PictureSink()` call lands
 *   in the canonical `pic-f16` configuration.
 */
public class PictureSink(
    private val backingSink: Sink = RasterSinkF16(),
) : Sink {

    override val tag: String = "pic-${backingSink.tag}"

    override fun draw(src: GM): Sink.Result {
        // Phase 1 — record the source GM into a picture. A crash here
        // means recording itself is broken (rare ; usually a missing
        // override on `SkRecordingCanvas`). Wrap as `Result.Error` with
        // the recording-side tag so the failure is locatable.
        val picture: SkPicture = try {
            val size = src.size()
            val recorder = SkPictureRecorder()
            val recCanvas = recorder.beginRecording(
                SkRect.MakeWH(size.width.toFloat(), size.height.toFloat()),
            )
            src.draw(recCanvas)
            recorder.finishRecordingAsPicture()
        } catch (e: Throwable) {
            return Sink.Result.Error(
                "PictureSink[${src.name()}] (recording): ${e.message ?: e::class.simpleName}",
            )
        }

        // Phase 2 — adapt the picture as a GM and defer to the backing
        // sink. Any draw-time exception is wrapped by the backing sink
        // itself (its tag will appear in the message), so the chain
        // stays diagnosable.
        val playbackSrc = PlaybackGM(
            gmName = src.name(),
            gmSize = src.size(),
            gmBgColor = src.bgColor(),
            picture = picture,
        )
        return backingSink.draw(playbackSrc)
    }

    /**
     * Adapter GM that replays a pre-recorded [SkPicture] in its
     * [onDraw]. Carries the source GM's name / size / bg color so the
     * backing sink allocates a bitmap of the right shape and erases
     * to the right colour before playback.
     */
    private class PlaybackGM(
        private val gmName: String,
        private val gmSize: SkISize,
        gmBgColor: SkColor,
        private val picture: SkPicture,
    ) : GM() {
        init { setBGColor(gmBgColor) }
        override fun getName(): String = gmName
        override fun getISize(): SkISize = gmSize
        override fun onDraw(canvas: SkCanvas?) {
            picture.playback(canvas ?: return)
        }
    }
}
