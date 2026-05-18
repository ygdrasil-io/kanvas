package org.skia.gpu.webgpu

import io.ygdrasil.webgpu.ArrayBuffer
import io.ygdrasil.webgpu.BindGroupDescriptor
import io.ygdrasil.webgpu.BindGroupEntry
import io.ygdrasil.webgpu.BindGroupLayoutDescriptor
import io.ygdrasil.webgpu.BindGroupLayoutEntry
import io.ygdrasil.webgpu.BlendComponent
import io.ygdrasil.webgpu.BlendState
import io.ygdrasil.webgpu.BufferBinding
import io.ygdrasil.webgpu.BufferBindingLayout
import io.ygdrasil.webgpu.BufferDescriptor
import io.ygdrasil.webgpu.Color
import io.ygdrasil.webgpu.ColorTargetState
import io.ygdrasil.webgpu.DepthStencilState
import io.ygdrasil.webgpu.FragmentState
import io.ygdrasil.webgpu.GPUAddressMode
import io.ygdrasil.webgpu.GPUColorWrite
import io.ygdrasil.webgpu.GPUCompareFunction
import io.ygdrasil.webgpu.GPUFilterMode
import io.ygdrasil.webgpu.GPUSampler
import io.ygdrasil.webgpu.GPUSamplerBindingType
import io.ygdrasil.webgpu.GPUStencilOperation
import io.ygdrasil.webgpu.RenderPassDepthStencilAttachment
import io.ygdrasil.webgpu.StencilFaceState
import io.ygdrasil.webgpu.GPUBindGroupLayout
import io.ygdrasil.webgpu.GPUBlendFactor
import io.ygdrasil.webgpu.GPUBlendOperation
import io.ygdrasil.webgpu.GPUBufferBindingType
import io.ygdrasil.webgpu.GPUBufferUsage
import io.ygdrasil.webgpu.GPULoadOp
import io.ygdrasil.webgpu.GPURenderPipeline
import io.ygdrasil.webgpu.GPUShaderModule
import io.ygdrasil.webgpu.GPUShaderStage
import io.ygdrasil.webgpu.GPUStoreOp
import io.ygdrasil.webgpu.GPUTextureFormat
import io.ygdrasil.webgpu.PipelineLayoutDescriptor
import io.ygdrasil.webgpu.RenderPassColorAttachment
import io.ygdrasil.webgpu.RenderPassDescriptor
import io.ygdrasil.webgpu.RenderPipelineDescriptor
import io.ygdrasil.webgpu.Extent3D
import io.ygdrasil.webgpu.GPUBuffer
import io.ygdrasil.webgpu.GPUTexture
import io.ygdrasil.webgpu.GPUTextureSampleType
import io.ygdrasil.webgpu.GPUTextureUsage
import io.ygdrasil.webgpu.GPUTextureView
import io.ygdrasil.webgpu.GPUTextureViewDimension
import io.ygdrasil.webgpu.GPUVertexFormat
import io.ygdrasil.webgpu.SamplerBindingLayout
import io.ygdrasil.webgpu.SamplerDescriptor
import io.ygdrasil.webgpu.ShaderModuleDescriptor
import io.ygdrasil.webgpu.TexelCopyBufferLayout
import io.ygdrasil.webgpu.TexelCopyTextureInfo
import io.ygdrasil.webgpu.TextureBindingLayout
import io.ygdrasil.webgpu.TextureDescriptor
import io.ygdrasil.webgpu.VertexAttribute
import io.ygdrasil.webgpu.VertexBufferLayout
import io.ygdrasil.webgpu.VertexState
import io.ygdrasil.webgpu.beginRenderPass
import kotlinx.coroutines.runBlocking
import org.skia.core.SkDevice
import org.skia.core.SrcRectConstraint
import org.skia.foundation.SkBitmapShader
import org.skia.foundation.SkBlendMode
import org.graphiks.math.SkColorGetA
import org.graphiks.math.SkColorGetB
import org.graphiks.math.SkColorGetG
import org.graphiks.math.SkColorGetR
import org.skia.foundation.SkImage
import org.skia.foundation.SkLinearGradient
import org.skia.foundation.SkConicalGradient
import org.skia.foundation.SkRadialGradient
import org.skia.foundation.SkSweepGradient
import org.skia.foundation.SkPaint
import org.skia.foundation.SkPath
import org.skia.foundation.SkPathFillType
import org.skia.foundation.SkFilterMode
import org.skia.foundation.SkSamplingOptions
import org.skia.foundation.SkStroker
import org.skia.foundation.SkTileMode
import org.graphiks.math.SkIRect
import org.graphiks.math.SkMatrix
import org.graphiks.math.SkRect
import kotlin.math.ceil
import kotlin.math.floor

/**
 * G1.2 — first GPU-backed [SkDevice] implementation, built on wgpu4k.
 *
 * **Scope.** Only `drawRect` with axis-aligned, fill-style, opaque-color,
 * non-AA paint is supported. The other three [SkDevice] methods stub out
 * with `TODO("G2+")`. This is enough to ratchet the SkDevice abstraction
 * through SkCanvas onto a non-raster backend and to feed the first
 * rect-fill cross-test (`RectFillCrossTest`).
 *
 * **How it works.**
 *  - The constructor allocates a [HeadlessTarget] (color texture +
 *    staging buffer) sized to `width`/`height`, compiles
 *    `shaders/solid_color.wgsl`, and builds a single `RGBA8Unorm` +
 *    SrcOver render pipeline that reads its fragment color from a
 *    `uniform Uniforms { color: vec4f }` binding.
 *  - Each [drawRect] applies SkBitmapDevice's non-AA pixel-edge rounding
 *    (`floor(coord + 0.5)`) to derive an integer scissor rect, intersects
 *    it with the user's clip and the viewport, and buffers the draw in
 *    [pending]. Nothing is sent to the GPU until [flush].
 *  - [flush] replays the buffered draws: one render pass per draw, each
 *    with its own uniform buffer + bind group. The first pass clears to
 *    [background], later passes use `GPULoadOp.Load`. After all draws,
 *    `copyTextureToBuffer` stages the texture and `mapAsync` reads the
 *    pixels back to the JVM heap as a tightly-packed RGBA byte array.
 *
 * **Why one render pass per draw, not one accumulating pass?** WebGPU
 * does not let us call `queue.writeBuffer` between draws of a single
 * render pass (the writes must happen outside the encoder). Per-draw
 * render passes are the simplest correct workaround; per-draw bind
 * groups + dynamic-offset are an optimisation for G2+.
 *
 * **GLFW main-thread caveat.** [WebGpuContext.createOrNull] still
 * bootstraps a hidden GLFW window for the [io.ygdrasil.webgpu.NativeSurface];
 * tests run under `-XstartOnFirstThread` (configured in
 * `gpu-raster/build.gradle.kts`). The device itself never touches GLFW
 * after the context is built.
 */
public class SkWebGpuDevice(
    private val context: WebGpuContext,
    override val width: Int,
    override val height: Int,
    /**
     * G6.1 — when `true`, the final present pass applies the
     * sRGB → linear → Rec.2020 → encoded transform so readback bytes
     * are in `DM_REFERENCE_COLOR_SPACE` (cross-test convention).
     * When `false`, the present pass is an identity copy — readback
     * bytes stay in raw sRGB primaries (what unit tests expect).
     * `WebGpuSink` (cross-tests) flips this to `true` ; everything
     * else stays on the raw sRGB default.
     */
    private val applyColorspaceTransform: Boolean = false,
    /**
     * G6.2 — backing format of the intermediate render target. All draw
     * pipelines target this format. Default `RGBA16Float` (F16) buys
     * sub-byte precision on intermediate blends, gradient stop lerps,
     * and future image filters. Callers on drivers that don't support
     * F16 blending (or memory-constrained backends) can pass
     * `RGBA8Unorm` to fall back to the G6.1 behaviour. The final
     * readback target stays `RGBA8Unorm` regardless ; this only
     * affects the intermediate.
     */
    private val intermediateFormat: GPUTextureFormat = GPUTextureFormat.RGBA16Float,
) : SkDevice, AutoCloseable {

    /**
     * Final readback target -- the present pass writes here, then we
     * `copyTextureToBuffer` from its `colorTexture`. After G6.1, draws
     * no longer target this texture directly ; they target
     * [intermediateTexture] and the present pass copies through the
     * sRGB → Rec.2020 transform.
     */
    private val target: HeadlessTarget =
        HeadlessTarget(context, width, height, GPUTextureFormat.RGBA8Unorm)

    /**
     * G6.1 intermediate render target. Draws (rect / polygon / aa-polygon)
     * target this texture. The present pass then samples it via
     * `textureLoad` and writes the colorspace-converted result to
     * `target.colorTexture`. Usage = RenderAttachment (for draws) +
     * TextureBinding (for the present pass).
     *
     * **G6.2** : format is `RGBA16Float` (was `RGBA8Unorm` in G6.1).
     * Content convention is unchanged -- draw shaders still emit
     * **premul sRGB-coded** values and the present pass / identity
     * pass still interpret the contents as sRGB-coded on readback.
     * The format change only buys sub-byte precision in the
     * intermediate, so blends and gradient stop lerps no longer
     * quantise to 8-bit before the readback re-encoding.
     *
     * **Why not linear ?** The original G6.2 task description had the
     * shaders linearise their source colours so the F16 attachment
     * would carry premul-linear values (a true linear working space).
     * In practice that flips the WebGPU blend hardware from
     * sRGB-coded blending to linear blending, which diverges from
     * the cross-test reference (rendered by `RasterSinkF16` whose
     * F16 raster blends in the destination's encoding space, similar
     * in shape to sRGB-coded blending and matched by the prior
     * RGBA8Unorm-intermediate output to within 0.06% on
     * `BatchedConvexPathsGM`). Switching to true-linear blending
     * regresses translucent-stacking GMs by 30-65 percentage points
     * which violates the G6.2 ratchet contract (math equivalence ;
     * scores unchanged). The compromise here is to keep the format
     * upgrade -- precision and future-readiness for image filters /
     * higher-precision gradients -- while preserving the existing
     * blending math byte-for-byte.
     */
    private val intermediateTexture: GPUTexture = context.device.createTexture(
        TextureDescriptor(
            size = Extent3D(width = width.toUInt(), height = height.toUInt()),
            format = intermediateFormat,
            usage = GPUTextureUsage.RenderAttachment or GPUTextureUsage.TextureBinding,
            label = "SkWebGpuDevice.intermediate",
        ),
    )

    /** Persistent view of [intermediateTexture] for the present bind group. */
    private val intermediateView: GPUTextureView = intermediateTexture.createView()

    /**
     * G3.3b.2b — depth/stencil texture for stencil-and-cover multi-contour
     * path rendering. The depth bits are never used (`depthCompare = Always`,
     * `depthWriteEnabled = false`). The 8 stencil bits track the path's
     * **winding count** during the stencil pass : front-face triangles
     * increment, back-face triangles decrement (wrapping at 0/255). The
     * cover pass then writes color where stencil != 0. Format
     * `depth24plus-stencil8` is the most portable depth-stencil combination
     * in WebGPU.
     */
    private val depthStencilTexture: GPUTexture = context.device.createTexture(
        TextureDescriptor(
            size = Extent3D(width = width.toUInt(), height = height.toUInt()),
            format = GPUTextureFormat.Depth24PlusStencil8,
            usage = GPUTextureUsage.RenderAttachment,
            label = "SkWebGpuDevice.depthStencil",
        ),
    )
    private val depthStencilView: GPUTextureView = depthStencilTexture.createView()

    /**
     * Clear value used by the first render pass of every [flush]. White
     * matches Skia's DM convention (GMs that don't override `bgColor()`
     * paint onto a white canvas).
     */
    private var background: Color = Color(1.0, 1.0, 1.0, 1.0)

    /** Update the clear value used by the next [flush]. */
    public fun setBackground(srgbArgb: Int) {
        background = Color(
            r = SkColorGetR(srgbArgb) / 255.0,
            g = SkColorGetG(srgbArgb) / 255.0,
            b = SkColorGetB(srgbArgb) / 255.0,
            a = SkColorGetA(srgbArgb) / 255.0,
        )
    }

    /**
     * One pending draw, accumulated by the public draw entry points and
     * replayed by [flush]. Two variants in G3.3a :
     *  - [RectDraw] : the G1.2/G2.3a path — full-screen Bjorke triangle
     *    + setScissorRect + analytical coverage in the fragment shader
     *    (`solid_color.wgsl`).
     *  - [PolygonDraw] : the G3.3a path — uploaded triangle list, vertex
     *    shader projects device-pixel coords into NDC, no AA coverage
     *    (`solid_polygon.wgsl`).
     *
     * The two variants share the blend pipeline cache (one per blend
     * mode per shader). Draw order is preserved across types so blend
     * modes compose correctly.
     */
    private sealed interface PendingDraw {
        val mode: SkBlendMode
        val r: Float; val g: Float; val b: Float; val a: Float
    }

    /**
     * Scissor rect (integer, device pixels — sets WebGPU `setScissorRect`)
     * paired with the fractional outer + inner bounds passed to the shader
     * for analytical coverage (`outer_cov - inner_cov`, G3.1.1).
     *  - **Fill rects** : `outer = rect bounds`, `inner = degenerate
     *    (l > r, t > b)` so `inner_cov = 0` and coverage collapses to the
     *    fill-only result.
     *  - **AA stroke rects / AA hairlines** : `outer = rect ± half_sw`,
     *    `inner = rect ∓ half_sw` (with `half_sw = 0.5` for hairlines per
     *    SkBitmapDevice's strokeRectAA convention).
     *
     * Non-AA fills collapse outer bounds to pixelEdge-rounded ints, the
     * shader's coverage formula evaluates to 1.0 for every interior
     * pixel, so the output is byte-identical to the pre-G3.1.1 path.
     */
    private data class RectDraw(
        val x: Int, val y: Int, val w: Int, val h: Int,
        val ol: Float, val ot: Float, val or: Float, val ob: Float,
        val il: Float, val it: Float, val ir: Float, val ib: Float,
        override val r: Float, override val g: Float, override val b: Float, override val a: Float,
        override val mode: SkBlendMode,
    ) : PendingDraw

    /**
     * Triangle-list buffer in **device-pixel** coords. `verts` holds
     * `triangleCount * 6` floats `[x0, y0, x1, y1, x2, y2, ...]`. Built
     * by [drawPath] via fan tessellation from the path's first vertex —
     * exact for convex polygons, only approximate for concave (G3.3b).
     *
     * [scissor] is the axis-aligned device clip captured at drawPath
     * time `(x, y, w, h)`. Applied via `setScissorRect` before drawing
     * so any clip the SkCanvas state had at the time is respected even
     * if the triangle list extends beyond it.
     */
    private data class PolygonDraw(
        val verts: FloatArray,
        val scissor: IntArray, // (x, y, w, h)
        override val r: Float, override val g: Float, override val b: Float, override val a: Float,
        override val mode: SkBlendMode,
    ) : PendingDraw

    /**
     * G3.3b.2b — multi-contour / concave path via stencil-and-cover.
     *  - `stencilVerts` : concatenated fan tessellations of each contour
     *    (each contour's vertices are fanned from their own first vertex).
     *    Rendered in the stencil pass with front-face increment + back-face
     *    decrement → stencil = winding count.
     *  - `coverVerts` : 2-triangle bbox spanning all contours, slightly
     *    inflated. Rendered in the cover pass with stencil-compare != 0
     *    → only pixels inside the path (per winding rule) get the color.
     *  - `scissor` : axis-aligned device clip rect, applied to both passes.
     */
    private data class StencilCoverPolygonDraw(
        val stencilVerts: FloatArray,
        val coverVerts: FloatArray,
        val scissor: IntArray,
        val fillType: SkPathFillType,
        override val r: Float, override val g: Float, override val b: Float, override val a: Float,
        override val mode: SkBlendMode,
    ) : PendingDraw

    /**
     * G3.3b.3a — AA multi-contour path via stencil-and-cover with a
     * per-fragment edge-segment coverage shader. Same `stencilVerts` /
     * `coverVerts` as [StencilCoverPolygonDraw] ; `edges` carries the
     * path's edge segments `(Ax, Ay, Bx, By)` across all contours
     * (caller guarantees `edgeCount <= MAX_AA_EDGES`).
     *
     * Stencil pass identical to the non-AA variant ; cover pass uses
     * the AA-stencil-cover pipeline (stencil-test NotEqual-0, fragment
     * shader computes `coverage = clamp(minDist + 0.5, 0, 1)` from the
     * edge segments). See `aa_stencil_cover.wgsl` for the trade-off
     * (loses the outside half of the AA falloff vs throwing).
     */
    private data class StencilCoverAaPolygonDraw(
        val stencilVerts: FloatArray,
        val coverVerts: FloatArray,
        val edges: FloatArray,   // (Ax, Ay, Bx, By) per edge, length = MAX_AA_EDGES * 4
        val edgeCount: Int,
        val scissor: IntArray,
        val fillType: SkPathFillType,
        override val r: Float, override val g: Float, override val b: Float, override val a: Float,
        override val mode: SkBlendMode,
    ) : PendingDraw

    /**
     * AA variant of [PolygonDraw]. `verts` is the fan-tessellated
     * triangle list (same as non-AA), `edges` is the polygon's
     * **perimeter** edge equations `(a, b, c, _)` per edge in
     * `clamp(a*x + b*y + c + 0.5, 0, 1)` form (positive `dist` = inside).
     * `edgeCount` is the number of meaningful entries in `edges` ; the
     * remainder is zero-padded up to `MAX_AA_EDGES`. The fragment shader
     * iterates the edges, takes min(coverage) and modulates the premul
     * source alpha — matches `SkBitmapDevice`'s analytical coverage
     * formulation for convex polygons.
     */
    private data class AaPolygonDraw(
        val verts: FloatArray,
        val edges: FloatArray,   // (a, b, c, _) per edge, length = MAX_AA_EDGES * 4
        val edgeCount: Int,
        val scissor: IntArray,
        override val r: Float, override val g: Float, override val b: Float, override val a: Float,
        override val mode: SkBlendMode,
    ) : PendingDraw

    /**
     * G4.1 — linear gradient (clamp tile mode) fill of an axis-aligned
     * rect. Same scissor + full-screen-triangle dispatch as [RectDraw],
     * but the fragment shader reads `(start, end, stops, positions)` from
     * the uniform and emits a per-pixel premul color rather than a constant.
     *
     *  - [scissor] : `(x, y, w, h)` — integer device-pixel scissor, set
     *    via `setScissorRect` before drawing.
     *  - [startX] / [startY] / [endX] / [endY] : gradient endpoints in
     *    **device-pixel coords** (i.e. already CTM-transformed from the
     *    shader-local endpoints).
     *  - [stopPositions] / [stopColors] : sorted-on-input table, both
     *    pre-padded to `MAX_GRADIENT_STOPS` slots (positions = first
     *    float of each vec4, colors = 4-float premul vec4 per stop).
     *  - [stopCount] : how many entries are meaningful.
     *
     * Inherited `r/g/b/a` are unused (the shader sources color from
     * the stops) but kept for the [PendingDraw] interface — set to the
     * first stop so they remain interpretable.
     */
    private data class LinearGradientRectDraw(
        val scissor: IntArray,
        val startX: Float, val startY: Float,
        val endX: Float, val endY: Float,
        val stopPositions: FloatArray, // MAX_GRADIENT_STOPS * 4 floats (x of each vec4)
        val stopColors: FloatArray,    // MAX_GRADIENT_STOPS * 4 premul vec4
        val stopCount: Int,
        // G4.1.1 -- one pipeline per (blend, tile) pair ; the shader has
        // 4 fragment entry points, one per SkTileMode (fs_clamp /
        // fs_repeat / fs_mirror / fs_decal).
        val tileMode: SkTileMode,
        override val r: Float, override val g: Float, override val b: Float, override val a: Float,
        override val mode: SkBlendMode,
    ) : PendingDraw

    /**
     * G4.2 -- pending radial-gradient fill of an axis-aligned rect. Mirrors
     * [LinearGradientRectDraw] : same scissor + stops packing, but the
     * gradient is parameterised by `(center, radius)` instead of
     * `(start, end)`. `centerX` / `centerY` / `radius` are in
     * **device-pixel coords** (already CTM-transformed at draw time).
     *
     * [tileMode] is kept in the per-draw record (and in the pipeline cache
     * key) for symmetry with the linear gradient even though only kClamp
     * routes here today ; the other tile modes throw at pipeline-build
     * time with a pointer to G4.2.1.
     */
    private data class RadialGradientRectDraw(
        val scissor: IntArray,
        val centerX: Float, val centerY: Float, val radius: Float,
        val stopPositions: FloatArray,
        val stopColors: FloatArray,
        val stopCount: Int,
        val tileMode: SkTileMode,
        override val r: Float, override val g: Float, override val b: Float, override val a: Float,
        override val mode: SkBlendMode,
    ) : PendingDraw

    /**
     * G4.3 -- pending sweep-gradient fill of an axis-aligned rect. Mirror
     * of [LinearGradientRectDraw] / [RadialGradientRectDraw] : same scissor
     * + stops packing, but the gradient is parameterised by `(center, tBias,
     * tScale)` in device-pixel coords. `tBias = -startAngle / 360` and
     * `tScale = 360 / (endAngle - startAngle)` collapse the host-side angle
     * remapping to one add + one mul in the fragment shader. For the
     * canonical full sweep (start = 0, end = 360) tBias = 0 and tScale = 1.
     *
     * [tileMode] is kept in the per-draw record (and in the pipeline cache
     * key) for symmetry with the linear / radial gradients ; G4.3.1 opens
     * the dispatch gate to all 4 tile modes (the pipeline cache already
     * wired fs_repeat / fs_mirror / fs_decal since G4.3).
     */
    private data class SweepGradientRectDraw(
        val scissor: IntArray,
        val centerX: Float, val centerY: Float,
        val tBias: Float, val tScale: Float,
        val stopPositions: FloatArray,
        val stopColors: FloatArray,
        val stopCount: Int,
        val tileMode: SkTileMode,
        override val r: Float, override val g: Float, override val b: Float, override val a: Float,
        override val mode: SkBlendMode,
    ) : PendingDraw

    /**
     * G4.4 / G4.4.2 -- pending conical (two-point) gradient fill of an
     * axis-aligned rect. Supports the **kRadial** sub-case of
     * [SkConicalGradient] (concentric circles, `c0 == c1`) under all 4
     * tile modes ; other sub-cases (kStrip, kFocal -- handled separately
     * via [ConicalFocalGradientRectDraw]) fall through at the dispatch
     * gate to the existing solid-color fill machinery.
     *
     * `centerX` / `centerY` are the shared centre in device-pixel coords
     * (already CTM-mapped) ; `startRadius` / `endRadius` are the start /
     * end circle radii scaled by `ctm.getMaxScale()` (axis-aligned-CTM
     * gate collapses this to `max(|sx|, |sy|)`). The shader evaluates
     * `t = (length(p - c1) - r0) / (r1 - r0)` and looks up the stops via
     * the per-tile-mode entry point ([tileMode] picks among `fs_clamp`,
     * `fs_repeat`, `fs_mirror`, `fs_decal`).
     */
    private data class ConicalGradientRectDraw(
        val scissor: IntArray,
        val centerX: Float, val centerY: Float,
        val startRadius: Float, val endRadius: Float,
        val stopPositions: FloatArray,
        val stopColors: FloatArray,
        val stopCount: Int,
        val tileMode: SkTileMode,
        override val r: Float, override val g: Float, override val b: Float, override val a: Float,
        override val mode: SkBlendMode,
    ) : PendingDraw

    /**
     * G4.4.1 / G4.4.2 / G4.4.5 / G4.4.6 -- pending conical (two-point)
     * gradient fill of an axis-aligned rect, **focal-inside well-
     * behaved**, **focal-outside**, or **focal-on-circle** sub-case of
     * [SkConicalGradient]. Focal-inside : `focalData.fR1 > 1`, not
     * focal-on-circle. Focal-outside : `focalData.fR1 < 1`, not focal-
     * on-circle. Focal-on-circle : `|focalData.fR1 - 1| < tolerance`,
     * the focal point lies on the end circle (G4.4.6). Routed for all
     * 4 tile modes.
     *
     * Per-draw payload :
     *  - `affine00..affine12` : 2x3 row-major affine `device -> focal frame`,
     *    computed as `gradientMatrix * (CTM * localMatrix)^-1`. This is the
     *    same matrix the CPU caches as `deviceToConical` in [setupForDraw].
     *    Pixel-center coords are passed straight through (WGSL's
     *    `@builtin(position)` is already at the half-pixel offset).
     *  - `fP0 = 1 / fR1`, `fP1 = fFocalX` : the focal scalars consumed by
     *    the well-behaved formula `t = sqrt(x*x + y*y) - x * fP0` and the
     *    focal-outside formula `t = sign * sqrt(x*x - y*y) - x * fP0`.
     *  - `compensateFocal` : 1.0 iff `fFocalX != 0` (apply `t += fP1`).
     *  - `unswap` : 1.0 iff `fIsSwapped` (apply `t = 1 - t`).
     *  - `negateX` : 1.0 iff `(1 - fFocalX) < 0` (apply `t = -t` between
     *    the formula and `compensateFocal`).
     *  - `subCase` : 0.0 for well-behaved focal-inside, 1.0 for focal-
     *    outside (greater / smaller), 2.0 for focal-on-circle. The
     *    shader picks the formula and the in-cone mask off this flag.
     *  - `subCaseSign` : +1.0 for the "greater" focal-outside variant
     *    (`+sqrt`), -1.0 for "smaller" (`-sqrt`). Only consulted when
     *    `subCase == 1.0`. Mirrors the CPU branch
     *    `isSwapped() || (1 - fFocalX) < 0` -> "smaller".
     *
     * Mirrors [SkConicalGradient.computeTFocal]'s well-behaved + focal-
     * outside + focal-on-circle branches byte-for-byte.
     */
    private data class ConicalFocalGradientRectDraw(
        val scissor: IntArray,
        // 2x3 row-major affine `device pixel -> focal frame`.
        val affine00: Float, val affine01: Float, val affine02: Float,
        val affine10: Float, val affine11: Float, val affine12: Float,
        val fP0: Float, val fP1: Float,
        val compensateFocal: Float, val unswap: Float,
        val negateX: Float,
        val subCase: Float, val subCaseSign: Float,
        val stopPositions: FloatArray,
        val stopColors: FloatArray,
        val stopCount: Int,
        val tileMode: SkTileMode,
        override val r: Float, override val g: Float, override val b: Float, override val a: Float,
        override val mode: SkBlendMode,
    ) : PendingDraw

    /**
     * G4.4.4 -- pending conical (two-point) gradient fill of an
     * axis-aligned rect, **kStrip** sub-case of [SkConicalGradient]
     * (`c0 != c1` and `r0 == r1`). All 4 tile modes routed (the pipeline
     * cache wires all 4 entry points from day one). Other sub-cases
     * (kRadial, kFocal in any variant) keep going through their existing
     * dispatch.
     *
     * Per-draw payload :
     *  - `affine00..affine12` : 2x3 row-major affine `device -> conical
     *    frame`, computed as `gradientMatrix * (CTM * localMatrix)^-1`,
     *    same chain as the focal-inside dispatch. `gradientMatrix` for
     *    kStrip is the `MapToUnitX` matrix (puts c0 at origin, c1 at
     *    (1, 0)).
     *  - `stripP0 = (r0 / centerX1)^2` : the strip's quadratic-disc
     *    constant, with `centerX1 = |c1 - c0|`. Mirrors the value cached
     *    on the CPU by [SkConicalGradient.getStripP0], which matches
     *    upstream's `scaledR0 = fRadius1 / getCenterX1()` (see
     *    SkConicalGradient.cpp::appendGradientStages). GPU stays in
     *    lockstep with the CPU.
     *
     * Mirrors [SkConicalGradient.computeT]'s kStrip branch byte-for-byte
     * (formula `t = x + sqrt(fP0 - y*y)`, with `disc < 0` producing
     * transparent black via the shader's `in_strip` factor).
     */
    private data class ConicalStripGradientRectDraw(
        val scissor: IntArray,
        // 2x3 row-major affine `device pixel -> conical frame`.
        val affine00: Float, val affine01: Float, val affine02: Float,
        val affine10: Float, val affine11: Float, val affine12: Float,
        val stripP0: Float,
        val stopPositions: FloatArray,
        val stopColors: FloatArray,
        val stopCount: Int,
        val tileMode: SkTileMode,
        override val r: Float, override val g: Float, override val b: Float, override val a: Float,
        override val mode: SkBlendMode,
    ) : PendingDraw

    /**
     * G4.1.2 -- AA stencil-and-cover path filled with a linear gradient.
     * Geometry payload mirrors [StencilCoverAaPolygonDraw] (stencil fan
     * triangles + cover bbox quad + edge segments for the AA falloff
     * helper) ; paint payload mirrors [LinearGradientRectDraw] (start/end
     * in device-pixel coords, premul stops, tileMode, stopCount). Routed
     * through [drawPath] when `paint.shader is SkLinearGradient` is AA and
     * the path is *not* an axis-aligned rect (the rect path keeps the
     * cheaper [LinearGradientRectDraw] full-screen-triangle dispatch).
     *
     * Inherited `r/g/b/a` are the first stop's premul color (placeholder ;
     * the fragment shader sources color from the stops table).
     */
    private data class StencilCoverAaGradientDraw(
        val stencilVerts: FloatArray,
        val coverVerts: FloatArray,
        val edges: FloatArray,
        val edgeCount: Int,
        val scissor: IntArray,
        val fillType: SkPathFillType,
        val startX: Float, val startY: Float,
        val endX: Float, val endY: Float,
        val stopPositions: FloatArray,
        val stopColors: FloatArray,
        val stopCount: Int,
        val tileMode: SkTileMode,
        override val r: Float, override val g: Float, override val b: Float, override val a: Float,
        override val mode: SkBlendMode,
    ) : PendingDraw

    /**
     * G4.2.2 -- AA stencil-and-cover path filled with a radial gradient.
     * Mirror of [StencilCoverAaGradientDraw] (linear variant) : same
     * geometry payload (stencil fan triangles + cover bbox quad + edge
     * segments for the AA falloff) ; paint payload swaps `(start, end)`
     * for `(center, radius)` in device-pixel coords. Routed through
     * [drawPath] when `paint.shader is SkRadialGradient` is AA and the
     * path is *not* an axis-aligned rect (the rect path keeps the
     * cheaper [RadialGradientRectDraw] full-screen-triangle dispatch).
     *
     * Inherited `r/g/b/a` are the first stop's premul color (placeholder ;
     * the fragment shader sources color from the stops table).
     */
    private data class StencilCoverAaRadialGradientDraw(
        val stencilVerts: FloatArray,
        val coverVerts: FloatArray,
        val edges: FloatArray,
        val edgeCount: Int,
        val scissor: IntArray,
        val fillType: SkPathFillType,
        val centerX: Float, val centerY: Float, val radius: Float,
        val stopPositions: FloatArray,
        val stopColors: FloatArray,
        val stopCount: Int,
        val tileMode: SkTileMode,
        override val r: Float, override val g: Float, override val b: Float, override val a: Float,
        override val mode: SkBlendMode,
    ) : PendingDraw

    // ─── Sweep gradient on non-rect (G4.3.2) ─────────────────────────────
    /**
     * G4.3.2 -- AA stencil-and-cover path filled with a sweep gradient.
     * Mirror of [StencilCoverAaGradientDraw] (linear) and
     * [StencilCoverAaRadialGradientDraw] (radial) : same geometry payload
     * (stencil fan triangles + cover bbox quad + edge segments for the AA
     * falloff) ; paint payload swaps `(start, end)` / `(center, radius)`
     * for `(center, tBias, tScale)` in device-pixel coords. Routed through
     * [drawPath] when `paint.shader is SkSweepGradient` is AA and the
     * path is *not* an axis-aligned rect (the rect path keeps the cheaper
     * [SweepGradientRectDraw] full-screen-triangle dispatch).
     *
     * Inherited `r/g/b/a` are the first stop's premul color (placeholder ;
     * the fragment shader sources color from the stops table).
     */
    private data class StencilCoverAaSweepGradientDraw(
        val stencilVerts: FloatArray,
        val coverVerts: FloatArray,
        val edges: FloatArray,
        val edgeCount: Int,
        val scissor: IntArray,
        val fillType: SkPathFillType,
        val centerX: Float, val centerY: Float,
        val tBias: Float, val tScale: Float,
        val stopPositions: FloatArray,
        val stopColors: FloatArray,
        val stopCount: Int,
        val tileMode: SkTileMode,
        override val r: Float, override val g: Float, override val b: Float, override val a: Float,
        override val mode: SkBlendMode,
    ) : PendingDraw

    // ─── Conical gradient on non-rect (G4.4.3) ─────────────────────────────
    /**
     * G4.4.3 -- AA stencil-and-cover path filled with a conical (two-point)
     * gradient, **kRadial** sub-case. Mirror of
     * [StencilCoverAaRadialGradientDraw] : same geometry payload (stencil
     * fan triangles + cover bbox quad + edge segments for the AA falloff) ;
     * paint payload swaps `(center, radius)` for `(center, r0, r1)`. Routed
     * through [drawPath] when `paint.shader is SkConicalGradient` of type
     * kRadial is AA and the path is *not* an axis-aligned rect (the rect
     * path keeps the cheaper [ConicalGradientRectDraw] full-screen-triangle
     * dispatch).
     *
     * Inherited `r/g/b/a` are the first stop's premul color (placeholder ;
     * the fragment shader sources color from the stops table).
     */
    private data class StencilCoverAaConicalGradientDraw(
        val stencilVerts: FloatArray,
        val coverVerts: FloatArray,
        val edges: FloatArray,
        val edgeCount: Int,
        val scissor: IntArray,
        val fillType: SkPathFillType,
        val centerX: Float, val centerY: Float,
        val startRadius: Float, val endRadius: Float,
        val stopPositions: FloatArray,
        val stopColors: FloatArray,
        val stopCount: Int,
        val tileMode: SkTileMode,
        override val r: Float, override val g: Float, override val b: Float, override val a: Float,
        override val mode: SkBlendMode,
    ) : PendingDraw

    /**
     * G4.4.3 / G4.4.5 / G4.4.6 -- AA stencil-and-cover path filled with
     * a conical (two-point) gradient, **focal-inside well-behaved**,
     * **focal-outside**, or **focal-on-circle** sub-case. Mirror of
     * [StencilCoverAaConicalGradientDraw] above and
     * [ConicalFocalGradientRectDraw] (rect-only G4.4.1 / G4.4.5 /
     * G4.4.6) ; same geometry payload as the other stencil-cover
     * gradient variants ; paint payload matches the focal rect-only
     * draw byte-for-byte (2x3 affine + focal scalars + flags + sub-case
     * selector + sign).
     */
    private data class StencilCoverAaConicalFocalGradientDraw(
        val stencilVerts: FloatArray,
        val coverVerts: FloatArray,
        val edges: FloatArray,
        val edgeCount: Int,
        val scissor: IntArray,
        val fillType: SkPathFillType,
        // 2x3 row-major affine `device pixel -> focal frame`.
        val affine00: Float, val affine01: Float, val affine02: Float,
        val affine10: Float, val affine11: Float, val affine12: Float,
        val fP0: Float, val fP1: Float,
        val compensateFocal: Float, val unswap: Float,
        val negateX: Float,
        val subCase: Float, val subCaseSign: Float,
        val stopPositions: FloatArray,
        val stopColors: FloatArray,
        val stopCount: Int,
        val tileMode: SkTileMode,
        override val r: Float, override val g: Float, override val b: Float, override val a: Float,
        override val mode: SkBlendMode,
    ) : PendingDraw

    // ─── Bitmap shader (G5.1) — drawImageRect skeleton ────────────────────

    /**
     * G5.1 -- pending bitmap-textured fill of an axis-aligned device-space
     * rect. Holds everything the bitmap-shader pipeline needs to render
     * one `drawImageRect` call : the source-image texture handle (already
     * uploaded and cached by [imageTextureCacheFor]), the source sub-rect
     * in image-pixel coords, the destination rect in device-pixel coords,
     * the integer scissor (device-pixel `(x, y, w, h)` -- clip-clamped),
     * and the paint scale folded into the sampled colour (premul vec4f ;
     * defaults to `(1, 1, 1, 1)` when the paint is null).
     *
     * G5.1.1 -- the (filter, tile) pair is now carried per-draw so the
     * dispatch gate can route `kNearest` and the non-clamp tile modes
     * through the same pipeline cache. The blend mode (inherited
     * `PendingDraw.mode`) is also widened to the 4 modes WebGPU
     * expresses natively (kClear / kSrc / kSrcOver / kDstOver via
     * [blendStateFor]).
     *
     * Inherited `r/g/b/a` carry the paint's solid colour (placeholder ;
     * the fragment shader sources colour from the texture, the rgba
     * channels are kept for the [PendingDraw] interface contract).
     *
     * G5.3 -- [csMode] selects the shader's colorspace-transform branch
     * (`0 = identity / sRGB fast path`, `1 = sRGB EOTF -> matrix -> sRGB
     * OETF`). [csMatrix] is the column-major 3x3 primaries matrix from
     * source-linear to sRGB-linear ; for `csMode == 0` the host passes
     * the identity so the multiply is a no-op even if the shader were
     * to take the transform branch. The matrix is computed once per
     * image via [bitmapColorSpaceFor].
     */
    private data class ImageRectDraw(
        val texture: GPUTexture,
        val imageWidth: Int, val imageHeight: Int,
        val srcL: Float, val srcT: Float, val srcR: Float, val srcB: Float,
        val dstL: Float, val dstT: Float, val dstR: Float, val dstB: Float,
        val scissor: IntArray,
        val paintR: Float, val paintG: Float, val paintB: Float, val paintA: Float,
        val filter: SkFilterMode,
        // G5.1 drawImageRect always uses the same tile on both axes ; G5.2
        // (paint.shader is SkBitmapShader) carries the per-axis tileX /
        // tileY pair that the shader was constructed with. The sampler
        // cache key + the shader's per-axis decal check both honour the
        // split.
        val tileX: SkTileMode,
        val tileY: SkTileMode,
        val csMode: Int,
        val csMatrix: FloatArray,
        override val r: Float, override val g: Float, override val b: Float, override val a: Float,
        override val mode: SkBlendMode,
    ) : PendingDraw

    // ─── Bitmap shader on non-rect (G5.2.1) ─────────────────────────────
    /**
     * G5.2.1 -- AA stencil-and-cover path filled with a bitmap shader
     * (`paint.shader is SkBitmapShader`). Mirror of
     * [StencilCoverAaSweepGradientDraw] / [StencilCoverAaConicalGradientDraw] :
     * same geometry payload (stencil fan triangles + cover bbox quad +
     * edge segments for the AA falloff) ; paint payload swaps the
     * gradient (center, tBias, tScale) for the bitmap shader's
     * (texture, src, dst, imageSize, tileX, tileY, paintColor, csMode,
     * csMatrix) tuple. Routed through [drawPath] when
     * `paint.shader is SkBitmapShader` is AA, the CTM is axis-aligned,
     * the shader's local matrix is axis-aligned, and the path is *not*
     * an axis-aligned rect (the rect path keeps the cheaper
     * [ImageRectDraw] full-screen-triangle dispatch via G5.2's
     * [drawBitmapShaderFillRect]).
     *
     * Inherited `r/g/b/a` are placeholders (the fragment shader sources
     * colour from the texture) ; kept for the [PendingDraw] interface
     * contract.
     */
    private data class StencilCoverAaBitmapShaderDraw(
        val stencilVerts: FloatArray,
        val coverVerts: FloatArray,
        val edges: FloatArray,
        val edgeCount: Int,
        val scissor: IntArray,
        val fillType: SkPathFillType,
        val texture: GPUTexture,
        val imageWidth: Int, val imageHeight: Int,
        val srcL: Float, val srcT: Float, val srcR: Float, val srcB: Float,
        val dstL: Float, val dstT: Float, val dstR: Float, val dstB: Float,
        val paintR: Float, val paintG: Float, val paintB: Float, val paintA: Float,
        val filter: SkFilterMode,
        val tileX: SkTileMode,
        val tileY: SkTileMode,
        val csMode: Int,
        val csMatrix: FloatArray,
        override val r: Float, override val g: Float, override val b: Float, override val a: Float,
        override val mode: SkBlendMode,
    ) : PendingDraw

    /**
     * G5.2.1 -- pre-computed bitmap-shader draw payload, shared by the
     * 3 path-shape branches (multi-contour / inverse-or-concave /
     * convex). Built once per [drawPath] from the shader's
     * `(image, tileX, tileY, sampling, localMatrix)` tuple composed
     * with the (axis-aligned) CTM. The (src, dst) rect pair encodes
     * the device-pixel -> image-pixel affine used by
     * `aa_stencil_cover_bitmap_shader.wgsl` (same shape as
     * `bitmap_shader.wgsl`'s rect pipeline). For non-rect paths we
     * pick `src = (0, 0, imgW, imgH)` and let
     * `dst = (ctm * localMatrix).mapRect(src)` ; the resulting affine
     * is correct over the whole device frame, the path-shape stencil
     * mask + AA cover-pass selects which fragments actually sample.
     *
     * Returns `null` when the combined matrix is degenerate (the
     * caller falls through to the solid-colour fill).
     */
    private data class BitmapShaderPayload(
        val texture: GPUTexture,
        val imageWidth: Int,
        val imageHeight: Int,
        val srcL: Float, val srcT: Float, val srcR: Float, val srcB: Float,
        val dstL: Float, val dstT: Float, val dstR: Float, val dstB: Float,
        val paintR: Float, val paintG: Float, val paintB: Float, val paintA: Float,
        val filter: SkFilterMode,
        val tileX: SkTileMode,
        val tileY: SkTileMode,
        val csMode: Int,
        val csMatrix: FloatArray,
    )

    private fun buildBitmapShaderPayload(
        shader: SkBitmapShader,
        ctm: SkMatrix,
        paint: SkPaint,
    ): BitmapShaderPayload? {
        val image = shader.getImage()
        if (image.width <= 0 || image.height <= 0) return null
        val combined = ctm.preConcat(shader.localMatrix)
        if (!combined.isAxisAligned) return null
        if (combined.sx == 0f || combined.sy == 0f) return null
        // src spans the full image (in image-pixel coords) ; dst is its
        // image through the combined affine. The shader uses (src, dst)
        // as a `device -> image-pixel` affine -- correct over the whole
        // viewport regardless of path shape ; the stencil + AA cover
        // pass select which fragments actually sample.
        val (x0, y0) = combined.mapXY(0f, 0f)
        val (x1, y1) = combined.mapXY(image.width.toFloat(), image.height.toFloat())
        val dstL = minOf(x0, x1); val dstT = minOf(y0, y1)
        val dstR = maxOf(x0, x1); val dstB = maxOf(y0, y1)
        val texture = imageTextureFor(image)
        val (csMode, csMatrix) = bitmapColorSpaceFor(image)
        val paintAlpha = paint.alpha / 255f
        return BitmapShaderPayload(
            texture = texture,
            imageWidth = image.width,
            imageHeight = image.height,
            srcL = 0f, srcT = 0f,
            srcR = image.width.toFloat(), srcB = image.height.toFloat(),
            dstL = dstL, dstT = dstT, dstR = dstR, dstB = dstB,
            paintR = paintAlpha, paintG = paintAlpha,
            paintB = paintAlpha, paintA = paintAlpha,
            filter = shader.getSampling().filter,
            tileX = shader.getTileX(),
            tileY = shader.getTileY(),
            csMode = csMode,
            csMatrix = csMatrix,
        )
    }

    /**
     * G5.2.1 -- materialise a [StencilCoverAaBitmapShaderDraw] from a
     * pre-computed [BitmapShaderPayload] + per-branch path geometry.
     * Centralises the field copy so each of the 3 dispatch branches
     * (multi-contour / inverse-or-concave / convex) stays short.
     */
    private fun BitmapShaderPayload.toStencilCoverDraw(
        stencilVerts: FloatArray,
        coverVerts: FloatArray,
        edges: FloatArray,
        edgeCount: Int,
        scissor: IntArray,
        fillType: SkPathFillType,
        mode: SkBlendMode,
    ): StencilCoverAaBitmapShaderDraw = StencilCoverAaBitmapShaderDraw(
        stencilVerts = stencilVerts,
        coverVerts = coverVerts,
        edges = edges,
        edgeCount = edgeCount,
        scissor = scissor,
        fillType = fillType,
        texture = texture,
        imageWidth = imageWidth, imageHeight = imageHeight,
        srcL = srcL, srcT = srcT, srcR = srcR, srcB = srcB,
        dstL = dstL, dstT = dstT, dstR = dstR, dstB = dstB,
        paintR = paintR, paintG = paintG, paintB = paintB, paintA = paintA,
        filter = filter,
        tileX = tileX, tileY = tileY,
        csMode = csMode,
        csMatrix = csMatrix,
        r = 1f, g = 1f, b = 1f, a = paintA,
        mode = mode,
    )

    private val pending: MutableList<PendingDraw> = mutableListOf()

    private fun loadShader(resource: String): GPUShaderModule {
        val wgsl = SkWebGpuDevice::class.java.classLoader
            .getResource(resource)?.readText()
            ?: error("$resource missing from classpath")
        return context.device.createShaderModule(ShaderModuleDescriptor(code = wgsl))
    }

    // ─── Rect pipeline (G1.2 / G2.3a) — full-screen tri + scissor + coverage ───

    private val rectShader: GPUShaderModule = loadShader("shaders/solid_color.wgsl")

    private val rectBindGroupLayout: GPUBindGroupLayout = context.device.createBindGroupLayout(
        BindGroupLayoutDescriptor(
            entries = listOf(
                BindGroupLayoutEntry(
                    binding = 0u,
                    visibility = GPUShaderStage.Fragment,
                    buffer = BufferBindingLayout(type = GPUBufferBindingType.Uniform),
                ),
            ),
        ),
    )

    private val rectPipelineLayout = context.device.createPipelineLayout(
        PipelineLayoutDescriptor(bindGroupLayouts = listOf(rectBindGroupLayout)),
    )

    /**
     * Lazily-populated rect-pipeline cache, one entry per [SkBlendMode]
     * the caller exercises. All entries share the same vertex / fragment
     * shader, same layout, same target format — only the [BlendState]
     * differs.
     */
    private val rectPipelineCache: MutableMap<SkBlendMode, GPURenderPipeline> = mutableMapOf()

    private fun rectPipelineFor(mode: SkBlendMode): GPURenderPipeline =
        rectPipelineCache.getOrPut(mode) {
            context.device.createRenderPipeline(
                RenderPipelineDescriptor(
                    layout = rectPipelineLayout,
                    vertex = VertexState(module = rectShader, entryPoint = "vs_main"),
                    fragment = FragmentState(
                        module = rectShader,
                        entryPoint = "fs_main",
                        targets = listOf(
                            ColorTargetState(
                                format = intermediateFormat,
                                blend = blendStateFor(mode),
                            ),
                        ),
                    ),
                ),
            )
        }

    // ─── Polygon pipeline (G3.3a) — vertex buffer in device coords ─────────

    /** Shared vertex buffer layout for both the non-AA and AA polygon pipelines. */
    private val POLYGON_VERTEX_LAYOUT: VertexBufferLayout = VertexBufferLayout(
        arrayStride = 8uL, // 2 floats * 4 bytes
        attributes = listOf(
            VertexAttribute(
                shaderLocation = 0u,
                offset = 0uL,
                format = GPUVertexFormat.Float32x2,
            ),
        ),
    )

    private val polygonShader: GPUShaderModule = loadShader("shaders/solid_polygon.wgsl")

    private val polygonBindGroupLayout: GPUBindGroupLayout = context.device.createBindGroupLayout(
        BindGroupLayoutDescriptor(
            entries = listOf(
                // Same fragment-stage uniform layout as the rect pipeline plus
                // the viewport size used by the vertex stage's NDC remap
                // (visibility = Vertex | Fragment).
                BindGroupLayoutEntry(
                    binding = 0u,
                    visibility = GPUShaderStage.Vertex or GPUShaderStage.Fragment,
                    buffer = BufferBindingLayout(type = GPUBufferBindingType.Uniform),
                ),
            ),
        ),
    )

    private val polygonPipelineLayout = context.device.createPipelineLayout(
        PipelineLayoutDescriptor(bindGroupLayouts = listOf(polygonBindGroupLayout)),
    )

    /**
     * Polygon pipeline cache keyed by [SkBlendMode]. Same set of blends
     * as the rect pipeline ; the vertex shader is the differentiator.
     */
    private val polygonPipelineCache: MutableMap<SkBlendMode, GPURenderPipeline> = mutableMapOf()

    private fun polygonPipelineFor(mode: SkBlendMode): GPURenderPipeline =
        polygonPipelineCache.getOrPut(mode) {
            context.device.createRenderPipeline(
                RenderPipelineDescriptor(
                    layout = polygonPipelineLayout,
                    vertex = VertexState(
                        module = polygonShader,
                        entryPoint = "vs_main",
                        buffers = listOf(POLYGON_VERTEX_LAYOUT),
                    ),
                    fragment = FragmentState(
                        module = polygonShader,
                        entryPoint = "fs_main",
                        targets = listOf(
                            ColorTargetState(
                                format = intermediateFormat,
                                blend = blendStateFor(mode),
                            ),
                        ),
                    ),
                ),
            )
        }

    // ─── Stencil & cover (G3.3b.2b) — multi-contour / concave paths ────────

    /**
     * Stencil-write pipeline. Front-face triangles increment the stencil
     * byte (wrap on overflow), back-face triangles decrement. After
     * rendering all of a path's triangles, the stencil buffer carries
     * the **winding count** at each pixel. Color writes are masked off ;
     * the fragment stage runs but its output is discarded.
     */
    private val stencilWritePipeline: GPURenderPipeline = context.device.createRenderPipeline(
        RenderPipelineDescriptor(
            layout = polygonPipelineLayout,
            vertex = VertexState(
                module = polygonShader,
                entryPoint = "vs_main",
                buffers = listOf(POLYGON_VERTEX_LAYOUT),
            ),
            fragment = FragmentState(
                module = polygonShader,
                entryPoint = "fs_main",
                targets = listOf(
                    ColorTargetState(
                        format = intermediateFormat,
                        blend = blendAddBoth(GPUBlendFactor.One, GPUBlendFactor.Zero),
                        writeMask = GPUColorWrite.None,
                    ),
                ),
            ),
            depthStencil = DepthStencilState(
                format = GPUTextureFormat.Depth24PlusStencil8,
                depthWriteEnabled = false,
                depthCompare = GPUCompareFunction.Always,
                stencilFront = StencilFaceState(
                    compare = GPUCompareFunction.Always,
                    failOp = GPUStencilOperation.Keep,
                    depthFailOp = GPUStencilOperation.Keep,
                    passOp = GPUStencilOperation.IncrementWrap,
                ),
                stencilBack = StencilFaceState(
                    compare = GPUCompareFunction.Always,
                    failOp = GPUStencilOperation.Keep,
                    depthFailOp = GPUStencilOperation.Keep,
                    passOp = GPUStencilOperation.DecrementWrap,
                ),
                stencilReadMask = 0xFFu,
                stencilWriteMask = 0xFFu,
            ),
        ),
    )

    /**
     * Cover pipeline cache, one entry per `(blend mode, fill type)`. The
     * fragment stencil-test compares `(stencil & readMask)` against the
     * reference (0, set by `setStencilReference` before drawing) :
     *
     *  - `kWinding`         : `readMask = 0xFF`, compare `NotEqual` 0  -> fill where `count != 0`.
     *  - `kEvenOdd`         : `readMask = 0x01`, compare `NotEqual` 0  -> fill where the low bit is set (count is odd).
     *  - `kInverseWinding`  : `readMask = 0xFF`, compare `Equal` 0     -> fill where `count == 0` (outside the path).
     *  - `kInverseEvenOdd`  : `readMask = 0x01`, compare `Equal` 0     -> fill where the low bit is clear (count is even).
     *
     * Inverse variants pair with a viewport-spanning cover quad (not
     * the path bbox) so the entire clipped device area is rasterised
     * and the stencil decides which fragments belong to the "outside".
     */
    private val coverPipelineCache: MutableMap<Pair<SkBlendMode, SkPathFillType>, GPURenderPipeline> = mutableMapOf()

    private fun coverPipelineFor(mode: SkBlendMode, fillType: SkPathFillType): GPURenderPipeline =
        coverPipelineCache.getOrPut(mode to fillType) {
            val readMask: UInt = if (fillType.isEvenOdd()) 0x01u else 0xFFu
            val compare = if (fillType.isInverse()) GPUCompareFunction.Equal else GPUCompareFunction.NotEqual
            context.device.createRenderPipeline(
                RenderPipelineDescriptor(
                    layout = polygonPipelineLayout,
                    vertex = VertexState(
                        module = polygonShader,
                        entryPoint = "vs_main",
                        buffers = listOf(POLYGON_VERTEX_LAYOUT),
                    ),
                    fragment = FragmentState(
                        module = polygonShader,
                        entryPoint = "fs_main",
                        targets = listOf(
                            ColorTargetState(
                                format = intermediateFormat,
                                blend = blendStateFor(mode),
                            ),
                        ),
                    ),
                    depthStencil = DepthStencilState(
                        format = GPUTextureFormat.Depth24PlusStencil8,
                        depthWriteEnabled = false,
                        depthCompare = GPUCompareFunction.Always,
                        stencilFront = StencilFaceState(
                            compare = compare,
                            failOp = GPUStencilOperation.Keep,
                            depthFailOp = GPUStencilOperation.Keep,
                            passOp = GPUStencilOperation.Keep,
                        ),
                        stencilBack = StencilFaceState(
                            compare = compare,
                            failOp = GPUStencilOperation.Keep,
                            depthFailOp = GPUStencilOperation.Keep,
                            passOp = GPUStencilOperation.Keep,
                        ),
                        stencilReadMask = readMask,
                        stencilWriteMask = 0xFFu,
                    ),
                ),
            )
        }

    // ─── AA stencil-cover (G3.3b.3a / G3.3b.3d) — AA cover for multi-contour ──

    private val aaStencilCoverShader: GPUShaderModule = loadShader("shaders/aa_stencil_cover.wgsl")

    /**
     * G3.3b.3d — which half of the AA falloff a cover sub-draw paints.
     * `Inside` paints fragments the stencil counts as inside the fill
     * region with `coverage = clamp(minDist + 0.5, 0, 1)` ; `Outside`
     * paints fragments the stencil counts as outside with
     * `coverage = clamp(0.5 - minDist, 0, 1)`. Two mutually-exclusive
     * sub-draws over the same cover quad recover the full AA profile.
     */
    private enum class CoverageSide { Inside, Outside }

    private val aaStencilCoverPipelineCache: MutableMap<Triple<SkBlendMode, SkPathFillType, CoverageSide>, GPURenderPipeline> = mutableMapOf()

    /**
     * AA-cover pipeline. Same fill-type encoding as [coverPipelineFor]
     * (readMask + compare op derived from [SkPathFillType]) but the
     * fragment shader produces AA coverage from the path's edge segments.
     * Bind group layout is shared with the single-contour AA pipeline.
     *
     * G3.3b.3d — [side] selects whether this pipeline paints the inside
     * or outside half of the AA boundary. `Outside` flips the stencil
     * compare op (so the same stencil reference 0 gates the opposite set
     * of fragments) and switches to the `fs_outside` shader entry point.
     */
    private fun aaStencilCoverPipelineFor(
        mode: SkBlendMode,
        fillType: SkPathFillType,
        side: CoverageSide,
    ): GPURenderPipeline =
        aaStencilCoverPipelineCache.getOrPut(Triple(mode, fillType, side)) {
            val readMask: UInt = if (fillType.isEvenOdd()) 0x01u else 0xFFu
            val insideCompare =
                if (fillType.isInverse()) GPUCompareFunction.Equal else GPUCompareFunction.NotEqual
            val compare = when (side) {
                CoverageSide.Inside -> insideCompare
                CoverageSide.Outside ->
                    if (insideCompare == GPUCompareFunction.Equal) GPUCompareFunction.NotEqual
                    else GPUCompareFunction.Equal
            }
            val entryPoint = when (side) {
                CoverageSide.Inside -> "fs_inside"
                CoverageSide.Outside -> "fs_outside"
            }
            context.device.createRenderPipeline(
                RenderPipelineDescriptor(
                    layout = aaPolygonPipelineLayout,
                    vertex = VertexState(
                        module = aaStencilCoverShader,
                        entryPoint = "vs_main",
                        buffers = listOf(POLYGON_VERTEX_LAYOUT),
                    ),
                    fragment = FragmentState(
                        module = aaStencilCoverShader,
                        entryPoint = entryPoint,
                        targets = listOf(
                            ColorTargetState(
                                format = intermediateFormat,
                                blend = blendStateFor(mode),
                            ),
                        ),
                    ),
                    depthStencil = DepthStencilState(
                        format = GPUTextureFormat.Depth24PlusStencil8,
                        depthWriteEnabled = false,
                        depthCompare = GPUCompareFunction.Always,
                        stencilFront = StencilFaceState(
                            compare = compare,
                            failOp = GPUStencilOperation.Keep,
                            depthFailOp = GPUStencilOperation.Keep,
                            passOp = GPUStencilOperation.Keep,
                        ),
                        stencilBack = StencilFaceState(
                            compare = compare,
                            failOp = GPUStencilOperation.Keep,
                            depthFailOp = GPUStencilOperation.Keep,
                            passOp = GPUStencilOperation.Keep,
                        ),
                        stencilReadMask = readMask,
                        stencilWriteMask = 0xFFu,
                    ),
                ),
            )
        }

    // ─── Present pass (G6.1) — sRGB→Rec.2020 transform on readback ─────────

    private val presentShader: GPUShaderModule = loadShader(
        if (applyColorspaceTransform) "shaders/present_pass.wgsl"
        else "shaders/present_identity.wgsl",
    )

    private val presentBindGroupLayout: GPUBindGroupLayout = context.device.createBindGroupLayout(
        BindGroupLayoutDescriptor(
            entries = listOf(
                BindGroupLayoutEntry(
                    binding = 0u,
                    visibility = GPUShaderStage.Fragment,
                    texture = TextureBindingLayout(
                        sampleType = GPUTextureSampleType.UnfilterableFloat,
                        viewDimension = GPUTextureViewDimension.TwoD,
                        multisampled = false,
                    ),
                ),
            ),
        ),
    )

    private val presentPipelineLayout = context.device.createPipelineLayout(
        PipelineLayoutDescriptor(bindGroupLayouts = listOf(presentBindGroupLayout)),
    )

    private val presentPipeline: GPURenderPipeline = context.device.createRenderPipeline(
        RenderPipelineDescriptor(
            layout = presentPipelineLayout,
            vertex = VertexState(module = presentShader, entryPoint = "vs_main"),
            fragment = FragmentState(
                module = presentShader,
                entryPoint = "fs_main",
                targets = listOf(
                    ColorTargetState(
                        format = GPUTextureFormat.RGBA8Unorm,
                        // Replace, not blend -- the present pass writes a
                        // fresh colorspace-converted frame.
                        blend = blendAddBoth(GPUBlendFactor.One, GPUBlendFactor.Zero),
                    ),
                ),
            ),
        ),
    )

    /** Persistent bind group : binds [intermediateView] to fragment slot 0. */
    private val presentBindGroup = context.device.createBindGroup(
        BindGroupDescriptor(
            layout = presentBindGroupLayout,
            entries = listOf(
                BindGroupEntry(binding = 0u, resource = intermediateView),
            ),
        ),
    )

    // ─── Bitmap shader (G5.1) — drawImageRect with kLinear / kClamp / SrcOver ──

    private val bitmapShader: GPUShaderModule = loadShader("shaders/bitmap_shader.wgsl")

    /**
     * G5.1 bind group layout :
     *   binding 0 -- uniform buffer (src, dst, image size, paint colour)
     *   binding 1 -- sampled 2D texture (the cached image)
     *   binding 2 -- filtering sampler (kLinear / ClampToEdge)
     * Layout shape mirrors the present pass's texture binding (G6.1)
     * but adds a sampler entry so the shader can lerp.
     */
    private val bitmapBindGroupLayout: GPUBindGroupLayout = context.device.createBindGroupLayout(
        BindGroupLayoutDescriptor(
            entries = listOf(
                BindGroupLayoutEntry(
                    binding = 0u,
                    visibility = GPUShaderStage.Fragment,
                    buffer = BufferBindingLayout(type = GPUBufferBindingType.Uniform),
                ),
                BindGroupLayoutEntry(
                    binding = 1u,
                    visibility = GPUShaderStage.Fragment,
                    texture = TextureBindingLayout(
                        sampleType = GPUTextureSampleType.Float,
                        viewDimension = GPUTextureViewDimension.TwoD,
                        multisampled = false,
                    ),
                ),
                BindGroupLayoutEntry(
                    binding = 2u,
                    visibility = GPUShaderStage.Fragment,
                    sampler = SamplerBindingLayout(type = GPUSamplerBindingType.Filtering),
                ),
            ),
        ),
    )

    private val bitmapPipelineLayout = context.device.createPipelineLayout(
        PipelineLayoutDescriptor(bindGroupLayouts = listOf(bitmapBindGroupLayout)),
    )

    /**
     * Lazily-populated bitmap-shader pipeline cache keyed by
     * `(blend, filter)`. The bitmap shader is single-source (no per-tile
     * entry point ; the tile modes are resolved by the sampler's
     * addressMode and the in-shader kDecal check), so the per-axis tile
     * pair does NOT feed into the pipeline cache — only into the sampler
     * cache key (and the uniform's `tileX/tileY` flag for the decal
     * branch). G5.1 reached here through a single `tile` ordinal, G5.2
     * (paint.shader = SkBitmapShader) widens to (tileX, tileY).
     */
    private val bitmapPipelineCache:
        MutableMap<Pair<SkBlendMode, SkFilterMode>, GPURenderPipeline> = mutableMapOf()

    private fun bitmapPipelineFor(
        mode: SkBlendMode,
        filter: SkFilterMode,
    ): GPURenderPipeline =
        bitmapPipelineCache.getOrPut(mode to filter) {
            context.device.createRenderPipeline(
                RenderPipelineDescriptor(
                    layout = bitmapPipelineLayout,
                    vertex = VertexState(module = bitmapShader, entryPoint = "vs_main"),
                    fragment = FragmentState(
                        module = bitmapShader,
                        entryPoint = "fs_main",
                        targets = listOf(
                            ColorTargetState(
                                format = intermediateFormat,
                                blend = blendStateFor(mode),
                            ),
                        ),
                    ),
                ),
            )
        }

    /**
     * Sampler cache keyed by `(filter, tileX, tileY)`. Per-axis tile mode
     * (G5.2 : SkBitmapShader carries independent `tileX`/`tileY`) ->
     * sampler `addressModeU` / `addressModeV` are set independently. The
     * `kDecal` mode is emulated in-shader (sampler stays ClampToEdge) ;
     * the per-axis decal check lives in `bitmap_shader.wgsl`.
     *
     * Mipmap filter is left at Nearest (no mip pyramid yet ; future
     * slices wire `image.mipLevels` through to a multi-level texture).
     */
    private val bitmapSamplerCache:
        MutableMap<Triple<SkFilterMode, SkTileMode, SkTileMode>, GPUSampler> = mutableMapOf()

    private fun bitmapSamplerFor(
        filter: SkFilterMode,
        tileX: SkTileMode,
        tileY: SkTileMode,
    ): GPUSampler =
        bitmapSamplerCache.getOrPut(Triple(filter, tileX, tileY)) {
            val magMin = when (filter) {
                SkFilterMode.kNearest -> GPUFilterMode.Nearest
                SkFilterMode.kLinear -> GPUFilterMode.Linear
            }
            val addressFor = { tile: SkTileMode -> when (tile) {
                SkTileMode.kClamp -> GPUAddressMode.ClampToEdge
                SkTileMode.kRepeat -> GPUAddressMode.Repeat
                SkTileMode.kMirror -> GPUAddressMode.MirrorRepeat
                SkTileMode.kDecal -> GPUAddressMode.ClampToEdge // decal handled in shader
            } }
            context.device.createSampler(
                SamplerDescriptor(
                    addressModeU = addressFor(tileX),
                    addressModeV = addressFor(tileY),
                    magFilter = magMin,
                    minFilter = magMin,
                    label = "SkWebGpuDevice.bitmapSampler($filter,$tileX,$tileY)",
                ),
            )
        }

    /**
     * G5.1 -- per-device cache mapping each [SkImage] seen by
     * [drawImageRect] to its uploaded `RGBA8Unorm` GPU texture. Keys
     * use **identity** (`IdentityHashMap`) so we don't depend on
     * [SkImage] hashing / equality (which would otherwise hash the
     * `IntArray` pixel buffer on every cache lookup). Closed in [close]
     * along with the rest of the device's GPU resources.
     *
     * The cache lives at the device level (not the context) for the
     * first slice -- multiple devices sharing a context will each
     * re-upload their images on first use. Promoting to a context-scoped
     * weak cache is straightforward but out of scope for G5.1.
     */
    private val imageTextureCache: MutableMap<SkImage, GPUTexture> = java.util.IdentityHashMap()

    /**
     * G5.3 -- compute the (mode, matrix) tuple the bitmap shader needs
     * to lift a sample of [image] into the intermediate target's
     * sRGB-coded working space.
     *
     * The intermediate target convention is **premul sRGB-coded** (sRGB
     * primaries, sRGB OETF -- see [intermediateTexture] kdoc). When
     * [image]'s colorSpace is sRGB the pipeline reduces to identity ;
     * we route the existing fast path (`mode = 0`, identity matrix).
     *
     * For other colorspaces we build the canonical CPU xform via
     * [SkColorSpaceXformSteps] from `image.colorSpace -> sRGB` (both
     * unpremul, since the texture upload keeps the source convention
     * and the shader's premul step happens after the colorspace
     * transform). Three cases are supported by the shader's
     * `CS_MODE_SRGB_TF_MATRIX` branch :
     *  - The source TF is the sRGB TF (Display P3 falls here -- it
     *    shares the sRGB curve, only the primaries matrix differs).
     *  - The destination TF is the sRGB TF (always true ; the working
     *    space encodes via sRGB OETF).
     *  - The gamut transform is exactly the matrix we ship to the
     *    shader (column-major 3x3, scaleFactor = 1 because no HDR).
     *
     * Any other source colorspace (linear Rec.2020, Adobe RGB, PQ /
     * HLG, ...) returns `mode = 0` -- the existing as-uploaded sRGB
     * fast path -- with a kdoc warning. Wiring those up means adding
     * a TF-coefficient slot to the uniform, which is out of scope
     * for G5.3.
     */
    private fun bitmapColorSpaceFor(image: SkImage): Pair<Int, FloatArray> {
        val cs = image.colorSpace
        val dst = org.skia.foundation.SkColorSpace.makeSRGB()
        if (cs.hash() == dst.hash()) {
            return 0 to IDENTITY_CS_MATRIX
        }
        // Only the sRGB-TF + non-sRGB-gamut case is wired up. Detect by
        // hashing the TF against sRGB's TF hash : same TF means we can
        // reuse the shader's hardcoded sRGB EOTF / OETF and only ship
        // the primaries matrix.
        val srgbTfHash = dst.transferFnHash
        if (cs.transferFnHash != srgbTfHash) {
            return 0 to IDENTITY_CS_MATRIX
        }
        val steps = org.skia.core.SkColorSpaceXformSteps(
            cs, org.skia.core.SkAlphaType.kUnpremul,
            dst, org.skia.core.SkAlphaType.kUnpremul,
        )
        if (steps.flags.isIdentity) {
            return 0 to IDENTITY_CS_MATRIX
        }
        // The xform steps' matrix is column-major already (see KDoc on
        // `SkColorSpaceXformSteps.srcToDstMatrix`) ; copy it verbatim.
        return 1 to steps.srcToDstMatrix.copyOf()
    }

    /**
     * Upload [image]'s 8888 pixels into a fresh `RGBA8Unorm` GPU texture
     * (or return the cached texture if [image] has already been uploaded).
     *
     * [SkImage.pixels] stores 32-bit packed `SkColor` (ARGB byte order
     * in the int : alpha in the top byte). The WebGPU `RGBA8Unorm`
     * texture expects R, G, B, A in that byte order. We unpack each
     * pixel into a `ByteArray` once, then upload via
     * `queue.writeTexture`. The texture is uploaded **unpremultiplied,
     * source-encoded** -- the bitmap-shader fragment stage applies the
     * colorspace transform (G5.3) then premultiplies after the
     * bilinear sample (so the lerp itself runs on unpremul values,
     * matching `SkBitmapShader.sampleLinear` on the raster side).
     */
    private fun imageTextureFor(image: SkImage): GPUTexture =
        imageTextureCache.getOrPut(image) {
            val w = image.width
            val h = image.height
            val tex = context.device.createTexture(
                TextureDescriptor(
                    size = Extent3D(width = w.toUInt(), height = h.toUInt()),
                    format = GPUTextureFormat.RGBA8Unorm,
                    usage = GPUTextureUsage.TextureBinding or GPUTextureUsage.CopyDst,
                    label = "SkWebGpuDevice.image(${w}x${h})",
                ),
            )
            // Unpack ARGB ints to RGBA bytes for the texture upload.
            val bytes = ByteArray(w * h * 4)
            val src = image.pixels
            for (i in 0 until w * h) {
                val c = src[i]
                bytes[i * 4]     = SkColorGetR(c).toByte()
                bytes[i * 4 + 1] = SkColorGetG(c).toByte()
                bytes[i * 4 + 2] = SkColorGetB(c).toByte()
                bytes[i * 4 + 3] = SkColorGetA(c).toByte()
            }
            context.queue.writeTexture(
                destination = TexelCopyTextureInfo(texture = tex),
                data = ArrayBuffer.of(bytes),
                dataLayout = TexelCopyBufferLayout(
                    offset = 0uL,
                    bytesPerRow = (w * 4).toUInt(),
                    rowsPerImage = h.toUInt(),
                ),
                size = Extent3D(width = w.toUInt(), height = h.toUInt()),
            )
            tex
        }

    // ─── AA polygon pipeline (G3.3b.2a) — per-fragment edge coverage ──────

    private val aaPolygonShader: GPUShaderModule = loadShader("shaders/aa_polygon.wgsl")

    private val aaPolygonBindGroupLayout: GPUBindGroupLayout = context.device.createBindGroupLayout(
        BindGroupLayoutDescriptor(
            entries = listOf(
                BindGroupLayoutEntry(
                    binding = 0u,
                    visibility = GPUShaderStage.Vertex or GPUShaderStage.Fragment,
                    buffer = BufferBindingLayout(type = GPUBufferBindingType.Uniform),
                ),
            ),
        ),
    )

    private val aaPolygonPipelineLayout = context.device.createPipelineLayout(
        PipelineLayoutDescriptor(bindGroupLayouts = listOf(aaPolygonBindGroupLayout)),
    )

    private val aaPolygonPipelineCache: MutableMap<SkBlendMode, GPURenderPipeline> = mutableMapOf()

    private fun aaPolygonPipelineFor(mode: SkBlendMode): GPURenderPipeline =
        aaPolygonPipelineCache.getOrPut(mode) {
            context.device.createRenderPipeline(
                RenderPipelineDescriptor(
                    layout = aaPolygonPipelineLayout,
                    vertex = VertexState(
                        module = aaPolygonShader,
                        entryPoint = "vs_main",
                        buffers = listOf(POLYGON_VERTEX_LAYOUT),
                    ),
                    fragment = FragmentState(
                        module = aaPolygonShader,
                        entryPoint = "fs_main",
                        targets = listOf(
                            ColorTargetState(
                                format = intermediateFormat,
                                blend = blendStateFor(mode),
                            ),
                        ),
                    ),
                ),
            )
        }

    // ─── Linear gradient pipeline (G4.1) — kClamp tile mode, drawRect ──────

    private val linearGradientShader: GPUShaderModule = loadShader("shaders/linear_gradient.wgsl")

    private val linearGradientBindGroupLayout: GPUBindGroupLayout = context.device.createBindGroupLayout(
        BindGroupLayoutDescriptor(
            entries = listOf(
                BindGroupLayoutEntry(
                    binding = 0u,
                    visibility = GPUShaderStage.Fragment,
                    buffer = BufferBindingLayout(type = GPUBufferBindingType.Uniform),
                ),
            ),
        ),
    )

    private val linearGradientPipelineLayout = context.device.createPipelineLayout(
        PipelineLayoutDescriptor(bindGroupLayouts = listOf(linearGradientBindGroupLayout)),
    )

    /**
     * Lazily-populated linear-gradient-pipeline cache, one entry per
     * (blend, tile) pair the caller exercises. G4.1.1 widened the key
     * from `SkBlendMode` to `Pair<SkBlendMode, SkTileMode>` so the 4
     * shader entry points (fs_clamp / fs_repeat / fs_mirror / fs_decal)
     * each get their own pipeline. Gradient stops still vary per draw
     * via the uniform buffer ; cache key only covers state that lives
     * on the pipeline itself.
     */
    private val linearGradientPipelineCache:
        MutableMap<Pair<SkBlendMode, SkTileMode>, GPURenderPipeline> = mutableMapOf()

    private fun linearGradientFragmentEntryPoint(tileMode: SkTileMode): String = when (tileMode) {
        SkTileMode.kClamp -> "fs_clamp"
        SkTileMode.kRepeat -> "fs_repeat"
        SkTileMode.kMirror -> "fs_mirror"
        SkTileMode.kDecal -> "fs_decal"
    }

    private fun linearGradientPipelineFor(
        mode: SkBlendMode,
        tileMode: SkTileMode,
    ): GPURenderPipeline =
        linearGradientPipelineCache.getOrPut(mode to tileMode) {
            context.device.createRenderPipeline(
                RenderPipelineDescriptor(
                    layout = linearGradientPipelineLayout,
                    vertex = VertexState(module = linearGradientShader, entryPoint = "vs_main"),
                    fragment = FragmentState(
                        module = linearGradientShader,
                        entryPoint = linearGradientFragmentEntryPoint(tileMode),
                        targets = listOf(
                            ColorTargetState(
                                format = intermediateFormat,
                                blend = blendStateFor(mode),
                            ),
                        ),
                    ),
                ),
            )
        }

    // ─── Radial gradient pipeline (G4.2) — kClamp tile mode, drawRect ──────

    private val radialGradientShader: GPUShaderModule = loadShader("shaders/radial_gradient.wgsl")

    private val radialGradientBindGroupLayout: GPUBindGroupLayout = context.device.createBindGroupLayout(
        BindGroupLayoutDescriptor(
            entries = listOf(
                BindGroupLayoutEntry(
                    binding = 0u,
                    visibility = GPUShaderStage.Fragment,
                    buffer = BufferBindingLayout(type = GPUBufferBindingType.Uniform),
                ),
            ),
        ),
    )

    private val radialGradientPipelineLayout = context.device.createPipelineLayout(
        PipelineLayoutDescriptor(bindGroupLayouts = listOf(radialGradientBindGroupLayout)),
    )

    /**
     * G4.2 -- radial-gradient pipeline cache. Keyed by `(blend, tile)` from
     * day 1 even though only kClamp is wired, so the G4.2.1 follow-up
     * adding `fs_repeat / fs_mirror / fs_decal` to `radial_gradient.wgsl`
     * is a strict superset of this change (no cache-key migration).
     */
    private val radialGradientPipelineCache:
        MutableMap<Pair<SkBlendMode, SkTileMode>, GPURenderPipeline> = mutableMapOf()

    private fun radialGradientFragmentEntryPoint(tileMode: SkTileMode): String = when (tileMode) {
        SkTileMode.kClamp -> "fs_clamp"
        SkTileMode.kRepeat -> "fs_repeat"
        SkTileMode.kMirror -> "fs_mirror"
        SkTileMode.kDecal -> "fs_decal"
    }

    private fun radialGradientPipelineFor(
        mode: SkBlendMode,
        tileMode: SkTileMode,
    ): GPURenderPipeline =
        radialGradientPipelineCache.getOrPut(mode to tileMode) {
            context.device.createRenderPipeline(
                RenderPipelineDescriptor(
                    layout = radialGradientPipelineLayout,
                    vertex = VertexState(module = radialGradientShader, entryPoint = "vs_main"),
                    fragment = FragmentState(
                        module = radialGradientShader,
                        entryPoint = radialGradientFragmentEntryPoint(tileMode),
                        targets = listOf(
                            ColorTargetState(
                                format = intermediateFormat,
                                blend = blendStateFor(mode),
                            ),
                        ),
                    ),
                ),
            )
        }

    // ─── Sweep gradient pipeline (G4.3 / G4.3.1) — all 4 tile modes, drawRect ──

    private val sweepGradientShader: GPUShaderModule = loadShader("shaders/sweep_gradient.wgsl")

    private val sweepGradientBindGroupLayout: GPUBindGroupLayout = context.device.createBindGroupLayout(
        BindGroupLayoutDescriptor(
            entries = listOf(
                BindGroupLayoutEntry(
                    binding = 0u,
                    visibility = GPUShaderStage.Fragment,
                    buffer = BufferBindingLayout(type = GPUBufferBindingType.Uniform),
                ),
            ),
        ),
    )

    private val sweepGradientPipelineLayout = context.device.createPipelineLayout(
        PipelineLayoutDescriptor(bindGroupLayouts = listOf(sweepGradientBindGroupLayout)),
    )

    /**
     * G4.3 / G4.3.1 -- sweep-gradient pipeline cache. Keyed by `(blend,
     * tile)` mirroring the linear / radial caches. G4.3 wired only kClamp
     * through the dispatch ; G4.3.1 widened the gate to fs_repeat /
     * fs_mirror / fs_decal without touching this cache shape (all 4 entry
     * points were already in place since G4.3 for cache readiness).
     */
    private val sweepGradientPipelineCache:
        MutableMap<Pair<SkBlendMode, SkTileMode>, GPURenderPipeline> = mutableMapOf()

    private fun sweepGradientFragmentEntryPoint(tileMode: SkTileMode): String = when (tileMode) {
        SkTileMode.kClamp -> "fs_clamp"
        SkTileMode.kRepeat -> "fs_repeat"
        SkTileMode.kMirror -> "fs_mirror"
        SkTileMode.kDecal -> "fs_decal"
    }

    private fun sweepGradientPipelineFor(
        mode: SkBlendMode,
        tileMode: SkTileMode,
    ): GPURenderPipeline =
        sweepGradientPipelineCache.getOrPut(mode to tileMode) {
            context.device.createRenderPipeline(
                RenderPipelineDescriptor(
                    layout = sweepGradientPipelineLayout,
                    vertex = VertexState(module = sweepGradientShader, entryPoint = "vs_main"),
                    fragment = FragmentState(
                        module = sweepGradientShader,
                        entryPoint = sweepGradientFragmentEntryPoint(tileMode),
                        targets = listOf(
                            ColorTargetState(
                                format = intermediateFormat,
                                blend = blendStateFor(mode),
                            ),
                        ),
                    ),
                ),
            )
        }

    // ─── Conical gradient pipeline (G4.4 / G4.4.2) — kRadial sub-case, all 4 tile modes, drawRect ──

    private val conicalGradientShader: GPUShaderModule =
        loadShader("shaders/conical_gradient.wgsl")

    private val conicalGradientBindGroupLayout: GPUBindGroupLayout = context.device.createBindGroupLayout(
        BindGroupLayoutDescriptor(
            entries = listOf(
                BindGroupLayoutEntry(
                    binding = 0u,
                    visibility = GPUShaderStage.Fragment,
                    buffer = BufferBindingLayout(type = GPUBufferBindingType.Uniform),
                ),
            ),
        ),
    )

    private val conicalGradientPipelineLayout = context.device.createPipelineLayout(
        PipelineLayoutDescriptor(bindGroupLayouts = listOf(conicalGradientBindGroupLayout)),
    )

    /**
     * G4.4 / G4.4.2 -- conical-gradient pipeline cache. Keyed by
     * `(blend, tile)` ; all 4 fragment entry points (`fs_clamp /
     * fs_repeat / fs_mirror / fs_decal`) are reachable as of G4.4.2
     * (mirrors the radial / sweep cache layouts).
     *
     * The pipeline itself doesn't distinguish between conical sub-cases ;
     * the host only routes the kRadial sub-case here today (see the
     * dispatch gate in [drawPath]). A future kStrip pipeline can share
     * this cache key with a different shader file (or extend
     * `conical_gradient.wgsl` with a typeFlag uniform -- the
     * bind-group-layout shape stays identical).
     */
    private val conicalGradientPipelineCache:
        MutableMap<Pair<SkBlendMode, SkTileMode>, GPURenderPipeline> = mutableMapOf()

    private fun conicalGradientFragmentEntryPoint(tileMode: SkTileMode): String = when (tileMode) {
        SkTileMode.kClamp -> "fs_clamp"
        SkTileMode.kRepeat -> "fs_repeat"
        SkTileMode.kMirror -> "fs_mirror"
        SkTileMode.kDecal -> "fs_decal"
    }

    private fun conicalGradientPipelineFor(
        mode: SkBlendMode,
        tileMode: SkTileMode,
    ): GPURenderPipeline =
        conicalGradientPipelineCache.getOrPut(mode to tileMode) {
            context.device.createRenderPipeline(
                RenderPipelineDescriptor(
                    layout = conicalGradientPipelineLayout,
                    vertex = VertexState(module = conicalGradientShader, entryPoint = "vs_main"),
                    fragment = FragmentState(
                        module = conicalGradientShader,
                        entryPoint = conicalGradientFragmentEntryPoint(tileMode),
                        targets = listOf(
                            ColorTargetState(
                                format = intermediateFormat,
                                blend = blendStateFor(mode),
                            ),
                        ),
                    ),
                ),
            )
        }

    // ─── Conical focal-inside pipeline (G4.4.1 / G4.4.2) — drawRect, all 4 tile modes ──

    private val conicalFocalGradientShader: GPUShaderModule =
        loadShader("shaders/conical_focal_gradient.wgsl")

    private val conicalFocalGradientBindGroupLayout: GPUBindGroupLayout = context.device.createBindGroupLayout(
        BindGroupLayoutDescriptor(
            entries = listOf(
                BindGroupLayoutEntry(
                    binding = 0u,
                    visibility = GPUShaderStage.Fragment,
                    buffer = BufferBindingLayout(type = GPUBufferBindingType.Uniform),
                ),
            ),
        ),
    )

    private val conicalFocalGradientPipelineLayout = context.device.createPipelineLayout(
        PipelineLayoutDescriptor(bindGroupLayouts = listOf(conicalFocalGradientBindGroupLayout)),
    )

    /**
     * G4.4.1 / G4.4.2 -- conical focal-inside pipeline cache. Keyed by
     * `(blend, tile)` ; all 4 fragment entry points are reachable as of
     * G4.4.2, mirroring the cache layout of the kRadial conical
     * pipeline.
     */
    private val conicalFocalGradientPipelineCache:
        MutableMap<Pair<SkBlendMode, SkTileMode>, GPURenderPipeline> = mutableMapOf()

    private fun conicalFocalGradientFragmentEntryPoint(tileMode: SkTileMode): String = when (tileMode) {
        SkTileMode.kClamp -> "fs_clamp"
        SkTileMode.kRepeat -> "fs_repeat"
        SkTileMode.kMirror -> "fs_mirror"
        SkTileMode.kDecal -> "fs_decal"
    }

    private fun conicalFocalGradientPipelineFor(
        mode: SkBlendMode,
        tileMode: SkTileMode,
    ): GPURenderPipeline =
        conicalFocalGradientPipelineCache.getOrPut(mode to tileMode) {
            context.device.createRenderPipeline(
                RenderPipelineDescriptor(
                    layout = conicalFocalGradientPipelineLayout,
                    vertex = VertexState(module = conicalFocalGradientShader, entryPoint = "vs_main"),
                    fragment = FragmentState(
                        module = conicalFocalGradientShader,
                        entryPoint = conicalFocalGradientFragmentEntryPoint(tileMode),
                        targets = listOf(
                            ColorTargetState(
                                format = intermediateFormat,
                                blend = blendStateFor(mode),
                            ),
                        ),
                    ),
                ),
            )
        }

    // ─── Conical kStrip pipeline (G4.4.4) — drawRect, all 4 tile modes ──

    private val conicalStripGradientShader: GPUShaderModule =
        loadShader("shaders/conical_strip_gradient.wgsl")

    private val conicalStripGradientBindGroupLayout: GPUBindGroupLayout = context.device.createBindGroupLayout(
        BindGroupLayoutDescriptor(
            entries = listOf(
                BindGroupLayoutEntry(
                    binding = 0u,
                    visibility = GPUShaderStage.Fragment,
                    buffer = BufferBindingLayout(type = GPUBufferBindingType.Uniform),
                ),
            ),
        ),
    )

    private val conicalStripGradientPipelineLayout = context.device.createPipelineLayout(
        PipelineLayoutDescriptor(bindGroupLayouts = listOf(conicalStripGradientBindGroupLayout)),
    )

    /**
     * G4.4.4 -- conical kStrip pipeline cache. Keyed by `(blend, tile)` ;
     * all 4 fragment entry points are reachable. Mirrors the cache shape
     * of the kRadial / focal-inside conical pipelines.
     */
    private val conicalStripGradientPipelineCache:
        MutableMap<Pair<SkBlendMode, SkTileMode>, GPURenderPipeline> = mutableMapOf()

    private fun conicalStripGradientFragmentEntryPoint(tileMode: SkTileMode): String = when (tileMode) {
        SkTileMode.kClamp -> "fs_clamp"
        SkTileMode.kRepeat -> "fs_repeat"
        SkTileMode.kMirror -> "fs_mirror"
        SkTileMode.kDecal -> "fs_decal"
    }

    private fun conicalStripGradientPipelineFor(
        mode: SkBlendMode,
        tileMode: SkTileMode,
    ): GPURenderPipeline =
        conicalStripGradientPipelineCache.getOrPut(mode to tileMode) {
            context.device.createRenderPipeline(
                RenderPipelineDescriptor(
                    layout = conicalStripGradientPipelineLayout,
                    vertex = VertexState(module = conicalStripGradientShader, entryPoint = "vs_main"),
                    fragment = FragmentState(
                        module = conicalStripGradientShader,
                        entryPoint = conicalStripGradientFragmentEntryPoint(tileMode),
                        targets = listOf(
                            ColorTargetState(
                                format = intermediateFormat,
                                blend = blendStateFor(mode),
                            ),
                        ),
                    ),
                ),
            )
        }

    // ─── AA stencil-cover gradient pipeline (G4.1.2) — linear gradient on non-rect paths ──

    private val aaStencilCoverGradientShader: GPUShaderModule =
        loadShader("shaders/aa_stencil_cover_gradient.wgsl")

    /**
     * G4.1.2 -- pipeline cache for the AA stencil-and-cover cover pass
     * carrying a linear-gradient fragment shader. Keyed on
     * `(blend, fillType, tileMode, side)` :
     *  - `tileMode` picks the fragment entry point (fs_*_clamp / repeat /
     *    mirror / decal) ;
     *  - `side` picks inside-cover vs outside-cover (inside / outside
     *    fragment entries with opposite stencil compare ops) ;
     *  - `fillType` derives the stencil readMask + compare exactly like
     *    [aaStencilCoverPipelineFor] (winding / evenodd / inverse) ;
     *  - `blend` derives the [BlendState] via [blendStateFor].
     *
     * Shares [aaPolygonPipelineLayout] with the existing AA pipelines :
     * the bind-group-layout shape is identical (one uniform binding,
     * Vertex|Fragment visibility) ; only the bound buffer's size grows.
     */
    private data class AaStencilCoverGradientKey(
        val mode: SkBlendMode,
        val fillType: SkPathFillType,
        val tileMode: SkTileMode,
        val side: CoverageSide,
    )

    private val aaStencilCoverGradientPipelineCache:
        MutableMap<AaStencilCoverGradientKey, GPURenderPipeline> = mutableMapOf()

    private fun aaStencilCoverGradientFragmentEntryPoint(
        side: CoverageSide,
        tileMode: SkTileMode,
    ): String = when (side) {
        CoverageSide.Inside -> when (tileMode) {
            SkTileMode.kClamp -> "fs_inside_clamp"
            SkTileMode.kRepeat -> "fs_inside_repeat"
            SkTileMode.kMirror -> "fs_inside_mirror"
            SkTileMode.kDecal -> "fs_inside_decal"
        }
        CoverageSide.Outside -> when (tileMode) {
            SkTileMode.kClamp -> "fs_outside_clamp"
            SkTileMode.kRepeat -> "fs_outside_repeat"
            SkTileMode.kMirror -> "fs_outside_mirror"
            SkTileMode.kDecal -> "fs_outside_decal"
        }
    }

    private fun aaStencilCoverGradientPipelineFor(
        mode: SkBlendMode,
        fillType: SkPathFillType,
        tileMode: SkTileMode,
        side: CoverageSide,
    ): GPURenderPipeline =
        aaStencilCoverGradientPipelineCache.getOrPut(
            AaStencilCoverGradientKey(mode, fillType, tileMode, side),
        ) {
            val readMask: UInt = if (fillType.isEvenOdd()) 0x01u else 0xFFu
            val insideCompare =
                if (fillType.isInverse()) GPUCompareFunction.Equal else GPUCompareFunction.NotEqual
            val compare = when (side) {
                CoverageSide.Inside -> insideCompare
                CoverageSide.Outside ->
                    if (insideCompare == GPUCompareFunction.Equal) GPUCompareFunction.NotEqual
                    else GPUCompareFunction.Equal
            }
            val entryPoint = aaStencilCoverGradientFragmentEntryPoint(side, tileMode)
            context.device.createRenderPipeline(
                RenderPipelineDescriptor(
                    layout = aaPolygonPipelineLayout,
                    vertex = VertexState(
                        module = aaStencilCoverGradientShader,
                        entryPoint = "vs_main",
                        buffers = listOf(POLYGON_VERTEX_LAYOUT),
                    ),
                    fragment = FragmentState(
                        module = aaStencilCoverGradientShader,
                        entryPoint = entryPoint,
                        targets = listOf(
                            ColorTargetState(
                                format = intermediateFormat,
                                blend = blendStateFor(mode),
                            ),
                        ),
                    ),
                    depthStencil = DepthStencilState(
                        format = GPUTextureFormat.Depth24PlusStencil8,
                        depthWriteEnabled = false,
                        depthCompare = GPUCompareFunction.Always,
                        stencilFront = StencilFaceState(
                            compare = compare,
                            failOp = GPUStencilOperation.Keep,
                            depthFailOp = GPUStencilOperation.Keep,
                            passOp = GPUStencilOperation.Keep,
                        ),
                        stencilBack = StencilFaceState(
                            compare = compare,
                            failOp = GPUStencilOperation.Keep,
                            depthFailOp = GPUStencilOperation.Keep,
                            passOp = GPUStencilOperation.Keep,
                        ),
                        stencilReadMask = readMask,
                        stencilWriteMask = 0xFFu,
                    ),
                ),
            )
        }

    // ─── AA stencil-cover radial-gradient pipeline (G4.2.2) — radial on non-rect paths ──

    private val aaStencilCoverRadialGradientShader: GPUShaderModule =
        loadShader("shaders/aa_stencil_cover_radial_gradient.wgsl")

    /**
     * G4.2.2 -- pipeline cache for the AA stencil-and-cover cover pass
     * carrying a radial-gradient fragment shader. Mirror of
     * [aaStencilCoverGradientPipelineCache] : same `(blend, fillType,
     * tileMode, side)` key shape, same selection logic for the stencil
     * compare-op + readMask. Shares [aaPolygonPipelineLayout] with the
     * linear non-rect gradient pipeline -- bind-group-layout shape is
     * identical (one uniform binding, Vertex|Fragment visibility) ;
     * only the bound buffer's contents differ.
     */
    private data class AaStencilCoverRadialGradientKey(
        val mode: SkBlendMode,
        val fillType: SkPathFillType,
        val tileMode: SkTileMode,
        val side: CoverageSide,
    )

    private val aaStencilCoverRadialGradientPipelineCache:
        MutableMap<AaStencilCoverRadialGradientKey, GPURenderPipeline> = mutableMapOf()

    private fun aaStencilCoverRadialGradientFragmentEntryPoint(
        side: CoverageSide,
        tileMode: SkTileMode,
    ): String = when (side) {
        CoverageSide.Inside -> when (tileMode) {
            SkTileMode.kClamp -> "fs_inside_clamp"
            SkTileMode.kRepeat -> "fs_inside_repeat"
            SkTileMode.kMirror -> "fs_inside_mirror"
            SkTileMode.kDecal -> "fs_inside_decal"
        }
        CoverageSide.Outside -> when (tileMode) {
            SkTileMode.kClamp -> "fs_outside_clamp"
            SkTileMode.kRepeat -> "fs_outside_repeat"
            SkTileMode.kMirror -> "fs_outside_mirror"
            SkTileMode.kDecal -> "fs_outside_decal"
        }
    }

    private fun aaStencilCoverRadialGradientPipelineFor(
        mode: SkBlendMode,
        fillType: SkPathFillType,
        tileMode: SkTileMode,
        side: CoverageSide,
    ): GPURenderPipeline =
        aaStencilCoverRadialGradientPipelineCache.getOrPut(
            AaStencilCoverRadialGradientKey(mode, fillType, tileMode, side),
        ) {
            val readMask: UInt = if (fillType.isEvenOdd()) 0x01u else 0xFFu
            val insideCompare =
                if (fillType.isInverse()) GPUCompareFunction.Equal else GPUCompareFunction.NotEqual
            val compare = when (side) {
                CoverageSide.Inside -> insideCompare
                CoverageSide.Outside ->
                    if (insideCompare == GPUCompareFunction.Equal) GPUCompareFunction.NotEqual
                    else GPUCompareFunction.Equal
            }
            val entryPoint = aaStencilCoverRadialGradientFragmentEntryPoint(side, tileMode)
            context.device.createRenderPipeline(
                RenderPipelineDescriptor(
                    layout = aaPolygonPipelineLayout,
                    vertex = VertexState(
                        module = aaStencilCoverRadialGradientShader,
                        entryPoint = "vs_main",
                        buffers = listOf(POLYGON_VERTEX_LAYOUT),
                    ),
                    fragment = FragmentState(
                        module = aaStencilCoverRadialGradientShader,
                        entryPoint = entryPoint,
                        targets = listOf(
                            ColorTargetState(
                                format = intermediateFormat,
                                blend = blendStateFor(mode),
                            ),
                        ),
                    ),
                    depthStencil = DepthStencilState(
                        format = GPUTextureFormat.Depth24PlusStencil8,
                        depthWriteEnabled = false,
                        depthCompare = GPUCompareFunction.Always,
                        stencilFront = StencilFaceState(
                            compare = compare,
                            failOp = GPUStencilOperation.Keep,
                            depthFailOp = GPUStencilOperation.Keep,
                            passOp = GPUStencilOperation.Keep,
                        ),
                        stencilBack = StencilFaceState(
                            compare = compare,
                            failOp = GPUStencilOperation.Keep,
                            depthFailOp = GPUStencilOperation.Keep,
                            passOp = GPUStencilOperation.Keep,
                        ),
                        stencilReadMask = readMask,
                        stencilWriteMask = 0xFFu,
                    ),
                ),
            )
        }

    // ─── Sweep gradient on non-rect (G4.3.2) ─────────────────────────────
    //
    // AA stencil-and-cover cover-pass pipeline carrying a sweep-gradient
    // fragment shader. Mirror of [aaStencilCoverGradientPipelineCache]
    // (linear) and [aaStencilCoverRadialGradientPipelineCache] (radial) :
    // same `(blend, fillType, tileMode, side)` key shape, same selection
    // logic for the stencil compare-op + readMask. Shares
    // [aaPolygonPipelineLayout] -- bind-group-layout shape is identical
    // (one uniform binding, Vertex|Fragment visibility) ; only the bound
    // buffer's contents differ.

    private val aaStencilCoverSweepGradientShader: GPUShaderModule =
        loadShader("shaders/aa_stencil_cover_sweep_gradient.wgsl")

    private data class AaStencilCoverSweepGradientKey(
        val mode: SkBlendMode,
        val fillType: SkPathFillType,
        val tileMode: SkTileMode,
        val side: CoverageSide,
    )

    private val aaStencilCoverSweepGradientPipelineCache:
        MutableMap<AaStencilCoverSweepGradientKey, GPURenderPipeline> = mutableMapOf()

    private fun aaStencilCoverSweepGradientFragmentEntryPoint(
        side: CoverageSide,
        tileMode: SkTileMode,
    ): String = when (side) {
        CoverageSide.Inside -> when (tileMode) {
            SkTileMode.kClamp -> "fs_inside_clamp"
            SkTileMode.kRepeat -> "fs_inside_repeat"
            SkTileMode.kMirror -> "fs_inside_mirror"
            SkTileMode.kDecal -> "fs_inside_decal"
        }
        CoverageSide.Outside -> when (tileMode) {
            SkTileMode.kClamp -> "fs_outside_clamp"
            SkTileMode.kRepeat -> "fs_outside_repeat"
            SkTileMode.kMirror -> "fs_outside_mirror"
            SkTileMode.kDecal -> "fs_outside_decal"
        }
    }

    private fun aaStencilCoverSweepGradientPipelineFor(
        mode: SkBlendMode,
        fillType: SkPathFillType,
        tileMode: SkTileMode,
        side: CoverageSide,
    ): GPURenderPipeline =
        aaStencilCoverSweepGradientPipelineCache.getOrPut(
            AaStencilCoverSweepGradientKey(mode, fillType, tileMode, side),
        ) {
            val readMask: UInt = if (fillType.isEvenOdd()) 0x01u else 0xFFu
            val insideCompare =
                if (fillType.isInverse()) GPUCompareFunction.Equal else GPUCompareFunction.NotEqual
            val compare = when (side) {
                CoverageSide.Inside -> insideCompare
                CoverageSide.Outside ->
                    if (insideCompare == GPUCompareFunction.Equal) GPUCompareFunction.NotEqual
                    else GPUCompareFunction.Equal
            }
            val entryPoint = aaStencilCoverSweepGradientFragmentEntryPoint(side, tileMode)
            context.device.createRenderPipeline(
                RenderPipelineDescriptor(
                    layout = aaPolygonPipelineLayout,
                    vertex = VertexState(
                        module = aaStencilCoverSweepGradientShader,
                        entryPoint = "vs_main",
                        buffers = listOf(POLYGON_VERTEX_LAYOUT),
                    ),
                    fragment = FragmentState(
                        module = aaStencilCoverSweepGradientShader,
                        entryPoint = entryPoint,
                        targets = listOf(
                            ColorTargetState(
                                format = intermediateFormat,
                                blend = blendStateFor(mode),
                            ),
                        ),
                    ),
                    depthStencil = DepthStencilState(
                        format = GPUTextureFormat.Depth24PlusStencil8,
                        depthWriteEnabled = false,
                        depthCompare = GPUCompareFunction.Always,
                        stencilFront = StencilFaceState(
                            compare = compare,
                            failOp = GPUStencilOperation.Keep,
                            depthFailOp = GPUStencilOperation.Keep,
                            passOp = GPUStencilOperation.Keep,
                        ),
                        stencilBack = StencilFaceState(
                            compare = compare,
                            failOp = GPUStencilOperation.Keep,
                            depthFailOp = GPUStencilOperation.Keep,
                            passOp = GPUStencilOperation.Keep,
                        ),
                        stencilReadMask = readMask,
                        stencilWriteMask = 0xFFu,
                    ),
                ),
            )
        }

    // ─── Conical gradient on non-rect (G4.4.3) ─────────────────────────────
    //
    // Two pipelines mirror the rect-only split (kRadial vs focal-inside) :
    // each loads a dedicated shader (uniform shape differs ; the focal
    // variant carries the 2x3 affine + focal scalars + flags). Both share
    // [aaPolygonPipelineLayout] with the linear / radial / sweep stencil-
    // cover gradient pipelines -- bind-group-layout shape is identical
    // (one uniform binding, Vertex|Fragment visibility) ; only the bound
    // buffer's contents differ.

    private val aaStencilCoverConicalGradientShader: GPUShaderModule =
        loadShader("shaders/aa_stencil_cover_conical_gradient.wgsl")

    private data class AaStencilCoverConicalGradientKey(
        val mode: SkBlendMode,
        val fillType: SkPathFillType,
        val tileMode: SkTileMode,
        val side: CoverageSide,
    )

    private val aaStencilCoverConicalGradientPipelineCache:
        MutableMap<AaStencilCoverConicalGradientKey, GPURenderPipeline> = mutableMapOf()

    private fun aaStencilCoverConicalGradientFragmentEntryPoint(
        side: CoverageSide,
        tileMode: SkTileMode,
    ): String = when (side) {
        CoverageSide.Inside -> when (tileMode) {
            SkTileMode.kClamp -> "fs_inside_clamp"
            SkTileMode.kRepeat -> "fs_inside_repeat"
            SkTileMode.kMirror -> "fs_inside_mirror"
            SkTileMode.kDecal -> "fs_inside_decal"
        }
        CoverageSide.Outside -> when (tileMode) {
            SkTileMode.kClamp -> "fs_outside_clamp"
            SkTileMode.kRepeat -> "fs_outside_repeat"
            SkTileMode.kMirror -> "fs_outside_mirror"
            SkTileMode.kDecal -> "fs_outside_decal"
        }
    }

    private fun aaStencilCoverConicalGradientPipelineFor(
        mode: SkBlendMode,
        fillType: SkPathFillType,
        tileMode: SkTileMode,
        side: CoverageSide,
    ): GPURenderPipeline =
        aaStencilCoverConicalGradientPipelineCache.getOrPut(
            AaStencilCoverConicalGradientKey(mode, fillType, tileMode, side),
        ) {
            val readMask: UInt = if (fillType.isEvenOdd()) 0x01u else 0xFFu
            val insideCompare =
                if (fillType.isInverse()) GPUCompareFunction.Equal else GPUCompareFunction.NotEqual
            val compare = when (side) {
                CoverageSide.Inside -> insideCompare
                CoverageSide.Outside ->
                    if (insideCompare == GPUCompareFunction.Equal) GPUCompareFunction.NotEqual
                    else GPUCompareFunction.Equal
            }
            val entryPoint = aaStencilCoverConicalGradientFragmentEntryPoint(side, tileMode)
            context.device.createRenderPipeline(
                RenderPipelineDescriptor(
                    layout = aaPolygonPipelineLayout,
                    vertex = VertexState(
                        module = aaStencilCoverConicalGradientShader,
                        entryPoint = "vs_main",
                        buffers = listOf(POLYGON_VERTEX_LAYOUT),
                    ),
                    fragment = FragmentState(
                        module = aaStencilCoverConicalGradientShader,
                        entryPoint = entryPoint,
                        targets = listOf(
                            ColorTargetState(
                                format = intermediateFormat,
                                blend = blendStateFor(mode),
                            ),
                        ),
                    ),
                    depthStencil = DepthStencilState(
                        format = GPUTextureFormat.Depth24PlusStencil8,
                        depthWriteEnabled = false,
                        depthCompare = GPUCompareFunction.Always,
                        stencilFront = StencilFaceState(
                            compare = compare,
                            failOp = GPUStencilOperation.Keep,
                            depthFailOp = GPUStencilOperation.Keep,
                            passOp = GPUStencilOperation.Keep,
                        ),
                        stencilBack = StencilFaceState(
                            compare = compare,
                            failOp = GPUStencilOperation.Keep,
                            depthFailOp = GPUStencilOperation.Keep,
                            passOp = GPUStencilOperation.Keep,
                        ),
                        stencilReadMask = readMask,
                        stencilWriteMask = 0xFFu,
                    ),
                ),
            )
        }

    private val aaStencilCoverConicalFocalGradientShader: GPUShaderModule =
        loadShader("shaders/aa_stencil_cover_conical_focal_gradient.wgsl")

    private data class AaStencilCoverConicalFocalGradientKey(
        val mode: SkBlendMode,
        val fillType: SkPathFillType,
        val tileMode: SkTileMode,
        val side: CoverageSide,
    )

    private val aaStencilCoverConicalFocalGradientPipelineCache:
        MutableMap<AaStencilCoverConicalFocalGradientKey, GPURenderPipeline> = mutableMapOf()

    private fun aaStencilCoverConicalFocalGradientFragmentEntryPoint(
        side: CoverageSide,
        tileMode: SkTileMode,
    ): String = when (side) {
        CoverageSide.Inside -> when (tileMode) {
            SkTileMode.kClamp -> "fs_inside_clamp"
            SkTileMode.kRepeat -> "fs_inside_repeat"
            SkTileMode.kMirror -> "fs_inside_mirror"
            SkTileMode.kDecal -> "fs_inside_decal"
        }
        CoverageSide.Outside -> when (tileMode) {
            SkTileMode.kClamp -> "fs_outside_clamp"
            SkTileMode.kRepeat -> "fs_outside_repeat"
            SkTileMode.kMirror -> "fs_outside_mirror"
            SkTileMode.kDecal -> "fs_outside_decal"
        }
    }

    private fun aaStencilCoverConicalFocalGradientPipelineFor(
        mode: SkBlendMode,
        fillType: SkPathFillType,
        tileMode: SkTileMode,
        side: CoverageSide,
    ): GPURenderPipeline =
        aaStencilCoverConicalFocalGradientPipelineCache.getOrPut(
            AaStencilCoverConicalFocalGradientKey(mode, fillType, tileMode, side),
        ) {
            val readMask: UInt = if (fillType.isEvenOdd()) 0x01u else 0xFFu
            val insideCompare =
                if (fillType.isInverse()) GPUCompareFunction.Equal else GPUCompareFunction.NotEqual
            val compare = when (side) {
                CoverageSide.Inside -> insideCompare
                CoverageSide.Outside ->
                    if (insideCompare == GPUCompareFunction.Equal) GPUCompareFunction.NotEqual
                    else GPUCompareFunction.Equal
            }
            val entryPoint = aaStencilCoverConicalFocalGradientFragmentEntryPoint(side, tileMode)
            context.device.createRenderPipeline(
                RenderPipelineDescriptor(
                    layout = aaPolygonPipelineLayout,
                    vertex = VertexState(
                        module = aaStencilCoverConicalFocalGradientShader,
                        entryPoint = "vs_main",
                        buffers = listOf(POLYGON_VERTEX_LAYOUT),
                    ),
                    fragment = FragmentState(
                        module = aaStencilCoverConicalFocalGradientShader,
                        entryPoint = entryPoint,
                        targets = listOf(
                            ColorTargetState(
                                format = intermediateFormat,
                                blend = blendStateFor(mode),
                            ),
                        ),
                    ),
                    depthStencil = DepthStencilState(
                        format = GPUTextureFormat.Depth24PlusStencil8,
                        depthWriteEnabled = false,
                        depthCompare = GPUCompareFunction.Always,
                        stencilFront = StencilFaceState(
                            compare = compare,
                            failOp = GPUStencilOperation.Keep,
                            depthFailOp = GPUStencilOperation.Keep,
                            passOp = GPUStencilOperation.Keep,
                        ),
                        stencilBack = StencilFaceState(
                            compare = compare,
                            failOp = GPUStencilOperation.Keep,
                            depthFailOp = GPUStencilOperation.Keep,
                            passOp = GPUStencilOperation.Keep,
                        ),
                        stencilReadMask = readMask,
                        stencilWriteMask = 0xFFu,
                    ),
                ),
            )
        }

    // ─── Bitmap shader on non-rect (G5.2.1) ─────────────────────────────
    //
    // AA stencil-and-cover cover-pass pipeline carrying a bitmap-shader
    // fragment shader. Mirror of the gradient stencil-cover pipelines
    // above but the bind group needs binding 1 (texture) + binding 2
    // (sampler) in addition to the uniform at binding 0 -- so this
    // family gets its own bind group layout + pipeline layout, plus its
    // own stencil-write pipeline (so both passes share the same 3-entry
    // bind group). The fragment shader has only 2 entry points (inside /
    // outside) -- the 4 tile modes are resolved by the per-draw sampler
    // (addressModeU / V for kClamp / kRepeat / kMirror) and a per-axis
    // in-shader kDecal check, matching `bitmap_shader.wgsl`'s scheme.
    // Hence the cache key shape is `(blend, fillType, side)` -- no
    // tileMode in the key. The (filter, tileX, tileY) tuple lives on the
    // bind group's sampler, not on the pipeline.

    private val aaStencilCoverBitmapShader: GPUShaderModule =
        loadShader("shaders/aa_stencil_cover_bitmap_shader.wgsl")

    /**
     * Bind group layout shared by the bitmap-shader stencil-write
     * pipeline AND the bitmap-shader cover pipelines :
     *   binding 0 -- uniform buffer (color + viewport + srcRect + dstRect +
     *                imageSize + paintColor + csFlags + csMatrix +
     *                edgeCountPad + edges), visibility = Vertex|Fragment
     *                (the stencil shader's vertex stage reads viewport
     *                from offset 16, the cover shader's fragment stage
     *                reads everything else)
     *   binding 1 -- sampled 2D texture (the cached image), visibility = Fragment
     *   binding 2 -- filtering sampler, visibility = Fragment
     * The stencil-write fragment shader (`solid_polygon.wgsl`) ignores
     * binding 1 / 2 -- they remain unread but must be bound, which
     * matches WebGPU's pipeline-vs-bind-group compatibility rule.
     */
    private val aaStencilCoverBitmapShaderBindGroupLayout: GPUBindGroupLayout =
        context.device.createBindGroupLayout(
            BindGroupLayoutDescriptor(
                entries = listOf(
                    BindGroupLayoutEntry(
                        binding = 0u,
                        visibility = GPUShaderStage.Vertex or GPUShaderStage.Fragment,
                        buffer = BufferBindingLayout(type = GPUBufferBindingType.Uniform),
                    ),
                    BindGroupLayoutEntry(
                        binding = 1u,
                        visibility = GPUShaderStage.Fragment,
                        texture = TextureBindingLayout(
                            sampleType = GPUTextureSampleType.Float,
                            viewDimension = GPUTextureViewDimension.TwoD,
                            multisampled = false,
                        ),
                    ),
                    BindGroupLayoutEntry(
                        binding = 2u,
                        visibility = GPUShaderStage.Fragment,
                        sampler = SamplerBindingLayout(type = GPUSamplerBindingType.Filtering),
                    ),
                ),
            ),
        )

    private val aaStencilCoverBitmapShaderPipelineLayout = context.device.createPipelineLayout(
        PipelineLayoutDescriptor(bindGroupLayouts = listOf(aaStencilCoverBitmapShaderBindGroupLayout)),
    )

    /**
     * Dedicated stencil-write pipeline that uses the bitmap-shader
     * pipeline layout (3-binding bind group). Functionally identical to
     * [stencilWritePipeline] -- same vertex shader (`solid_polygon.wgsl`),
     * same stencil ops (increment-front / decrement-back, color writes
     * masked off, depth always-pass). Only the bind group layout differs
     * so a single bind group can satisfy both the stencil and cover
     * passes for an [StencilCoverAaBitmapShaderDraw].
     */
    private val aaStencilCoverBitmapShaderStencilPipeline: GPURenderPipeline =
        context.device.createRenderPipeline(
            RenderPipelineDescriptor(
                layout = aaStencilCoverBitmapShaderPipelineLayout,
                vertex = VertexState(
                    module = polygonShader,
                    entryPoint = "vs_main",
                    buffers = listOf(POLYGON_VERTEX_LAYOUT),
                ),
                fragment = FragmentState(
                    module = polygonShader,
                    entryPoint = "fs_main",
                    targets = listOf(
                        ColorTargetState(
                            format = intermediateFormat,
                            blend = blendAddBoth(GPUBlendFactor.One, GPUBlendFactor.Zero),
                            writeMask = GPUColorWrite.None,
                        ),
                    ),
                ),
                depthStencil = DepthStencilState(
                    format = GPUTextureFormat.Depth24PlusStencil8,
                    depthWriteEnabled = false,
                    depthCompare = GPUCompareFunction.Always,
                    stencilFront = StencilFaceState(
                        compare = GPUCompareFunction.Always,
                        failOp = GPUStencilOperation.Keep,
                        depthFailOp = GPUStencilOperation.Keep,
                        passOp = GPUStencilOperation.IncrementWrap,
                    ),
                    stencilBack = StencilFaceState(
                        compare = GPUCompareFunction.Always,
                        failOp = GPUStencilOperation.Keep,
                        depthFailOp = GPUStencilOperation.Keep,
                        passOp = GPUStencilOperation.DecrementWrap,
                    ),
                    stencilReadMask = 0xFFu,
                    stencilWriteMask = 0xFFu,
                ),
            ),
        )

    private data class AaStencilCoverBitmapShaderKey(
        val mode: SkBlendMode,
        val fillType: SkPathFillType,
        val side: CoverageSide,
    )

    private val aaStencilCoverBitmapShaderPipelineCache:
        MutableMap<AaStencilCoverBitmapShaderKey, GPURenderPipeline> = mutableMapOf()

    private fun aaStencilCoverBitmapShaderFragmentEntryPoint(side: CoverageSide): String =
        when (side) {
            CoverageSide.Inside -> "fs_inside"
            CoverageSide.Outside -> "fs_outside"
        }

    private fun aaStencilCoverBitmapShaderPipelineFor(
        mode: SkBlendMode,
        fillType: SkPathFillType,
        side: CoverageSide,
    ): GPURenderPipeline =
        aaStencilCoverBitmapShaderPipelineCache.getOrPut(
            AaStencilCoverBitmapShaderKey(mode, fillType, side),
        ) {
            val readMask: UInt = if (fillType.isEvenOdd()) 0x01u else 0xFFu
            val insideCompare =
                if (fillType.isInverse()) GPUCompareFunction.Equal else GPUCompareFunction.NotEqual
            val compare = when (side) {
                CoverageSide.Inside -> insideCompare
                CoverageSide.Outside ->
                    if (insideCompare == GPUCompareFunction.Equal) GPUCompareFunction.NotEqual
                    else GPUCompareFunction.Equal
            }
            val entryPoint = aaStencilCoverBitmapShaderFragmentEntryPoint(side)
            context.device.createRenderPipeline(
                RenderPipelineDescriptor(
                    layout = aaStencilCoverBitmapShaderPipelineLayout,
                    vertex = VertexState(
                        module = aaStencilCoverBitmapShader,
                        entryPoint = "vs_main",
                        buffers = listOf(POLYGON_VERTEX_LAYOUT),
                    ),
                    fragment = FragmentState(
                        module = aaStencilCoverBitmapShader,
                        entryPoint = entryPoint,
                        targets = listOf(
                            ColorTargetState(
                                format = intermediateFormat,
                                blend = blendStateFor(mode),
                            ),
                        ),
                    ),
                    depthStencil = DepthStencilState(
                        format = GPUTextureFormat.Depth24PlusStencil8,
                        depthWriteEnabled = false,
                        depthCompare = GPUCompareFunction.Always,
                        stencilFront = StencilFaceState(
                            compare = compare,
                            failOp = GPUStencilOperation.Keep,
                            depthFailOp = GPUStencilOperation.Keep,
                            passOp = GPUStencilOperation.Keep,
                        ),
                        stencilBack = StencilFaceState(
                            compare = compare,
                            failOp = GPUStencilOperation.Keep,
                            depthFailOp = GPUStencilOperation.Keep,
                            passOp = GPUStencilOperation.Keep,
                        ),
                        stencilReadMask = readMask,
                        stencilWriteMask = 0xFFu,
                    ),
                ),
            )
        }

    /**
     * Translate a [SkBlendMode] into the WebGPU [BlendState] that
     * implements it. G2.2 covers the 4 modes WebGPU expresses natively
     * via `BlendComponent` (no shader-side blending, no `loadOp = load`
     * round-trip) :
     *
     *  - [SkBlendMode.kClear]   : `src * 0 + dst * 0 = 0`
     *  - [SkBlendMode.kSrc]     : `src * 1 + dst * 0 = src`
     *  - [SkBlendMode.kSrcOver] : `src * 1 + dst * (1 - src.a)`
     *  - [SkBlendMode.kDstOver] : `src * (1 - dst.a) + dst * 1`
     *
     * Other modes throw with a pointer to the phase that lands them
     * (kPlus / kScreen / kModulate need fragment-side blending and are
     * scheduled for a later G-phase per master plan G2 note).
     *
     * Note that fragment output is **premultiplied** (see
     * `solid_color.wgsl`), so `src.a` and `src.rgb * src.a` are already
     * in lockstep when these factors apply — the standard premul-input
     * formulation is what these constants assume.
     */
    private fun blendStateFor(mode: SkBlendMode): BlendState = when (mode) {
        SkBlendMode.kClear -> blendAddBoth(src = GPUBlendFactor.Zero, dst = GPUBlendFactor.Zero)
        SkBlendMode.kSrc -> blendAddBoth(src = GPUBlendFactor.One, dst = GPUBlendFactor.Zero)
        SkBlendMode.kSrcOver -> blendAddBoth(src = GPUBlendFactor.One, dst = GPUBlendFactor.OneMinusSrcAlpha)
        SkBlendMode.kDstOver -> blendAddBoth(src = GPUBlendFactor.OneMinusDstAlpha, dst = GPUBlendFactor.One)
        // G3.3a.1 — kPlus is `out = clamp(src + dst, 0, 1)` per Skia
        // (`SkBlendMode.kPlus`). In premul-in / premul-out (our convention),
        // `(srcFactor=One, dstFactor=One, op=Add)` evaluates exactly to
        // that clamped sum. The G2.2 plan note about "kPlus needs
        // fragment-side blending" turned out to be over-conservative for
        // the premul case.
        SkBlendMode.kPlus -> blendAddBoth(src = GPUBlendFactor.One, dst = GPUBlendFactor.One)
        else -> error(
            "SkWebGpuDevice : blend mode $mode not supported yet. " +
                "Supported : kClear / kSrc / kSrcOver / kDstOver (G2.2), kPlus (G3.3a.1). " +
                "kScreen / kModulate / etc. require fragment-side blending and land in a " +
                "later G-phase; see MIGRATION_PLAN_GPU_WEBGPU.md G2.",
        )
    }

    /**
     * Helper : symmetric `BlendComponent` for color AND alpha with
     * `op = Add`. Both Porter-Duff modes in G2.2 use the same factors
     * for color and alpha (the standard premul formulation), so a
     * single helper covers all 4.
     */
    private fun blendAddBoth(src: GPUBlendFactor, dst: GPUBlendFactor): BlendState {
        val component = BlendComponent(operation = GPUBlendOperation.Add, srcFactor = src, dstFactor = dst)
        return BlendState(color = component, alpha = component)
    }

    override fun deviceClipBounds(): SkIRect = SkIRect.MakeWH(width, height)

    override fun drawRect(rect: SkRect, clip: SkIRect, paint: SkPaint) {
        // G2.1 — translucent SrcOver supported via premul shader.
        // G2.2 — paint.blendMode honoured (kClear / kSrc / kSrcOver / kDstOver).
        // G2.3a — paint.isAntiAlias honoured via analytical coverage in the
        //          shader. Both AA and non-AA route through the same
        //          pipeline ; only the bounds passed to the shader and the
        //          scissor extent differ.
        // G3.1 — paint.style honoured (Fill / Stroke / StrokeAndFill). Stroke
        //          decomposes into 4 edge sub-rects (top/bottom/left/right)
        //          per SkBitmapDevice.strokeRect ; hairline (sw <= 0) snaps
        //          to integer coords and uses 1-pixel non-AA edges (matches
        //          SkScan::HairLineRgn).
        when (paint.style) {
            SkPaint.Style.kFill_Style -> drawFillRect(rect, clip, paint)
            SkPaint.Style.kStroke_Style -> drawStrokeRect(rect, clip, paint)
            SkPaint.Style.kStrokeAndFill_Style -> {
                drawFillRect(rect, clip, paint)
                drawStrokeRect(rect, clip, paint)
            }
        }
    }

    private fun drawFillRect(rect: SkRect, clip: SkIRect, paint: SkPaint) {
        val color = paint.color
        val scissor: IntArray
        val bounds: FloatArray
        if (paint.isAntiAlias) {
            // Conservative scissor : floor/ceil of the fractional rect,
            // clipped to the integer clip + viewport. Edge pixels that the
            // rect partially covers must reach the fragment stage so the
            // shader can compute their fractional coverage.
            val fl = rect.left.coerceAtLeast(clip.left.toFloat()).coerceAtLeast(0f)
            val ft = rect.top.coerceAtLeast(clip.top.toFloat()).coerceAtLeast(0f)
            val fr = rect.right.coerceAtMost(clip.right.toFloat()).coerceAtMost(width.toFloat())
            val fb = rect.bottom.coerceAtMost(clip.bottom.toFloat()).coerceAtMost(height.toFloat())
            if (fl >= fr || ft >= fb) return
            val sl = floor(fl.toDouble()).toInt().coerceAtLeast(0)
            val st = floor(ft.toDouble()).toInt().coerceAtLeast(0)
            val sr = ceil(fr.toDouble()).toInt().coerceAtMost(width)
            val sb = ceil(fb.toDouble()).toInt().coerceAtMost(height)
            if (sl >= sr || st >= sb) return
            scissor = intArrayOf(sl, st, sr - sl, sb - st)
            bounds = floatArrayOf(fl, ft, fr, fb)
        } else {
            // Non-AA : pixelEdge rounding matches SkBitmapDevice exactly,
            // and integer bounds make the shader's coverage formula
            // collapse to 1.0 for every visited pixel (pixel-centers sit
            // at least 0.5 device-pixels from any edge, so cov = 1).
            val l = pixelEdge(rect.left).coerceAtLeast(clip.left).coerceAtLeast(0)
            val t = pixelEdge(rect.top).coerceAtLeast(clip.top).coerceAtLeast(0)
            val r = pixelEdge(rect.right).coerceAtMost(clip.right).coerceAtMost(width)
            val b = pixelEdge(rect.bottom).coerceAtMost(clip.bottom).coerceAtMost(height)
            if (l >= r || t >= b) return
            scissor = intArrayOf(l, t, r - l, b - t)
            bounds = floatArrayOf(l.toFloat(), t.toFloat(), r.toFloat(), b.toFloat())
        }

        pending.add(
            RectDraw(
                x = scissor[0], y = scissor[1], w = scissor[2], h = scissor[3],
                ol = bounds[0], ot = bounds[1], or = bounds[2], ob = bounds[3],
                // Fill : degenerate inner bounds -> inner_cov = 0.
                il = DEGENERATE_INNER, it = DEGENERATE_INNER,
                ir = -DEGENERATE_INNER, ib = -DEGENERATE_INNER,
                r = SkColorGetR(color) / 255f,
                g = SkColorGetG(color) / 255f,
                b = SkColorGetB(color) / 255f,
                a = SkColorGetA(color) / 255f,
                mode = paint.blendMode,
            ),
        )
    }

    /**
     * G4.1 — emit a [LinearGradientRectDraw] for a kClamp `SkLinearGradient`
     * fill of the axis-aligned device-space rect [devRect] (already CTM-
     * transformed by the caller). Scissor + rect bounds derivation mirrors
     * [drawFillRect] : AA path widens the scissor by 1 px so the fragment
     * stage sees every potentially-covered pixel ; non-AA snaps to integer
     * edges via [pixelEdge].
     *
     * Endpoints come from `SkLinearGradient.getStartPoint/getEndPoint` in
     * source space ; the caller's CTM ([ctm]) maps them into device space
     * here so the fragment shader can operate directly in device-pixel
     * coords. Routed through [drawPath]'s [path.isRect] + axis-aligned-CTM
     * gate ; rotated/skewed CTM paints with the rect-shaped polygon under
     * a future generic gradient path (not part of G4.1).
     *
     * Returns `false` if the rect is entirely outside the clip / viewport ;
     * caller drops the draw (no fallback colour for fully-clipped rects).
     */
    private fun drawLinearGradientFillRect(
        devRect: SkRect, clip: SkIRect, paint: SkPaint, grad: SkLinearGradient, ctm: SkMatrix,
    ): Boolean {
        val scissor: IntArray
        if (paint.isAntiAlias) {
            val fl = devRect.left.coerceAtLeast(clip.left.toFloat()).coerceAtLeast(0f)
            val ft = devRect.top.coerceAtLeast(clip.top.toFloat()).coerceAtLeast(0f)
            val fr = devRect.right.coerceAtMost(clip.right.toFloat()).coerceAtMost(width.toFloat())
            val fb = devRect.bottom.coerceAtMost(clip.bottom.toFloat()).coerceAtMost(height.toFloat())
            if (fl >= fr || ft >= fb) return false
            val sl = floor(fl.toDouble()).toInt().coerceAtLeast(0)
            val st = floor(ft.toDouble()).toInt().coerceAtLeast(0)
            val sr = ceil(fr.toDouble()).toInt().coerceAtMost(width)
            val sb = ceil(fb.toDouble()).toInt().coerceAtMost(height)
            if (sl >= sr || st >= sb) return false
            scissor = intArrayOf(sl, st, sr - sl, sb - st)
        } else {
            val l = pixelEdge(devRect.left).coerceAtLeast(clip.left).coerceAtLeast(0)
            val t = pixelEdge(devRect.top).coerceAtLeast(clip.top).coerceAtLeast(0)
            val r = pixelEdge(devRect.right).coerceAtMost(clip.right).coerceAtMost(width)
            val b = pixelEdge(devRect.bottom).coerceAtMost(clip.bottom).coerceAtMost(height)
            if (l >= r || t >= b) return false
            scissor = intArrayOf(l, t, r - l, b - t)
        }

        val p0 = grad.getStartPoint()
        val p1 = grad.getEndPoint()
        val (sx, sy) = ctm.mapXY(p0.fX, p0.fY)
        val (ex, ey) = ctm.mapXY(p1.fX, p1.fY)

        val srcColors = grad.getColors()
        val positions = grad.getPositions()
        val count = srcColors.size.coerceAtMost(MAX_GRADIENT_STOPS)
        val packedPositions = FloatArray(MAX_GRADIENT_STOPS * 4)
        val packedColors = FloatArray(MAX_GRADIENT_STOPS * 4)
        for (i in 0 until count) {
            packedPositions[i * 4] = positions[i]
            val c = srcColors[i]
            val a = SkColorGetA(c) / 255f
            val r = SkColorGetR(c) / 255f
            val g = SkColorGetG(c) / 255f
            val b = SkColorGetB(c) / 255f
            // Premultiplied : matches the fragment-output convention
            // (G2.1 premul) and the SrcOver blend state (G2.2).
            packedColors[i * 4]     = r * a
            packedColors[i * 4 + 1] = g * a
            packedColors[i * 4 + 2] = b * a
            packedColors[i * 4 + 3] = a
        }

        // Use the first stop's color as the placeholder paint color so
        // [PendingDraw.r/g/b/a] stays meaningful (unused on this path).
        val first = srcColors[0]
        pending.add(
            LinearGradientRectDraw(
                scissor = scissor,
                startX = sx, startY = sy, endX = ex, endY = ey,
                stopPositions = packedPositions,
                stopColors = packedColors,
                stopCount = count,
                tileMode = grad.getTileMode(),
                r = SkColorGetR(first) / 255f,
                g = SkColorGetG(first) / 255f,
                b = SkColorGetB(first) / 255f,
                a = SkColorGetA(first) / 255f,
                mode = paint.blendMode,
            ),
        )
        return true
    }

    /**
     * G4.2 -- emit a [RadialGradientRectDraw] for a kClamp `SkRadialGradient`
     * fill of the axis-aligned device-space rect [devRect]. Scissor derivation
     * is identical to [drawLinearGradientFillRect].
     *
     * `SkRadialGradient.center` is shader-local ; the caller's CTM ([ctm]) maps
     * it into device space here. The radius is scaled by `ctm.getMaxScale()` ;
     * the dispatch gate restricts to axis-aligned CTMs where this collapses to
     * `max(|sx|, |sy|)`. Non-uniform scale (sx != sy) is allowed by the gate
     * but would produce an ellipse on the raster side -- the radial shader
     * can only render circles, so the result will drift from the reference
     * under non-uniform scale. Caller is responsible for routing those cases
     * through the generic path (G4.3) when needed.
     */
    private fun drawRadialGradientFillRect(
        devRect: SkRect, clip: SkIRect, paint: SkPaint, grad: SkRadialGradient, ctm: SkMatrix,
    ): Boolean {
        val scissor: IntArray
        if (paint.isAntiAlias) {
            val fl = devRect.left.coerceAtLeast(clip.left.toFloat()).coerceAtLeast(0f)
            val ft = devRect.top.coerceAtLeast(clip.top.toFloat()).coerceAtLeast(0f)
            val fr = devRect.right.coerceAtMost(clip.right.toFloat()).coerceAtMost(width.toFloat())
            val fb = devRect.bottom.coerceAtMost(clip.bottom.toFloat()).coerceAtMost(height.toFloat())
            if (fl >= fr || ft >= fb) return false
            val sl = floor(fl.toDouble()).toInt().coerceAtLeast(0)
            val st = floor(ft.toDouble()).toInt().coerceAtLeast(0)
            val sr = ceil(fr.toDouble()).toInt().coerceAtMost(width)
            val sb = ceil(fb.toDouble()).toInt().coerceAtMost(height)
            if (sl >= sr || st >= sb) return false
            scissor = intArrayOf(sl, st, sr - sl, sb - st)
        } else {
            val l = pixelEdge(devRect.left).coerceAtLeast(clip.left).coerceAtLeast(0)
            val t = pixelEdge(devRect.top).coerceAtLeast(clip.top).coerceAtLeast(0)
            val r = pixelEdge(devRect.right).coerceAtMost(clip.right).coerceAtMost(width)
            val b = pixelEdge(devRect.bottom).coerceAtMost(clip.bottom).coerceAtMost(height)
            if (l >= r || t >= b) return false
            scissor = intArrayOf(l, t, r - l, b - t)
        }

        val center = grad.getCenter()
        val (cx, cy) = ctm.mapXY(center.fX, center.fY)
        val devRadius = grad.getRadius() * ctm.getMaxScale()

        val srcColors = grad.getColors()
        val positions = grad.getPositions()
        val count = srcColors.size.coerceAtMost(MAX_GRADIENT_STOPS)
        val packedPositions = FloatArray(MAX_GRADIENT_STOPS * 4)
        val packedColors = FloatArray(MAX_GRADIENT_STOPS * 4)
        for (i in 0 until count) {
            packedPositions[i * 4] = positions[i]
            val c = srcColors[i]
            val a = SkColorGetA(c) / 255f
            val r = SkColorGetR(c) / 255f
            val g = SkColorGetG(c) / 255f
            val b = SkColorGetB(c) / 255f
            packedColors[i * 4]     = r * a
            packedColors[i * 4 + 1] = g * a
            packedColors[i * 4 + 2] = b * a
            packedColors[i * 4 + 3] = a
        }

        val first = srcColors[0]
        pending.add(
            RadialGradientRectDraw(
                scissor = scissor,
                centerX = cx, centerY = cy, radius = devRadius,
                stopPositions = packedPositions,
                stopColors = packedColors,
                stopCount = count,
                tileMode = grad.getTileMode(),
                r = SkColorGetR(first) / 255f,
                g = SkColorGetG(first) / 255f,
                b = SkColorGetB(first) / 255f,
                a = SkColorGetA(first) / 255f,
                mode = paint.blendMode,
            ),
        )
        return true
    }

    /**
     * G4.3 / G4.3.1 -- emit a [SweepGradientRectDraw] for an `SkSweepGradient`
     * fill of the axis-aligned device-space rect [devRect]. Scissor derivation
     * is identical to [drawLinearGradientFillRect] / [drawRadialGradientFillRect].
     *
     * `SkSweepGradient.center` is shader-local ; the caller's CTM ([ctm]) maps
     * it into device space here. Angles (startAngle / endAngle) are in source-
     * space degrees ; under the axis-aligned-CTM dispatch gate they pass through
     * unchanged (axis-aligned scale + translate preserve angular orientation).
     * The host-side `tBias = -startAngle / 360` and `tScale = 360 / (endAngle -
     * startAngle)` collapse the fragment-shader remapping to one add + one mul.
     *
     * Tile modes : all 4 (kClamp / kRepeat / kMirror / kDecal) route here since
     * G4.3.1. The pipeline cache picks the matching fragment entry point via
     * [sweepGradientPipelineFor].
     */
    private fun drawSweepGradientFillRect(
        devRect: SkRect, clip: SkIRect, paint: SkPaint, grad: SkSweepGradient, ctm: SkMatrix,
    ): Boolean {
        val scissor: IntArray
        if (paint.isAntiAlias) {
            val fl = devRect.left.coerceAtLeast(clip.left.toFloat()).coerceAtLeast(0f)
            val ft = devRect.top.coerceAtLeast(clip.top.toFloat()).coerceAtLeast(0f)
            val fr = devRect.right.coerceAtMost(clip.right.toFloat()).coerceAtMost(width.toFloat())
            val fb = devRect.bottom.coerceAtMost(clip.bottom.toFloat()).coerceAtMost(height.toFloat())
            if (fl >= fr || ft >= fb) return false
            val sl = floor(fl.toDouble()).toInt().coerceAtLeast(0)
            val st = floor(ft.toDouble()).toInt().coerceAtLeast(0)
            val sr = ceil(fr.toDouble()).toInt().coerceAtMost(width)
            val sb = ceil(fb.toDouble()).toInt().coerceAtMost(height)
            if (sl >= sr || st >= sb) return false
            scissor = intArrayOf(sl, st, sr - sl, sb - st)
        } else {
            val l = pixelEdge(devRect.left).coerceAtLeast(clip.left).coerceAtLeast(0)
            val t = pixelEdge(devRect.top).coerceAtLeast(clip.top).coerceAtLeast(0)
            val r = pixelEdge(devRect.right).coerceAtMost(clip.right).coerceAtMost(width)
            val b = pixelEdge(devRect.bottom).coerceAtMost(clip.bottom).coerceAtMost(height)
            if (l >= r || t >= b) return false
            scissor = intArrayOf(l, t, r - l, b - t)
        }

        val center = grad.getCenter()
        val (cx, cy) = ctm.mapXY(center.fX, center.fY)
        val startAngle = grad.getStartAngle()
        val endAngle = grad.getEndAngle()
        val tBias = -startAngle / 360f
        val tScale = 360f / (endAngle - startAngle)

        val srcColors = grad.getColors()
        val positions = grad.getPositions()
        val count = srcColors.size.coerceAtMost(MAX_GRADIENT_STOPS)
        val packedPositions = FloatArray(MAX_GRADIENT_STOPS * 4)
        val packedColors = FloatArray(MAX_GRADIENT_STOPS * 4)
        for (i in 0 until count) {
            packedPositions[i * 4] = positions[i]
            val c = srcColors[i]
            val a = SkColorGetA(c) / 255f
            val r = SkColorGetR(c) / 255f
            val g = SkColorGetG(c) / 255f
            val b = SkColorGetB(c) / 255f
            packedColors[i * 4]     = r * a
            packedColors[i * 4 + 1] = g * a
            packedColors[i * 4 + 2] = b * a
            packedColors[i * 4 + 3] = a
        }

        val first = srcColors[0]
        pending.add(
            SweepGradientRectDraw(
                scissor = scissor,
                centerX = cx, centerY = cy,
                tBias = tBias, tScale = tScale,
                stopPositions = packedPositions,
                stopColors = packedColors,
                stopCount = count,
                tileMode = grad.getTileMode(),
                r = SkColorGetR(first) / 255f,
                g = SkColorGetG(first) / 255f,
                b = SkColorGetB(first) / 255f,
                a = SkColorGetA(first) / 255f,
                mode = paint.blendMode,
            ),
        )
        return true
    }

    /**
     * G4.4 / G4.4.2 -- emit a [ConicalGradientRectDraw] for a
     * `SkConicalGradient` fill of the axis-aligned device-space rect
     * [devRect] under any of the 4 tile modes. Scissor derivation is
     * identical to [drawRadialGradientFillRect].
     *
     * Only the **kRadial** sub-case (concentric circles, `c0 == c1`) is
     * routed here ; the dispatch gate at the top of [drawPath] already
     * filtered other sub-cases. The shared centre is mapped through [ctm]
     * to device space ; the start / end radii are scaled by
     * `ctm.getMaxScale()` (collapses to `max(|sx|, |sy|)` under the
     * axis-aligned-CTM gate). The shader evaluates
     *   t = (length(p - c) - r0) / (r1 - r0)
     * and the per-tile-mode entry point clamps / wraps / mirrors / decals
     * the result before the stops lookup.
     */
    private fun drawConicalGradientFillRect(
        devRect: SkRect, clip: SkIRect, paint: SkPaint, grad: SkConicalGradient, ctm: SkMatrix,
    ): Boolean {
        val scissor: IntArray
        if (paint.isAntiAlias) {
            val fl = devRect.left.coerceAtLeast(clip.left.toFloat()).coerceAtLeast(0f)
            val ft = devRect.top.coerceAtLeast(clip.top.toFloat()).coerceAtLeast(0f)
            val fr = devRect.right.coerceAtMost(clip.right.toFloat()).coerceAtMost(width.toFloat())
            val fb = devRect.bottom.coerceAtMost(clip.bottom.toFloat()).coerceAtMost(height.toFloat())
            if (fl >= fr || ft >= fb) return false
            val sl = floor(fl.toDouble()).toInt().coerceAtLeast(0)
            val st = floor(ft.toDouble()).toInt().coerceAtLeast(0)
            val sr = ceil(fr.toDouble()).toInt().coerceAtMost(width)
            val sb = ceil(fb.toDouble()).toInt().coerceAtMost(height)
            if (sl >= sr || st >= sb) return false
            scissor = intArrayOf(sl, st, sr - sl, sb - st)
        } else {
            val l = pixelEdge(devRect.left).coerceAtLeast(clip.left).coerceAtLeast(0)
            val t = pixelEdge(devRect.top).coerceAtLeast(clip.top).coerceAtLeast(0)
            val r = pixelEdge(devRect.right).coerceAtMost(clip.right).coerceAtMost(width)
            val b = pixelEdge(devRect.bottom).coerceAtMost(clip.bottom).coerceAtMost(height)
            if (l >= r || t >= b) return false
            scissor = intArrayOf(l, t, r - l, b - t)
        }

        // kRadial sub-case : c0 == c1 in source space ; use either, here `end`.
        val end = grad.getEnd()
        val (cx, cy) = ctm.mapXY(end.fX, end.fY)
        val maxScale = ctm.getMaxScale()
        val devR0 = grad.getStartRadius() * maxScale
        val devR1 = grad.getEndRadius() * maxScale

        val srcColors = grad.getColors()
        val positions = grad.getPositions()
        val count = srcColors.size.coerceAtMost(MAX_GRADIENT_STOPS)
        val packedPositions = FloatArray(MAX_GRADIENT_STOPS * 4)
        val packedColors = FloatArray(MAX_GRADIENT_STOPS * 4)
        for (i in 0 until count) {
            packedPositions[i * 4] = positions[i]
            val c = srcColors[i]
            val a = SkColorGetA(c) / 255f
            val r = SkColorGetR(c) / 255f
            val g = SkColorGetG(c) / 255f
            val b = SkColorGetB(c) / 255f
            packedColors[i * 4]     = r * a
            packedColors[i * 4 + 1] = g * a
            packedColors[i * 4 + 2] = b * a
            packedColors[i * 4 + 3] = a
        }

        val first = srcColors[0]
        pending.add(
            ConicalGradientRectDraw(
                scissor = scissor,
                centerX = cx, centerY = cy,
                startRadius = devR0, endRadius = devR1,
                stopPositions = packedPositions,
                stopColors = packedColors,
                stopCount = count,
                tileMode = grad.getTileMode(),
                r = SkColorGetR(first) / 255f,
                g = SkColorGetG(first) / 255f,
                b = SkColorGetB(first) / 255f,
                a = SkColorGetA(first) / 255f,
                mode = paint.blendMode,
            ),
        )
        return true
    }

    /**
     * G4.4.1 / G4.4.2 -- emit a [ConicalFocalGradientRectDraw] for a
     * `SkConicalGradient` (focal-inside well-behaved sub-case) filling
     * the axis-aligned device-space rect [devRect] under any of the 4
     * tile modes. Scissor derivation mirrors [drawConicalGradientFillRect].
     *
     * Per-draw transform : `gradientMatrix * (CTM * localMatrix)^-1`,
     * identical to the CPU `deviceToConical` cached by
     * [SkConicalGradient.setupForDraw]. The resulting 2x3 affine maps
     * device-pixel coords straight into the focal frame ; the shader
     * applies the well-behaved formula + the static post-passes
     * (`negate_x`, `compensateFocal`, `unswap`) read from per-draw flags.
     *
     * Falls back to `false` (no draw enqueued) when the combined
     * `(CTM * localMatrix)` is singular -- caller routes through the
     * solid-color fill (matches the CPU shader's degenerate-matrix
     * fallback to the first stop).
     */
    private fun drawConicalFocalGradientFillRect(
        devRect: SkRect, clip: SkIRect, paint: SkPaint, grad: SkConicalGradient, ctm: SkMatrix,
    ): Boolean {
        val scissor: IntArray
        if (paint.isAntiAlias) {
            val fl = devRect.left.coerceAtLeast(clip.left.toFloat()).coerceAtLeast(0f)
            val ft = devRect.top.coerceAtLeast(clip.top.toFloat()).coerceAtLeast(0f)
            val fr = devRect.right.coerceAtMost(clip.right.toFloat()).coerceAtMost(width.toFloat())
            val fb = devRect.bottom.coerceAtMost(clip.bottom.toFloat()).coerceAtMost(height.toFloat())
            if (fl >= fr || ft >= fb) return false
            val sl = floor(fl.toDouble()).toInt().coerceAtLeast(0)
            val st = floor(ft.toDouble()).toInt().coerceAtLeast(0)
            val sr = ceil(fr.toDouble()).toInt().coerceAtMost(width)
            val sb = ceil(fb.toDouble()).toInt().coerceAtMost(height)
            if (sl >= sr || st >= sb) return false
            scissor = intArrayOf(sl, st, sr - sl, sb - st)
        } else {
            val l = pixelEdge(devRect.left).coerceAtLeast(clip.left).coerceAtLeast(0)
            val t = pixelEdge(devRect.top).coerceAtLeast(clip.top).coerceAtLeast(0)
            val r = pixelEdge(devRect.right).coerceAtMost(clip.right).coerceAtMost(width)
            val b = pixelEdge(devRect.bottom).coerceAtMost(clip.bottom).coerceAtMost(height)
            if (l >= r || t >= b) return false
            scissor = intArrayOf(l, t, r - l, b - t)
        }

        val ctmLocal = ctm.preConcat(grad.localMatrix)
        val invCtmLocal = ctmLocal.invert() ?: return false
        val devToFocal = grad.getGradientMatrix().preConcat(invCtmLocal)

        val fd = grad.getFocalData() ?: return false
        val fP0 = 1f / fd.fR1
        val fP1 = fd.fFocalX
        val compensate = if (!fd.isNativelyFocal()) 1f else 0f
        val unswap = if (fd.isSwapped()) 1f else 0f
        val negateX = if ((1f - fP1) < 0f) 1f else 0f
        // G4.4.5 / G4.4.6 -- sub-case selector :
        //   well-behaved      -> 0 (default formula),
        //   focal-outside     -> 1 (greater/smaller picked via subCaseSign),
        //   focal-on-circle   -> 2 (`t = (x*x + y*y) / x`).
        val subCase = when {
            fd.isFocalOnCircle() -> 2f
            fd.isFocalOutside() -> 1f
            else -> 0f
        }
        val subCaseSign = if (fd.isFocalOutside() && fd.isFocalOutsideSmaller()) -1f else 1f

        val srcColors = grad.getColors()
        val positions = grad.getPositions()
        val count = srcColors.size.coerceAtMost(MAX_GRADIENT_STOPS)
        val packedPositions = FloatArray(MAX_GRADIENT_STOPS * 4)
        val packedColors = FloatArray(MAX_GRADIENT_STOPS * 4)
        for (i in 0 until count) {
            packedPositions[i * 4] = positions[i]
            val c = srcColors[i]
            val a = SkColorGetA(c) / 255f
            val r = SkColorGetR(c) / 255f
            val g = SkColorGetG(c) / 255f
            val b = SkColorGetB(c) / 255f
            packedColors[i * 4]     = r * a
            packedColors[i * 4 + 1] = g * a
            packedColors[i * 4 + 2] = b * a
            packedColors[i * 4 + 3] = a
        }

        val first = srcColors[0]
        pending.add(
            ConicalFocalGradientRectDraw(
                scissor = scissor,
                affine00 = devToFocal.sx, affine01 = devToFocal.kx, affine02 = devToFocal.tx,
                affine10 = devToFocal.ky, affine11 = devToFocal.sy, affine12 = devToFocal.ty,
                fP0 = fP0, fP1 = fP1,
                compensateFocal = compensate, unswap = unswap,
                negateX = negateX,
                subCase = subCase, subCaseSign = subCaseSign,
                stopPositions = packedPositions,
                stopColors = packedColors,
                stopCount = count,
                tileMode = grad.getTileMode(),
                r = SkColorGetR(first) / 255f,
                g = SkColorGetG(first) / 255f,
                b = SkColorGetB(first) / 255f,
                a = SkColorGetA(first) / 255f,
                mode = paint.blendMode,
            ),
        )
        return true
    }

    /**
     * G4.4.4 -- emit a [ConicalStripGradientRectDraw] for a
     * `SkConicalGradient` of [SkConicalGradient.Type.kStrip] filling the
     * axis-aligned device-space rect [devRect] under any of the 4 tile
     * modes. Scissor derivation mirrors [drawConicalFocalGradientFillRect].
     *
     * Per-draw transform : `gradientMatrix * (CTM * localMatrix)^-1`,
     * identical to the CPU `deviceToConical` cached by
     * [SkConicalGradient.setupForDraw]. The 2x3 affine maps device-pixel
     * coords straight into the conical frame (where c0 = (0, 0),
     * c1 = (1, 0)) ; the shader applies the kStrip formula
     * `t = x + sqrt(stripP0 - y*y)` plus the `disc < 0` mask.
     *
     * Falls back to `false` (no draw enqueued) when the combined
     * `(CTM * localMatrix)` is singular -- caller routes through the
     * solid-color fill.
     */
    private fun drawConicalStripGradientFillRect(
        devRect: SkRect, clip: SkIRect, paint: SkPaint, grad: SkConicalGradient, ctm: SkMatrix,
    ): Boolean {
        val scissor: IntArray
        if (paint.isAntiAlias) {
            val fl = devRect.left.coerceAtLeast(clip.left.toFloat()).coerceAtLeast(0f)
            val ft = devRect.top.coerceAtLeast(clip.top.toFloat()).coerceAtLeast(0f)
            val fr = devRect.right.coerceAtMost(clip.right.toFloat()).coerceAtMost(width.toFloat())
            val fb = devRect.bottom.coerceAtMost(clip.bottom.toFloat()).coerceAtMost(height.toFloat())
            if (fl >= fr || ft >= fb) return false
            val sl = floor(fl.toDouble()).toInt().coerceAtLeast(0)
            val st = floor(ft.toDouble()).toInt().coerceAtLeast(0)
            val sr = ceil(fr.toDouble()).toInt().coerceAtMost(width)
            val sb = ceil(fb.toDouble()).toInt().coerceAtMost(height)
            if (sl >= sr || st >= sb) return false
            scissor = intArrayOf(sl, st, sr - sl, sb - st)
        } else {
            val l = pixelEdge(devRect.left).coerceAtLeast(clip.left).coerceAtLeast(0)
            val t = pixelEdge(devRect.top).coerceAtLeast(clip.top).coerceAtLeast(0)
            val r = pixelEdge(devRect.right).coerceAtMost(clip.right).coerceAtMost(width)
            val b = pixelEdge(devRect.bottom).coerceAtMost(clip.bottom).coerceAtMost(height)
            if (l >= r || t >= b) return false
            scissor = intArrayOf(l, t, r - l, b - t)
        }

        val ctmLocal = ctm.preConcat(grad.localMatrix)
        val invCtmLocal = ctmLocal.invert() ?: return false
        val devToConical = grad.getGradientMatrix().preConcat(invCtmLocal)

        val stripP0 = grad.getStripP0()

        val srcColors = grad.getColors()
        val positions = grad.getPositions()
        val count = srcColors.size.coerceAtMost(MAX_GRADIENT_STOPS)
        val packedPositions = FloatArray(MAX_GRADIENT_STOPS * 4)
        val packedColors = FloatArray(MAX_GRADIENT_STOPS * 4)
        for (i in 0 until count) {
            packedPositions[i * 4] = positions[i]
            val c = srcColors[i]
            val a = SkColorGetA(c) / 255f
            val r = SkColorGetR(c) / 255f
            val g = SkColorGetG(c) / 255f
            val b = SkColorGetB(c) / 255f
            packedColors[i * 4]     = r * a
            packedColors[i * 4 + 1] = g * a
            packedColors[i * 4 + 2] = b * a
            packedColors[i * 4 + 3] = a
        }

        val first = srcColors[0]
        pending.add(
            ConicalStripGradientRectDraw(
                scissor = scissor,
                affine00 = devToConical.sx, affine01 = devToConical.kx, affine02 = devToConical.tx,
                affine10 = devToConical.ky, affine11 = devToConical.sy, affine12 = devToConical.ty,
                stripP0 = stripP0,
                stopPositions = packedPositions,
                stopColors = packedColors,
                stopCount = count,
                tileMode = grad.getTileMode(),
                r = SkColorGetR(first) / 255f,
                g = SkColorGetG(first) / 255f,
                b = SkColorGetB(first) / 255f,
                a = SkColorGetA(first) / 255f,
                mode = paint.blendMode,
            ),
        )
        return true
    }

    /**
     * G3.1.1 — single annular AA rect draw : outer bounds = `rect ± half_sw`,
     * inner bounds = `rect ∓ half_sw`. Replaces the G3.1 4-edge fill
     * decomposition for the AA path so the corner pixels get the same
     * `outer_cov - inner_cov` coverage as `SkBitmapDevice.strokeRectAA`.
     * Hairline AA uses the same machinery with effective width 1 (per
     * SkBitmapDevice's `w = if (strokeWidth <= 0f) 1f else strokeWidth`).
     */
    private fun drawAnnularStrokeRect(
        rect: SkRect, clip: SkIRect, paint: SkPaint, strokeWidth: Float,
    ) {
        val half = strokeWidth * 0.5f
        val outerL = (rect.left - half).coerceAtLeast(clip.left.toFloat()).coerceAtLeast(0f)
        val outerT = (rect.top - half).coerceAtLeast(clip.top.toFloat()).coerceAtLeast(0f)
        val outerR = (rect.right + half).coerceAtMost(clip.right.toFloat()).coerceAtMost(width.toFloat())
        val outerB = (rect.bottom + half).coerceAtMost(clip.bottom.toFloat()).coerceAtMost(height.toFloat())
        if (outerL >= outerR || outerT >= outerB) return
        // Inner bounds are NOT clipped to the viewport — the shader's
        // clamp() handles out-of-range gracefully and inner_cov collapses
        // to 0 where the inner rect doesn't overlap the fragment.
        val innerL = rect.left + half
        val innerT = rect.top + half
        val innerR = rect.right - half
        val innerB = rect.bottom - half

        val sl = floor(outerL.toDouble()).toInt().coerceAtLeast(0)
        val st = floor(outerT.toDouble()).toInt().coerceAtLeast(0)
        val sr = ceil(outerR.toDouble()).toInt().coerceAtMost(width)
        val sb = ceil(outerB.toDouble()).toInt().coerceAtMost(height)
        if (sl >= sr || st >= sb) return

        val color = paint.color
        pending.add(
            RectDraw(
                x = sl, y = st, w = sr - sl, h = sb - st,
                ol = outerL, ot = outerT, or = outerR, ob = outerB,
                il = innerL, it = innerT, ir = innerR, ib = innerB,
                r = SkColorGetR(color) / 255f,
                g = SkColorGetG(color) / 255f,
                b = SkColorGetB(color) / 255f,
                a = SkColorGetA(color) / 255f,
                mode = paint.blendMode,
            ),
        )
    }

    /**
     * Decompose an axis-aligned rect stroke into fill sub-draws — mirrors
     * [org.skia.core.SkBitmapDevice.strokeRect]'s annular outer/inner
     * formulation, modulo the per-pixel emit which is delegated to the
     * shader's analytical coverage path.
     *
     * The 4 edges (top, bottom, left, right) are pushed as separate
     * `RectDraw`s. Their bounds are computed in floating-point so AA
     * paints get fractional coverage at the outer + inner edges. The
     * corners are covered by top/bottom only ; left/right exclude them
     * to avoid double-painting (matters for non-opaque blend modes).
     */
    private fun drawStrokeRect(rect: SkRect, clip: SkIRect, paint: SkPaint) {
        val sw = paint.strokeWidth
        if (sw <= 0f) {
            drawHairlineRect(rect, clip, paint)
            return
        }
        // G3.1.1 — AA stroke takes the annular fast path (one draw, exact
        // SkBitmapDevice corner convention). Non-AA keeps the G3.1 4-edge
        // decomposition (byte-stable for existing non-AA stroke tests).
        if (paint.isAntiAlias) {
            drawAnnularStrokeRect(rect, clip, paint, sw)
            return
        }

        val half = sw * 0.5f
        val outerL = rect.left - half
        val outerT = rect.top - half
        val outerR = rect.right + half
        val outerB = rect.bottom + half
        val innerL = rect.left + half
        val innerT = rect.top + half
        val innerR = rect.right - half
        val innerB = rect.bottom - half

        if (innerL >= innerR || innerT >= innerB) {
            // Stroke is so thick that the inner rect is empty (or inverted) ;
            // the whole outer rect becomes the painted region.
            drawFillRect(SkRect.MakeLTRB(outerL, outerT, outerR, outerB), clip, paint)
            return
        }

        // top edge : full outer width, height = upper band
        drawFillRect(SkRect.MakeLTRB(outerL, outerT, outerR, innerT), clip, paint)
        // bottom edge : full outer width, height = lower band
        drawFillRect(SkRect.MakeLTRB(outerL, innerB, outerR, outerB), clip, paint)
        // left edge : excluding corners (top/bottom already cover them)
        drawFillRect(SkRect.MakeLTRB(outerL, innerT, innerL, innerB), clip, paint)
        // right edge : excluding corners
        drawFillRect(SkRect.MakeLTRB(innerR, innerT, outerR, innerB), clip, paint)
    }

    /**
     * Hairline rect stroke — `strokeWidth <= 0` in Skia means a
     * 1-device-pixel outline. Two code paths :
     *  - **Non-AA** : snap edges to floor() integer coords and emit 4
     *    1-pixel rect fills, matching `SkScan::HairLineRgn` /
     *    `SkBitmapDevice.strokeRect`'s hairline branch.
     *  - **AA** (G3.1.1) : route through [drawAnnularStrokeRect] with
     *    effective `strokeWidth = 1` -- mirrors
     *    `SkBitmapDevice.strokeRectAA`'s `w = if (sw <= 0f) 1f else sw`
     *    rule, so sub-pixel coverage matches the raster output.
     */
    private fun drawHairlineRect(rect: SkRect, clip: SkIRect, paint: SkPaint) {
        if (paint.isAntiAlias) {
            drawAnnularStrokeRect(rect, clip, paint, strokeWidth = 1f)
            return
        }
        val l = floor(rect.left.toDouble()).toFloat()
        val t = floor(rect.top.toDouble()).toFloat()
        val r = floor(rect.right.toDouble()).toFloat()
        val b = floor(rect.bottom.toDouble()).toFloat()
        drawFillRect(SkRect.MakeLTRB(l,     t,     r + 1, t + 1), clip, paint) // top
        drawFillRect(SkRect.MakeLTRB(l,     b,     r + 1, b + 1), clip, paint) // bottom
        drawFillRect(SkRect.MakeLTRB(l,     t + 1, l + 1, b),     clip, paint) // left
        drawFillRect(SkRect.MakeLTRB(r,     t + 1, r + 1, b),     clip, paint) // right
    }

    override fun drawPaint(ctm: SkMatrix, clip: SkIRect, paint: SkPaint) {
        // G3.2 — drawPaint = fill the entire clip with the paint. Route
        // through drawRect so all of G2.1/G2.2/G2.3a/G3.1 logic (alpha,
        // blend mode, AA, stroke-style validation) applies uniformly.
        // CTM is unused for the solid-paint fast path : drawPaint already
        // operates in device coords by contract (matches
        // SkBitmapDevice.drawPaint).
        //
        // G5.2 -- when `paint.shader != null` the clip-sized rect must
        // reach the shader dispatch gate inside [drawPath] (drawRect
        // dispatches to the solid-colour rasterizer, which would drop
        // the shader). drawPath expects the rect in SOURCE (user-
        // space) coords + the CTM applied at dispatch time, so we
        // invert the device-coord clip rect back through `ctm^-1`
        // before dispatch. The bitmap shader's `localMatrix` is
        // composed with this same `ctm` inside [drawBitmapShaderFillRect]
        // so the shader sees the correct user -> image-pixel chain
        // regardless of the canvas's translate/scale state.
        //
        // Limitation : non-invertible CTM (degenerate scale) falls
        // back to the solid drawRect path -- same behaviour as the
        // existing gradient dispatches (`drawLinearGradientFillRect`
        // and friends return false on a fully-clipped device rect).
        if (paint.shader != null) {
            val inv = ctm.invert()
            if (inv != null) {
                val (sl, st) = inv.mapXY(clip.left.toFloat(), clip.top.toFloat())
                val (sr, sb) = inv.mapXY(clip.right.toFloat(), clip.bottom.toFloat())
                val srcRect = SkRect.MakeLTRB(
                    minOf(sl, sr), minOf(st, sb),
                    maxOf(sl, sr), maxOf(st, sb),
                )
                drawPath(SkPath.Rect(srcRect), ctm, clip, paint)
                return
            }
        }
        val rect = SkRect.MakeLTRB(
            clip.left.toFloat(), clip.top.toFloat(),
            clip.right.toFloat(), clip.bottom.toFloat(),
        )
        drawRect(rect, clip, paint)
    }

    /**
     * `drawPath` for filled paths on GPU.
     *
     * G3.3a — single-contour convex polygon paths (Move + Line + Close),
     *         non-AA, fill-only ; fan tessellation from the first vertex.
     * G3.3b.1 — adds Bezier flattening : `kQuad`, `kCubic`, `kConic` verbs
     *           are subdivided into polyline segments in device space
     *           (port of `SkBitmapDevice.flatten{Quad,Cubic,Conic}`).
     *           After flattening, the same fan tessellation runs — so
     *           **convex curved paths** (ovals, circles, smooth blobs)
     *           render correctly, but **concave paths still get
     *           artefacts** until G3.3b.2 introduces ear-clipping or
     *           libtess2-like triangulation.
     *
     * AA path coverage (G3.3b.2) and stroke paths via SkStroker (G3.4)
     * remain TODO.
     */
    override fun drawPath(path: SkPath, ctm: SkMatrix, clip: SkIRect, paint: SkPaint) {
        // G4.1 / G4.1.1 — linear gradient fill of an axis-aligned rect
        // routes through the dedicated gradient pipeline, for all 4
        // SkTileMode values. SkCanvas sends shaded rect draws here (the
        // drawRect fast path requires `paint.shader == null`), so the
        // gate is `path.isRect() != null && ctm.isAxisAligned`. Rotated/
        // skewed CTMs and non-rect paths fall through to the existing
        // fill machinery, which will paint them as solid color (the
        // pre-G4.1 fallback) until a generic gradient-over-polygon
        // pipeline lands later in G4.
        val shader = paint.shader
        if (shader is SkLinearGradient &&
            paint.style == SkPaint.Style.kFill_Style &&
            ctm.isAxisAligned
        ) {
            val srcRect = path.isRect()
            if (srcRect != null) {
                val (x0, y0) = ctm.mapXY(srcRect.left, srcRect.top)
                val (x1, y1) = ctm.mapXY(srcRect.right, srcRect.bottom)
                val devRect = SkRect.MakeLTRB(
                    minOf(x0, x1), minOf(y0, y1),
                    maxOf(x0, x1), maxOf(y0, y1),
                )
                drawLinearGradientFillRect(devRect, clip, paint, shader, ctm)
                // Either emitted a draw or rect was outside clip ; either
                // way we own this paint and the fill machinery must not
                // re-render it as solid color.
                return
            }
        }
        // G4.2 / G4.2.1 -- radial gradient fill of an axis-aligned rect.
        // Same gate shape as the linear branch (path.isRect + axis-aligned
        // CTM), all 4 SkTileMode values routed through the dedicated
        // pipeline. Non-rect paths fall through to the existing solid-
        // color fill machinery (radial-on-non-rect lands in G4.2.2).
        if (shader is SkRadialGradient &&
            paint.style == SkPaint.Style.kFill_Style &&
            ctm.isAxisAligned
        ) {
            val srcRect = path.isRect()
            if (srcRect != null) {
                val (x0, y0) = ctm.mapXY(srcRect.left, srcRect.top)
                val (x1, y1) = ctm.mapXY(srcRect.right, srcRect.bottom)
                val devRect = SkRect.MakeLTRB(
                    minOf(x0, x1), minOf(y0, y1),
                    maxOf(x0, x1), maxOf(y0, y1),
                )
                drawRadialGradientFillRect(devRect, clip, paint, shader, ctm)
                return
            }
        }
        // G4.3 / G4.3.1 -- sweep gradient fill of an axis-aligned rect, all
        // 4 tile modes. Same gate shape as the linear / radial branches
        // (path.isRect + axis-aligned CTM). The pipeline cache picks the
        // matching fragment entry point per tile mode (G4.3.1 opened the
        // gate to fs_repeat / fs_mirror / fs_decal without touching the
        // cache key shape). Non-rect paths and rotated/skewed CTMs still
        // fall through to the existing solid-color fill machinery
        // (paint.color, pre-G4.3 fallback).
        if (shader is SkSweepGradient &&
            paint.style == SkPaint.Style.kFill_Style &&
            ctm.isAxisAligned
        ) {
            val srcRect = path.isRect()
            if (srcRect != null) {
                val (x0, y0) = ctm.mapXY(srcRect.left, srcRect.top)
                val (x1, y1) = ctm.mapXY(srcRect.right, srcRect.bottom)
                val devRect = SkRect.MakeLTRB(
                    minOf(x0, x1), minOf(y0, y1),
                    maxOf(x0, x1), maxOf(y0, y1),
                )
                drawSweepGradientFillRect(devRect, clip, paint, shader, ctm)
                return
            }
        }
        // G4.4 / G4.4.2 -- conical gradient fill of an axis-aligned rect.
        // Only the **kRadial** sub-case (concentric circles, `c0 == c1` ;
        // SkConicalGradient.Make tags this `Type.kRadial`) routes through
        // the dedicated pipeline today ; all 4 tile modes are wired (G4.4
        // landed kClamp only ; G4.4.2 widened the gate to kRepeat /
        // kMirror / kDecal -- the pipeline cache already wired all 4
        // entry points from day one). Other sub-cases (kStrip, kFocal in
        // any of its variants) fall through to the existing solid-color
        // fill machinery -- focal-inside is handled by G4.4.1 below.
        // Non-rect paths similarly defer to a later slice (G4.4.3 =
        // conical-on-non-rect-stencil-and-cover).
        if (shader is SkConicalGradient &&
            paint.style == SkPaint.Style.kFill_Style &&
            ctm.isAxisAligned &&
            shader.getType() == SkConicalGradient.Type.kRadial
        ) {
            val srcRect = path.isRect()
            if (srcRect != null) {
                val (x0, y0) = ctm.mapXY(srcRect.left, srcRect.top)
                val (x1, y1) = ctm.mapXY(srcRect.right, srcRect.bottom)
                val devRect = SkRect.MakeLTRB(
                    minOf(x0, x1), minOf(y0, y1),
                    maxOf(x0, x1), maxOf(y0, y1),
                )
                drawConicalGradientFillRect(devRect, clip, paint, shader, ctm)
                return
            }
        }
        // G4.4.1 / G4.4.2 / G4.4.5 / G4.4.6 -- conical focal sub-cases
        // routed through the focal-frame pipeline :
        //   - focal-inside well-behaved (`focalData.fR1 > 1`, not focal-
        //     on-circle, G4.4.1 / G4.4.2) -- the most common case.
        //   - focal-outside (`focalData.fR1 < 1`, not focal-on-circle,
        //     G4.4.5) -- "greater" / "smaller" raster-pipeline variants ;
        //     the shader picks the sign from `subCaseSign` and masks
        //     pixels outside the cone via the `in_cone` factor.
        //   - focal-on-circle (`|fR1 - 1| < tolerance`, G4.4.6) -- the
        //     focal point lies exactly on the end circle. The shader
        //     uses `t = (x*x + y*y) / x` and masks `x ~= 0` / `t <= 0`
        //     via the `in_cone` factor.
        // All 4 tile modes routed (the pipeline cache wired all 4 entry
        // points from day one).
        if (shader is SkConicalGradient &&
            paint.style == SkPaint.Style.kFill_Style &&
            ctm.isAxisAligned &&
            shader.getType() == SkConicalGradient.Type.kFocal &&
            shader.getFocalData()?.let {
                it.isWellBehaved() || it.isFocalOutside() || it.isFocalOnCircle()
            } == true
        ) {
            val srcRect = path.isRect()
            if (srcRect != null) {
                val (x0, y0) = ctm.mapXY(srcRect.left, srcRect.top)
                val (x1, y1) = ctm.mapXY(srcRect.right, srcRect.bottom)
                val devRect = SkRect.MakeLTRB(
                    minOf(x0, x1), minOf(y0, y1),
                    maxOf(x0, x1), maxOf(y0, y1),
                )
                if (drawConicalFocalGradientFillRect(devRect, clip, paint, shader, ctm)) {
                    return
                }
            }
        }
        // G4.4.4 -- conical kStrip sub-case (`r0 == r1`, `c0 != c1`).
        // All 4 tile modes routed -- the pipeline cache wires all 4
        // entry points from day one. Other sub-cases keep their
        // existing routing.
        if (shader is SkConicalGradient &&
            paint.style == SkPaint.Style.kFill_Style &&
            ctm.isAxisAligned &&
            shader.getType() == SkConicalGradient.Type.kStrip
        ) {
            val srcRect = path.isRect()
            if (srcRect != null) {
                val (x0, y0) = ctm.mapXY(srcRect.left, srcRect.top)
                val (x1, y1) = ctm.mapXY(srcRect.right, srcRect.bottom)
                val devRect = SkRect.MakeLTRB(
                    minOf(x0, x1), minOf(y0, y1),
                    maxOf(x0, x1), maxOf(y0, y1),
                )
                if (drawConicalStripGradientFillRect(devRect, clip, paint, shader, ctm)) {
                    return
                }
            }
        }
        // G5.2 -- `paint.shader is SkBitmapShader` fill of an axis-aligned
        // rect. Reuses the G5.1 / G5.1.1 drawImageRect pipeline (sampler
        // cache, pipeline cache, bitmap_shader.wgsl) by deriving an
        // (src, dst) pair from the shader's local matrix composed with
        // the CTM. Hard scope :
        //   - rect routing only (`path.isRect() != null`),
        //   - axis-aligned CTM,
        //   - shader local matrix is identity or axial scale + translate
        //     (no rotation / skew / perspective),
        //   - filter / tile / blend support inherited from G5.1.1.
        // Other configurations (rotated CTM, non-axis-aligned local
        // matrix, non-rect path) fall through to the existing solid-
        // colour fill machinery -- they are out of scope for the
        // current slice and will land in future G5.x. The dispatch sits
        // AFTER the gradient gates (gradients win when both are valid
        // ; in practice a paint carries one shader, not two).
        if (shader is SkBitmapShader &&
            paint.style == SkPaint.Style.kFill_Style &&
            ctm.isAxisAligned &&
            shader.localMatrix.isAxisAligned
        ) {
            val srcRect = path.isRect()
            if (srcRect != null) {
                if (drawBitmapShaderFillRect(srcRect, clip, paint, shader, ctm)) {
                    return
                }
            }
        }

        // G3.4.1 — stroke style : run SkStroker in source space to produce
        // the filled outline path, then recurse with a fill-style paint
        // copy. The outline is multi-contour for closed paths (outer +
        // reversed inner) and single-contour for open paths (left + cap +
        // reverse-right + cap), both already handled by the fill machinery
        // below (stencil-and-cover with kWinding takes care of multi-
        // contour ; the concave-single-contour route handles open paths).
        // kStrokeAndFill is the standard "fill then stroke" pair.
        if (paint.style != SkPaint.Style.kFill_Style) {
            val fillPaint = paint.copy().apply { style = SkPaint.Style.kFill_Style }
            val resScale = ctm.getMaxScale().coerceAtLeast(1f)
            if (paint.style == SkPaint.Style.kStrokeAndFill_Style) {
                drawPath(path, ctm, clip, fillPaint)
            }
            // G3.4.3 — true hairline : strokeWidth <= 0 in Skia means "1
            // device pixel regardless of CTM". SkStroker operates in source
            // space and would otherwise see `1f` from `fromPaint`'s default,
            // producing a 1-source-unit stroke (= CTM-scaled in device).
            // Synthesising `1 / resScale` makes the device-space width = 1px.
            val strokerPaint = if (paint.strokeWidth <= 0f) {
                paint.copy().apply { strokeWidth = 1f / resScale }
            } else {
                paint
            }
            val outline = SkStroker.fromPaint(strokerPaint, resScale).stroke(path)
            if (outline.isEmpty()) return
            drawPath(outline, ctm, clip, fillPaint)
            return
        }

        // Walk the verb stream once, transform each point by the CTM,
        // collect device-pixel vertices. Curves are subdivided in
        // device space via [flattenQuadInto] / [flattenCubicInto] /
        // [flattenConicInto] — same algorithms as
        // [org.skia.core.SkBitmapDevice.buildEdges] flatteners.
        // Multiple contours are flattened into a single vertex list
        // (the fan tessellator treats them as one polygon — fine for
        // convex single-contour paths, artefacts otherwise until G3.3b.2).
        val devVerts = ArrayList<Float>(path.verbs.size * 2)
        // Index (in vertex count, devVerts.size / 2) at which each contour
        // begins. G3.3b.2b's stencil-and-cover path fan-tessellates each
        // contour separately ; G3.3a's single-fan path uses only the
        // first entry.
        val contourStarts = ArrayList<Int>(4)
        var ci = 0
        var wi = 0
        var px = 0f; var py = 0f
        for (verb in path.verbs) {
            when (verb) {
                SkPath.Verb.kMove, SkPath.Verb.kLine -> {
                    val sx = path.coords[ci++]
                    val sy = path.coords[ci++]
                    val (dx, dy) = ctm.mapXY(sx, sy)
                    if (verb == SkPath.Verb.kMove) contourStarts.add(devVerts.size / 2)
                    devVerts.add(dx); devVerts.add(dy)
                    px = dx; py = dy
                }
                SkPath.Verb.kQuad -> {
                    val sx1 = path.coords[ci++]; val sy1 = path.coords[ci++]
                    val sx2 = path.coords[ci++]; val sy2 = path.coords[ci++]
                    val (x1, y1) = ctm.mapXY(sx1, sy1)
                    val (x2, y2) = ctm.mapXY(sx2, sy2)
                    flattenQuadInto(devVerts, px, py, x1, y1, x2, y2, 0)
                    px = x2; py = y2
                }
                SkPath.Verb.kCubic -> {
                    val sx1 = path.coords[ci++]; val sy1 = path.coords[ci++]
                    val sx2 = path.coords[ci++]; val sy2 = path.coords[ci++]
                    val sx3 = path.coords[ci++]; val sy3 = path.coords[ci++]
                    val (x1, y1) = ctm.mapXY(sx1, sy1)
                    val (x2, y2) = ctm.mapXY(sx2, sy2)
                    val (x3, y3) = ctm.mapXY(sx3, sy3)
                    flattenCubicInto(devVerts, px, py, x1, y1, x2, y2, x3, y3, 0)
                    px = x3; py = y3
                }
                SkPath.Verb.kConic -> {
                    val sx1 = path.coords[ci++]; val sy1 = path.coords[ci++]
                    val sx2 = path.coords[ci++]; val sy2 = path.coords[ci++]
                    val weight = path.conicWeights[wi++]
                    val (x1, y1) = ctm.mapXY(sx1, sy1)
                    val (x2, y2) = ctm.mapXY(sx2, sy2)
                    flattenConicInto(devVerts, px, py, x1, y1, x2, y2, weight)
                    px = x2; py = y2
                }
                SkPath.Verb.kClose -> { /* polygon closes implicitly on the fan */ }
                else -> error(
                    "SkWebGpuDevice (G3.3b.1): verb $verb not supported.",
                )
            }
        }
        val n = devVerts.size / 2
        if (n < 3) return // Degenerate : nothing to fill.

        // Honour the device clip via scissor (axis-aligned int clip).
        val scissorL = clip.left.coerceAtLeast(0)
        val scissorT = clip.top.coerceAtLeast(0)
        val scissorR = clip.right.coerceAtMost(width)
        val scissorB = clip.bottom.coerceAtMost(height)
        if (scissorL >= scissorR || scissorT >= scissorB) return

        val color = paint.color
        val rF = SkColorGetR(color) / 255f
        val gF = SkColorGetG(color) / 255f
        val bF = SkColorGetB(color) / 255f
        val aF = SkColorGetA(color) / 255f
        val scissor = intArrayOf(scissorL, scissorT, scissorR - scissorL, scissorB - scissorT)

        // G4.1.2 -- linear gradient on a non-rect AA path. The rect+axis-
        // aligned-CTM shortcut at the top of drawPath already peeled off
        // the [LinearGradientRectDraw] case ; we now route every AA fill
        // that carries a SkLinearGradient through stencil-and-cover with
        // the gradient cover-pipeline (factoring choice "b" of the G4.1.2
        // plan : forces AA convex single-contour gradient fills through
        // stencil-and-cover too, accepting one extra stencil pass per
        // such draw in exchange for one shared dispatch instead of two
        // parallel ones in `aa_polygon.wgsl`). Non-AA paths keep the
        // solid-color fall-through ; non-axis-aligned CTMs and shader
        // types other than [SkLinearGradient] do likewise.
        //
        // G4.2.2 -- mirror of the above for radial gradients : same gate
        // shape, same stencil-and-cover route, separate (center, radius)
        // payload + dedicated [StencilCoverAaRadialGradientDraw] type.
        //
        // G4.3.2 -- mirror of the above for sweep gradients : same gate
        // shape, same stencil-and-cover route, separate
        // (center, tBias, tScale) payload + dedicated
        // [StencilCoverAaSweepGradientDraw] type.
        //
        // G4.4.3 -- mirror of the above for conical gradients : same gate
        // shape, same stencil-and-cover route, two payload shapes (one for
        // kRadial = `(center, r0, r1)`, one for focal-inside = a 2x3
        // device->focal affine + focal scalars + flags), dedicated
        // [StencilCoverAaConicalGradientDraw] and
        // [StencilCoverAaConicalFocalGradientDraw] types. Only the kRadial
        // and focal-inside-well-behaved sub-cases route here today, all
        // 4 tile modes (mirrors the rect-only G4.4.2 widening once it
        // lands ; the shader's 8 entry points are wired up regardless).
        val linearGradForAaPath: SkLinearGradient? =
            if (shader is SkLinearGradient && paint.isAntiAlias && ctm.isAxisAligned) shader
            else null
        val radialGradForAaPath: SkRadialGradient? =
            if (shader is SkRadialGradient && paint.isAntiAlias && ctm.isAxisAligned) shader
            else null
        val sweepGradForAaPath: SkSweepGradient? =
            if (shader is SkSweepGradient && paint.isAntiAlias && ctm.isAxisAligned) shader
            else null
        val conicalRadialGradForAaPath: SkConicalGradient? =
            if (shader is SkConicalGradient && paint.isAntiAlias && ctm.isAxisAligned &&
                shader.getTileMode() == SkTileMode.kClamp &&
                shader.getType() == SkConicalGradient.Type.kRadial
            ) shader
            else null
        val conicalFocalGradForAaPath: SkConicalGradient? =
            if (shader is SkConicalGradient && paint.isAntiAlias && ctm.isAxisAligned &&
                shader.getTileMode() == SkTileMode.kClamp &&
                shader.getType() == SkConicalGradient.Type.kFocal &&
                shader.getFocalData()?.let {
                    it.isWellBehaved() || it.isFocalOutside() || it.isFocalOnCircle()
                } == true
            ) shader
            else null
        var gradEndpoints: FloatArray? = null
        var gradCenterRadius: FloatArray? = null
        var gradCenterBiasScale: FloatArray? = null
        var gradConicalCenterRadii: FloatArray? = null
        var gradConicalFocalAffine: FloatArray? = null
        var gradConicalFocalScalars: FloatArray? = null
        var gradPositions: FloatArray? = null
        var gradColors: FloatArray? = null
        var gradStopCount: Int = 0
        var gradTileMode: SkTileMode? = null
        if (linearGradForAaPath != null ||
            radialGradForAaPath != null ||
            sweepGradForAaPath != null ||
            conicalRadialGradForAaPath != null ||
            conicalFocalGradForAaPath != null
        ) {
            val srcColors: IntArray
            val positions: FloatArray
            if (linearGradForAaPath != null) {
                val p0 = linearGradForAaPath.getStartPoint()
                val p1 = linearGradForAaPath.getEndPoint()
                val (sx, sy) = ctm.mapXY(p0.fX, p0.fY)
                val (ex, ey) = ctm.mapXY(p1.fX, p1.fY)
                gradEndpoints = floatArrayOf(sx, sy, ex, ey)
                srcColors = linearGradForAaPath.getColors()
                positions = linearGradForAaPath.getPositions()
                gradTileMode = linearGradForAaPath.getTileMode()
            } else if (radialGradForAaPath != null) {
                val center = radialGradForAaPath.getCenter()
                val (cx, cy) = ctm.mapXY(center.fX, center.fY)
                val devRadius = radialGradForAaPath.getRadius() * ctm.getMaxScale()
                gradCenterRadius = floatArrayOf(cx, cy, devRadius)
                srcColors = radialGradForAaPath.getColors()
                positions = radialGradForAaPath.getPositions()
                gradTileMode = radialGradForAaPath.getTileMode()
            } else if (sweepGradForAaPath != null) {
                // G4.3.2 -- sweep gradient on non-rect path. Centre maps
                // through the axis-aligned CTM to device space. Angles
                // (startAngle / endAngle) are in source-space degrees ; the
                // axis-aligned-CTM gate preserves angular orientation so
                // they pass through unchanged.
                val grad = sweepGradForAaPath
                val center = grad.getCenter()
                val (cx, cy) = ctm.mapXY(center.fX, center.fY)
                val tBias = -grad.getStartAngle() / 360f
                val tScale = 360f / (grad.getEndAngle() - grad.getStartAngle())
                gradCenterBiasScale = floatArrayOf(cx, cy, tBias, tScale)
                srcColors = grad.getColors()
                positions = grad.getPositions()
                gradTileMode = grad.getTileMode()
            } else if (conicalRadialGradForAaPath != null) {
                // G4.4.3 -- kRadial sub-case on non-rect path. Centre maps
                // through the axis-aligned CTM ; both radii scale by
                // `ctm.getMaxScale()` (collapses to `max(|sx|, |sy|)` under
                // the axis-aligned gate), mirroring the rect-only G4.4
                // drawConicalGradientFillRect.
                val grad = conicalRadialGradForAaPath
                val end = grad.getEnd()
                val (cx, cy) = ctm.mapXY(end.fX, end.fY)
                val maxScale = ctm.getMaxScale()
                gradConicalCenterRadii = floatArrayOf(
                    cx, cy, grad.getStartRadius() * maxScale, grad.getEndRadius() * maxScale,
                )
                srcColors = grad.getColors()
                positions = grad.getPositions()
                gradTileMode = grad.getTileMode()
            } else {
                // G4.4.3 -- focal-inside well-behaved. Same focal-frame
                // mapping as the rect-only G4.4.1 path : compose
                // `gradientMatrix * (CTM * localMatrix)^-1` once per draw
                // and pass it as a 2x3 affine ; the shader applies the
                // well-behaved formula + the static post-passes
                // (`negate_x`, `compensateFocal`, `unswap`) read from
                // per-draw flags.
                val grad = conicalFocalGradForAaPath!!
                val ctmLocal = ctm.preConcat(grad.localMatrix)
                val invCtmLocal = ctmLocal.invert()
                val fd = grad.getFocalData()
                srcColors = grad.getColors()
                positions = grad.getPositions()
                gradTileMode = grad.getTileMode()
                if (invCtmLocal != null && fd != null) {
                    val devToFocal = grad.getGradientMatrix().preConcat(invCtmLocal)
                    gradConicalFocalAffine = floatArrayOf(
                        devToFocal.sx, devToFocal.kx, devToFocal.tx,
                        devToFocal.ky, devToFocal.sy, devToFocal.ty,
                    )
                    val fP0 = 1f / fd.fR1
                    val fP1 = fd.fFocalX
                    val compensate = if (!fd.isNativelyFocal()) 1f else 0f
                    val unswap = if (fd.isSwapped()) 1f else 0f
                    val negateX = if ((1f - fP1) < 0f) 1f else 0f
                    // G4.4.5 / G4.4.6 -- sub-case selector. Mirrors the
                    // rect-only path : 0 = well-behaved, 1 = focal-
                    // outside (greater / smaller via subCaseSign), 2 =
                    // focal-on-circle.
                    val subCase = when {
                        fd.isFocalOnCircle() -> 2f
                        fd.isFocalOutside() -> 1f
                        else -> 0f
                    }
                    val subCaseSign =
                        if (fd.isFocalOutside() && fd.isFocalOutsideSmaller()) -1f else 1f
                    gradConicalFocalScalars = floatArrayOf(
                        fP0, fP1, compensate, unswap, negateX, subCase, subCaseSign,
                    )
                }
                // If invCtmLocal / fd is null, gradConicalFocalAffine
                // stays null and the dispatch falls through to solid color.
            }
            val count = srcColors.size.coerceAtMost(MAX_GRADIENT_STOPS)
            val packedPositions = FloatArray(MAX_GRADIENT_STOPS * 4)
            val packedColors = FloatArray(MAX_GRADIENT_STOPS * 4)
            for (i in 0 until count) {
                packedPositions[i * 4] = positions[i]
                val c = srcColors[i]
                val ca = SkColorGetA(c) / 255f
                val cr = SkColorGetR(c) / 255f
                val cg = SkColorGetG(c) / 255f
                val cb = SkColorGetB(c) / 255f
                packedColors[i * 4]     = cr * ca
                packedColors[i * 4 + 1] = cg * ca
                packedColors[i * 4 + 2] = cb * ca
                packedColors[i * 4 + 3] = ca
            }
            gradPositions = packedPositions
            gradColors = packedColors
            gradStopCount = count
        }
        // G4.4.3 -- conical focal-inside falls back to solid color when
        // `(CTM * localMatrix)` is singular ; treat as null gradient for
        // dispatch purposes.
        val conicalFocalActive: Boolean =
            conicalFocalGradForAaPath != null && gradConicalFocalAffine != null

        // G5.2.1 -- bitmap shader on a non-rect AA path. Mirror of the
        // gradient gate above : axis-aligned CTM + axis-aligned local
        // matrix + AA paint. The rect-on-axis-aligned-CTM case was
        // already peeled off at the top of drawPath (G5.2's
        // [drawBitmapShaderFillRect]) ; we route every remaining AA
        // bitmap-shader fill through the dedicated stencil-and-cover
        // pipeline. Non-AA paths and non-axis-aligned matrices fall
        // through to the existing solid-colour fill machinery.
        val bitmapShaderForAaPath: SkBitmapShader? =
            if (shader is SkBitmapShader && paint.isAntiAlias && ctm.isAxisAligned &&
                shader.localMatrix.isAxisAligned
            ) shader
            else null
        var bitmapPayload: BitmapShaderPayload? = null
        if (bitmapShaderForAaPath != null) {
            bitmapPayload = buildBitmapShaderPayload(bitmapShaderForAaPath, ctm, paint)
        }

        // G3.3b.2b — multi-contour path : stencil-and-cover. Each contour
        // is fan-tessellated from its own first vertex ; the concatenated
        // triangle list is rendered into the stencil buffer with
        // increment/decrement-by-face → stencil holds the winding count.
        // The cover pass writes color where stencil != 0 (kWinding fill).
        // Naturally handles holes (opposite-winding inner contour
        // subtracts from outer winding count).
        if (contourStarts.size > 1) {
            val stencilTri = fanTessellateContours(devVerts, contourStarts)
            // G3.3b.3b — inverse fill types fill the viewport MINUS the
            // path region, so the cover quad spans the full viewport
            // (scissored to the clip rect). Non-inverse keeps the path
            // bbox as before.
            val coverTri = if (path.fillType.isInverse()) {
                viewportTrianglesFor(width, height)
            } else {
                bboxTrianglesFor(devVerts, width, height)
            }
            val totalVerts = devVerts.size / 2
            if (paint.isAntiAlias && totalVerts <= MAX_AA_EDGES) {
                val edges = FloatArray(MAX_AA_EDGES * 4)
                buildContourEdgeSegments(devVerts, contourStarts, edges)
                if (linearGradForAaPath != null) {
                    pending.add(
                        StencilCoverAaGradientDraw(
                            stencilVerts = stencilTri,
                            coverVerts = coverTri,
                            edges = edges,
                            edgeCount = totalVerts,
                            scissor = scissor,
                            fillType = path.fillType,
                            startX = gradEndpoints!![0], startY = gradEndpoints[1],
                            endX = gradEndpoints[2], endY = gradEndpoints[3],
                            stopPositions = gradPositions!!,
                            stopColors = gradColors!!,
                            stopCount = gradStopCount,
                            tileMode = gradTileMode!!,
                            r = rF, g = gF, b = bF, a = aF,
                            mode = paint.blendMode,
                        ),
                    )
                    return
                }
                if (radialGradForAaPath != null) {
                    pending.add(
                        StencilCoverAaRadialGradientDraw(
                            stencilVerts = stencilTri,
                            coverVerts = coverTri,
                            edges = edges,
                            edgeCount = totalVerts,
                            scissor = scissor,
                            fillType = path.fillType,
                            centerX = gradCenterRadius!![0],
                            centerY = gradCenterRadius[1],
                            radius = gradCenterRadius[2],
                            stopPositions = gradPositions!!,
                            stopColors = gradColors!!,
                            stopCount = gradStopCount,
                            tileMode = gradTileMode!!,
                            r = rF, g = gF, b = bF, a = aF,
                            mode = paint.blendMode,
                        ),
                    )
                    return
                }
                if (sweepGradForAaPath != null) {
                    pending.add(
                        StencilCoverAaSweepGradientDraw(
                            stencilVerts = stencilTri,
                            coverVerts = coverTri,
                            edges = edges,
                            edgeCount = totalVerts,
                            scissor = scissor,
                            fillType = path.fillType,
                            centerX = gradCenterBiasScale!![0],
                            centerY = gradCenterBiasScale[1],
                            tBias = gradCenterBiasScale[2],
                            tScale = gradCenterBiasScale[3],
                            stopPositions = gradPositions!!,
                            stopColors = gradColors!!,
                            stopCount = gradStopCount,
                            tileMode = gradTileMode!!,
                            r = rF, g = gF, b = bF, a = aF,
                            mode = paint.blendMode,
                        ),
                    )
                    return
                }
                if (conicalRadialGradForAaPath != null) {
                    pending.add(
                        StencilCoverAaConicalGradientDraw(
                            stencilVerts = stencilTri,
                            coverVerts = coverTri,
                            edges = edges,
                            edgeCount = totalVerts,
                            scissor = scissor,
                            fillType = path.fillType,
                            centerX = gradConicalCenterRadii!![0],
                            centerY = gradConicalCenterRadii[1],
                            startRadius = gradConicalCenterRadii[2],
                            endRadius = gradConicalCenterRadii[3],
                            stopPositions = gradPositions!!,
                            stopColors = gradColors!!,
                            stopCount = gradStopCount,
                            tileMode = gradTileMode!!,
                            r = rF, g = gF, b = bF, a = aF,
                            mode = paint.blendMode,
                        ),
                    )
                    return
                }
                if (conicalFocalActive) {
                    val affine: FloatArray = gradConicalFocalAffine!!
                    val scalars: FloatArray = gradConicalFocalScalars!!
                    pending.add(
                        StencilCoverAaConicalFocalGradientDraw(
                            stencilVerts = stencilTri,
                            coverVerts = coverTri,
                            edges = edges,
                            edgeCount = totalVerts,
                            scissor = scissor,
                            fillType = path.fillType,
                            affine00 = affine[0], affine01 = affine[1], affine02 = affine[2],
                            affine10 = affine[3], affine11 = affine[4], affine12 = affine[5],
                            fP0 = scalars[0], fP1 = scalars[1],
                            compensateFocal = scalars[2], unswap = scalars[3],
                            negateX = scalars[4],
                            subCase = scalars[5], subCaseSign = scalars[6],
                            stopPositions = gradPositions!!,
                            stopColors = gradColors!!,
                            stopCount = gradStopCount,
                            tileMode = gradTileMode!!,
                            r = rF, g = gF, b = bF, a = aF,
                            mode = paint.blendMode,
                        ),
                    )
                    return
                }
                if (bitmapPayload != null) {
                    pending.add(
                        bitmapPayload.toStencilCoverDraw(
                            stencilVerts = stencilTri,
                            coverVerts = coverTri,
                            edges = edges,
                            edgeCount = totalVerts,
                            scissor = scissor,
                            fillType = path.fillType,
                            mode = paint.blendMode,
                        ),
                    )
                    return
                }
                pending.add(
                    StencilCoverAaPolygonDraw(
                        stencilVerts = stencilTri,
                        coverVerts = coverTri,
                        edges = edges,
                        edgeCount = totalVerts,
                        scissor = scissor,
                        fillType = path.fillType,
                        r = rF, g = gF, b = bF, a = aF,
                        mode = paint.blendMode,
                    ),
                )
                return
            }
            pending.add(
                StencilCoverPolygonDraw(
                    stencilVerts = stencilTri,
                    coverVerts = coverTri,
                    scissor = scissor,
                    fillType = path.fillType,
                    r = rF, g = gF, b = bF, a = aF,
                    mode = paint.blendMode,
                ),
            )
            return
        }

        // Single-contour. Inverse fills OR concave shapes route through
        // stencil-and-cover (fan-tess winding cancels in concave pockets ;
        // the stencil decides inside vs outside). Convex non-inverse
        // paths keep the cheap fan-tess + AA-polygon fast paths.
        if (path.fillType.isInverse() || !isPolygonConvex(devVerts)) {
            val stencilTri = fanTessellateContours(devVerts, contourStarts)
            val coverTri = if (path.fillType.isInverse()) {
                viewportTrianglesFor(width, height)
            } else {
                bboxTrianglesFor(devVerts, width, height)
            }
            if (paint.isAntiAlias && n <= MAX_AA_EDGES) {
                val edges = FloatArray(MAX_AA_EDGES * 4)
                buildContourEdgeSegments(devVerts, contourStarts, edges)
                if (linearGradForAaPath != null) {
                    pending.add(
                        StencilCoverAaGradientDraw(
                            stencilVerts = stencilTri,
                            coverVerts = coverTri,
                            edges = edges,
                            edgeCount = n,
                            scissor = scissor,
                            fillType = path.fillType,
                            startX = gradEndpoints!![0], startY = gradEndpoints[1],
                            endX = gradEndpoints[2], endY = gradEndpoints[3],
                            stopPositions = gradPositions!!,
                            stopColors = gradColors!!,
                            stopCount = gradStopCount,
                            tileMode = gradTileMode!!,
                            r = rF, g = gF, b = bF, a = aF,
                            mode = paint.blendMode,
                        ),
                    )
                } else if (radialGradForAaPath != null) {
                    pending.add(
                        StencilCoverAaRadialGradientDraw(
                            stencilVerts = stencilTri,
                            coverVerts = coverTri,
                            edges = edges,
                            edgeCount = n,
                            scissor = scissor,
                            fillType = path.fillType,
                            centerX = gradCenterRadius!![0],
                            centerY = gradCenterRadius[1],
                            radius = gradCenterRadius[2],
                            stopPositions = gradPositions!!,
                            stopColors = gradColors!!,
                            stopCount = gradStopCount,
                            tileMode = gradTileMode!!,
                            r = rF, g = gF, b = bF, a = aF,
                            mode = paint.blendMode,
                        ),
                    )
                } else if (sweepGradForAaPath != null) {
                    pending.add(
                        StencilCoverAaSweepGradientDraw(
                            stencilVerts = stencilTri,
                            coverVerts = coverTri,
                            edges = edges,
                            edgeCount = n,
                            scissor = scissor,
                            fillType = path.fillType,
                            centerX = gradCenterBiasScale!![0],
                            centerY = gradCenterBiasScale[1],
                            tBias = gradCenterBiasScale[2],
                            tScale = gradCenterBiasScale[3],
                            stopPositions = gradPositions!!,
                            stopColors = gradColors!!,
                            stopCount = gradStopCount,
                            tileMode = gradTileMode!!,
                            r = rF, g = gF, b = bF, a = aF,
                            mode = paint.blendMode,
                        ),
                    )
                } else if (conicalRadialGradForAaPath != null) {
                    pending.add(
                        StencilCoverAaConicalGradientDraw(
                            stencilVerts = stencilTri,
                            coverVerts = coverTri,
                            edges = edges,
                            edgeCount = n,
                            scissor = scissor,
                            fillType = path.fillType,
                            centerX = gradConicalCenterRadii!![0],
                            centerY = gradConicalCenterRadii[1],
                            startRadius = gradConicalCenterRadii[2],
                            endRadius = gradConicalCenterRadii[3],
                            stopPositions = gradPositions!!,
                            stopColors = gradColors!!,
                            stopCount = gradStopCount,
                            tileMode = gradTileMode!!,
                            r = rF, g = gF, b = bF, a = aF,
                            mode = paint.blendMode,
                        ),
                    )
                } else if (conicalFocalActive) {
                    val affine: FloatArray = gradConicalFocalAffine!!
                    val scalars: FloatArray = gradConicalFocalScalars!!
                    pending.add(
                        StencilCoverAaConicalFocalGradientDraw(
                            stencilVerts = stencilTri,
                            coverVerts = coverTri,
                            edges = edges,
                            edgeCount = n,
                            scissor = scissor,
                            fillType = path.fillType,
                            affine00 = affine[0], affine01 = affine[1], affine02 = affine[2],
                            affine10 = affine[3], affine11 = affine[4], affine12 = affine[5],
                            fP0 = scalars[0], fP1 = scalars[1],
                            compensateFocal = scalars[2], unswap = scalars[3],
                            negateX = scalars[4],
                            subCase = scalars[5], subCaseSign = scalars[6],
                            stopPositions = gradPositions!!,
                            stopColors = gradColors!!,
                            stopCount = gradStopCount,
                            tileMode = gradTileMode!!,
                            r = rF, g = gF, b = bF, a = aF,
                            mode = paint.blendMode,
                        ),
                    )
                } else if (bitmapPayload != null) {
                    pending.add(
                        bitmapPayload.toStencilCoverDraw(
                            stencilVerts = stencilTri,
                            coverVerts = coverTri,
                            edges = edges,
                            edgeCount = n,
                            scissor = scissor,
                            fillType = path.fillType,
                            mode = paint.blendMode,
                        ),
                    )
                } else {
                    pending.add(
                        StencilCoverAaPolygonDraw(
                            stencilVerts = stencilTri,
                            coverVerts = coverTri,
                            edges = edges,
                            edgeCount = n,
                            scissor = scissor,
                            fillType = path.fillType,
                            r = rF, g = gF, b = bF, a = aF,
                            mode = paint.blendMode,
                        ),
                    )
                }
            } else {
                pending.add(
                    StencilCoverPolygonDraw(
                        stencilVerts = stencilTri,
                        coverVerts = coverTri,
                        scissor = scissor,
                        fillType = path.fillType,
                        r = rF, g = gF, b = bF, a = aF,
                        mode = paint.blendMode,
                    ),
                )
            }
            return
        }

        // Single-contour, convex, non-inverse : cheap fan tess.
        val triCount = n - 2
        val tri = FloatArray(triCount * 6)
        var w = 0
        for (i in 1 until n - 1) {
            tri[w++] = devVerts[0]; tri[w++] = devVerts[1]
            tri[w++] = devVerts[i * 2]; tri[w++] = devVerts[i * 2 + 1]
            tri[w++] = devVerts[(i + 1) * 2]; tri[w++] = devVerts[(i + 1) * 2 + 1]
        }

        // G3.3b.2a — convex single-contour AA : render a 1-pixel-inflated
        // bbox quad and let the fragment shader's `min` over signed
        // perpendicular edge distances mask it down to the polygon, with
        // sub-pixel coverage falloff on the perimeter.
        //
        // G4.1.2 / G4.2.2 — convex AA paths with a gradient paint detour
        // through stencil-and-cover (factoring choice "b" of the G4.1.2
        // plan : we don't extend `aa_polygon.wgsl` with gradient entry
        // points, and the G4.2.2 follow-up keeps the same trade-off for
        // radial). The extra stencil pass is one of two parallel cover
        // sub-draws either way ; the cost difference is one stencil-write
        // pass per convex gradient path, accepted for the simpler diff.
        if (paint.isAntiAlias && n <= MAX_AA_EDGES && linearGradForAaPath != null) {
            val stencilTri = fanTessellateContours(devVerts, contourStarts)
            val coverTri = bboxTrianglesFor(devVerts, width, height)
            val edges = FloatArray(MAX_AA_EDGES * 4)
            buildContourEdgeSegments(devVerts, contourStarts, edges)
            pending.add(
                StencilCoverAaGradientDraw(
                    stencilVerts = stencilTri,
                    coverVerts = coverTri,
                    edges = edges,
                    edgeCount = n,
                    scissor = scissor,
                    fillType = path.fillType,
                    startX = gradEndpoints!![0], startY = gradEndpoints[1],
                    endX = gradEndpoints[2], endY = gradEndpoints[3],
                    stopPositions = gradPositions!!,
                    stopColors = gradColors!!,
                    stopCount = gradStopCount,
                    tileMode = gradTileMode!!,
                    r = rF, g = gF, b = bF, a = aF,
                    mode = paint.blendMode,
                ),
            )
            return
        }
        if (paint.isAntiAlias && n <= MAX_AA_EDGES && radialGradForAaPath != null) {
            val stencilTri = fanTessellateContours(devVerts, contourStarts)
            val coverTri = bboxTrianglesFor(devVerts, width, height)
            val edges = FloatArray(MAX_AA_EDGES * 4)
            buildContourEdgeSegments(devVerts, contourStarts, edges)
            pending.add(
                StencilCoverAaRadialGradientDraw(
                    stencilVerts = stencilTri,
                    coverVerts = coverTri,
                    edges = edges,
                    edgeCount = n,
                    scissor = scissor,
                    fillType = path.fillType,
                    centerX = gradCenterRadius!![0],
                    centerY = gradCenterRadius[1],
                    radius = gradCenterRadius[2],
                    stopPositions = gradPositions!!,
                    stopColors = gradColors!!,
                    stopCount = gradStopCount,
                    tileMode = gradTileMode!!,
                    r = rF, g = gF, b = bF, a = aF,
                    mode = paint.blendMode,
                ),
            )
            return
        }
        // G4.3.2 -- sweep gradient on a convex AA path. Same factoring
        // choice as G4.1.2 / G4.2.2 : detour through stencil-and-cover
        // with the dedicated sweep cover pipeline rather than extending
        // `aa_polygon.wgsl` with gradient entry points.
        if (paint.isAntiAlias && n <= MAX_AA_EDGES && sweepGradForAaPath != null) {
            val stencilTri = fanTessellateContours(devVerts, contourStarts)
            val coverTri = bboxTrianglesFor(devVerts, width, height)
            val edges = FloatArray(MAX_AA_EDGES * 4)
            buildContourEdgeSegments(devVerts, contourStarts, edges)
            pending.add(
                StencilCoverAaSweepGradientDraw(
                    stencilVerts = stencilTri,
                    coverVerts = coverTri,
                    edges = edges,
                    edgeCount = n,
                    scissor = scissor,
                    fillType = path.fillType,
                    centerX = gradCenterBiasScale!![0],
                    centerY = gradCenterBiasScale[1],
                    tBias = gradCenterBiasScale[2],
                    tScale = gradCenterBiasScale[3],
                    stopPositions = gradPositions!!,
                    stopColors = gradColors!!,
                    stopCount = gradStopCount,
                    tileMode = gradTileMode!!,
                    r = rF, g = gF, b = bF, a = aF,
                    mode = paint.blendMode,
                ),
            )
            return
        }
        // G4.4.3 -- conical gradient (kRadial sub-case) on a convex AA path.
        // Same factoring as G4.1.2 / G4.2.2 / G4.3.2 : detour through
        // stencil-and-cover with the dedicated conical cover pipeline.
        if (paint.isAntiAlias && n <= MAX_AA_EDGES && conicalRadialGradForAaPath != null) {
            val stencilTri = fanTessellateContours(devVerts, contourStarts)
            val coverTri = bboxTrianglesFor(devVerts, width, height)
            val edges = FloatArray(MAX_AA_EDGES * 4)
            buildContourEdgeSegments(devVerts, contourStarts, edges)
            pending.add(
                StencilCoverAaConicalGradientDraw(
                    stencilVerts = stencilTri,
                    coverVerts = coverTri,
                    edges = edges,
                    edgeCount = n,
                    scissor = scissor,
                    fillType = path.fillType,
                    centerX = gradConicalCenterRadii!![0],
                    centerY = gradConicalCenterRadii[1],
                    startRadius = gradConicalCenterRadii[2],
                    endRadius = gradConicalCenterRadii[3],
                    stopPositions = gradPositions!!,
                    stopColors = gradColors!!,
                    stopCount = gradStopCount,
                    tileMode = gradTileMode!!,
                    r = rF, g = gF, b = bF, a = aF,
                    mode = paint.blendMode,
                ),
            )
            return
        }
        // G4.4.3 -- conical focal-inside on a convex AA path. Mirrors the
        // kRadial arm above with the focal-frame payload.
        if (paint.isAntiAlias && n <= MAX_AA_EDGES && conicalFocalActive) {
            val stencilTri = fanTessellateContours(devVerts, contourStarts)
            val coverTri = bboxTrianglesFor(devVerts, width, height)
            val edges = FloatArray(MAX_AA_EDGES * 4)
            buildContourEdgeSegments(devVerts, contourStarts, edges)
            val affine: FloatArray = gradConicalFocalAffine!!
            val scalars: FloatArray = gradConicalFocalScalars!!
            pending.add(
                StencilCoverAaConicalFocalGradientDraw(
                    stencilVerts = stencilTri,
                    coverVerts = coverTri,
                    edges = edges,
                    edgeCount = n,
                    scissor = scissor,
                    fillType = path.fillType,
                    affine00 = affine[0], affine01 = affine[1], affine02 = affine[2],
                    affine10 = affine[3], affine11 = affine[4], affine12 = affine[5],
                    fP0 = scalars[0], fP1 = scalars[1],
                    compensateFocal = scalars[2], unswap = scalars[3],
                    negateX = scalars[4],
                    subCase = scalars[5], subCaseSign = scalars[6],
                    stopPositions = gradPositions!!,
                    stopColors = gradColors!!,
                    stopCount = gradStopCount,
                    tileMode = gradTileMode!!,
                    r = rF, g = gF, b = bF, a = aF,
                    mode = paint.blendMode,
                ),
            )
            return
        }
        // G5.2.1 -- bitmap shader on a convex AA path. Mirror of the
        // gradient arms above ; detour through stencil-and-cover with
        // the dedicated bitmap-shader cover pipeline.
        if (paint.isAntiAlias && n <= MAX_AA_EDGES && bitmapPayload != null) {
            val stencilTri = fanTessellateContours(devVerts, contourStarts)
            val coverTri = bboxTrianglesFor(devVerts, width, height)
            val edges = FloatArray(MAX_AA_EDGES * 4)
            buildContourEdgeSegments(devVerts, contourStarts, edges)
            pending.add(
                bitmapPayload.toStencilCoverDraw(
                    stencilVerts = stencilTri,
                    coverVerts = coverTri,
                    edges = edges,
                    edgeCount = n,
                    scissor = scissor,
                    fillType = path.fillType,
                    mode = paint.blendMode,
                ),
            )
            return
        }
        if (paint.isAntiAlias && n <= MAX_AA_EDGES) {
            val edges = FloatArray(MAX_AA_EDGES * 4)
            buildPerimeterEdges(devVerts, edges)
            val bboxTri = bboxTrianglesFor(devVerts, width, height)
            pending.add(
                AaPolygonDraw(
                    verts = bboxTri,
                    edges = edges,
                    edgeCount = n,
                    scissor = scissor,
                    r = rF, g = gF, b = bF, a = aF,
                    mode = paint.blendMode,
                ),
            )
        } else {
            pending.add(
                PolygonDraw(
                    verts = tri,
                    scissor = scissor,
                    r = rF, g = gF, b = bF, a = aF,
                    mode = paint.blendMode,
                ),
            )
        }
    }

    /**
     * G5.1 -- first GPU `drawImageRect` slice. Routes the call to the
     * bitmap-shader pipeline when the (sampling, tile, blend) tuple is
     * in scope :
     *  - filter = kLinear ; mipmap = kNone ; cubic = null ; no aniso.
     *  - tile mode = kClamp (the default SrcRectConstraint clamp + the
     *    sampler's ClampToEdge address mode).
     *  - blend mode = kSrcOver (or null paint, defaulting to SrcOver).
     *
     * Out-of-scope combinations throw with a pointer to the G5.x
     * follow-up plan -- they're not supposed to reach this overload
     * yet (the high-level dispatch in `SkBitmapDevice` / `SkCanvas`
     * routes the in-scope GMs through here). Extending the cache
     * keys is structural-only (the data class already carries the
     * (filter, tile, blend) needed) ; the follow-up slices wire each
     * combination through.
     *
     * **Dispatch shape.** Scissor follows the non-AA fill convention :
     * pixelEdge-rounded integer bounds (`floor(coord + 0.5)`) clipped
     * to the user clip and the viewport. AA-on-the-edges is not yet
     * wired up -- the fragment stage runs unconditionally inside the
     * scissor so the edge-pixel falloff is the sampler's bilinear job
     * (which already gives sub-pixel softening when src / dst scales
     * are non-integer). Future slices will add an outer scissor +
     * shader-side analytical coverage à la `solid_color.wgsl` for the
     * `paint.isAntiAlias` case.
     *
     * **Paint shader.** For G5.1 the bitmap shader IS the paint shader ;
     * a `paint.shader` that's a `SkBitmapShader` is NOT yet routed
     * through here (that's G5.2). The `paint` parameter only contributes
     * its alpha / colour modulation (folded into `paintColor` in the
     * uniform) and its blend mode.
     */
    override fun drawImageRect(
        image: SkImage,
        src: SkRect,
        devDst: SkRect,
        sampling: SkSamplingOptions,
        paint: SkPaint?,
        constraint: SrcRectConstraint,
        clip: SkIRect,
    ) {
        // kClamp is the default tile mode for the SkCanvas.drawImageRect
        // contract -- the SrcRectConstraint distinction (kStrict vs kFast)
        // is a sub-pixel filter-overflow tweak ; both treat out-of-src
        // samples as "clamp at src edge". Tests can route the alternative
        // tile modes via [enqueueImageRectDrawForTest].
        enqueueImageRectDrawInternal(
            image = image,
            src = src,
            devDst = devDst,
            sampling = sampling,
            paint = paint,
            tileX = SkTileMode.kClamp,
            tileY = SkTileMode.kClamp,
            clip = clip,
        )
    }

    /**
     * G5.1.1 -- shared dispatch entry-point for [drawImageRect] and the
     * test-only [enqueueImageRectDrawForTest] hook. Validates the
     * (sampling, blend, tile) tuple against the bitmap-shader pipeline's
     * current capability matrix, then enqueues an [ImageRectDraw].
     *
     * In scope :
     *  - filter = `SkFilterMode.kLinear` or `SkFilterMode.kNearest`
     *  - mipmap = `SkMipmapMode.kNone` ; no bicubic ; no anisotropic.
     *  - tile mode = `SkTileMode.kClamp` / `kRepeat` / `kMirror` /
     *                `kDecal` (latter handled in-shader ; sampler stays
     *                ClampToEdge because WebGPU has no `BorderColor`
     *                mode for non-depth sampled textures).
     *  - blend mode = `SkBlendMode.kClear` / `kSrc` / `kSrcOver` /
     *                 `kDstOver` (the natively-blendable subset that
     *                 [blendStateFor] expresses without a fragment-side
     *                 round-trip).
     */
    private fun enqueueImageRectDrawInternal(
        image: SkImage,
        src: SkRect,
        devDst: SkRect,
        sampling: SkSamplingOptions,
        paint: SkPaint?,
        tileX: SkTileMode,
        tileY: SkTileMode,
        clip: SkIRect,
    ) {
        if (image.width <= 0 || image.height <= 0) return
        if (src.right <= src.left || src.bottom <= src.top) return
        if (devDst.right <= devDst.left || devDst.bottom <= devDst.top) return

        val mode = paint?.blendMode ?: SkBlendMode.kSrcOver
        when (mode) {
            SkBlendMode.kClear,
            SkBlendMode.kSrc,
            SkBlendMode.kSrcOver,
            SkBlendMode.kDstOver -> Unit
            else -> error(
                "SkWebGpuDevice.drawImageRect : blend mode $mode not supported in G5.1.1 " +
                    "(in scope : kClear / kSrc / kSrcOver / kDstOver -- the natively-blendable " +
                    "subset that [blendStateFor] expresses without fragment-side round-trip).",
            )
        }
        if (sampling.cubic != null || sampling.useAniso) {
            error(
                "SkWebGpuDevice.drawImageRect : cubic / anisotropic sampling not supported in G5.1 " +
                    "(only SkFilterMode.kLinear / kNearest with kNone mipmap routed ; G5.x widens).",
            )
        }
        // sampling.filter ∈ {kLinear, kNearest} -- both supported (G5.1.1).
        // The mipmap mode is ignored at this slice (no mip pyramid in the
        // texture upload) ; this matches the gate enforced above and is
        // also how kanvas-skia's CPU path treats kNone-only images.

        // Non-AA pixelEdge rounding -- matches SkBitmapDevice.drawImageRect.
        val ix0 = pixelEdge(devDst.left).coerceAtLeast(clip.left).coerceAtLeast(0)
        val iy0 = pixelEdge(devDst.top).coerceAtLeast(clip.top).coerceAtLeast(0)
        val ix1 = pixelEdge(devDst.right).coerceAtMost(clip.right).coerceAtMost(width)
        val iy1 = pixelEdge(devDst.bottom).coerceAtMost(clip.bottom).coerceAtMost(height)
        if (ix0 >= ix1 || iy0 >= iy1) return

        val texture = imageTextureFor(image)
        val (csMode, csMatrix) = bitmapColorSpaceFor(image)

        // Paint colour scale : alpha (and future colour filter) multiplies
        // the sampled texel. Default = (1, 1, 1, 1) when paint is null.
        // For G5.1 only alpha is honoured ; colour filters / blenders /
        // shaders are G5.x follow-ups.
        val paintAlpha = (paint?.alpha ?: 0xFF) / 255f

        pending.add(
            ImageRectDraw(
                texture = texture,
                imageWidth = image.width,
                imageHeight = image.height,
                srcL = src.left, srcT = src.top, srcR = src.right, srcB = src.bottom,
                dstL = devDst.left, dstT = devDst.top, dstR = devDst.right, dstB = devDst.bottom,
                scissor = intArrayOf(ix0, iy0, ix1 - ix0, iy1 - iy0),
                paintR = paintAlpha, paintG = paintAlpha, paintB = paintAlpha, paintA = paintAlpha,
                filter = sampling.filter,
                tileX = tileX,
                tileY = tileY,
                csMode = csMode,
                csMatrix = csMatrix,
                r = 1f, g = 1f, b = 1f, a = paintAlpha,
                mode = mode,
            ),
        )
    }

    /**
     * G5.1.1 test-only -- enqueue a bitmap draw with an explicit tile
     * mode. Used by [org.skia.gpu.webgpu.ImageRectTest] to exercise
     * `kRepeat` / `kMirror` / `kDecal` ; the public
     * [org.skia.core.SkCanvas.drawImageRect] API does not carry a tile
     * mode (`SkSamplingOptions` is purely filter / mipmap / cubic).
     * Tile modes will reach the device naturally once
     * `paint.shader is SkBitmapShader` routes through this pipeline
     * (G5.2 onwards) ; until then the test path is the only client.
     */
    internal fun enqueueImageRectDrawForTest(
        image: SkImage,
        src: SkRect,
        devDst: SkRect,
        sampling: SkSamplingOptions,
        paint: SkPaint?,
        tile: SkTileMode,
        clip: SkIRect = SkIRect.MakeWH(width, height),
    ) {
        enqueueImageRectDrawInternal(
            image = image,
            src = src,
            devDst = devDst,
            sampling = sampling,
            paint = paint,
            tileX = tile,
            tileY = tile,
            clip = clip,
        )
    }

    /**
     * G5.2 -- route a `paint.shader is SkBitmapShader` fill of an
     * axis-aligned rect through the existing drawImageRect bitmap
     * pipeline (G5.1 / G5.1.1).
     *
     * The shader's `localMatrix` maps shader-local (= image-pixel)
     * coords to user-space coords. Composed with the CTM (also axis-
     * aligned by the dispatch gate above), the combined matrix
     * `M = ctm * localMatrix` is a pure scale + translate. The
     * `bitmap_shader.wgsl` fragment stage expects an affine
     * `(devX, devY) -> (sx, sy)` of the same shape -- so we set
     * `dst = devRect` (the rect mapped through the CTM) and back-solve
     * the matching `src` rect in source-image-pixel coords via
     * `M^-1`. The `(tileX, tileY)` pair flows from the shader straight
     * into the per-draw uniform (sampler addressMode + in-shader
     * decal check).
     *
     * Returns `false` when the rect is fully clipped or the image is
     * degenerate (caller drops the draw). Returns `true` otherwise --
     * the caller MUST NOT fall through to the solid-colour fill
     * machinery (would over-paint with `paint.color = transparent`).
     */
    private fun drawBitmapShaderFillRect(
        srcRect: SkRect,
        clip: SkIRect,
        paint: SkPaint,
        shader: SkBitmapShader,
        ctm: SkMatrix,
    ): Boolean {
        val image = shader.getImage()
        if (image.width <= 0 || image.height <= 0) return false
        // Combined matrix : ctm * localMatrix maps shader-local (image
        // pixel) coords -> device coords. Both factors are axis-aligned
        // (gated above), so the product is too. Bail honestly if the
        // inverse doesn't exist (degenerate scale = 0) -- the caller
        // returns without a fallback draw, mirroring how the gradient
        // helpers handle fully-clipped rects.
        val combined = ctm.preConcat(shader.localMatrix)
        if (!combined.isAxisAligned) return false
        if (combined.sx == 0f || combined.sy == 0f) return false
        val inv = combined.invert() ?: return false

        // dst : the rect mapped into device coords. With an axis-aligned
        // CTM the four corners reduce to two ; `min/max` absorbs any
        // negative-scale (mirror) factor.
        val (x0, y0) = ctm.mapXY(srcRect.left, srcRect.top)
        val (x1, y1) = ctm.mapXY(srcRect.right, srcRect.bottom)
        val devDst = SkRect.MakeLTRB(
            minOf(x0, x1), minOf(y0, y1),
            maxOf(x0, x1), maxOf(y0, y1),
        )

        // src : back-solved through `M^-1`. We use the corners of the
        // ORIGINAL `srcRect` (in user-space) through `localMatrix^-1`
        // to land in image-pixel coords -- equivalent to applying the
        // combined inverse to the device-rect corners.
        val (sl, st) = inv.mapXY(devDst.left, devDst.top)
        val (sr, sb) = inv.mapXY(devDst.right, devDst.bottom)
        val imgSrc = SkRect.MakeLTRB(
            minOf(sl, sr), minOf(st, sb),
            maxOf(sl, sr), maxOf(st, sb),
        )

        enqueueImageRectDrawInternal(
            image = image,
            src = imgSrc,
            devDst = devDst,
            sampling = shader.getSampling(),
            paint = paint,
            tileX = shader.getTileX(),
            tileY = shader.getTileY(),
            clip = clip,
        )
        return true
    }

    /**
     * G5.2 -- per-axis tile mode variant of [enqueueImageRectDrawForTest].
     * Used by [BitmapShaderPaintRectTest] to mirror the cross-axis
     * shader-routing pipeline path without going through the SkCanvas
     * shader machinery. The asymmetric `(tileX, tileY)` pair exercises
     * the sampler-cache key and the in-shader per-axis decal check
     * that production code reaches via `paint.shader is SkBitmapShader`.
     */
    internal fun enqueueImageRectDrawForTest(
        image: SkImage,
        src: SkRect,
        devDst: SkRect,
        sampling: SkSamplingOptions,
        paint: SkPaint?,
        tileX: SkTileMode,
        tileY: SkTileMode,
        clip: SkIRect = SkIRect.MakeWH(width, height),
    ) {
        enqueueImageRectDrawInternal(
            image = image,
            src = src,
            devDst = devDst,
            sampling = sampling,
            paint = paint,
            tileX = tileX,
            tileY = tileY,
            clip = clip,
        )
    }

    /**
     * Submit the pending draws, read the colour texture back, and return
     * its raw bytes. Layout: row-major RGBA, `width * height * 4` bytes,
     * de-padded from WebGPU's 256-byte row alignment. Clears [pending]
     * for the next frame.
     */
    public fun flush(): ByteArray = runBlocking {
        val encoder = context.device.createCommandEncoder()
        // G6.1 — draws target the *intermediate* texture ; the final
        // present pass below samples it, applies the sRGB → Rec.2020
        // transform, and writes to `target.colorTexture` for readback.
        val colorView = intermediateView

        if (pending.isEmpty()) {
            // Explicit clear pass so the caller can read back the background.
            encoder.beginRenderPass(
                RenderPassDescriptor(
                    colorAttachments = listOf(
                        RenderPassColorAttachment(
                            view = colorView,
                            loadOp = GPULoadOp.Clear,
                            clearValue = background,
                            storeOp = GPUStoreOp.Store,
                        ),
                    ),
                ),
            ) {
                end()
            }
        }

        // Per-draw GPU resources (uniform buffer + bind group + optional
        // vertex buffer for polygons). WebGPU forbids `queue.writeBuffer`
        // between draws inside a render pass — per-draw resources are
        // the simplest correct fix ; dynamic-offset bind groups are an
        // optimisation for later phases.
        val perDrawResources: List<DrawResources> = pending.map { d ->
            when (d) {
                is RectDraw -> buildRectDrawResources(d)
                is PolygonDraw -> buildPolygonDrawResources(d)
                is AaPolygonDraw -> buildAaPolygonDrawResources(d)
                is StencilCoverPolygonDraw -> buildStencilCoverDrawResources(d)
                is StencilCoverAaPolygonDraw -> buildStencilCoverAaDrawResources(d)
                is StencilCoverAaGradientDraw -> buildStencilCoverAaGradientDrawResources(d)
                is StencilCoverAaRadialGradientDraw ->
                    buildStencilCoverAaRadialGradientDrawResources(d)
                is StencilCoverAaSweepGradientDraw ->
                    buildStencilCoverAaSweepGradientDrawResources(d)
                is StencilCoverAaConicalGradientDraw ->
                    buildStencilCoverAaConicalGradientDrawResources(d)
                is StencilCoverAaConicalFocalGradientDraw ->
                    buildStencilCoverAaConicalFocalGradientDrawResources(d)
                is StencilCoverAaBitmapShaderDraw ->
                    buildStencilCoverAaBitmapShaderDrawResources(d)
                is LinearGradientRectDraw -> buildLinearGradientRectDrawResources(d)
                is RadialGradientRectDraw -> buildRadialGradientRectDrawResources(d)
                is SweepGradientRectDraw -> buildSweepGradientRectDrawResources(d)
                is ConicalGradientRectDraw -> buildConicalGradientRectDrawResources(d)
                is ConicalFocalGradientRectDraw -> buildConicalFocalGradientRectDrawResources(d)
                is ConicalStripGradientRectDraw -> buildConicalStripGradientRectDrawResources(d)
                is ImageRectDraw -> buildImageRectDrawResources(d)
            }
        }

        pending.forEachIndexed { i, d ->
            val loadOp = if (i == 0) GPULoadOp.Clear else GPULoadOp.Load
            val res = perDrawResources[i]
            if (d is StencilCoverPolygonDraw) {
                // Stencil & cover : 2 sub-passes in a single render pass.
                // Stencil sub-pass writes the winding count, cover sub-pass
                // reads it and writes color where != 0. Color attachment
                // loadOp matches the rest of flush() (Clear for the first
                // draw, Load otherwise) so we compose with prior draws.
                // Depth-stencil attachment is cleared per pass (each path
                // has its own scope) and discarded after.
                encoder.beginRenderPass(
                    RenderPassDescriptor(
                        colorAttachments = listOf(
                            RenderPassColorAttachment(
                                view = colorView,
                                loadOp = loadOp,
                                clearValue = background,
                                storeOp = GPUStoreOp.Store,
                            ),
                        ),
                        depthStencilAttachment = RenderPassDepthStencilAttachment(
                            view = depthStencilView,
                            stencilClearValue = 0u,
                            stencilLoadOp = GPULoadOp.Clear,
                            stencilStoreOp = GPUStoreOp.Discard,
                            stencilReadOnly = false,
                            depthReadOnly = true,
                        ),
                    ),
                ) {
                    setBindGroup(0u, res.bindGroup)
                    setScissorRect(
                        x = d.scissor[0].toUInt(),
                        y = d.scissor[1].toUInt(),
                        width = d.scissor[2].toUInt(),
                        height = d.scissor[3].toUInt(),
                    )
                    // Stencil pass : compute winding count, no color writes.
                    setPipeline(stencilWritePipeline)
                    setVertexBuffer(slot = 0u, buffer = res.vertexBuffer!!)
                    draw((d.stencilVerts.size / 2).toUInt())
                    // Cover pass : color writes where stencil != 0.
                    // Reference value 0 + compare NotEqual = "winding count
                    // != 0" = kWinding fill rule.
                    setStencilReference(0u)
                    setPipeline(coverPipelineFor(d.mode, d.fillType))
                    setVertexBuffer(slot = 0u, buffer = res.coverVertexBuffer!!)
                    draw((d.coverVerts.size / 2).toUInt())
                    end()
                }
                return@forEachIndexed
            }
            if (d is StencilCoverAaPolygonDraw) {
                // G3.3b.3a / G3.3b.3d — same shape as the non-AA
                // stencil-and-cover pass, but the cover phase emits TWO
                // sub-draws sharing the edge data : inside-half + outside-
                // half. The two pipelines have opposite stencil compare
                // ops, so each fragment is gated to exactly one of them,
                // and their coverage formulas sum to the full AA profile
                // across the half-pixel boundary band. Closes the
                // outside-half AA loss inherent to the G3.3b.3a single
                // cover sub-draw.
                encoder.beginRenderPass(
                    RenderPassDescriptor(
                        colorAttachments = listOf(
                            RenderPassColorAttachment(
                                view = colorView,
                                loadOp = loadOp,
                                clearValue = background,
                                storeOp = GPUStoreOp.Store,
                            ),
                        ),
                        depthStencilAttachment = RenderPassDepthStencilAttachment(
                            view = depthStencilView,
                            stencilClearValue = 0u,
                            stencilLoadOp = GPULoadOp.Clear,
                            stencilStoreOp = GPUStoreOp.Discard,
                            stencilReadOnly = false,
                            depthReadOnly = true,
                        ),
                    ),
                ) {
                    setBindGroup(0u, res.bindGroup)
                    setScissorRect(
                        x = d.scissor[0].toUInt(),
                        y = d.scissor[1].toUInt(),
                        width = d.scissor[2].toUInt(),
                        height = d.scissor[3].toUInt(),
                    )
                    setPipeline(stencilWritePipeline)
                    setVertexBuffer(slot = 0u, buffer = res.vertexBuffer!!)
                    draw((d.stencilVerts.size / 2).toUInt())
                    setStencilReference(0u)
                    setVertexBuffer(slot = 0u, buffer = res.coverVertexBuffer!!)
                    setPipeline(aaStencilCoverPipelineFor(d.mode, d.fillType, CoverageSide.Inside))
                    draw((d.coverVerts.size / 2).toUInt())
                    setPipeline(aaStencilCoverPipelineFor(d.mode, d.fillType, CoverageSide.Outside))
                    draw((d.coverVerts.size / 2).toUInt())
                    end()
                }
                return@forEachIndexed
            }
            if (d is StencilCoverAaGradientDraw) {
                // G4.1.2 — same stencil-and-cover envelope as
                // [StencilCoverAaPolygonDraw], but the two cover sub-draws
                // bind the gradient pipeline (linear gradient lookup per
                // fragment instead of a uniform color). Stencil sub-pass
                // is identical to the solid-color variant.
                encoder.beginRenderPass(
                    RenderPassDescriptor(
                        colorAttachments = listOf(
                            RenderPassColorAttachment(
                                view = colorView,
                                loadOp = loadOp,
                                clearValue = background,
                                storeOp = GPUStoreOp.Store,
                            ),
                        ),
                        depthStencilAttachment = RenderPassDepthStencilAttachment(
                            view = depthStencilView,
                            stencilClearValue = 0u,
                            stencilLoadOp = GPULoadOp.Clear,
                            stencilStoreOp = GPUStoreOp.Discard,
                            stencilReadOnly = false,
                            depthReadOnly = true,
                        ),
                    ),
                ) {
                    setBindGroup(0u, res.bindGroup)
                    setScissorRect(
                        x = d.scissor[0].toUInt(),
                        y = d.scissor[1].toUInt(),
                        width = d.scissor[2].toUInt(),
                        height = d.scissor[3].toUInt(),
                    )
                    setPipeline(stencilWritePipeline)
                    setVertexBuffer(slot = 0u, buffer = res.vertexBuffer!!)
                    draw((d.stencilVerts.size / 2).toUInt())
                    setStencilReference(0u)
                    setVertexBuffer(slot = 0u, buffer = res.coverVertexBuffer!!)
                    setPipeline(
                        aaStencilCoverGradientPipelineFor(
                            d.mode, d.fillType, d.tileMode, CoverageSide.Inside,
                        ),
                    )
                    draw((d.coverVerts.size / 2).toUInt())
                    setPipeline(
                        aaStencilCoverGradientPipelineFor(
                            d.mode, d.fillType, d.tileMode, CoverageSide.Outside,
                        ),
                    )
                    draw((d.coverVerts.size / 2).toUInt())
                    end()
                }
                return@forEachIndexed
            }
            if (d is StencilCoverAaRadialGradientDraw) {
                // G4.2.2 -- mirror of the [StencilCoverAaGradientDraw]
                // dispatch above. Same stencil-write sub-pass, but the
                // two cover sub-draws bind the radial-gradient pipeline.
                encoder.beginRenderPass(
                    RenderPassDescriptor(
                        colorAttachments = listOf(
                            RenderPassColorAttachment(
                                view = colorView,
                                loadOp = loadOp,
                                clearValue = background,
                                storeOp = GPUStoreOp.Store,
                            ),
                        ),
                        depthStencilAttachment = RenderPassDepthStencilAttachment(
                            view = depthStencilView,
                            stencilClearValue = 0u,
                            stencilLoadOp = GPULoadOp.Clear,
                            stencilStoreOp = GPUStoreOp.Discard,
                            stencilReadOnly = false,
                            depthReadOnly = true,
                        ),
                    ),
                ) {
                    setBindGroup(0u, res.bindGroup)
                    setScissorRect(
                        x = d.scissor[0].toUInt(),
                        y = d.scissor[1].toUInt(),
                        width = d.scissor[2].toUInt(),
                        height = d.scissor[3].toUInt(),
                    )
                    setPipeline(stencilWritePipeline)
                    setVertexBuffer(slot = 0u, buffer = res.vertexBuffer!!)
                    draw((d.stencilVerts.size / 2).toUInt())
                    setStencilReference(0u)
                    setVertexBuffer(slot = 0u, buffer = res.coverVertexBuffer!!)
                    setPipeline(
                        aaStencilCoverRadialGradientPipelineFor(
                            d.mode, d.fillType, d.tileMode, CoverageSide.Inside,
                        ),
                    )
                    draw((d.coverVerts.size / 2).toUInt())
                    setPipeline(
                        aaStencilCoverRadialGradientPipelineFor(
                            d.mode, d.fillType, d.tileMode, CoverageSide.Outside,
                        ),
                    )
                    draw((d.coverVerts.size / 2).toUInt())
                    end()
                }
                return@forEachIndexed
            }
            if (d is StencilCoverAaConicalGradientDraw) {
                // G4.4.3 -- conical gradient (kRadial sub-case) on a non-rect
                // path. Mirror of the linear / radial / sweep stencil-cover
                // gradient dispatches above ; same stencil-write sub-pass,
                // two cover sub-draws bind the conical-kRadial pipeline.
                encoder.beginRenderPass(
                    RenderPassDescriptor(
                        colorAttachments = listOf(
                            RenderPassColorAttachment(
                                view = colorView,
                                loadOp = loadOp,
                                clearValue = background,
                                storeOp = GPUStoreOp.Store,
                            ),
                        ),
                        depthStencilAttachment = RenderPassDepthStencilAttachment(
                            view = depthStencilView,
                            stencilClearValue = 0u,
                            stencilLoadOp = GPULoadOp.Clear,
                            stencilStoreOp = GPUStoreOp.Discard,
                            stencilReadOnly = false,
                            depthReadOnly = true,
                        ),
                    ),
                ) {
                    setBindGroup(0u, res.bindGroup)
                    setScissorRect(
                        x = d.scissor[0].toUInt(),
                        y = d.scissor[1].toUInt(),
                        width = d.scissor[2].toUInt(),
                        height = d.scissor[3].toUInt(),
                    )
                    setPipeline(stencilWritePipeline)
                    setVertexBuffer(slot = 0u, buffer = res.vertexBuffer!!)
                    draw((d.stencilVerts.size / 2).toUInt())
                    setStencilReference(0u)
                    setVertexBuffer(slot = 0u, buffer = res.coverVertexBuffer!!)
                    setPipeline(
                        aaStencilCoverConicalGradientPipelineFor(
                            d.mode, d.fillType, d.tileMode, CoverageSide.Inside,
                        ),
                    )
                    draw((d.coverVerts.size / 2).toUInt())
                    setPipeline(
                        aaStencilCoverConicalGradientPipelineFor(
                            d.mode, d.fillType, d.tileMode, CoverageSide.Outside,
                        ),
                    )
                    draw((d.coverVerts.size / 2).toUInt())
                    end()
                }
                return@forEachIndexed
            }
            if (d is StencilCoverAaConicalFocalGradientDraw) {
                // G4.4.3 -- conical gradient (focal-inside well-behaved sub-case)
                // on a non-rect path. Mirror of the kRadial dispatch just above
                // with the focal-inside pipeline + uniform layout.
                encoder.beginRenderPass(
                    RenderPassDescriptor(
                        colorAttachments = listOf(
                            RenderPassColorAttachment(
                                view = colorView,
                                loadOp = loadOp,
                                clearValue = background,
                                storeOp = GPUStoreOp.Store,
                            ),
                        ),
                        depthStencilAttachment = RenderPassDepthStencilAttachment(
                            view = depthStencilView,
                            stencilClearValue = 0u,
                            stencilLoadOp = GPULoadOp.Clear,
                            stencilStoreOp = GPUStoreOp.Discard,
                            stencilReadOnly = false,
                            depthReadOnly = true,
                        ),
                    ),
                ) {
                    setBindGroup(0u, res.bindGroup)
                    setScissorRect(
                        x = d.scissor[0].toUInt(),
                        y = d.scissor[1].toUInt(),
                        width = d.scissor[2].toUInt(),
                        height = d.scissor[3].toUInt(),
                    )
                    setPipeline(stencilWritePipeline)
                    setVertexBuffer(slot = 0u, buffer = res.vertexBuffer!!)
                    draw((d.stencilVerts.size / 2).toUInt())
                    setStencilReference(0u)
                    setVertexBuffer(slot = 0u, buffer = res.coverVertexBuffer!!)
                    setPipeline(
                        aaStencilCoverConicalFocalGradientPipelineFor(
                            d.mode, d.fillType, d.tileMode, CoverageSide.Inside,
                        ),
                    )
                    draw((d.coverVerts.size / 2).toUInt())
                    setPipeline(
                        aaStencilCoverConicalFocalGradientPipelineFor(
                            d.mode, d.fillType, d.tileMode, CoverageSide.Outside,
                        ),
                    )
                    draw((d.coverVerts.size / 2).toUInt())
                    end()
                }
                return@forEachIndexed
            }
            if (d is StencilCoverAaBitmapShaderDraw) {
                // G5.2.1 -- mirror of the gradient stencil-cover dispatches
                // above ; same two-sub-pass structure (stencil-write +
                // inside/outside cover), the cover sub-draws bind the
                // bitmap-shader pipeline. The stencil-write pass uses the
                // dedicated [aaStencilCoverBitmapShaderStencilPipeline]
                // (3-binding pipeline layout) so the bind group satisfies
                // both passes' layouts in one go.
                encoder.beginRenderPass(
                    RenderPassDescriptor(
                        colorAttachments = listOf(
                            RenderPassColorAttachment(
                                view = colorView,
                                loadOp = loadOp,
                                clearValue = background,
                                storeOp = GPUStoreOp.Store,
                            ),
                        ),
                        depthStencilAttachment = RenderPassDepthStencilAttachment(
                            view = depthStencilView,
                            stencilClearValue = 0u,
                            stencilLoadOp = GPULoadOp.Clear,
                            stencilStoreOp = GPUStoreOp.Discard,
                            stencilReadOnly = false,
                            depthReadOnly = true,
                        ),
                    ),
                ) {
                    setBindGroup(0u, res.bindGroup)
                    setScissorRect(
                        x = d.scissor[0].toUInt(),
                        y = d.scissor[1].toUInt(),
                        width = d.scissor[2].toUInt(),
                        height = d.scissor[3].toUInt(),
                    )
                    setPipeline(aaStencilCoverBitmapShaderStencilPipeline)
                    setVertexBuffer(slot = 0u, buffer = res.vertexBuffer!!)
                    draw((d.stencilVerts.size / 2).toUInt())
                    setStencilReference(0u)
                    setVertexBuffer(slot = 0u, buffer = res.coverVertexBuffer!!)
                    setPipeline(
                        aaStencilCoverBitmapShaderPipelineFor(
                            d.mode, d.fillType, CoverageSide.Inside,
                        ),
                    )
                    draw((d.coverVerts.size / 2).toUInt())
                    setPipeline(
                        aaStencilCoverBitmapShaderPipelineFor(
                            d.mode, d.fillType, CoverageSide.Outside,
                        ),
                    )
                    draw((d.coverVerts.size / 2).toUInt())
                    end()
                }
                return@forEachIndexed
            }
            if (d is StencilCoverAaSweepGradientDraw) {
                // G4.3.2 -- mirror of the [StencilCoverAaGradientDraw] /
                // [StencilCoverAaRadialGradientDraw] dispatches above.
                // Same stencil-write sub-pass, but the two cover sub-draws
                // bind the sweep-gradient pipeline.
                encoder.beginRenderPass(
                    RenderPassDescriptor(
                        colorAttachments = listOf(
                            RenderPassColorAttachment(
                                view = colorView,
                                loadOp = loadOp,
                                clearValue = background,
                                storeOp = GPUStoreOp.Store,
                            ),
                        ),
                        depthStencilAttachment = RenderPassDepthStencilAttachment(
                            view = depthStencilView,
                            stencilClearValue = 0u,
                            stencilLoadOp = GPULoadOp.Clear,
                            stencilStoreOp = GPUStoreOp.Discard,
                            stencilReadOnly = false,
                            depthReadOnly = true,
                        ),
                    ),
                ) {
                    setBindGroup(0u, res.bindGroup)
                    setScissorRect(
                        x = d.scissor[0].toUInt(),
                        y = d.scissor[1].toUInt(),
                        width = d.scissor[2].toUInt(),
                        height = d.scissor[3].toUInt(),
                    )
                    setPipeline(stencilWritePipeline)
                    setVertexBuffer(slot = 0u, buffer = res.vertexBuffer!!)
                    draw((d.stencilVerts.size / 2).toUInt())
                    setStencilReference(0u)
                    setVertexBuffer(slot = 0u, buffer = res.coverVertexBuffer!!)
                    setPipeline(
                        aaStencilCoverSweepGradientPipelineFor(
                            d.mode, d.fillType, d.tileMode, CoverageSide.Inside,
                        ),
                    )
                    draw((d.coverVerts.size / 2).toUInt())
                    setPipeline(
                        aaStencilCoverSweepGradientPipelineFor(
                            d.mode, d.fillType, d.tileMode, CoverageSide.Outside,
                        ),
                    )
                    draw((d.coverVerts.size / 2).toUInt())
                    end()
                }
                return@forEachIndexed
            }
            encoder.beginRenderPass(
                RenderPassDescriptor(
                    colorAttachments = listOf(
                        RenderPassColorAttachment(
                            view = colorView,
                            loadOp = loadOp,
                            clearValue = background,
                            storeOp = GPUStoreOp.Store,
                        ),
                    ),
                ),
            ) {
                when (d) {
                    is RectDraw -> {
                        setPipeline(rectPipelineFor(d.mode))
                        setBindGroup(0u, res.bindGroup)
                        setScissorRect(
                            x = d.x.toUInt(),
                            y = d.y.toUInt(),
                            width = d.w.toUInt(),
                            height = d.h.toUInt(),
                        )
                        draw(FULL_SCREEN_TRIANGLE_VERTEX_COUNT)
                    }
                    is PolygonDraw -> {
                        setPipeline(polygonPipelineFor(d.mode))
                        setBindGroup(0u, res.bindGroup)
                        setScissorRect(
                            x = d.scissor[0].toUInt(),
                            y = d.scissor[1].toUInt(),
                            width = d.scissor[2].toUInt(),
                            height = d.scissor[3].toUInt(),
                        )
                        setVertexBuffer(slot = 0u, buffer = res.vertexBuffer!!)
                        draw((d.verts.size / 2).toUInt())
                    }
                    is AaPolygonDraw -> {
                        setPipeline(aaPolygonPipelineFor(d.mode))
                        setBindGroup(0u, res.bindGroup)
                        setScissorRect(
                            x = d.scissor[0].toUInt(),
                            y = d.scissor[1].toUInt(),
                            width = d.scissor[2].toUInt(),
                            height = d.scissor[3].toUInt(),
                        )
                        setVertexBuffer(slot = 0u, buffer = res.vertexBuffer!!)
                        draw((d.verts.size / 2).toUInt())
                    }
                    is LinearGradientRectDraw -> {
                        setPipeline(linearGradientPipelineFor(d.mode, d.tileMode))
                        setBindGroup(0u, res.bindGroup)
                        setScissorRect(
                            x = d.scissor[0].toUInt(),
                            y = d.scissor[1].toUInt(),
                            width = d.scissor[2].toUInt(),
                            height = d.scissor[3].toUInt(),
                        )
                        draw(FULL_SCREEN_TRIANGLE_VERTEX_COUNT)
                    }
                    is RadialGradientRectDraw -> {
                        setPipeline(radialGradientPipelineFor(d.mode, d.tileMode))
                        setBindGroup(0u, res.bindGroup)
                        setScissorRect(
                            x = d.scissor[0].toUInt(),
                            y = d.scissor[1].toUInt(),
                            width = d.scissor[2].toUInt(),
                            height = d.scissor[3].toUInt(),
                        )
                        draw(FULL_SCREEN_TRIANGLE_VERTEX_COUNT)
                    }
                    is SweepGradientRectDraw -> {
                        setPipeline(sweepGradientPipelineFor(d.mode, d.tileMode))
                        setBindGroup(0u, res.bindGroup)
                        setScissorRect(
                            x = d.scissor[0].toUInt(),
                            y = d.scissor[1].toUInt(),
                            width = d.scissor[2].toUInt(),
                            height = d.scissor[3].toUInt(),
                        )
                        draw(FULL_SCREEN_TRIANGLE_VERTEX_COUNT)
                    }
                    is ConicalGradientRectDraw -> {
                        setPipeline(conicalGradientPipelineFor(d.mode, d.tileMode))
                        setBindGroup(0u, res.bindGroup)
                        setScissorRect(
                            x = d.scissor[0].toUInt(),
                            y = d.scissor[1].toUInt(),
                            width = d.scissor[2].toUInt(),
                            height = d.scissor[3].toUInt(),
                        )
                        draw(FULL_SCREEN_TRIANGLE_VERTEX_COUNT)
                    }
                    is ImageRectDraw -> {
                        // G5.1.1 -- pipeline keyed on (blend, filter, tile).
                        // The blend axis switches the blend state ; filter
                        // and tile both affect the *sampler* (built into
                        // the bind group, not the pipeline) -- the pipeline
                        // entry is identical across them so the cache is
                        // technically over-keyed. Keeping the cache shape
                        // matches the gradient pipelines and leaves room
                        // for shader-side filter / tile branches later.
                        setPipeline(bitmapPipelineFor(d.mode, d.filter))
                        setBindGroup(0u, res.bindGroup)
                        setScissorRect(
                            x = d.scissor[0].toUInt(),
                            y = d.scissor[1].toUInt(),
                            width = d.scissor[2].toUInt(),
                            height = d.scissor[3].toUInt(),
                        )
                        draw(FULL_SCREEN_TRIANGLE_VERTEX_COUNT)
                    }
                    is ConicalFocalGradientRectDraw -> {
                        setPipeline(conicalFocalGradientPipelineFor(d.mode, d.tileMode))
                        setBindGroup(0u, res.bindGroup)
                        setScissorRect(
                            x = d.scissor[0].toUInt(),
                            y = d.scissor[1].toUInt(),
                            width = d.scissor[2].toUInt(),
                            height = d.scissor[3].toUInt(),
                        )
                        draw(FULL_SCREEN_TRIANGLE_VERTEX_COUNT)
                    }
                    is ConicalStripGradientRectDraw -> {
                        setPipeline(conicalStripGradientPipelineFor(d.mode, d.tileMode))
                        setBindGroup(0u, res.bindGroup)
                        setScissorRect(
                            x = d.scissor[0].toUInt(),
                            y = d.scissor[1].toUInt(),
                            width = d.scissor[2].toUInt(),
                            height = d.scissor[3].toUInt(),
                        )
                        draw(FULL_SCREEN_TRIANGLE_VERTEX_COUNT)
                    }
                    is StencilCoverPolygonDraw -> { /* handled above */ }
                    is StencilCoverAaPolygonDraw -> { /* handled above */ }
                    is StencilCoverAaGradientDraw -> { /* handled above */ }
                    is StencilCoverAaRadialGradientDraw -> { /* handled above */ }
                    is StencilCoverAaSweepGradientDraw -> { /* handled above */ }
                    is StencilCoverAaConicalGradientDraw -> { /* handled above */ }
                    is StencilCoverAaConicalFocalGradientDraw -> { /* handled above */ }
                    is StencilCoverAaBitmapShaderDraw -> { /* handled above */ }
                }
                end()
            }
        }

        // G6.1 present pass : transform sRGB intermediate to Rec.2020
        // final, in a fragment shader (textureLoad + transform + write).
        // Replaces the G6.0 CPU loop in WebGpuSink. Targets
        // `target.colorTexture` so the existing readback machinery picks
        // up the colorspace-converted pixels unchanged.
        val finalView = target.colorTexture.createView()
        encoder.beginRenderPass(
            RenderPassDescriptor(
                colorAttachments = listOf(
                    RenderPassColorAttachment(
                        view = finalView,
                        loadOp = GPULoadOp.Clear,
                        clearValue = Color(0.0, 0.0, 0.0, 0.0),
                        storeOp = GPUStoreOp.Store,
                    ),
                ),
            ),
        ) {
            setPipeline(presentPipeline)
            setBindGroup(0u, presentBindGroup)
            draw(FULL_SCREEN_TRIANGLE_VERTEX_COUNT)
            end()
        }

        target.encodeCopyToStaging(encoder)
        context.queue.submit(listOf(encoder.finish()))

        val pixels = target.readPixels()

        perDrawResources.forEach {
            it.uniform.close()
            it.vertexBuffer?.close()
            it.coverVertexBuffer?.close()
        }
        pending.clear()
        pixels
    }

    /**
     * Per-draw GPU resources lifetime-managed by [flush] : the uniform
     * buffer + its bind group, plus optional vertex buffers (polygon
     * draws use [vertexBuffer] ; stencil-and-cover draws additionally
     * use [coverVertexBuffer] for the cover-pass bbox quad).
     */
    private data class DrawResources(
        val uniform: GPUBuffer,
        val bindGroup: io.ygdrasil.webgpu.GPUBindGroup,
        val vertexBuffer: GPUBuffer? = null,
        val coverVertexBuffer: GPUBuffer? = null,
    )

    private fun buildRectDrawResources(d: RectDraw): DrawResources {
        val buf = context.device.createBuffer(
            BufferDescriptor(
                size = RECT_UNIFORM_SIZE,
                usage = GPUBufferUsage.Uniform or GPUBufferUsage.CopyDst,
                label = "SkWebGpuDevice.rectDraw",
            ),
        )
        // Layout : color (4) + outerBounds (4) + innerBounds (4) = 48 bytes.
        // Matches `Uniforms { color, outerBounds, innerBounds }` in
        // solid_color.wgsl. innerBounds is degenerate for fill draws so
        // `inner_cov` collapses to 0 in the shader.
        context.queue.writeBuffer(
            buf, 0uL,
            ArrayBuffer.of(floatArrayOf(
                d.r, d.g, d.b, d.a,
                d.ol, d.ot, d.or, d.ob,
                d.il, d.it, d.ir, d.ib,
            )),
        )
        val bindGroup = context.device.createBindGroup(
            BindGroupDescriptor(
                layout = rectBindGroupLayout,
                entries = listOf(
                    BindGroupEntry(binding = 0u, resource = BufferBinding(buffer = buf)),
                ),
            ),
        )
        return DrawResources(uniform = buf, bindGroup = bindGroup)
    }

    private fun buildPolygonDrawResources(d: PolygonDraw): DrawResources {
        val uniform = context.device.createBuffer(
            BufferDescriptor(
                size = POLYGON_UNIFORM_SIZE,
                usage = GPUBufferUsage.Uniform or GPUBufferUsage.CopyDst,
                label = "SkWebGpuDevice.polygonDraw",
            ),
        )
        // Layout : color (4 floats) + viewport (vec4 padded ; only x,y used).
        // Matches `Uniforms { color, viewport }` in solid_polygon.wgsl.
        context.queue.writeBuffer(
            uniform, 0uL,
            ArrayBuffer.of(floatArrayOf(
                d.r, d.g, d.b, d.a,
                width.toFloat(), height.toFloat(), 0f, 0f,
            )),
        )
        val bindGroup = context.device.createBindGroup(
            BindGroupDescriptor(
                layout = polygonBindGroupLayout,
                entries = listOf(
                    BindGroupEntry(binding = 0u, resource = BufferBinding(buffer = uniform)),
                ),
            ),
        )
        val vertexSize = (d.verts.size * Float.SIZE_BYTES).toULong()
        val vertexBuffer = context.device.createBuffer(
            BufferDescriptor(
                size = vertexSize,
                usage = GPUBufferUsage.Vertex or GPUBufferUsage.CopyDst,
                label = "SkWebGpuDevice.polygonVerts",
            ),
        )
        context.queue.writeBuffer(vertexBuffer, 0uL, ArrayBuffer.of(d.verts))
        return DrawResources(uniform = uniform, bindGroup = bindGroup, vertexBuffer = vertexBuffer)
    }

    private fun buildStencilCoverDrawResources(d: StencilCoverPolygonDraw): DrawResources {
        val uniform = context.device.createBuffer(
            BufferDescriptor(
                size = POLYGON_UNIFORM_SIZE,
                usage = GPUBufferUsage.Uniform or GPUBufferUsage.CopyDst,
                label = "SkWebGpuDevice.stencilCoverDraw",
            ),
        )
        context.queue.writeBuffer(
            uniform, 0uL,
            ArrayBuffer.of(floatArrayOf(
                d.r, d.g, d.b, d.a,
                width.toFloat(), height.toFloat(), 0f, 0f,
            )),
        )
        val bindGroup = context.device.createBindGroup(
            BindGroupDescriptor(
                layout = polygonBindGroupLayout,
                entries = listOf(
                    BindGroupEntry(binding = 0u, resource = BufferBinding(buffer = uniform)),
                ),
            ),
        )
        val stencilVB = context.device.createBuffer(
            BufferDescriptor(
                size = (d.stencilVerts.size * Float.SIZE_BYTES).toULong(),
                usage = GPUBufferUsage.Vertex or GPUBufferUsage.CopyDst,
                label = "SkWebGpuDevice.stencilVerts",
            ),
        )
        context.queue.writeBuffer(stencilVB, 0uL, ArrayBuffer.of(d.stencilVerts))
        val coverVB = context.device.createBuffer(
            BufferDescriptor(
                size = (d.coverVerts.size * Float.SIZE_BYTES).toULong(),
                usage = GPUBufferUsage.Vertex or GPUBufferUsage.CopyDst,
                label = "SkWebGpuDevice.coverVerts",
            ),
        )
        context.queue.writeBuffer(coverVB, 0uL, ArrayBuffer.of(d.coverVerts))
        return DrawResources(
            uniform = uniform,
            bindGroup = bindGroup,
            vertexBuffer = stencilVB,
            coverVertexBuffer = coverVB,
        )
    }

    private fun buildAaPolygonDrawResources(d: AaPolygonDraw): DrawResources {
        val uniform = context.device.createBuffer(
            BufferDescriptor(
                size = AA_POLYGON_UNIFORM_SIZE,
                usage = GPUBufferUsage.Uniform or GPUBufferUsage.CopyDst,
                label = "SkWebGpuDevice.aaPolygonDraw",
            ),
        )
        // Layout matches `aa_polygon.wgsl` :
        //   offset  0 : color    (4 floats)
        //   offset 16 : viewport (4 floats, only x/y used)
        //   offset 32 : edgeCount (u32) + 3 u32 padding
        //   offset 48 : edges[MAX_AA_EDGES] (vec4 each)
        // Pack the whole thing in one FloatArray ; reinterpret edgeCount's
        // bits as a float so `floatArrayOf` lays it out at the right
        // byte offset.
        val packed = FloatArray(12 + MAX_AA_EDGES * 4)
        // color
        packed[0] = d.r; packed[1] = d.g; packed[2] = d.b; packed[3] = d.a
        // viewport
        packed[4] = width.toFloat(); packed[5] = height.toFloat()
        packed[6] = 0f; packed[7] = 0f
        // edgeCount as bit-reinterpreted float (so the byte pattern matches
        // a u32 in WGSL).
        packed[8] = Float.fromBits(d.edgeCount)
        packed[9] = 0f; packed[10] = 0f; packed[11] = 0f
        // edges
        System.arraycopy(d.edges, 0, packed, 12, d.edges.size)

        context.queue.writeBuffer(uniform, 0uL, ArrayBuffer.of(packed))
        val bindGroup = context.device.createBindGroup(
            BindGroupDescriptor(
                layout = aaPolygonBindGroupLayout,
                entries = listOf(
                    BindGroupEntry(binding = 0u, resource = BufferBinding(buffer = uniform)),
                ),
            ),
        )
        val vertexSize = (d.verts.size * Float.SIZE_BYTES).toULong()
        val vertexBuffer = context.device.createBuffer(
            BufferDescriptor(
                size = vertexSize,
                usage = GPUBufferUsage.Vertex or GPUBufferUsage.CopyDst,
                label = "SkWebGpuDevice.aaPolygonVerts",
            ),
        )
        context.queue.writeBuffer(vertexBuffer, 0uL, ArrayBuffer.of(d.verts))
        return DrawResources(uniform = uniform, bindGroup = bindGroup, vertexBuffer = vertexBuffer)
    }

    private fun buildLinearGradientRectDrawResources(d: LinearGradientRectDraw): DrawResources {
        val uniform = context.device.createBuffer(
            BufferDescriptor(
                size = LINEAR_GRADIENT_UNIFORM_SIZE,
                usage = GPUBufferUsage.Uniform or GPUBufferUsage.CopyDst,
                label = "SkWebGpuDevice.linearGradientRectDraw",
            ),
        )
        // Layout matches `linear_gradient.wgsl` :
        //   offset   0 : startEnd  (vec4f -- start.xy, end.xy)
        //   offset  16 : viewport  (vec4f, only x/y used)
        //   offset  32 : countPad  (u32 in .x as bit-reinterpreted float)
        //   offset  48 : positions[MAX_GRADIENT_STOPS] (vec4 each, .x = pos)
        //   offset 304 : colors   [MAX_GRADIENT_STOPS] (vec4 each, premul rgba)
        val packed = FloatArray(12 + MAX_GRADIENT_STOPS * 8)
        packed[0] = d.startX; packed[1] = d.startY
        packed[2] = d.endX;   packed[3] = d.endY
        packed[4] = width.toFloat(); packed[5] = height.toFloat()
        packed[6] = 0f; packed[7] = 0f
        packed[8] = Float.fromBits(d.stopCount)
        packed[9] = 0f; packed[10] = 0f; packed[11] = 0f
        System.arraycopy(d.stopPositions, 0, packed, 12, MAX_GRADIENT_STOPS * 4)
        System.arraycopy(d.stopColors, 0, packed, 12 + MAX_GRADIENT_STOPS * 4, MAX_GRADIENT_STOPS * 4)

        context.queue.writeBuffer(uniform, 0uL, ArrayBuffer.of(packed))
        val bindGroup = context.device.createBindGroup(
            BindGroupDescriptor(
                layout = linearGradientBindGroupLayout,
                entries = listOf(
                    BindGroupEntry(binding = 0u, resource = BufferBinding(buffer = uniform)),
                ),
            ),
        )
        return DrawResources(uniform = uniform, bindGroup = bindGroup)
    }

    private fun buildRadialGradientRectDrawResources(d: RadialGradientRectDraw): DrawResources {
        val uniform = context.device.createBuffer(
            BufferDescriptor(
                size = RADIAL_GRADIENT_UNIFORM_SIZE,
                usage = GPUBufferUsage.Uniform or GPUBufferUsage.CopyDst,
                label = "SkWebGpuDevice.radialGradientRectDraw",
            ),
        )
        // Layout matches `radial_gradient.wgsl` :
        //   offset   0 : viewport     (vec4f, only x/y used)
        //   offset  16 : centerRadius (vec4f -- center.xy, radius, padding)
        //   offset  32 : countPad     (u32 in .x as bit-reinterpreted float)
        //   offset  48 : positions[MAX_GRADIENT_STOPS] (vec4 each, .x = pos)
        //   offset 304 : colors   [MAX_GRADIENT_STOPS] (vec4 each, premul rgba)
        val packed = FloatArray(12 + MAX_GRADIENT_STOPS * 8)
        packed[0] = width.toFloat(); packed[1] = height.toFloat()
        packed[2] = 0f; packed[3] = 0f
        packed[4] = d.centerX; packed[5] = d.centerY; packed[6] = d.radius; packed[7] = 0f
        packed[8] = Float.fromBits(d.stopCount)
        packed[9] = 0f; packed[10] = 0f; packed[11] = 0f
        System.arraycopy(d.stopPositions, 0, packed, 12, MAX_GRADIENT_STOPS * 4)
        System.arraycopy(d.stopColors, 0, packed, 12 + MAX_GRADIENT_STOPS * 4, MAX_GRADIENT_STOPS * 4)

        context.queue.writeBuffer(uniform, 0uL, ArrayBuffer.of(packed))
        val bindGroup = context.device.createBindGroup(
            BindGroupDescriptor(
                layout = radialGradientBindGroupLayout,
                entries = listOf(
                    BindGroupEntry(binding = 0u, resource = BufferBinding(buffer = uniform)),
                ),
            ),
        )
        return DrawResources(uniform = uniform, bindGroup = bindGroup)
    }

    private fun buildSweepGradientRectDrawResources(d: SweepGradientRectDraw): DrawResources {
        val uniform = context.device.createBuffer(
            BufferDescriptor(
                size = SWEEP_GRADIENT_UNIFORM_SIZE,
                usage = GPUBufferUsage.Uniform or GPUBufferUsage.CopyDst,
                label = "SkWebGpuDevice.sweepGradientRectDraw",
            ),
        )
        // Layout matches `sweep_gradient.wgsl` :
        //   offset   0 : viewport         (vec4f, only x/y used)
        //   offset  16 : centerBiasScale  (vec4f -- center.xy, tBias, tScale)
        //   offset  32 : countPad         (u32 in .x as bit-reinterpreted float)
        //   offset  48 : positions[MAX_GRADIENT_STOPS] (vec4 each, .x = pos)
        //   offset 304 : colors   [MAX_GRADIENT_STOPS] (vec4 each, premul rgba)
        val packed = FloatArray(12 + MAX_GRADIENT_STOPS * 8)
        packed[0] = width.toFloat(); packed[1] = height.toFloat()
        packed[2] = 0f; packed[3] = 0f
        packed[4] = d.centerX; packed[5] = d.centerY
        packed[6] = d.tBias; packed[7] = d.tScale
        packed[8] = Float.fromBits(d.stopCount)
        packed[9] = 0f; packed[10] = 0f; packed[11] = 0f
        System.arraycopy(d.stopPositions, 0, packed, 12, MAX_GRADIENT_STOPS * 4)
        System.arraycopy(d.stopColors, 0, packed, 12 + MAX_GRADIENT_STOPS * 4, MAX_GRADIENT_STOPS * 4)

        context.queue.writeBuffer(uniform, 0uL, ArrayBuffer.of(packed))
        val bindGroup = context.device.createBindGroup(
            BindGroupDescriptor(
                layout = sweepGradientBindGroupLayout,
                entries = listOf(
                    BindGroupEntry(binding = 0u, resource = BufferBinding(buffer = uniform)),
                ),
            ),
        )
        return DrawResources(uniform = uniform, bindGroup = bindGroup)
    }

    private fun buildConicalFocalGradientRectDrawResources(
        d: ConicalFocalGradientRectDraw,
    ): DrawResources {
        val uniform = context.device.createBuffer(
            BufferDescriptor(
                size = CONICAL_FOCAL_GRADIENT_UNIFORM_SIZE,
                usage = GPUBufferUsage.Uniform or GPUBufferUsage.CopyDst,
                label = "SkWebGpuDevice.conicalFocalGradientRectDraw",
            ),
        )
        // Layout matches `conical_focal_gradient.wgsl` :
        //   offset   0 : viewport      (vec4f, only x/y used)
        //   offset  16 : affineRow0    (m00, m01, m02, _)
        //   offset  32 : affineRow1    (m10, m11, m12, _)
        //   offset  48 : focalScalars  (fP0, fP1, compensate, unswap)
        //   offset  64 : flagsCount    (negateX, subCase, count_bits, subCaseSign)
        //   offset  80 : positions[MAX_GRADIENT_STOPS] (vec4 each, .x = pos)
        //   offset 336 : colors   [MAX_GRADIENT_STOPS] (vec4 each, premul rgba)
        val packed = FloatArray(20 + MAX_GRADIENT_STOPS * 8)
        packed[0] = width.toFloat(); packed[1] = height.toFloat()
        packed[2] = 0f; packed[3] = 0f
        packed[4] = d.affine00; packed[5] = d.affine01; packed[6] = d.affine02; packed[7] = 0f
        packed[8] = d.affine10; packed[9] = d.affine11; packed[10] = d.affine12; packed[11] = 0f
        packed[12] = d.fP0; packed[13] = d.fP1; packed[14] = d.compensateFocal; packed[15] = d.unswap
        packed[16] = d.negateX; packed[17] = d.subCase
        packed[18] = Float.fromBits(d.stopCount); packed[19] = d.subCaseSign
        System.arraycopy(d.stopPositions, 0, packed, 20, MAX_GRADIENT_STOPS * 4)
        System.arraycopy(d.stopColors, 0, packed, 20 + MAX_GRADIENT_STOPS * 4, MAX_GRADIENT_STOPS * 4)

        context.queue.writeBuffer(uniform, 0uL, ArrayBuffer.of(packed))
        val bindGroup = context.device.createBindGroup(
            BindGroupDescriptor(
                layout = conicalFocalGradientBindGroupLayout,
                entries = listOf(
                    BindGroupEntry(binding = 0u, resource = BufferBinding(buffer = uniform)),
                ),
            ),
        )
        return DrawResources(uniform = uniform, bindGroup = bindGroup)
    }

    private fun buildConicalStripGradientRectDrawResources(
        d: ConicalStripGradientRectDraw,
    ): DrawResources {
        val uniform = context.device.createBuffer(
            BufferDescriptor(
                size = CONICAL_STRIP_GRADIENT_UNIFORM_SIZE,
                usage = GPUBufferUsage.Uniform or GPUBufferUsage.CopyDst,
                label = "SkWebGpuDevice.conicalStripGradientRectDraw",
            ),
        )
        // Layout matches `conical_strip_gradient.wgsl` :
        //   offset   0 : viewport      (vec4f, only x/y used)
        //   offset  16 : affineRow0    (m00, m01, m02, _)
        //   offset  32 : affineRow1    (m10, m11, m12, _)
        //   offset  48 : stripScalars  (fP0, _, count_bits, _)
        //   offset  64 : positions[MAX_GRADIENT_STOPS] (vec4 each, .x = pos)
        //   offset 320 : colors   [MAX_GRADIENT_STOPS] (vec4 each, premul rgba)
        val packed = FloatArray(16 + MAX_GRADIENT_STOPS * 8)
        packed[0] = width.toFloat(); packed[1] = height.toFloat()
        packed[2] = 0f; packed[3] = 0f
        packed[4] = d.affine00; packed[5] = d.affine01; packed[6] = d.affine02; packed[7] = 0f
        packed[8] = d.affine10; packed[9] = d.affine11; packed[10] = d.affine12; packed[11] = 0f
        packed[12] = d.stripP0; packed[13] = 0f
        packed[14] = Float.fromBits(d.stopCount); packed[15] = 0f
        System.arraycopy(d.stopPositions, 0, packed, 16, MAX_GRADIENT_STOPS * 4)
        System.arraycopy(d.stopColors, 0, packed, 16 + MAX_GRADIENT_STOPS * 4, MAX_GRADIENT_STOPS * 4)

        context.queue.writeBuffer(uniform, 0uL, ArrayBuffer.of(packed))
        val bindGroup = context.device.createBindGroup(
            BindGroupDescriptor(
                layout = conicalStripGradientBindGroupLayout,
                entries = listOf(
                    BindGroupEntry(binding = 0u, resource = BufferBinding(buffer = uniform)),
                ),
            ),
        )
        return DrawResources(uniform = uniform, bindGroup = bindGroup)
    }

    private fun buildConicalGradientRectDrawResources(d: ConicalGradientRectDraw): DrawResources {
        val uniform = context.device.createBuffer(
            BufferDescriptor(
                size = CONICAL_GRADIENT_UNIFORM_SIZE,
                usage = GPUBufferUsage.Uniform or GPUBufferUsage.CopyDst,
                label = "SkWebGpuDevice.conicalGradientRectDraw",
            ),
        )
        // Layout matches `conical_gradient.wgsl` :
        //   offset   0 : viewport     (vec4f, only x/y used)
        //   offset  16 : centerRadii  (vec4f -- center.xy, r0, r1)
        //   offset  32 : countPad     (u32 in .x as bit-reinterpreted float)
        //   offset  48 : positions[MAX_GRADIENT_STOPS] (vec4 each, .x = pos)
        //   offset 304 : colors   [MAX_GRADIENT_STOPS] (vec4 each, premul rgba)
        val packed = FloatArray(12 + MAX_GRADIENT_STOPS * 8)
        packed[0] = width.toFloat(); packed[1] = height.toFloat()
        packed[2] = 0f; packed[3] = 0f
        packed[4] = d.centerX; packed[5] = d.centerY
        packed[6] = d.startRadius; packed[7] = d.endRadius
        packed[8] = Float.fromBits(d.stopCount)
        packed[9] = 0f; packed[10] = 0f; packed[11] = 0f
        System.arraycopy(d.stopPositions, 0, packed, 12, MAX_GRADIENT_STOPS * 4)
        System.arraycopy(d.stopColors, 0, packed, 12 + MAX_GRADIENT_STOPS * 4, MAX_GRADIENT_STOPS * 4)

        context.queue.writeBuffer(uniform, 0uL, ArrayBuffer.of(packed))
        val bindGroup = context.device.createBindGroup(
            BindGroupDescriptor(
                layout = conicalGradientBindGroupLayout,
                entries = listOf(
                    BindGroupEntry(binding = 0u, resource = BufferBinding(buffer = uniform)),
                ),
            ),
        )
        return DrawResources(uniform = uniform, bindGroup = bindGroup)
    }

    private fun buildImageRectDrawResources(d: ImageRectDraw): DrawResources {
        val uniform = context.device.createBuffer(
            BufferDescriptor(
                size = IMAGE_RECT_UNIFORM_SIZE,
                usage = GPUBufferUsage.Uniform or GPUBufferUsage.CopyDst,
                label = "SkWebGpuDevice.imageRectDraw",
            ),
        )
        // Layout matches `bitmap_shader.wgsl` :
        //   offset   0 : srcRect    (vec4f -- l, t, r, b in image-pixel coords)
        //   offset  16 : dstRect    (vec4f -- l, t, r, b in device-pixel coords)
        //   offset  32 : imageSize  (vec4f -- w, h, tileX (bit-reinterp u32), tileY (bit-reinterp u32))
        //   offset  48 : paintColor (vec4f -- premul tint, default (1, 1, 1, 1))
        //   offset  64 : csFlags    (vec4f -- .x = csMode bit-reinterp u32 ; G5.3)
        //   offset  80 : csMatrix   (mat3x3<f32>, std140 padded 3 * vec4 = 48 bytes ; G5.3)
        // Total = 128 bytes.
        //
        // G5.1.1 -- `imageSize.z` carries the tile-mode ordinal as a
        // bit-reinterpreted f32 (same trick the gradient shaders use for
        // `stopCount` / `edgeCount`). The shader does `bitcast<u32>(.z)`
        // and only branches on kDecal ; the other 3 tile modes are
        // resolved by the sampler's addressMode.
        //
        // G5.2 -- per-axis tile mode : `.z = tileX`, `.w = tileY`. The
        // shader's decal check is now per-axis (covers TinyBitmapGM's
        // `kRepeat`/`kMirror` mix as a no-op, only `kDecal` on either
        // axis triggers the kill).
        //
        // G5.3 -- `csFlags.x` carries the colorspace transform mode
        // (`0 = identity` / `1 = sRGB-TF + matrix`) bit-reinterpreted ;
        // the shader's `bitcast<u32>` gates the transform branch.
        // `csMatrix` is column-major source-linear -> sRGB-linear ;
        // WGSL std140 inserts 4 bytes of padding after each 3-float
        // column, so we pack 3 columns of `(x, y, z, 0)` here.
        val m = d.csMatrix
        context.queue.writeBuffer(
            uniform, 0uL,
            ArrayBuffer.of(floatArrayOf(
                d.srcL, d.srcT, d.srcR, d.srcB,
                d.dstL, d.dstT, d.dstR, d.dstB,
                d.imageWidth.toFloat(), d.imageHeight.toFloat(),
                Float.fromBits(d.tileX.ordinal), Float.fromBits(d.tileY.ordinal),
                d.paintR, d.paintG, d.paintB, d.paintA,
                Float.fromBits(d.csMode), 0f, 0f, 0f,
                m[0], m[1], m[2], 0f,
                m[3], m[4], m[5], 0f,
                m[6], m[7], m[8], 0f,
            )),
        )
        val view = d.texture.createView()
        val sampler = bitmapSamplerFor(d.filter, d.tileX, d.tileY)
        val bindGroup = context.device.createBindGroup(
            BindGroupDescriptor(
                layout = bitmapBindGroupLayout,
                entries = listOf(
                    BindGroupEntry(binding = 0u, resource = BufferBinding(buffer = uniform)),
                    BindGroupEntry(binding = 1u, resource = view),
                    BindGroupEntry(binding = 2u, resource = sampler),
                ),
            ),
        )
        return DrawResources(uniform = uniform, bindGroup = bindGroup)
    }

    private fun buildStencilCoverAaDrawResources(d: StencilCoverAaPolygonDraw): DrawResources {
        val uniform = context.device.createBuffer(
            BufferDescriptor(
                size = AA_POLYGON_UNIFORM_SIZE,
                usage = GPUBufferUsage.Uniform or GPUBufferUsage.CopyDst,
                label = "SkWebGpuDevice.stencilCoverAaDraw",
            ),
        )
        // Layout shared with `aa_polygon.wgsl` / `aa_stencil_cover.wgsl` :
        // color + viewport + edgeCount + edges[256]. `edges` here carry
        // (Ax, Ay, Bx, By) per edge segment instead of (a, b, c, _).
        val packed = FloatArray(12 + MAX_AA_EDGES * 4)
        packed[0] = d.r; packed[1] = d.g; packed[2] = d.b; packed[3] = d.a
        packed[4] = width.toFloat(); packed[5] = height.toFloat()
        packed[6] = 0f; packed[7] = 0f
        packed[8] = Float.fromBits(d.edgeCount)
        packed[9] = 0f; packed[10] = 0f; packed[11] = 0f
        System.arraycopy(d.edges, 0, packed, 12, d.edges.size)
        context.queue.writeBuffer(uniform, 0uL, ArrayBuffer.of(packed))
        val bindGroup = context.device.createBindGroup(
            BindGroupDescriptor(
                layout = aaPolygonBindGroupLayout,
                entries = listOf(
                    BindGroupEntry(binding = 0u, resource = BufferBinding(buffer = uniform)),
                ),
            ),
        )
        val stencilVB = context.device.createBuffer(
            BufferDescriptor(
                size = (d.stencilVerts.size * Float.SIZE_BYTES).toULong(),
                usage = GPUBufferUsage.Vertex or GPUBufferUsage.CopyDst,
                label = "SkWebGpuDevice.stencilCoverAaStencilVerts",
            ),
        )
        context.queue.writeBuffer(stencilVB, 0uL, ArrayBuffer.of(d.stencilVerts))
        val coverVB = context.device.createBuffer(
            BufferDescriptor(
                size = (d.coverVerts.size * Float.SIZE_BYTES).toULong(),
                usage = GPUBufferUsage.Vertex or GPUBufferUsage.CopyDst,
                label = "SkWebGpuDevice.stencilCoverAaCoverVerts",
            ),
        )
        context.queue.writeBuffer(coverVB, 0uL, ArrayBuffer.of(d.coverVerts))
        return DrawResources(
            uniform = uniform,
            bindGroup = bindGroup,
            vertexBuffer = stencilVB,
            coverVertexBuffer = coverVB,
        )
    }

    private fun buildStencilCoverAaSweepGradientDrawResources(
        d: StencilCoverAaSweepGradientDraw,
    ): DrawResources {
        val uniform = context.device.createBuffer(
            BufferDescriptor(
                size = AA_STENCIL_COVER_GRADIENT_UNIFORM_SIZE,
                usage = GPUBufferUsage.Uniform or GPUBufferUsage.CopyDst,
                label = "SkWebGpuDevice.stencilCoverAaSweepGradientDraw",
            ),
        )
        // Layout matches `aa_stencil_cover_sweep_gradient.wgsl` -- same
        // total size as the linear / radial variants ; only slot 2 differs
        // (`startEnd` / `centerRadius` -> `centerBiasScale`).
        //   offset    0 : color           (vec4f, unused by gradient frag)
        //   offset   16 : viewport        (vec4f, only x/y used)
        //   offset   32 : centerBiasScale (vec4f -- cx, cy, tBias, tScale)
        //   offset   48 : countPad        (.x = stopCount as bit-reinterp f32)
        //   offset   64 : edgeCountPad    (.x = edgeCount as bit-reinterp f32)
        //   offset   80 : edges[MAX_AA_EDGES]      (vec4 each)
        //   offset 4176 : positions[MAX_GRADIENT_STOPS] (vec4 each, .x = pos)
        //   offset 4432 : colors[MAX_GRADIENT_STOPS]    (vec4 each, premul rgba)
        val packed = FloatArray(20 + MAX_AA_EDGES * 4 + MAX_GRADIENT_STOPS * 8)
        packed[0] = 0f; packed[1] = 0f; packed[2] = 0f; packed[3] = 0f
        packed[4] = width.toFloat(); packed[5] = height.toFloat()
        packed[6] = 0f; packed[7] = 0f
        packed[8] = d.centerX; packed[9] = d.centerY
        packed[10] = d.tBias; packed[11] = d.tScale
        packed[12] = Float.fromBits(d.stopCount)
        packed[13] = 0f; packed[14] = 0f; packed[15] = 0f
        packed[16] = Float.fromBits(d.edgeCount)
        packed[17] = 0f; packed[18] = 0f; packed[19] = 0f
        System.arraycopy(d.edges, 0, packed, 20, d.edges.size)
        System.arraycopy(d.stopPositions, 0, packed, 20 + MAX_AA_EDGES * 4, MAX_GRADIENT_STOPS * 4)
        System.arraycopy(
            d.stopColors, 0,
            packed, 20 + MAX_AA_EDGES * 4 + MAX_GRADIENT_STOPS * 4,
            MAX_GRADIENT_STOPS * 4,
        )
        context.queue.writeBuffer(uniform, 0uL, ArrayBuffer.of(packed))
        val bindGroup = context.device.createBindGroup(
            BindGroupDescriptor(
                layout = aaPolygonBindGroupLayout,
                entries = listOf(
                    BindGroupEntry(binding = 0u, resource = BufferBinding(buffer = uniform)),
                ),
            ),
        )
        val stencilVB = context.device.createBuffer(
            BufferDescriptor(
                size = (d.stencilVerts.size * Float.SIZE_BYTES).toULong(),
                usage = GPUBufferUsage.Vertex or GPUBufferUsage.CopyDst,
                label = "SkWebGpuDevice.stencilCoverAaSweepGradientStencilVerts",
            ),
        )
        context.queue.writeBuffer(stencilVB, 0uL, ArrayBuffer.of(d.stencilVerts))
        val coverVB = context.device.createBuffer(
            BufferDescriptor(
                size = (d.coverVerts.size * Float.SIZE_BYTES).toULong(),
                usage = GPUBufferUsage.Vertex or GPUBufferUsage.CopyDst,
                label = "SkWebGpuDevice.stencilCoverAaSweepGradientCoverVerts",
            ),
        )
        context.queue.writeBuffer(coverVB, 0uL, ArrayBuffer.of(d.coverVerts))
        return DrawResources(
            uniform = uniform,
            bindGroup = bindGroup,
            vertexBuffer = stencilVB,
            coverVertexBuffer = coverVB,
        )
    }

    private fun buildStencilCoverAaRadialGradientDrawResources(
        d: StencilCoverAaRadialGradientDraw,
    ): DrawResources {
        val uniform = context.device.createBuffer(
            BufferDescriptor(
                size = AA_STENCIL_COVER_GRADIENT_UNIFORM_SIZE,
                usage = GPUBufferUsage.Uniform or GPUBufferUsage.CopyDst,
                label = "SkWebGpuDevice.stencilCoverAaRadialGradientDraw",
            ),
        )
        // Layout matches `aa_stencil_cover_radial_gradient.wgsl` -- the only
        // diff vs the linear variant is slot 2 (`startEnd` -> `centerRadius`).
        // The total uniform size and every other field stay byte-compatible.
        //   offset    0 : color        (vec4f, unused by gradient frag)
        //   offset   16 : viewport     (vec4f, only x/y used)
        //   offset   32 : centerRadius (vec4f -- cx, cy, radius, _)
        //   offset   48 : countPad     (.x = stopCount as bit-reinterp f32)
        //   offset   64 : edgeCountPad (.x = edgeCount as bit-reinterp f32)
        //   offset   80 : edges[MAX_AA_EDGES]      (vec4 each)
        //   offset 4176 : positions[MAX_GRADIENT_STOPS] (vec4 each, .x = pos)
        //   offset 4432 : colors[MAX_GRADIENT_STOPS]    (vec4 each, premul rgba)
        val packed = FloatArray(20 + MAX_AA_EDGES * 4 + MAX_GRADIENT_STOPS * 8)
        packed[0] = 0f; packed[1] = 0f; packed[2] = 0f; packed[3] = 0f
        packed[4] = width.toFloat(); packed[5] = height.toFloat()
        packed[6] = 0f; packed[7] = 0f
        packed[8] = d.centerX; packed[9] = d.centerY
        packed[10] = d.radius; packed[11] = 0f
        packed[12] = Float.fromBits(d.stopCount)
        packed[13] = 0f; packed[14] = 0f; packed[15] = 0f
        packed[16] = Float.fromBits(d.edgeCount)
        packed[17] = 0f; packed[18] = 0f; packed[19] = 0f
        System.arraycopy(d.edges, 0, packed, 20, d.edges.size)
        System.arraycopy(d.stopPositions, 0, packed, 20 + MAX_AA_EDGES * 4, MAX_GRADIENT_STOPS * 4)
        System.arraycopy(
            d.stopColors, 0,
            packed, 20 + MAX_AA_EDGES * 4 + MAX_GRADIENT_STOPS * 4,
            MAX_GRADIENT_STOPS * 4,
        )
        context.queue.writeBuffer(uniform, 0uL, ArrayBuffer.of(packed))
        val bindGroup = context.device.createBindGroup(
            BindGroupDescriptor(
                layout = aaPolygonBindGroupLayout,
                entries = listOf(
                    BindGroupEntry(binding = 0u, resource = BufferBinding(buffer = uniform)),
                ),
            ),
        )
        val stencilVB = context.device.createBuffer(
            BufferDescriptor(
                size = (d.stencilVerts.size * Float.SIZE_BYTES).toULong(),
                usage = GPUBufferUsage.Vertex or GPUBufferUsage.CopyDst,
                label = "SkWebGpuDevice.stencilCoverAaRadialGradientStencilVerts",
            ),
        )
        context.queue.writeBuffer(stencilVB, 0uL, ArrayBuffer.of(d.stencilVerts))
        val coverVB = context.device.createBuffer(
            BufferDescriptor(
                size = (d.coverVerts.size * Float.SIZE_BYTES).toULong(),
                usage = GPUBufferUsage.Vertex or GPUBufferUsage.CopyDst,
                label = "SkWebGpuDevice.stencilCoverAaRadialGradientCoverVerts",
            ),
        )
        context.queue.writeBuffer(coverVB, 0uL, ArrayBuffer.of(d.coverVerts))
        return DrawResources(
            uniform = uniform,
            bindGroup = bindGroup,
            vertexBuffer = stencilVB,
            coverVertexBuffer = coverVB,
        )
    }

    private fun buildStencilCoverAaGradientDrawResources(
        d: StencilCoverAaGradientDraw,
    ): DrawResources {
        val uniform = context.device.createBuffer(
            BufferDescriptor(
                size = AA_STENCIL_COVER_GRADIENT_UNIFORM_SIZE,
                usage = GPUBufferUsage.Uniform or GPUBufferUsage.CopyDst,
                label = "SkWebGpuDevice.stencilCoverAaGradientDraw",
            ),
        )
        // Layout matches `aa_stencil_cover_gradient.wgsl` (note the
        // leading `color` slot kept in lockstep with `solid_polygon.wgsl`
        // so the stencil-write pass can share this draw's bind group --
        // it only reads `viewport` for its NDC remap and ignores the
        // rest).
        //   offset    0 : color        (vec4f, unused by gradient frag)
        //   offset   16 : viewport     (vec4f, only x/y used)
        //   offset   32 : startEnd     (vec4f -- sx, sy, ex, ey)
        //   offset   48 : countPad     (.x = stopCount as bit-reinterp f32)
        //   offset   64 : edgeCountPad (.x = edgeCount as bit-reinterp f32)
        //   offset   80 : edges[MAX_AA_EDGES]      (vec4 each)
        //   offset 4176 : positions[MAX_GRADIENT_STOPS] (vec4 each, .x = pos)
        //   offset 4432 : colors[MAX_GRADIENT_STOPS]    (vec4 each, premul rgba)
        val packed = FloatArray(20 + MAX_AA_EDGES * 4 + MAX_GRADIENT_STOPS * 8)
        // color (unused ; left at zero)
        packed[0] = 0f; packed[1] = 0f; packed[2] = 0f; packed[3] = 0f
        // viewport
        packed[4] = width.toFloat(); packed[5] = height.toFloat()
        packed[6] = 0f; packed[7] = 0f
        // startEnd
        packed[8] = d.startX; packed[9] = d.startY
        packed[10] = d.endX;  packed[11] = d.endY
        // countPad
        packed[12] = Float.fromBits(d.stopCount)
        packed[13] = 0f; packed[14] = 0f; packed[15] = 0f
        // edgeCountPad
        packed[16] = Float.fromBits(d.edgeCount)
        packed[17] = 0f; packed[18] = 0f; packed[19] = 0f
        // edges
        System.arraycopy(d.edges, 0, packed, 20, d.edges.size)
        // positions + colors
        System.arraycopy(d.stopPositions, 0, packed, 20 + MAX_AA_EDGES * 4, MAX_GRADIENT_STOPS * 4)
        System.arraycopy(
            d.stopColors, 0,
            packed, 20 + MAX_AA_EDGES * 4 + MAX_GRADIENT_STOPS * 4,
            MAX_GRADIENT_STOPS * 4,
        )
        context.queue.writeBuffer(uniform, 0uL, ArrayBuffer.of(packed))
        val bindGroup = context.device.createBindGroup(
            BindGroupDescriptor(
                layout = aaPolygonBindGroupLayout,
                entries = listOf(
                    BindGroupEntry(binding = 0u, resource = BufferBinding(buffer = uniform)),
                ),
            ),
        )
        val stencilVB = context.device.createBuffer(
            BufferDescriptor(
                size = (d.stencilVerts.size * Float.SIZE_BYTES).toULong(),
                usage = GPUBufferUsage.Vertex or GPUBufferUsage.CopyDst,
                label = "SkWebGpuDevice.stencilCoverAaGradientStencilVerts",
            ),
        )
        context.queue.writeBuffer(stencilVB, 0uL, ArrayBuffer.of(d.stencilVerts))
        val coverVB = context.device.createBuffer(
            BufferDescriptor(
                size = (d.coverVerts.size * Float.SIZE_BYTES).toULong(),
                usage = GPUBufferUsage.Vertex or GPUBufferUsage.CopyDst,
                label = "SkWebGpuDevice.stencilCoverAaGradientCoverVerts",
            ),
        )
        context.queue.writeBuffer(coverVB, 0uL, ArrayBuffer.of(d.coverVerts))
        return DrawResources(
            uniform = uniform,
            bindGroup = bindGroup,
            vertexBuffer = stencilVB,
            coverVertexBuffer = coverVB,
        )
    }

    // ─── Conical gradient on non-rect (G4.4.3) ─────────────────────────────

    private fun buildStencilCoverAaConicalGradientDrawResources(
        d: StencilCoverAaConicalGradientDraw,
    ): DrawResources {
        val uniform = context.device.createBuffer(
            BufferDescriptor(
                size = AA_STENCIL_COVER_GRADIENT_UNIFORM_SIZE,
                usage = GPUBufferUsage.Uniform or GPUBufferUsage.CopyDst,
                label = "SkWebGpuDevice.stencilCoverAaConicalGradientDraw",
            ),
        )
        // Layout matches `aa_stencil_cover_conical_gradient.wgsl` -- the
        // only diff vs the radial variant is slot 2 carries `centerRadii`
        // (cx, cy, r0, r1) instead of `centerRadius` (cx, cy, radius, _).
        // Total uniform size identical to linear / radial / sweep variants.
        //   offset    0 : color        (vec4f, unused by gradient frag)
        //   offset   16 : viewport     (vec4f, only x/y used)
        //   offset   32 : centerRadii  (vec4f -- cx, cy, r0, r1)
        //   offset   48 : countPad     (.x = stopCount as bit-reinterp f32)
        //   offset   64 : edgeCountPad (.x = edgeCount as bit-reinterp f32)
        //   offset   80 : edges[MAX_AA_EDGES]      (vec4 each)
        //   offset 4176 : positions[MAX_GRADIENT_STOPS] (vec4 each, .x = pos)
        //   offset 4432 : colors[MAX_GRADIENT_STOPS]    (vec4 each, premul rgba)
        val packed = FloatArray(20 + MAX_AA_EDGES * 4 + MAX_GRADIENT_STOPS * 8)
        packed[0] = 0f; packed[1] = 0f; packed[2] = 0f; packed[3] = 0f
        packed[4] = width.toFloat(); packed[5] = height.toFloat()
        packed[6] = 0f; packed[7] = 0f
        packed[8] = d.centerX; packed[9] = d.centerY
        packed[10] = d.startRadius; packed[11] = d.endRadius
        packed[12] = Float.fromBits(d.stopCount)
        packed[13] = 0f; packed[14] = 0f; packed[15] = 0f
        packed[16] = Float.fromBits(d.edgeCount)
        packed[17] = 0f; packed[18] = 0f; packed[19] = 0f
        System.arraycopy(d.edges, 0, packed, 20, d.edges.size)
        System.arraycopy(d.stopPositions, 0, packed, 20 + MAX_AA_EDGES * 4, MAX_GRADIENT_STOPS * 4)
        System.arraycopy(
            d.stopColors, 0,
            packed, 20 + MAX_AA_EDGES * 4 + MAX_GRADIENT_STOPS * 4,
            MAX_GRADIENT_STOPS * 4,
        )
        context.queue.writeBuffer(uniform, 0uL, ArrayBuffer.of(packed))
        val bindGroup = context.device.createBindGroup(
            BindGroupDescriptor(
                layout = aaPolygonBindGroupLayout,
                entries = listOf(
                    BindGroupEntry(binding = 0u, resource = BufferBinding(buffer = uniform)),
                ),
            ),
        )
        val stencilVB = context.device.createBuffer(
            BufferDescriptor(
                size = (d.stencilVerts.size * Float.SIZE_BYTES).toULong(),
                usage = GPUBufferUsage.Vertex or GPUBufferUsage.CopyDst,
                label = "SkWebGpuDevice.stencilCoverAaConicalGradientStencilVerts",
            ),
        )
        context.queue.writeBuffer(stencilVB, 0uL, ArrayBuffer.of(d.stencilVerts))
        val coverVB = context.device.createBuffer(
            BufferDescriptor(
                size = (d.coverVerts.size * Float.SIZE_BYTES).toULong(),
                usage = GPUBufferUsage.Vertex or GPUBufferUsage.CopyDst,
                label = "SkWebGpuDevice.stencilCoverAaConicalGradientCoverVerts",
            ),
        )
        context.queue.writeBuffer(coverVB, 0uL, ArrayBuffer.of(d.coverVerts))
        return DrawResources(
            uniform = uniform,
            bindGroup = bindGroup,
            vertexBuffer = stencilVB,
            coverVertexBuffer = coverVB,
        )
    }

    private fun buildStencilCoverAaConicalFocalGradientDrawResources(
        d: StencilCoverAaConicalFocalGradientDraw,
    ): DrawResources {
        val uniform = context.device.createBuffer(
            BufferDescriptor(
                size = AA_STENCIL_COVER_CONICAL_FOCAL_GRADIENT_UNIFORM_SIZE,
                usage = GPUBufferUsage.Uniform or GPUBufferUsage.CopyDst,
                label = "SkWebGpuDevice.stencilCoverAaConicalFocalGradientDraw",
            ),
        )
        // Layout matches `aa_stencil_cover_conical_focal_gradient.wgsl` :
        //   offset    0 : color        (vec4f, unused by gradient frag)
        //   offset   16 : viewport     (vec4f, only x/y used)
        //   offset   32 : affineRow0   (m00, m01, m02, _)
        //   offset   48 : affineRow1   (m10, m11, m12, _)
        //   offset   64 : focalScalars (fP0, fP1, compensate, unswap)
        //   offset   80 : flagsCount   (negateX, subCase, count_bits, subCaseSign)
        //   offset   96 : edgeCountPad (.x = edgeCount as bit-reinterp f32)
        //   offset  112 : edges[MAX_AA_EDGES]      (vec4 each)
        //   offset 4208 : positions[MAX_GRADIENT_STOPS] (vec4 each, .x = pos)
        //   offset 4464 : colors[MAX_GRADIENT_STOPS]    (vec4 each, premul rgba)
        val packed = FloatArray(28 + MAX_AA_EDGES * 4 + MAX_GRADIENT_STOPS * 8)
        packed[0] = 0f; packed[1] = 0f; packed[2] = 0f; packed[3] = 0f
        packed[4] = width.toFloat(); packed[5] = height.toFloat()
        packed[6] = 0f; packed[7] = 0f
        packed[8] = d.affine00; packed[9] = d.affine01; packed[10] = d.affine02; packed[11] = 0f
        packed[12] = d.affine10; packed[13] = d.affine11; packed[14] = d.affine12; packed[15] = 0f
        packed[16] = d.fP0; packed[17] = d.fP1
        packed[18] = d.compensateFocal; packed[19] = d.unswap
        packed[20] = d.negateX; packed[21] = d.subCase
        packed[22] = Float.fromBits(d.stopCount); packed[23] = d.subCaseSign
        packed[24] = Float.fromBits(d.edgeCount)
        packed[25] = 0f; packed[26] = 0f; packed[27] = 0f
        System.arraycopy(d.edges, 0, packed, 28, d.edges.size)
        System.arraycopy(d.stopPositions, 0, packed, 28 + MAX_AA_EDGES * 4, MAX_GRADIENT_STOPS * 4)
        System.arraycopy(
            d.stopColors, 0,
            packed, 28 + MAX_AA_EDGES * 4 + MAX_GRADIENT_STOPS * 4,
            MAX_GRADIENT_STOPS * 4,
        )
        context.queue.writeBuffer(uniform, 0uL, ArrayBuffer.of(packed))
        val bindGroup = context.device.createBindGroup(
            BindGroupDescriptor(
                layout = aaPolygonBindGroupLayout,
                entries = listOf(
                    BindGroupEntry(binding = 0u, resource = BufferBinding(buffer = uniform)),
                ),
            ),
        )
        val stencilVB = context.device.createBuffer(
            BufferDescriptor(
                size = (d.stencilVerts.size * Float.SIZE_BYTES).toULong(),
                usage = GPUBufferUsage.Vertex or GPUBufferUsage.CopyDst,
                label = "SkWebGpuDevice.stencilCoverAaConicalFocalGradientStencilVerts",
            ),
        )
        context.queue.writeBuffer(stencilVB, 0uL, ArrayBuffer.of(d.stencilVerts))
        val coverVB = context.device.createBuffer(
            BufferDescriptor(
                size = (d.coverVerts.size * Float.SIZE_BYTES).toULong(),
                usage = GPUBufferUsage.Vertex or GPUBufferUsage.CopyDst,
                label = "SkWebGpuDevice.stencilCoverAaConicalFocalGradientCoverVerts",
            ),
        )
        context.queue.writeBuffer(coverVB, 0uL, ArrayBuffer.of(d.coverVerts))
        return DrawResources(
            uniform = uniform,
            bindGroup = bindGroup,
            vertexBuffer = stencilVB,
            coverVertexBuffer = coverVB,
        )
    }

    private fun buildStencilCoverAaBitmapShaderDrawResources(
        d: StencilCoverAaBitmapShaderDraw,
    ): DrawResources {
        val uniform = context.device.createBuffer(
            BufferDescriptor(
                size = AA_STENCIL_COVER_BITMAP_SHADER_UNIFORM_SIZE,
                usage = GPUBufferUsage.Uniform or GPUBufferUsage.CopyDst,
                label = "SkWebGpuDevice.stencilCoverAaBitmapShaderDraw",
            ),
        )
        // Layout matches `aa_stencil_cover_bitmap_shader.wgsl` :
        //   offset    0 : color        (vec4f, unused by bitmap frag ; kept
        //                                for layout-compat with solid_polygon)
        //   offset   16 : viewport     (vec4f, only x/y used)
        //   offset   32 : srcRect      (l, t, r, b in image-pixel coords)
        //   offset   48 : dstRect      (l, t, r, b in device-pixel coords)
        //   offset   64 : imageSize    (w, h, tileX bit-reinterp u32, tileY bit-reinterp u32)
        //   offset   80 : paintColor   (vec4f, premul tint)
        //   offset   96 : csFlags      (.x = csMode bit-reinterp u32 ; G5.3)
        //   offset  112 : csMatrix     (mat3x3<f32>, std140 padded 3 * vec4 = 48 bytes ; G5.3)
        //   offset  160 : edgeCountPad (.x = edgeCount as bit-reinterp f32)
        //   offset  176 : edges[MAX_AA_EDGES] (vec4 each)
        // Total = 4272 bytes.
        val packed = FloatArray(44 + MAX_AA_EDGES * 4)
        // color (unused ; left at zero)
        packed[0] = 0f; packed[1] = 0f; packed[2] = 0f; packed[3] = 0f
        // viewport
        packed[4] = width.toFloat(); packed[5] = height.toFloat()
        packed[6] = 0f; packed[7] = 0f
        // srcRect
        packed[8] = d.srcL; packed[9] = d.srcT; packed[10] = d.srcR; packed[11] = d.srcB
        // dstRect
        packed[12] = d.dstL; packed[13] = d.dstT; packed[14] = d.dstR; packed[15] = d.dstB
        // imageSize
        packed[16] = d.imageWidth.toFloat(); packed[17] = d.imageHeight.toFloat()
        packed[18] = Float.fromBits(d.tileX.ordinal); packed[19] = Float.fromBits(d.tileY.ordinal)
        // paintColor
        packed[20] = d.paintR; packed[21] = d.paintG; packed[22] = d.paintB; packed[23] = d.paintA
        // csFlags
        packed[24] = Float.fromBits(d.csMode); packed[25] = 0f; packed[26] = 0f; packed[27] = 0f
        // csMatrix : column-major 3x3, std140 padded to 3 * vec4
        val m = d.csMatrix
        packed[28] = m[0]; packed[29] = m[1]; packed[30] = m[2]; packed[31] = 0f
        packed[32] = m[3]; packed[33] = m[4]; packed[34] = m[5]; packed[35] = 0f
        packed[36] = m[6]; packed[37] = m[7]; packed[38] = m[8]; packed[39] = 0f
        // edgeCountPad
        packed[40] = Float.fromBits(d.edgeCount)
        packed[41] = 0f; packed[42] = 0f; packed[43] = 0f
        // edges
        System.arraycopy(d.edges, 0, packed, 44, d.edges.size)
        context.queue.writeBuffer(uniform, 0uL, ArrayBuffer.of(packed))
        val view = d.texture.createView()
        val sampler = bitmapSamplerFor(d.filter, d.tileX, d.tileY)
        val bindGroup = context.device.createBindGroup(
            BindGroupDescriptor(
                layout = aaStencilCoverBitmapShaderBindGroupLayout,
                entries = listOf(
                    BindGroupEntry(binding = 0u, resource = BufferBinding(buffer = uniform)),
                    BindGroupEntry(binding = 1u, resource = view),
                    BindGroupEntry(binding = 2u, resource = sampler),
                ),
            ),
        )
        val stencilVB = context.device.createBuffer(
            BufferDescriptor(
                size = (d.stencilVerts.size * Float.SIZE_BYTES).toULong(),
                usage = GPUBufferUsage.Vertex or GPUBufferUsage.CopyDst,
                label = "SkWebGpuDevice.stencilCoverAaBitmapShaderStencilVerts",
            ),
        )
        context.queue.writeBuffer(stencilVB, 0uL, ArrayBuffer.of(d.stencilVerts))
        val coverVB = context.device.createBuffer(
            BufferDescriptor(
                size = (d.coverVerts.size * Float.SIZE_BYTES).toULong(),
                usage = GPUBufferUsage.Vertex or GPUBufferUsage.CopyDst,
                label = "SkWebGpuDevice.stencilCoverAaBitmapShaderCoverVerts",
            ),
        )
        context.queue.writeBuffer(coverVB, 0uL, ArrayBuffer.of(d.coverVerts))
        return DrawResources(
            uniform = uniform,
            bindGroup = bindGroup,
            vertexBuffer = stencilVB,
            coverVertexBuffer = coverVB,
        )
    }

    override fun close() {
        rectPipelineCache.values.forEach { it.close() }
        rectPipelineCache.clear()
        polygonPipelineCache.values.forEach { it.close() }
        polygonPipelineCache.clear()
        aaPolygonPipelineCache.values.forEach { it.close() }
        aaPolygonPipelineCache.clear()
        coverPipelineCache.values.forEach { it.close() }
        coverPipelineCache.clear()
        aaStencilCoverPipelineCache.values.forEach { it.close() }
        aaStencilCoverPipelineCache.clear()
        aaStencilCoverGradientPipelineCache.values.forEach { it.close() }
        aaStencilCoverGradientPipelineCache.clear()
        aaStencilCoverRadialGradientPipelineCache.values.forEach { it.close() }
        aaStencilCoverRadialGradientPipelineCache.clear()
        aaStencilCoverSweepGradientPipelineCache.values.forEach { it.close() }
        aaStencilCoverSweepGradientPipelineCache.clear()
        aaStencilCoverConicalGradientPipelineCache.values.forEach { it.close() }
        aaStencilCoverConicalGradientPipelineCache.clear()
        aaStencilCoverConicalFocalGradientPipelineCache.values.forEach { it.close() }
        aaStencilCoverConicalFocalGradientPipelineCache.clear()
        aaStencilCoverBitmapShaderPipelineCache.values.forEach { it.close() }
        aaStencilCoverBitmapShaderPipelineCache.clear()
        aaStencilCoverBitmapShaderStencilPipeline.close()
        linearGradientPipelineCache.values.forEach { it.close() }
        linearGradientPipelineCache.clear()
        radialGradientPipelineCache.values.forEach { it.close() }
        radialGradientPipelineCache.clear()
        sweepGradientPipelineCache.values.forEach { it.close() }
        sweepGradientPipelineCache.clear()
        bitmapPipelineCache.values.forEach { it.close() }
        bitmapPipelineCache.clear()
        bitmapSamplerCache.values.forEach { it.close() }
        bitmapSamplerCache.clear()
        imageTextureCache.values.forEach { it.close() }
        imageTextureCache.clear()
        stencilWritePipeline.close()
        presentPipeline.close()
        rectShader.close()
        polygonShader.close()
        aaPolygonShader.close()
        aaStencilCoverShader.close()
        aaStencilCoverGradientShader.close()
        aaStencilCoverRadialGradientShader.close()
        aaStencilCoverSweepGradientShader.close()
        aaStencilCoverConicalGradientShader.close()
        aaStencilCoverConicalFocalGradientShader.close()
        aaStencilCoverBitmapShader.close()
        linearGradientShader.close()
        radialGradientShader.close()
        sweepGradientShader.close()
        bitmapShader.close()
        presentShader.close()
        intermediateView.close()
        intermediateTexture.close()
        depthStencilView.close()
        depthStencilTexture.close()
        target.close()
    }

    private companion object {
        /**
         * Size of the rect per-draw uniform :
         *   color (16) + outerBounds (16) + innerBounds (16) = 48 bytes.
         * G3.1.1 bumped from 32 to 48 to carry the optional annular
         * inner-rect bounds (degenerate for fill draws).
         */
        const val RECT_UNIFORM_SIZE: ULong = 48uL
        /**
         * Sentinel value for degenerate inner bounds (fill draws). The
         * shader's `clamp(min(p+1, r) - max(p, l), 0, 1)` naturally
         * collapses to 0 when `r < l` (and `b < t`), regardless of `p`.
         * Using a large positive value for l/t and its negation for r/b
         * keeps the math well-clear of FP precision concerns.
         */
        const val DEGENERATE_INNER: Float = 1e10f
        /** Size of the polygon per-draw uniform : `color: vec4f` + `viewport: vec4f` = 32 bytes. */
        const val POLYGON_UNIFORM_SIZE: ULong = 32uL
        /**
         * Max polygon vertex count for the AA path. Bounded by the
         * `array<vec4f, 256>` in `aa_polygon.wgsl` ; circles flattened
         * to ~32 segments fit easily, even complex curved paths under
         * normal scales.
         */
        const val MAX_AA_EDGES: Int = 256
        /**
         * Size of the AA polygon per-draw uniform :
         *   color (16) + viewport (16) + edgeCount+pad (16) + edges (256*16) = 4144 bytes.
         */
        const val AA_POLYGON_UNIFORM_SIZE: ULong = 4144uL // 48 + 256 * 16
        /**
         * G4.1 — cap on `SkLinearGradient` stop count for the WGSL
         * uniform table. Skia's `MakeLinear` accepts arbitrary counts but
         * gradient GMs in scope cap at 6 stops (FillrectGradientGM row 8).
         * A larger cap can be added when a real GM exceeds it.
         */
        const val MAX_GRADIENT_STOPS: Int = 16
        /**
         * Size of the linear-gradient per-draw uniform :
         *   startEnd (16) + viewport (16) + countPad (16) +
         *   positions (16 * 16) + colors (16 * 16) = 560 bytes.
         */
        const val LINEAR_GRADIENT_UNIFORM_SIZE: ULong = 560uL // 48 + 16 * 16 + 16 * 16
        /**
         * G4.2 -- size of the radial-gradient per-draw uniform :
         *   viewport (16) + centerRadius (16) + countPad (16) +
         *   positions (16 * 16) + colors (16 * 16) = 560 bytes.
         * Same total as the linear uniform but the first 32 bytes carry
         * different fields (viewport / center+radius vs startEnd / viewport).
         */
        const val RADIAL_GRADIENT_UNIFORM_SIZE: ULong = 560uL
        /**
         * G4.3 -- size of the sweep-gradient per-draw uniform :
         *   viewport (16) + centerBiasScale (16) + countPad (16) +
         *   positions (16 * 16) + colors (16 * 16) = 560 bytes.
         * Same total as the linear / radial uniforms ; only the second
         * vec4 carries different fields (center.xy + tBias + tScale here).
         */
        const val SWEEP_GRADIENT_UNIFORM_SIZE: ULong = 560uL
        /**
         * G4.4 -- size of the conical-gradient per-draw uniform :
         *   viewport (16) + centerRadii (16) + countPad (16) +
         *   positions (16 * 16) + colors (16 * 16) = 560 bytes.
         * Same total as the linear / radial / sweep uniforms ; only the
         * second vec4 carries different fields (centre.xy + r0 + r1 here,
         * for the kRadial sub-case).
         */
        const val CONICAL_GRADIENT_UNIFORM_SIZE: ULong = 560uL
        /**
         * G4.4.1 -- size of the conical focal-inside per-draw uniform :
         *   viewport (16) + affineRow0 (16) + affineRow1 (16) +
         *   focalScalars (16) + flagsCount (16) +
         *   positions (16 * 16) + colors (16 * 16) = 592 bytes.
         * 32 bytes larger than the kRadial conical uniform because the
         * focal-frame affine takes 2 vec4f slots and the flags / focal
         * scalars consume two more.
         */
        const val CONICAL_FOCAL_GRADIENT_UNIFORM_SIZE: ULong = 592uL
        /**
         * G4.4.4 -- size of the conical kStrip per-draw uniform :
         *   viewport (16) + affineRow0 (16) + affineRow1 (16) +
         *   stripScalars (16) + positions (16 * 16) + colors (16 * 16) = 576 bytes.
         * 16 bytes smaller than the focal-inside uniform (no flagsCount
         * vec4 ; the strip has only fP0 + stop count).
         */
        const val CONICAL_STRIP_GRADIENT_UNIFORM_SIZE: ULong = 576uL
        /**
         * G5.1 / G5.3 -- size of the bitmap-shader per-draw uniform :
         *   srcRect (16) + dstRect (16) + imageSize (16) + paintColor (16) +
         *   csFlags (16) + csMatrix (mat3x3 -> 3 * 16 = 48) = 128 bytes.
         * Matches `Uniforms { srcRect, dstRect, imageSize, paintColor,
         * csFlags, csMatrix }` in `bitmap_shader.wgsl`. WGSL std140
         * stores each `mat3x3<f32>` column padded to 16 bytes, hence
         * the 48 bytes for the 9 floats of the primaries matrix.
         */
        const val IMAGE_RECT_UNIFORM_SIZE: ULong = 128uL
        /**
         * G5.3 -- identity column-major 3x3 used as the no-op
         * primaries matrix when [bitmapColorSpaceFor] returns the sRGB
         * fast path. Shared across all draws to avoid re-allocating
         * a fresh FloatArray per [ImageRectDraw].
         */
        val IDENTITY_CS_MATRIX: FloatArray =
            floatArrayOf(1f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f)
        /**
         * G4.1.2 -- size of the AA stencil-cover gradient per-draw uniform :
         *   color (16) + viewport (16) + startEnd (16) + countPad (16) +
         *   edgeCountPad (16) + edges (256 * 16) + positions (16 * 16) +
         *   colors (16 * 16) = 4688 bytes.
         * The leading `color` slot matches the polygon shader's layout so
         * the stencil-write pass can share this draw's bind group.
         */
        const val AA_STENCIL_COVER_GRADIENT_UNIFORM_SIZE: ULong = 4688uL
        /**
         * G4.4.3 -- size of the AA stencil-cover conical focal-inside
         * per-draw uniform :
         *   color (16) + viewport (16) + affineRow0 (16) + affineRow1 (16) +
         *   focalScalars (16) + flagsCount (16) + edgeCountPad (16) +
         *   edges (256 * 16) + positions (16 * 16) + colors (16 * 16) = 4720 bytes.
         * 32 bytes larger than the kRadial / linear / radial / sweep stencil-
         * cover uniforms because the focal-frame affine takes 2 vec4f slots
         * and the focal scalars / flags consume two more (vs one in the
         * other variants).
         */
        const val AA_STENCIL_COVER_CONICAL_FOCAL_GRADIENT_UNIFORM_SIZE: ULong = 4720uL
        /**
         * G5.2.1 -- size of the AA stencil-cover bitmap-shader per-draw uniform :
         *   color (16) + viewport (16) + srcRect (16) + dstRect (16) +
         *   imageSize (16) + paintColor (16) + csFlags (16) + csMatrix (48) +
         *   edgeCountPad (16) + edges (256 * 16) = 4272 bytes.
         * Mirror of [IMAGE_RECT_UNIFORM_SIZE] (128 bytes) extended with the
         * stencil-cover machinery's `color` / `viewport` / `edgeCountPad`
         * + `edges[256]` tail. The leading `color` slot matches the polygon
         * shader's layout so the bitmap-shader stencil-write pass can share
         * this draw's bind group.
         */
        const val AA_STENCIL_COVER_BITMAP_SHADER_UNIFORM_SIZE: ULong = 4272uL
        const val FULL_SCREEN_TRIANGLE_VERTEX_COUNT: UInt = 3u

        /**
         * Build the polygon perimeter's edge equations in
         * `a*x + b*y + c = 0` form, normalised so signed distance is
         * positive *inside* the polygon. Winding (CW vs CCW) is detected
         * via the polygon's signed area and folded into the sign.
         *
         * `out` must have capacity for `MAX_AA_EDGES * 4` floats ; only
         * the first `n * 4` entries (where `n = devVerts.size / 2`) are
         * written. Caller guarantees `n <= MAX_AA_EDGES`.
         */
        /**
         * G3.3b.2b — fan-tessellate each contour from its own first
         * vertex, concatenate the triangle lists. The contour boundaries
         * come from [contourStarts] (vertex index of each contour's
         * first vertex ; the next entry — or the total vertex count
         * for the last contour — is the contour's exclusive end).
         *
         * Each contour's triangle winding follows its vertex order. With
         * the stencil pipeline's front-face increment + back-face
         * decrement, a CW outer contour + CCW inner contour leaves
         * stencil = 0 inside the hole, stencil = 1 inside the ring.
         */
        fun fanTessellateContours(
            devVerts: ArrayList<Float>,
            contourStarts: ArrayList<Int>,
        ): FloatArray {
            val total = devVerts.size / 2
            var triCount = 0
            for (c in contourStarts.indices) {
                val start = contourStarts[c]
                val end = if (c + 1 < contourStarts.size) contourStarts[c + 1] else total
                val len = end - start
                if (len >= 3) triCount += len - 2
            }
            val out = FloatArray(triCount * 6)
            var w = 0
            for (c in contourStarts.indices) {
                val start = contourStarts[c]
                val end = if (c + 1 < contourStarts.size) contourStarts[c + 1] else total
                val len = end - start
                if (len < 3) continue
                val v0x = devVerts[start * 2]; val v0y = devVerts[start * 2 + 1]
                for (i in 1 until len - 1) {
                    val vIa = start + i
                    val vIb = start + i + 1
                    out[w++] = v0x; out[w++] = v0y
                    out[w++] = devVerts[vIa * 2]; out[w++] = devVerts[vIa * 2 + 1]
                    out[w++] = devVerts[vIb * 2]; out[w++] = devVerts[vIb * 2 + 1]
                }
            }
            return out
        }

        /**
         * G3.3b.3b — full-viewport 2-triangle quad. Used as the cover
         * quad for inverse fill types : the stencil decides which
         * fragments belong to the "outside" of the path, and the cover
         * pass must rasterise the entire device (scissored to the
         * current clip) so those fragments get a chance to write color.
         */
        fun viewportTrianglesFor(viewportW: Int, viewportH: Int): FloatArray {
            val w = viewportW.toFloat()
            val h = viewportH.toFloat()
            return floatArrayOf(
                0f, 0f, w, 0f, w, h,
                0f, 0f, w, h, 0f, h,
            )
        }

        /**
         * Build a 2-triangle bbox covering the polygon, inflated by 1
         * device pixel outward and clamped to the viewport. Used by the
         * AA polygon path so every near-edge pixel reaches the fragment
         * shader for coverage evaluation.
         */
        /**
         * Cross-product convexity test. Walks consecutive vertex triples
         * `(v[i], v[i+1], v[i+2])` (indices mod n) and checks whether
         * every non-degenerate turn shares the same orientation. A sign
         * flip = at least one reflex vertex = concave. Collinear triples
         * (cross product = 0) are ignored. Triangles (`n < 4`) are
         * trivially convex.
         */
        fun isPolygonConvex(devVerts: ArrayList<Float>): Boolean {
            val n = devVerts.size / 2
            if (n < 4) return true
            var sign = 0
            for (i in 0 until n) {
                val ax = devVerts[i * 2]
                val ay = devVerts[i * 2 + 1]
                val bx = devVerts[((i + 1) % n) * 2]
                val by = devVerts[((i + 1) % n) * 2 + 1]
                val cx = devVerts[((i + 2) % n) * 2]
                val cy = devVerts[((i + 2) % n) * 2 + 1]
                val cross = (bx - ax) * (cy - by) - (by - ay) * (cx - bx)
                if (cross == 0f) continue
                val newSign = if (cross > 0f) 1 else -1
                if (sign == 0) {
                    sign = newSign
                } else if (sign != newSign) {
                    return false
                }
            }
            return true
        }

        /**
         * G3.3b.3a — for each contour, emit one edge segment per vertex
         * (vertex `i` to vertex `i+1`, with the last vertex closing back
         * to the contour's first vertex). Edges across all contours are
         * concatenated into [out] as `(Ax, Ay, Bx, By)` quads. Caller
         * guarantees the total edge count fits in `MAX_AA_EDGES`.
         */
        fun buildContourEdgeSegments(
            devVerts: ArrayList<Float>,
            contourStarts: ArrayList<Int>,
            out: FloatArray,
        ) {
            val total = devVerts.size / 2
            var w = 0
            for (c in contourStarts.indices) {
                val start = contourStarts[c]
                val end = if (c + 1 < contourStarts.size) contourStarts[c + 1] else total
                val len = end - start
                if (len < 2) continue
                for (i in 0 until len) {
                    val a = start + i
                    val b = if (i == len - 1) start else (a + 1)
                    out[w++] = devVerts[a * 2]
                    out[w++] = devVerts[a * 2 + 1]
                    out[w++] = devVerts[b * 2]
                    out[w++] = devVerts[b * 2 + 1]
                }
            }
        }

        fun bboxTrianglesFor(
            devVerts: ArrayList<Float>,
            viewportW: Int,
            viewportH: Int,
        ): FloatArray {
            val n = devVerts.size / 2
            var bbL = Float.POSITIVE_INFINITY
            var bbT = Float.POSITIVE_INFINITY
            var bbR = Float.NEGATIVE_INFINITY
            var bbB = Float.NEGATIVE_INFINITY
            for (i in 0 until n) {
                val x = devVerts[i * 2]; val y = devVerts[i * 2 + 1]
                if (x < bbL) bbL = x
                if (x > bbR) bbR = x
                if (y < bbT) bbT = y
                if (y > bbB) bbB = y
            }
            // Inflate by 1px outward, clamp to viewport.
            bbL = (bbL - 1f).coerceAtLeast(0f)
            bbT = (bbT - 1f).coerceAtLeast(0f)
            bbR = (bbR + 1f).coerceAtMost(viewportW.toFloat())
            bbB = (bbB + 1f).coerceAtMost(viewportH.toFloat())
            return floatArrayOf(
                bbL, bbT, bbR, bbT, bbR, bbB,
                bbL, bbT, bbR, bbB, bbL, bbB,
            )
        }

        fun buildPerimeterEdges(devVerts: ArrayList<Float>, out: FloatArray) {
            val n = devVerts.size / 2
            // Signed area in screen coords (Y-down) :
            //   > 0 = visually CW polygon, < 0 = visually CCW.
            // For an "inside = positive signed dist" convention, we need
            // cross > 0 inside. In screen coords with a CW polygon, the
            // natural cross product is NEGATIVE inside, so we flip with
            // orient = -1 when area > 0 (CW). Verified empirically with
            // a square (10,10)-(30,30) and interior point (20,20).
            var area2 = 0.0
            for (i in 0 until n) {
                val j = if (i + 1 == n) 0 else i + 1
                area2 += devVerts[i * 2].toDouble() * devVerts[j * 2 + 1] -
                         devVerts[j * 2].toDouble() * devVerts[i * 2 + 1]
            }
            val orient = if (area2 >= 0.0) -1f else 1f

            for (i in 0 until n) {
                val j = if (i + 1 == n) 0 else i + 1
                val x0 = devVerts[i * 2]; val y0 = devVerts[i * 2 + 1]
                val x1 = devVerts[j * 2]; val y1 = devVerts[j * 2 + 1]
                val dx = x1 - x0; val dy = y1 - y0
                val len = kotlin.math.sqrt(dx * dx + dy * dy)
                if (len < 1e-6f) {
                    // Degenerate edge -- emit a far-positive "always inside"
                    // equation so it doesn't drag down min coverage.
                    out[i * 4] = 0f
                    out[i * 4 + 1] = 0f
                    out[i * 4 + 2] = 1e9f
                    out[i * 4 + 3] = 0f
                } else {
                    val invLen = 1f / len
                    val a = orient * dy * invLen
                    val b = orient * -dx * invLen
                    val c = -(a * x0 + b * y0)
                    out[i * 4] = a
                    out[i * 4 + 1] = b
                    out[i * 4 + 2] = c
                    out[i * 4 + 3] = 0f
                }
            }
        }

        // ─── G3.3b.1 Bezier flattening (mirrors SkBitmapDevice constants) ───

        /** Chord-error tolerance in device-space pixels. Same as raster. */
        const val PATH_FLATNESS: Float = 0.25f
        /** Squared tolerance — comparisons avoid `sqrt`. */
        const val PATH_FLATNESS_SQ: Float = PATH_FLATNESS * PATH_FLATNESS
        /** Adaptive subdivision depth bound (safety net ; 4–6 levels typically suffice). */
        const val PATH_MAX_DEPTH: Int = 18
        /** Uniform parametric steps for conic flattening. */
        const val CONIC_STEPS: Int = 32

        /**
         * Recursive De Casteljau subdivision of a quadratic Bezier into
         * the `out` vertex list. Each non-flat segment is split at
         * `t = 0.5` ; flat segments append only their endpoint
         * `(x2, y2)` since the start `(x0, y0)` is already the last
         * vertex emitted to `out`.
         */
        fun flattenQuadInto(
            out: ArrayList<Float>,
            x0: Float, y0: Float,
            x1: Float, y1: Float,
            x2: Float, y2: Float,
            depth: Int,
        ) {
            if (depth >= PATH_MAX_DEPTH || quadIsFlat(x0, y0, x1, y1, x2, y2)) {
                out.add(x2); out.add(y2)
                return
            }
            val m01x = (x0 + x1) * 0.5f; val m01y = (y0 + y1) * 0.5f
            val m12x = (x1 + x2) * 0.5f; val m12y = (y1 + y2) * 0.5f
            val mx = (m01x + m12x) * 0.5f; val my = (m01y + m12y) * 0.5f
            flattenQuadInto(out, x0, y0, m01x, m01y, mx, my, depth + 1)
            flattenQuadInto(out, mx, my, m12x, m12y, x2, y2, depth + 1)
        }

        private fun quadIsFlat(
            x0: Float, y0: Float, x1: Float, y1: Float, x2: Float, y2: Float,
        ): Boolean {
            val dx = x2 - x0; val dy = y2 - y0
            val chord2 = dx * dx + dy * dy
            if (chord2 < 1e-12f) return true
            val cross = (x1 - x0) * dy - (y1 - y0) * dx
            return (cross * cross) <= PATH_FLATNESS_SQ * chord2
        }

        /**
         * Recursive De Casteljau subdivision of a cubic Bezier into
         * the `out` vertex list. Same pattern as [flattenQuadInto].
         */
        fun flattenCubicInto(
            out: ArrayList<Float>,
            x0: Float, y0: Float,
            x1: Float, y1: Float,
            x2: Float, y2: Float,
            x3: Float, y3: Float,
            depth: Int,
        ) {
            if (depth >= PATH_MAX_DEPTH || cubicIsFlat(x0, y0, x1, y1, x2, y2, x3, y3)) {
                out.add(x3); out.add(y3)
                return
            }
            val m01x = (x0 + x1) * 0.5f; val m01y = (y0 + y1) * 0.5f
            val m12x = (x1 + x2) * 0.5f; val m12y = (y1 + y2) * 0.5f
            val m23x = (x2 + x3) * 0.5f; val m23y = (y2 + y3) * 0.5f
            val m012x = (m01x + m12x) * 0.5f; val m012y = (m01y + m12y) * 0.5f
            val m123x = (m12x + m23x) * 0.5f; val m123y = (m12y + m23y) * 0.5f
            val mx = (m012x + m123x) * 0.5f; val my = (m012y + m123y) * 0.5f
            flattenCubicInto(out, x0, y0, m01x, m01y, m012x, m012y, mx, my, depth + 1)
            flattenCubicInto(out, mx, my, m123x, m123y, m23x, m23y, x3, y3, depth + 1)
        }

        private fun cubicIsFlat(
            x0: Float, y0: Float, x1: Float, y1: Float,
            x2: Float, y2: Float, x3: Float, y3: Float,
        ): Boolean {
            val dx = x3 - x0; val dy = y3 - y0
            val chord2 = dx * dx + dy * dy
            if (chord2 < 1e-12f) return true
            val c1 = (x1 - x0) * dy - (y1 - y0) * dx
            val c2 = (x2 - x0) * dy - (y2 - y0) * dx
            val maxCross2 = maxOf(c1 * c1, c2 * c2)
            return maxCross2 <= PATH_FLATNESS_SQ * chord2
        }

        /**
         * Conic flattening via uniform parametric stepping. Matches
         * [org.skia.core.SkBitmapDevice]'s conic flattener : 32 steps
         * keep visible chord error well below 0.25 px at GM scale.
         */
        fun flattenConicInto(
            out: ArrayList<Float>,
            x0: Float, y0: Float,
            x1: Float, y1: Float,
            x2: Float, y2: Float,
            w: Float,
        ) {
            val n = CONIC_STEPS
            for (k in 1..n) {
                val t = k.toFloat() / n
                val u = 1f - t
                val numW = u * u + 2f * u * t * w + t * t
                val numX = u * u * x0 + 2f * u * t * w * x1 + t * t * x2
                val numY = u * u * y0 + 2f * u * t * w * y1 + t * t * y2
                out.add(numX / numW); out.add(numY / numW)
            }
        }

        /**
         * Match [SkBitmapDevice]'s non-AA pixel-edge rule:
         * `floor(coord + 0.5)`. Ties round toward +inf — equivalent to
         * Skia's `SkScalarRoundToInt`. Keeping this in lockstep with
         * SkBitmapDevice means a non-AA fill on the GPU lands on the
         * same integer pixels as on the raster device.
         */
        fun pixelEdge(c: Float): Int = floor(c.toDouble() + 0.5).toInt()
    }
}
