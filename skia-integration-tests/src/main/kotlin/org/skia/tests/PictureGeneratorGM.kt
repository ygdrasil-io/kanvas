package org.skia.tests

import org.skia.core.SkCanvas
import org.graphiks.math.SkISize

/**
 * Placeholder for Skia's `gm/pictureimagegenerator.cpp::PictureGeneratorGM`
 * (1160 × 860).
 *
 * Upstream exercises `SkImageGenerators::MakeFromPicture(...)` — feeds an
 * `SkPicture` (containing a stylised "SKIA" vector logo rendered through
 * gradients + text + paths) into the image-generator pipeline with 16
 * `{size, scaleX, scaleY, opacity}` permutations. The resulting
 * generators are queried via `getPixels` into a target [SkBitmap] which
 * is then drawn at a grid position.
 *
 * **kanvas-skia adaptation** : neither `SkImageGenerators::MakeFromPicture`
 * nor any equivalent SkPicture → SkImageGenerator factory exists in the
 * port. The 16-cell grid would require a non-trivial picture-rasterisation
 * pipeline (picture playback → bitmap → image), so we register a no-op
 * placeholder that just clears to white. The corresponding test is
 * `@Ignored`.
 *
 * Reference image `pictureimagegenerator.png` is preserved in
 * `original-888/` for historical traceability ; rescoring it would
 * require implementing the missing API.
 */
public class PictureGeneratorGM : GM() {

    override fun getName(): String = "pictureimagegenerator"
    override fun getISize(): SkISize = SkISize.Make(1160, 860)

    override fun onDraw(canvas: SkCanvas?) {
        // TODO(O4) : depends on SkImageGenerators.MakeFromPicture, which
        // isn't surfaced in kanvas-skia. Leave a blank canvas to avoid
        // crashing the harness ; the @Ignored test prevents scoring.
    }
}
