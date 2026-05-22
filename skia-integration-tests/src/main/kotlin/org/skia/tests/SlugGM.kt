package org.skia.tests

import org.skia.core.SkCanvas
import org.graphiks.math.SkISize

/**
 * Stub port of Skia's `gm/slug.cpp::SlugGM` (registered as
 * `slug`, 800 x 800).
 *
 * Upstream verifies the `sktext::gpu::Slug` round-trip:
 * construct an `SkTextBlob`, convert it to a `Slug`, serialise
 * + deserialise it, and re-draw via `canvas.drawSlug(slug)`. The
 * point is to verify the slug binary format + GPU-renderable
 * glyph-run replay path.
 *
 * `:kanvas-skia` does not implement `sktext::gpu::Slug` (the
 * GPU-renderable glyph-run snapshot is not part of the
 * raster-first port). The full plan is tracked under
 * `MIGRATION_PLAN_GPU_WEBGPU.md` (Slug section).
 *
 * TODO: missing API -- `sktext::gpu::Slug`,
 * `SkTextBlob.convertToSlug()`, `SkCanvas.drawSlug()` +
 * `Slug.MakeFromData` / `Slug.serialize`. Flag-planting stub.
 */
public class SlugGM : GM() {

    override fun getName(): String = "slug"
    override fun getISize(): SkISize = SkISize.Make(800, 800)

    override fun onDraw(canvas: SkCanvas?) {
        // TODO: missing API -- sktext::gpu::Slug.
    }
}
