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
import io.ygdrasil.webgpu.GPUColorWrite
import io.ygdrasil.webgpu.GPUCompareFunction
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
import io.ygdrasil.webgpu.ShaderModuleDescriptor
import io.ygdrasil.webgpu.TextureBindingLayout
import io.ygdrasil.webgpu.TextureDescriptor
import io.ygdrasil.webgpu.VertexAttribute
import io.ygdrasil.webgpu.VertexBufferLayout
import io.ygdrasil.webgpu.VertexState
import io.ygdrasil.webgpu.beginRenderPass
import kotlinx.coroutines.runBlocking
import org.skia.core.SkDevice
import org.skia.core.SrcRectConstraint
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
     */
    private val intermediateTexture: GPUTexture = context.device.createTexture(
        TextureDescriptor(
            size = Extent3D(width = width.toUInt(), height = height.toUInt()),
            format = GPUTextureFormat.RGBA8Unorm,
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
     * key) for symmetry with the linear / radial gradients ; only kClamp
     * routes here today (the dispatch gate throws otherwise -- see G4.3.1).
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
     * G4.4 -- pending conical (two-point) gradient fill of an axis-aligned
     * rect. Skeleton supports only the **kRadial** sub-case of
     * [SkConicalGradient] (concentric circles, `c0 == c1`) under kClamp ;
     * other sub-cases (kStrip, kFocal) and other tile modes fall through
     * at the dispatch gate to the existing solid-color fill machinery.
     *
     * `centerX` / `centerY` are the shared centre in device-pixel coords
     * (already CTM-mapped) ; `startRadius` / `endRadius` are the start /
     * end circle radii scaled by `ctm.getMaxScale()` (axis-aligned-CTM
     * gate collapses this to `max(|sx|, |sy|)`). The shader evaluates
     * `t = (length(p - c1) - r0) / (r1 - r0)` and looks up the stops via
     * the per-tile-mode entry point.
     *
     * [tileMode] is kept in the per-draw record (and in the pipeline
     * cache key) for symmetry with the linear / radial / sweep gradients
     * even though only kClamp routes here today ; the other 3 entry
     * points exist in `conical_gradient.wgsl` for the G4.4.1 follow-up
     * widening the dispatch.
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
                                format = GPUTextureFormat.RGBA8Unorm,
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
                                format = GPUTextureFormat.RGBA8Unorm,
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
                        format = GPUTextureFormat.RGBA8Unorm,
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
                                format = GPUTextureFormat.RGBA8Unorm,
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
                                format = GPUTextureFormat.RGBA8Unorm,
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
                                format = GPUTextureFormat.RGBA8Unorm,
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
                                format = GPUTextureFormat.RGBA8Unorm,
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
                                format = GPUTextureFormat.RGBA8Unorm,
                                blend = blendStateFor(mode),
                            ),
                        ),
                    ),
                ),
            )
        }

    // ─── Sweep gradient pipeline (G4.3) — kClamp tile mode, drawRect ──────

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
     * G4.3 -- sweep-gradient pipeline cache. Keyed by `(blend, tile)` from
     * day 1 even though only kClamp dispatches here ; the other 3 fragment
     * entry points (fs_repeat / fs_mirror / fs_decal) exist for future-
     * readiness, mirroring the linear / radial caches. A follow-up slice
     * (G4.3.1) opens the dispatch gate to the other tile modes without
     * touching this cache key.
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
                                format = GPUTextureFormat.RGBA8Unorm,
                                blend = blendStateFor(mode),
                            ),
                        ),
                    ),
                ),
            )
        }

    // ─── Conical gradient pipeline (G4.4) — kRadial sub-case, kClamp, drawRect ──

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
     * G4.4 -- conical-gradient pipeline cache. Keyed by `(blend, tile)`
     * from day 1 even though only kClamp dispatches here ; the other 3
     * fragment entry points (`fs_repeat / fs_mirror / fs_decal`) exist
     * in `conical_gradient.wgsl` for the G4.4.1 follow-up that widens
     * the dispatch gate (mirrors the radial / sweep cache layouts).
     *
     * The pipeline itself doesn't distinguish between conical sub-cases ;
     * the host only routes the kRadial sub-case here today (see the
     * dispatch gate in [drawPath]). A future kFocal pipeline can share
     * this cache key with a different shader file (or extend
     * `conical_gradient.wgsl` with a typeFlag uniform and a focal-frame
     * affine -- the bind-group-layout shape stays identical).
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
                                format = GPUTextureFormat.RGBA8Unorm,
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
                                format = GPUTextureFormat.RGBA8Unorm,
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
                                format = GPUTextureFormat.RGBA8Unorm,
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
     * G4.3 -- emit a [SweepGradientRectDraw] for a kClamp `SkSweepGradient`
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
     * Tile modes : only kClamp routes here today ; the dispatch gate at the
     * top of [drawPath] already filters non-kClamp callers, so reaching this
     * function with any other tile mode is a programming error (the pipeline
     * cache still wires all 4 entry points for future-readiness via G4.3.1).
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
     * G4.4 -- emit a [ConicalGradientRectDraw] for a kClamp `SkConicalGradient`
     * fill of the axis-aligned device-space rect [devRect]. Scissor derivation
     * is identical to [drawRadialGradientFillRect].
     *
     * Only the **kRadial** sub-case (concentric circles, `c0 == c1`) is
     * routed here ; the dispatch gate at the top of [drawPath] already
     * filtered other sub-cases. The shared centre is mapped through [ctm]
     * to device space ; the start / end radii are scaled by
     * `ctm.getMaxScale()` (collapses to `max(|sx|, |sy|)` under the
     * axis-aligned-CTM gate). The shader evaluates
     *   t = (length(p - c) - r0) / (r1 - r0)
     * and the per-tile-mode entry point clamps / wraps / mirrors / decals
     * the result before the stops lookup (only fs_clamp is reachable
     * today -- see G4.4.1 follow-up).
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
        // CTM is unused : drawPaint already operates in device coords by
        // contract (matches SkBitmapDevice.drawPaint).
        //
        // Limitation : paint.shader is silently ignored (same as on
        // drawRect today). Shader support lands with G4 (gradients +
        // bitmap shaders).
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
        // G4.3 -- sweep gradient fill of an axis-aligned rect, kClamp only.
        // Same gate shape as the linear / radial branches (path.isRect +
        // axis-aligned CTM) but additionally requires `tileMode == kClamp`
        // until G4.3.1 widens the dispatch to the other tile modes (the
        // pipeline cache already wires fs_repeat / fs_mirror / fs_decal,
        // so that's a strict superset of this change). Non-rect paths,
        // rotated/skewed CTMs and the other tile modes fall through to the
        // existing solid-color fill machinery (paint.color, pre-G4.3
        // fallback).
        if (shader is SkSweepGradient &&
            paint.style == SkPaint.Style.kFill_Style &&
            ctm.isAxisAligned &&
            shader.getTileMode() == SkTileMode.kClamp
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
        // G4.4 -- conical gradient fill of an axis-aligned rect, skeleton.
        // Only the **kRadial** sub-case (concentric circles, `c0 == c1` ;
        // SkConicalGradient.Make tags this `Type.kRadial`) routes through
        // the dedicated pipeline today, and only under kClamp tile mode.
        // Other sub-cases (kStrip, kFocal in any of its variants) and
        // other tile modes fall through to the existing solid-color fill
        // machinery -- a G4.4.x follow-up adds focal-inside (the most
        // common kFocal case) ; G4.4.1 widens to the other tile modes
        // for the kRadial sub-case (the pipeline cache already wires all
        // 4 entry points). Non-rect paths similarly defer to a later
        // slice (G4.4.2 = conical-on-non-rect-stencil-and-cover).
        if (shader is SkConicalGradient &&
            paint.style == SkPaint.Style.kFill_Style &&
            ctm.isAxisAligned &&
            shader.getTileMode() == SkTileMode.kClamp &&
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
        val linearGradForAaPath: SkLinearGradient? =
            if (shader is SkLinearGradient && paint.isAntiAlias && ctm.isAxisAligned) shader
            else null
        val radialGradForAaPath: SkRadialGradient? =
            if (shader is SkRadialGradient && paint.isAntiAlias && ctm.isAxisAligned) shader
            else null
        val gradEndpoints: FloatArray?
        val gradCenterRadius: FloatArray?
        val gradPositions: FloatArray?
        val gradColors: FloatArray?
        val gradStopCount: Int
        val gradTileMode: SkTileMode?
        if (linearGradForAaPath != null || radialGradForAaPath != null) {
            val srcColors: IntArray
            val positions: FloatArray
            if (linearGradForAaPath != null) {
                val p0 = linearGradForAaPath.getStartPoint()
                val p1 = linearGradForAaPath.getEndPoint()
                val (sx, sy) = ctm.mapXY(p0.fX, p0.fY)
                val (ex, ey) = ctm.mapXY(p1.fX, p1.fY)
                gradEndpoints = floatArrayOf(sx, sy, ex, ey)
                gradCenterRadius = null
                srcColors = linearGradForAaPath.getColors()
                positions = linearGradForAaPath.getPositions()
                gradTileMode = linearGradForAaPath.getTileMode()
            } else {
                val center = radialGradForAaPath!!.getCenter()
                val (cx, cy) = ctm.mapXY(center.fX, center.fY)
                val devRadius = radialGradForAaPath.getRadius() * ctm.getMaxScale()
                gradEndpoints = null
                gradCenterRadius = floatArrayOf(cx, cy, devRadius)
                srcColors = radialGradForAaPath.getColors()
                positions = radialGradForAaPath.getPositions()
                gradTileMode = radialGradForAaPath.getTileMode()
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
        } else {
            gradEndpoints = null
            gradCenterRadius = null
            gradPositions = null
            gradColors = null
            gradStopCount = 0
            gradTileMode = null
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

    override fun drawImageRect(
        image: SkImage,
        src: SkRect,
        devDst: SkRect,
        sampling: SkSamplingOptions,
        paint: SkPaint?,
        constraint: SrcRectConstraint,
        clip: SkIRect,
    ) {
        TODO("SkWebGpuDevice.drawImageRect — G5 (BitmapShader + texture upload).")
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
                is LinearGradientRectDraw -> buildLinearGradientRectDrawResources(d)
                is RadialGradientRectDraw -> buildRadialGradientRectDrawResources(d)
                is SweepGradientRectDraw -> buildSweepGradientRectDrawResources(d)
                is ConicalGradientRectDraw -> buildConicalGradientRectDrawResources(d)
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
                    is StencilCoverPolygonDraw -> { /* handled above */ }
                    is StencilCoverAaPolygonDraw -> { /* handled above */ }
                    is StencilCoverAaGradientDraw -> { /* handled above */ }
                    is StencilCoverAaRadialGradientDraw -> { /* handled above */ }
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
        linearGradientPipelineCache.values.forEach { it.close() }
        linearGradientPipelineCache.clear()
        radialGradientPipelineCache.values.forEach { it.close() }
        radialGradientPipelineCache.clear()
        sweepGradientPipelineCache.values.forEach { it.close() }
        sweepGradientPipelineCache.clear()
        stencilWritePipeline.close()
        presentPipeline.close()
        rectShader.close()
        polygonShader.close()
        aaPolygonShader.close()
        aaStencilCoverShader.close()
        aaStencilCoverGradientShader.close()
        aaStencilCoverRadialGradientShader.close()
        linearGradientShader.close()
        radialGradientShader.close()
        sweepGradientShader.close()
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
         * G4.1.2 -- size of the AA stencil-cover gradient per-draw uniform :
         *   color (16) + viewport (16) + startEnd (16) + countPad (16) +
         *   edgeCountPad (16) + edges (256 * 16) + positions (16 * 16) +
         *   colors (16 * 16) = 4688 bytes.
         * The leading `color` slot matches the polygon shader's layout so
         * the stencil-write pass can share this draw's bind group.
         */
        const val AA_STENCIL_COVER_GRADIENT_UNIFORM_SIZE: ULong = 4688uL
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
