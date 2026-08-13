package org.graphiks.kanvas.gpu.renderer.execution

import io.ygdrasil.webgpu.BindGroupLayoutDescriptor
import io.ygdrasil.webgpu.BindGroupLayoutEntry
import io.ygdrasil.webgpu.BlendComponent
import io.ygdrasil.webgpu.BlendState
import io.ygdrasil.webgpu.BufferBindingLayout
import io.ygdrasil.webgpu.ColorTargetState
import io.ygdrasil.webgpu.DepthStencilState
import io.ygdrasil.webgpu.Extent3D
import io.ygdrasil.webgpu.FragmentState
import io.ygdrasil.webgpu.GPUAddressMode
import io.ygdrasil.webgpu.GPUBindGroupLayout
import io.ygdrasil.webgpu.GPUBlendFactor
import io.ygdrasil.webgpu.GPUBlendOperation
import io.ygdrasil.webgpu.GPUBufferBindingType
import io.ygdrasil.webgpu.GPUCompareFunction
import io.ygdrasil.webgpu.GPUDevice
import io.ygdrasil.webgpu.GPUFilterMode
import io.ygdrasil.webgpu.GPUPipelineLayout
import io.ygdrasil.webgpu.GPURenderPipeline
import io.ygdrasil.webgpu.GPUSampler
import io.ygdrasil.webgpu.GPUSamplerBindingType
import io.ygdrasil.webgpu.GPUShaderModule
import io.ygdrasil.webgpu.GPUShaderStage
import io.ygdrasil.webgpu.GPUTexture
import io.ygdrasil.webgpu.GPUTextureFormat
import io.ygdrasil.webgpu.GPUTextureSampleType
import io.ygdrasil.webgpu.GPUTextureUsage
import io.ygdrasil.webgpu.GPUTextureView
import io.ygdrasil.webgpu.GPUTextureViewDimension
import io.ygdrasil.webgpu.MultisampleState
import io.ygdrasil.webgpu.PipelineLayoutDescriptor
import io.ygdrasil.webgpu.PrimitiveState
import io.ygdrasil.webgpu.RenderPipelineDescriptor
import io.ygdrasil.webgpu.SamplerBindingLayout
import io.ygdrasil.webgpu.SamplerDescriptor
import io.ygdrasil.webgpu.ShaderModuleDescriptor
import io.ygdrasil.webgpu.StencilFaceState
import io.ygdrasil.webgpu.TextureBindingLayout
import io.ygdrasil.webgpu.TextureDescriptor
import io.ygdrasil.webgpu.VertexState
import org.graphiks.kanvas.gpu.renderer.recording.MASK_BLUR_COMPOSITE_CLIP_DST_WGSL
import org.graphiks.kanvas.gpu.renderer.recording.MASK_BLUR_COMPOSITE_DST_WGSL
import org.graphiks.kanvas.gpu.renderer.recording.TOP_LEVEL_MASK_BLUR_LAYOUT_BLUR
import org.graphiks.kanvas.gpu.renderer.recording.TOP_LEVEL_MASK_BLUR_LAYOUT_COMPOSITE
import org.graphiks.kanvas.gpu.renderer.recording.TOP_LEVEL_MASK_BLUR_LAYOUT_COMPOSITE_DST
import org.graphiks.kanvas.gpu.renderer.recording.TOP_LEVEL_MASK_BLUR_LAYOUT_MASK
import org.graphiks.kanvas.gpu.renderer.recording.TOP_LEVEL_MASK_BLUR_LAYOUT_STYLE

internal data class GPUMaskBlurNativeCacheCounters(
    val invariantCreations: Long = 0L,
    val invariantReuses: Long = 0L,
    val intermediateCreations: Long = 0L,
    val intermediateReuses: Long = 0L,
)

/** Composite pipelines bound to one exact scene color format (SRGB scene variants included). */
internal class GPUWgpu4kMaskBlurCompositePipelines(
    val sceneFormat: GPUTextureFormat,
    val srcOverPipeline: GPURenderPipeline,
    val srcPipeline: GPURenderPipeline,
    val dstPipeline: GPURenderPipeline,
    /** Fullscreen solid-color SRC_OVER pass for non-blur SolidRect scene renders. */
    val solidPipeline: GPURenderPipeline,
    private val owned: GPUMaskBlurCachedHandleSet,
) : AutoCloseable by owned

/**
 * Clip-variant composite pipelines for one exact scene color format (Task 7): the
 * same src-over/src/dst program shapes with an extra analytic-clip uniform64 binding
 * so the composite shader can multiply the blurred mask coverage by the clip coverage.
 */
internal class GPUWgpu4kMaskBlurCompositeClipPipelines(
    val sceneFormat: GPUTextureFormat,
    val srcOverPipeline: GPURenderPipeline,
    val srcPipeline: GPURenderPipeline,
    val dstPipeline: GPURenderPipeline,
    private val owned: GPUMaskBlurCachedHandleSet,
) : AutoCloseable by owned

