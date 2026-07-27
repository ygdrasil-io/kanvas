package org.graphiks.kanvas.surface.gpu

import org.graphiks.kanvas.gpu.renderer.images.GPUPreparedImageArtifactFactory
import org.graphiks.kanvas.gpu.renderer.images.GPUPreparedImageArtifactResult
import org.graphiks.kanvas.gpu.renderer.diagnostics.GPUPreparedImageRefusalCodes
import org.graphiks.kanvas.paint.BlendMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class GPUPreparedImageSourceRefusalMatrixTest {

    @Test
    fun `every testable source refusal case produces its exact stable code`() {
        for (case in GPUPreparedImageRefusalMatrix.sourceRefusalCases.filter { it.testableNow }) {
            val result = GPUPreparedImageArtifactFactory.prepare(case.input)

            assertIs<GPUPreparedImageArtifactResult.Refused>(
                result,
                "case ${case.name}: expected Refused, got ${result::class.simpleName}",
            )
            val refused = result as GPUPreparedImageArtifactResult.Refused

            assertEquals(
                case.expectedCode,
                refused.code,
                "case ${case.name}: wrong refusal code",
            )
            assertNotNull(refused.facts["boundary"], "case ${case.name}: missing boundary fact")
        }
    }

    @Test
    fun `upload budget exceeded case refuses before allocation`() {
        val case = GPUPreparedImageRefusalMatrix.uploadBudgetCase
        val result = GPUPreparedImageArtifactFactory.prepare(case.input, maxUploadBytes = 0)

        val refused = assertIs<GPUPreparedImageArtifactResult.Refused>(result)
        assertEquals(GPUPreparedImageRefusalCodes.UPLOAD_BUDGET_EXCEEDED, refused.code)
    }

    @Test
    fun `animation refusal code is the canonical unsupported dot image dot animation`() {
        assertEquals(
            "unsupported.image.animation",
            GPUPreparedImageRefusalCodes.ANIMATION,
        )
        val result = GPUPreparedImageArtifactFactory.prepare(
            GPUPreparedImageRefusalMatrix.sourceRefusalCases
                .first { it.name == "animated source" }.input,
        )
        assertEquals(
            "unsupported.image.animation",
            (result as GPUPreparedImageArtifactResult.Refused).code,
        )
    }

    @Test
    fun `all 29 BlendMode entries are classified in atlas blend table`() {
        val covered = GPUPreparedImageRefusalMatrix.atlasBlendCases.map { it.blendMode }.toSet()
        assertEquals(
            BlendMode.entries.toSet(),
            covered,
            "atlas blend table must cover every BlendMode entry",
        )
    }

    @Test
    fun `accepted atlas blend modes are exactly Src Dst SrcOver Plus Modulate`() {
        assertEquals(
            setOf(BlendMode.SRC, BlendMode.DST, BlendMode.SRC_OVER, BlendMode.PLUS, BlendMode.MODULATE),
            GPUPreparedImageRefusalMatrix.acceptedAtlasBlendModes,
        )
    }

    @Test
    fun `every rejected atlas blend mode maps to atlas_source_blend refusal`() {
        for (case in GPUPreparedImageRefusalMatrix.atlasBlendCases.filter { !it.accepted }) {
            assertEquals(
                GPUPreparedImageRefusalCodes.ATLAS_SOURCE_BLEND,
                case.refusalCode,
                "blend mode ${case.blendMode} should refuse ${GPUPreparedImageRefusalCodes.ATLAS_SOURCE_BLEND}",
            )
        }
    }

    @Test
    fun `every accepted atlas blend mode has no refusal code`() {
        for (case in GPUPreparedImageRefusalMatrix.atlasBlendCases.filter { it.accepted }) {
            assertEquals(
                null,
                case.refusalCode,
                "blend mode ${case.blendMode} should be accepted (null refusal)",
            )
        }
    }

    @Test
    fun `all 29 refusal codes in GPUPreparedImageRefusalCodes ALL are present in the matrix`() {
        val covered = GPUPreparedImageRefusalMatrix.sourceRefusalCases
            .filter { it.testableNow }
            .map { it.expectedCode }
            .toMutableSet()
        covered.add(GPUPreparedImageRefusalMatrix.uploadBudgetCase.expectedCode)
        covered.addAll(
            GPUPreparedImageRefusalMatrix.futureSourceBoundaryCases.map { it.expectedCode },
        )
        covered.addAll(
            GPUPreparedImageRefusalMatrix.atlasBlendCases
                .filter { !it.accepted }
                .mapNotNull { it.refusalCode },
        )

        assertEquals(
            GPUPreparedImageRefusalCodes.ALL,
            covered,
            "matrix must cover every refusal code in GPUPreparedImageRefusalCodes.ALL",
        )
    }

    @Test
    fun `A8 pixels with PREMUL alpha are accepted`() {
        val result = GPUPreparedImageArtifactFactory.prepare(
            org.graphiks.kanvas.gpu.renderer.images.GPUPreparedImageSourceInput(
                org.graphiks.kanvas.gpu.renderer.images.GPUPreparedImageSourceClass.DecodedCpu,
                "a8", 3, 1,
                org.graphiks.kanvas.gpu.renderer.images.GPUPreparedImageSourceFormat.A8,
                org.graphiks.kanvas.gpu.renderer.images.AlphaType.PREMUL,
                3,
                org.graphiks.kanvas.gpu.renderer.images.GPUPreparedImageProfile.Srgb,
                org.graphiks.kanvas.gpu.renderer.images.GPUPreparedImageOrientation.AppliedIdentity,
                org.graphiks.kanvas.gpu.renderer.images.GPUPreparedImageProvenance.CallerPixels,
                7, byteArrayOf(1, 2, 3),
            ),
        )
        assertIs<GPUPreparedImageArtifactResult.Ready>(result)
    }

    @Test
    fun `OPAQUE RGBA with all 255 alpha bytes is accepted`() {
        val result = GPUPreparedImageArtifactFactory.prepare(
            org.graphiks.kanvas.gpu.renderer.images.GPUPreparedImageSourceInput(
                org.graphiks.kanvas.gpu.renderer.images.GPUPreparedImageSourceClass.DecodedCpu,
                "opaque", 2, 1,
                org.graphiks.kanvas.gpu.renderer.images.GPUPreparedImageSourceFormat.Rgba8,
                org.graphiks.kanvas.gpu.renderer.images.AlphaType.OPAQUE,
                8,
                org.graphiks.kanvas.gpu.renderer.images.GPUPreparedImageProfile.Srgb,
                org.graphiks.kanvas.gpu.renderer.images.GPUPreparedImageOrientation.AppliedIdentity,
                org.graphiks.kanvas.gpu.renderer.images.GPUPreparedImageProvenance.CallerPixels,
                7, byteArrayOf(1, 2, 3, -1, 4, 5, 6, -1),
            ),
        )
        assertIs<GPUPreparedImageArtifactResult.Ready>(result)
    }

    @Test
    fun `BGRA premultiplied pixels are accepted`() {
        val result = GPUPreparedImageArtifactFactory.prepare(
            org.graphiks.kanvas.gpu.renderer.images.GPUPreparedImageSourceInput(
                org.graphiks.kanvas.gpu.renderer.images.GPUPreparedImageSourceClass.DecodedCpu,
                "bgra", 1, 1,
                org.graphiks.kanvas.gpu.renderer.images.GPUPreparedImageSourceFormat.Bgra8,
                org.graphiks.kanvas.gpu.renderer.images.AlphaType.PREMUL,
                4,
                org.graphiks.kanvas.gpu.renderer.images.GPUPreparedImageProfile.Srgb,
                org.graphiks.kanvas.gpu.renderer.images.GPUPreparedImageOrientation.AppliedIdentity,
                org.graphiks.kanvas.gpu.renderer.images.GPUPreparedImageProvenance.CallerPixels,
                7, byteArrayOf(3, 2, 1, 4),
            ),
        )
        assertIs<GPUPreparedImageArtifactResult.Ready>(result)
    }
}
