package org.skia.tests

import org.skia.core.SkCanvas
import org.graphiks.math.SkISize

/**
 * Placeholder port of upstream Skia `gm/animcodecplayer_exif.cpp::AnimCodecPlayerExifGM`.
 *
 * Original draws every frame of an animated codec into a grid (with EXIF
 * orientation applied). Requires `SkAnimCodecPlayer` + `Codec` decode
 * pipeline that doesn't exist in `:kanvas-skia` yet.
 *
 * TODO: missing API — `SkAnimCodecPlayer`, `Codec::MakeFromData`,
 * frame-by-frame seek (`fPlayer->seek(duration)`), and per-frame
 * `getFrameInfo()` metadata. Flag-planting stub: empty draw, fixed size.
 */
public class AnimCodecPlayerExifGM(
    private val path: String = "images/orientation/Landscape_1.jpg",
) : GM() {
    override fun getName(): String =
        "AnimCodecPlayerExif_" + path.substringAfterLast('/')
    override fun getISize(): SkISize = SkISize.Make(100, 100)

    override fun onDraw(canvas: SkCanvas?) {
        // TODO: missing API — SkAnimCodecPlayer + Codec frame seek pipeline.
    }
}