internal class GPUWgpu4kMaskBlurInvariantHandles(
    val maskBindGroupLayout: GPUBindGroupLayout,
    val blurBindGroupLayout: GPUBindGroupLayout,
    val styleBindGroupLayout: GPUBindGroupLayout,
    val compositeBindGroupLayout: GPUBindGroupLayout,
    val compositeDstBindGroupLayout: GPUBindGroupLayout,
    val compositeClipBindGroupLayout: GPUBindGroupLayout,
    val compositeDstClipBindGroupLayout: GPUBindGroupLayout,
    val maskPipeline: GPURenderPipeline,
    val blurHPipeline: GPURenderPipeline,
    val blurVPipeline: GPURenderPipeline,
    val stylePipeline: GPURenderPipeline,
    val sampler: GPUSampler,
    private val owned: GPUMaskBlurCachedHandleSet,
) : AutoCloseable by owned

internal class GPUWgpu4kMaskBlurIntermediateHandles(
    val width: Int,
    val height: Int,
    val maskTexture: GPUTexture,
    val maskView: GPUTextureView,
    val horizontalTexture: GPUTexture,
    val horizontalView: GPUTextureView,
    val verticalTexture: GPUTexture,
    val verticalView: GPUTextureView,
    val styledTexture: GPUTexture,
    val styledView: GPUTextureView,
    private val owned: GPUMaskBlurCachedHandleSet,
) : AutoCloseable by owned

internal data class GPUWgpu4kMaskBlurCacheLease(
    val invariants: GPUWgpu4kMaskBlurInvariantHandles,
    val intermediates: GPUWgpu4kMaskBlurIntermediateHandles,
)

/**
 * Session cache for the prepared top-level mask blur lane (Task 11): static coverage,
 * separable-blur, style, and composite programs plus the serialized frame intermediates
 * (mask / blur-h / blur-v / styled).
 */
