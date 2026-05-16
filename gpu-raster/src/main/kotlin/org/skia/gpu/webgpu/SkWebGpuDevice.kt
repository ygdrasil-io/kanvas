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
import io.ygdrasil.webgpu.ShaderModuleDescriptor
import io.ygdrasil.webgpu.VertexState
import io.ygdrasil.webgpu.beginRenderPass
import kotlinx.coroutines.runBlocking
import org.skia.core.SkDevice
import org.skia.core.SrcRectConstraint
import org.skia.foundation.SkBlendMode
import org.skia.foundation.SkColorGetA
import org.skia.foundation.SkColorGetB
import org.skia.foundation.SkColorGetG
import org.skia.foundation.SkColorGetR
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
     * One pending draw, accumulated by [drawRect] and replayed by
     * [flush]. Carries both the scissor rect (integer, in device pixels —
     * sets the WebGPU `setScissorRect`) and the fractional bounds
     * (passed to the shader for analytical coverage).
     *
     * For non-AA draws : `(fl, ft, fr, fb)` are the pixelEdge-rounded
     * integers — every interior pixel-center hits the rect by exactly
     * 0.5, so the shader's coverage formula evaluates to 1.0 and the
     * output is byte-identical to the pre-G2.3a unconditional path.
     *
     * For AA draws : the bounds are the original fractional rect and
     * the scissor is the conservative floor/ceil bbox, so edge pixels
     * are visited and get fractional coverage.
     */
    private data class RectDraw(
        val x: Int, val y: Int, val w: Int, val h: Int,
        val fl: Float, val ft: Float, val fr: Float, val fb: Float,
        val r: Float, val g: Float, val b: Float, val a: Float,
        val mode: SkBlendMode,
    )

    private val pending: MutableList<RectDraw> = mutableListOf()

    private val shader: GPUShaderModule = run {
        val wgsl = SkWebGpuDevice::class.java.classLoader
            .getResource("shaders/solid_color.wgsl")?.readText()
            ?: error("shaders/solid_color.wgsl missing from classpath")
        context.device.createShaderModule(ShaderModuleDescriptor(code = wgsl))
    }

    private val bindGroupLayout: GPUBindGroupLayout = context.device.createBindGroupLayout(
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

    private val pipelineLayout = context.device.createPipelineLayout(
        PipelineLayoutDescriptor(bindGroupLayouts = listOf(bindGroupLayout)),
    )

    /**
     * Lazily-populated pipeline cache, one entry per [SkBlendMode] the
     * caller actually exercises. All pipelines share the same vertex /
     * fragment shader, same layout, same target format — only the
     * [BlendState] differs. The cache is bounded by the number of
     * supported modes (4 in G2.2), so no eviction policy is needed.
     */
    private val pipelineCache: MutableMap<SkBlendMode, GPURenderPipeline> = mutableMapOf()

    private fun pipelineFor(mode: SkBlendMode): GPURenderPipeline =
        pipelineCache.getOrPut(mode) {
            context.device.createRenderPipeline(
                RenderPipelineDescriptor(
                    layout = pipelineLayout,
                    vertex = VertexState(module = shader, entryPoint = "vs_main"),
                    fragment = FragmentState(
                        module = shader,
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
        else -> error(
            "SkWebGpuDevice (G2.2): blend mode $mode not supported yet. " +
                "G2.2 covers WebGPU-native Porter-Duff (kClear / kSrc / kSrcOver / kDstOver). " +
                "kPlus / kScreen / kModulate / etc. require fragment-side blending and land in a " +
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
        require(paint.style == SkPaint.Style.kFill_Style) {
            "SkWebGpuDevice (G2.1): only fill-style supported — got ${paint.style}. Strokes arrive in G3."
        }
        // G2.1 — translucent SrcOver supported via premul shader.
        // G2.2 — paint.blendMode honoured (kClear / kSrc / kSrcOver / kDstOver).
        // G2.3a — paint.isAntiAlias honoured via analytical coverage in the
        //          shader. Both AA and non-AA route through the same
        //          pipeline ; only the bounds passed to the shader and the
        //          scissor extent differ.
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

    override fun drawPaint(ctm: SkMatrix, clip: SkIRect, paint: SkPaint) {
        TODO("SkWebGpuDevice.drawPaint — G2+. Will route to a viewport-sized scissor draw with the paint's color.")
    }

    override fun drawPath(path: SkPath, ctm: SkMatrix, clip: SkIRect, paint: SkPaint) {
        TODO("SkWebGpuDevice.drawPath — G3 (CPU path tessellation per master plan D2).")
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

        // Per-draw uniform buffers + bind groups (WebGPU forbids
        // queue.writeBuffer between draws inside a render pass — per-draw
        // resources are the simplest correct fix; dynamic-offset bind
        // groups are a G2+ optimisation).
        val uniforms = pending.map { rd ->
            val buf = context.device.createBuffer(
                BufferDescriptor(
                    size = DRAW_UNIFORM_SIZE,
                    usage = GPUBufferUsage.Uniform or GPUBufferUsage.CopyDst,
                    label = "SkWebGpuDevice.draw",
                ),
            )
            // Layout : color (4 floats, offset 0) + bounds (4 floats,
            // offset 16). Matches `Uniforms { color, bounds }` in
            // solid_color.wgsl.
            context.queue.writeBuffer(
                buf, 0uL,
                ArrayBuffer.of(floatArrayOf(rd.r, rd.g, rd.b, rd.a, rd.fl, rd.ft, rd.fr, rd.fb)),
            )
            buf
        }
        val bindGroups = uniforms.map { buf ->
            context.device.createBindGroup(
                BindGroupDescriptor(
                    layout = bindGroupLayout,
                    entries = listOf(
                        BindGroupEntry(binding = 0u, resource = BufferBinding(buffer = buf)),
                    ),
                ),
            )
        }

        pending.forEachIndexed { i, draw ->
            val loadOp = if (i == 0) GPULoadOp.Clear else GPULoadOp.Load
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
                setPipeline(pipelineFor(draw.mode))
                setBindGroup(0u, bindGroups[i])
                setScissorRect(
                    x = draw.x.toUInt(),
                    y = draw.y.toUInt(),
                    width = draw.w.toUInt(),
                    height = draw.h.toUInt(),
                )
                draw(FULL_SCREEN_TRIANGLE_VERTEX_COUNT)
                end()
            }
        }

        target.encodeCopyToStaging(encoder)
        context.queue.submit(listOf(encoder.finish()))

        val pixels = target.readPixels()

        uniforms.forEach { it.close() }
        pending.clear()
        pixels
    }

    override fun close() {
        pipelineCache.values.forEach { it.close() }
        pipelineCache.clear()
        shader.close()
        target.close()
    }

    private companion object {
        /** Size of the per-draw uniform : `color: vec4f` + `bounds: vec4f` = 32 bytes. */
        const val DRAW_UNIFORM_SIZE: ULong = 32uL
        const val FULL_SCREEN_TRIANGLE_VERTEX_COUNT: UInt = 3u

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
