package org.graphiks.kanvas.gpu.renderer.images

import org.graphiks.kanvas.gpu.renderer.materials.GPUMaterialSourceDescriptor
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals

class DecodedImageShaderPreparedRouteTest {
    @Test
    fun `already decoded pixels build CPU prepared image shader route evidence`() {
        val plan = GPUDecodedImageShaderPreparedPlanner().plan(
            source = checkerPixels,
            sampling = linearClampSampling,
        )

        val imageSource = assertIs<GPUMaterialSourceDescriptor.Image>(plan.materialSource)

        assertEquals("CPUPreparedGPU", plan.routeKind)
        assertEquals("UploadedTextureArtifact", plan.artifact.artifactType)
        assertEquals("sampled-texture.image-shader", plan.binding.bindingLabel)
        assertEquals("image-source:decoded-pixels", imageSource.plan.imageSourceKey)
        assertEquals("image.shader.decoded-pixels.v1", plan.materialKey.value.substringBeforeLast(':'))
        assertEquals(setOf("texture_binding", "copy_dst"), plan.textureDescriptor.usageLabels)
        assertEquals("2d", plan.viewDescriptor.viewDimension)
        assertEquals("clamp-to-edge", plan.samplerDescriptor.addressModeU)
        assertEquals("linear", plan.samplerDescriptor.magFilter)
        assertFalse(plan.artifact.artifactKey.value.contains("handle", ignoreCase = true))
        assertFalse(plan.artifact.artifactKey.value.contains("0x", ignoreCase = true))
        assertFalse(plan.materialKey.value.contains(plan.artifact.artifactKey.value))
        assertFalse(plan.materialKey.value.contains(checkerPixels.contentHash))
        assertFalse(plan.materialKey.value.contains("row8"))
        assertEquals(
            listOf(
                "image:decoded.prepared routeKind=CPUPreparedGPU consumer=sampled-texture.image-shader material=image.shader.decoded-pixels.v1",
                "image:source id=image:checker:v1 kind=AlreadyDecodedPixels size=2x2 format=RGBA8Unorm alpha=Premul color=srgb orientation=Applied generation=3 provenance=unit-test",
                "image:upload artifact=${plan.artifact.artifactKey.value} type=UploadedTextureArtifact lifetime=recording-local budget=image-small uploadBeforeSample=true",
                "texture:descriptor size=2x2 format=RGBA8Unorm usage=copy_dst,texture_binding sampleCount=1 view=2d mipRange=0..0",
                "sampler:descriptor address=clamp-to-edge/clamp-to-edge filter=linear/linear mipmap=none",
                "binding:sampledTexture label=sampled-texture.image-shader layout=group1.binding1.texture_2d_rgba8_unorm sampler=group1.binding2.sampler",
                "material:key=${plan.materialKey.value} excludes=upload-artifact-key,pixel-content,row-bytes,resource-handle",
                "nonclaim:no-product-activation no-adapter-backed-execution no-codec-support no-mipmap-support no-broad-image-support no-cpu-rendered-compat-texture",
            ),
            plan.dumpLines(),
        )
    }

    @Test
    fun `material key excludes decoded pixel upload and content facts`() {
        val first = GPUDecodedImageShaderPreparedPlanner().plan(
            source = checkerPixels,
            sampling = linearClampSampling,
        )
        val changedPixels = GPUDecodedImageShaderPreparedPlanner().plan(
            source = checkerPixels.copy(
                generation = 4,
                contentHash = "sha256:changed-pixel-content",
                rowBytes = 16,
            ),
            sampling = linearClampSampling,
        )

        assertEquals(first.materialKey, changedPixels.materialKey)
        assertNotEquals(first.artifact.artifactKey, changedPixels.artifact.artifactKey)
        assertFalse(changedPixels.materialKey.value.contains("changed-pixel-content"))
        assertFalse(changedPixels.materialKey.value.contains("row16"))
    }

