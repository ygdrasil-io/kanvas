package org.graphiks.kanvas.gpu.renderer.images

import org.graphiks.kanvas.gpu.renderer.artifacts.GPUPreparedColorUploadEncoding as ArtifactColorUploadEncoding
import org.graphiks.kanvas.gpu.renderer.artifacts.GPUPreparedImageUploadArtifact as PreparedUploadArtifact
import org.graphiks.kanvas.gpu.renderer.artifacts.preparedSdrColorContract as artifactSdrColorContract
import org.graphiks.kanvas.gpu.renderer.color.GPUColorInterpretation
import org.graphiks.kanvas.gpu.renderer.diagnostics.GPUPreparedImageRefusalCodes as CanonicalRefusalCodes
import io.ygdrasil.webgpu.GPUTextureFormat
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PreparedImageContractsTest {
    @Test
    fun `bounded SDR contract names the native source decode coverage and sRGB store`() {
        val contract = artifactSdrColorContract()

        assertEquals(GPUTextureFormat.RGBA8UnormSrgb, contract.colorSourceTextureFormat)
        assertEquals(GPUTextureFormat.RGBA8Unorm, contract.coverageSourceTextureFormat)
        assertEquals(
            ArtifactColorUploadEncoding.StraightEncodedSrgb,
            contract.colorUploadEncoding,
        )
        assertEquals(GPUTextureFormat.RGBA8UnormSrgb, contract.targetTextureFormat)
        assertEquals(GPUColorInterpretation.LinearPremul, contract.shaderInterpretation)
        assertEquals(
            GPUColorInterpretation.EncodedPremulSrgb,
            contract.readbackInterpretation,
        )
    }

    @Test
    fun `factory recovers bounded straight encoded sRGB before color upload`() {
        val artifact = ready(
            input(
                alpha = AlphaType.PREMUL,
                bytes = byteArrayOf(25, 75, 132.toByte(), 160.toByte()),
            ),
        )

        assertContentEquals(
            byteArrayOf(40, 120, 210.toByte(), 160.toByte()),
            artifact.tightRgba8BytesForUpload(),
        )
        assertEquals(
            ArtifactColorUploadEncoding.StraightEncodedSrgb,
            artifact.colorUploadEncoding,
        )
        assertEquals(
            GPUColorInterpretation.StraightEncodedSrgb.value,
            artifact.colorUploadInterpretation,
        )
        assertTrue(
            artifact.key.value.contains(ArtifactColorUploadEncoding.StraightEncodedSrgb.name),
        )
        assertTrue(artifact.key.value.contains(artifact.contentHash))
    }

    @Test
    fun `factory zeroes transparent color and keeps opaque straight bytes bounded`() {
        val transparent = ready(input(bytes = byteArrayOf(12, 34, 56, 0)))
        val opaque = ready(
            input(
                alpha = AlphaType.OPAQUE,
                bytes = byteArrayOf(40, 120, 210.toByte(), 255.toByte()),
            ),
        )

        assertContentEquals(byteArrayOf(0, 0, 0, 0), transparent.tightRgba8BytesForUpload())
        assertContentEquals(
            byteArrayOf(40, 120, 210.toByte(), 255.toByte()),
            opaque.tightRgba8BytesForUpload(),
        )
    }

    @Test
    fun `factory snapshots A8 bytes and expands tight premultiplied upload bytes`() {
        val callerBytes = byteArrayOf(1, 2, 3)
        val input = GPUPreparedImageSourceInput(
            GPUPreparedImageSourceClass.DecodedCpu, "ignored", 3, 1,
            GPUPreparedImageSourceFormat.A8, AlphaType.PREMUL, 3,
            GPUPreparedImageProfile.Srgb, GPUPreparedImageOrientation.AppliedIdentity,
            GPUPreparedImageProvenance.CallerPixels, 7, callerBytes,
        )
        callerBytes[0] = 99
        val artifact = ready(input)

        assertEquals(3L, artifact.pixelLayout.sourceRowBytes)
        assertEquals(12L, artifact.pixelLayout.normalizedRgba8RowBytes)
        assertEquals(GPUColorInterpretation.EncodedPremulSrgb.value, artifact.colorInterpretation)
        assertNull(artifact.colorUploadEncoding)
        assertEquals(GPUColorInterpretation.LinearPremul.value, artifact.colorUploadInterpretation)
        assertContentEquals(
            byteArrayOf(1, 1, 1, 1, 2, 2, 2, 2, 3, 3, 3, 3),
            artifact.tightRgba8BytesForUpload(),
        )
        val returned = artifact.tightRgba8BytesForUpload()
        returned[0] = 55
        assertEquals(1, artifact.tightRgba8BytesForUpload()[0])
    }

    @Test
    fun `factory converts BGRA and accepts only authoritative opaque RGBA`() {
        assertContentEquals(
            byteArrayOf(64, 128.toByte(), 191.toByte(), 4),
            ready(input()).tightRgba8BytesForUpload(),
        )
        val bgra = ready(input(format = GPUPreparedImageSourceFormat.Bgra8, alpha = AlphaType.PREMUL, bytes = byteArrayOf(3, 2, 1, 4)))
        assertContentEquals(
            byteArrayOf(64, 128.toByte(), 191.toByte(), 4),
            bgra.tightRgba8BytesForUpload(),
        )
        assertIs<GPUPreparedImageArtifactResult.Ready>(GPUPreparedImageArtifactFactory.prepare(input(alpha = AlphaType.OPAQUE, bytes = byteArrayOf(1, 2, 3, -1))))
        assertRefusal(
            input(alpha = AlphaType.OPAQUE, bytes = byteArrayOf(1, 2, 3, 4)),
            CanonicalRefusalCodes.ALPHA_INTERPRETATION,
        )
    }

    @Test
    fun `factory emits canonical FP04 refusals for unsupported source authorities`() {
        assertRefusal(input(bytes = null), CanonicalRefusalCodes.PIXELS_MISSING)
        assertRefusal(
            input(format = GPUPreparedImageSourceFormat.Unsupported),
            CanonicalRefusalCodes.PIXEL_FORMAT,
        )
        assertRefusal(
            input(alpha = AlphaType.UNPREMUL),
            CanonicalRefusalCodes.ALPHA_INTERPRETATION,
        )
        assertRefusal(
            input(format = GPUPreparedImageSourceFormat.A8, alpha = AlphaType.OPAQUE, bytes = byteArrayOf(1)),
            CanonicalRefusalCodes.ALPHA_INTERPRETATION,
        )
        assertRefusal(
            input(sourceRowBytes = 3, bytes = byteArrayOf(1, 2, 3)),
            CanonicalRefusalCodes.PIXEL_ROW_STRIDE,
        )
        assertRefusal(
            input(generation = -1),
            CanonicalRefusalCodes.NATIVE_GENERATION,
        )
        assertRefusal(
            input(sourceClass = GPUPreparedImageSourceClass.Encoded),
            CanonicalRefusalCodes.CODEC_UNREGISTERED,
        )
        assertRefusal(
            input(sourceClass = GPUPreparedImageSourceClass.Animated),
            CanonicalRefusalCodes.ANIMATION,
        )
        assertRefusal(
            input(sourceClass = GPUPreparedImageSourceClass.Yuv),
            CanonicalRefusalCodes.YUV_CONVERSION,
        )
        assertRefusal(
            input(sourceClass = GPUPreparedImageSourceClass.Hdr),
            CanonicalRefusalCodes.HDR_TRANSFER,
        )
        assertRefusal(
            input(sourceClass = GPUPreparedImageSourceClass.Imported),
            CanonicalRefusalCodes.TEXTURE_IMPORT_UNVALIDATED,
        )
        assertRefusal(
            input(profile = GPUPreparedImageProfile.Other),
            CanonicalRefusalCodes.GAMUT_TRANSFORM,
        )
        assertRefusal(
            input(profile = GPUPreparedImageProfile.Unresolved),
            CanonicalRefusalCodes.IMAGE_PROFILE_CONVERSION,
        )
        assertRefusal(
            input(orientation = GPUPreparedImageOrientation.Unresolved),
            CanonicalRefusalCodes.ORIENTATION,
        )
    }

    @Test
    fun `public refusal authority contains the complete stable FP04 table`() {
        assertEquals("unsupported.image.animation", CanonicalRefusalCodes.ANIMATION)
        assertTrue(CanonicalRefusalCodes.ANIMATION in CanonicalRefusalCodes.ALL)
        assertEquals(
            setOf(
                "unsupported.image.pixels_missing",
                "unsupported.image.dimensions",
                "unsupported.image.pixel.row_stride",
                "unsupported.image.pixel.length",
                "unsupported.image.pixel.format",
                "unsupported.image.alpha_interpretation",
                "unsupported.color.image_profile_conversion",
                "unsupported.color.gamut_transform",
                "unsupported.image.orientation",
                "unsupported.color.yuv_conversion",
                "unsupported.color.hdr_transfer",
                "unsupported.image.codec.unregistered",
                "unsupported.image.animation",
                "unsupported.texture.import_unvalidated",
                "unsupported.image.upload.budget_exceeded",
                "unsupported.image.texture_limit",
                "unsupported.image.mip_required",
                "unsupported.image.sampling_cubic",
                "unsupported.image.sampling_anisotropic",
                "unsupported.image.tile_mode",
                "unsupported.image.perspective_sampling",
                "unsupported.image.nine_geometry",
                "unsupported.image.lattice_geometry",
                "unsupported.image.atlas.array_lengths",
                "unsupported.image.atlas.geometry",
                "unsupported.image.atlas.source_blend",
                "unsupported.image.native_generation",
                "unsupported.image.native_binding",
                "unsupported.image.wgsl_validation",
            ),
            CanonicalRefusalCodes.ALL,
        )
    }

    @Test
    fun `upload key includes physical conversion facts but excludes source id and padding`() {
        val a = ready(input(sourceId = "one", sourceRowBytes = 8, bytes = byteArrayOf(1, 2, 3, 4, 9, 9, 9, 9)))
        val same = ready(input(sourceId = "two", sourceRowBytes = 8, bytes = byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8)))
        assertEquals(a.key, same.key)
        assertNotEquals(a.key, ready(input(bytes = byteArrayOf(2, 2, 3, 4))).key)
        assertNotEquals(a.key, ready(input(width = 2, sourceRowBytes = 8, bytes = byteArrayOf(1, 2, 3, 4, 1, 2, 3, 4))).key)
        assertNotEquals(a.key, ready(input(sourceRowBytes = 4, bytes = byteArrayOf(1, 2, 3, 4))).key)
        assertNotEquals(a.key, ready(input(format = GPUPreparedImageSourceFormat.Bgra8, bytes = byteArrayOf(3, 2, 1, 4))).key)
        assertNotEquals(a.key, ready(input(alpha = AlphaType.OPAQUE, bytes = byteArrayOf(1, 2, 3, -1))).key)
        assertNotEquals(a.key, ready(input(provenance = GPUPreparedImageProvenance.SurfaceReadback)).key)
        assertNotEquals(a.key, ready(input(generation = 8)).key)
    }

    private fun ready(input: GPUPreparedImageSourceInput): PreparedUploadArtifact =
        assertIs<GPUPreparedImageArtifactResult.Ready>(GPUPreparedImageArtifactFactory.prepare(input)).artifact

    private fun assertRefusal(input: GPUPreparedImageSourceInput, code: String) {
        val refused = assertIs<GPUPreparedImageArtifactResult.Refused>(
            GPUPreparedImageArtifactFactory.prepare(input),
        )
        assertEquals(code, refused.code)
        assertEquals("artifact", refused.facts["boundary"])
    }

    private fun input(
        sourceClass: GPUPreparedImageSourceClass = GPUPreparedImageSourceClass.DecodedCpu,
        sourceId: String = "source",
        width: Int = 1,
        format: GPUPreparedImageSourceFormat = GPUPreparedImageSourceFormat.Rgba8,
        alpha: AlphaType = AlphaType.PREMUL,
        sourceRowBytes: Long = 4,
        profile: GPUPreparedImageProfile = GPUPreparedImageProfile.Srgb,
        orientation: GPUPreparedImageOrientation = GPUPreparedImageOrientation.AppliedIdentity,
        provenance: GPUPreparedImageProvenance = GPUPreparedImageProvenance.CallerPixels,
        generation: Long = 7,
        bytes: ByteArray? = byteArrayOf(1, 2, 3, 4),
    ) = GPUPreparedImageSourceInput(sourceClass, sourceId, width, 1, format, alpha, sourceRowBytes, profile, orientation, provenance, generation, bytes)
}