internal class GPUWgpu4kMaskBlurSessionCache(
    private val device: GPUDevice,
) : AutoCloseable {
    private val preRegistrationHandles = GPUPreRegistrationNativeHandleLedger()
    private var invariants: GPUWgpu4kMaskBlurInvariantHandles? = null
    private var intermediates: GPUWgpu4kMaskBlurIntermediateHandles? = null
    private val compositePipelinesByFormat =
        linkedMapOf<GPUTextureFormat, GPUWgpu4kMaskBlurCompositePipelines>()
    private val compositeClipPipelinesByFormat =
        linkedMapOf<GPUTextureFormat, GPUWgpu4kMaskBlurCompositeClipPipelines>()
    private var closed = false
    private var invariantCreations = 0L
    private var invariantReuses = 0L
    private var intermediateCreations = 0L
    private var intermediateReuses = 0L

    @Synchronized
    fun acquire(width: Int, height: Int): GPUWgpu4kMaskBlurCacheLease {
        check(!closed) { "The mask blur native session cache is closed" }
        require(width in 1..4096 && height in 1..4096) {
            "The top-level mask blur lane accepts local masks in 1..4096"
        }
        val invariantHandles = invariants?.also { invariantReuses += 1L } ?: run {
            requireCleanSetupLedger()
            try {
                createInvariants().also {
                    invariants = it
                    preRegistrationHandles.transferAll()
                    invariantCreations += 1L
                }
            } catch (failure: Throwable) {
                preRegistrationHandles.closeRetainingFailures()
                throw failure
            }
        }
        val existing = intermediates
        val intermediateHandles = if (existing != null && existing.width == width && existing.height == height) {
            intermediateReuses += 1L
            existing
        } else {
            existing?.close()
            requireCleanSetupLedger()
            try {
                createIntermediates(width, height).also {
                    intermediates = it
                    preRegistrationHandles.transferAll()
                    intermediateCreations += 1L
                }
            } catch (failure: Throwable) {
                preRegistrationHandles.closeRetainingFailures()
                throw failure
            }
        }
        return GPUWgpu4kMaskBlurCacheLease(invariantHandles, intermediateHandles)
    }

    /** Returns (creating once per scene format) the composite pipeline set for the scene target. */
    @Synchronized
    fun acquireCompositePipelines(sceneFormat: GPUTextureFormat): GPUWgpu4kMaskBlurCompositePipelines {
        check(!closed) { "The mask blur native session cache is closed" }
        return compositePipelinesByFormat[sceneFormat] ?: run {
            requireCleanSetupLedger()
            try {
                createCompositePipelines(sceneFormat).also { set ->
                    compositePipelinesByFormat[sceneFormat] = set
                    preRegistrationHandles.transferAll()
                }
            } catch (failure: Throwable) {
                preRegistrationHandles.closeRetainingFailures()
                throw failure
            }
        }
    }

    /**
     * Returns (creating once per scene format) the clip-variant composite pipeline set
     * (Task 7), used only when a mask blur composite carries an admitted analytic
     * device-rect clip so clip-less frames keep the plain pipeline sets untouched.
     */
    @Synchronized
    fun acquireCompositeClipPipelines(sceneFormat: GPUTextureFormat): GPUWgpu4kMaskBlurCompositeClipPipelines {
        check(!closed) { "The mask blur native session cache is closed" }
        return compositeClipPipelinesByFormat[sceneFormat] ?: run {
            requireCleanSetupLedger()
            try {
                createCompositeClipPipelines(sceneFormat).also { set ->
                    compositeClipPipelinesByFormat[sceneFormat] = set
                    preRegistrationHandles.transferAll()
                }
            } catch (failure: Throwable) {
                preRegistrationHandles.closeRetainingFailures()
                throw failure
            }
        }
    }

    @Synchronized
    fun counters(): GPUMaskBlurNativeCacheCounters = GPUMaskBlurNativeCacheCounters(
        invariantCreations,
        invariantReuses,
        intermediateCreations,
        intermediateReuses,
    )

    @Synchronized
    override fun close() {
        if (closed && invariants == null && intermediates == null &&
            preRegistrationHandles.pendingHandleCount == 0
        ) return
        closed = true
        var firstFailure: Throwable? = null
        intermediates?.let { handles ->
            try {
                handles.close()
                intermediates = null
            } catch (failure: Throwable) {
                firstFailure = failure
            }
        }
        invariants?.let { handles ->
            try {
                handles.close()
                invariants = null
            } catch (failure: Throwable) {
                if (firstFailure == null) firstFailure = failure else firstFailure.addSuppressed(failure)
            }
        }
        if (!preRegistrationHandles.closeRetainingFailures()) {
            val failure = IllegalStateException(
                "Mask blur session cache retained " +
                    "${preRegistrationHandles.pendingHandleCount} failed setup handle(s)",
            )
            if (firstFailure == null) firstFailure = failure else firstFailure.addSuppressed(failure)
        }
        firstFailure?.let { throw it }
        compositePipelinesByFormat.values.forEach { set ->
            try {
                set.close()
            } catch (failure: Throwable) {
                if (firstFailure == null) firstFailure = failure else firstFailure.addSuppressed(failure)
            }
        }
        compositePipelinesByFormat.clear()
        compositeClipPipelinesByFormat.values.forEach { set ->
            try {
                set.close()
            } catch (failure: Throwable) {
                if (firstFailure == null) firstFailure = failure else firstFailure.addSuppressed(failure)
            }
        }
        compositeClipPipelinesByFormat.clear()
    }

    private fun createCompositePipelines(sceneFormat: GPUTextureFormat): GPUWgpu4kMaskBlurCompositePipelines {
        val pending = mutableListOf<AutoCloseable>()
        fun <T : AutoCloseable> T.track(): T = also {
            pending += it
            preRegistrationHandles.track(it)
        }
        return try {
            val compositeLayout = requireNotNull(invariants).compositeBindGroupLayout
            val compositeDstLayout = requireNotNull(invariants).compositeDstBindGroupLayout
            val compositeShader = requireNotNull(invariants).let { _ ->
                device.createShaderModule(
                    ShaderModuleDescriptor(
                        label = "Kanvas.session.maskBlur.composite.shader",
                        code = MASK_BLUR_COMPOSITE_WGSL,
                    ),
                ).track()
            }
            val compositeDstShader = device.createShaderModule(
                ShaderModuleDescriptor(
                    label = "Kanvas.session.maskBlur.compositeDst.shader",
                    code = MASK_BLUR_COMPOSITE_DST_WGSL,
                ),
            ).track()


            fun layout(bindGroupLayout: GPUBindGroupLayout): GPUPipelineLayout = device.createPipelineLayout(
                PipelineLayoutDescriptor(
                    label = "Kanvas.session.maskBlur.compositePipelineLayout",
                    bindGroupLayouts = listOf(bindGroupLayout),
                ),
            ).track()
            val solidShader = device.createShaderModule(
                ShaderModuleDescriptor(
                    label = "Kanvas.session.maskBlur.solid.shader",
                    code = MASK_BLUR_SOLID_WGSL,
                ),
            ).track()
            val solidLayout = device.createPipelineLayout(
                PipelineLayoutDescriptor(
                    label = "Kanvas.session.maskBlur.solidPipelineLayout",
                    bindGroupLayouts = listOf(requireNotNull(invariants).maskBindGroupLayout),
                ),
            ).track()
            val srcOverPipeline = compositePipeline(
                "composite-src-over",
                layout(compositeLayout),
                compositeShader,
                srcOverBlendState(),
                sceneFormat,
            ).track()
            val srcPipeline = compositePipeline(
                "composite-src",
                layout(compositeLayout),
                compositeShader,
                replaceBlendState(),
                sceneFormat,
            ).track()
            val dstPipeline = compositePipeline(
                "composite-dst",
                layout(compositeDstLayout),
                compositeDstShader,
                replaceBlendState(),
                sceneFormat,
            ).track()
            val solidPipeline = compositePipeline(
                "solid",
                solidLayout,
                solidShader,
                srcOverBlendState(),
                sceneFormat,
            ).track()
            GPUWgpu4kMaskBlurCompositePipelines(
                sceneFormat,
                srcOverPipeline,
                srcPipeline,
                dstPipeline,
                solidPipeline,
                GPUMaskBlurCachedHandleSet(pending.toList()),
            )
        } catch (failure: Throwable) {
            throw failure
        }
    }

    private fun createCompositeClipPipelines(sceneFormat: GPUTextureFormat): GPUWgpu4kMaskBlurCompositeClipPipelines {
        val pending = mutableListOf<AutoCloseable>()
        fun <T : AutoCloseable> T.track(): T = also {
            pending += it
            preRegistrationHandles.track(it)
        }
        return try {
            val compositeClipLayout = requireNotNull(invariants).compositeClipBindGroupLayout
            val compositeDstClipLayout = requireNotNull(invariants).compositeDstClipBindGroupLayout
            val compositeClipShader = device.createShaderModule(
                ShaderModuleDescriptor(
                    label = "Kanvas.session.maskBlur.compositeClip.shader",
                    code = MASK_BLUR_COMPOSITE_CLIP_WGSL,
                ),
            ).track()
            val compositeDstClipShader = device.createShaderModule(
                ShaderModuleDescriptor(
                    label = "Kanvas.session.maskBlur.compositeDstClip.shader",
                    code = MASK_BLUR_COMPOSITE_CLIP_DST_WGSL,
                ),
            ).track()

            fun layout(bindGroupLayout: GPUBindGroupLayout): GPUPipelineLayout = device.createPipelineLayout(
                PipelineLayoutDescriptor(
                    label = "Kanvas.session.maskBlur.compositeClipPipelineLayout",
                    bindGroupLayouts = listOf(bindGroupLayout),
                ),
            ).track()
            val srcOverPipeline = compositePipeline(
                "composite-src-over-clip",
                layout(compositeClipLayout),
                compositeClipShader,
                srcOverBlendState(),
                sceneFormat,
            ).track()
            val srcPipeline = compositePipeline(
                "composite-src-clip",
                layout(compositeClipLayout),
                compositeClipShader,
                replaceBlendState(),
                sceneFormat,
            ).track()
            val dstPipeline = compositePipeline(
                "composite-dst-clip",
                layout(compositeDstClipLayout),
                compositeDstClipShader,
                replaceBlendState(),
                sceneFormat,
            ).track()
            GPUWgpu4kMaskBlurCompositeClipPipelines(
                sceneFormat,
                srcOverPipeline,
                srcPipeline,
                dstPipeline,
                GPUMaskBlurCachedHandleSet(pending.toList()),
            )
        } catch (failure: Throwable) {
            throw failure
        }
    }

    private fun createInvariants(): GPUWgpu4kMaskBlurInvariantHandles {
        val pending = mutableListOf<AutoCloseable>()
        fun <T : AutoCloseable> T.track(): T = also {
            pending += it
            preRegistrationHandles.track(it)
        }
        return try {
            val maskLayout = device.createBindGroupLayout(
                BindGroupLayoutDescriptor(
                    label = "Kanvas.session.maskBlur.maskLayout",
                    entries = listOf(uniformLayoutEntry()),
                ),
            ).track()
            val blurLayout = device.createBindGroupLayout(
                BindGroupLayoutDescriptor(
                    label = "Kanvas.session.maskBlur.blurLayout",
                    entries = listOf(
                        uniformLayoutEntry(),
                        textureLayoutEntry(1u),
                        samplerLayoutEntry(2u),
                    ),
                ),
            ).track()
            val styleLayout = device.createBindGroupLayout(
                BindGroupLayoutDescriptor(
                    label = "Kanvas.session.maskBlur.styleLayout",
                    entries = listOf(
                        uniformLayoutEntry(),
                        textureLayoutEntry(1u),
                        samplerLayoutEntry(2u),
                        textureLayoutEntry(3u),
                        samplerLayoutEntry(4u),
                    ),
                ),
            ).track()
            val compositeLayout = device.createBindGroupLayout(
                BindGroupLayoutDescriptor(
                    label = "Kanvas.session.maskBlur.compositeLayout",
                    entries = listOf(
                        uniformLayoutEntry(),
                        textureLayoutEntry(1u),
                        samplerLayoutEntry(2u),
                    ),
                ),
            ).track()
            val compositeDstLayout = device.createBindGroupLayout(
                BindGroupLayoutDescriptor(
                    label = "Kanvas.session.maskBlur.compositeDstLayout",
                    entries = listOf(
                        uniformLayoutEntry(),
                        textureLayoutEntry(1u),
                        samplerLayoutEntry(2u),
                        textureLayoutEntry(3u),
                        samplerLayoutEntry(4u),
                    ),
                ),
            ).track()
            val compositeClipLayout = device.createBindGroupLayout(
                BindGroupLayoutDescriptor(
                    label = "Kanvas.session.maskBlur.compositeClipLayout",
                    entries = listOf(
                        uniformLayoutEntry(),
                        textureLayoutEntry(1u),
                        samplerLayoutEntry(2u),
                        uniformLayoutEntry(binding = 3u),
                    ),
                ),
            ).track()
            val compositeDstClipLayout = device.createBindGroupLayout(
                BindGroupLayoutDescriptor(
                    label = "Kanvas.session.maskBlur.compositeDstClipLayout",
                    entries = listOf(
                        uniformLayoutEntry(),
                        textureLayoutEntry(1u),
                        samplerLayoutEntry(2u),
                        textureLayoutEntry(3u),
                        samplerLayoutEntry(4u),
                        uniformLayoutEntry(binding = 5u),
                    ),
                ),
            ).track()
            val maskShader = shader("mask", MASK_BLUR_MASK_WGSL).track()
            val blurHShader = shader("blur-h", MASK_BLUR_BLUR_WGSL(horizontal = true)).track()
            val blurVShader = shader("blur-v", MASK_BLUR_BLUR_WGSL(horizontal = false)).track()
            val styleShader = shader("style", MASK_BLUR_STYLE_WGSL).track()
            fun layout(vararg layouts: GPUBindGroupLayout): GPUPipelineLayout = device.createPipelineLayout(
                PipelineLayoutDescriptor(
                    label = "Kanvas.session.maskBlur.pipelineLayout",
                    bindGroupLayouts = layouts.toList(),
                ),
            ).track()
            val maskPipeline = pipeline(
                "mask",
                layout(maskLayout),
                maskShader,
                replaceBlendState(),
            ).track()
            val blurHPipeline = pipeline(
                "blur-h",
                layout(blurLayout),
                blurHShader,
                replaceBlendState(),
            ).track()
            val blurVPipeline = pipeline(
                "blur-v",
                layout(blurLayout),
                blurVShader,
                replaceBlendState(),
            ).track()
            val stylePipeline = pipeline(
                "style",
                layout(styleLayout),
                styleShader,
                replaceBlendState(),
            ).track()
            val sampler = device.createSampler(
                SamplerDescriptor(
                    addressModeU = GPUAddressMode.ClampToEdge,
                    addressModeV = GPUAddressMode.ClampToEdge,
                    magFilter = GPUFilterMode.Nearest,
                    minFilter = GPUFilterMode.Nearest,
                    label = "Kanvas.session.maskBlur.nearestSampler",
                ),
            ).track()
            GPUWgpu4kMaskBlurInvariantHandles(
                maskLayout,
                blurLayout,
                styleLayout,
                compositeLayout,
                compositeDstLayout,
                compositeClipLayout,
                compositeDstClipLayout,
                maskPipeline,
                blurHPipeline,
                blurVPipeline,
                stylePipeline,
                sampler,
                GPUMaskBlurCachedHandleSet(pending.toList()),
            )
        } catch (failure: Throwable) {
            throw failure
        }
    }

    private fun createIntermediates(width: Int, height: Int): GPUWgpu4kMaskBlurIntermediateHandles {
        val pending = mutableListOf<AutoCloseable>()
        fun <T : AutoCloseable> T.track(): T = also {
            pending += it
            preRegistrationHandles.track(it)
        }
        return try {
            fun texture(label: String): GPUTexture = device.createTexture(
                TextureDescriptor(
                    size = Extent3D(width.toUInt(), height.toUInt()),
                    format = GPUTextureFormat.RGBA8Unorm,
                    usage = GPUTextureUsage.RenderAttachment or GPUTextureUsage.TextureBinding,
                    label = label,
                ),
            ).track()
            val maskTexture = texture("Kanvas.session.maskBlur.maskTexture")
            val maskView = maskTexture.createView().track()
            val horizontalTexture = texture("Kanvas.session.maskBlur.horizontalTexture")
            val horizontalView = horizontalTexture.createView().track()
            val verticalTexture = texture("Kanvas.session.maskBlur.verticalTexture")
            val verticalView = verticalTexture.createView().track()
            val styledTexture = texture("Kanvas.session.maskBlur.styledTexture")
            val styledView = styledTexture.createView().track()
            GPUWgpu4kMaskBlurIntermediateHandles(
                width,
                height,
                maskTexture,
                maskView,
                horizontalTexture,
                horizontalView,
                verticalTexture,
                verticalView,
                styledTexture,
                styledView,
                GPUMaskBlurCachedHandleSet(pending.toList()),
            )
        } catch (failure: Throwable) {
            throw failure
        }
    }

    private fun requireCleanSetupLedger() {
        check(preRegistrationHandles.closeRetainingFailures()) {
            "Mask blur cache cannot allocate while failed setup handles remain quarantined"
        }
    }

    private fun shader(label: String, source: String): GPUShaderModule = device.createShaderModule(
        ShaderModuleDescriptor(
            label = "Kanvas.session.maskBlur.$label.shader",
            code = source,
        ),
    )

    private fun pipeline(
        label: String,
        layout: GPUPipelineLayout,
        shader: GPUShaderModule,
        blend: BlendState,
    ): GPURenderPipeline = compositePipeline(label, layout, shader, blend, GPUTextureFormat.RGBA8Unorm)

    private fun compositePipeline(
        label: String,
        layout: GPUPipelineLayout,
        shader: GPUShaderModule,
        blend: BlendState,
        targetFormat: GPUTextureFormat,
    ): GPURenderPipeline = device.createRenderPipeline(
        RenderPipelineDescriptor(
            label = "Kanvas.session.maskBlur.$label.pipeline",
            layout = layout,
            vertex = VertexState(module = shader, entryPoint = "vs_main"),
            primitive = PrimitiveState(),
            multisample = MultisampleState(count = 1u),
            fragment = FragmentState(
                module = shader,
                entryPoint = "fs_main",
                targets = listOf(
                    ColorTargetState(
                        format = targetFormat,
                        blend = blend,
                    ),
                ),
            ),
        ),
    )

    private fun uniformLayoutEntry(binding: UInt = 0u) = BindGroupLayoutEntry(
        binding = binding,
        visibility = GPUShaderStage.Fragment,
        buffer = BufferBindingLayout(type = GPUBufferBindingType.Uniform),
    )

    private fun textureLayoutEntry(binding: UInt) = BindGroupLayoutEntry(
        binding = binding,
        visibility = GPUShaderStage.Fragment,
        texture = TextureBindingLayout(
            sampleType = GPUTextureSampleType.Float,
            viewDimension = GPUTextureViewDimension.TwoD,
            multisampled = false,
        ),
    )

    private fun samplerLayoutEntry(binding: UInt) = BindGroupLayoutEntry(
        binding = binding,
        visibility = GPUShaderStage.Fragment,
        sampler = SamplerBindingLayout(type = GPUSamplerBindingType.Filtering),
    )

    private fun replaceBlendState() = BlendState(
        color = BlendComponent(
            GPUBlendOperation.Add,
            GPUBlendFactor.One,
            GPUBlendFactor.Zero,
        ),
        alpha = BlendComponent(
            GPUBlendOperation.Add,
            GPUBlendFactor.One,
            GPUBlendFactor.Zero,
        ),
    )

    private fun srcOverBlendState() = BlendState(
        color = BlendComponent(
            GPUBlendOperation.Add,
            GPUBlendFactor.One,
            GPUBlendFactor.OneMinusSrcAlpha,
        ),
        alpha = BlendComponent(
            GPUBlendOperation.Add,
            GPUBlendFactor.One,
            GPUBlendFactor.OneMinusSrcAlpha,
        ),
    )
}

