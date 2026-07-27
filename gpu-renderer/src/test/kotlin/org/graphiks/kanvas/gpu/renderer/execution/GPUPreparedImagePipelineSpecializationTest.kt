package org.graphiks.kanvas.gpu.renderer.execution

import io.ygdrasil.webgpu.GPUDevice
import java.lang.reflect.Proxy
import java.nio.file.Files
import java.nio.file.Path
import java.util.IdentityHashMap
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUDeviceGenerationID
import org.graphiks.kanvas.gpu.renderer.coordinates.GPUPixelBounds
import org.graphiks.kanvas.gpu.renderer.images.AlphaType
import org.graphiks.kanvas.gpu.renderer.images.GPUPreparedImageArtifactFactory
import org.graphiks.kanvas.gpu.renderer.images.GPUPreparedImageArtifactResult
import org.graphiks.kanvas.gpu.renderer.images.GPUPreparedImageOrientation
import org.graphiks.kanvas.gpu.renderer.images.GPUPreparedImageProfile
import org.graphiks.kanvas.gpu.renderer.images.GPUPreparedImageProvenance
import org.graphiks.kanvas.gpu.renderer.images.GPUPreparedImageSourceClass
import org.graphiks.kanvas.gpu.renderer.images.GPUPreparedImageSourceFormat
import org.graphiks.kanvas.gpu.renderer.images.GPUPreparedImageSourceInput
import org.graphiks.kanvas.gpu.renderer.payloads.GPUDrawPayloadRef
import org.graphiks.kanvas.gpu.renderer.payloads.GPUDrawSemanticPayload
import org.graphiks.kanvas.gpu.renderer.payloads.GPUPreparedAtlasSourceBlend
import org.graphiks.kanvas.gpu.renderer.payloads.GPUPreparedImageGeometry
import org.graphiks.kanvas.gpu.renderer.payloads.GPUPreparedImageGeometryClass
import org.graphiks.kanvas.gpu.renderer.payloads.GPUPreparedImagePayloadGatherer
import org.graphiks.kanvas.gpu.renderer.payloads.GPUPreparedImagePayloadInput
import org.graphiks.kanvas.gpu.renderer.payloads.GPUPreparedImageSampling
import org.graphiks.kanvas.gpu.renderer.payloads.GPUPreparedImageVertex
import org.graphiks.kanvas.gpu.renderer.state.GPUFrameProvenance

class GPUPreparedImagePipelineSpecializationTest {
    @Test
    fun `one hundred alternating draws create no specialized pipeline after warmup`() {
        val generation = GPUDeviceGenerationID(7)
        val native = MeasuringDevice()
        val cache = GPUWgpu4kPreparedImageSessionCache(native.device, generation)
        val seenPipelines = IdentityHashMap<Any, Boolean>()
        val semanticHashes = linkedSetOf<String>()
        val preChangeKeys = linkedSetOf<PreChangeKey>()
        var cacheHits = 0
        var cacheMisses = 0
        var pipelineCreatesAfterWarmup = 0
        var uniformUploadBytes = 0L

        try {
            repeat(DRAW_COUNT) { index ->
                val draw = fixtureDraw(index)
                semanticHashes += draw.canonicalHash
                preChangeKeys += PreChangeKey(
                    alphaOnly = draw.artifact.alphaOnly,
                    atlasColorMode = if (draw.atlasColorPremultipliedRgba == null) "none" else "premultiplied-rgba",
                    atlasSourceBlend = draw.atlasSourceBlend,
                    clipClass = if (draw.scissorBounds == draw.targetBounds) "full-target" else "scissor",
                )
                val pipelineCreatesBefore = native.pipelineCreates
                val acquired = assertIs<GPUPreparedImageCacheAcquire.Ready>(
                    cache.acquire(draw.pipelineKey, generation),
                )
                if (seenPipelines.put(acquired.pipeline.pipeline, true) == null) {
                    cacheMisses += 1
                } else {
                    cacheHits += 1
                }
                if (index > 0) {
                    pipelineCreatesAfterWarmup += native.pipelineCreates - pipelineCreatesBefore
                }
                uniformUploadBytes += preparedImageBindingLayoutContract().uniformMinBindingSize
            }
        } finally {
            cache.close()
        }

        val measuredReport = buildString {
            appendLine("draws=$DRAW_COUNT")
            appendLine("shaderModules=${native.shaderModules}")
            appendLine("pipelines=${native.pipelineCreates}")
            appendLine("pipelineCreatesAfterWarmup=$pipelineCreatesAfterWarmup")
            appendLine("cacheHits=$cacheHits")
            appendLine("cacheMisses=$cacheMisses")
            appendLine("uniformUploadBytes=$uniformUploadBytes")
        }
        val checkedInReport = Files.readString(repoRoot().resolve(EVIDENCE_PATH))

        assertEquals(DRAW_COUNT, semanticHashes.size)
        assertEquals(0, pipelineCreatesAfterWarmup)
        assertTrue(native.pipelineCreates < preChangeKeys.size)
        assertEquals(measuredReport, checkedInReport, "checked-in evidence must equal fixture output")
    }

