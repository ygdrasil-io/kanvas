package org.skia.core

import org.skia.foundation.SkImage
import org.skia.foundation.SkPaint
import org.skia.foundation.SkPath
import org.skia.foundation.SkSamplingOptions
import org.graphiks.math.SkIRect
import org.graphiks.math.SkMatrix
import org.graphiks.math.SkRect

/**
 * Backend-agnostic drawing surface — the bridge between [SkCanvas] and a
 * concrete rasterizer / GPU rasterizer.
 *
 * Extracted from [SkBitmapDevice] in Phase G1.1 of
 * [MIGRATION_PLAN_GPU_WEBGPU.md] so that `SkCanvas` can drive both a CPU
 * raster device ([SkBitmapDevice]) and an upcoming WebGPU device
 * (`SkWebGpuDevice` in `:gpu-raster`) through the same primitives.
 *
 * **Scope.** Only methods exercised by the cross-backend GMs land here.
 * The advanced raster-only surface (compositing layers back into a
 * parent via `compositeFrom`, the internal
 * `setActiveClip` / `setActiveClipShader` plumbing,
 * `drawColoredTriangle` / `drawTexturedTriangle`, direct `bitmap`
 * access) stays on [SkBitmapDevice] for now — `SkCanvas` casts to
 * [SkBitmapDevice] at those call sites and fails fast if a non-raster
 * device flows in. Generalising them is deferred to later G-phases (see
 * the G1.1 entry in the plan for the rationale).
 *
 * Coordinates entering this surface are already in **device pixels**;
 * the canvas owns the matrix and clip stacks and has transformed +
 * clipped before dispatching to the device. The `clip: SkIRect`
 * parameter on each draw call is the integer-aligned device clip that
 * the canvas has computed from its stack.
 */
public interface SkDevice {

    /** Device pixel width. */
    public val width: Int

    /** Device pixel height. */
    public val height: Int

    /**
     * Maximum drawable region in device coordinates — typically
     * `(0, 0, width, height)`. Layers and clip stacks intersect this
     * with the user's clip ops in [SkCanvas].
     */
    public fun deviceClipBounds(): SkIRect

    /** Fill / stroke a rectangle (with optional AA) per `paint`. */
    public fun drawRect(rect: SkRect, clip: SkIRect, paint: SkPaint)

    /**
     * Fill every pixel inside [clip] using [paint] — covers the
     * full clipped device area. Used by `SkCanvas.drawPaint` and
     * `SkCanvas.drawColor` paths.
     */
    public fun drawPaint(ctm: SkMatrix, clip: SkIRect, paint: SkPaint)

    /**
     * Fill / stroke [path] under [ctm] with [paint]. Source-space verbs;
     * the device flattens and rasterizes itself (matching upstream Skia's
     * `SkBaseDevice::drawPath` contract).
     */
    public fun drawPath(path: SkPath, ctm: SkMatrix, clip: SkIRect, paint: SkPaint)

    /**
     * Draw a region of [image] into [devDst] with sampling per
     * [sampling]. `constraint` controls whether texels outside [src]
     * may leak into the resampling kernel (matches upstream's
     * `SkCanvas::SrcRectConstraint`).
     */
    public fun drawImageRect(
        image: SkImage,
        src: SkRect,
        devDst: SkRect,
        sampling: SkSamplingOptions,
        paint: SkPaint?,
        constraint: SrcRectConstraint,
        clip: SkIRect,
    )

    /**
     * Phase G-saveLayer — allocate a fresh, backend-matched offscreen
     * device of the given pixel size that subsequent layer draws will
     * target. The returned device must share this device's colour
     * profile (color space + color type for raster ; intermediate
     * format / present-pass settings for GPU) so [compositeFrom] can
     * blit pixels back without an extra colour-space conversion.
     *
     * **Scope.** Used by [SkCanvas.saveLayer] to back a transparency
     * group. The simplest scaffolding implementation may bail with a
     * helpful error when the backend doesn't yet support layers — this
     * keeps the existing fail-fast contract for GMs that touch layers
     * on a backend that hasn't implemented them.
     */
    public fun makeLayerDevice(width: Int, height: Int): SkDevice

    /**
     * Phase G-saveLayer — composite [src]'s pixels onto this device,
     * with `src`'s `(0, 0)` landing at `(originX, originY)` of this
     * device, intersecting writes with [clip] (in this device's
     * coords). Source pixels are blended through `paint?.blendMode`
     * (defaults to [SkBlendMode.kSrcOver]) after multiplying by
     * `paint?.alpha`.
     *
     * Used by [SkCanvas.restore] to flatten a `saveLayer`'s offscreen
     * device back into its parent. The CPU implementation has the
     * fully-featured pipeline (every blend mode, colour filters,
     * blenders, modes that affect zero-alpha sources). The GPU
     * scaffolding implementation honours **alpha + blendMode only**
     * (kClear / kSrc / kSrcOver / kDstOver in the bitmap-shader
     * pipeline ; the layer's extended modes are deferred).
     */
    public fun compositeFrom(
        src: SkDevice,
        originX: Int,
        originY: Int,
        clip: SkIRect,
        paint: SkPaint?,
    )

    /**
     * G2.x -- bind an analytical "simple shape" clip captured from the
     * canvas's clip stack. When non-null, the device should multiply per-
     * fragment coverage by the shape's coverage (1.0 inside, 0.0 outside,
     * partial on the boundary for AA-friendly pipelines). The shape is
     * already in **device coordinates** ; the device does not need to
     * re-apply the CTM.
     *
     * `null` means "no shape clip on top of the integer [clip] rect",
     * which is the default raster behaviour. This default implementation
     * is a no-op : [SkBitmapDevice] handles `clipPath` via its
     * `setActiveClip(SkAAClip)` machinery, so the simple-shape slot
     * doesn't need to feed back into the CPU rasterizer. GPU devices
     * override to thread the shape through their fragment pipelines.
     *
     * Wired by `SkCanvas.bindClip` before each draw entry point. Resets
     * itself between draws : the canvas pushes `null` whenever the
     * active state has no recognised simple-shape clip.
     */
    public fun setActiveClipShape(shape: SkClipShape?) {
        // Default : ignored. Raster device routes through SkAAClip.
    }
}