internal class GPUMaskBlurCachedHandleSet(handles: List<AutoCloseable>) : AutoCloseable {
    private val pending = handles.asReversed().toMutableList()

    @Synchronized
    override fun close() {
        var firstFailure: Throwable? = null
        val iterator = pending.iterator()
        while (iterator.hasNext()) {
            try {
                iterator.next().close()
                iterator.remove()
            } catch (failure: Throwable) {
                if (firstFailure == null) firstFailure = failure else firstFailure.addSuppressed(failure)
            }
        }
        firstFailure?.let { throw IllegalStateException("Mask blur cached handles remain live", it) }
    }
}

internal val MASK_BLUR_VERTEX_WGSL: String = """
@vertex
fn vs_main(@builtin(vertex_index) vertex_index: u32) -> @builtin(position) vec4f {
    var positions = array<vec2f, 3>(
        vec2f(-1.0, -1.0),
        vec2f(3.0, -1.0),
        vec2f(-1.0, 3.0),
    );
    return vec4f(positions[vertex_index], 0.0, 1.0);
}
""".trimIndent()

/**
 * Static local shape coverage pass (Task 11). The uniform carries the plan-scaled local
 * geometry: rect, rrect (rect + 8 radii), or a polygon up to 64 vertices with fill rule.
 * Coverage is evaluated analytically at pixel centers, mirroring the legacy local-mask
 * semantics (binary coverage for non-AA shapes).
 */
