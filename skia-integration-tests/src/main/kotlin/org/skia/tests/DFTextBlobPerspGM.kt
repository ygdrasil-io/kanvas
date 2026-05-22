package org.skia.tests

import org.skia.core.SkCanvas
import org.graphiks.math.SkISize

/**
 * Placeholder for `gm/dftext_blob_persp.cpp::DFTextBlobPerspGM`
 * (700 × 840, GPU-only).
 *
 * Renders a `SkTextBlob` under perspective transforms, with the
 * distance-field-text path (`kUseDeviceIndependentFonts_Flag`). The
 * upstream reference `original-888/dftext_blob_persp.png` is GPU-rendered.
 * `kanvas-skia` has no SDF text yet — see [DFTextGM] for the broader
 * gap. Stub keeps the class registered ; the associated test is
 * `@Ignore`'d.
 */
public class DFTextBlobPerspGM : GM() {
    override fun getName(): String = "dftext_blob_persp"
    override fun getISize(): SkISize = SkISize.Make(700, 840)
    override fun onDraw(canvas: SkCanvas?) {
        // TODO : implement once distance-field text + perspective
        //   text-blob CTM path is wired.
    }
}
