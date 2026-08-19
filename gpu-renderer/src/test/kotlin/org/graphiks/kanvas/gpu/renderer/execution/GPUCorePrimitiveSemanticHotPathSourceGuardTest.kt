package org.graphiks.kanvas.gpu.renderer.execution

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GPUCorePrimitiveSemanticHotPathSourceGuardTest {
    @Test
    fun `coverage mask hot paths never evaluate semantic canonical hashes`() {
        val sourceRoot = File("src/main/kotlin/org/graphiks/kanvas/gpu/renderer")
        val hotPaths = listOf(
            sourceRoot.resolve(
                "recording/GPUCorePrimitivePreparedFrameTaskListBuilder.kt",
            ),
            sourceRoot.resolve("execution/GPUFramePreflighter.kt"),
            sourceRoot.resolve(
                "execution/GPUWgpu4kCorePrimitiveFramePayloadMaterializer.kt",
            ),
        )

        hotPaths.forEach { sourceFile ->
            assertFalse(
                sourceFile.readText().contains("semantic.canonicalHash"),
                "${sourceFile.name} must validate the opaque semantic authority in O(1)",
            )
        }
    }

    @Test
    fun `coverage mask owner validation stays allocation free and execution consumes only its seal`() {
        val sourceRoot = File("src/main/kotlin/org/graphiks/kanvas/gpu/renderer")
        val owner = sourceRoot.resolve(
            "passes/GPUCorePrimitiveCoverageMaskPreparedRoute.kt",
        ).readText()
        val validation = owner
            .substringAfter("internal fun validateGPUCorePrimitiveCoverageMaskPreparedAuthority(")
            .substringBefore("internal fun corePrimitiveCoverageMaskProducerUniformBytes(")
        listOf(
            "ByteArray(",
            "corePrimitiveCoverageMaskProducerUniformBytes(",
            "corePrimitiveCoverageMaskConsumerUniformBytes(",
            "snapshotGPUCorePrimitiveCoverageMaskPreparedCandidate(",
            "sealGPUCorePrimitiveCoverageMaskPreparedRoute(",
            ".take(",
            ".drop(",
            ".map {",
            "stableRenderPipelineKey(",
        ).forEach { forbidden ->
            assertFalse(
                validation.contains(forbidden),
                "coverage-mask owner validation must not re-snapshot or repack: $forbidden",
            )
        }
        assertTrue(validation.contains("hasLittleEndianFloat"))
        assertTrue(validation.contains("hasLittleEndianInt"))

        val preflight = sourceRoot.resolve("execution/GPUFramePreflighter.kt").readText()
        val coveragePreflight = preflight
            .substringAfter("private fun validateCorePrimitiveCoverageMaskPreparedRoutes(")
            .substringBefore("private fun validateCorePrimitiveClipStencilPreparedRoutes(")
        assertFalse(
            coveragePreflight.contains("clipExecutionPlan"),
            "execution coverage-mask preflight must consume the owner-side passive validation only",
        )

        val materializer = sourceRoot.resolve(
            "execution/GPUWgpu4kCorePrimitiveFramePayloadMaterializer.kt",
        ).readText()
        val coverageMaterializer = materializer
            .substringAfter("private fun materializePreparedCoverageMaskCore(")
            .substringBefore("private fun materializePreparedClipStencilCore(")
        listOf(
            "clipExecutionPlan",
            "semanticAuthority.matches(",
            "validateCorePrimitiveClipProducerAuthority(",
        ).forEach { forbidden ->
            assertFalse(
                coverageMaterializer.contains(forbidden),
                "coverage-mask materializer must consume retained passive route authority: $forbidden",
            )
        }
        assertTrue(
            Regex("""\bvalidateGPUCorePrimitiveCoverageMaskPreparedAuthority\(""")
                .findAll(coverageMaterializer)
                .count() == 1,
            "coverage-mask materialization must reauthenticate once through the allocation-free owner",
        )

        val genericProducerValidation = sourceRoot.resolve(
            "recording/GPUCorePrimitivePreparedFrameTaskListBuilder.kt",
        ).readText()
            .substringAfter("internal fun validateCorePrimitiveClipProducerAuthority(")
            .substringBefore("data class GPUCorePrimitivePreparedFrameRequest(")
        assertFalse(genericProducerValidation.contains("nativeCoverageSlabSeal"))
        assertFalse(genericProducerValidation.contains("coverageMaskUniformSlabSeal"))
        assertTrue(
            genericProducerValidation.contains("alreadySealedCoverageMaskProducerPacketIds"),
        )
        assertTrue(
            genericProducerValidation.contains("alreadySealedCoverageMaskConsumerPacketIds"),
        )
        assertFalse(
            genericProducerValidation.contains("alreadySealedCoverageMaskPlanIdentities"),
            "generic validation must exempt exact sealed packet IDs, never a global plan identity",
        )
        val exactConsumerLoop = genericProducerValidation
            .substringAfter("for (consumerPacket in render.drawPackets)")
            .substringBefore("return GPUCorePrimitiveClipProducerValidation(")
        val sealedConsumerSkip = exactConsumerLoop.indexOf(
            "consumerPacket.packetId in alreadySealedCoverageMaskConsumerPacketIds",
        )
        val firstConsumerPlanRead = exactConsumerLoop.indexOf(
            "val consumerPlan = consumerPacket.clipExecutionPlan",
        )
        assertTrue(
            sealedConsumerSkip >= 0 && firstConsumerPlanRead > sealedConsumerSkip,
            "the exact sealed consumer-ID short circuit must precede clipExecutionPlan access",
        )
        assertFalse(
            exactConsumerLoop.substring(0, firstConsumerPlanRead).contains("clipExecutionPlan"),
            "sealed consumers must be skipped before any inferred clip-plan access",
        )

        val preparedSurfacePreflight = sourceRoot.resolve(
            "execution/GPUPreparedSurfaceNativePreflight.kt",
        ).readText()
        val colorGlyphDestinationAuthentication = preparedSurfacePreflight
            .substringAfter("private fun authenticateColorGlyphDestinationReads(")
            .substringBefore("private fun validatePreparedTextAuthority(")
        assertTrue(
            colorGlyphDestinationAuthentication.contains(
                "matchesPreparedColorGlyphCoverageMaskProducer",
            ),
        )
        assertFalse(
            colorGlyphDestinationAuthentication.contains("producerPacket.clipExecutionPlan"),
            "execution must consume the recording-owner CoverageMask producer predicate",
        )
        val preparedTextDependencies = preparedSurfacePreflight
            .substringAfter("private fun validatePreparedTextDependencies(")
            .substringBefore("fun validate(")
        assertTrue(preparedTextDependencies.contains("orderingToken"))
        assertFalse(
            preparedTextDependencies.contains("clipExecutionPlan") ||
                preparedTextDependencies.contains("coverageMaskUniformSlabSeal"),
            "prepared-text dependencies must consume the passive clip ordering seal",
        )
    }
}