internal val MASK_BLUR_MASK_WGSL: String = """
struct MaskUniforms {
    localSize: vec2f,
    vertexCount: u32,
    kind: u32,
    fillRule: u32,
    inverse: u32,
    _pad0: u32,
    rect: vec4f,
    radii: vec4f,
    radii2: vec4f,
    vertices: array<vec4f, 32>,
};

@group(0) @binding(0) var<uniform> uniforms: MaskUniforms;

$MASK_BLUR_VERTEX_WGSL

fn pointInTriangle(px: f32, py: f32, ax: f32, ay: f32, bx: f32, by: f32, cx: f32, cy: f32) -> bool {
    let d1 = (px - bx) * (ay - by) - (ax - bx) * (py - by);
    let d2 = (px - cx) * (by - cy) - (bx - cx) * (py - cy);
    let d3 = (px - ax) * (cy - ay) - (cx - ax) * (py - ay);
    let hasNeg = (d1 < 0.0) || (d2 < 0.0) || (d3 < 0.0);
    let hasPos = (d1 > 0.0) || (d2 > 0.0) || (d3 > 0.0);
    return !(hasNeg && hasPos);
}

fn insideShape(px: f32, py: f32) -> f32 {
    if (uniforms.kind == 0u) {
        if (px >= uniforms.rect.x && px < uniforms.rect.z && py >= uniforms.rect.y && py < uniforms.rect.w) {
            return 1.0;
        }
        return 0.0;
    }
    if (uniforms.kind == 1u) {
        let left = uniforms.rect.x;
        let top = uniforms.rect.y;
        let right = uniforms.rect.z;
        let bottom = uniforms.rect.w;
        if (px < left || px >= right || py < top || py >= bottom) {
            return 0.0;
        }
        var cornerX = 0.0;
        var cornerY = 0.0;
        if (px < left + uniforms.radii.x && py < top + uniforms.radii.y) {
            cornerX = uniforms.radii.x; cornerY = uniforms.radii.y;
        } else if (px >= right - uniforms.radii.z && py < top + uniforms.radii.w) {
            cornerX = uniforms.radii.z; cornerY = uniforms.radii.w;
        } else if (px >= right - uniforms.radii2.x && py >= bottom - uniforms.radii2.y) {
            cornerX = uniforms.radii2.x; cornerY = uniforms.radii2.y;
        } else if (px < left + uniforms.radii2.z && py >= bottom - uniforms.radii2.w) {
            cornerX = uniforms.radii2.z; cornerY = uniforms.radii2.w;
        } else {
            return 1.0;
        }
        let ex = select(right - cornerX, left + cornerX, px < left + cornerX);
        let ey = select(bottom - cornerY, top + cornerY, py < top + cornerY);
        let dx = (px - ex) / max(cornerX, 0.0001);
        let dy = (py - ey) / max(cornerY, 0.0001);
        return select(0.0, 1.0, dx * dx + dy * dy <= 1.0);
    }
    var winding = 0i;
    let count = min(uniforms.vertexCount, 64u);
    for (var i = 0u; i < count; i = i + 1u) {
        let j = (i + 1u) % count;
        let xi = uniforms.vertices[i].x;
        let yi = uniforms.vertices[i].y;
        let xj = uniforms.vertices[j].x;
        let yj = uniforms.vertices[j].y;
        if ((yi > py) != (yj > py)) {
            let xint = xj + (xi - xj) * (py - yj) / (yi - yj);
            if (px < xint) {
                if (yi < yj) { winding = winding + 1; } else { winding = winding - 1; }
            }
        }
    }
    let inside = select(winding != 0, (winding % 2) != 0, uniforms.fillRule == 1u);
    return select(0.0, 1.0, inside);
}

@fragment
fn fs_main(@builtin(position) position: vec4f) -> @location(0) vec4f {
    let size = max(uniforms.localSize, vec2f(1.0));
    let pos = position.xy;
    var coverage = insideShape(pos.x, pos.y);
    if (uniforms.inverse == 1u) {
        coverage = 1.0 - coverage;
    }
    return vec4f(coverage, coverage, coverage, coverage);
}
""".trimIndent()