    @Test
    fun `upload artifact key includes decoded pixel preparation facts`() {
        val base = GPUDecodedImageShaderPreparedPlanner().plan(
            source = checkerPixels,
            sampling = linearClampSampling,
        )
        val changedAlpha = GPUDecodedImageShaderPreparedPlanner().plan(
            source = checkerPixels.copy(alphaType = "Unpremul"),
            sampling = linearClampSampling,
        )
        val changedColor = GPUDecodedImageShaderPreparedPlanner().plan(
            source = checkerPixels.copy(colorProfileLabel = "display-p3"),
            sampling = linearClampSampling,
        )
        val key = base.artifact.artifactKey.value

        assertNotEquals(base.artifact.artifactKey, changedAlpha.artifact.artifactKey)
        assertNotEquals(base.artifact.artifactKey, changedColor.artifact.artifactKey)
        assertContains(key, "descriptorv1")
        assertContains(key, "alpha.premul")
        assertContains(key, "color.srgb")
        assertContains(key, "orientation.applied")
        assertContains(key, "budget.image-small")
        assertContains(key, "generator.m4-decoded-image-v1")
    }

    @Test
    fun `unsupported decoded image variants refuse with stable diagnostics`() {
        val cases = listOf(
            RefusalCase("unsupported.image.source_descriptor_invalid", source = checkerPixels.copy(width = 0)),
            RefusalCase("unsupported.image.pixel.format", source = checkerPixels.copy(pixelFormat = "BGRA8Unorm")),
            RefusalCase("unsupported.image.pixel.row_stride", source = checkerPixels.copy(rowBytes = 4)),
            RefusalCase("unsupported.image.orientation", source = checkerPixels.copy(orientationState = "ExifRotate90")),
            RefusalCase("unsupported.image.upload.artifact_key_nondeterministic", source = checkerPixels.copy(contentHash = "handle:0xdeadbeef")),
            RefusalCase(
                "unsupported.image.upload.artifact_key_nondeterministic",
                source = checkerPixels.copy(colorProfileLabel = "profile:handle:0xdeadbeef"),
            ),
            RefusalCase("unsupported.image.upload.budget_exceeded", source = checkerPixels.copy(width = 2048, height = 2048, rowBytes = 8192)),
            RefusalCase(
                "unsupported.image.tile_mode",
                sampling = linearClampSampling.copy(tileModeX = "repeat"),
            ),
            RefusalCase(
                "unsupported.image.mip_required",
                sampling = linearClampSampling.copy(mipmapMode = "linear"),
            ),
            RefusalCase(
                "unsupported.image.sampling_filter",
                sampling = linearClampSampling.copy(filterMode = "cubic"),
            ),
        )

        for (case in cases) {
            val plan = GPUDecodedImageShaderPreparedPlanner().plan(
                source = case.source,
                sampling = case.sampling,
            )

            assertEquals("RefuseDiagnostic", plan.routeKind)
            assertEquals(case.expectedCode, plan.diagnostics.single().code)
            assertContains(plan.dumpLines().first(), "reason=${case.expectedCode}")
            assertContains(
                plan.dumpLines().last(),
                "no-cpu-rendered-compat-texture",
            )
        }
    }
}

private data class RefusalCase(
    val expectedCode: String,
    val source: GPUDecodedImagePixelsDescriptor = checkerPixels,
    val sampling: GPUDecodedImageSamplingPlan = linearClampSampling,
)

private val checkerPixels = GPUDecodedImagePixelsDescriptor(
    sourceId = "image:checker:v1",
    width = 2,
    height = 2,
    pixelFormat = "RGBA8Unorm",
    rowBytes = 8,
    alphaType = "Premul",
    colorProfileLabel = "srgb",
    orientationState = "Applied",
    generation = 3,
    contentHash = "sha256:checker-pixels-v1",
    provenance = "unit-test",
)

private val linearClampSampling = GPUDecodedImageSamplingPlan(
    tileModeX = "clamp",
    tileModeY = "clamp",
    filterMode = "linear",
    mipmapMode = "none",
)
