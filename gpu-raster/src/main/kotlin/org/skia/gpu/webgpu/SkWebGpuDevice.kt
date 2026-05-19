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
import org.skia.core.SkClipShape
import org.skia.core.SkDevice
import org.skia.core.SrcRectConstraint
import org.skia.foundation.SkBitmapShader
import org.skia.foundation.SkBlendMode
import org.skia.foundation.SkDashPathEffect
import org.skia.foundation.SkPathEffect
import org.skia.foundation.asBlendModeFilter
import org.skia.foundation.asBlurImageFilter
import org.skia.foundation.asColorFilterImageFilter
import org.skia.foundation.asComposeImageFilter
import org.skia.foundation.asDropShadowImageFilter
import org.skia.foundation.asOffsetImageFilter
import org.skia.foundation.asMatrixFilter
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
import org.skia.foundation.SkBlurMaskFilter
import org.skia.foundation.SkBlurStyle
import kotlin.math.ceil
import kotlin.math.exp
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
        /**
         * G2.x -- analytical clip-shape payload. When [clipKind] is 0
         * (`CLIP_KIND_NONE`), [clipShapeBounds] / [clipShapeRx] /
         * [clipShapeRy] are ignored and the shader behaves exactly as
         * before (rect coverage only). When [clipKind] is 1
         * (`CLIP_KIND_RRECT`), the fragment shader multiplies the rect
         * coverage by `rrect_cov(pos, clipShapeBounds, clipShapeRx,
         * clipShapeRy)` -- pixels outside the clip shape go to 0,
         * inside to 1, with a smooth half-pixel band on the boundary.
         */
        val clipKind: Float = CLIP_KIND_NONE,
        val clipShapeBounds: FloatArray = FloatArray(4),
        val clipShapeRx: Float = 0f,
        val clipShapeRy: Float = 0f,
        /**
         * Phase G-direct-colorFilter -- optional packed `SkColorFilter`
         * payload (6 vec4f = 24 floats) consumed by `solid_color.wgsl`'s
         * `apply_color_filter` helper. Built by [packLayerCompositeColorFilter]
         * (shared with the layer-composite shader -- same layout). When the
         * paint carries no colour filter, this is an all-zero 24-element
         * array : `colorFilterKindMode.x == 0` makes the shader's filter
         * branch dead and the output is byte-identical to the pre-slice
         * path. Same fallback for unsupported variants (Compose / Lerp /
         * Table / sRGB-gamma / working-CS wrapper).
         */
        val colorFilterPacked: FloatArray = ZERO_COLOR_FILTER_24,
    ) : PendingDraw {
        // Manual equals / hashCode to handle FloatArray equality
        // properly -- the per-element comparison is what the cross-
        // backend RectFillCrossTest depends on for deterministic
        // dispatch keying. Data-class default would compare array
        // references (i.e. always !=).
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is RectDraw) return false
            if (x != other.x || y != other.y || w != other.w || h != other.h) return false
            if (ol != other.ol || ot != other.ot || or != other.or || ob != other.ob) return false
            if (il != other.il || it != other.it || ir != other.ir || ib != other.ib) return false
            if (r != other.r || g != other.g || b != other.b || a != other.a) return false
            if (mode != other.mode) return false
            if (clipKind != other.clipKind) return false
            if (!clipShapeBounds.contentEquals(other.clipShapeBounds)) return false
            if (clipShapeRx != other.clipShapeRx) return false
            if (clipShapeRy != other.clipShapeRy) return false
            if (!colorFilterPacked.contentEquals(other.colorFilterPacked)) return false
            return true
        }

        override fun hashCode(): Int {
            var result = x
            result = 31 * result + y
            result = 31 * result + w
            result = 31 * result + h
            result = 31 * result + mode.hashCode()
            result = 31 * result + clipKind.hashCode()
            return result
        }
    }

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
        /**
         * G2.x (closing slice) -- analytical clip shape captured at
         * drawPath time. `null` / [SkClipShape.Rect] -> sentinel zeros
         * in the uniform, shader skips the `rrect_cov` modulation.
         */
        val clipShape: SkClipShape? = null,
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
        /**
         * G2.x (closing slice) -- analytical clip shape carried into the
         * cover pass uniform (the stencil pass shares the same buffer
         * but its colour writes are masked off, so the slot is inert).
         */
        val clipShape: SkClipShape? = null,
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
        /**
         * G2.x (closing slice) -- analytical clip shape carried into the
         * AA cover pass uniform. Both fs_inside and fs_outside multiply
         * their polygon coverage by `clip_cov(frag.xy)` so the analytical
         * clip's boundary band intersects with the polygon's AA falloff.
         */
        val clipShape: SkClipShape? = null,
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
        /**
         * G2.x (closing slice) -- analytical clip shape carried into the
         * AA polygon uniform. Fragment shader multiplies its per-edge
         * coverage by `clip_cov(frag.xy)` to honour the analytical clip.
         */
        val clipShape: SkClipShape? = null,
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
        /**
         * G2.x -- analytical clip-shape captured from the canvas's clip
         * stack at draw-record time. `null` means "no shape clip" (the
         * integer scissor is the only clip) ; the shader collapses the
         * `rrect_cov` modulation to 1.0 in that case. Curved shapes
         * (circle / oval / uniform-corner rrect) are passed through to
         * `rrect_cov` as a uniform-radii rrect.
         */
        val clipShape: SkClipShape? = null,
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
        /** G2.x -- see [LinearGradientRectDraw.clipShape]. */
        val clipShape: SkClipShape? = null,
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
        /** G2.x -- see [LinearGradientRectDraw.clipShape]. */
        val clipShape: SkClipShape? = null,
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
        /** G2.x -- see [LinearGradientRectDraw.clipShape]. */
        val clipShape: SkClipShape? = null,
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
        /** G2.x -- see [LinearGradientRectDraw.clipShape]. */
        val clipShape: SkClipShape? = null,
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
        /** G2.x -- see [LinearGradientRectDraw.clipShape]. */
        val clipShape: SkClipShape? = null,
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
        /** G2.x -- see [LinearGradientRectDraw.clipShape]. */
        val clipShape: SkClipShape? = null,
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
        /** G2.x -- see [LinearGradientRectDraw.clipShape]. */
        val clipShape: SkClipShape? = null,
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
        /** G2.x -- see [LinearGradientRectDraw.clipShape]. */
        val clipShape: SkClipShape? = null,
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
        /** G2.x -- see [LinearGradientRectDraw.clipShape]. */
        val clipShape: SkClipShape? = null,
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
        /** G2.x -- see [LinearGradientRectDraw.clipShape]. */
        val clipShape: SkClipShape? = null,
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
     * G5.3 / G5.3.x -- [csMode] selects the shader's colorspace-transform
     * branch (`0 = identity / sRGB fast path`, `1 = sRGB EOTF -> matrix
     * -> sRGB OETF`, `2 = parametric sRGBish EOTF -> matrix -> sRGB
     * OETF`). [csMatrix] is the column-major 3x3 primaries matrix from
     * source-linear to sRGB-linear ; for `csMode == 0` the host passes
     * the identity so the multiply is a no-op even if the shader were
     * to take the transform branch. [csTfParams] is the 7-float
     * parametric TF (`(g, a, b, c, d, e, f)`, matching
     * `SkcmsTransferFunction`) consumed by `csMode == 2` ; mode 0 / 1
     * still get a deterministic identity payload so the std140 slot
     * has stable bytes. The triple is computed once per image via
     * [bitmapColorSpaceFor].
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
        val csTfParams: FloatArray,
        override val r: Float, override val g: Float, override val b: Float, override val a: Float,
        override val mode: SkBlendMode,
        /**
         * G2.x -- analytical clip-shape payload, mirrors [RectDraw]. When
         * [clipKind] is [CLIP_KIND_NONE], the slots are ignored and the
         * shader behaves exactly as before (integer scissor is the only
         * clip). When [clipKind] is [CLIP_KIND_RRECT], the fragment
         * shader multiplies the premul output by `rrect_cov(pos,
         * clipShapeBounds, clipShapeRx, clipShapeRy)`.
         */
        val clipKind: Float = CLIP_KIND_NONE,
        val clipShapeBounds: FloatArray = FloatArray(4),
        val clipShapeRx: Float = 0f,
        val clipShapeRy: Float = 0f,
        /**
         * G5.2.2 -- 2x3 device-to-image affine `M^-1 = (ctm * localMatrix)^-1`.
         * Row 0 = `(sx, kx, tx)` ; row 1 = `(ky, sy, ty)`. The fragment shader
         * uses this directly to derive image-pixel coords from the fragment
         * center, replacing the legacy `srcRect/dstRect` rect-affine.
         *
         * For [drawImageRect] callers (no shader rotation) the affine is
         * built from the axis-aligned src/dst ratio so the byte output stays
         * unchanged ; for [SkBitmapShader] callers the affine is the inverse
         * of `ctm * localMatrix`.
         */
        val devToImageRow0: FloatArray,
        val devToImageRow1: FloatArray,
    ) : PendingDraw

    // ─── Layer composite (Phase G-saveLayer-fast) ───────────────────────
    /**
     * Phase G-saveLayer-fast -- direct GPU-to-GPU layer composite. The
     * source is a sibling [SkWebGpuDevice]'s [intermediateTexture] (the
     * child layer's pending draws have already been flushed onto it via
     * [flushDrawsOnly] before this pending draw is enqueued). The
     * fragment stage uses `textureLoad` with integer pixel coords --
     * 1:1 layer-pixel / parent-device-pixel grid alignment, no filter,
     * no sampler.
     *
     * Replaces the scaffolding flush+readback+re-upload+sample
     * round-trip in [compositeFrom] with a single fullscreen-quad
     * render pass on the parent's intermediate. Blend mode is honoured
     * via the pipeline's blend state (matches the natively-blendable
     * subset : kClear / kSrc / kSrcOver / kDstOver, same gate as the
     * scaffolding's [enqueueImageRectDrawInternal]).
     *
     * Inherited `r/g/b/a` are placeholders (fragment shader sources
     * colour from the layer texture) ; kept for the [PendingDraw]
     * interface contract.
     */
    private data class LayerCompositeDraw(
        val layerView: GPUTextureView,
        val layerWidth: Int, val layerHeight: Int,
        val dstOriginX: Int, val dstOriginY: Int,
        val scissor: IntArray,
        val paintR: Float, val paintG: Float, val paintB: Float, val paintA: Float,
        override val r: Float, override val g: Float, override val b: Float, override val a: Float,
        override val mode: SkBlendMode,
        /**
         * Phase G-saveLayer-colorFilter -- packed colorFilter payload.
         * Layout (matches `Uniforms` in `layer_composite.wgsl`) :
         *
         *   colorFilterKindMode (vec4f) : (kind, blendMode, _, _)
         *     kind : 0 = none, 1 = SkBlendModeFilter, 2 = SkMatrixFilter.
         *     blendMode : SkBlendMode ordinal (used only when kind == 1).
         *   colorFilterParam0 (vec4f) :
         *     kind == 1 -> constant src colour (premul RGBA).
         *     kind == 2 -> matrix row 0 (R out coefs : rR, rG, rB, rA).
         *   colorFilterParam1 (vec4f) : matrix row 1 (G out coefs).
         *   colorFilterParam2 (vec4f) : matrix row 2 (B out coefs).
         *   colorFilterParam3 (vec4f) : matrix row 3 (A out coefs).
         *   colorFilterBias   (vec4f) : per-row bias (R, G, B, A).
         *
         * The whole payload is `null` when the layer paint has no
         * colour filter (fast path -- the shader's `kind == 0` branch
         * is a no-op and the uniform still ships zeros for the params
         * so the bytes are deterministic).
         */
        val colorFilterPacked: FloatArray,
    ) : PendingDraw

    // ─── MaskFilter Gaussian blur (Phase MaskFilter-blur) ───────────────
    /**
     * Phase MaskFilter-blur -- pending entry for `paint.maskFilter is
     * SkBlurMaskFilter(kNormal)` on `drawPath` / `drawRect`. The shape
     * is rasterised onto a child [SkWebGpuDevice]'s intermediate texture
     * (the "shape mask") by the dispatch gate before this draw is
     * enqueued ; the gate also allocates a per-draw scratch H-pass
     * texture sized to the shape mask. The flush replays this draw as
     * two render passes :
     *   1. Horizontal Gaussian blur : shape mask -> scratchH.
     *   2. Vertical Gaussian blur + paint colour modulation -> parent
     *      intermediate, blended via the pipeline's blend state.
     *
     * Both scratch textures (the child layer device's intermediate +
     * the host-allocated scratch H texture) outlive a single command
     * buffer because the V pass reads scratchH after the H pass wrote
     * it ; we close them in [closeDrawResources] after submission.
     *
     * The kernel weights are pre-computed CPU-side (symmetric half-
     * kernel, 33 floats max) and shipped via the uniform. The radius is
     * clamped to [MAX_BLUR_RADIUS] at the dispatch gate ; the kernel is
     * renormalised after the clamp so the centre pixel preserves its
     * full alpha.
     *
     * Inherited `r/g/b/a` are placeholders ; the fragment shader sources
     * colour from `paintColor` in the uniform.
     */
    private data class BlurredPathDraw(
        // Child layer device whose intermediate holds the shape mask.
        // Kept on the draw so [flush] can close it after submission --
        // the layer device owns the GPU resource and we must release
        // it once the parent's H pass has finished sampling.
        val shapeMaskDevice: SkWebGpuDevice,
        // Shape-mask source : the child device's intermediate view.
        val shapeMaskView: GPUTextureView,
        // Host-allocated scratch H-pass texture (closed after submit).
        val scratchHTexture: GPUTexture,
        val scratchHView: GPUTextureView,
        // Size of the shape mask (and of scratchH, they share dims).
        val srcWidth: Int, val srcHeight: Int,
        // Origin of the shape mask in parent-device pixel coords.
        val dstOriginX: Int, val dstOriginY: Int,
        // Integer scissor in parent-device pixels for the V pass.
        val scissor: IntArray,
        // Symmetric half-kernel : (1 + radius) floats, packed into 9
        // vec4f (36 floats, trailing zeroed).
        val kernel: FloatArray,
        val radius: Int,
        // Paint colour, premul.
        val paintR: Float, val paintG: Float, val paintB: Float, val paintA: Float,
        // SkBlurStyle ordinal (0 = kNormal, 1 = kSolid, 2 = kOuter,
        // 3 = kInner). Packed into the V-pass uniform's axisRadius.w
        // slot ; the V-pass fragment branches on this to combine the
        // blurred coverage B with the sharp shape-mask alpha M per
        // the formulas in [SkBlurMaskFilter].
        val blurStyleOrdinal: Int,
        // PendingDraw interface : r/g/b/a placeholders ; mode honoured
        // by the V-pass pipeline's blend state.
        override val r: Float, override val g: Float, override val b: Float, override val a: Float,
        override val mode: SkBlendMode,
    ) : PendingDraw

    // ─── MaskFilter Gaussian blur on shaded paints (Phase MaskFilter-blur-shaded) ─
    /**
     * Phase MaskFilter-blur-shaded -- pending entry for `paint.maskFilter
     * is SkBlurMaskFilter` on `drawPath` / `drawRect` when the paint
     * also carries a [SkShader] (linear / radial / sweep / conical
     * gradient, or bitmap shader). The path is rasterised onto a child
     * [SkWebGpuDevice]'s intermediate texture with the FULL paint
     * (shader + colourFilter), so the layer holds the final per-pixel
     * RGBA premul colours of the unblurred shape. The dispatch gate
     * also allocates a per-draw scratch H-pass texture sized to the
     * shaded layer. The flush replays this draw as two render passes :
     *   1. Horizontal Gaussian blur : shaded layer -> scratchH.
     *   2. Vertical Gaussian blur + style combine + composite :
     *      scratchH (-> blurred B) + shaded layer (-> A, M = A.a)
     *      combined per [SkBlurStyle] and blended onto the parent
     *      intermediate via the pipeline's blend state.
     *
     * The single shaded layer plays both roles (convolution source for
     * pass 1, sharp shape mask + SrcOver "src" for pass 2's kSolid
     * combine) -- M = A.a always since the child device started
     * cleared transparent and only the path's pixels carry alpha.
     *
     * Both scratch textures (the child layer device's intermediate +
     * the host-allocated scratch H texture) outlive a single command
     * buffer because the V pass reads scratchH after the H pass wrote
     * it ; we close them in [closeDrawResources] after submission.
     *
     * The kernel weights, radius cap, and uniform layout mirror
     * [BlurredPathDraw] exactly -- the host packs the same 192-byte
     * uniform, only `paintColor` ships as zeros since the shaded layer
     * already carries the final colour (no paint-colour fold in the
     * shader). The bind-group layout is shared with the solid-paint
     * variant so the two MaskFilter pipelines reuse a single
     * [blurBindGroupLayout].
     *
     * Style combine (V-pass, premul RGBA) :
     *   - kNormal : output = B
     *   - kSolid  : output = A + B * (1 - A.a)   (SrcOver A over B)
     *   - kOuter  : output = B * (1 - M)         (halo only)
     *   - kInner  : output = B * M               (clipped inside)
     *
     * Inherited `r/g/b/a` are placeholders ; the fragment shader sources
     * colour from the shaded layer texture.
     */
    private data class BlurredShadedPathDraw(
        // Child layer device whose intermediate holds the shaded shape
        // render. Kept on the draw so [flush] can close it after
        // submission -- the layer device owns the GPU resource and we
        // must release it once the parent's H pass has finished
        // sampling.
        val shadedLayerDevice: SkWebGpuDevice,
        // Shaded source : the child device's intermediate view.
        val shadedLayerView: GPUTextureView,
        // Host-allocated scratch H-pass texture (closed after submit).
        val scratchHTexture: GPUTexture,
        val scratchHView: GPUTextureView,
        // Size of the shaded layer (and of scratchH, they share dims).
        val srcWidth: Int, val srcHeight: Int,
        // Origin of the shaded layer in parent-device pixel coords.
        val dstOriginX: Int, val dstOriginY: Int,
        // Integer scissor in parent-device pixels for the V pass.
        val scissor: IntArray,
        // Symmetric half-kernel : (1 + radius) floats, packed into 9
        // vec4f (36 floats, trailing zeroed).
        val kernel: FloatArray,
        val radius: Int,
        // SkBlurStyle ordinal (0 = kNormal, 1 = kSolid, 2 = kOuter,
        // 3 = kInner). Packed into the V-pass uniform's axisRadius.w
        // slot ; the V-pass fragment branches on this to combine B
        // (blurred RGBA) with A (original shaded RGBA, M = A.a).
        val blurStyleOrdinal: Int,
        // PendingDraw interface : r/g/b/a placeholders ; mode honoured
        // by the V-pass pipeline's blend state.
        override val r: Float, override val g: Float, override val b: Float, override val a: Float,
        override val mode: SkBlendMode,
    ) : PendingDraw

    // ─── ImageFilter Gaussian blur on saveLayer (Phase G-saveLayer-imageFilter-blur) ─
    /**
     * Phase G-saveLayer-imageFilter-blur -- pending entry for the layer
     * composite path when `paint.imageFilter is SkImageFilters.Blur(
     * sigmaX, sigmaY, kClamp / kDecal, child = null)`. The child layer
     * device's intermediate texture has been drained by the dispatch
     * gate (via [SkWebGpuDevice.flushDrawsOnly]) and is ready to be
     * sampled. The flush replays this draw as three render passes :
     *   1. Horizontal Gaussian blur : layer texture -> scratchH.
     *   2. Vertical Gaussian blur   : scratchH      -> scratchV.
     *   3. Layer composite          : scratchV      -> parent
     *      intermediate (via the existing `layer_composite.wgsl`
     *      pipeline, so paintColor + colorFilter + blendMode all follow
     *      the no-filter saveLayer code path).
     *
     * The three scratch textures + the child layer device outlive a
     * single command buffer because passes 2 and 3 read pass 1's / 2's
     * output ; we close them in [closeDrawResources] after submission.
     *
     * Both blur scratches share the layer's `(w, h)` dimensions -- no
     * padding. Near-edge pixels lose part of their kernel mass on
     * kDecal (transparent border) ; on kClamp the shader extends the
     * edge texel out. Larger sigmas where the kernel meaningfully
     * spreads beyond the layer bounds would need a padded scratch ;
     * deferred to a follow-up slice (the current scope matches the
     * MaskFilter blur's "intrinsic layer bounds" assumption).
     *
     * The kernel weights and dispatch padding strategy mirror
     * [BlurredPathDraw] exactly -- the same [buildSymmetricGaussianHalfKernel]
     * helper is reused, the same [MAX_BLUR_RADIUS] cap applies.
     */
    private data class BlurredLayerCompositeDraw(
        // Child layer device's intermediate view (the layer pixels).
        // The device is owned by SkCanvas (mirrors [LayerCompositeDraw])
        // -- we sample it in pass 1 ; the canvas closes it after we
        // submit. The two scratch textures below are owned by this draw
        // and closed in [closeDrawResources].
        val layerView: GPUTextureView,
        val layerWidth: Int, val layerHeight: Int,
        // Host-allocated scratch H/V textures (closed after submit).
        val scratchHTexture: GPUTexture,
        val scratchHView: GPUTextureView,
        val scratchVTexture: GPUTexture,
        val scratchVView: GPUTextureView,
        // Origin of the layer in parent-device pixel coords (the final
        // composite step uses this to align scratchV onto the parent).
        val dstOriginX: Int, val dstOriginY: Int,
        // Integer scissor in parent-device pixels for the composite pass.
        val scissor: IntArray,
        // Symmetric half-kernels per axis. Each is (1 + radius) floats,
        // packed into 9 vec4f (36 floats, trailing zeroed). When sigmaX
        // and sigmaY differ, the two kernels and radii differ too.
        val kernelX: FloatArray, val radiusX: Int,
        val kernelY: FloatArray, val radiusY: Int,
        // Tile mode applied per-axis by the blur shader on out-of-source
        // samples (SkTileMode ordinal : 0 = kClamp, 3 = kDecal). Only
        // those two are supported -- the dispatch gate throws for
        // kRepeat / kMirror.
        val tileModeOrdinal: Int,
        // Layer composite payload : paint colour (premul scale) + packed
        // colour-filter (kind / params / bias). Routed through the
        // existing [layer_composite.wgsl] pipeline on pass 3, so the
        // pixel result matches the no-filter saveLayer path exactly
        // when the colour-filter slot is identity.
        val paintR: Float, val paintG: Float, val paintB: Float, val paintA: Float,
        val colorFilterPacked: FloatArray,
        // Phase G-saveLayer-imageFilter-compose -- optional pre-blur
        // ColorFilter pass. When non-null, the dispatch encodes a
        // 4-pass pipeline (preCF, H, V, composite) instead of 3 ; the
        // pre-CF pass reads the layer texture, applies the colour
        // filter via `layer_composite.wgsl` with kSrc blend, and writes
        // to [scratchPreCfTexture] (which then feeds the H pass instead
        // of the raw layer texture). The packed payload mirrors
        // [colorFilterPacked] : 24 floats (6 vec4f), `kind == 0` for
        // identity / no pre-CF.
        val preBlurColorFilterPacked: FloatArray?,
        val scratchPreCfTexture: GPUTexture?,
        val scratchPreCfView: GPUTextureView?,
        // PendingDraw interface : r/g/b/a placeholders ; mode honoured
        // by the composite-pass pipeline's blend state.
        override val r: Float, override val g: Float, override val b: Float, override val a: Float,
        override val mode: SkBlendMode,
    ) : PendingDraw

    // ─── DropShadow on saveLayer (Phase G-saveLayer-imageFilter-dropshadow) ─
    /**
     * Phase G-saveLayer-imageFilter-dropshadow -- pending entry for the
     * layer composite path when `paint.imageFilter is SkImageFilters
     * .DropShadow(dx, dy, sigmaX, sigmaY, color, input = null)`. The
     * child layer device's intermediate texture has been drained by the
     * dispatch gate (via [SkWebGpuDevice.flushDrawsOnly]) and is ready
     * to be sampled. The flush replays this draw as four render passes :
     *   1. H blur pass : layer texture -> scratchH (separable Gaussian
     *      along X). Same shader as the plain blur ImageFilter.
     *   2. V blur pass : scratchH -> scratchV (separable Gaussian along
     *      Y). The output is the layer texture's premul RGBA blurred ;
     *      the colorize-to-shadow step happens at the composite pass
     *      via the existing `Blend(shadowColor, kSrcIn)` colour filter
     *      slot, which the [layer_composite.wgsl] shader already
     *      implements (kind = 1, mode = kSrcIn).
     *   3. Composite (shadow) : scratchV -> parent intermediate at the
     *      shadow's offset origin `(originX + sdx, originY + sdy)`.
     *      `colorFilter` is packed as `Blend(shadowColor, kSrcIn)` --
     *      the shader masks out the layer's RGB and keeps only the
     *      blurred alpha tinted to the shadow colour, premul. Blends
     *      onto the parent via kSrcOver (gated at the dispatch site).
     *   4. Composite (original) : layer texture -> parent intermediate
     *      at the original origin `(originX, originY)`. Same code path
     *      as the plain no-filter saveLayer composite (paintColor +
     *      colour filter + kSrcOver). Draws the layer content ON TOP
     *      of the shadow ; in combination, that's "draw a shadow under
     *      the layer".
     *
     * The two scratch textures + the child layer device outlive a
     * single command buffer because passes 2..4 read passes 1..2's
     * output ; we close them in [closeDrawResources] after submission.
     *
     * Inherited `r/g/b/a` are placeholders ; the composite-pass blend
     * mode is kSrcOver (gated at the dispatch site) and the paint
     * colour modulation folds into the per-pass uniform.
     */
    private data class DropShadowLayerCompositeDraw(
        // Child layer device's intermediate view (the layer pixels).
        // Used by both the H blur pass (sampled) AND the original-pass
        // composite (sampled again). The device is owned by SkCanvas
        // (mirrors [BlurredLayerCompositeDraw]) -- we sample it, but
        // the canvas closes it after we submit. The two scratch
        // textures below are owned by this draw and closed in
        // [closeDrawResources].
        val layerView: GPUTextureView,
        val layerWidth: Int, val layerHeight: Int,
        // Host-allocated scratch H/V textures (closed after submit).
        val scratchHTexture: GPUTexture,
        val scratchHView: GPUTextureView,
        val scratchVTexture: GPUTexture,
        val scratchVView: GPUTextureView,
        // Origin of the layer in parent-device pixel coords (the
        // original-pass composite uses this).
        val dstOriginX: Int, val dstOriginY: Int,
        // Origin of the shadow in parent-device pixel coords -- the
        // layer origin shifted by the integer-rounded (dx, dy). The
        // shadow-pass composite uses this.
        val shadowDstOriginX: Int, val shadowDstOriginY: Int,
        // Integer scissor in parent-device pixels for the original-
        // pass composite.
        val originalScissor: IntArray,
        // Integer scissor in parent-device pixels for the shadow-pass
        // composite (intersected with the parent clip + viewport at
        // the shifted origin).
        val shadowScissor: IntArray,
        // Symmetric half-kernels per axis. Each is (1 + radius) floats,
        // packed into 9 vec4f (36 floats, trailing zeroed).
        val kernelX: FloatArray, val radiusX: Int,
        val kernelY: FloatArray, val radiusY: Int,
        // Tile mode for the blur's out-of-source samples. Only kDecal
        // (transparent border) is supported -- the shadow alpha falls
        // off to zero outside the layer, so the blur kernel sees a
        // decaled silhouette. kClamp would extend the edge alpha out
        // (and so the shadow would fill the entire kernel-spread band
        // outside the layer with the edge colour, not the upstream
        // SkDropShadow semantic).
        val tileModeOrdinal: Int,
        // Shadow colour-filter payload : packed as `Blend(shadowColor,
        // kSrcIn)` (kind = 1, mode = 5, param0 = premul shadow colour).
        // The composite shader masks the layer texel down to its alpha
        // multiplied by the shadow colour -- that's the colorize step.
        val shadowColorFilterPacked: FloatArray,
        // Original-pass paint payload : paint colour modulation +
        // colour-filter slot. Identical to the no-filter saveLayer
        // composite path. The shadow pass uses (1, 1, 1, 1) for its
        // paintColor slot since the shadow colour is folded into the
        // colour filter.
        val paintR: Float, val paintG: Float, val paintB: Float, val paintA: Float,
        val originalColorFilterPacked: FloatArray,
        // PendingDraw interface : r/g/b/a placeholders ; mode honoured
        // by both composite passes' blend state (gated to kSrcOver at
        // the dispatch site).
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
        val csTfParams: FloatArray,
        override val r: Float, override val g: Float, override val b: Float, override val a: Float,
        override val mode: SkBlendMode,
        /**
         * G2.x -- analytical clip-shape payload, mirrors [RectDraw] /
         * [ImageRectDraw]. Folded into the AA cover-pass coverage by
         * the fragment shader's `clipShapeCoverage()` helper.
         */
        val clipKind: Float = CLIP_KIND_NONE,
        val clipShapeBounds: FloatArray = FloatArray(4),
        val clipShapeRx: Float = 0f,
        val clipShapeRy: Float = 0f,
        /**
         * G5.2.2 -- 2x3 device-to-image affine, mirror of [ImageRectDraw].
         * Replaces the legacy srcRect/dstRect rect-affine in the fragment
         * math so rotated / skewed CTM + localMatrix combinations route
         * through the same pipeline as the axis-aligned case.
         */
        val devToImageRow0: FloatArray = FloatArray(3),
        val devToImageRow1: FloatArray = FloatArray(3),
    ) : PendingDraw

    /**
     * G-suivi (round 17 follow-up) -- returns `true` when this pending
     * draw would build a zero-size GPU vertex buffer, which makes
     * `wgpu::setVertexBuffer` panic with "invalid size". Fan
     * tessellation of multi-contour paths whose individual contours each
     * carry fewer than 3 vertices (e.g. the stroker output of
     * `FLT_EPSILON` / zero-extent rects in `StrokeRectGM`) emits an
     * empty stencil-vertex array. The dispatch loop drops these draws
     * silently — no fragments to render, same observable result as
     * upstream Skia on degenerate paths.
     *
     * `coverVerts` is currently never empty for any caller
     * (`bboxTrianglesFor` / `viewportTrianglesFor` always return 12
     * floats), but the guard checks it too for future-proofing : any
     * new producer that emits empty cover triangles will also be
     * skipped cleanly instead of crashing the test runner.
     *
     * Non-stencil-cover variants ([PolygonDraw], [AaPolygonDraw]) keep
     * their existing producer-side `n < 3` early returns in
     * [drawPath] — they would also hit this filter as a safety net.
     */
    private fun PendingDraw.producesEmptyVertexBuffer(): Boolean = when (this) {
        is PolygonDraw -> verts.isEmpty()
        is AaPolygonDraw -> verts.isEmpty()
        is StencilCoverPolygonDraw -> stencilVerts.isEmpty() || coverVerts.isEmpty()
        is StencilCoverAaPolygonDraw -> stencilVerts.isEmpty() || coverVerts.isEmpty()
        is StencilCoverAaGradientDraw -> stencilVerts.isEmpty() || coverVerts.isEmpty()
        is StencilCoverAaRadialGradientDraw -> stencilVerts.isEmpty() || coverVerts.isEmpty()
        is StencilCoverAaSweepGradientDraw -> stencilVerts.isEmpty() || coverVerts.isEmpty()
        is StencilCoverAaConicalGradientDraw -> stencilVerts.isEmpty() || coverVerts.isEmpty()
        is StencilCoverAaConicalFocalGradientDraw -> stencilVerts.isEmpty() || coverVerts.isEmpty()
        is StencilCoverAaBitmapShaderDraw -> stencilVerts.isEmpty() || coverVerts.isEmpty()
        // Rect / gradient-rect / image-rect / layer-composite draws use
        // a full-screen-triangle in the vertex shader (no vertex buffer
        // bound), so they never hit the panic path.
        is RectDraw,
        is LinearGradientRectDraw,
        is RadialGradientRectDraw,
        is SweepGradientRectDraw,
        is ConicalGradientRectDraw,
        is ConicalFocalGradientRectDraw,
        is ConicalStripGradientRectDraw,
        is ImageRectDraw,
        is LayerCompositeDraw,
        is BlurredPathDraw,
        is BlurredShadedPathDraw,
        is BlurredLayerCompositeDraw,
        is DropShadowLayerCompositeDraw -> false
    }

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
        val csTfParams: FloatArray,
        // G5.2.2 -- 2x3 device-to-image affine `M^-1 = (ctm * localMatrix)^-1`.
        // Row 0 = `(sx, kx, tx)` ; row 1 = `(ky, sy, ty)`. The shader applies
        // this directly to the fragment center to derive image-pixel coords,
        // unifying axis-aligned and rotated / skewed paths.
        val devToImageRow0: FloatArray,
        val devToImageRow1: FloatArray,
    )

    private fun buildBitmapShaderPayload(
        shader: SkBitmapShader,
        ctm: SkMatrix,
        paint: SkPaint,
    ): BitmapShaderPayload? {
        val image = shader.getImage()
        if (image.width <= 0 || image.height <= 0) return null
        // G5.2.2 -- arbitrary affine CTM + localMatrix. Compose
        // `M = ctm * localMatrix` (the shader-local -> device affine)
        // and invert it once to derive the device -> image-pixel map
        // that the shader applies per fragment. Bail honestly when the
        // combined affine is singular (degenerate scale / perspective) ;
        // the caller falls through to the solid-colour fill.
        val combined = ctm.preConcat(shader.localMatrix)
        if (combined.hasPerspective()) return null
        val inv = combined.invert() ?: return null
        // Diagnostic `srcRect / dstRect` pair -- the device-AABB of the
        // image footprint under `M`. The fragment shader ignores these
        // (it reads the affine instead) but the host still uses
        // `dst{L,T,R,B}` to size the AA stencil-cover cover quad when
        // the path bbox is wider than the image footprint (defensive --
        // historically tests expected the cover quad to span the full
        // device frame, which still works with the affine path).
        val (x0, y0) = combined.mapXY(0f, 0f)
        val (x1, y1) = combined.mapXY(image.width.toFloat(), 0f)
        val (x2, y2) = combined.mapXY(0f, image.height.toFloat())
        val (x3, y3) = combined.mapXY(image.width.toFloat(), image.height.toFloat())
        val dstL = minOf(x0, x1, x2, x3); val dstT = minOf(y0, y1, y2, y3)
        val dstR = maxOf(x0, x1, x2, x3); val dstB = maxOf(y0, y1, y2, y3)
        val texture = imageTextureFor(image)
        val (csMode, csMatrix, csTfParams) = bitmapColorSpaceFor(image)
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
            csTfParams = csTfParams,
            devToImageRow0 = floatArrayOf(inv.sx, inv.kx, inv.tx),
            devToImageRow1 = floatArrayOf(inv.ky, inv.sy, inv.ty),
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
        clipKind: Float = CLIP_KIND_NONE,
        clipShapeBounds: FloatArray = ZERO_RECT4,
        clipShapeRx: Float = 0f,
        clipShapeRy: Float = 0f,
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
        csTfParams = csTfParams,
        r = 1f, g = 1f, b = 1f, a = paintA,
        mode = mode,
        clipKind = clipKind,
        clipShapeBounds = clipShapeBounds,
        clipShapeRx = clipShapeRx,
        clipShapeRy = clipShapeRy,
        devToImageRow0 = devToImageRow0,
        devToImageRow1 = devToImageRow1,
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

    // ─── Layer composite (Phase G-saveLayer-fast) ────────────────────────
    /**
     * Phase G-saveLayer-fast -- direct GPU-to-GPU composite shader. A
     * minimal fullscreen-quad pipeline that samples a child
     * [SkWebGpuDevice]'s intermediate texture via `textureLoad` and
     * writes onto this device's intermediate, replacing the scaffolding
     * flush+readback+re-upload round-trip in [compositeFrom].
     */
    private val layerCompositeShader: GPUShaderModule = loadShader("shaders/layer_composite.wgsl")

    /**
     * Phase G-saveLayer-fast -- bind group layout :
     *   binding 0 -- uniform buffer (dstOrigin, size, paintColor)
     *   binding 1 -- the child device's intermediate texture (sampleType
     *                UnfilterableFloat so the same layout works for both
     *                `RGBA16Float` and `RGBA8Unorm` intermediate formats ;
     *                the shader uses `textureLoad`, not `textureSample`,
     *                so no sampler is needed).
     */
    private val layerCompositeBindGroupLayout: GPUBindGroupLayout =
        context.device.createBindGroupLayout(
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
                            sampleType = GPUTextureSampleType.UnfilterableFloat,
                            viewDimension = GPUTextureViewDimension.TwoD,
                            multisampled = false,
                        ),
                    ),
                ),
            ),
        )

    private val layerCompositePipelineLayout = context.device.createPipelineLayout(
        PipelineLayoutDescriptor(bindGroupLayouts = listOf(layerCompositeBindGroupLayout)),
    )

    /**
     * Phase G-saveLayer-fast -- pipeline cache keyed by [SkBlendMode].
     * Small set (4 modes : kClear / kSrc / kSrcOver / kDstOver -- the
     * natively-blendable subset that [blendStateFor] expresses without
     * a fragment-side round-trip). Other modes error out at the
     * dispatch gate, matching the scaffolding's gate via
     * [enqueueImageRectDrawInternal].
     */
    private val layerCompositePipelineCache: MutableMap<SkBlendMode, GPURenderPipeline> =
        mutableMapOf()

    private fun layerCompositePipelineFor(mode: SkBlendMode): GPURenderPipeline =
        layerCompositePipelineCache.getOrPut(mode) {
            context.device.createRenderPipeline(
                RenderPipelineDescriptor(
                    layout = layerCompositePipelineLayout,
                    vertex = VertexState(module = layerCompositeShader, entryPoint = "vs_main"),
                    fragment = FragmentState(
                        module = layerCompositeShader,
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

    // ─── MaskFilter Gaussian blur pipelines (Phase MaskFilter-blur) ────
    /**
     * Phase MaskFilter-blur -- separable Gaussian blur shader. Two
     * entry points (`fs_horizontal`, `fs_vertical_composite`). The H
     * pass writes to a host-allocated scratch texture of the same size
     * as the shape mask ; the V pass writes onto this device's
     * intermediate, modulated by the per-draw paint colour and blended
     * via the pipeline's blend state.
     */
    private val blurGaussianShader: GPUShaderModule = loadShader("shaders/blur_gaussian.wgsl")

    /**
     * Phase MaskFilter-blur -- bind group layout, shared by the H and V
     * passes. One uniform buffer + two sampled textures (no sampler --
     * both shader entries use `textureLoad`). Binding 1 is the
     * convolution source : the shape mask for the H pass, the H-pass
     * scratch for the V pass. Binding 2 is the original (sharp) shape
     * mask, consumed by the V pass for the kSolid / kOuter / kInner
     * style formulas ; the H pass ignores it but the binding has to
     * exist because the layout is shared.
     */
    private val blurBindGroupLayout: GPUBindGroupLayout =
        context.device.createBindGroupLayout(
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
                            sampleType = GPUTextureSampleType.UnfilterableFloat,
                            viewDimension = GPUTextureViewDimension.TwoD,
                            multisampled = false,
                        ),
                    ),
                    BindGroupLayoutEntry(
                        binding = 2u,
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

    private val blurPipelineLayout = context.device.createPipelineLayout(
        PipelineLayoutDescriptor(bindGroupLayouts = listOf(blurBindGroupLayout)),
    )

    /**
     * Phase MaskFilter-blur -- H pass pipeline. Single pipeline (no
     * blend mode key) -- the H pass writes to a scratch texture cleared
     * to zero at the start of its render pass, so blending is moot ;
     * we use `SkBlendMode.kSrc`-equivalent (no blend state). The
     * pipeline targets the same [intermediateFormat] as the parent
     * intermediate, so the scratch texture inherits the same precision.
     */
    private val blurHorizontalPipeline: GPURenderPipeline =
        context.device.createRenderPipeline(
            RenderPipelineDescriptor(
                layout = blurPipelineLayout,
                vertex = VertexState(module = blurGaussianShader, entryPoint = "vs_main"),
                fragment = FragmentState(
                    module = blurGaussianShader,
                    entryPoint = "fs_horizontal",
                    targets = listOf(
                        ColorTargetState(
                            format = intermediateFormat,
                            // No blend : H pass clears its scratch then
                            // writes the convolved RGBA in one pass.
                            blend = null,
                        ),
                    ),
                ),
            ),
        )

    /**
     * Phase MaskFilter-blur -- V pass + composite pipeline, keyed by
     * [SkBlendMode] (same natively-blendable subset as the layer
     * composite : kClear / kSrc / kSrcOver / kDstOver). The V pass
     * samples the H scratch, modulates the alpha by the per-draw
     * paint colour, and blends onto the parent intermediate.
     */
    private val blurVerticalPipelineCache: MutableMap<SkBlendMode, GPURenderPipeline> =
        mutableMapOf()

    private fun blurVerticalPipelineFor(mode: SkBlendMode): GPURenderPipeline =
        blurVerticalPipelineCache.getOrPut(mode) {
            context.device.createRenderPipeline(
                RenderPipelineDescriptor(
                    layout = blurPipelineLayout,
                    vertex = VertexState(module = blurGaussianShader, entryPoint = "vs_main"),
                    fragment = FragmentState(
                        module = blurGaussianShader,
                        entryPoint = "fs_vertical_composite",
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

    // ─── MaskFilter Gaussian blur on shaded paints (Phase MaskFilter-blur-shaded) ─
    /**
     * Phase MaskFilter-blur-shaded -- separable Gaussian blur shader on
     * a SHADED layer. Two entry points (`fs_horizontal`,
     * `fs_vertical_composite`) ; the H pass writes pure blurred RGBA
     * into a scratch texture and the V pass combines B (blurred) with
     * A (original shaded layer, M = A.a) per [SkBlurStyle], then blends
     * onto the parent intermediate. Shares [blurBindGroupLayout] with
     * the solid-paint variant -- the binding shapes are identical (one
     * uniform + two sampled textures).
     */
    private val blurMaskFilterShadedShader: GPUShaderModule =
        loadShader("shaders/blur_mask_filter_shaded.wgsl")

    /**
     * Phase MaskFilter-blur-shaded -- H pass pipeline. Same shape as
     * [blurHorizontalPipeline] (no blend ; writes a freshly-cleared
     * scratch). The H entry samples the shaded layer and writes pure
     * RGBA along the X axis ; the V pass owns the style combine and
     * the final composite onto the parent.
     */
    private val blurShadedHorizontalPipeline: GPURenderPipeline =
        context.device.createRenderPipeline(
            RenderPipelineDescriptor(
                layout = blurPipelineLayout,
                vertex = VertexState(module = blurMaskFilterShadedShader, entryPoint = "vs_main"),
                fragment = FragmentState(
                    module = blurMaskFilterShadedShader,
                    entryPoint = "fs_horizontal",
                    targets = listOf(
                        ColorTargetState(
                            format = intermediateFormat,
                            blend = null,
                        ),
                    ),
                ),
            ),
        )

    /**
     * Phase MaskFilter-blur-shaded -- V pass + composite pipeline, keyed
     * by [SkBlendMode] (same natively-blendable subset as the solid
     * variant). The V pass samples the H scratch, combines with the
     * shaded layer per style, and blends onto the parent.
     */
    private val blurShadedVerticalPipelineCache: MutableMap<SkBlendMode, GPURenderPipeline> =
        mutableMapOf()

    private fun blurShadedVerticalPipelineFor(mode: SkBlendMode): GPURenderPipeline =
        blurShadedVerticalPipelineCache.getOrPut(mode) {
            context.device.createRenderPipeline(
                RenderPipelineDescriptor(
                    layout = blurPipelineLayout,
                    vertex = VertexState(module = blurMaskFilterShadedShader, entryPoint = "vs_main"),
                    fragment = FragmentState(
                        module = blurMaskFilterShadedShader,
                        entryPoint = "fs_vertical_composite",
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

    // ─── ImageFilter Gaussian blur pipelines (Phase G-saveLayer-imageFilter-blur) ─
    /**
     * Phase G-saveLayer-imageFilter-blur -- separable Gaussian blur
     * shader, fork of [blurGaussianShader]. Two entry points
     * (`fs_horizontal`, `fs_vertical`) -- both write a pure blurred
     * RGBA premul into a scratch texture, no paint-colour fold. The
     * final composite onto the parent runs through
     * [layerCompositePipelineFor] in a third render pass, so paint
     * alpha + colour filter follow the same code path as the no-filter
     * saveLayer.
     */
    private val blurImageFilterShader: GPUShaderModule =
        loadShader("shaders/blur_image_filter.wgsl")

    /**
     * Phase G-saveLayer-imageFilter-blur -- bind group layout shared by
     * the H and V passes. Shape matches [blurBindGroupLayout] (uniform
     * + sampled texture, no sampler) but kept distinct so the two blur
     * variants stay independent and we can evolve the ImageFilter
     * uniform layout without touching the MaskFilter pipeline.
     */
    private val blurImageFilterBindGroupLayout: GPUBindGroupLayout =
        context.device.createBindGroupLayout(
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
                            sampleType = GPUTextureSampleType.UnfilterableFloat,
                            viewDimension = GPUTextureViewDimension.TwoD,
                            multisampled = false,
                        ),
                    ),
                ),
            ),
        )

    private val blurImageFilterPipelineLayout = context.device.createPipelineLayout(
        PipelineLayoutDescriptor(bindGroupLayouts = listOf(blurImageFilterBindGroupLayout)),
    )

    /**
     * Phase G-saveLayer-imageFilter-blur -- H pass pipeline. Writes
     * pure RGBA into a freshly-cleared scratch texture ; no blend.
     */
    private val blurImageFilterHorizontalPipeline: GPURenderPipeline =
        context.device.createRenderPipeline(
            RenderPipelineDescriptor(
                layout = blurImageFilterPipelineLayout,
                vertex = VertexState(module = blurImageFilterShader, entryPoint = "vs_main"),
                fragment = FragmentState(
                    module = blurImageFilterShader,
                    entryPoint = "fs_horizontal",
                    targets = listOf(
                        ColorTargetState(
                            format = intermediateFormat,
                            blend = null,
                        ),
                    ),
                ),
            ),
        )

    /**
     * Phase G-saveLayer-imageFilter-blur -- V pass pipeline. Same shape
     * as the H pipeline -- writes pure RGBA into a freshly-cleared
     * scratch texture, no blend. The blending onto the parent happens
     * in the third pass via [layerCompositePipelineFor].
     */
    private val blurImageFilterVerticalPipeline: GPURenderPipeline =
        context.device.createRenderPipeline(
            RenderPipelineDescriptor(
                layout = blurImageFilterPipelineLayout,
                vertex = VertexState(module = blurImageFilterShader, entryPoint = "vs_main"),
                fragment = FragmentState(
                    module = blurImageFilterShader,
                    entryPoint = "fs_vertical",
                    targets = listOf(
                        ColorTargetState(
                            format = intermediateFormat,
                            blend = null,
                        ),
                    ),
                ),
            ),
        )

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
     * G5.3 / G5.3.x -- compute the (mode, matrix, tfParams) triple the
     * bitmap shader needs to lift a sample of [image] into the
     * intermediate target's sRGB-coded working space.
     *
     * The intermediate target convention is **premul sRGB-coded** (sRGB
     * primaries, sRGB OETF -- see [intermediateTexture] kdoc). When
     * [image]'s colorSpace is sRGB the pipeline reduces to identity ;
     * we route the existing fast path (`mode = 0`, identity matrix,
     * identity TF coefs).
     *
     * For other colorspaces we build the canonical CPU xform via
     * [SkColorSpaceXformSteps] from `image.colorSpace -> sRGB` (both
     * unpremul, since the texture upload keeps the source convention
     * and the shader's premul step happens after the colorspace
     * transform). The shader's two transform branches cover :
     *  - `CS_MODE_SRGB_TF_MATRIX` (mode = 1) : source TF is the sRGB TF
     *    (Display P3 falls here -- it shares the sRGB curve, only the
     *    primaries matrix differs). The shader uses the hardcoded sRGB
     *    EOTF and we ship only the column-major primaries matrix.
     *  - `CS_MODE_PARAMETRIC_TF_MATRIX` (mode = 2 ; G5.3.x) : source TF
     *    is sRGBish-classifying but not exactly the sRGB curve
     *    (Rec.2020 linear, Adobe RGB / k2Dot2, Rec.709 power, ...). The
     *    shader reads the parametric coefs from `csTfParams0/1` and
     *    evaluates the same 2-branch eval `SkcmsTransferFunctionEval`
     *    uses on the CPU side.
     *
     * Any source TF that classifies as PQ / HLG / HDR or as `Invalid`
     * returns `mode = 0` -- the existing as-uploaded sRGB fast path.
     * Wiring those up needs luminance scaling / OOTF handling out of
     * scope for G5.3.x.
     *
     * The destination TF is always the sRGB OETF (the intermediate
     * target's encoding). For the `srcToDstMatrix` we copy the
     * `SkColorSpaceXformSteps.srcToDstMatrix` verbatim -- it is
     * column-major and already folds in any non-HDR scale factor.
     */
    private fun bitmapColorSpaceFor(image: SkImage): Triple<Int, FloatArray, FloatArray> {
        val cs = image.colorSpace
        val dst = org.skia.foundation.SkColorSpace.makeSRGB()
        if (cs.hash() == dst.hash()) {
            return Triple(0, IDENTITY_CS_MATRIX, IDENTITY_CS_TF_PARAMS)
        }
        val srgbTfHash = dst.transferFnHash
        val srcTfType = org.skia.foundation.skcms.classify(cs.transferFn)
        // Only the sRGBish parametric family is wired up. PQ / HLG /
        // HLGish / HLGinvish / Invalid fall back to the as-uploaded
        // fast path (luminance scaling + OOTF are out of scope).
        if (srcTfType != org.skia.foundation.skcms.SkcmsTFType.sRGBish) {
            return Triple(0, IDENTITY_CS_MATRIX, IDENTITY_CS_TF_PARAMS)
        }
        val steps = org.skia.core.SkColorSpaceXformSteps(
            cs, org.skia.core.SkAlphaType.kUnpremul,
            dst, org.skia.core.SkAlphaType.kUnpremul,
        )
        if (steps.flags.isIdentity) {
            return Triple(0, IDENTITY_CS_MATRIX, IDENTITY_CS_TF_PARAMS)
        }
        // The xform steps' matrix is column-major already (see KDoc on
        // `SkColorSpaceXformSteps.srcToDstMatrix`) ; copy it verbatim.
        val matrix = steps.srcToDstMatrix.copyOf()
        if (cs.transferFnHash == srgbTfHash) {
            // Source TF == sRGB curve : the shader's hardcoded sRGB
            // EOTF path (mode = 1) is byte-identical to the parametric
            // eval with sRGB coefs, but we keep the dedicated branch
            // for G5.3 regression parity. Mode = 1 ignores the TF
            // params slot ; ship the identity coefs as a stable byte
            // payload.
            return Triple(1, matrix, IDENTITY_CS_TF_PARAMS)
        }
        // Source TF is sRGBish but not the sRGB curve : ship the
        // parametric coefs through `csTfParams0/1`. Layout matches
        // `SkcmsTransferFunction` field order (g, a, b, c, d, e, f) so
        // the shader's `parametric_tf` byte-for-byte mirrors the CPU
        // `skcmsTransferFunctionEval` for the sRGBish branch.
        val tf = cs.transferFn
        val tfParams = floatArrayOf(tf.g, tf.a, tf.b, tf.c, tf.d, tf.e, tf.f)
        return Triple(2, matrix, tfParams)
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

    /**
     * Phase G-saveLayer — allocate a child GPU device for a
     * `saveLayer` scope. The returned device shares this device's
     * [context] (so layer textures and parent textures live on the
     * same WebGPU adapter / queue) and the same
     * [intermediateFormat] (so a future direct-blit composite stays
     * bit-iso). The colorspace transform is forced to `false` on
     * layer devices : the layer renders into intermediate-format
     * pixels that the parent samples via [compositeFrom] ; the final
     * present pass (sRGB → Rec.2020) only runs once, on the root
     * canvas's flush.
     *
     * **Caveats.**
     *  - Layer devices have their own depth-stencil / pipeline caches.
     *    They cost N draw passes worth of resources per layer.
     *  - `width` and `height` are clamped to the layer bounds the
     *    canvas passes (already intersected with the parent clip), so
     *    1×1 minimum suffices. A zero-area layer never reaches this
     *    path (SkCanvas degenerates it to an empty save).
     *  - Currently used only for plain `saveLayer(bounds, paint)`. The
     *    SaveLayerRec backdrop / SkImageFilter / SkColorFilter slots
     *    on the layer paint fall through to `compositeFrom` with
     *    those slots dropped (documented deferred Phase G-saveLayer-X).
     */
    override fun makeLayerDevice(width: Int, height: Int): SkDevice =
        SkWebGpuDevice(
            context = context,
            width = width,
            height = height,
            applyColorspaceTransform = false,
            intermediateFormat = intermediateFormat,
        ).also {
            // Phase G-saveLayer-imageFilter-dropshadow -- saveLayer
            // semantics require the layer device to start transparent
            // (the layer captures only the draws that hit it ; pixels
            // not drawn remain alpha = 0 so the parent composite
            // sees-through to whatever was below). The root SkWebGpuDevice
            // ctor defaults `background` to opaque white (matching the
            // GM convention) ; we override here so the layer's first-
            // pass `loadOp = Clear` clears to transparent. Without this,
            // every saveLayer would have an opaque white background and
            // the imageFilter Blur / DropShadow paths would silhouette
            // the entire layer rect instead of the actually-drawn shape.
            it.setBackground(0)
        }

    /**
     * Phase G-saveLayer — composite a child device's pixels back onto
     * this device, honouring [paint]'s alpha + blendMode. **Direct
     * GPU-to-GPU blit** (Phase G-saveLayer-fast) : drains the child
     * device's pending draws onto its intermediate texture via
     * [flushDrawsOnly], then enqueues a [LayerCompositeDraw] that the
     * parent's flush replays as a fullscreen-quad render pass sampling
     * the child's intermediate via `textureLoad`. No readback, no
     * re-upload -- both textures live on the same WebGPU adapter / queue
     * (the child device was built via [makeLayerDevice], which shares
     * the parent's [context] + [intermediateFormat]).
     *
     * Blend mode is honoured via the composite pipeline's blend state
     * (matches the natively-blendable subset : kClear / kSrc / kSrcOver
     * / kDstOver) ; alpha + colour modulation fold into a per-draw
     * `paintColor` uniform.
     *
     * **Constraints.**
     *  - [src] must be a child [SkWebGpuDevice] (same context). A
     *    mismatched backend errors out — the cross-device path is not
     *    in scope.
     *  - Blend mode outside the kClear / kSrc / kSrcOver / kDstOver
     *    subset errors out -- same gate as the scaffolding's
     *    [enqueueImageRectDrawInternal].
     *  - **Colour filter** : honoured for the two upstream variants
     *    the WGSL shader implements -- `SkColorFilters.Blend(colour,
     *    mode)` and `SkColorFilters.Matrix(20 floats)`. Other variants
     *    (compose, lerp, table, sRGB-gamma, luma, working-CS wrapper,
     *    ...) silently fall through with the filter dropped -- same
     *    behaviour as before, scoped follow-ups will widen this set
     *    (Phase G-saveLayer-colorFilter-x). The supported blend modes
     *    for the `Blend` variant are the 15 Porter-Duff modes the WGSL
     *    `blend_premul` helper expresses (kClear...kScreen) ; the rest
     *    fall back to identity in-shader.
     *  - **Image filter** on [paint] : the tree is flattened via
     *    [resolveLayerImageFilterPlan] into a normalized
     *    `[preCF?][Blur?][postCF?]` plan. Supported variants :
     *      - `SkImageFilters.ColorFilter(cf, input = null)` -- routed
     *        through the composite-pass colour filter slot, same code
     *        path as `paint.colorFilter` (Phase G-saveLayer-imageFilter).
     *      - `SkImageFilters.Blur(sigmaX, sigmaY, kClamp / kDecal, input
     *        = null)` -- separable Gaussian via the 3-pass pipeline
     *        (Phase G-saveLayer-imageFilter-blur).
     *      - `SkImageFilters.Offset(dx, dy, input = null)` -- shifts the
     *        composite's `dstOriginSize.xy` -- no new pipeline (Phase
     *        G-saveLayer-imageFilter-offset).
     *      - `SkImageFilters.DropShadow(dx, dy, sigmaX, sigmaY, color,
     *        input = null)` -- 4-pass blur + colorize-via-Blend(color,
     *        kSrcIn) + offset composite + original composite, kSrcOver
     *        only (Phase G-saveLayer-imageFilter-dropshadow).
     *      - `SkImageFilters.Compose(outer, inner)` -- recursive walk
     *        of the chain (Phase G-saveLayer-imageFilter-compose), with
     *        leaves classified as "pre-blur CF" / "Blur" / "post-blur
     *        CF" depending on where they fall. A pre-blur CF triggers
     *        an extra (4th) render pass that applies the colour filter
     *        to the layer texture into a scratch before the Blur reads
     *        from it. Compose where a child is Offset or DropShadow is
     *        deferred -- throws.
     *    Other variants (MatrixTransform, DisplacementMap, Magnifier,
     *    Tile, Crop, Blend, Erode / Dilate, MatrixConvolution, Lighting,
     *    Arithmetic, ...) throw a clear "not yet supported" error. The
     *    single-occupancy rule still applies : `paint.colorFilter` may
     *    not coexist with a CF in the same stage of the Compose chain.
     */
    override fun compositeFrom(
        src: SkDevice,
        originX: Int,
        originY: Int,
        clip: SkIRect,
        paint: SkPaint?,
    ) {
        val gpuSrc = src as? SkWebGpuDevice
            ?: error(
                "SkWebGpuDevice.compositeFrom : source device is " +
                    "${src::class.simpleName} ; GPU composite only consumes " +
                    "SkWebGpuDevice. Cross-backend composite (CPU layer onto " +
                    "GPU root, or vice-versa) is out of scope — keep the " +
                    "layer device backend-matched to its parent."
            )
        if (gpuSrc.context !== context) {
            error(
                "SkWebGpuDevice.compositeFrom : child device's WebGPU " +
                    "context does not match the parent's. The layer texture " +
                    "cannot be sampled across contexts -- ensure the child " +
                    "was built via makeLayerDevice() so it inherits the " +
                    "parent's context."
            )
        }
        val mode = paint?.blendMode ?: SkBlendMode.kSrcOver
        when (mode) {
            SkBlendMode.kClear,
            SkBlendMode.kSrc,
            SkBlendMode.kSrcOver,
            SkBlendMode.kDstOver -> Unit
            else -> error(
                "SkWebGpuDevice.compositeFrom : blend mode $mode not " +
                    "supported. In scope : kClear / kSrc / kSrcOver / " +
                    "kDstOver -- the natively-blendable subset that " +
                    "[blendStateFor] expresses without fragment-side " +
                    "round-trip. Other modes need a fragment-side blend " +
                    "(Phase G-saveLayer-x).",
            )
        }

        // Phase G-saveLayer-imageFilter-compose -- walk the paint's
        // imageFilter tree (possibly Compose-wrapped) into a normalized
        // plan of "[pre-blur CF][Blur?][post-blur CF folded with
        // paint.colorFilter]". Unsupported leaves throw inside the
        // walker so the call site (saveLayer + restore) surfaces the
        // missing backend support rather than silently dropping the
        // filter. See [resolveLayerImageFilterPlan] for the full set
        // of supported / rejected patterns.
        val plan = resolveLayerImageFilterPlan(paint)

        val w = gpuSrc.width
        val h = gpuSrc.height
        // Drain the child's pending draws onto its intermediateTexture.
        // We must not run the child's flush() : that would copy through
        // its identity present pass to the readback target and stage a
        // CPU buffer back -- exactly the round-trip we're killing. The
        // child's intermediateTexture is in the same WebGPU queue as
        // ours, so encoding a render pass that samples it on a later
        // command buffer is well-ordered as long as the child's draws
        // have already been submitted.
        gpuSrc.flushDrawsOnly()

        // Compute the integer dst rect on the parent device, clipped
        // against the caller's [clip] (already intersected with the
        // parent's clip stack by SkCanvas) and the viewport.
        val ix0 = originX.coerceAtLeast(clip.left).coerceAtLeast(0)
        val iy0 = originY.coerceAtLeast(clip.top).coerceAtLeast(0)
        val ix1 = (originX + w).coerceAtMost(clip.right).coerceAtMost(width)
        val iy1 = (originY + h).coerceAtMost(clip.bottom).coerceAtMost(height)
        if (ix0 >= ix1 || iy0 >= iy1) return

        // Paint colour scale : alpha (and future colour filter) multiplies
        // the loaded texel. Default = (1, 1, 1, 1) when paint is null.
        // The layer pixels are premul, so we scale all 4 channels by the
        // paint alpha (matches the scaffolding's drawImageRect path which
        // unpremul-then-premul-with-paintAlpha collapsed to the same
        // multiply for premul sources).
        val paintAlpha = (paint?.alpha ?: 0xFF) / 255f

        // Phase G-saveLayer-colorFilter -- pack the post-blur colour
        // filter (or the only colour filter, if no blur is present)
        // into the 6-vec4f payload consumed by `layer_composite.wgsl`.
        // Unsupported variants leave the payload at the identity
        // (kind = 0) so the shader's fast path stays warm.
        val colorFilterPacked = packLayerCompositeColorFilter(plan.effectiveColorFilter)

        // Phase G-saveLayer-imageFilter-blur / -compose -- if the
        // imageFilter tree contains a Blur (with sigma > 0 on either
        // axis), route through the multi-pass blur pipeline. The pass
        // count is 3 when there is no pre-blur CF (H, V, composite) ;
        // 4 when a pre-blur CF is present (preCF, H, V, composite).
        val blurParams = plan.blurParams
        if (blurParams != null &&
            (blurParams.sigmaX > 0f || blurParams.sigmaY > 0f)
        ) {
            enqueueBlurredLayerComposite(
                gpuSrc = gpuSrc,
                w = w, h = h,
                originX = originX, originY = originY,
                scissor = intArrayOf(ix0, iy0, ix1 - ix0, iy1 - iy0),
                sigmaX = blurParams.sigmaX,
                sigmaY = blurParams.sigmaY,
                tileMode = blurParams.tileMode,
                paintAlpha = paintAlpha,
                colorFilterPacked = colorFilterPacked,
                preBlurColorFilter = plan.preBlurColorFilter,
                mode = mode,
            )
            return
        }

        // Phase G-saveLayer-imageFilter-offset -- `SkImageFilters.Offset(
        // dx, dy, input = null)` is a pure UV translation : the layer
        // pixels are unchanged, only the dst origin shifts by (dx, dy).
        // We reuse the plain [LayerCompositeDraw] path with the dst
        // origin and scissor shifted by the integer-rounded offset. No
        // new pipeline, no new shader -- the composite shader's existing
        // `dstOriginSize.xy` slot already drives the parent-pixel
        // mapping. Out-of-bounds samples land outside the integer dst
        // rect and the scissor culls them.
        val offsetParams = paint?.imageFilter?.asOffsetImageFilter()
        if (offsetParams != null) {
            if (offsetParams.input != null) {
                error(
                    "SkWebGpuDevice.compositeFrom : paint.imageFilter is a " +
                        "SkImageFilters.Offset(input = nonNull) with a non-null " +
                        "child filter. Only the input == null case is supported " +
                        "(Phase G-saveLayer-imageFilter-offset) -- a non-null " +
                        "child needs a render-to-texture pre-pass that the " +
                        "WebGPU layer composite pipeline cannot express yet."
                )
            }
            // Apply the (dx, dy) translation by shifting the dst origin
            // and the scissor by the same integer rounding the CPU
            // raster's `SkOffsetImageFilter.filterImage` applies
            // (`(dx * scale + 0.5).toInt()` with `scale >= 1`). The
            // saveLayer composite runs at the device's identity CTM, so
            // `scale == 1` and the rounding collapses to `floor(dx + 0.5)`.
            val sdx = floor(offsetParams.dx + 0.5f).toInt()
            val sdy = floor(offsetParams.dy + 0.5f).toInt()
            val shiftedOriginX = originX + sdx
            val shiftedOriginY = originY + sdy
            val shiftedIx0 = shiftedOriginX.coerceAtLeast(clip.left).coerceAtLeast(0)
            val shiftedIy0 = shiftedOriginY.coerceAtLeast(clip.top).coerceAtLeast(0)
            val shiftedIx1 = (shiftedOriginX + w).coerceAtMost(clip.right).coerceAtMost(width)
            val shiftedIy1 = (shiftedOriginY + h).coerceAtMost(clip.bottom).coerceAtMost(height)
            if (shiftedIx0 >= shiftedIx1 || shiftedIy0 >= shiftedIy1) return
            pending.add(
                LayerCompositeDraw(
                    layerView = gpuSrc.intermediateView,
                    layerWidth = w, layerHeight = h,
                    dstOriginX = shiftedOriginX, dstOriginY = shiftedOriginY,
                    scissor = intArrayOf(
                        shiftedIx0, shiftedIy0,
                        shiftedIx1 - shiftedIx0, shiftedIy1 - shiftedIy0,
                    ),
                    paintR = paintAlpha, paintG = paintAlpha,
                    paintB = paintAlpha, paintA = paintAlpha,
                    r = 1f, g = 1f, b = 1f, a = paintAlpha,
                    mode = mode,
                    colorFilterPacked = colorFilterPacked,
                ),
            )
            return
        }

        // Phase G-saveLayer-imageFilter-dropshadow -- `SkImageFilters
        // .DropShadow(dx, dy, sigmaX, sigmaY, color, input = null)`
        // renders a colorized blurred copy of the layer (offset by
        // (dx, dy)) BEHIND the original layer content. Two composite
        // sub-passes :
        //   1. shadow : layer alpha -> blur(sigma) -> tint(color, kSrcIn)
        //               -> composite at (originX + sdx, originY + sdy).
        //   2. original : layer RGBA -> composite at (originX, originY)
        //                 with the paint's regular colorFilter / alpha.
        // The user's [mode] is gated to kSrcOver here -- the Skia
        // semantic Compose(srcOver, original) commutes with the parent
        // composite only for kSrcOver. Other modes need an intermediate
        // layer to hold (shadow + original) before applying the user's
        // mode against the parent -- deferred follow-up.
        val dropShadowParams = paint?.imageFilter?.asDropShadowImageFilter()
        if (dropShadowParams != null) {
            if (dropShadowParams.input != null) {
                error(
                    "SkWebGpuDevice.compositeFrom : paint.imageFilter is a " +
                        "SkImageFilters.DropShadow(input = nonNull) with a " +
                        "non-null child filter. Only the input == null case is " +
                        "supported (Phase G-saveLayer-imageFilter-dropshadow) " +
                        "-- a non-null child needs a render-to-texture pre-pass " +
                        "that the WebGPU layer composite pipeline cannot " +
                        "express yet."
                )
            }
            if (mode != SkBlendMode.kSrcOver) {
                error(
                    "SkWebGpuDevice.compositeFrom : paint.imageFilter is a " +
                        "SkImageFilters.DropShadow but the layer paint's " +
                        "blendMode is $mode. Only kSrcOver is supported in " +
                        "the first DropShadow slice (Phase " +
                        "G-saveLayer-imageFilter-dropshadow) -- other modes " +
                        "need an intermediate layer to hold the (shadow + " +
                        "original) composite before applying the user's mode " +
                        "against the parent, which is a follow-up slice."
                )
            }
            enqueueDropShadowLayerComposite(
                gpuSrc = gpuSrc,
                w = w, h = h,
                originX = originX, originY = originY,
                clip = clip,
                params = dropShadowParams,
                paintAlpha = paintAlpha,
                colorFilterPacked = colorFilterPacked,
            )
            return
        }

        // No blur (or sigma == 0). A pre-blur CF from the Compose tree
        // collapses to the post-blur slot when there is no blur ; the
        // resolver folded both into `effectiveColorFilter` above.
        // Defensive : if the resolver ever leaves a stray preBlurCF
        // without a blur, surface it as an error rather than dropping
        // the filter.
        if (plan.preBlurColorFilter != null) {
            error(
                "SkWebGpuDevice.compositeFrom : internal -- pre-blur " +
                    "colour filter present without a Blur. The plan resolver " +
                    "should have folded it into effectiveColorFilter."
            )
        }
        pending.add(
            LayerCompositeDraw(
                layerView = gpuSrc.intermediateView,
                layerWidth = w, layerHeight = h,
                dstOriginX = originX, dstOriginY = originY,
                scissor = intArrayOf(ix0, iy0, ix1 - ix0, iy1 - iy0),
                paintR = paintAlpha, paintG = paintAlpha,
                paintB = paintAlpha, paintA = paintAlpha,
                r = 1f, g = 1f, b = 1f, a = paintAlpha,
                mode = mode,
                colorFilterPacked = colorFilterPacked,
            ),
        )
    }

    /**
     * Phase G-saveLayer-imageFilter-blur -- helper for [compositeFrom].
     * Allocates the two scratch textures (H and V passes), pre-computes
     * the per-axis Gaussian half-kernels (matches the MaskFilter blur's
     * [buildSymmetricGaussianHalfKernel]), and enqueues a single
     * [BlurredLayerCompositeDraw] that the dispatch loop expands into
     * three render passes.
     *
     * Sigma 0 on a given axis -> radius 0 (centre tap only, weight 1) :
     * the convolution along that axis is the identity. We still run
     * the pass for layout simplicity ; the cost is a single textureLoad
     * per pixel and the output is bit-iso with the non-blurred sample.
     */
    private fun enqueueBlurredLayerComposite(
        gpuSrc: SkWebGpuDevice,
        w: Int, h: Int,
        originX: Int, originY: Int,
        scissor: IntArray,
        sigmaX: Float, sigmaY: Float,
        tileMode: SkTileMode,
        paintAlpha: Float,
        colorFilterPacked: FloatArray,
        // Phase G-saveLayer-imageFilter-compose -- optional pre-blur
        // ColorFilter (from a Compose chain). `null` -> 3-pass pipeline
        // (H, V, composite). Non-null -> 4-pass pipeline (preCF, H, V,
        // composite) ; the preCF pass runs `layer_composite.wgsl` with
        // kSrc blend on the layer texture into a fresh scratch, then
        // the blur reads from that scratch.
        preBlurColorFilter: org.skia.foundation.SkColorFilter?,
        mode: SkBlendMode,
    ) {
        if (!sigmaX.isFinite() || !sigmaY.isFinite() || sigmaX < 0f || sigmaY < 0f) {
            error(
                "SkWebGpuDevice.compositeFrom : SkImageFilters.Blur sigma " +
                    "must be finite and non-negative ; got (sigmaX = $sigmaX, " +
                    "sigmaY = $sigmaY)."
            )
        }
        val unboundedRadiusX = if (sigmaX > 0f) {
            ceil(3.0 * sigmaX).toInt().coerceAtLeast(1)
        } else 0
        val unboundedRadiusY = if (sigmaY > 0f) {
            ceil(3.0 * sigmaY).toInt().coerceAtLeast(1)
        } else 0
        val radiusX = unboundedRadiusX.coerceAtMost(MAX_BLUR_RADIUS)
        val radiusY = unboundedRadiusY.coerceAtMost(MAX_BLUR_RADIUS)
        val kernelX = if (radiusX > 0) {
            buildSymmetricGaussianHalfKernel(sigmaX, radiusX)
        } else floatArrayOf(1f)
        val kernelY = if (radiusY > 0) {
            buildSymmetricGaussianHalfKernel(sigmaY, radiusY)
        } else floatArrayOf(1f)

        // Allocate two scratch textures of the same dimensions as the
        // layer texture. Both are colour-attachment + texture-binding
        // usage : the H pass writes scratchH (sampled by the V pass) ;
        // the V pass writes scratchV (sampled by the composite pass).
        val scratchH = context.device.createTexture(
            TextureDescriptor(
                size = Extent3D(width = w.toUInt(), height = h.toUInt()),
                format = intermediateFormat,
                usage = GPUTextureUsage.RenderAttachment or GPUTextureUsage.TextureBinding,
                label = "SkWebGpuDevice.blurImageFilterScratchH",
            ),
        )
        val scratchHView = scratchH.createView()
        val scratchV = context.device.createTexture(
            TextureDescriptor(
                size = Extent3D(width = w.toUInt(), height = h.toUInt()),
                format = intermediateFormat,
                usage = GPUTextureUsage.RenderAttachment or GPUTextureUsage.TextureBinding,
                label = "SkWebGpuDevice.blurImageFilterScratchV",
            ),
        )
        val scratchVView = scratchV.createView()

        // Phase G-saveLayer-imageFilter-compose -- allocate the pre-CF
        // scratch only when needed. The Blur H pass reads from this
        // texture (instead of the raw layer view) when a pre-blur CF is
        // present.
        val preCfScratch: GPUTexture?
        val preCfScratchView: GPUTextureView?
        val preCfPacked: FloatArray?
        if (preBlurColorFilter != null) {
            preCfScratch = context.device.createTexture(
                TextureDescriptor(
                    size = Extent3D(width = w.toUInt(), height = h.toUInt()),
                    format = intermediateFormat,
                    usage = GPUTextureUsage.RenderAttachment or GPUTextureUsage.TextureBinding,
                    label = "SkWebGpuDevice.blurImageFilterScratchPreCf",
                ),
            )
            preCfScratchView = preCfScratch.createView()
            preCfPacked = packLayerCompositeColorFilter(preBlurColorFilter)
        } else {
            preCfScratch = null
            preCfScratchView = null
            preCfPacked = null
        }

        pending.add(
            BlurredLayerCompositeDraw(
                layerView = gpuSrc.intermediateView,
                layerWidth = w, layerHeight = h,
                scratchHTexture = scratchH, scratchHView = scratchHView,
                scratchVTexture = scratchV, scratchVView = scratchVView,
                dstOriginX = originX, dstOriginY = originY,
                scissor = scissor,
                kernelX = kernelX, radiusX = radiusX,
                kernelY = kernelY, radiusY = radiusY,
                tileModeOrdinal = tileMode.ordinal,
                paintR = paintAlpha, paintG = paintAlpha,
                paintB = paintAlpha, paintA = paintAlpha,
                colorFilterPacked = colorFilterPacked,
                preBlurColorFilterPacked = preCfPacked,
                scratchPreCfTexture = preCfScratch,
                scratchPreCfView = preCfScratchView,
                r = 1f, g = 1f, b = 1f, a = paintAlpha,
                mode = mode,
            ),
        )
    }

    /**
     * Phase G-saveLayer-imageFilter-dropshadow -- helper for
     * [compositeFrom]. Allocates the two scratch textures (H and V
     * blur), pre-computes the per-axis Gaussian half-kernels, packs the
     * shadow-pass colour filter as `Blend(shadowColor, kSrcIn)` (the
     * colorize-from-alpha step), and enqueues a single
     * [DropShadowLayerCompositeDraw] that the dispatch loop expands into
     * four render passes.
     *
     * Sigma constraints mirror [enqueueBlurredLayerComposite] -- the
     * radius is clamped to [MAX_BLUR_RADIUS] and the kernel is
     * renormalised after the clamp. The blur tile mode is forced to
     * kDecal (transparent border) -- a kClamp blur would extend the
     * edge alpha out indefinitely, which doesn't match the upstream
     * SkDropShadowImageFilter semantic.
     */
    private fun enqueueDropShadowLayerComposite(
        gpuSrc: SkWebGpuDevice,
        w: Int, h: Int,
        originX: Int, originY: Int,
        clip: SkIRect,
        params: org.skia.foundation.SkDropShadowImageFilterParams,
        paintAlpha: Float,
        colorFilterPacked: FloatArray,
    ) {
        if (!params.sigmaX.isFinite() || !params.sigmaY.isFinite() ||
            params.sigmaX < 0f || params.sigmaY < 0f
        ) {
            error(
                "SkWebGpuDevice.compositeFrom : SkImageFilters.DropShadow " +
                    "sigma must be finite and non-negative ; got (sigmaX = " +
                    "${params.sigmaX}, sigmaY = ${params.sigmaY})."
            )
        }
        val unboundedRadiusX = if (params.sigmaX > 0f) {
            ceil(3.0 * params.sigmaX).toInt().coerceAtLeast(1)
        } else 0
        val unboundedRadiusY = if (params.sigmaY > 0f) {
            ceil(3.0 * params.sigmaY).toInt().coerceAtLeast(1)
        } else 0
        val radiusX = unboundedRadiusX.coerceAtMost(MAX_BLUR_RADIUS)
        val radiusY = unboundedRadiusY.coerceAtMost(MAX_BLUR_RADIUS)
        val kernelX = if (radiusX > 0) {
            buildSymmetricGaussianHalfKernel(params.sigmaX, radiusX)
        } else floatArrayOf(1f)
        val kernelY = if (radiusY > 0) {
            buildSymmetricGaussianHalfKernel(params.sigmaY, radiusY)
        } else floatArrayOf(1f)

        // Allocate two scratch textures of the same dimensions as the
        // layer texture -- same shape as [enqueueBlurredLayerComposite].
        val scratchH = context.device.createTexture(
            TextureDescriptor(
                size = Extent3D(width = w.toUInt(), height = h.toUInt()),
                format = intermediateFormat,
                usage = GPUTextureUsage.RenderAttachment or GPUTextureUsage.TextureBinding,
                label = "SkWebGpuDevice.dropShadowScratchH",
            ),
        )
        val scratchHView = scratchH.createView()
        val scratchV = context.device.createTexture(
            TextureDescriptor(
                size = Extent3D(width = w.toUInt(), height = h.toUInt()),
                format = intermediateFormat,
                usage = GPUTextureUsage.RenderAttachment or GPUTextureUsage.TextureBinding,
                label = "SkWebGpuDevice.dropShadowScratchV",
            ),
        )
        val scratchVView = scratchV.createView()

        // Integer-round the shadow displacement -- same rounding the
        // CPU raster's [SkDropShadowImageFilter] uses ([SkOffsetImageFilter]
        // shares this rounding). The saveLayer composite runs at the
        // device's identity CTM, so the `scale >= 1` factor collapses
        // to 1.
        val sdx = floor(params.dx + 0.5f).toInt()
        val sdy = floor(params.dy + 0.5f).toInt()
        val shadowOriginX = originX + sdx
        val shadowOriginY = originY + sdy

        // Original-pass scissor (the layer pixels land at the original
        // origin -- same shape as the no-filter composite path).
        val origIx0 = originX.coerceAtLeast(clip.left).coerceAtLeast(0)
        val origIy0 = originY.coerceAtLeast(clip.top).coerceAtLeast(0)
        val origIx1 = (originX + w).coerceAtMost(clip.right).coerceAtMost(width)
        val origIy1 = (originY + h).coerceAtMost(clip.bottom).coerceAtMost(height)
        // Shadow-pass scissor (the shadow lands at the offset origin --
        // its kernel-spread band is captured by the layer texture's
        // own bounds since the blur runs in layer space, no padding).
        val shadIx0 = shadowOriginX.coerceAtLeast(clip.left).coerceAtLeast(0)
        val shadIy0 = shadowOriginY.coerceAtLeast(clip.top).coerceAtLeast(0)
        val shadIx1 = (shadowOriginX + w).coerceAtMost(clip.right).coerceAtMost(width)
        val shadIy1 = (shadowOriginY + h).coerceAtMost(clip.bottom).coerceAtMost(height)

        // Both passes need a non-empty scissor to do useful work. If
        // the shadow lands entirely outside the parent clip, we still
        // need to draw the original. Skip the shadow pass by collapsing
        // its scissor to (0, 0, 0, 0) ; the dispatch loop honours
        // zero-area scissors (no draw).
        val shadowScissor = if (shadIx0 >= shadIx1 || shadIy0 >= shadIy1) {
            intArrayOf(0, 0, 0, 0)
        } else {
            intArrayOf(shadIx0, shadIy0, shadIx1 - shadIx0, shadIy1 - shadIy0)
        }
        if (origIx0 >= origIx1 || origIy0 >= origIy1) {
            // The original layer is entirely clipped out. Still draw
            // the shadow when its scissor is non-empty. If both are
            // empty, fall through to add nothing.
            if (shadowScissor[2] == 0 || shadowScissor[3] == 0) {
                scratchHView.close(); scratchH.close()
                scratchVView.close(); scratchV.close()
                return
            }
        }
        val originalScissor = if (origIx0 >= origIx1 || origIy0 >= origIy1) {
            intArrayOf(0, 0, 0, 0)
        } else {
            intArrayOf(origIx0, origIy0, origIx1 - origIx0, origIy1 - origIy0)
        }

        // Pack the shadow-pass colour filter as `Blend(shadowColor,
        // kSrcIn)`. Decompose the [SkColor] (ARGB) into non-premul
        // floats, premultiply by the paint alpha (the shadow inherits
        // the paint's alpha modulation -- mirrors the upstream
        // SkDropShadowImageFilter::filterImage which composes its
        // `Blend(SK_ColorBLACK, kSrcIn)` ColorFilterImageFilter into
        // the shadow output before the parent's SrcOver), then premul
        // the colour by its own alpha for the shader's premul-source
        // assumption.
        val shadowColor = params.color
        val ca = (((shadowColor ushr 24) and 0xFF) / 255f) * paintAlpha
        val crNonPre = ((shadowColor ushr 16) and 0xFF) / 255f
        val cgNonPre = ((shadowColor ushr 8) and 0xFF) / 255f
        val cbNonPre = (shadowColor and 0xFF) / 255f
        val shadowColorFilterPacked = FloatArray(24)
        shadowColorFilterPacked[0] = 1f                              // kind = 1 (Blend)
        shadowColorFilterPacked[1] = SkBlendMode.kSrcIn.ordinal.toFloat()
        shadowColorFilterPacked[4] = crNonPre * ca                   // premul R
        shadowColorFilterPacked[5] = cgNonPre * ca                   // premul G
        shadowColorFilterPacked[6] = cbNonPre * ca                   // premul B
        shadowColorFilterPacked[7] = ca                              // alpha

        pending.add(
            DropShadowLayerCompositeDraw(
                layerView = gpuSrc.intermediateView,
                layerWidth = w, layerHeight = h,
                scratchHTexture = scratchH, scratchHView = scratchHView,
                scratchVTexture = scratchV, scratchVView = scratchVView,
                dstOriginX = originX, dstOriginY = originY,
                shadowDstOriginX = shadowOriginX, shadowDstOriginY = shadowOriginY,
                originalScissor = originalScissor,
                shadowScissor = shadowScissor,
                kernelX = kernelX, radiusX = radiusX,
                kernelY = kernelY, radiusY = radiusY,
                tileModeOrdinal = SkTileMode.kDecal.ordinal,
                shadowColorFilterPacked = shadowColorFilterPacked,
                paintR = paintAlpha, paintG = paintAlpha,
                paintB = paintAlpha, paintA = paintAlpha,
                originalColorFilterPacked = colorFilterPacked,
                r = 1f, g = 1f, b = 1f, a = paintAlpha,
                mode = SkBlendMode.kSrcOver,
            ),
        )
    }

    /**
     * Phase G-saveLayer-imageFilter-compose -- normalized plan for the
     * layer paint's [SkImageFilter] slot, built by walking the (possibly
     * Compose-wrapped) tree into the shape the WebGPU composite pipeline
     * can dispatch :
     *
     *   [optional pre-blur ColorFilter]
     *   [optional Blur]
     *   [optional post-blur ColorFilter (folded with paint.colorFilter)]
     *
     * The pre-blur ColorFilter runs in a dedicated pre-pass that reads
     * the layer texture and writes to a scratch (using
     * `layer_composite.wgsl` with `kSrc` blend) before the Blur's H pass
     * reads from that scratch. The post-blur ColorFilter rides in the
     * composite pass's uniform (the existing slot consumed by
     * `layer_composite.wgsl` after the paintColor scale).
     *
     * When the imageFilter contains no Blur, the whole plan collapses
     * onto the existing [LayerCompositeDraw] path : pre-blur and
     * post-blur slots fold into a single effective colour filter (the
     * existing single-occupancy rule applies -- if both slots are non-
     * null, the resolver throws).
     *
     * Top-level Offset / DropShadow are NOT modelled by the plan : the
     * dispatch in [compositeFrom] handles them BEFORE calling this
     * resolver. The resolver therefore only sees Blur, ColorFilter,
     * and Compose nodes ; encountering an Offset / DropShadow inside
     * a Compose tree throws a "deferred" error (mixing those structural
     * filters with the normalized `[preCF?][Blur?][postCF?]` plan needs
     * a render-to-texture intermediate that is out of scope for the
     * first Compose slice).
     */
    private data class ResolvedLayerImageFilterPlan(
        // Single ColorFilter applied to the layer texture before the
        // Blur reads from it. `null` means no pre-blur stage.
        val preBlurColorFilter: org.skia.foundation.SkColorFilter?,
        // Blur parameters, or `null` if the filter tree carries no Blur.
        // When present, the dispatch enqueues a [BlurredLayerCompositeDraw]
        // (optionally extended with a pre-blur CF pass) ; when absent,
        // the dispatch enqueues a plain [LayerCompositeDraw].
        val blurParams: org.skia.foundation.SkBlurImageFilterParams?,
        // Effective ColorFilter consumed by the composite pass uniform.
        // Folds `paint.colorFilter` with any post-blur (or pre-blur when
        // no blur is present) ColorFilter from the tree.
        val effectiveColorFilter: org.skia.foundation.SkColorFilter?,
    )

    /**
     * Phase G-saveLayer-imageFilter -- resolver for the layer paint's
     * [SkImageFilter] slot. See [ResolvedLayerImageFilterPlan] for the
     * shape of the normalized plan.
     *
     * Supported variants (Phase G-saveLayer-imageFilter / -blur /
     * -compose) :
     *  - `null` filter -> just `paint.colorFilter` (no blur, no pre-CF).
     *  - `SkImageFilters.ColorFilter(cf, input = null)` -> post-blur CF
     *    only ; folds with `paint.colorFilter` per the single-occupancy
     *    rule (throws if both are set).
     *  - `SkImageFilters.Blur(sigmaX, sigmaY, kClamp/kDecal,
     *    input = null)` -> blur params, no pre-CF, post-CF =
     *    `paint.colorFilter`.
     *  - `SkImageFilters.Offset(dx, dy, input = null)` / `SkImageFilters
     *    .DropShadow(...)` at the TOP level -> pass-through : no blur,
     *    no pre-CF, effective CF = `paint.colorFilter`. The dispatch
     *    in [compositeFrom] handles the structural shift / shadow pass
     *    before this resolver sees the filter.
     *  - `SkImageFilters.Compose(outer, inner)` -> walk the children
     *    recursively : `inner` is applied first to the source image,
     *    then `outer` to the inner's result. The walk classifies each
     *    leaf as "before blur" / "blur" / "after blur" depending on
     *    where it falls in the chain.
     *
     * Unsupported patterns throw with a clear error :
     *  - Any Compose chain that introduces more than one Blur (e.g.
     *    `Compose(Blur, Blur)`), or a Blur with a non-`kClamp`/`kDecal`
     *    tile mode, or a non-null `input` on a leaf Blur / ColorFilter.
     *  - Any non-`null` `paint.colorFilter` combined with a post-blur
     *    ColorFilter from the Compose chain (single-occupancy uniform).
     *  - Any Offset / DropShadow inside a Compose tree -- deferred
     *    (the structural filter mixed with the normalized blur/CF plan
     *    needs a render-to-texture intermediate that the first Compose
     *    slice doesn't ship).
     *  - Any other [SkImageFilter] variant in a leaf position
     *    (MatrixTransform, DisplacementMap, ...).
     */
    private fun resolveLayerImageFilterPlan(
        paint: SkPaint?,
    ): ResolvedLayerImageFilterPlan {
        val imf = paint?.imageFilter
            ?: return ResolvedLayerImageFilterPlan(
                preBlurColorFilter = null,
                blurParams = null,
                effectiveColorFilter = paint?.colorFilter,
            )

        // Phase G-saveLayer-imageFilter-offset / -dropshadow -- both
        // variants are folded into the composite uniform / dispatch by
        // [compositeFrom] directly when they sit at the TOP of the
        // filter tree ; the plan here is just "no blur, no preCF,
        // effectiveCF = paint.colorFilter" (the offset / shadow placement
        // is structural, not per-pixel). The dispatch gate validates
        // input == null there too, so this branch only short-circuits
        // the throw path of the walker below.
        if (imf.asOffsetImageFilter() != null || imf.asDropShadowImageFilter() != null) {
            return ResolvedLayerImageFilterPlan(
                preBlurColorFilter = null,
                blurParams = null,
                effectiveColorFilter = paint.colorFilter,
            )
        }

        // Walk the Compose tree, classifying each leaf as "pre-blur CF"
        // / "blur" / "post-blur CF". `inner` runs first, so we recurse
        // into it before `outer`. The walk uses a mutable accumulator
        // because the chain is left-associative (e.g.
        // `Compose(Compose(A, B), C)` resolves as `A(B(C(src)))`).
        var preBlurCF: org.skia.foundation.SkColorFilter? = null
        var blur: org.skia.foundation.SkBlurImageFilterParams? = null
        var postBlurCF: org.skia.foundation.SkColorFilter? = null

        fun walk(node: org.skia.foundation.SkImageFilter) {
            val composeParams = node.asComposeImageFilter()
            if (composeParams != null) {
                // inner runs first, then outer.
                walk(composeParams.inner)
                walk(composeParams.outer)
                return
            }
            // Phase G-saveLayer-imageFilter-compose -- Offset / DropShadow
            // inside a Compose tree is deferred. Mixing those structural
            // filters with the normalized `[preCF?][Blur?][postCF?]` plan
            // needs a render-to-texture intermediate (the Offset / shadow
            // would shift either the source the Blur reads from, or the
            // composite output -- both need a separate pre-pass). Throw
            // a clear "deferred" error so the call site surfaces the gap.
            if (node.asOffsetImageFilter() != null ||
                node.asDropShadowImageFilter() != null
            ) {
                error(
                    "SkWebGpuDevice.compositeFrom : a Compose chain contains " +
                        "an ${node::class.simpleName} leaf (e.g. Compose(" +
                        "Offset, Blur)) -- not yet supported. Top-level " +
                        "Offset / DropShadow ship in their own dispatch " +
                        "paths, but mixing them with a Compose tree (where " +
                        "they would shift the texture the Blur reads from, " +
                        "or shift the composite output relative to a colour-" +
                        "filtered scratch) is deferred -- it needs a render-" +
                        "to-texture intermediate that the first Compose " +
                        "support slice (Phase G-saveLayer-imageFilter-" +
                        "compose) doesn't ship."
                )
            }
            val blurLeaf = node.asBlurImageFilter()
            if (blurLeaf != null) {
                if (blurLeaf.input != null) {
                    error(
                        "SkWebGpuDevice.compositeFrom : a Blur leaf inside the " +
                            "imageFilter tree has a non-null child filter. The " +
                            "Compose support flattens the tree, so leaves must " +
                            "have input == null -- nest the child via a Compose " +
                            "node instead (e.g. SkImageFilters.Compose(Blur(...), " +
                            "child) rather than Blur(..., input = child))."
                    )
                }
                when (blurLeaf.tileMode) {
                    SkTileMode.kClamp, SkTileMode.kDecal -> Unit
                    else -> error(
                        "SkWebGpuDevice.compositeFrom : a Blur leaf inside the " +
                            "imageFilter tree has tileMode = ${blurLeaf.tileMode}. " +
                            "Only kClamp and kDecal are supported on the WebGPU " +
                            "backend ; kRepeat / kMirror need a sampler with the " +
                            "corresponding addressMode (follow-up slice)."
                    )
                }
                if (blur != null) {
                    error(
                        "SkWebGpuDevice.compositeFrom : the imageFilter tree " +
                            "carries more than one Blur (e.g. Compose(Blur, " +
                            "Blur)). The first Compose support slice handles at " +
                            "most one Blur per layer composite -- folding two " +
                            "Gaussians via sigma = sqrt(s1^2 + s2^2) needs a " +
                            "follow-up slice that proves the kernel-mass " +
                            "renormalization on kDecal."
                    )
                }
                blur = blurLeaf
                return
            }
            val cfLeaf = node.asColorFilterImageFilter()
            if (cfLeaf != null) {
                if (cfLeaf.input != null) {
                    error(
                        "SkWebGpuDevice.compositeFrom : a ColorFilter leaf " +
                            "inside the imageFilter tree has a non-null child " +
                            "filter. Compose flattens the tree, so leaves must " +
                            "have input == null -- nest the child via a Compose " +
                            "node instead (e.g. SkImageFilters.Compose(" +
                            "ColorFilter(cf), child) rather than ColorFilter(cf, " +
                            "input = child))."
                    )
                }
                if (blur == null) {
                    // This CF runs before the Blur (or there is no Blur).
                    if (preBlurCF != null) {
                        error(
                            "SkWebGpuDevice.compositeFrom : the imageFilter tree " +
                                "carries more than one ColorFilter in the same " +
                                "stage (e.g. Compose(ColorFilter, ColorFilter) " +
                                "with no Blur between them). The composite " +
                                "shader's colorFilter uniform is single-occupancy " +
                                "per stage -- folding two arbitrary SkColorFilter " +
                                "variants needs an SkComposeColorFilter extractor " +
                                "that the scaffolding slice doesn't ship."
                        )
                    }
                    preBlurCF = cfLeaf.colorFilter
                } else {
                    // This CF runs after the Blur.
                    if (postBlurCF != null) {
                        error(
                            "SkWebGpuDevice.compositeFrom : the imageFilter tree " +
                                "carries more than one ColorFilter after the " +
                                "Blur (e.g. Compose(ColorFilter, Compose(" +
                                "ColorFilter, Blur))). The composite shader's " +
                                "colorFilter uniform is single-occupancy per " +
                                "stage."
                        )
                    }
                    postBlurCF = cfLeaf.colorFilter
                }
                return
            }
            error(
                "SkWebGpuDevice.compositeFrom : paint.imageFilter " +
                    "${node::class.simpleName} not yet supported on the WebGPU " +
                    "backend. Supported leaves in a Compose chain : " +
                    "SkImageFilters.Blur(kClamp / kDecal, input = null) and " +
                    "SkImageFilters.ColorFilter(cf, input = null). Other " +
                    "variants -- Offset, DropShadow, MatrixTransform, " +
                    "DisplacementMap, Magnifier, Tile, Crop, Blend, Erode / " +
                    "Dilate, MatrixConvolution, Lighting, Arithmetic -- need " +
                    "follow-up slices that add a fragment-side UV-remapping " +
                    "or multi-pass render-target pipeline."
            )
        }
        walk(imf)

        // Single-occupancy rule -- a CF on paint.colorFilter may not
        // coexist with a CF from the Compose chain that lands in the
        // same stage (the composite shader's uniform is single-slot).
        // When no Blur is present, pre-blur and post-blur collapse onto
        // the same stage as the composite pass, so we treat any non-
        // null tree-side CF as the post-blur slot for the conflict check.
        if (blur == null) {
            // No blur : preBlurCF and postBlurCF are not both populated
            // (the walk classifies all CFs as "pre-blur" before any blur
            // is seen). Fold into a single effective CF.
            val treeCF = preBlurCF ?: postBlurCF
            if (treeCF != null && paint.colorFilter != null) {
                error(
                    "SkWebGpuDevice.compositeFrom : both paint.colorFilter and " +
                        "paint.imageFilter (ColorFilter / Compose chain) are " +
                        "set. The WebGPU layer composite uniform is single-" +
                        "occupancy (one SkColorFilter per draw) -- folding two " +
                        "arbitrary SkColorFilter variants needs an " +
                        "SkComposeColorFilter extractor that the scaffolding " +
                        "slice doesn't ship. Set only one of the two on the " +
                        "layer paint."
                )
            }
            return ResolvedLayerImageFilterPlan(
                preBlurColorFilter = null,
                blurParams = null,
                effectiveColorFilter = treeCF ?: paint.colorFilter,
            )
        }
        // Blur present : pre-blur CF (if any) stays as the pre-pass ;
        // post-blur CF folds with paint.colorFilter under the single-
        // occupancy rule.
        if (postBlurCF != null && paint.colorFilter != null) {
            error(
                "SkWebGpuDevice.compositeFrom : both paint.colorFilter and a " +
                    "post-blur ColorFilter from the imageFilter tree are set. " +
                    "The composite shader's colorFilter uniform is single-" +
                    "occupancy -- folding the two would need an " +
                    "SkComposeColorFilter extractor that the scaffolding slice " +
                    "doesn't ship. Set only one of the two."
            )
        }
        return ResolvedLayerImageFilterPlan(
            preBlurColorFilter = preBlurCF,
            blurParams = blur,
            effectiveColorFilter = postBlurCF ?: paint.colorFilter,
        )
    }

    /**
     * Phase G-saveLayer-colorFilter -- pack the layer paint's colour
     * filter into the 6-vec4f payload consumed by `layer_composite.wgsl`.
     *
     * Layout :
     *   [ 0..3 ] colorFilterKindMode : (kind, blendMode, 0, 0)
     *   [ 4..7 ] colorFilterParam0   : kind 1 -> premul colour ;
     *                                  kind 2 -> matrix row 0
     *   [ 8..11] colorFilterParam1   : matrix row 1
     *   [12..15] colorFilterParam2   : matrix row 2
     *   [16..19] colorFilterParam3   : matrix row 3
     *   [20..23] colorFilterBias     : per-row bias (R, G, B, A)
     *
     * Returns a 24-element [FloatArray] zeroed at kind 0 for the
     * identity / unsupported path. Supported variants :
     *  - `SkColorFilters.Blend(colour, mode)` -> kind = 1. The constant
     *    [SkColor] is decomposed to non-premul float RGBA and then
     *    premultiplied in-shader-friendly form (R*A, G*A, B*A, A).
     *  - `SkColorFilters.Matrix(20 floats)` -> kind = 2. The 20 row-
     *    major floats are unpacked into 4 vec4f rows (4 coefficients
     *    each) + a 1 vec4f bias (5th column per row).
     *
     * All other variants return the identity payload -- the shader
     * fast-path through `kind == 0` makes the composite bit-iso with
     * the no-filter case.
     */
    private fun packLayerCompositeColorFilter(filter: org.skia.foundation.SkColorFilter?): FloatArray {
        val out = FloatArray(24) // 6 vec4f * 4 floats each.
        if (filter == null) return out
        val blendParams = filter.asBlendModeFilter()
        if (blendParams != null) {
            out[0] = 1f                                  // kind = 1
            out[1] = blendParams.mode.ordinal.toFloat()  // SkBlendMode ordinal
            val c = blendParams.colour
            val ca = ((c ushr 24) and 0xFF) / 255f
            val cr = ((c ushr 16) and 0xFF) / 255f
            val cg = ((c ushr 8)  and 0xFF) / 255f
            val cb = ((c       )  and 0xFF) / 255f
            // Premul the constant colour -- the shader's blend operates
            // on premul vec4fs, matching SkBlendColorFilter's CPU path.
            out[4] = cr * ca
            out[5] = cg * ca
            out[6] = cb * ca
            out[7] = ca
            return out
        }
        val matrixParams = filter.asMatrixFilter()
        if (matrixParams != null) {
            out[0] = 2f // kind = 2
            // matrix is row-major, 20 floats : 4 rows of (R, G, B, A
            // coefs + bias). Pack each row's 4 coefs into a vec4f and
            // the 4 biases into the shared bias vec4f.
            val m = matrixParams.matrix
            // Row 0 (R) -- coefs (m0..m3), bias m4.
            out[4]  = m[0]; out[5]  = m[1]; out[6]  = m[2];  out[7]  = m[3]
            // Row 1 (G) -- coefs (m5..m8), bias m9.
            out[8]  = m[5]; out[9]  = m[6]; out[10] = m[7];  out[11] = m[8]
            // Row 2 (B) -- coefs (m10..m13), bias m14.
            out[12] = m[10]; out[13] = m[11]; out[14] = m[12]; out[15] = m[13]
            // Row 3 (A) -- coefs (m15..m18), bias m19.
            out[16] = m[15]; out[17] = m[16]; out[18] = m[17]; out[19] = m[18]
            // Bias vec4f -- (R, G, B, A) biases (m4, m9, m14, m19).
            out[20] = m[4]; out[21] = m[9]; out[22] = m[14]; out[23] = m[19]
            return out
        }
        // Unsupported variant -- drop the filter, fall through to the
        // no-filter composite path (matches the pre-slice behaviour).
        return out
    }

    /**
     * G2.x -- last [SkClipShape] pushed by [SkCanvas.bindClip]. Sampled
     * by [drawFillRect] at draw-record time, captured in the [RectDraw]
     * so the per-draw uniform encodes the clip alongside the rect
     * bounds. Other pipelines (gradients / bitmap shader / polygon /
     * stencil-and-cover) **do not** consume this slot yet -- they fall
     * back to the integer scissor (their current behaviour). When the
     * canvas's clip is a non-shape path on a non-rect-fill draw, the
     * canvas-side guard in [SkCanvas.bindClip] still throws. Future
     * slices widen the consumer set ; this first cut just covers
     * solid-color rect fills, the most common case.
     */
    private var activeClipShape: SkClipShape? = null

    override fun setActiveClipShape(shape: SkClipShape?) {
        activeClipShape = shape
    }

    /**
     * G2.x slice 2 -- predicate for [drawPath] : `true` if the dispatch
     * would land in one of the bitmap-shader pipelines that now honour
     * the analytical clip shape. Mirrors the gates downstream in
     * [drawPath] without performing the actual draw enqueue. Used to
     * short-circuit [requireClipShapeHonoured] at the top of [drawPath]
     * so curved clipPath + bitmap-shader fills work end-to-end while
     * other branches keep the fail-fast.
     *
     * Gate 1 (rect path / `bitmap_shader.wgsl`) :
     *   - `paint.shader is SkBitmapShader`,
     *   - `paint.style == kFill_Style`,
     *   - `ctm.isAxisAligned` (axis-aligned device-space rect ; rotated
     *     CTM rect paths fall to gate 2 which bounds the painted region
     *     via stencil-and-cover).
     *   - any localMatrix (rotated / skewed allowed via the affine
     *     uniform ; G5.2.2 widening).
     *   - `path.isRect() != null`.
     *
     * Gate 2 (non-rect AA path / `aa_stencil_cover_bitmap_shader.wgsl`) :
     *   - `paint.shader is SkBitmapShader`,
     *   - `paint.isAntiAlias`,
     *   - any CTM + any localMatrix (rotated / skewed allowed ; G5.2.2).
     *   - `path.isRect() == null` (covered by gate 1 otherwise) OR
     *     rotated-CTM rect (gate 1 falls through).
     *
     * Other configurations (non-AA non-rect path under rotated CTM)
     * fall back to the solid-colour fill machinery and remain under
     * the existing fail-fast.
     */
    private fun willRouteThroughClipAwareBitmapShader(
        path: SkPath,
        ctm: SkMatrix,
        paint: SkPaint,
    ): Boolean {
        val shader = paint.shader as? SkBitmapShader ?: return false
        if (shader.localMatrix.hasPerspective() || ctm.hasPerspective()) return false
        val isRect = path.isRect() != null
        // Rect branch (G5.2) -- axis-aligned-CTM rect fast path.
        if (isRect && paint.style == SkPaint.Style.kFill_Style && ctm.isAxisAligned) return true
        // AA non-rect branch (G5.2.1 / G5.2.2) -- arbitrary affine CTM
        // + localMatrix, fill style + AA paint. Captures rotated-CTM
        // rect paths too (they fall through gate 1's axis-aligned
        // requirement).
        if (paint.isAntiAlias && paint.style == SkPaint.Style.kFill_Style) return true
        return false
    }

    /**
     * G2.x -- fail-fast guard for draws that are NOT yet plumbed through
     * the analytical clip shape. Called by every non-rect-fill entry
     * point (stroke rects, paths, gradients, bitmap shader, etc.). When
     * an [activeClipShape] is set but the draw doesn't consume it, we
     * throw rather than silently producing wrong pixels. This preserves
     * the pre-G2.x behaviour : non-rect-fill draws under a curved
     * clipPath used to throw at canvas-bindClip time ; now they throw
     * one level lower (the canvas still binds, but the device refuses
     * the draw).
     *
     * Plain rect clip shapes ([SkClipShape.Rect]) are honoured by the
     * integer scissor on every pipeline -- the device passes those
     * through without complaint.
     */
    private fun requireClipShapeHonoured(drawKind: String) {
        val shape = activeClipShape ?: return
        if (shape is SkClipShape.Rect) return  // Scissor already handles this.
        error(
            "SkWebGpuDevice.$drawKind does not honour the active clipPath " +
                "(curved-shape clip captured by SkCanvas). Today only " +
                "solid-color drawRect-fill consumes simpleShapeClip ; other " +
                "pipelines (stroke / drawPath / gradients / bitmap shader) " +
                "fall back to the integer scissor and would silently drop " +
                "the curve mask. Use clipRect() instead, route the draw " +
                "through drawRect-fill, or wait for the follow-up slice " +
                "that widens the consumer set."
        )
    }

    /**
     * Phase MaskFilter-blur -- if [paint]'s `maskFilter` is a
     * Gaussian blur (any of `kNormal` / `kSolid` / `kOuter` / `kInner`)
     * on a non-shader paint and the CTM is axis-aligned, render [path]
     * through the offscreen-mask + separable Gaussian blur pipeline
     * and return `true`. Returns `false` if the paint / CTM doesn't
     * fall in the supported sub-set ; the caller falls through to the
     * regular fill machinery (with the maskFilter silently dropped,
     * matching the pre-slice GPU behaviour).
     *
     * The four [SkBlurStyle]s differ only in the V-pass shader's
     * post-convolution composition step :
     *   - kNormal : output = B(p)              (soft blur only)
     *   - kSolid  : output = min(M+B, 1)       (sharp shape + halo)
     *   - kOuter  : output = max(B-M, 0)       (halo outside the shape)
     *   - kInner  : output = B * M             (blur clipped to inside)
     * where M is the sharp shape-mask alpha and B is the blurred
     * coverage. The shader receives both textures via the V-pass bind
     * group and branches on the style ordinal packed into the uniform.
     *
     * Hard scope :
     *   - `paint.maskFilter is SkBlurMaskFilter` (all 4 styles),
     *   - `paint.shader == null` (shaded blur out of scope ; CPU raster
     *     has the same limitation, see
     *     [org.skia.core.SkBitmapDevice.drawPathWithMaskFilter]),
     *   - `ctm.isAxisAligned` (rotated / skewed -> drop blur).
     *   - non-inverse path fillType (inverse fills under blur are
     *     conceptually unbounded, matching the CPU raster fallback).
     *
     * Implementation :
     *   1. Compute device-space bounds of [path] under [ctm], expanded
     *      by `ceil(3 * sigma)` (the kernel radius) plus a 1-px AA
     *      safety margin. Intersect with [clip] and viewport. Bail if
     *      degenerate.
     *   2. Allocate a child [SkWebGpuDevice] sized to the expanded
     *      bounds via [makeLayerDevice]. Render [path] onto it with a
     *      white-tint paint (`maskFilter = null`, `color = WHITE`,
     *      `blendMode = kSrc`, no shader / colourFilter / pathEffect)
     *      under a translated CTM that maps the path's origin to the
     *      mask's `(0, 0)`. Call [flushDrawsOnly] so the child's
     *      intermediate carries the rasterised shape mask.
     *   3. Allocate a host-side scratch H-pass texture of the same
     *      dimensions and format as the shape mask.
     *   4. Enqueue a [BlurredPathDraw] : the parent's [flush] picks it
     *      up and emits two render passes (H : mask -> scratchH ; V :
     *      scratchH -> parent intermediate, modulated by the paint
     *      colour and blended via the pipeline's blend state).
     *
     * The kernel is the same separable 1-D Gaussian the CPU raster
     * uses ([org.skia.foundation.SkBlurMaskFilter.gaussianKernel1D]),
     * pre-computed here and shipped to the shader through the
     * per-draw uniform. Radius is clamped to [MAX_BLUR_RADIUS] (sigma
     * up to ~10.6 ; larger sigma renormalises the kernel at the
     * clamped radius -- slightly under-spread visually but stays
     * mass-conserving). Larger blurs fall outside this slice's scope.
     */
    private fun drawPathWithBlurMaskFilterIfApplicable(
        path: SkPath,
        ctm: SkMatrix,
        clip: SkIRect,
        paint: SkPaint,
    ): Boolean {
        val rawMaskFilter = paint.maskFilter ?: return false
        if (rawMaskFilter !is SkBlurMaskFilter) return false
        if (!ctm.isAxisAligned) return false
        if (path.fillType.isInverse()) return false
        // Phase MaskFilter-blur-shaded -- shaded paints (gradient /
        // bitmap shader) take the dedicated RGBA layer pipeline below.
        // The fast alpha-only path further down handles the solid-paint
        // case (the historical #570 / #575 scope).
        if (paint.shader != null) {
            return drawPathWithShadedBlurMaskFilter(path, ctm, clip, paint, rawMaskFilter)
        }

        // Apply the CTM-scale rescale if the filter ignores the CTM
        // (Phase R1-C contract -- mirrors SkBitmapDevice).
        val scale = ctm.getMaxScale().coerceAtLeast(1f)
        val maskFilter = (if (!rawMaskFilter.respectCTM) {
            rawMaskFilter.withCtmScale(scale) as? SkBlurMaskFilter ?: return false
        } else {
            rawMaskFilter
        })

        val sigma = maskFilter.sigma
        if (!sigma.isFinite() || sigma <= 0f) return false

        // Clamp the radius to the shader's MAX_BLUR_RADIUS so the
        // uniform layout stays fixed. The kernel is renormalised after
        // the clamp so the centre tap preserves full alpha (the
        // out-spread tails are slightly clipped, which is a known
        // first-slice limitation -- documented in the BlurredPathDraw
        // kdoc).
        val unboundedRadius = ceil(3.0 * sigma).toInt().coerceAtLeast(1)
        val radius = unboundedRadius.coerceAtMost(MAX_BLUR_RADIUS)
        val kernel = buildSymmetricGaussianHalfKernel(sigma, radius)

        // Compute device-space bounds of the path under the CTM,
        // expand by radius + 1 px safety, intersect with clip + viewport.
        val srcBounds = path.computeBounds()
        val devBounds = ctm.mapRect(srcBounds)
        // Stroke-style paths expand the bounds by half the stroke
        // width (post-CTM scale) ; mirror SkBitmapDevice. The blur
        // padding sits on top of the stroke expansion.
        val strokeExpand = if (paint.style != SkPaint.Style.kFill_Style) {
            (paint.strokeWidth * scale * 0.5f) + 1f
        } else 1f
        var ml = floor(devBounds.left - strokeExpand).toInt() - radius
        var mt = floor(devBounds.top - strokeExpand).toInt() - radius
        var mr = ceil(devBounds.right + strokeExpand).toInt() + radius
        var mb = ceil(devBounds.bottom + strokeExpand).toInt() + radius
        ml = maxOf(ml, clip.left, 0)
        mt = maxOf(mt, clip.top, 0)
        mr = minOf(mr, clip.right, width)
        mb = minOf(mb, clip.bottom, height)
        val maskW = mr - ml
        val maskH = mb - mt
        if (maskW <= 0 || maskH <= 0) return true  // Fully clipped -- nothing to draw.

        // Render the shape into a child layer device. The white-tint
        // paint produces a premul (1, 1, 1, coverage) mask in the
        // child's intermediate ; the V pass below reads `.a` (= .rgb)
        // and scales by the user's paint colour.
        val maskDevice = makeLayerDevice(maskW, maskH) as SkWebGpuDevice
        val maskClip = SkIRect.MakeWH(maskW, maskH)
        val maskCtm = ctm.postTranslate(-ml.toFloat(), -mt.toFloat())
        val whitePaint = paint.copy().apply {
            color = org.graphiks.math.SK_ColorWHITE
            blendMode = SkBlendMode.kSrc
            // Explicit `this.` -- avoid shadow with the outer
            // `val maskFilter` captured by the closure.
            this.maskFilter = null
            shader = null
            colorFilter = null
            pathEffect = null
        }
        // The child device's intermediate starts cleared to the
        // background (white). We need a transparent base so the shape
        // mask reads (0,0,0,0) outside the path -- a kClear-fill of
        // the layer's bounds with a transparent paint precedes the
        // shape rasterisation. SkBlendMode.kClear writes (0,0,0,0)
        // regardless of the paint colour.
        val clearPaint = SkPaint().apply { blendMode = SkBlendMode.kClear }
        maskDevice.drawRect(
            SkRect.MakeLTRB(0f, 0f, maskW.toFloat(), maskH.toFloat()),
            maskClip,
            clearPaint,
        )
        maskDevice.drawPath(path, maskCtm, maskClip, whitePaint)
        maskDevice.flushDrawsOnly()

        // Allocate the per-draw scratch H-pass texture.
        val scratchH = context.device.createTexture(
            TextureDescriptor(
                size = Extent3D(width = maskW.toUInt(), height = maskH.toUInt()),
                format = intermediateFormat,
                usage = GPUTextureUsage.RenderAttachment or GPUTextureUsage.TextureBinding,
                label = "SkWebGpuDevice.blurScratchH",
            ),
        )
        val scratchHView = scratchH.createView()

        // Scissor for the V pass : the union (mr-ml) x (mb-mt) region
        // on the parent intermediate, already clipped to clip + viewport.
        val scissor = intArrayOf(ml, mt, maskW, maskH)

        // Paint colour : extract premul RGBA. The shape mask is white-
        // premul, so the final premul output is paintColor * coverage.
        val color = paint.color
        val ca = SkColorGetA(color) / 255f
        val cr = SkColorGetR(color) / 255f * ca
        val cg = SkColorGetG(color) / 255f * ca
        val cb = SkColorGetB(color) / 255f * ca

        // Style ordinal : matches the SkBlurStyle declaration order
        // (kNormal = 0, kSolid = 1, kOuter = 2, kInner = 3). The V-pass
        // shader branches on this value to combine B and M.
        val styleOrdinal = when (maskFilter.style) {
            SkBlurStyle.kNormal -> 0
            SkBlurStyle.kSolid -> 1
            SkBlurStyle.kOuter -> 2
            SkBlurStyle.kInner -> 3
        }

        pending.add(
            BlurredPathDraw(
                shapeMaskDevice = maskDevice,
                shapeMaskView = maskDevice.intermediateView,
                scratchHTexture = scratchH,
                scratchHView = scratchHView,
                srcWidth = maskW, srcHeight = maskH,
                dstOriginX = ml, dstOriginY = mt,
                scissor = scissor,
                kernel = kernel,
                radius = radius,
                paintR = cr, paintG = cg, paintB = cb, paintA = ca,
                blurStyleOrdinal = styleOrdinal,
                r = 1f, g = 1f, b = 1f, a = 1f,
                mode = paint.blendMode,
            ),
        )
        return true
    }

    /**
     * Phase MaskFilter-blur-shaded -- shaded-paint variant of
     * [drawPathWithBlurMaskFilterIfApplicable]. The path carries a
     * [SkShader] (gradient or bitmap shader), so the unblurred shape
     * is rasterised onto a child layer device with the FULL paint
     * (shader + colourFilter + paint colour modulation), producing
     * RGBA premul pixels rather than a white-tinted alpha-only mask.
     * The blur convolution then runs on RGBA throughout, and the V
     * pass applies the [SkBlurStyle] combine per-RGBA-channel using
     * the original shaded layer as both the SrcOver "src" (for kSolid)
     * and the sharp coverage mask (M = A.a, for kOuter / kInner).
     *
     * Bounds + radius + kernel + scissor computation mirrors the
     * solid-paint path verbatim ; only the child render and the
     * dispatch [BlurredShadedPathDraw] differ. The child paint strips
     * the maskFilter (so the shaded render doesn't recurse) but
     * retains shader / colourFilter / pathEffect / blendMode (the
     * shaded layer reproduces the unblurred shape with its full paint
     * applied). The blend mode on the child render is forced to
     * kSrcOver onto the transparent layer base -- so per-pixel alpha
     * accumulates with the shader output, matching what the upstream
     * raster does for a shaded shape on a freshly-cleared mask buffer.
     */
    private fun drawPathWithShadedBlurMaskFilter(
        path: SkPath,
        ctm: SkMatrix,
        clip: SkIRect,
        paint: SkPaint,
        rawMaskFilter: SkBlurMaskFilter,
    ): Boolean {
        // Apply the CTM-scale rescale if the filter ignores the CTM
        // (Phase R1-C contract -- mirrors SkBitmapDevice).
        val scale = ctm.getMaxScale().coerceAtLeast(1f)
        val maskFilter = (if (!rawMaskFilter.respectCTM) {
            rawMaskFilter.withCtmScale(scale) as? SkBlurMaskFilter ?: return false
        } else {
            rawMaskFilter
        })

        val sigma = maskFilter.sigma
        if (!sigma.isFinite() || sigma <= 0f) return false

        // Clamp the radius to the shader's MAX_BLUR_RADIUS so the
        // uniform layout stays fixed. The kernel is renormalised after
        // the clamp -- same shape as the solid variant.
        val unboundedRadius = ceil(3.0 * sigma).toInt().coerceAtLeast(1)
        val radius = unboundedRadius.coerceAtMost(MAX_BLUR_RADIUS)
        val kernel = buildSymmetricGaussianHalfKernel(sigma, radius)

        // Compute device-space bounds of the path under the CTM,
        // expand by radius + 1 px safety, intersect with clip + viewport.
        val srcBounds = path.computeBounds()
        val devBounds = ctm.mapRect(srcBounds)
        val strokeExpand = if (paint.style != SkPaint.Style.kFill_Style) {
            (paint.strokeWidth * scale * 0.5f) + 1f
        } else 1f
        var ml = floor(devBounds.left - strokeExpand).toInt() - radius
        var mt = floor(devBounds.top - strokeExpand).toInt() - radius
        var mr = ceil(devBounds.right + strokeExpand).toInt() + radius
        var mb = ceil(devBounds.bottom + strokeExpand).toInt() + radius
        ml = maxOf(ml, clip.left, 0)
        mt = maxOf(mt, clip.top, 0)
        mr = minOf(mr, clip.right, width)
        mb = minOf(mb, clip.bottom, height)
        val maskW = mr - ml
        val maskH = mb - mt
        if (maskW <= 0 || maskH <= 0) return true  // Fully clipped -- nothing to draw.

        // Render the shaded shape into a child layer device. The paint
        // here keeps shader + colourFilter + pathEffect (so the layer
        // carries the full unblurred shaded result), but drops the
        // maskFilter (no recursion) and forces kSrcOver onto the
        // transparent layer base so the shader output accumulates
        // straightforwardly. The layer device starts cleared to
        // transparent (set by [makeLayerDevice.setBackground(0)]).
        val shadedDevice = makeLayerDevice(maskW, maskH) as SkWebGpuDevice
        val shadedClip = SkIRect.MakeWH(maskW, maskH)
        val shadedCtm = ctm.postTranslate(-ml.toFloat(), -mt.toFloat())
        val shadedPaint = paint.copy().apply {
            // Strip the maskFilter so the child render doesn't recurse
            // back through this helper.
            this.maskFilter = null
            // Force kSrcOver so the shader output composites onto the
            // transparent layer base. The paint's original blend mode
            // is honoured by the V-pass composite onto the PARENT, not
            // by the shaded-layer render.
            blendMode = SkBlendMode.kSrcOver
        }
        // Explicit kClear-fill to ensure the layer base is fully
        // transparent before the shaded shape lands -- mirrors the
        // solid-paint path's preamble.
        val clearPaint = SkPaint().apply { blendMode = SkBlendMode.kClear }
        shadedDevice.drawRect(
            SkRect.MakeLTRB(0f, 0f, maskW.toFloat(), maskH.toFloat()),
            shadedClip,
            clearPaint,
        )
        shadedDevice.drawPath(path, shadedCtm, shadedClip, shadedPaint)
        shadedDevice.flushDrawsOnly()

        // Allocate the per-draw scratch H-pass texture.
        val scratchH = context.device.createTexture(
            TextureDescriptor(
                size = Extent3D(width = maskW.toUInt(), height = maskH.toUInt()),
                format = intermediateFormat,
                usage = GPUTextureUsage.RenderAttachment or GPUTextureUsage.TextureBinding,
                label = "SkWebGpuDevice.shadedBlurScratchH",
            ),
        )
        val scratchHView = scratchH.createView()

        val scissor = intArrayOf(ml, mt, maskW, maskH)

        val styleOrdinal = when (maskFilter.style) {
            SkBlurStyle.kNormal -> 0
            SkBlurStyle.kSolid -> 1
            SkBlurStyle.kOuter -> 2
            SkBlurStyle.kInner -> 3
        }

        pending.add(
            BlurredShadedPathDraw(
                shadedLayerDevice = shadedDevice,
                shadedLayerView = shadedDevice.intermediateView,
                scratchHTexture = scratchH,
                scratchHView = scratchHView,
                srcWidth = maskW, srcHeight = maskH,
                dstOriginX = ml, dstOriginY = mt,
                scissor = scissor,
                kernel = kernel,
                radius = radius,
                blurStyleOrdinal = styleOrdinal,
                r = 1f, g = 1f, b = 1f, a = 1f,
                mode = paint.blendMode,
            ),
        )
        return true
    }

    /**
     * Phase MaskFilter-blur -- pre-compute the symmetric half of a 1-D
     * Gaussian kernel with the given [sigma] and half-width [radius].
     * Returns a [FloatArray] of length 1 + radius : `out[0]` is the
     * centre tap, `out[k]` (k > 0) is the weight at off-centre offsets
     * -k and +k. The kernel is renormalised against its full-width
     * mass `out[0] + 2 * (out[1] + ... + out[radius])` so the shader's
     * weighted sum integrates to 1.0 on uniform input.
     */
    private fun buildSymmetricGaussianHalfKernel(sigma: Float, radius: Int): FloatArray {
        val half = FloatArray(1 + radius)
        val twoSigmaSq = 2.0 * sigma * sigma
        var mass = 0.0
        for (k in 0..radius) {
            val v = exp(-(k * k).toDouble() / twoSigmaSq).toFloat()
            half[k] = v
            mass += if (k == 0) v.toDouble() else 2.0 * v.toDouble()
        }
        val inv = if (mass > 0.0) (1.0 / mass).toFloat() else 1f
        for (k in 0..radius) half[k] = half[k] * inv
        return half
    }

    override fun drawRect(rect: SkRect, clip: SkIRect, paint: SkPaint) {
        // Phase MaskFilter-blur -- intercept `paint.maskFilter is
        // SkBlurMaskFilter` (kNormal) and route through the offscreen-mask
        // + Gaussian-blur pipeline. The rect is already in device space
        // (drawRect convention) ; we wrap it into an SkPath.Rect and
        // dispatch through the shared helper, which renders a white-tint
        // rect into a child layer device then composites the blurred
        // mask onto this device's intermediate.
        if (drawPathWithBlurMaskFilterIfApplicable(
                SkPath.Rect(rect), SkMatrix.Identity, clip, paint)) return
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

        // G2.x -- pack the analytic clip shape (if any) into the
        // per-draw uniform. Circle / oval / rrect / rect all reduce to
        // the rrect representation : the shader's `rrect_cov` handles
        // uniform corners for all of them. Plain rects pass `rx = ry =
        // 0`, which makes `rrect_cov` collapse to the conventional axis-
        // aligned coverage (and is redundant with the integer scissor
        // anyway -- we skip emitting CLIP_KIND_RRECT in that case).
        val (clipKind, clipBounds, clipRx, clipRy) = packClipShape(activeClipShape)
        // Phase G-direct-colorFilter -- detect `paint.colorFilter` and
        // pack it into the 6-vec4f payload consumed by
        // `solid_color.wgsl`. Reuses [packLayerCompositeColorFilter]
        // verbatim -- both shaders share the (kind, mode, 4 matrix
        // rows, bias) layout. Unsupported variants (Compose / Lerp /
        // Table / sRGB-gamma / working-CS wrapper) yield the all-zero
        // identity payload and the filter is silently dropped, matching
        // the saveLayer behaviour from #568.
        val cf = paint.colorFilter
        val colorFilterPacked = if (cf == null) ZERO_COLOR_FILTER_24
                                else packLayerCompositeColorFilter(cf)
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
                clipKind = clipKind,
                clipShapeBounds = clipBounds,
                clipShapeRx = clipRx,
                clipShapeRy = clipRy,
                colorFilterPacked = colorFilterPacked,
            ),
        )
    }

    /**
     * G2.x -- collapse an [SkClipShape] (circle / oval / uniform-corner
     * rrect / rect) into the rrect parameterisation expected by
     * `solid_color.wgsl`'s `rrect_cov` helper. Returns `(kind, bounds,
     * rx, ry)` where `kind` is either [CLIP_KIND_NONE] (no shape clip ;
     * the shader skips the modulation step) or [CLIP_KIND_RRECT] (the
     * shader runs the rrect coverage formula). [SkClipShape.Rect] also
     * collapses to `CLIP_KIND_NONE` -- the integer scissor already
     * tightens the draw to the rect bounds, no need to pay the rrect-
     * coverage cost in the shader.
     */
    private fun packClipShape(
        shape: SkClipShape?,
    ): ClipShapePack = when (shape) {
        null, is SkClipShape.Rect -> ClipShapePack(
            kind = CLIP_KIND_NONE,
            bounds = ZERO_RECT4,
            rx = 0f,
            ry = 0f,
        )
        is SkClipShape.Circle -> ClipShapePack(
            kind = CLIP_KIND_RRECT,
            bounds = floatArrayOf(
                shape.cx - shape.r, shape.cy - shape.r,
                shape.cx + shape.r, shape.cy + shape.r,
            ),
            rx = shape.r,
            ry = shape.r,
        )
        is SkClipShape.Oval -> ClipShapePack(
            kind = CLIP_KIND_RRECT,
            bounds = floatArrayOf(
                shape.bounds.left, shape.bounds.top,
                shape.bounds.right, shape.bounds.bottom,
            ),
            rx = shape.bounds.width() * 0.5f,
            ry = shape.bounds.height() * 0.5f,
        )
        is SkClipShape.RRect -> ClipShapePack(
            kind = CLIP_KIND_RRECT,
            bounds = floatArrayOf(
                shape.bounds.left, shape.bounds.top,
                shape.bounds.right, shape.bounds.bottom,
            ),
            rx = shape.rx,
            ry = shape.ry,
        )
    }

    /**
     * Tuple result of [packClipShape]. A simple data class instead of
     * `Pair<Float, FloatArray>` plus two more floats for readability at
     * the [drawFillRect] call site.
     */
    private data class ClipShapePack(
        val kind: Float,
        val bounds: FloatArray,
        val rx: Float,
        val ry: Float,
    ) {
        // Default data-class equals/hashCode would compare bounds by
        // reference. We don't actually need value equality on this
        // helper -- override to silence the linter and keep semantics
        // explicit.
        override fun equals(other: Any?): Boolean = this === other
        override fun hashCode(): Int = System.identityHashCode(this)
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
                clipShape = activeClipShape,
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
                clipShape = activeClipShape,
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
                clipShape = activeClipShape,
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
                clipShape = activeClipShape,
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
                clipShape = activeClipShape,
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
                clipShape = activeClipShape,
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
        // G2.x -- pick up the active analytical clip shape so AA strokes
        // (which emit a RectDraw directly, bypassing drawFillRect) also
        // get masked against a curved clipPath. Mirrors drawFillRect.
        val (clipKind, clipBounds, clipRx, clipRy) = packClipShape(activeClipShape)
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
                clipKind = clipKind,
                clipShapeBounds = clipBounds,
                clipShapeRx = clipRx,
                clipShapeRy = clipRy,
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
        // H5 -- PathEffect dispatch. The canonical Skia pipeline for paths
        // is `path -> pathEffect -> stroker -> maskFilter -> colorFilter
        // -> blend` (mirrored on CPU at SkBitmapDevice.drawPath:1092). The
        // GPU device silently dropped `paint.pathEffect` before this slice ;
        // we now route through [SkPathEffect.filterPath] before the rest
        // of the dispatch runs.
        //
        // Scope :
        //   - [SkDashPathEffect] -- supported. The dash effect decomposes
        //     the input path into a sequence of `moveTo + lineTo` pairs
        //     (one per "on" interval) ; we then recurse with a paint copy
        //     whose `pathEffect = null`, letting the existing stroker /
        //     fill machinery thicken each dash segment normally.
        //   - All other [SkPathEffect] subtypes (CornerPathEffect,
        //     DiscretePathEffect, ComposePathEffect, SumPathEffect,
        //     Path1D/2D path effects) -- deferred. Throws a clear error
        //     so callers learn at draw time rather than silently mis-render.
        //
        // Empty / null filter result follows the CPU contract exactly :
        //   - `filterPath() == null` -- passthrough (use original path).
        //   - `filterPath()` returns an empty path -- draw nothing (unless
        //     fillType is inverse, which means "fill outside the empty
        //     path" i.e. the whole clip).
        val pathEffect = paint.pathEffect
        if (pathEffect != null) {
            when (pathEffect) {
                is SkDashPathEffect -> {
                    val effectivePath = pathEffect.filterPath(path, ctm) ?: path
                    if (effectivePath !== path &&
                        effectivePath.isEmpty() &&
                        !effectivePath.fillType.isInverse()
                    ) return
                    val paintNoEffect = paint.copy().apply { this.pathEffect = null }
                    drawPath(effectivePath, ctm, clip, paintNoEffect)
                    return
                }
                else -> error(
                    "SkWebGpuDevice (H5): paint.pathEffect of type " +
                        "${pathEffect::class.simpleName} is not supported. " +
                        "Only SkDashPathEffect is in scope today ; " +
                        "SkCornerPathEffect / SkDiscretePathEffect / " +
                        "SkComposePathEffect / SkSumPathEffect / 1D / 2D " +
                        "path effects are deferred to a follow-up slice.",
                )
            }
        }
        // Phase MaskFilter-blur -- `paint.maskFilter is SkBlurMaskFilter`
        // (kNormal) on `drawPath` routes through the offscreen-mask + blur
        // pipeline. Hard scope :
        //   - kNormal style only (kSolid / kOuter / kInner deferred),
        //   - axis-aligned CTM (rotated / skewed -> drop blur, fall back
        //     to no-blur fill so the rest of the paint still draws),
        //   - paint.shader == null (shaded blur is out of scope ; CPU
        //     raster has the same limitation, see
        //     SkBitmapDevice.drawPathWithMaskFilter).
        // Non-matching configurations fall through to the existing fill
        // machinery with the maskFilter silently dropped, matching the
        // pre-slice GPU behaviour.
        if (drawPathWithBlurMaskFilterIfApplicable(path, ctm, clip, paint)) return
        // G2.x (closing slice) -- every drawPath dispatch now honours
        // the analytical clip shape. The bitmap-shader pipelines (rect
        // fast path + non-rect AA), the 8 gradient pipelines (linear /
        // radial / sweep / conical family x rect path + non-rect AA),
        // and now the solid-colour polygon / stencil-cover / AA polygon
        // pipelines all carry `clipShapeBounds` + `clipShapeRadiiKind`
        // in their per-draw uniform. The old `requireClipShapeHonoured`
        // throw is dead code on this entry point (kept as a defensive
        // guard in case a future dispatch path bypasses the polygon /
        // gradient / bitmap arms and lands somewhere unexpected).
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
        // G5.2 / G5.2.2 -- `paint.shader is SkBitmapShader` fill of an
        // axis-aligned-in-device-space rect. Reuses the G5.1 / G5.1.1
        // drawImageRect pipeline (sampler cache, pipeline cache,
        // bitmap_shader.wgsl) but now drives the per-fragment image-
        // coord lookup through the 2x3 device-to-image affine carried
        // in the uniform (G5.2.2). The axis-aligned subset still rounds
        // out to the same pixels as before (the host derives the affine
        // from `src/dst` ratios for drawImage callers, and from
        // `(ctm * localMatrix)^-1` for shader callers ; both reduce to
        // the same matrix when the inputs are axis-aligned).
        //
        // Hard scope :
        //   - rect routing only (`path.isRect() != null`),
        //   - axis-aligned CTM (otherwise the integer scissor on the
        //     fullscreen-triangle would over-paint fragments outside
        //     the rotated rect bounds ; rotated-CTM rect paths fall
        //     through to the AA stencil-cover non-rect dispatch which
        //     bounds the painted region exactly).
        //   - rotated / skewed localMatrix allowed (G5.2.2 widening :
        //     the shader's affine handles it ; the rect is still
        //     axis-aligned in device space so the scissor is exact).
        //   - filter / tile / blend support inherited from G5.1.1.
        // The dispatch sits AFTER the gradient gates (gradients win
        // when both are valid ; in practice a paint carries one
        // shader, not two).
        if (shader is SkBitmapShader &&
            paint.style == SkPaint.Style.kFill_Style &&
            ctm.isAxisAligned
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

        // G5.2.1 / G5.2.2 / G5.2.3 -- bitmap shader on a non-rect path
        // (or rotated-CTM rect, which is rejected by the axis-aligned
        // gate of [drawBitmapShaderFillRect] and lands here as a 4-vertex
        // convex polygon). We route every remaining bitmap-shader fill
        // through the dedicated stencil-and-cover pipeline. The CTM +
        // localMatrix can be any invertible affine (G5.2.2 widening :
        // the shader applies the 2x3 device-to-image affine directly,
        // so rotated / skewed combinations work without a separate
        // code path).
        //
        // G5.2.3 -- non-AA paths now route through the same pipeline.
        // Setting `edgeCount = 0` makes `minSegmentDistance` return a
        // large sentinel, so `inside` coverage saturates to 1.0 and
        // `outside` coverage collapses to 0.0 -- a sharp stencil-bound
        // fill with no AA falloff. This unlocks `RepeatedBitmapGM` and
        // any other GM that draws a bitmap-shader fill under a rotated
        // CTM with `paint.isAntiAlias = false` (the rect fast path's
        // axis-aligned gate would otherwise drop the shader entirely
        // and paint a solid-colour fallback -- see
        // `RepeatedBitmapCrossBackendTest`).
        val bitmapShaderForPath: SkBitmapShader? =
            if (shader is SkBitmapShader) shader
            else null
        var bitmapPayload: BitmapShaderPayload? = null
        if (bitmapShaderForPath != null) {
            bitmapPayload = buildBitmapShaderPayload(bitmapShaderForPath, ctm, paint)
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
                            clipShape = activeClipShape,
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
                            clipShape = activeClipShape,
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
                            clipShape = activeClipShape,
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
                            clipShape = activeClipShape,
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
                            clipShape = activeClipShape,
                        ),
                    )
                    return
                }
                if (bitmapPayload != null) {
                    val (bClipKind, bClipBounds, bClipRx, bClipRy) =
                        packClipShape(activeClipShape)
                    pending.add(
                        bitmapPayload.toStencilCoverDraw(
                            stencilVerts = stencilTri,
                            coverVerts = coverTri,
                            edges = edges,
                            edgeCount = totalVerts,
                            scissor = scissor,
                            fillType = path.fillType,
                            mode = paint.blendMode,
                            clipKind = bClipKind,
                            clipShapeBounds = bClipBounds,
                            clipShapeRx = bClipRx,
                            clipShapeRy = bClipRy,
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
                        clipShape = activeClipShape,
                    ),
                )
                return
            }
            // G5.2.3 -- non-AA bitmap shader on a multi-contour path.
            // Reuse the AA stencil-cover bitmap pipeline with
            // `edgeCount = 0` (sentinel : the AA fragment shader's
            // `minSegmentDistance` returns 1e9, collapsing the inside
            // cover to coverage = 1.0 and the outside cover to 0.0 --
            // sharp stencil-bound fill, no AA falloff).
            if (bitmapPayload != null) {
                val (bClipKind, bClipBounds, bClipRx, bClipRy) =
                    packClipShape(activeClipShape)
                pending.add(
                    bitmapPayload.toStencilCoverDraw(
                        stencilVerts = stencilTri,
                        coverVerts = coverTri,
                        edges = FloatArray(MAX_AA_EDGES * 4),
                        edgeCount = 0,
                        scissor = scissor,
                        fillType = path.fillType,
                        mode = paint.blendMode,
                        clipKind = bClipKind,
                        clipShapeBounds = bClipBounds,
                        clipShapeRx = bClipRx,
                        clipShapeRy = bClipRy,
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
                    clipShape = activeClipShape,
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
                            clipShape = activeClipShape,
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
                            clipShape = activeClipShape,
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
                            clipShape = activeClipShape,
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
                            clipShape = activeClipShape,
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
                            clipShape = activeClipShape,
                        ),
                    )
                } else if (bitmapPayload != null) {
                    val (bClipKind, bClipBounds, bClipRx, bClipRy) =
                        packClipShape(activeClipShape)
                    pending.add(
                        bitmapPayload.toStencilCoverDraw(
                            stencilVerts = stencilTri,
                            coverVerts = coverTri,
                            edges = edges,
                            edgeCount = n,
                            scissor = scissor,
                            fillType = path.fillType,
                            mode = paint.blendMode,
                            clipKind = bClipKind,
                            clipShapeBounds = bClipBounds,
                            clipShapeRx = bClipRx,
                            clipShapeRy = bClipRy,
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
                            clipShape = activeClipShape,
                        ),
                    )
                }
            } else if (bitmapPayload != null) {
                // G5.2.3 -- non-AA bitmap shader on a concave / inverse
                // single-contour path. Same edgeCount = 0 sentinel as
                // the multi-contour arm : sharp stencil-bound fill via
                // the AA pipeline with the AA falloff collapsed.
                val (bClipKind, bClipBounds, bClipRx, bClipRy) =
                    packClipShape(activeClipShape)
                pending.add(
                    bitmapPayload.toStencilCoverDraw(
                        stencilVerts = stencilTri,
                        coverVerts = coverTri,
                        edges = FloatArray(MAX_AA_EDGES * 4),
                        edgeCount = 0,
                        scissor = scissor,
                        fillType = path.fillType,
                        mode = paint.blendMode,
                        clipKind = bClipKind,
                        clipShapeBounds = bClipBounds,
                        clipShapeRx = bClipRx,
                        clipShapeRy = bClipRy,
                    ),
                )
            } else {
                pending.add(
                    StencilCoverPolygonDraw(
                        stencilVerts = stencilTri,
                        coverVerts = coverTri,
                        scissor = scissor,
                        fillType = path.fillType,
                        r = rF, g = gF, b = bF, a = aF,
                        mode = paint.blendMode,
                        clipShape = activeClipShape,
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
                    clipShape = activeClipShape,
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
                    clipShape = activeClipShape,
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
                    clipShape = activeClipShape,
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
                    clipShape = activeClipShape,
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
                    clipShape = activeClipShape,
                ),
            )
            return
        }
        // G5.2.1 -- bitmap shader on a convex path. Mirror of the
        // gradient arms above ; detour through stencil-and-cover with
        // the dedicated bitmap-shader cover pipeline. G5.2.3 -- non-AA
        // paths now route here too with `edgeCount = 0` (sharp
        // stencil-bound fill, no AA falloff) ; this is what unblocks
        // `RepeatedBitmapGM` (drawRect under a rotated CTM with
        // `paint.isAntiAlias = false`).
        if (n <= MAX_AA_EDGES && bitmapPayload != null) {
            val stencilTri = fanTessellateContours(devVerts, contourStarts)
            val coverTri = bboxTrianglesFor(devVerts, width, height)
            val edges = FloatArray(MAX_AA_EDGES * 4)
            val edgeCount: Int
            if (paint.isAntiAlias) {
                buildContourEdgeSegments(devVerts, contourStarts, edges)
                edgeCount = n
            } else {
                // edges left zero-filled ; `edgeCount = 0` tells the
                // fragment shader's `minSegmentDistance` to return a
                // 1e9 sentinel, collapsing the inside coverage to 1.0
                // and the outside coverage to 0.0.
                edgeCount = 0
            }
            val (bClipKind, bClipBounds, bClipRx, bClipRy) =
                packClipShape(activeClipShape)
            pending.add(
                bitmapPayload.toStencilCoverDraw(
                    stencilVerts = stencilTri,
                    coverVerts = coverTri,
                    edges = edges,
                    edgeCount = edgeCount,
                    scissor = scissor,
                    fillType = path.fillType,
                    mode = paint.blendMode,
                    clipKind = bClipKind,
                    clipShapeBounds = bClipBounds,
                    clipShapeRx = bClipRx,
                    clipShapeRy = bClipRy,
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
                    clipShape = activeClipShape,
                ),
            )
        } else {
            pending.add(
                PolygonDraw(
                    verts = tri,
                    scissor = scissor,
                    r = rF, g = gF, b = bF, a = aF,
                    mode = paint.blendMode,
                    clipShape = activeClipShape,
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
        // G2.x slice 2 -- bitmap-shader pipeline (`bitmap_shader.wgsl`)
        // now honours the analytical clip shape via the trailing two
        // vec4 slots ; the canvas-side aaClip throw stays out of the
        // way for curved clipPath. Rect-shape clip already falls
        // through the scissor.
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
        // G5.2.2 -- when non-null, override the axis-aligned affine that
        // would otherwise be derived from (src, devDst). Used by
        // [drawBitmapShaderFillRect] to ship the true inverse of
        // `ctm * localMatrix` for rotated / skewed bitmap shaders.
        affineOverrideRow0: FloatArray? = null,
        affineOverrideRow1: FloatArray? = null,
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
        val (csMode, csMatrix, csTfParams) = bitmapColorSpaceFor(image)

        // Paint colour scale : alpha (and future colour filter) multiplies
        // the sampled texel. Default = (1, 1, 1, 1) when paint is null.
        // For G5.1 only alpha is honoured ; colour filters / blenders /
        // shaders are G5.x follow-ups.
        val paintAlpha = (paint?.alpha ?: 0xFF) / 255f

        // G2.x slice 2 -- fold the analytical clip shape (if any) into
        // the per-draw uniform. Same shape as `drawFillRect` : circle /
        // oval / rrect / rect all reduce to the rrect representation.
        // Plain rect / no-shape clips skip the modulation (the integer
        // scissor already handles them).
        val (clipKind, clipBounds, clipRx, clipRy) = packClipShape(activeClipShape)

        // G5.2.2 -- derive the device-to-image affine from the axis-aligned
        // (src, devDst) pair the caller supplied. The fragment math used to
        // be `sx = src.l + (pos.x - dst.l) * (src.w / dst.w)` ; rewriting
        // as a 2x3 affine gives :
        //   row0 = (src.w / dst.w, 0, src.l - dst.l * src.w / dst.w)
        //   row1 = (0, src.h / dst.h, src.t - dst.t * src.h / dst.h)
        // The fragment now reads this affine instead of the rect pair so
        // [SkBitmapShader] callers with a rotated localMatrix can route
        // through the same pipeline -- they pass `affineOverrideRow*` with
        // the full inverse of `ctm * localMatrix` instead of falling back
        // to this axis-aligned derivation.
        val row0: FloatArray
        val row1: FloatArray
        if (affineOverrideRow0 != null && affineOverrideRow1 != null) {
            row0 = affineOverrideRow0
            row1 = affineOverrideRow1
        } else {
            val srcW = src.right - src.left
            val srcH = src.bottom - src.top
            val dstW = devDst.right - devDst.left
            val dstH = devDst.bottom - devDst.top
            val sxRow0 = srcW / dstW
            val syRow1 = srcH / dstH
            val txRow0 = src.left - devDst.left * sxRow0
            val tyRow1 = src.top - devDst.top * syRow1
            row0 = floatArrayOf(sxRow0, 0f, txRow0)
            row1 = floatArrayOf(0f, syRow1, tyRow1)
        }

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
                csTfParams = csTfParams,
                r = 1f, g = 1f, b = 1f, a = paintAlpha,
                mode = mode,
                clipKind = clipKind,
                clipShapeBounds = clipBounds,
                clipShapeRx = clipRx,
                clipShapeRy = clipRy,
                devToImageRow0 = row0,
                devToImageRow1 = row1,
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
        // G5.2.2 -- combined matrix `M = ctm * localMatrix` may be any
        // invertible affine (rotated / skewed allowed). Perspective and
        // singular factors bail to the solid-colour fallback ; the
        // gradient helpers handle degenerate matrices the same way.
        val combined = ctm.preConcat(shader.localMatrix)
        if (combined.hasPerspective()) return false
        val inv = combined.invert() ?: return false

        // Cover quad : the device-AABB of the rotated source-rect
        // corners under `ctm`. Mirrors how the gradient rect helpers
        // (`drawLinearGradientFillRect` & friends) compute their cover
        // rect from `ctm.mapXY(corner)` -- but we map all four corners
        // (not just two) because `ctm` may now rotate, so the rect
        // does not reduce to its diagonal pair.
        val (x0, y0) = ctm.mapXY(srcRect.left, srcRect.top)
        val (x1, y1) = ctm.mapXY(srcRect.right, srcRect.top)
        val (x2, y2) = ctm.mapXY(srcRect.left, srcRect.bottom)
        val (x3, y3) = ctm.mapXY(srcRect.right, srcRect.bottom)
        val devDst = SkRect.MakeLTRB(
            minOf(x0, x1, x2, x3), minOf(y0, y1, y2, y3),
            maxOf(x0, x1, x2, x3), maxOf(y0, y1, y2, y3),
        )

        // Image-space src bounds : the corners of `devDst` mapped
        // through `M^-1`. For an axis-aligned matrix this matches the
        // pre-G5.2.2 derivation byte-for-byte ; for rotated matrices
        // it's a loose AABB of the rotated image footprint -- the
        // shader honours the affine per fragment, so the `srcRect`
        // value is now purely diagnostic (the fragment math ignores
        // it). We keep computing a sensible value so the uniform
        // bytes stay deterministic.
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
            // G5.2.2 -- override the affine derived from src/dst (which
            // is axis-aligned by construction) with the true inverse of
            // `M = ctm * localMatrix`. The two match exactly when the
            // CTM and localMatrix are axis-aligned ; they diverge for
            // rotated / skewed inputs -- in which case the shader-supplied
            // affine is the correct one (the rect-derived affine is a
            // lossy axis-aligned approximation).
            affineOverrideRow0 = floatArrayOf(inv.sx, inv.kx, inv.tx),
            affineOverrideRow1 = floatArrayOf(inv.ky, inv.sy, inv.ty),
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
    /**
     * Phase G-saveLayer-fast -- encode every pending draw into [encoder]
     * targeting [intermediateView], and return the per-draw GPU resources
     * (uniform buffers / bind groups / vertex buffers) so the caller can
     * close them after submitting the encoder.
     *
     * Shared by [flush] (which adds a present pass + readback tail) and
     * [flushDrawsOnly] (which submits without the readback so a parent
     * device can sample this device's [intermediateTexture] in a later
     * command buffer -- the layer-composite path).
     *
     * Pending is consumed but not cleared here ; the caller clears
     * after closing the resources.
     */
    private fun encodePendingDrawsToIntermediate(
        encoder: io.ygdrasil.webgpu.GPUCommandEncoder,
    ): List<DrawResources> {
        val colorView = intermediateView

        // G-suivi (round 17 follow-up) -- drop pending draws whose vertex
        // arrays would produce a zero-size GPU buffer. WebGPU panics in
        // `setVertexBuffer` ("invalid size") on a 0-byte vertex binding,
        // and an empty triangle list means no fragments to render — silent
        // skip is the correct semantics (mirrors upstream Skia's behaviour
        // on degenerate paths). The producer side can emit empty
        // `stencilVerts` when `fanTessellateContours` returns an empty
        // array (e.g. multi-contour paths where every individual contour
        // has fewer than 3 vertices, as happens with the stroker output
        // of `FLT_EPSILON` / zero-extent rects in `StrokeRectGM`).
        // `coverVerts` is currently never empty (bbox / viewport quads are
        // always 12 floats), but the guard covers it for future-proofing.
        val survivors = pending.filterNot { it.producesEmptyVertexBuffer() }
        if (survivors.size != pending.size) {
            pending.clear()
            pending.addAll(survivors)
        }

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
                is LayerCompositeDraw -> buildLayerCompositeDrawResources(d)
                is BlurredPathDraw -> buildBlurredPathDrawResources(d)
                is BlurredShadedPathDraw -> buildBlurredShadedPathDrawResources(d)
                is BlurredLayerCompositeDraw -> buildBlurredLayerCompositeDrawResources(d)
                is DropShadowLayerCompositeDraw ->
                    buildDropShadowLayerCompositeDrawResources(d)
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
            if (d is BlurredShadedPathDraw) {
                // Phase MaskFilter-blur-shaded -- two render passes,
                // mirrors the solid-paint variant below but :
                //   - the H pass samples the SHADED layer (RGBA premul)
                //     instead of a white-tinted alpha-only shape mask,
                //   - the V pass combines the blurred RGBA B with the
                //     original shaded layer A per SkBlurStyle and
                //     blends onto the parent. No paint-colour fold (A
                //     already carries the final shader output).
                val hView = res.scratchHView!!
                encoder.beginRenderPass(
                    RenderPassDescriptor(
                        colorAttachments = listOf(
                            RenderPassColorAttachment(
                                view = hView,
                                loadOp = GPULoadOp.Clear,
                                clearValue = Color(0.0, 0.0, 0.0, 0.0),
                                storeOp = GPUStoreOp.Store,
                            ),
                        ),
                    ),
                ) {
                    setPipeline(blurShadedHorizontalPipeline)
                    setBindGroup(0u, res.bindGroup)
                    setScissorRect(
                        x = 0u, y = 0u,
                        width = d.srcWidth.toUInt(),
                        height = d.srcHeight.toUInt(),
                    )
                    draw(FULL_SCREEN_TRIANGLE_VERTEX_COUNT)
                    end()
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
                    setPipeline(blurShadedVerticalPipelineFor(d.mode))
                    setBindGroup(0u, res.secondaryBindGroup!!)
                    setScissorRect(
                        x = d.scissor[0].toUInt(),
                        y = d.scissor[1].toUInt(),
                        width = d.scissor[2].toUInt(),
                        height = d.scissor[3].toUInt(),
                    )
                    draw(FULL_SCREEN_TRIANGLE_VERTEX_COUNT)
                    end()
                }
                return@forEachIndexed
            }
            if (d is BlurredPathDraw) {
                // Phase MaskFilter-blur -- two render passes :
                //   1. H pass : sample shape mask, write to scratchH
                //      (cleared). No blend.
                //   2. V pass : sample scratchH, modulate by paint
                //      colour, blend onto the parent intermediate via
                //      the pipeline's blend state. The colour-attachment
                //      loadOp matches the rest of [flush] (Clear for the
                //      first draw, Load otherwise) so the composite
                //      stacks with prior draws.
                //
                // The H scratch texture is freshly allocated per draw
                // (see [drawPathWithBlurMaskFilterIfApplicable]) so the
                // Clear loadOp is the natural choice -- it gives the
                // scissored shader a zero-padded sandbox.
                val hView = res.scratchHView!!
                encoder.beginRenderPass(
                    RenderPassDescriptor(
                        colorAttachments = listOf(
                            RenderPassColorAttachment(
                                view = hView,
                                loadOp = GPULoadOp.Clear,
                                clearValue = Color(0.0, 0.0, 0.0, 0.0),
                                storeOp = GPUStoreOp.Store,
                            ),
                        ),
                    ),
                ) {
                    setPipeline(blurHorizontalPipeline)
                    setBindGroup(0u, res.bindGroup)
                    // H pass scissor : the full scratch extent. The
                    // shader's own bounds guard handles any leakage.
                    setScissorRect(
                        x = 0u, y = 0u,
                        width = d.srcWidth.toUInt(),
                        height = d.srcHeight.toUInt(),
                    )
                    draw(FULL_SCREEN_TRIANGLE_VERTEX_COUNT)
                    end()
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
                    setPipeline(blurVerticalPipelineFor(d.mode))
                    setBindGroup(0u, res.secondaryBindGroup!!)
                    setScissorRect(
                        x = d.scissor[0].toUInt(),
                        y = d.scissor[1].toUInt(),
                        width = d.scissor[2].toUInt(),
                        height = d.scissor[3].toUInt(),
                    )
                    draw(FULL_SCREEN_TRIANGLE_VERTEX_COUNT)
                    end()
                }
                return@forEachIndexed
            }
            if (d is BlurredLayerCompositeDraw) {
                // Phase G-saveLayer-imageFilter-blur -- three render
                // passes :
                //   1. H pass : sample layer texture (or pre-CF scratch
                //      when a pre-blur CF is present, see below), write
                //      to scratchH (cleared). No blend.
                //   2. V pass : sample scratchH, write to scratchV
                //      (cleared). No blend. Output is blurred premul
                //      RGBA, no paint-colour fold.
                //   3. Composite : sample scratchV, modulate by
                //      paintColor, apply colorFilter, blend onto the
                //      parent intermediate via the layer composite
                //      pipeline (same code path as the no-filter
                //      saveLayer).
                //
                // Phase G-saveLayer-imageFilter-compose -- when a pre-
                // blur ColorFilter is present (from a Compose chain),
                // an extra pass runs *before* the H pass : sample the
                // layer texture, apply the colour filter via
                // `layer_composite.wgsl` with kSrc blend, write to the
                // pre-CF scratch. The H pass then reads from the
                // pre-CF scratch (its bind group's binding 1 was wired
                // by [buildBlurredLayerCompositeDrawResources]).
                if (res.scratchPreCfView != null && res.quaternaryBindGroup != null) {
                    val preCfView = res.scratchPreCfView
                    encoder.beginRenderPass(
                        RenderPassDescriptor(
                            colorAttachments = listOf(
                                RenderPassColorAttachment(
                                    view = preCfView,
                                    loadOp = GPULoadOp.Clear,
                                    clearValue = Color(0.0, 0.0, 0.0, 0.0),
                                    storeOp = GPUStoreOp.Store,
                                ),
                            ),
                        ),
                    ) {
                        setPipeline(layerCompositePipelineFor(SkBlendMode.kSrc))
                        setBindGroup(0u, res.quaternaryBindGroup)
                        setScissorRect(
                            x = 0u, y = 0u,
                            width = d.layerWidth.toUInt(),
                            height = d.layerHeight.toUInt(),
                        )
                        draw(FULL_SCREEN_TRIANGLE_VERTEX_COUNT)
                        end()
                    }
                }
                val hView = res.scratchHView!!
                val vView = res.scratchVView!!
                encoder.beginRenderPass(
                    RenderPassDescriptor(
                        colorAttachments = listOf(
                            RenderPassColorAttachment(
                                view = hView,
                                loadOp = GPULoadOp.Clear,
                                clearValue = Color(0.0, 0.0, 0.0, 0.0),
                                storeOp = GPUStoreOp.Store,
                            ),
                        ),
                    ),
                ) {
                    setPipeline(blurImageFilterHorizontalPipeline)
                    setBindGroup(0u, res.bindGroup)
                    setScissorRect(
                        x = 0u, y = 0u,
                        width = d.layerWidth.toUInt(),
                        height = d.layerHeight.toUInt(),
                    )
                    draw(FULL_SCREEN_TRIANGLE_VERTEX_COUNT)
                    end()
                }
                encoder.beginRenderPass(
                    RenderPassDescriptor(
                        colorAttachments = listOf(
                            RenderPassColorAttachment(
                                view = vView,
                                loadOp = GPULoadOp.Clear,
                                clearValue = Color(0.0, 0.0, 0.0, 0.0),
                                storeOp = GPUStoreOp.Store,
                            ),
                        ),
                    ),
                ) {
                    setPipeline(blurImageFilterVerticalPipeline)
                    setBindGroup(0u, res.secondaryBindGroup!!)
                    setScissorRect(
                        x = 0u, y = 0u,
                        width = d.layerWidth.toUInt(),
                        height = d.layerHeight.toUInt(),
                    )
                    draw(FULL_SCREEN_TRIANGLE_VERTEX_COUNT)
                    end()
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
                    setPipeline(layerCompositePipelineFor(d.mode))
                    setBindGroup(0u, res.tertiaryBindGroup!!)
                    setScissorRect(
                        x = d.scissor[0].toUInt(),
                        y = d.scissor[1].toUInt(),
                        width = d.scissor[2].toUInt(),
                        height = d.scissor[3].toUInt(),
                    )
                    draw(FULL_SCREEN_TRIANGLE_VERTEX_COUNT)
                    end()
                }
                return@forEachIndexed
            }
            if (d is DropShadowLayerCompositeDraw) {
                // Phase G-saveLayer-imageFilter-dropshadow -- four render
                // passes :
                //   1. H pass : sample layer texture, write to scratchH
                //      (cleared). No blend. Same shader as the plain
                //      blur ImageFilter -- the colorize-to-shadow step
                //      runs at pass 3 via the colour-filter slot.
                //   2. V pass : sample scratchH, write to scratchV
                //      (cleared). No blend. Output is the layer's
                //      premul RGBA blurred along both axes.
                //   3. Composite (shadow) : sample scratchV, run the
                //      packed `Blend(shadowColor, kSrcIn)` colour
                //      filter (kind = 1, mode = 5) which masks the
                //      blurred RGB out and keeps the alpha * shadowColor
                //      product, then blend onto the parent intermediate
                //      via the layer-composite pipeline at the offset
                //      origin.
                //   4. Composite (original) : sample the layer texture
                //      directly, modulate by paintColor, apply the
                //      paint's regular colour filter, blend onto the
                //      parent intermediate at the original origin.
                //      Lands the layer content ON TOP of the shadow.
                val hView = res.scratchHView!!
                val vView = res.scratchVView!!
                encoder.beginRenderPass(
                    RenderPassDescriptor(
                        colorAttachments = listOf(
                            RenderPassColorAttachment(
                                view = hView,
                                loadOp = GPULoadOp.Clear,
                                clearValue = Color(0.0, 0.0, 0.0, 0.0),
                                storeOp = GPUStoreOp.Store,
                            ),
                        ),
                    ),
                ) {
                    setPipeline(blurImageFilterHorizontalPipeline)
                    setBindGroup(0u, res.bindGroup)
                    setScissorRect(
                        x = 0u, y = 0u,
                        width = d.layerWidth.toUInt(),
                        height = d.layerHeight.toUInt(),
                    )
                    draw(FULL_SCREEN_TRIANGLE_VERTEX_COUNT)
                    end()
                }
                encoder.beginRenderPass(
                    RenderPassDescriptor(
                        colorAttachments = listOf(
                            RenderPassColorAttachment(
                                view = vView,
                                loadOp = GPULoadOp.Clear,
                                clearValue = Color(0.0, 0.0, 0.0, 0.0),
                                storeOp = GPUStoreOp.Store,
                            ),
                        ),
                    ),
                ) {
                    setPipeline(blurImageFilterVerticalPipeline)
                    setBindGroup(0u, res.secondaryBindGroup!!)
                    setScissorRect(
                        x = 0u, y = 0u,
                        width = d.layerWidth.toUInt(),
                        height = d.layerHeight.toUInt(),
                    )
                    draw(FULL_SCREEN_TRIANGLE_VERTEX_COUNT)
                    end()
                }
                // Shadow composite : skip when the scissor is empty
                // (shadow clipped entirely outside parent bounds).
                if (d.shadowScissor[2] > 0 && d.shadowScissor[3] > 0) {
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
                        setPipeline(layerCompositePipelineFor(d.mode))
                        setBindGroup(0u, res.tertiaryBindGroup!!)
                        setScissorRect(
                            x = d.shadowScissor[0].toUInt(),
                            y = d.shadowScissor[1].toUInt(),
                            width = d.shadowScissor[2].toUInt(),
                            height = d.shadowScissor[3].toUInt(),
                        )
                        draw(FULL_SCREEN_TRIANGLE_VERTEX_COUNT)
                        end()
                    }
                }
                // Original composite : the second composite pass lands
                // the layer content on top of the shadow. The shadow
                // pass above may have cleared the first attachment with
                // a Clear loadOp on the first draw -- the dispatch loop
                // forces Load on subsequent passes anyway, but if the
                // shadow pass was skipped we still need to honour the
                // outer loadOp here, so we pick the right one based on
                // whether the shadow ran.
                val originalLoadOp =
                    if (d.shadowScissor[2] > 0 && d.shadowScissor[3] > 0) GPULoadOp.Load
                    else loadOp
                if (d.originalScissor[2] > 0 && d.originalScissor[3] > 0) {
                    encoder.beginRenderPass(
                        RenderPassDescriptor(
                            colorAttachments = listOf(
                                RenderPassColorAttachment(
                                    view = colorView,
                                    loadOp = originalLoadOp,
                                    clearValue = background,
                                    storeOp = GPUStoreOp.Store,
                                ),
                            ),
                        ),
                    ) {
                        setPipeline(layerCompositePipelineFor(d.mode))
                        setBindGroup(0u, res.quaternaryBindGroup!!)
                        setScissorRect(
                            x = d.originalScissor[0].toUInt(),
                            y = d.originalScissor[1].toUInt(),
                            width = d.originalScissor[2].toUInt(),
                            height = d.originalScissor[3].toUInt(),
                        )
                        draw(FULL_SCREEN_TRIANGLE_VERTEX_COUNT)
                        end()
                    }
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
                    is LayerCompositeDraw -> {
                        // Phase G-saveLayer-fast -- direct GPU-to-GPU
                        // composite : fullscreen-quad render pass that
                        // samples the child device's intermediate via
                        // `textureLoad` and writes onto our intermediate.
                        // Blend mode honoured by the pipeline's blend
                        // state ; alpha + colour modulation fold into
                        // the per-draw `paintColor` uniform.
                        setPipeline(layerCompositePipelineFor(d.mode))
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
                    is BlurredPathDraw -> { /* handled above */ }
                    is BlurredShadedPathDraw -> { /* handled above */ }
                    is BlurredLayerCompositeDraw -> { /* handled above */ }
                    is DropShadowLayerCompositeDraw -> { /* handled above */ }
                }
                end()
            }
        }

        return perDrawResources
    }

    /**
     * Phase G-saveLayer-fast -- close out per-draw GPU resources after
     * the command buffer they were bound into has been submitted.
     * Shared by [flush] and [flushDrawsOnly].
     */
    private fun closeDrawResources(perDrawResources: List<DrawResources>) {
        perDrawResources.forEach {
            it.uniform.close()
            it.vertexBuffer?.close()
            it.coverVertexBuffer?.close()
            it.secondaryUniform?.close()
            // Phase MaskFilter-blur -- release the H-pass scratch
            // texture + view, then the child layer device that rendered
            // the shape mask. The view must close before the texture ;
            // the layer device's close() releases its intermediate
            // texture, depth-stencil, pipeline caches, etc.
            it.scratchHView?.close()
            it.scratchHTexture?.close()
            it.layerDevice?.close()
            // Phase G-saveLayer-imageFilter-blur -- release the V-pass
            // scratch and the composite-pass uniform. The layer device
            // for this path is owned by SkCanvas, not by this draw, so
            // we do *not* close it here.
            it.tertiaryUniform?.close()
            it.scratchVView?.close()
            it.scratchVTexture?.close()
            // Phase G-saveLayer-imageFilter-dropshadow / -compose --
            // release the fourth-pass uniform (DropShadow's original-pass
            // composite OR Compose's pre-blur CF pass uniform ; the two
            // never coexist on the same draw) and, when present, the
            // pre-blur CF scratch texture + view (Compose only).
            it.quaternaryUniform?.close()
            it.scratchPreCfView?.close()
            it.scratchPreCfTexture?.close()
        }
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
        val perDrawResources = encodePendingDrawsToIntermediate(encoder)

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

        closeDrawResources(perDrawResources)
        pending.clear()
        pixels
    }

    /**
     * Phase G-saveLayer-fast -- drain pending draws onto
     * [intermediateTexture] and submit, **without** the present pass or
     * readback round-trip. Used by [compositeFrom] to flush a child
     * layer device before its texture is sampled by the parent's
     * subsequent composite render pass.
     *
     * After this call, the device's [intermediateTexture] holds the
     * fully-blended content of all pending draws (sRGB-coded premul,
     * matching the present-pass identity-copy output) and `pending` is
     * empty. The intermediate is readable as a sampled texture in any
     * subsequent command buffer queued onto [context]'s shared queue.
     */
    internal fun flushDrawsOnly() {
        val encoder = context.device.createCommandEncoder()
        val perDrawResources = encodePendingDrawsToIntermediate(encoder)
        context.queue.submit(listOf(encoder.finish()))
        closeDrawResources(perDrawResources)
        pending.clear()
    }

    /**
     * Per-draw GPU resources lifetime-managed by [flush] : the uniform
     * buffer + its bind group, plus optional vertex buffers (polygon
     * draws use [vertexBuffer] ; stencil-and-cover draws additionally
     * use [coverVertexBuffer] for the cover-pass bbox quad).
     *
     * Phase MaskFilter-blur adds [secondaryUniform] / [secondaryBindGroup]
     * for the V pass of the separable Gaussian blur. The H pass uses
     * the primary [uniform] / [bindGroup] (binding the shape mask) ; the
     * V pass uses the secondaries (binding the H-pass scratch texture
     * and a different per-axis uniform). The secondary slots also carry
     * the per-draw child layer device + scratch H texture so [close]
     * can release them after the command buffer submission.
     */
    private data class DrawResources(
        val uniform: GPUBuffer,
        val bindGroup: io.ygdrasil.webgpu.GPUBindGroup,
        val vertexBuffer: GPUBuffer? = null,
        val coverVertexBuffer: GPUBuffer? = null,
        val secondaryUniform: GPUBuffer? = null,
        val secondaryBindGroup: io.ygdrasil.webgpu.GPUBindGroup? = null,
        // Phase MaskFilter-blur -- transient GPU objects to release
        // after the command buffer that consumed them has been
        // submitted. [scratchHTexture] is the H-pass scratch ;
        // [layerDevice] is the child SkWebGpuDevice that rendered the
        // shape mask. Both are owned by this DrawResources, not by the
        // PendingDraw (which holds *views*).
        val scratchHTexture: GPUTexture? = null,
        val scratchHView: GPUTextureView? = null,
        val layerDevice: SkWebGpuDevice? = null,
        // Phase G-saveLayer-imageFilter-blur -- a third uniform + bind
        // group for the final composite pass (consumed by
        // `layer_composite.wgsl`) plus the V-pass scratch texture that
        // the composite samples. The composite pass reads scratchV, so
        // both scratches need to outlive the command buffer ; we close
        // them after submit.
        val tertiaryUniform: GPUBuffer? = null,
        val tertiaryBindGroup: io.ygdrasil.webgpu.GPUBindGroup? = null,
        val scratchVTexture: GPUTexture? = null,
        val scratchVView: GPUTextureView? = null,
        // A fourth uniform + bind group, shared between two paths that
        // never coexist on the same draw :
        //  - Phase G-saveLayer-imageFilter-dropshadow : the original-pass
        //    composite (layer content drawn ON TOP of the shadow). Same
        //    byte layout as the tertiary (the shadow-pass composite) but
        //    different colour-filter + dst-origin payload, so we need a
        //    distinct GPU buffer.
        //  - Phase G-saveLayer-imageFilter-compose : the pre-blur
        //    ColorFilter pass. Bound by
        //    [buildBlurredLayerCompositeDrawResources] when the
        //    BlurredLayerCompositeDraw carries a pre-blur CF, else left
        //    null and the blur H pass reads from the raw layer view.
        val quaternaryUniform: GPUBuffer? = null,
        val quaternaryBindGroup: io.ygdrasil.webgpu.GPUBindGroup? = null,
        // Phase G-saveLayer-imageFilter-compose -- pre-blur CF scratch
        // texture + view. The blur H pass reads from this scratch when
        // present (instead of from the raw layer view). Released in
        // [closeDrawResources] alongside the other scratch textures.
        val scratchPreCfTexture: GPUTexture? = null,
        val scratchPreCfView: GPUTextureView? = null,
    )

    private fun buildRectDrawResources(d: RectDraw): DrawResources {
        val buf = context.device.createBuffer(
            BufferDescriptor(
                size = RECT_UNIFORM_SIZE,
                usage = GPUBufferUsage.Uniform or GPUBufferUsage.CopyDst,
                label = "SkWebGpuDevice.rectDraw",
            ),
        )
        // Layout : color (4) + outerBounds (4) + innerBounds (4) +
        //          clipShapeBounds (4) + clipShapeRadiiKind (4) +
        //          colorFilterKindMode (4) + colorFilterParam0..3 (4*4) +
        //          colorFilterBias (4) = 44 floats = 176 bytes.
        // Matches `Uniforms { color, outerBounds, innerBounds,
        // clipShapeBounds, clipShapeRadiiKind, colorFilterKindMode,
        // colorFilterParam0..3, colorFilterBias }` in solid_color.wgsl.
        // For draws without an analytical clip shape, `clipKind = 0`
        // skips the modulation step in the shader. For draws without a
        // colour filter, the trailing 6 vec4f are all zeros and the
        // shader's `colorFilterKindMode.x == 0` branch is a no-op.
        val packed = FloatArray(44)
        packed[0] = d.r; packed[1] = d.g; packed[2] = d.b; packed[3] = d.a
        packed[4] = d.ol; packed[5] = d.ot; packed[6] = d.or; packed[7] = d.ob
        packed[8] = d.il; packed[9] = d.it; packed[10] = d.ir; packed[11] = d.ib
        packed[12] = d.clipShapeBounds[0]; packed[13] = d.clipShapeBounds[1]
        packed[14] = d.clipShapeBounds[2]; packed[15] = d.clipShapeBounds[3]
        packed[16] = d.clipShapeRx; packed[17] = d.clipShapeRy
        packed[18] = d.clipKind; packed[19] = 0f
        // Phase G-direct-colorFilter -- 6 contiguous vec4f starting at
        // vec4f index 5 (offset 80 bytes = float index 20). When the
        // paint had no colour filter, [colorFilterPacked] is the shared
        // [ZERO_COLOR_FILTER_24] sentinel, so the trailing 24 floats
        // are zero and the shader's fast path stays warm.
        System.arraycopy(d.colorFilterPacked, 0, packed, 20, 24)
        context.queue.writeBuffer(buf, 0uL, ArrayBuffer.of(packed))
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
        // Layout : color (4 floats) + viewport (vec4 padded ; only x,y used)
        // + clipShapeBounds (vec4) + clipShapeRadiiKind (vec4). Matches
        // `Uniforms { color, viewport, clipShapeBounds, clipShapeRadiiKind }`
        // in solid_polygon.wgsl.
        val packed = FloatArray(16)
        packed[0] = d.r; packed[1] = d.g; packed[2] = d.b; packed[3] = d.a
        packed[4] = width.toFloat(); packed[5] = height.toFloat()
        packed[6] = 0f; packed[7] = 0f
        writeClipShape(packed, 8, d.clipShape)
        context.queue.writeBuffer(uniform, 0uL, ArrayBuffer.of(packed))
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
        // Same layout as buildPolygonDrawResources -- shared with
        // solid_polygon.wgsl. The stencil-write sub-pass discards its
        // fragment output (writeMask = None) so the trailing clip-shape
        // slots only matter to the cover sub-pass.
        val packed = FloatArray(16)
        packed[0] = d.r; packed[1] = d.g; packed[2] = d.b; packed[3] = d.a
        packed[4] = width.toFloat(); packed[5] = height.toFloat()
        packed[6] = 0f; packed[7] = 0f
        writeClipShape(packed, 8, d.clipShape)
        context.queue.writeBuffer(uniform, 0uL, ArrayBuffer.of(packed))
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
        //   offset    0 : color    (4 floats)
        //   offset   16 : viewport (4 floats, only x/y used)
        //   offset   32 : edgeCount (u32) + 3 u32 padding
        //   offset   48 : edges[MAX_AA_EDGES] (vec4 each)
        //   offset 4144 : clipShapeBounds (vec4) ; G2.x (closing slice)
        //   offset 4160 : clipShapeRadiiKind (vec4) ; G2.x (closing slice)
        // Pack the whole thing in one FloatArray ; reinterpret edgeCount's
        // bits as a float so `floatArrayOf` lays it out at the right
        // byte offset.
        val packed = FloatArray(12 + MAX_AA_EDGES * 4 + 8)
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
        // G2.x (closing slice) -- clip-shape payload at offset 4144.
        writeClipShape(packed, 12 + MAX_AA_EDGES * 4, d.clipShape)

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

    /**
     * G2.x -- write the analytical clip-shape (8 floats : `clipShapeBounds`
     * followed by `clipShapeRadiiKind`) at offset [base] in [out]. Mirrors
     * [packClipShape] : `null` and [SkClipShape.Rect] produce the
     * `clipKind = 0` sentinel (no shape clip ; the shader skips the
     * `rrect_cov` modulation), [SkClipShape.Circle] / [SkClipShape.Oval]
     * / [SkClipShape.RRect] all collapse to the rrect parameterisation
     * (bounds + uniform `(rx, ry)`).
     */
    private fun writeClipShape(out: FloatArray, base: Int, shape: SkClipShape?) {
        val (kind, bounds, rx, ry) = packClipShape(shape)
        out[base]     = bounds[0]
        out[base + 1] = bounds[1]
        out[base + 2] = bounds[2]
        out[base + 3] = bounds[3]
        out[base + 4] = rx
        out[base + 5] = ry
        out[base + 6] = kind
        out[base + 7] = 0f
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
        //   offset 560 : clipShapeBounds (vec4f -- l, t, r, b) ; G2.x
        //   offset 576 : clipShapeRadiiKind (vec4f -- rx, ry, clipKind, _) ; G2.x
        val packed = FloatArray(12 + MAX_GRADIENT_STOPS * 8 + 8)
        packed[0] = d.startX; packed[1] = d.startY
        packed[2] = d.endX;   packed[3] = d.endY
        packed[4] = width.toFloat(); packed[5] = height.toFloat()
        packed[6] = 0f; packed[7] = 0f
        packed[8] = Float.fromBits(d.stopCount)
        packed[9] = 0f; packed[10] = 0f; packed[11] = 0f
        System.arraycopy(d.stopPositions, 0, packed, 12, MAX_GRADIENT_STOPS * 4)
        System.arraycopy(d.stopColors, 0, packed, 12 + MAX_GRADIENT_STOPS * 4, MAX_GRADIENT_STOPS * 4)
        // G2.x -- clip-shape payload appended at offset 560.
        val clipBase = 12 + MAX_GRADIENT_STOPS * 8
        writeClipShape(packed, clipBase, d.clipShape)

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
        //   offset 560 : clipShapeBounds (vec4f) ; G2.x
        //   offset 576 : clipShapeRadiiKind (vec4f) ; G2.x
        val packed = FloatArray(12 + MAX_GRADIENT_STOPS * 8 + 8)
        packed[0] = width.toFloat(); packed[1] = height.toFloat()
        packed[2] = 0f; packed[3] = 0f
        packed[4] = d.centerX; packed[5] = d.centerY; packed[6] = d.radius; packed[7] = 0f
        packed[8] = Float.fromBits(d.stopCount)
        packed[9] = 0f; packed[10] = 0f; packed[11] = 0f
        System.arraycopy(d.stopPositions, 0, packed, 12, MAX_GRADIENT_STOPS * 4)
        System.arraycopy(d.stopColors, 0, packed, 12 + MAX_GRADIENT_STOPS * 4, MAX_GRADIENT_STOPS * 4)
        writeClipShape(packed, 12 + MAX_GRADIENT_STOPS * 8, d.clipShape)

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
        //   offset 560 : clipShapeBounds (vec4f) ; G2.x
        //   offset 576 : clipShapeRadiiKind (vec4f) ; G2.x
        val packed = FloatArray(12 + MAX_GRADIENT_STOPS * 8 + 8)
        packed[0] = width.toFloat(); packed[1] = height.toFloat()
        packed[2] = 0f; packed[3] = 0f
        packed[4] = d.centerX; packed[5] = d.centerY
        packed[6] = d.tBias; packed[7] = d.tScale
        packed[8] = Float.fromBits(d.stopCount)
        packed[9] = 0f; packed[10] = 0f; packed[11] = 0f
        System.arraycopy(d.stopPositions, 0, packed, 12, MAX_GRADIENT_STOPS * 4)
        System.arraycopy(d.stopColors, 0, packed, 12 + MAX_GRADIENT_STOPS * 4, MAX_GRADIENT_STOPS * 4)
        writeClipShape(packed, 12 + MAX_GRADIENT_STOPS * 8, d.clipShape)

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
        //   offset 592 : clipShapeBounds (vec4f) ; G2.x
        //   offset 608 : clipShapeRadiiKind (vec4f) ; G2.x
        val packed = FloatArray(20 + MAX_GRADIENT_STOPS * 8 + 8)
        packed[0] = width.toFloat(); packed[1] = height.toFloat()
        packed[2] = 0f; packed[3] = 0f
        packed[4] = d.affine00; packed[5] = d.affine01; packed[6] = d.affine02; packed[7] = 0f
        packed[8] = d.affine10; packed[9] = d.affine11; packed[10] = d.affine12; packed[11] = 0f
        packed[12] = d.fP0; packed[13] = d.fP1; packed[14] = d.compensateFocal; packed[15] = d.unswap
        packed[16] = d.negateX; packed[17] = d.subCase
        packed[18] = Float.fromBits(d.stopCount); packed[19] = d.subCaseSign
        System.arraycopy(d.stopPositions, 0, packed, 20, MAX_GRADIENT_STOPS * 4)
        System.arraycopy(d.stopColors, 0, packed, 20 + MAX_GRADIENT_STOPS * 4, MAX_GRADIENT_STOPS * 4)
        writeClipShape(packed, 20 + MAX_GRADIENT_STOPS * 8, d.clipShape)

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
        //   offset 576 : clipShapeBounds (vec4f) ; G2.x
        //   offset 592 : clipShapeRadiiKind (vec4f) ; G2.x
        val packed = FloatArray(16 + MAX_GRADIENT_STOPS * 8 + 8)
        packed[0] = width.toFloat(); packed[1] = height.toFloat()
        packed[2] = 0f; packed[3] = 0f
        packed[4] = d.affine00; packed[5] = d.affine01; packed[6] = d.affine02; packed[7] = 0f
        packed[8] = d.affine10; packed[9] = d.affine11; packed[10] = d.affine12; packed[11] = 0f
        packed[12] = d.stripP0; packed[13] = 0f
        packed[14] = Float.fromBits(d.stopCount); packed[15] = 0f
        System.arraycopy(d.stopPositions, 0, packed, 16, MAX_GRADIENT_STOPS * 4)
        System.arraycopy(d.stopColors, 0, packed, 16 + MAX_GRADIENT_STOPS * 4, MAX_GRADIENT_STOPS * 4)
        writeClipShape(packed, 16 + MAX_GRADIENT_STOPS * 8, d.clipShape)

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
        //   offset 560 : clipShapeBounds (vec4f) ; G2.x
        //   offset 576 : clipShapeRadiiKind (vec4f) ; G2.x
        val packed = FloatArray(12 + MAX_GRADIENT_STOPS * 8 + 8)
        packed[0] = width.toFloat(); packed[1] = height.toFloat()
        packed[2] = 0f; packed[3] = 0f
        packed[4] = d.centerX; packed[5] = d.centerY
        packed[6] = d.startRadius; packed[7] = d.endRadius
        packed[8] = Float.fromBits(d.stopCount)
        packed[9] = 0f; packed[10] = 0f; packed[11] = 0f
        System.arraycopy(d.stopPositions, 0, packed, 12, MAX_GRADIENT_STOPS * 4)
        System.arraycopy(d.stopColors, 0, packed, 12 + MAX_GRADIENT_STOPS * 4, MAX_GRADIENT_STOPS * 4)
        writeClipShape(packed, 12 + MAX_GRADIENT_STOPS * 8, d.clipShape)

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
        //   offset   0 : srcRect            (vec4f -- l, t, r, b in image-pixel coords)
        //   offset  16 : dstRect            (vec4f -- l, t, r, b in device-pixel coords)
        //   offset  32 : imageSize          (vec4f -- w, h, tileX (bit-reinterp u32), tileY (bit-reinterp u32))
        //   offset  48 : paintColor         (vec4f -- premul tint, default (1, 1, 1, 1))
        //   offset  64 : csFlags            (vec4f -- .x = csMode bit-reinterp u32 ; G5.3)
        //   offset  80 : csMatrix           (mat3x3<f32>, std140 padded 3 * vec4 = 48 bytes ; G5.3)
        //   offset 128 : csTfParams0        (vec4f -- (g, a, b, c) parametric TF ; G5.3.x)
        //   offset 144 : csTfParams1        (vec4f -- (d, e, f, _) parametric TF ; G5.3.x)
        //   offset 160 : clipShapeBounds    (vec4f -- l, t, r, b device-px ; G2.x)
        //   offset 176 : clipShapeRadiiKind (vec4f -- rx, ry, clipKind, _ ; G2.x)
        //   offset 192 : devToImageRow0     (vec4f -- (sx, kx, tx, _) ; G5.2.2)
        //   offset 208 : devToImageRow1     (vec4f -- (ky, sy, ty, _) ; G5.2.2)
        // Total = 224 bytes.
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
        // (`0 = identity` / `1 = sRGB-TF + matrix` / `2 = parametric
        // sRGBish TF + matrix`, G5.3.x) bit-reinterpreted ; the
        // shader's `bitcast<u32>` gates the transform branch.
        // `csMatrix` is column-major source-linear -> sRGB-linear ;
        // WGSL std140 inserts 4 bytes of padding after each 3-float
        // column, so we pack 3 columns of `(x, y, z, 0)` here.
        //
        // G5.3.x -- `csTfParams0/1` carry the 7-float parametric TF
        // coefficients (`(g, a, b, c, d, e, f)`, mirroring
        // `SkcmsTransferFunction`) used by the shader's mode = 2
        // branch. Modes 0 and 1 still ship `(1, 1, 0, 0, 0, 0, 0)`
        // (linear identity TF) as a stable byte payload so the std140
        // alignment is deterministic across draws.
        //
        // G2.x slice 2 -- `clipShapeBounds` + `clipShapeRadiiKind`
        // mirror the rect pipeline. `clipKind == 0` => no shape clip
        // (the slots are ignored) ; `clipKind == 1` => rrect coverage
        // multiplied into the fragment output. The two slots sit after
        // the G5.3 colorspace block to keep that block contiguous.
        //
        // G5.2.2 -- `devToImageRow0` / `devToImageRow1` carry the 2x3
        // device-to-image affine that drives the per-fragment image-
        // coord lookup. Axis-aligned callers (drawImageRect) build it
        // from the rect ratio ; SkBitmapShader callers ship the
        // inverse of `ctm * localMatrix` directly so rotated / skewed
        // shaders route through the same shader path.
        val m = d.csMatrix
        val tf = d.csTfParams
        val cb = d.clipShapeBounds
        val r0 = d.devToImageRow0
        val r1 = d.devToImageRow1
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
                tf[0], tf[1], tf[2], tf[3],
                tf[4], tf[5], tf[6], 0f,
                cb[0], cb[1], cb[2], cb[3],
                d.clipShapeRx, d.clipShapeRy, d.clipKind, 0f,
                r0[0], r0[1], r0[2], 0f,
                r1[0], r1[1], r1[2], 0f,
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

    /**
     * Phase G-saveLayer-fast -- build per-draw resources for a
     * [LayerCompositeDraw]. The uniform carries the dst origin + layer
     * size + paint colour ; the bind group also captures the child's
     * intermediate texture view (already populated by
     * [flushDrawsOnly]).
     */
    private fun buildLayerCompositeDrawResources(d: LayerCompositeDraw): DrawResources {
        val uniform = context.device.createBuffer(
            BufferDescriptor(
                size = LAYER_COMPOSITE_UNIFORM_SIZE,
                usage = GPUBufferUsage.Uniform or GPUBufferUsage.CopyDst,
                label = "SkWebGpuDevice.layerCompositeDraw",
            ),
        )
        // Layout matches `layer_composite.wgsl` :
        //   offset   0 : dstOriginSize       (vec4f -- x, y, layerW, layerH)
        //   offset  16 : paintColor          (vec4f -- premul rgba modulation)
        //   offset  32 : colorFilterKindMode (vec4f -- kind, mode, 0, 0)
        //   offset  48 : colorFilterParam0   (vec4f -- premul colour OR matrix row 0)
        //   offset  64 : colorFilterParam1   (vec4f -- matrix row 1)
        //   offset  80 : colorFilterParam2   (vec4f -- matrix row 2)
        //   offset  96 : colorFilterParam3   (vec4f -- matrix row 3)
        //   offset 112 : colorFilterBias     (vec4f -- per-row bias)
        // Total = 128 bytes. The colourFilter payload is zeroed when
        // the layer paint has no filter -- the shader's `kind == 0`
        // branch is a no-op, so the fast path stays bit-iso.
        val packed = FloatArray(32) // 8 vec4f * 4 floats each.
        packed[0] = d.dstOriginX.toFloat()
        packed[1] = d.dstOriginY.toFloat()
        packed[2] = d.layerWidth.toFloat()
        packed[3] = d.layerHeight.toFloat()
        packed[4] = d.paintR; packed[5] = d.paintG
        packed[6] = d.paintB; packed[7] = d.paintA
        // colorFilterPacked is laid out as 6 contiguous vec4f starting
        // at vec4f index 2 (offset 32 bytes).
        System.arraycopy(d.colorFilterPacked, 0, packed, 8, 24)
        context.queue.writeBuffer(
            uniform, 0uL,
            ArrayBuffer.of(packed),
        )
        val bindGroup = context.device.createBindGroup(
            BindGroupDescriptor(
                layout = layerCompositeBindGroupLayout,
                entries = listOf(
                    BindGroupEntry(binding = 0u, resource = BufferBinding(buffer = uniform)),
                    BindGroupEntry(binding = 1u, resource = d.layerView),
                ),
            ),
        )
        return DrawResources(uniform = uniform, bindGroup = bindGroup)
    }

    /**
     * Phase MaskFilter-blur -- build the GPU resources for a
     * [BlurredPathDraw]. Two uniform buffers + two bind groups :
     *   - Primary (H pass) : axisRadius.x = 1, .y = 0, source binding
     *     is the child layer device's shape-mask view.
     *   - Secondary (V pass) : axisRadius.x = 0, .y = 1, source binding
     *     is the host-allocated H-pass scratch view.
     * Both uniforms share the same byte layout
     * ([BLUR_UNIFORM_SIZE]) ; only `axisRadius` and (for the V pass)
     * `paintColor` differ. The H uniform's `paintColor` is unused but
     * shipped as zeros for deterministic bytes.
     *
     * The kernel half-array (1 + radius floats) is packed into the
     * `weights` slot starting at offset 48 ; trailing slots are zeroed
     * so the uniform's WGSL `array<vec4f, 9>` is fully initialised.
     */
    private fun buildBlurredPathDrawResources(d: BlurredPathDraw): DrawResources {
        // ── H pass uniform : axisRadius = (1, 0, radius, 0). paintColor
        //    is irrelevant for the H pass (the V pass owns the colour
        //    fold) ; we ship zeros for deterministic bytes.
        val hUniform = context.device.createBuffer(
            BufferDescriptor(
                size = BLUR_UNIFORM_SIZE,
                usage = GPUBufferUsage.Uniform or GPUBufferUsage.CopyDst,
                label = "SkWebGpuDevice.blurHorizontalDraw",
            ),
        )
        val hPacked = FloatArray(BLUR_UNIFORM_FLOATS)
        // dstOriginSize : H pass writes to a scratch sized to (srcW, srcH)
        // with origin (0, 0) -- the shader treats dst pixel coords as
        // the source pixel coords directly.
        hPacked[0] = 0f; hPacked[1] = 0f
        hPacked[2] = d.srcWidth.toFloat(); hPacked[3] = d.srcHeight.toFloat()
        // paintColor : zero in the H pass (ignored by fs_horizontal).
        hPacked[4] = 0f; hPacked[5] = 0f; hPacked[6] = 0f; hPacked[7] = 0f
        // axisRadius : (1, 0, radius, 0). Style is irrelevant for the
        // H pass (the H entry doesn't read the shape mask) ; we ship 0
        // for deterministic bytes.
        hPacked[8] = 1f; hPacked[9] = 0f; hPacked[10] = d.radius.toFloat(); hPacked[11] = 0f
        // weights[0..radius] -> flat indices starting at packed[12].
        for (k in 0..d.radius) {
            hPacked[12 + k] = d.kernel[k]
        }
        context.queue.writeBuffer(hUniform, 0uL, ArrayBuffer.of(hPacked))
        // Binding 2 (shape mask) is unused by the H entry but the
        // shared layout requires it ; bind the shape-mask view so a
        // valid texture sits in the slot.
        val hBindGroup = context.device.createBindGroup(
            BindGroupDescriptor(
                layout = blurBindGroupLayout,
                entries = listOf(
                    BindGroupEntry(binding = 0u, resource = BufferBinding(buffer = hUniform)),
                    BindGroupEntry(binding = 1u, resource = d.shapeMaskView),
                    BindGroupEntry(binding = 2u, resource = d.shapeMaskView),
                ),
            ),
        )

        // ── V pass uniform : axisRadius = (0, 1, radius, 0). paintColor
        //    is the per-draw premul colour the shader multiplies by the
        //    blurred coverage. dstOrigin is the (ml, mt) of the shape
        //    mask in parent-device pixel coords -- the shader subtracts
        //    it from the fragment position to land in scratch-pixel
        //    coords.
        val vUniform = context.device.createBuffer(
            BufferDescriptor(
                size = BLUR_UNIFORM_SIZE,
                usage = GPUBufferUsage.Uniform or GPUBufferUsage.CopyDst,
                label = "SkWebGpuDevice.blurVerticalDraw",
            ),
        )
        val vPacked = FloatArray(BLUR_UNIFORM_FLOATS)
        vPacked[0] = d.dstOriginX.toFloat(); vPacked[1] = d.dstOriginY.toFloat()
        vPacked[2] = d.srcWidth.toFloat(); vPacked[3] = d.srcHeight.toFloat()
        vPacked[4] = d.paintR; vPacked[5] = d.paintG
        vPacked[6] = d.paintB; vPacked[7] = d.paintA
        // axisRadius : (0, 1, radius, blurStyle). The V-pass shader
        // branches on axisRadius.w to compose B (blurred coverage)
        // with M (sharp shape-mask alpha) per the SkBlurStyle formula.
        vPacked[8] = 0f
        vPacked[9] = 1f
        vPacked[10] = d.radius.toFloat()
        vPacked[11] = d.blurStyleOrdinal.toFloat()
        for (k in 0..d.radius) {
            vPacked[12 + k] = d.kernel[k]
        }
        context.queue.writeBuffer(vUniform, 0uL, ArrayBuffer.of(vPacked))
        // V-pass bind group : binding 1 is the H-pass scratch (the
        // convolution source for the V pass), binding 2 is the sharp
        // shape mask (consumed by the kSolid / kOuter / kInner branch).
        val vBindGroup = context.device.createBindGroup(
            BindGroupDescriptor(
                layout = blurBindGroupLayout,
                entries = listOf(
                    BindGroupEntry(binding = 0u, resource = BufferBinding(buffer = vUniform)),
                    BindGroupEntry(binding = 1u, resource = d.scratchHView),
                    BindGroupEntry(binding = 2u, resource = d.shapeMaskView),
                ),
            ),
        )
        return DrawResources(
            uniform = hUniform,
            bindGroup = hBindGroup,
            secondaryUniform = vUniform,
            secondaryBindGroup = vBindGroup,
            scratchHTexture = d.scratchHTexture,
            scratchHView = d.scratchHView,
            layerDevice = d.shapeMaskDevice,
        )
    }

    /**
     * Phase MaskFilter-blur-shaded -- build the GPU resources for a
     * [BlurredShadedPathDraw]. Shape mirrors
     * [buildBlurredPathDrawResources] (two uniform buffers + two bind
     * groups against the shared [blurBindGroupLayout]) but :
     *   - Primary (H pass) binding 1 + 2 are both the shaded layer view
     *     (the H pass only reads binding 1 ; binding 2 is kept for
     *     layout parity).
     *   - Secondary (V pass) binding 1 is the H-pass scratch, binding
     *     2 is the shaded layer (consumed by the kSolid / kOuter /
     *     kInner style combine).
     * Both uniforms share the [BLUR_UNIFORM_SIZE] byte layout ; the
     * `paintColor` slot ships as zeros (the shaded layer already
     * carries the final colours, no paint-colour fold in the shader).
     */
    private fun buildBlurredShadedPathDrawResources(d: BlurredShadedPathDraw): DrawResources {
        // ── H pass uniform : axisRadius = (1, 0, radius, 0). paintColor
        //    is zero (the shaded layer carries the final colours).
        val hUniform = context.device.createBuffer(
            BufferDescriptor(
                size = BLUR_UNIFORM_SIZE,
                usage = GPUBufferUsage.Uniform or GPUBufferUsage.CopyDst,
                label = "SkWebGpuDevice.shadedBlurHorizontalDraw",
            ),
        )
        val hPacked = FloatArray(BLUR_UNIFORM_FLOATS)
        hPacked[0] = 0f; hPacked[1] = 0f
        hPacked[2] = d.srcWidth.toFloat(); hPacked[3] = d.srcHeight.toFloat()
        // paintColor : unused for the shaded variant ; ship zeros for
        // deterministic bytes.
        hPacked[4] = 0f; hPacked[5] = 0f; hPacked[6] = 0f; hPacked[7] = 0f
        hPacked[8] = 1f; hPacked[9] = 0f; hPacked[10] = d.radius.toFloat(); hPacked[11] = 0f
        for (k in 0..d.radius) {
            hPacked[12 + k] = d.kernel[k]
        }
        context.queue.writeBuffer(hUniform, 0uL, ArrayBuffer.of(hPacked))
        val hBindGroup = context.device.createBindGroup(
            BindGroupDescriptor(
                layout = blurBindGroupLayout,
                entries = listOf(
                    BindGroupEntry(binding = 0u, resource = BufferBinding(buffer = hUniform)),
                    BindGroupEntry(binding = 1u, resource = d.shadedLayerView),
                    BindGroupEntry(binding = 2u, resource = d.shadedLayerView),
                ),
            ),
        )

        // ── V pass uniform : axisRadius = (0, 1, radius, blurStyle).
        val vUniform = context.device.createBuffer(
            BufferDescriptor(
                size = BLUR_UNIFORM_SIZE,
                usage = GPUBufferUsage.Uniform or GPUBufferUsage.CopyDst,
                label = "SkWebGpuDevice.shadedBlurVerticalDraw",
            ),
        )
        val vPacked = FloatArray(BLUR_UNIFORM_FLOATS)
        vPacked[0] = d.dstOriginX.toFloat(); vPacked[1] = d.dstOriginY.toFloat()
        vPacked[2] = d.srcWidth.toFloat(); vPacked[3] = d.srcHeight.toFloat()
        vPacked[4] = 0f; vPacked[5] = 0f; vPacked[6] = 0f; vPacked[7] = 0f
        vPacked[8] = 0f
        vPacked[9] = 1f
        vPacked[10] = d.radius.toFloat()
        vPacked[11] = d.blurStyleOrdinal.toFloat()
        for (k in 0..d.radius) {
            vPacked[12 + k] = d.kernel[k]
        }
        context.queue.writeBuffer(vUniform, 0uL, ArrayBuffer.of(vPacked))
        val vBindGroup = context.device.createBindGroup(
            BindGroupDescriptor(
                layout = blurBindGroupLayout,
                entries = listOf(
                    BindGroupEntry(binding = 0u, resource = BufferBinding(buffer = vUniform)),
                    BindGroupEntry(binding = 1u, resource = d.scratchHView),
                    BindGroupEntry(binding = 2u, resource = d.shadedLayerView),
                ),
            ),
        )
        return DrawResources(
            uniform = hUniform,
            bindGroup = hBindGroup,
            secondaryUniform = vUniform,
            secondaryBindGroup = vBindGroup,
            scratchHTexture = d.scratchHTexture,
            scratchHView = d.scratchHView,
            layerDevice = d.shadedLayerDevice,
        )
    }

    /**
     * Phase G-saveLayer-imageFilter-blur -- build the GPU resources for
     * a [BlurredLayerCompositeDraw]. Three uniform buffers + three bind
     * groups :
     *   - Primary   (H pass)    : `axisTileRadius.x = 1, .y = 0`, source
     *                              binding is the layer texture view.
     *                              Kernel + radius are per-X.
     *   - Secondary (V pass)    : `axisTileRadius.x = 0, .y = 1`, source
     *                              binding is the H-pass scratch view.
     *                              Kernel + radius are per-Y.
     *   - Tertiary  (composite) : layout matches `layer_composite.wgsl`
     *                              (128-byte uniform : dstOriginSize,
     *                              paintColor, colorFilter*). Source
     *                              binding is the V-pass scratch view.
     *
     * The H and V uniforms share the [BLUR_IMAGE_FILTER_UNIFORM_SIZE]
     * byte layout ; only the per-axis flags + the kernel data differ.
     * The composite uniform is the same byte layout as the
     * no-imageFilter [LayerCompositeDraw], so the pixel result for
     * the colour-filter-applied step is bit-iso with the plain
     * saveLayer composite for the same paint payload.
     */
    private fun buildBlurredLayerCompositeDrawResources(
        d: BlurredLayerCompositeDraw,
    ): DrawResources {
        // ── Phase G-saveLayer-imageFilter-compose -- optional pre-blur
        //    CF pass. Allocated only when the draw carries a pre-blur
        //    CF (i.e. a Compose chain landed with a CF inner of a Blur
        //    outer). Reuses the layer_composite.wgsl shader / bind
        //    group layout with kSrc blend so the scratch is overwritten
        //    rather than blended with prior content.
        val preCfPacked = d.preBlurColorFilterPacked
        val preCfView = d.scratchPreCfView
        var preCfUniform: GPUBuffer? = null
        var preCfBindGroup: io.ygdrasil.webgpu.GPUBindGroup? = null
        // The blur H pass reads from preCfView when the pre-CF pass
        // produced one ; otherwise it reads from the raw layer view.
        val hPassSourceView: GPUTextureView = preCfView ?: d.layerView
        if (preCfPacked != null && preCfView != null) {
            preCfUniform = context.device.createBuffer(
                BufferDescriptor(
                    size = LAYER_COMPOSITE_UNIFORM_SIZE,
                    usage = GPUBufferUsage.Uniform or GPUBufferUsage.CopyDst,
                    label = "SkWebGpuDevice.blurImageFilterPreCfDraw",
                ),
            )
            // layer_composite.wgsl uniform layout : 8 vec4f.
            //   dstOriginSize : (0, 0, w, h)  -> identity mapping
            //   paintColor    : (1, 1, 1, 1)  -> no scale
            //   colorFilter*  : packed CF (6 vec4f, 24 floats)
            val packed = FloatArray(32)
            packed[0] = 0f; packed[1] = 0f
            packed[2] = d.layerWidth.toFloat(); packed[3] = d.layerHeight.toFloat()
            packed[4] = 1f; packed[5] = 1f; packed[6] = 1f; packed[7] = 1f
            System.arraycopy(preCfPacked, 0, packed, 8, 24)
            context.queue.writeBuffer(preCfUniform, 0uL, ArrayBuffer.of(packed))
            preCfBindGroup = context.device.createBindGroup(
                BindGroupDescriptor(
                    layout = layerCompositeBindGroupLayout,
                    entries = listOf(
                        BindGroupEntry(
                            binding = 0u,
                            resource = BufferBinding(buffer = preCfUniform),
                        ),
                        BindGroupEntry(binding = 1u, resource = d.layerView),
                    ),
                ),
            )
        }

        // ── H pass uniform : axisTileRadius = (1, 0, tileMode, radiusX).
        val hUniform = context.device.createBuffer(
            BufferDescriptor(
                size = BLUR_IMAGE_FILTER_UNIFORM_SIZE,
                usage = GPUBufferUsage.Uniform or GPUBufferUsage.CopyDst,
                label = "SkWebGpuDevice.blurImageFilterHorizontalDraw",
            ),
        )
        val hPacked = FloatArray(BLUR_IMAGE_FILTER_UNIFORM_FLOATS)
        // dstOriginSize.xy unused (layer-space sampling), .zw = (w, h).
        hPacked[0] = 0f; hPacked[1] = 0f
        hPacked[2] = d.layerWidth.toFloat(); hPacked[3] = d.layerHeight.toFloat()
        // axisTileRadius = (1, 0, tileMode, radiusX).
        hPacked[4] = 1f; hPacked[5] = 0f
        hPacked[6] = d.tileModeOrdinal.toFloat()
        hPacked[7] = d.radiusX.toFloat()
        // weights[0..radiusX] -> flat indices starting at packed[8].
        for (k in 0..d.radiusX) {
            hPacked[8 + k] = d.kernelX[k]
        }
        context.queue.writeBuffer(hUniform, 0uL, ArrayBuffer.of(hPacked))
        val hBindGroup = context.device.createBindGroup(
            BindGroupDescriptor(
                layout = blurImageFilterBindGroupLayout,
                entries = listOf(
                    BindGroupEntry(binding = 0u, resource = BufferBinding(buffer = hUniform)),
                    BindGroupEntry(binding = 1u, resource = hPassSourceView),
                ),
            ),
        )

        // ── V pass uniform : axisTileRadius = (0, 1, tileMode, radiusY).
        val vUniform = context.device.createBuffer(
            BufferDescriptor(
                size = BLUR_IMAGE_FILTER_UNIFORM_SIZE,
                usage = GPUBufferUsage.Uniform or GPUBufferUsage.CopyDst,
                label = "SkWebGpuDevice.blurImageFilterVerticalDraw",
            ),
        )
        val vPacked = FloatArray(BLUR_IMAGE_FILTER_UNIFORM_FLOATS)
        vPacked[0] = 0f; vPacked[1] = 0f
        vPacked[2] = d.layerWidth.toFloat(); vPacked[3] = d.layerHeight.toFloat()
        vPacked[4] = 0f; vPacked[5] = 1f
        vPacked[6] = d.tileModeOrdinal.toFloat()
        vPacked[7] = d.radiusY.toFloat()
        for (k in 0..d.radiusY) {
            vPacked[8 + k] = d.kernelY[k]
        }
        context.queue.writeBuffer(vUniform, 0uL, ArrayBuffer.of(vPacked))
        val vBindGroup = context.device.createBindGroup(
            BindGroupDescriptor(
                layout = blurImageFilterBindGroupLayout,
                entries = listOf(
                    BindGroupEntry(binding = 0u, resource = BufferBinding(buffer = vUniform)),
                    BindGroupEntry(binding = 1u, resource = d.scratchHView),
                ),
            ),
        )

        // ── Composite pass uniform : matches `layer_composite.wgsl`'s
        //    128-byte layout. dstOriginSize : (originX, originY,
        //    layerW, layerH) -- the composite shader subtracts this
        //    origin to map the parent-pixel fragment to a layer-pixel
        //    sample. paintColor + colorFilter payload mirror the plain
        //    [LayerCompositeDraw] packing.
        val compUniform = context.device.createBuffer(
            BufferDescriptor(
                size = LAYER_COMPOSITE_UNIFORM_SIZE,
                usage = GPUBufferUsage.Uniform or GPUBufferUsage.CopyDst,
                label = "SkWebGpuDevice.blurImageFilterCompositeDraw",
            ),
        )
        val compPacked = FloatArray(32) // 8 vec4f * 4 floats each.
        compPacked[0] = d.dstOriginX.toFloat()
        compPacked[1] = d.dstOriginY.toFloat()
        compPacked[2] = d.layerWidth.toFloat()
        compPacked[3] = d.layerHeight.toFloat()
        compPacked[4] = d.paintR; compPacked[5] = d.paintG
        compPacked[6] = d.paintB; compPacked[7] = d.paintA
        System.arraycopy(d.colorFilterPacked, 0, compPacked, 8, 24)
        context.queue.writeBuffer(compUniform, 0uL, ArrayBuffer.of(compPacked))
        val compBindGroup = context.device.createBindGroup(
            BindGroupDescriptor(
                layout = layerCompositeBindGroupLayout,
                entries = listOf(
                    BindGroupEntry(binding = 0u, resource = BufferBinding(buffer = compUniform)),
                    BindGroupEntry(binding = 1u, resource = d.scratchVView),
                ),
            ),
        )

        return DrawResources(
            uniform = hUniform,
            bindGroup = hBindGroup,
            secondaryUniform = vUniform,
            secondaryBindGroup = vBindGroup,
            tertiaryUniform = compUniform,
            tertiaryBindGroup = compBindGroup,
            scratchHTexture = d.scratchHTexture,
            scratchHView = d.scratchHView,
            scratchVTexture = d.scratchVTexture,
            scratchVView = d.scratchVView,
            // Phase G-saveLayer-imageFilter-compose -- pre-blur CF
            // pass resources (only populated when the draw carried a
            // pre-blur CF, else left null and the dispatch skips the
            // pre-CF render pass).
            quaternaryUniform = preCfUniform,
            quaternaryBindGroup = preCfBindGroup,
            scratchPreCfTexture = d.scratchPreCfTexture,
            scratchPreCfView = d.scratchPreCfView,
            // layerDevice intentionally left null -- the layer device
            // is owned by SkCanvas, not by this draw (mirrors
            // [LayerCompositeDraw]).
        )
    }

    /**
     * Phase G-saveLayer-imageFilter-dropshadow -- build the GPU resources
     * for a [DropShadowLayerCompositeDraw]. Four uniform buffers + four
     * bind groups :
     *   - Primary    (H blur)            : same shape as the plain blur
     *                                       ImageFilter's primary -- sources
     *                                       the layer texture, X-axis kernel.
     *   - Secondary  (V blur)            : sources the H scratch, Y-axis
     *                                       kernel.
     *   - Tertiary   (shadow composite)  : sources the V scratch, applies
     *                                       the packed `Blend(shadowColor,
     *                                       kSrcIn)` colour filter, lands
     *                                       at the shifted origin.
     *   - Quaternary (original composite): sources the layer texture
     *                                       directly, applies the paint's
     *                                       regular colour filter, lands
     *                                       at the original origin.
     *
     * The two composite uniforms share the same byte layout
     * ([LAYER_COMPOSITE_UNIFORM_SIZE]) as the no-filter saveLayer ; only
     * their `dstOriginSize.xy` + `colorFilterPacked` payload differ.
     */
    private fun buildDropShadowLayerCompositeDrawResources(
        d: DropShadowLayerCompositeDraw,
    ): DrawResources {
        // ── H blur pass uniform : axisTileRadius = (1, 0, kDecal, radiusX).
        val hUniform = context.device.createBuffer(
            BufferDescriptor(
                size = BLUR_IMAGE_FILTER_UNIFORM_SIZE,
                usage = GPUBufferUsage.Uniform or GPUBufferUsage.CopyDst,
                label = "SkWebGpuDevice.dropShadowBlurHorizontalDraw",
            ),
        )
        val hPacked = FloatArray(BLUR_IMAGE_FILTER_UNIFORM_FLOATS)
        hPacked[0] = 0f; hPacked[1] = 0f
        hPacked[2] = d.layerWidth.toFloat(); hPacked[3] = d.layerHeight.toFloat()
        hPacked[4] = 1f; hPacked[5] = 0f
        hPacked[6] = d.tileModeOrdinal.toFloat()
        hPacked[7] = d.radiusX.toFloat()
        for (k in 0..d.radiusX) {
            hPacked[8 + k] = d.kernelX[k]
        }
        context.queue.writeBuffer(hUniform, 0uL, ArrayBuffer.of(hPacked))
        val hBindGroup = context.device.createBindGroup(
            BindGroupDescriptor(
                layout = blurImageFilterBindGroupLayout,
                entries = listOf(
                    BindGroupEntry(binding = 0u, resource = BufferBinding(buffer = hUniform)),
                    BindGroupEntry(binding = 1u, resource = d.layerView),
                ),
            ),
        )

        // ── V blur pass uniform : axisTileRadius = (0, 1, kDecal, radiusY).
        val vUniform = context.device.createBuffer(
            BufferDescriptor(
                size = BLUR_IMAGE_FILTER_UNIFORM_SIZE,
                usage = GPUBufferUsage.Uniform or GPUBufferUsage.CopyDst,
                label = "SkWebGpuDevice.dropShadowBlurVerticalDraw",
            ),
        )
        val vPacked = FloatArray(BLUR_IMAGE_FILTER_UNIFORM_FLOATS)
        vPacked[0] = 0f; vPacked[1] = 0f
        vPacked[2] = d.layerWidth.toFloat(); vPacked[3] = d.layerHeight.toFloat()
        vPacked[4] = 0f; vPacked[5] = 1f
        vPacked[6] = d.tileModeOrdinal.toFloat()
        vPacked[7] = d.radiusY.toFloat()
        for (k in 0..d.radiusY) {
            vPacked[8 + k] = d.kernelY[k]
        }
        context.queue.writeBuffer(vUniform, 0uL, ArrayBuffer.of(vPacked))
        val vBindGroup = context.device.createBindGroup(
            BindGroupDescriptor(
                layout = blurImageFilterBindGroupLayout,
                entries = listOf(
                    BindGroupEntry(binding = 0u, resource = BufferBinding(buffer = vUniform)),
                    BindGroupEntry(binding = 1u, resource = d.scratchHView),
                ),
            ),
        )

        // ── Shadow composite uniform : dstOriginSize at the shifted
        //    origin, paintColor = (1, 1, 1, 1) (shadow alpha is folded
        //    into the colour-filter's premul colour), colour filter =
        //    Blend(shadowColor, kSrcIn).
        val shadowUniform = context.device.createBuffer(
            BufferDescriptor(
                size = LAYER_COMPOSITE_UNIFORM_SIZE,
                usage = GPUBufferUsage.Uniform or GPUBufferUsage.CopyDst,
                label = "SkWebGpuDevice.dropShadowShadowCompositeDraw",
            ),
        )
        val shadowPacked = FloatArray(32)
        shadowPacked[0] = d.shadowDstOriginX.toFloat()
        shadowPacked[1] = d.shadowDstOriginY.toFloat()
        shadowPacked[2] = d.layerWidth.toFloat()
        shadowPacked[3] = d.layerHeight.toFloat()
        // paintColor = (1, 1, 1, 1) -- the shadow's alpha + paint alpha
        // modulation is already folded into the colour-filter's premul
        // src colour, so we don't double-multiply here.
        shadowPacked[4] = 1f; shadowPacked[5] = 1f
        shadowPacked[6] = 1f; shadowPacked[7] = 1f
        System.arraycopy(d.shadowColorFilterPacked, 0, shadowPacked, 8, 24)
        context.queue.writeBuffer(shadowUniform, 0uL, ArrayBuffer.of(shadowPacked))
        val shadowBindGroup = context.device.createBindGroup(
            BindGroupDescriptor(
                layout = layerCompositeBindGroupLayout,
                entries = listOf(
                    BindGroupEntry(binding = 0u, resource = BufferBinding(buffer = shadowUniform)),
                    BindGroupEntry(binding = 1u, resource = d.scratchVView),
                ),
            ),
        )

        // ── Original composite uniform : dstOriginSize at the layer's
        //    original origin, paintColor = (paintA, paintA, paintA,
        //    paintA), colour filter = the paint's original.
        val originalUniform = context.device.createBuffer(
            BufferDescriptor(
                size = LAYER_COMPOSITE_UNIFORM_SIZE,
                usage = GPUBufferUsage.Uniform or GPUBufferUsage.CopyDst,
                label = "SkWebGpuDevice.dropShadowOriginalCompositeDraw",
            ),
        )
        val originalPacked = FloatArray(32)
        originalPacked[0] = d.dstOriginX.toFloat()
        originalPacked[1] = d.dstOriginY.toFloat()
        originalPacked[2] = d.layerWidth.toFloat()
        originalPacked[3] = d.layerHeight.toFloat()
        originalPacked[4] = d.paintR; originalPacked[5] = d.paintG
        originalPacked[6] = d.paintB; originalPacked[7] = d.paintA
        System.arraycopy(d.originalColorFilterPacked, 0, originalPacked, 8, 24)
        context.queue.writeBuffer(originalUniform, 0uL, ArrayBuffer.of(originalPacked))
        val originalBindGroup = context.device.createBindGroup(
            BindGroupDescriptor(
                layout = layerCompositeBindGroupLayout,
                entries = listOf(
                    BindGroupEntry(binding = 0u, resource = BufferBinding(buffer = originalUniform)),
                    BindGroupEntry(binding = 1u, resource = d.layerView),
                ),
            ),
        )

        return DrawResources(
            uniform = hUniform,
            bindGroup = hBindGroup,
            secondaryUniform = vUniform,
            secondaryBindGroup = vBindGroup,
            tertiaryUniform = shadowUniform,
            tertiaryBindGroup = shadowBindGroup,
            scratchHTexture = d.scratchHTexture,
            scratchHView = d.scratchHView,
            scratchVTexture = d.scratchVTexture,
            scratchVView = d.scratchVView,
            quaternaryUniform = originalUniform,
            quaternaryBindGroup = originalBindGroup,
            // layerDevice intentionally left null -- the layer device
            // is owned by SkCanvas, not by this draw (mirrors
            // [BlurredLayerCompositeDraw]).
        )
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
        //   offset    0 : color           (vec4)
        //   offset   16 : viewport        (vec4, only x/y used)
        //   offset   32 : edgeCount + pad (u32 reinterp + 3 pad)
        //   offset   48 : edges[256]      (vec4 each, here (Ax, Ay, Bx, By))
        //   offset 4144 : clipShapeBounds (vec4) ; G2.x (closing slice)
        //   offset 4160 : clipShapeRadiiKind (vec4) ; G2.x (closing slice)
        val packed = FloatArray(12 + MAX_AA_EDGES * 4 + 8)
        packed[0] = d.r; packed[1] = d.g; packed[2] = d.b; packed[3] = d.a
        packed[4] = width.toFloat(); packed[5] = height.toFloat()
        packed[6] = 0f; packed[7] = 0f
        packed[8] = Float.fromBits(d.edgeCount)
        packed[9] = 0f; packed[10] = 0f; packed[11] = 0f
        System.arraycopy(d.edges, 0, packed, 12, d.edges.size)
        writeClipShape(packed, 12 + MAX_AA_EDGES * 4, d.clipShape)
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
        val packed = FloatArray(20 + MAX_AA_EDGES * 4 + MAX_GRADIENT_STOPS * 8 + 8)
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
        writeClipShape(packed, 20 + MAX_AA_EDGES * 4 + MAX_GRADIENT_STOPS * 8, d.clipShape)
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
        val packed = FloatArray(20 + MAX_AA_EDGES * 4 + MAX_GRADIENT_STOPS * 8 + 8)
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
        writeClipShape(packed, 20 + MAX_AA_EDGES * 4 + MAX_GRADIENT_STOPS * 8, d.clipShape)
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
        val packed = FloatArray(20 + MAX_AA_EDGES * 4 + MAX_GRADIENT_STOPS * 8 + 8)
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
        writeClipShape(packed, 20 + MAX_AA_EDGES * 4 + MAX_GRADIENT_STOPS * 8, d.clipShape)
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
        val packed = FloatArray(20 + MAX_AA_EDGES * 4 + MAX_GRADIENT_STOPS * 8 + 8)
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
        writeClipShape(packed, 20 + MAX_AA_EDGES * 4 + MAX_GRADIENT_STOPS * 8, d.clipShape)
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
        val packed = FloatArray(28 + MAX_AA_EDGES * 4 + MAX_GRADIENT_STOPS * 8 + 8)
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
        writeClipShape(packed, 28 + MAX_AA_EDGES * 4 + MAX_GRADIENT_STOPS * 8, d.clipShape)
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
        //   offset    0 : color              (vec4f, unused by bitmap frag ; kept
        //                                      for layout-compat with solid_polygon)
        //   offset   16 : viewport           (vec4f, only x/y used)
        //   offset   32 : srcRect            (l, t, r, b in image-pixel coords)
        //   offset   48 : dstRect            (l, t, r, b in device-pixel coords)
        //   offset   64 : imageSize          (w, h, tileX bit-reinterp u32, tileY bit-reinterp u32)
        //   offset   80 : paintColor         (vec4f, premul tint)
        //   offset   96 : csFlags            (.x = csMode bit-reinterp u32 ; G5.3)
        //   offset  112 : csMatrix           (mat3x3<f32>, std140 padded 3 * vec4 = 48 bytes ; G5.3)
        //   offset  160 : csTfParams0        (vec4f -- (g, a, b, c) parametric TF ; G5.3.x)
        //   offset  176 : csTfParams1        (vec4f -- (d, e, f, _) parametric TF ; G5.3.x)
        //   offset  192 : clipShapeBounds    (vec4f -- l, t, r, b device-px ; G2.x)
        //   offset  208 : clipShapeRadiiKind (vec4f -- rx, ry, clipKind, _ ; G2.x)
        //   offset  224 : devToImageRow0     (vec4f -- (sx, kx, tx, _) ; G5.2.2)
        //   offset  240 : devToImageRow1     (vec4f -- (ky, sy, ty, _) ; G5.2.2)
        //   offset  256 : edgeCountPad       (.x = edgeCount as bit-reinterp f32)
        //   offset  272 : edges[MAX_AA_EDGES] (vec4 each)
        // Total = 4368 bytes (was 4336 before G5.2.2 ; +32 for the
        // device-to-image affine, edge tail shifts forward by 32 bytes).
        val packed = FloatArray(68 + MAX_AA_EDGES * 4)
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
        // csTfParams0 / csTfParams1 (G5.3.x) -- `(g, a, b, c)` and `(d, e, f, _)`.
        val tf = d.csTfParams
        packed[40] = tf[0]; packed[41] = tf[1]; packed[42] = tf[2]; packed[43] = tf[3]
        packed[44] = tf[4]; packed[45] = tf[5]; packed[46] = tf[6]; packed[47] = 0f
        // G2.x -- clipShapeBounds + clipShapeRadiiKind. clipKind == 0
        // means "no shape clip" (slots ignored by the shader, same as
        // the rect pipeline).
        val cb = d.clipShapeBounds
        packed[48] = cb[0]; packed[49] = cb[1]; packed[50] = cb[2]; packed[51] = cb[3]
        packed[52] = d.clipShapeRx; packed[53] = d.clipShapeRy
        packed[54] = d.clipKind; packed[55] = 0f
        // G5.2.2 -- 2x3 device-to-image affine (mirrors bitmap_shader.wgsl).
        val r0 = d.devToImageRow0
        val r1 = d.devToImageRow1
        packed[56] = r0[0]; packed[57] = r0[1]; packed[58] = r0[2]; packed[59] = 0f
        packed[60] = r1[0]; packed[61] = r1[1]; packed[62] = r1[2]; packed[63] = 0f
        // edgeCountPad
        packed[64] = Float.fromBits(d.edgeCount)
        packed[65] = 0f; packed[66] = 0f; packed[67] = 0f
        // edges
        System.arraycopy(d.edges, 0, packed, 68, d.edges.size)
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
        layerCompositePipelineCache.values.forEach { it.close() }
        layerCompositePipelineCache.clear()
        blurVerticalPipelineCache.values.forEach { it.close() }
        blurVerticalPipelineCache.clear()
        blurHorizontalPipeline.close()
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
        layerCompositeShader.close()
        blurGaussianShader.close()
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
         *   color (16) + outerBounds (16) + innerBounds (16) +
         *   clipShapeBounds (16) + clipShapeRadiiKind (16) +
         *   colorFilterKindMode (16) + colorFilterParam0 (16) +
         *   colorFilterParam1 (16) + colorFilterParam2 (16) +
         *   colorFilterParam3 (16) + colorFilterBias (16) = 176 bytes.
         * G3.1.1 bumped from 32 to 48 to carry the optional annular
         * inner-rect bounds (degenerate for fill draws). G2.x extended
         * to 80 bytes to carry the optional analytical clip-shape
         * (kClipKind = 0 means "no shape clip", existing behaviour).
         * Phase G-direct-colorFilter bumped from 80 to 176 bytes to
         * carry the optional paint.colorFilter payload consumed by the
         * `apply_color_filter` helper in `solid_color.wgsl`. Fast path
         * (no filter) ships zeros for the trailing 96 bytes, the
         * shader's `kind == 0` branch is a no-op and existing tests
         * stay bit-iso.
         */
        const val RECT_UNIFORM_SIZE: ULong = 176uL
        /**
         * G2.x -- clip-shape kind packed into the third float of
         * `clipShapeRadiiKind` in the rect uniform. 0 means "no shape
         * clip" (the legacy behaviour ; the integer scissor is the only
         * clip), 1 means "rrect-style shape" with `clipShapeBounds` +
         * `(rx, ry)` (subsumes oval / circle by reduction to rrect with
         * `rx = halfW, ry = halfH` for oval, `rx = ry = r` for circle).
         */
        const val CLIP_KIND_NONE: Float = 0f
        const val CLIP_KIND_RRECT: Float = 1f
        /** Zero-filled placeholder rect bounds for [CLIP_KIND_NONE] uniform packing. */
        val ZERO_RECT4: FloatArray = floatArrayOf(0f, 0f, 0f, 0f)
        /**
         * Phase G-direct-colorFilter -- shared identity payload for
         * the 6-vec4f `colorFilter*` slots when a draw has no colour
         * filter. Length 24 = 6 vec4f * 4 floats. Treated as read-only
         * by the uniform packer ([buildRectDrawResources]). DO NOT
         * MUTATE -- callers either copy or replace wholesale via
         * [packLayerCompositeColorFilter] when they need a non-zero
         * payload.
         */
        val ZERO_COLOR_FILTER_24: FloatArray = FloatArray(24)
        /**
         * Sentinel value for degenerate inner bounds (fill draws). The
         * shader's `clamp(min(p+1, r) - max(p, l), 0, 1)` naturally
         * collapses to 0 when `r < l` (and `b < t`), regardless of `p`.
         * Using a large positive value for l/t and its negation for r/b
         * keeps the math well-clear of FP precision concerns.
         */
        const val DEGENERATE_INNER: Float = 1e10f
        /**
         * Size of the polygon per-draw uniform :
         *   color (16) + viewport (16) + clipShapeBounds (16) +
         *   clipShapeRadiiKind (16) = 64 bytes.
         * G2.x (closing slice) bumped from 32 to 64 to carry the optional
         * analytical clip-shape consumed by `solid_polygon.wgsl`'s cover
         * pass. The stencil-write pass shares this layout (color writes
         * masked off ; only `viewport` is read by its vertex stage so the
         * trailing clip-shape slots are inert).
         */
        const val POLYGON_UNIFORM_SIZE: ULong = 64uL
        /**
         * Max polygon vertex count for the AA path. Bounded by the
         * `array<vec4f, 256>` in `aa_polygon.wgsl` ; circles flattened
         * to ~32 segments fit easily, even complex curved paths under
         * normal scales.
         */
        const val MAX_AA_EDGES: Int = 256
        /**
         * Size of the AA polygon per-draw uniform :
         *   color (16) + viewport (16) + edgeCount+pad (16) + edges (256*16) +
         *   clipShapeBounds (16) + clipShapeRadiiKind (16) = 4176 bytes.
         * G2.x (closing slice) bumped from 4144 to 4176 to carry the
         * optional analytical clip-shape consumed by `aa_polygon.wgsl` and
         * `aa_stencil_cover.wgsl`. Both shaders share this layout via the
         * `aaPolygonBindGroupLayout` ; the gradient stencil-cover shaders
         * use a larger uniform (positions / colors slots before the clip
         * payload), so this constant does not gate them.
         */
        const val AA_POLYGON_UNIFORM_SIZE: ULong = 4176uL // 48 + 256 * 16 + 32
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
         *   positions (16 * 16) + colors (16 * 16) +
         *   clipShapeBounds (16) + clipShapeRadiiKind (16) = 592 bytes.
         * G2.x bumped from 560 to 592 to carry the optional analytical
         * clip-shape (`clipKind = 0` means "no shape clip").
         */
        const val LINEAR_GRADIENT_UNIFORM_SIZE: ULong = 592uL // 560 + 32
        /**
         * G4.2 / G2.x -- size of the radial-gradient per-draw uniform :
         *   viewport (16) + centerRadius (16) + countPad (16) +
         *   positions (16 * 16) + colors (16 * 16) +
         *   clipShapeBounds (16) + clipShapeRadiiKind (16) = 592 bytes.
         * Same total as the linear uniform ; the first 32 bytes carry
         * different fields (viewport / center+radius vs startEnd /
         * viewport). G2.x bumped from 560 to 592 to carry the optional
         * analytical clip-shape.
         */
        const val RADIAL_GRADIENT_UNIFORM_SIZE: ULong = 592uL
        /**
         * G4.3 / G2.x -- size of the sweep-gradient per-draw uniform :
         *   viewport (16) + centerBiasScale (16) + countPad (16) +
         *   positions (16 * 16) + colors (16 * 16) +
         *   clipShapeBounds (16) + clipShapeRadiiKind (16) = 592 bytes.
         * Same total as the linear / radial uniforms ; only the second
         * vec4 carries different fields (center.xy + tBias + tScale here).
         * G2.x bumped from 560 to 592 to carry the optional analytical
         * clip-shape.
         */
        const val SWEEP_GRADIENT_UNIFORM_SIZE: ULong = 592uL
        /**
         * G4.4 / G2.x -- size of the conical-gradient per-draw uniform :
         *   viewport (16) + centerRadii (16) + countPad (16) +
         *   positions (16 * 16) + colors (16 * 16) +
         *   clipShapeBounds (16) + clipShapeRadiiKind (16) = 592 bytes.
         * Same total as the linear / radial / sweep uniforms ; only the
         * second vec4 carries different fields (centre.xy + r0 + r1 here,
         * for the kRadial sub-case). G2.x bumped from 560 to 592 to
         * carry the optional analytical clip-shape.
         */
        const val CONICAL_GRADIENT_UNIFORM_SIZE: ULong = 592uL
        /**
         * G4.4.1 / G2.x -- size of the conical focal-inside per-draw uniform :
         *   viewport (16) + affineRow0 (16) + affineRow1 (16) +
         *   focalScalars (16) + flagsCount (16) +
         *   positions (16 * 16) + colors (16 * 16) +
         *   clipShapeBounds (16) + clipShapeRadiiKind (16) = 624 bytes.
         * 32 bytes larger than the kRadial conical uniform because the
         * focal-frame affine takes 2 vec4f slots and the flags / focal
         * scalars consume two more. G2.x bumped from 592 to 624 to carry
         * the optional analytical clip-shape.
         */
        const val CONICAL_FOCAL_GRADIENT_UNIFORM_SIZE: ULong = 624uL
        /**
         * G4.4.4 / G2.x -- size of the conical kStrip per-draw uniform :
         *   viewport (16) + affineRow0 (16) + affineRow1 (16) +
         *   stripScalars (16) + positions (16 * 16) + colors (16 * 16) +
         *   clipShapeBounds (16) + clipShapeRadiiKind (16) = 608 bytes.
         * 16 bytes smaller than the focal-inside uniform (no flagsCount
         * vec4 ; the strip has only fP0 + stop count). G2.x bumped from
         * 576 to 608 to carry the optional analytical clip-shape.
         */
        const val CONICAL_STRIP_GRADIENT_UNIFORM_SIZE: ULong = 608uL
        /**
         * G5.1 / G5.3 / G5.3.x / G2.x -- size of the bitmap-shader per-draw uniform :
         *   srcRect (16) + dstRect (16) + imageSize (16) + paintColor (16) +
         *   csFlags (16) + csMatrix (mat3x3 -> 3 * 16 = 48) +
         *   csTfParams0 (16) + csTfParams1 (16) +
         *   clipShapeBounds (16) + clipShapeRadiiKind (16) = 192 bytes.
         * Matches `Uniforms { srcRect, dstRect, imageSize, paintColor,
         * csFlags, csMatrix, csTfParams0, csTfParams1, clipShapeBounds,
         * clipShapeRadiiKind }` in `bitmap_shader.wgsl`. WGSL std140
         * stores each `mat3x3<f32>` column padded to 16 bytes, hence the
         * 48 bytes for the 9 floats of the primaries matrix.
         *
         * G5.3.x added 32 bytes for the 7-float parametric TF
         * coefficients (`(g, a, b, c, d, e, f)`) used by csMode = 2
         * (Rec.2020 linear, Adobe RGB / k2Dot2, ...). G2.x added 32
         * more bytes for the analytical clip-shape payload -- when
         * `clipShapeRadiiKind.z == 0` the slots are ignored, matching
         * the rect pipeline. G5.2.2 added 32 more bytes for the 2x3
         * device-to-image affine (rotated / skewed bitmap shader) ;
         * the legacy srcRect/dstRect slots stay in the layout for
         * host-side scissor reuse but the fragment math now reads the
         * affine instead.
         */
        const val IMAGE_RECT_UNIFORM_SIZE: ULong = 224uL
        /**
         * Phase G-saveLayer-fast / Phase G-saveLayer-colorFilter -- size
         * of the layer-composite per-draw uniform. Layout (matches
         * `Uniforms` in `layer_composite.wgsl`) :
         *
         *   dstOriginSize       (vec4f,  16)
         *   paintColor          (vec4f,  16)
         *   colorFilterKindMode (vec4f,  16)
         *   colorFilterParam0   (vec4f,  16)
         *   colorFilterParam1   (vec4f,  16)
         *   colorFilterParam2   (vec4f,  16)
         *   colorFilterParam3   (vec4f,  16)
         *   colorFilterBias     (vec4f,  16)
         *   ------                       128 bytes
         *
         * The shader uses `textureLoad` (no sampler), so the bind group
         * only needs a uniform buffer + a texture view -- the layer
         * texture's pixel grid lines up 1:1 with the parent's device-
         * pixel grid (integer dstOrigin + integer-sized layer device,
         * set by SkCanvas), so no UV normalisation is needed.
         *
         * Bumped from 32 to 128 bytes in Phase G-saveLayer-colorFilter
         * to carry the (kind, blendMode, params, bias) payload for
         * `SkColorFilters.Blend` (kind = 1) and `SkColorFilters.Matrix`
         * (kind = 2). The fast path (no filter) zeroes everything past
         * `paintColor`, the shader's `kind == 0` branch is a no-op.
         */
        const val LAYER_COMPOSITE_UNIFORM_SIZE: ULong = 128uL
        /**
         * Phase MaskFilter-blur -- maximum supported off-centre tap count
         * per side for the separable Gaussian blur shader's uniform.
         * Total taps per pass = 1 + 2 * radius = 65. Maps to a sigma
         * limit of ~10.6 (`ceil(3 * sigma) <= 32`). Larger sigma clamps
         * to this radius in [drawPathWithBlurMaskFilterIfApplicable] ;
         * the kernel is renormalised at the clamp so the centre pixel
         * preserves full alpha, at the cost of a slightly under-spread
         * outer tail.
         *
         * Wired into `blur_gaussian.wgsl` as `array<vec4f, 9>` (9 *
         * 4 = 36 floats ; we use 33 -- indices 0..32 -- and zero the
         * trailing 3).
         */
        const val MAX_BLUR_RADIUS: Int = 32
        /**
         * Phase MaskFilter-blur -- size of the per-draw blur uniform.
         * Layout (matches `Uniforms` in `blur_gaussian.wgsl`) :
         *   offset   0 : dstOriginSize (vec4f -- dstX, dstY, srcW, srcH)
         *   offset  16 : paintColor    (vec4f -- premul rgba modulation)
         *   offset  32 : axisRadius    (vec4f -- axisX, axisY, radius,
         *                                blurStyle). `blurStyle` is the
         *                                [org.skia.foundation.SkBlurStyle]
         *                                ordinal (0 = kNormal, 1 = kSolid,
         *                                2 = kOuter, 3 = kInner) and is
         *                                only consumed by the V pass.
         *   offset  48 : weights       (array<vec4f, 9> -- 144 bytes,
         *                                36 float slots ; indices 0..32
         *                                hold the symmetric half-kernel,
         *                                indices 33..35 are zeroed pad)
         *   ------                       192 bytes
         */
        const val BLUR_UNIFORM_SIZE: ULong = 192uL
        /** Float count of [BLUR_UNIFORM_SIZE] for FloatArray packing : 48 floats. */
        const val BLUR_UNIFORM_FLOATS: Int = 48
        /**
         * Phase G-saveLayer-imageFilter-blur -- size of the per-pass
         * blur uniform for the ImageFilter blur shader. Layout (matches
         * `Uniforms` in `blur_image_filter.wgsl`) :
         *   offset   0 : dstOriginSize  (vec4f -- 0, 0, srcW, srcH)
         *   offset  16 : axisTileRadius (vec4f -- axisX, axisY,
         *                                tileMode, radius)
         *   offset  32 : weights        (array<vec4f, 9> -- 144 bytes,
         *                                36 float slots ; indices 0..32
         *                                hold the symmetric half-kernel,
         *                                indices 33..35 are zeroed pad)
         *   ------                       176 bytes
         *
         * Smaller than [BLUR_UNIFORM_SIZE] because the ImageFilter blur
         * doesn't fold a paintColor in the shader -- the colour
         * modulation happens on the final composite pass via
         * `layer_composite.wgsl`.
         */
        const val BLUR_IMAGE_FILTER_UNIFORM_SIZE: ULong = 176uL
        /** Float count of [BLUR_IMAGE_FILTER_UNIFORM_SIZE] for FloatArray packing : 44 floats. */
        const val BLUR_IMAGE_FILTER_UNIFORM_FLOATS: Int = 44
        /**
         * G5.3 -- identity column-major 3x3 used as the no-op
         * primaries matrix when [bitmapColorSpaceFor] returns the sRGB
         * fast path. Shared across all draws to avoid re-allocating
         * a fresh FloatArray per [ImageRectDraw].
         */
        val IDENTITY_CS_MATRIX: FloatArray =
            floatArrayOf(1f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f)
        /**
         * G5.3.x -- 7-float parametric TF coefficient array used when
         * `csMode != 2` (mode 0 and mode 1 ignore the TF coefs but the
         * uniform's slot still needs deterministic bytes for the std140
         * alignment to stay stable). Shared across draws.
         *
         * Layout : `(g, a, b, c, d, e, f)` -- same as
         * `SkcmsTransferFunction`. Set to the linear identity TF
         * `(1, 1, 0, 0, 0, 0, 0)` so a stray `csMode = 2` branch with
         * an unset uniform would still produce identity output.
         */
        val IDENTITY_CS_TF_PARAMS: FloatArray =
            floatArrayOf(1f, 1f, 0f, 0f, 0f, 0f, 0f)
        /**
         * G4.1.2 / G2.x -- size of the AA stencil-cover gradient per-draw uniform :
         *   color (16) + viewport (16) + startEnd (16) + countPad (16) +
         *   edgeCountPad (16) + edges (256 * 16) + positions (16 * 16) +
         *   colors (16 * 16) + clipShapeBounds (16) + clipShapeRadiiKind (16)
         *   = 4720 bytes.
         * The leading `color` slot matches the polygon shader's layout so
         * the stencil-write pass can share this draw's bind group. G2.x
         * bumped from 4688 to 4720 to carry the optional analytical
         * clip-shape.
         */
        const val AA_STENCIL_COVER_GRADIENT_UNIFORM_SIZE: ULong = 4720uL
        /**
         * G4.4.3 / G2.x -- size of the AA stencil-cover conical focal-inside
         * per-draw uniform :
         *   color (16) + viewport (16) + affineRow0 (16) + affineRow1 (16) +
         *   focalScalars (16) + flagsCount (16) + edgeCountPad (16) +
         *   edges (256 * 16) + positions (16 * 16) + colors (16 * 16) +
         *   clipShapeBounds (16) + clipShapeRadiiKind (16) = 4752 bytes.
         * 32 bytes larger than the kRadial / linear / radial / sweep stencil-
         * cover uniforms because the focal-frame affine takes 2 vec4f slots
         * and the focal scalars / flags consume two more (vs one in the
         * other variants). G2.x bumped from 4720 to 4752 to carry the
         * optional analytical clip-shape.
         */
        const val AA_STENCIL_COVER_CONICAL_FOCAL_GRADIENT_UNIFORM_SIZE: ULong = 4752uL
        /**
         * G5.2.1 / G5.3.x / G2.x -- size of the AA stencil-cover bitmap-shader per-draw uniform :
         *   color (16) + viewport (16) + srcRect (16) + dstRect (16) +
         *   imageSize (16) + paintColor (16) + csFlags (16) + csMatrix (48) +
         *   csTfParams0 (16) + csTfParams1 (16) +
         *   clipShapeBounds (16) + clipShapeRadiiKind (16) +
         *   edgeCountPad (16) + edges (256 * 16) = 4336 bytes.
         * Mirror of [IMAGE_RECT_UNIFORM_SIZE] (192 bytes) extended with the
         * stencil-cover machinery's `color` / `viewport` / `edgeCountPad`
         * + `edges[256]` tail. The leading `color` slot matches the polygon
         * shader's layout so the bitmap-shader stencil-write pass can share
         * this draw's bind group.
         *
         * G5.3.x added 32 bytes for the parametric TF coefficients
         * (2 vec4f) shared with the rect pipeline's csMode = 2 branch.
         * G2.x added 32 more bytes for the analytical clip-shape slots
         * sitting after the G5.3 colorspace block (so the colorspace
         * block stays contiguous). G5.2.2 added 32 more bytes for the
         * 2x3 device-to-image affine (rotated / skewed bitmap shader),
         * inserted between `clipShapeRadiiKind` and `edgeCountPad` ;
         * the edge tail shifts forward by 32 bytes.
         */
        const val AA_STENCIL_COVER_BITMAP_SHADER_UNIFORM_SIZE: ULong = 4368uL
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