/** Static separable Gaussian mask blur (horizontal or vertical), legacy MaskBlurUniforms ABI. */
internal fun MASK_BLUR_BLUR_WGSL(horizontal: Boolean): String {
    val offset = if (horizontal) {
        "vec2f(f32(i) - f32(half), 0.0) / size"
    } else {
        "vec2f(0.0, f32(i) - f32(half)) / size"
    }
    return """
struct BlurUniforms {
    tapCount: u32,
    _pad0: u32,
    targetSize: vec2f,
    _pad1: vec2f,
    _pad2: vec2f,
    weights: array<vec4f, 7>,
};

@group(0) @binding(0) var<uniform> uniforms: BlurUniforms;
@group(0) @binding(1) var inputTexture: texture_2d<f32>;
@group(0) @binding(2) var inputSampler: sampler;

$MASK_BLUR_VERTEX_WGSL

fn sampleDecal(uv: vec2f) -> vec4f {
    if (any(uv < vec2f(0.0)) || any(uv >= vec2f(1.0))) {
        return vec4f(0.0);
    }
    return textureSample(inputTexture, inputSampler, uv);
}

@fragment
fn fs_main(@builtin(position) position: vec4f) -> @location(0) vec4f {
    let size = max(uniforms.targetSize, vec2f(1.0));
    let uv = position.xy / size;
    let half = uniforms.tapCount / 2u;
    var result = vec4f(0.0);
    for (var i = 0u; i < 25u; i = i + 1u) {
        if (i >= uniforms.tapCount) {
            break;
        }
        let packedWeights = uniforms.weights[i / 4u];
        let weight = packedWeights[i % 4u];
        let sampleOffset = $offset;
        result += weight * sampleDecal(uv + sampleOffset);
    }
    return result;
}
""".trimIndent()
}

