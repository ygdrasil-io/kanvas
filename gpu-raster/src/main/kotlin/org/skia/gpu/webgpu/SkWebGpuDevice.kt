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
import org.skia.foundation.SkPaint
import org.skia.foundation.SkPath
import org.skia.foundation.SkPathFillType
import org.skia.foundation.SkSamplingOptions
import org.skia.foundation.SkStroker
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
                    is StencilCoverPolygonDraw -> { /* handled above */ }
                    is StencilCoverAaPolygonDraw -> { /* handled above */ }
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
        stencilWritePipeline.close()
        presentPipeline.close()
        rectShader.close()
        polygonShader.close()
        aaPolygonShader.close()
        aaStencilCoverShader.close()
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