    private fun fixtureDraw(index: Int): GPUDrawSemanticPayload.SampledImage {
        val alphaOnly = index % 2 == 1
        val hasAtlasColor = index % 4 >= 2
        val atlasSourceBlend = when {
            !hasAtlasColor -> null
            index % 8 >= 4 -> GPUPreparedAtlasSourceBlend.Plus
            else -> GPUPreparedAtlasSourceBlend.Modulate
        }
        val sourceFormat =
            if (alphaOnly) GPUPreparedImageSourceFormat.A8 else GPUPreparedImageSourceFormat.Rgba8
        val artifact = (
            GPUPreparedImageArtifactFactory.prepare(
                GPUPreparedImageSourceInput(
                    sourceClass = GPUPreparedImageSourceClass.DecodedCpu,
                    sourceId = "pipeline-specialization-$index",
                    width = 1,
                    height = 1,
                    sourceFormat = sourceFormat,
                    alphaType = AlphaType.PREMUL,
                    sourceRowBytes = if (alphaOnly) 1 else 4,
                    profile = GPUPreparedImageProfile.Srgb,
                    orientation = GPUPreparedImageOrientation.AppliedIdentity,
                    provenance = GPUPreparedImageProvenance.CallerPixels,
                    sourceGeneration = 2,
                    pixelBytes =
                        if (alphaOnly) {
                            byteArrayOf(index.toByte())
                        } else {
                            byteArrayOf(index.toByte(), 2, 3, 4)
                        },
                ),
            ) as GPUPreparedImageArtifactResult.Ready
            ).artifact
        return GPUPreparedImagePayloadGatherer().gatherSemantic(
            GPUPreparedImagePayloadInput(
                payloadRef = GPUDrawPayloadRef(
                    commandIdValue = index,
                    renderStepIdentity = "image.draw.texture_upload",
                ),
                artifact = artifact,
                geometry = GPUPreparedImageGeometry(
                    GPUPreparedImageGeometryClass.Rect,
                    listOf(
                        GPUPreparedImageVertex(0f, 0f, 0f, 0f),
                        GPUPreparedImageVertex(4f, 0f, 1f, 0f),
                        GPUPreparedImageVertex(4f, 3f, 1f, 1f),
                        GPUPreparedImageVertex(0f, 3f, 0f, 1f),
                    ),
                    listOf(0, 1, 2, 0, 2, 3),
                ),
                sampling = GPUPreparedImageSampling.Nearest,
                tintPremultipliedRgba = listOf(1f, 1f, 1f, 1f),
                atlasColorPremultipliedRgba =
                    if (hasAtlasColor) listOf(0.25f, 0.5f, 0.75f, 1f) else null,
                atlasSourceBlend = atlasSourceBlend,
                targetBounds = GPUPixelBounds(0, 0, 16, 16),
                scissorBounds =
                    if (index % 3 == 0) {
                        GPUPixelBounds(0, 0, 16, 16)
                    } else {
                        GPUPixelBounds(1, 1, 15, 15)
                    },
                blendPlanIdentity = "src-over",
                frameProvenance = GPUFrameProvenance.GmContent,
            ),
        )
    }

    private class MeasuringDevice {
        var shaderModules: Int = 0
        var pipelineCreates: Int = 0
        val device: GPUDevice = proxy(GPUDevice::class.java) { methodName, returnType, _ ->
            when (methodName) {
                "createShaderModule" -> {
                    shaderModules += 1
                    nativeHandle(returnType, "shader-$shaderModules")
                }
                "createRenderPipeline" -> {
                    pipelineCreates += 1
                    nativeHandle(returnType, "pipeline-$pipelineCreates")
                }
                "createBindGroupLayout",
                "createPipelineLayout",
                -> nativeHandle(returnType, methodName)
                else -> defaultValue(returnType)
            }
        }

        private fun nativeHandle(type: Class<*>, label: String): Any =
            Proxy.newProxyInstance(type.classLoader, arrayOf(type)) { proxy, method, args ->
                when (method.name) {
                    "close" -> Unit
                    "toString" -> label
                    "hashCode" -> System.identityHashCode(proxy)
                    "equals" -> proxy === args?.singleOrNull()
                    else -> defaultValue(method.returnType)
                }
            }
    }

    private data class PreChangeKey(
        val alphaOnly: Boolean,
        val atlasColorMode: String,
        val atlasSourceBlend: GPUPreparedAtlasSourceBlend?,
        val clipClass: String,
    )

    private companion object {
        const val DRAW_COUNT = 100
        const val EVIDENCE_PATH =
            "reports/upstream-rebaseline/graphite-dawn-frame-plan/fp-04-pipeline-key.txt"

        fun repoRoot(): Path = generateSequence(
            Path.of(System.getProperty("user.dir")).toAbsolutePath(),
            Path::getParent,
        ).first { Files.exists(it.resolve("settings.gradle.kts")) }

        fun <T> proxy(
            type: Class<T>,
            handler: (methodName: String, returnType: Class<*>, args: Array<out Any?>?) -> Any?,
        ): T = type.cast(
            Proxy.newProxyInstance(type.classLoader, arrayOf(type)) { _, method, args ->
                handler(method.name, method.returnType, args)
            },
        )

        fun defaultValue(type: Class<*>): Any? = when (type) {
            java.lang.Boolean.TYPE -> false
            java.lang.Byte.TYPE -> 0.toByte()
            java.lang.Short.TYPE -> 0.toShort()
            java.lang.Integer.TYPE -> 0
            java.lang.Long.TYPE -> 0L
            java.lang.Float.TYPE -> 0f
            java.lang.Double.TYPE -> 0.0
            java.lang.Character.TYPE -> '\u0000'
            java.lang.Void.TYPE -> Unit
            else -> null
        }
    }
}