/** Static style pass: combines the blurred coverage with the original mask per BlurStyle. */
internal val MASK_BLUR_STYLE_WGSL: String = """
struct StyleUniforms {
    style: u32,
};

@group(0) @binding(0) var<uniform> uniforms: StyleUniforms;
@group(0) @binding(1) var srcTexture: texture_2d<f32>;
@group(0) @binding(2) var srcSampler: sampler;
@group(0) @binding(3) var dstTexture: texture_2d<f32>;
@group(0) @binding(4) var dstSampler: sampler;

$MASK_BLUR_VERTEX_WGSL

@fragment
fn fs_main(@builtin(position) position: vec4f) -> @location(0) vec4f {
    let dims = textureDimensions(srcTexture);
    let uv = position.xy / vec2f(f32(dims.x), f32(dims.y));
    let blurred = textureSample(srcTexture, srcSampler, uv).a;
    let original = textureSample(dstTexture, dstSampler, uv).a;
    var coverage = blurred;
    switch (uniforms.style) {
        case 0u: { coverage = blurred; }
        case 1u: { coverage = max(original, blurred); }
        case 2u: { coverage = blurred * (1.0 - original); }
        default: { coverage = blurred * original; }
    }
    return vec4f(coverage, coverage, coverage, coverage);
}
""".trimIndent()

