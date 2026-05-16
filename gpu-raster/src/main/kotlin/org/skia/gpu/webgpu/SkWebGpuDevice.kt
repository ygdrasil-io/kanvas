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
import io.ygdrasil.webgpu.FragmentState
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
import io.ygdrasil.webgpu.GPUBuffer
import io.ygdrasil.webgpu.GPUVertexFormat
import io.ygdrasil.webgpu.ShaderModuleDescriptor
import io.ygdrasil.webgpu.VertexAttribute
import io.ygdrasil.webgpu.VertexBufferLayout
import io.ygdrasil.webgpu.VertexState
import io.ygdrasil.webgpu.beginRenderPass
import kotlinx.coroutines.runBlocking
import org.skia.core.SkDevice
import org.skia.core.SrcRectConstraint
import org.skia.foundation.SkBlendMode
import org.skia.math.SkColorGetA
import org.skia.math.SkColorGetB
import org.skia.math.SkColorGetG
import org.skia.math.SkColorGetR
import org.skia.foundation.SkImage
import org.skia.foundation.SkPaint
import org.skia.foundation.SkPath
import org.skia.foundation.SkSamplingOptions
import org.skia.math.SkIRect
import org.skia.math.SkMatrix
import org.skia.math.SkRect
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
) : SkDevice, AutoCloseable {

    private val target: HeadlessTarget =
        HeadlessTarget(context, width, height, GPUTextureFormat.RGBA8Unorm)

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
     * paired with the fractional bounds passed to the shader for
     * analytical coverage. Non-AA collapses bounds to integers ; AA keeps
     * the originals and uses the conservative floor/ceil scissor.
     */
    private data class RectDraw(
        val x: Int, val y: Int, val w: Int, val h: Int,
        val fl: Float, val ft: Float, val fr: Float, val fb: Float,
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
                fl = bounds[0], ft = bounds[1], fr = bounds[2], fb = bounds[3],
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
     * Hairline rect stroke — `strokeWidth <= 0` in Skia means a 1-device-pixel
     * outline snapped to floor-style integer coords (matches
     * `SkScan::HairLineRgn` and [org.skia.core.SkBitmapDevice.strokeRect]'s
     * hairline branch). Always non-AA in this slice — true AA hairlines are
     * a follow-up if a GM demands it. The hairline `paint` is swapped to
     * `isAntiAlias = false` for the sub-draws so the shader's coverage
     * formula collapses to 1.0 on the integer-aligned 1px edges.
     */
    private fun drawHairlineRect(rect: SkRect, clip: SkIRect, paint: SkPaint) {
        val l = floor(rect.left.toDouble()).toFloat()
        val t = floor(rect.top.toDouble()).toFloat()
        val r = floor(rect.right.toDouble()).toFloat()
        val b = floor(rect.bottom.toDouble()).toFloat()
        val nonAaPaint = paint.copy().apply { isAntiAlias = false }
        drawFillRect(SkRect.MakeLTRB(l,     t,     r + 1, t + 1), clip, nonAaPaint) // top
        drawFillRect(SkRect.MakeLTRB(l,     b,     r + 1, b + 1), clip, nonAaPaint) // bottom
        drawFillRect(SkRect.MakeLTRB(l,     t + 1, l + 1, b),     clip, nonAaPaint) // left
        drawFillRect(SkRect.MakeLTRB(r,     t + 1, r + 1, b),     clip, nonAaPaint) // right
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
        require(paint.style == SkPaint.Style.kFill_Style) {
            "SkWebGpuDevice (G3.3b.1): only fill-style paths supported — got ${paint.style}. " +
                "Stroke paths (SkStroker.outline) arrive in G3.4."
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
        var contourCount = 0
        var ci = 0
        var wi = 0
        var px = 0f; var py = 0f
        for (verb in path.verbs) {
            when (verb) {
                SkPath.Verb.kMove, SkPath.Verb.kLine -> {
                    val sx = path.coords[ci++]
                    val sy = path.coords[ci++]
                    val (dx, dy) = ctm.mapXY(sx, sy)
                    devVerts.add(dx); devVerts.add(dy)
                    px = dx; py = dy
                    if (verb == SkPath.Verb.kMove) contourCount++
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

        // Fan tessellation from vertex 0 : triangles (0, 1, 2), (0, 2, 3), …,
        // (0, n-2, n-1). Convex-correct ; concave paths get artefacts.
        val triCount = n - 2
        val tri = FloatArray(triCount * 6)
        var w = 0
        for (i in 1 until n - 1) {
            tri[w++] = devVerts[0]; tri[w++] = devVerts[1]
            tri[w++] = devVerts[i * 2]; tri[w++] = devVerts[i * 2 + 1]
            tri[w++] = devVerts[(i + 1) * 2]; tri[w++] = devVerts[(i + 1) * 2 + 1]
        }

        // Honour the device clip via scissor (axis-aligned int clip).
        // For G3.3a we leave the polygon's triangles intact and let
        // setScissorRect cull pixels outside `clip`.
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

        // G3.3b.2a — AA path : compute polygon perimeter edge equations
        // and route to the analytical-coverage pipeline. Restricted to
        // single-contour paths within MAX_AA_EDGES vertices ; multi-
        // contour and very large paths fall back to non-AA fan tess
        // (G3.3b.2b will lift these restrictions).
        //
        // Key trick : the AA path renders the polygon's **bounding box
        // (slightly inflated)** as 2 triangles instead of the fan tess.
        // Reason : the fan triangles share their outer edges with the
        // polygon perimeter ; the GPU rasterizer's top-left edge rule
        // can exclude pixel centres that sit exactly on those edges,
        // robbing the fragment shader of the chance to compute their
        // coverage. The bbox triangles are axis-aligned and inflated
        // by 1 pixel beyond the polygon ; every pixel near a polygon
        // edge is reliably rasterized, and the shader's coverage
        // (`min` over edge equations) masks the bbox down to the
        // actual polygon shape, with smooth fall-off on the perimeter.
        if (paint.isAntiAlias && contourCount == 1 && n <= MAX_AA_EDGES) {
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
        val colorView = target.colorTexture.createView()

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
            }
        }

        pending.forEachIndexed { i, d ->
            val loadOp = if (i == 0) GPULoadOp.Clear else GPULoadOp.Load
            val res = perDrawResources[i]
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
                }
                end()
            }
        }

        target.encodeCopyToStaging(encoder)
        context.queue.submit(listOf(encoder.finish()))

        val pixels = target.readPixels()

        perDrawResources.forEach { it.uniform.close(); it.vertexBuffer?.close() }
        pending.clear()
        pixels
    }

    /**
     * Per-draw GPU resources lifetime-managed by [flush] : the uniform
     * buffer + its bind group, and (for polygon draws only) the vertex
     * buffer holding the triangle list.
     */
    private data class DrawResources(
        val uniform: GPUBuffer,
        val bindGroup: io.ygdrasil.webgpu.GPUBindGroup,
        val vertexBuffer: GPUBuffer? = null,
    )

    private fun buildRectDrawResources(d: RectDraw): DrawResources {
        val buf = context.device.createBuffer(
            BufferDescriptor(
                size = RECT_UNIFORM_SIZE,
                usage = GPUBufferUsage.Uniform or GPUBufferUsage.CopyDst,
                label = "SkWebGpuDevice.rectDraw",
            ),
        )
        // Layout : color (4 floats) + bounds (4 floats) = 32 bytes.
        // Matches `Uniforms { color, bounds }` in solid_color.wgsl.
        context.queue.writeBuffer(
            buf, 0uL,
            ArrayBuffer.of(floatArrayOf(d.r, d.g, d.b, d.a, d.fl, d.ft, d.fr, d.fb)),
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

    override fun close() {
        rectPipelineCache.values.forEach { it.close() }
        rectPipelineCache.clear()
        polygonPipelineCache.values.forEach { it.close() }
        polygonPipelineCache.clear()
        aaPolygonPipelineCache.values.forEach { it.close() }
        aaPolygonPipelineCache.clear()
        rectShader.close()
        polygonShader.close()
        aaPolygonShader.close()
        target.close()
    }

    private companion object {
        /** Size of the rect per-draw uniform : `color: vec4f` + `bounds: vec4f` = 32 bytes. */
        const val RECT_UNIFORM_SIZE: ULong = 32uL
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
         * Build a 2-triangle bbox covering the polygon, inflated by 1
         * device pixel outward and clamped to the viewport. Used by the
         * AA polygon path so every near-edge pixel reaches the fragment
         * shader for coverage evaluation.
         */
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