/** Static fullscreen solid-color pass (SolidRect scene renders inside blur frames). */
internal val MASK_BLUR_SOLID_WGSL: String = """
struct SolidRectBlock {
    rect: vec4<f32>,
    radii: vec4<f32>,
    color: vec4<f32>,
    reserved: vec4<f32>,
};

@group(0) @binding(0) var<uniform> uniforms: SolidRectBlock;

$MASK_BLUR_VERTEX_WGSL

@fragment
fn fs_main() -> @location(0) vec4f {
    return uniforms.color;
}
""".trimIndent()

/** Static scene composite: color × coverage with the pipeline blend state (SRC_OVER or SRC). */
internal val MASK_BLUR_COMPOSITE_WGSL: String = """
struct CompositeUniforms {
    deviceBounds: vec4f,
    color: vec4f,
};

@group(0) @binding(0) var<uniform> uniforms: CompositeUniforms;
@group(0) @binding(1) var maskTexture: texture_2d<f32>;
@group(0) @binding(2) var maskSampler: sampler;

$MASK_BLUR_VERTEX_WGSL

@fragment
fn fs_main(@builtin(position) position: vec4f) -> @location(0) vec4f {
    let localSize = max(uniforms.deviceBounds.zw - uniforms.deviceBounds.xy, vec2f(1.0));
    let uv = (position.xy - uniforms.deviceBounds.xy) / localSize;
    let coverage = textureSample(maskTexture, maskSampler, uv).a;
    return uniforms.color * coverage;
}
""".trimIndent()

/**
 * Static scene composite with an analytic device-rect clip (Task 7): the blurred mask
 * coverage is multiplied by the analytic clip coverage (the same rect signed-distance
 * AA math as the core lane's `CorePrimitiveAnalyticClipBlock`, which matches the
 * `TopLevelMaskBlurPixelOracle.RectClip(antiAlias = true)` linear falloff at pixel
 * centers) before the color shade with the pipeline blend state (SRC_OVER or SRC).
 */
internal val MASK_BLUR_COMPOSITE_CLIP_WGSL: String = """
struct CompositeUniforms {
    deviceBounds: vec4f,
    color: vec4f,
};

struct CorePrimitiveAnalyticClipBlock {
    target_size: vec2f,
    clip_type: u32,
    anti_alias: u32,
    premul_rgba: vec4f,
    clip_bounds: vec4f,
    clip_radii: vec4f,
};

@group(0) @binding(0) var<uniform> uniforms: CompositeUniforms;
@group(0) @binding(1) var maskTexture: texture_2d<f32>;
@group(0) @binding(2) var maskSampler: sampler;
@group(0) @binding(3) var<uniform> clipUniforms: CorePrimitiveAnalyticClipBlock;

$MASK_BLUR_VERTEX_WGSL

fn rect_signed_distance(position: vec2f, bounds: vec4f) -> f32 {
    let center = (bounds.xy + bounds.zw) * 0.5;
    let half_extent = (bounds.zw - bounds.xy) * 0.5;
    let q = abs(position - center) - half_extent;
    return length(max(q, vec2f(0.0))) + min(max(q.x, q.y), 0.0);
}

fn clip_coverage(position: vec2f) -> f32 {
    let distance = rect_signed_distance(position, clipUniforms.clip_bounds);
    let hard = select(0.0, 1.0, distance <= 0.0);
    let aa = clamp(0.5 - distance, 0.0, 1.0);
    return select(hard, aa, clipUniforms.anti_alias != 0u);
}

@fragment
fn fs_main(@builtin(position) position: vec4f) -> @location(0) vec4f {
    let localSize = max(uniforms.deviceBounds.zw - uniforms.deviceBounds.xy, vec2f(1.0));
    let uv = (position.xy - uniforms.deviceBounds.xy) / localSize;
    var coverage = textureSample(maskTexture, maskSampler, uv).a;
    coverage *= clip_coverage(position.xy);
    return uniforms.color * coverage;
}
""".trimIndent()
