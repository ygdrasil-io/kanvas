package org.graphiks.kanvas.gpu.renderer.resources

import io.ygdrasil.webgpu.GPUTextureFormat
import io.ygdrasil.webgpu.GPUTextureUsage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUDeviceGenerationID
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUCapabilities
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUImplementationIdentity
import org.graphiks.kanvas.gpu.renderer.capabilities.GPULimits
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUTextureFormatSampleSupport
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUTextureSampleCountSupport
import org.graphiks.kanvas.gpu.renderer.color.GPUColorFormat
import org.graphiks.kanvas.gpu.renderer.coordinates.GPUPixelBounds

class GPUScratchTexturePoolTest {
    @Test
    fun `normalizes width and height independently while preserving logical bounds`() {
        val pool = GPUScratchTexturePool()

        val cases = listOf(
            1 to 16,
            16 to 16,
            17 to 32,
            900 to 1024,
            1023 to 1024,
            1024 to 1024,
            1025 to 1536,
            1536 to 1536,
            1537 to 2048,
            1600 to 2048,
            2100 to 3072,
        )

        cases.forEachIndexed { index, (logicalWidth, expectedBackingWidth) ->
            val logicalBounds = GPUPixelBounds(7, 11, 7 + logicalWidth, 28)
            val lease = pool.reserve(
                request = scratchRequest(
                    id = "normalize-$index",
                    logicalBounds = logicalBounds,
                    scope = "normalize-$index",
                ),
                budget = budgetPlan(configuredBytes = Long.MAX_VALUE),
            ).acceptedLease()

            assertEquals(logicalBounds, lease.logicalBounds)
            assertEquals(expectedBackingWidth, lease.backingWidth)
            assertEquals(32, lease.backingHeight)
        }
    }

    @Test
    fun `key uses exact physical topology but excludes logical bounds and semantic role`() {
        val pool = GPUScratchTexturePool()
        val budget = budgetPlan()
        val base = scratchRequest(
            id = "base",
            logicalBounds = GPUPixelBounds(3, 5, 20, 22),
            role = GPUFrameResourceRole.LayerTarget,
            usages = setOf(GPUFrameResourceUsage.RenderAttachment, GPUFrameResourceUsage.TextureBinding),
        )

        val first = pool.reserve(base, budget).acceptedLease()
        val sameKey = pool.reserve(
            base.copy(
                reservationId = "same-key",
                preparation = texturePreparation(
                    id = "same-key",
                    logicalBounds = GPUPixelBounds(41, 73, 60, 92),
                    role = GPUFrameResourceRole.FilterTarget,
                    usages = base.preparation.usages,
                ),
                firstStep = 3,
                lastStepExclusive = 4,
            ),
            budget,
        ).acceptedLease()

        assertEquals(first.key, sameKey.key)
        assertEquals(first.resourceRef, sameKey.resourceRef)
        assertEquals(GPUFrameResourceRole.LayerTarget, first.role)
        assertEquals(GPUFrameResourceRole.FilterTarget, sameKey.role)

        val variants = listOf(
            base.copy(reservationId = "generation", deviceGeneration = GPUDeviceGenerationID(8)),
            base.copy(
                reservationId = "width",
                preparation = texturePreparation(
                    id = "width",
                    logicalBounds = GPUPixelBounds(0, 0, 33, 17),
                    usages = base.preparation.usages,
                ),
            ),
            base.copy(
                reservationId = "format",
                preparation = texturePreparation(
                    id = "format",
                    logicalBounds = base.logicalBounds,
                    format = GPUColorFormat("bgra8unorm"),
                    usages = base.preparation.usages,
                ),
            ),
            base.copy(
                reservationId = "samples",
                preparation = texturePreparation(
                    id = "samples",
                    logicalBounds = base.logicalBounds,
                    sampleCount = 4,
                    usages = base.preparation.usages,
                ),
            ),
            base.copy(
                reservationId = "usage",
                preparation = texturePreparation(
                    id = "usage",
                    logicalBounds = base.logicalBounds,
                    usages = base.preparation.usages + GPUFrameResourceUsage.CopySource,
                ),
            ),
        )

        variants.forEach { variant ->
            assertNotEquals(first.key, pool.reserve(variant, budget).acceptedLease().key)
        }
    }

    @Test
    fun `refuses a format whose physical footprint is not centrally known`() {
        val pool = GPUScratchTexturePool()
        val base = scratchRequest(id = "unknown-format")
        val unknownFormat = base.copy(
            preparation = texturePreparation(
                id = "unknown-format",
                logicalBounds = base.logicalBounds,
                format = GPUColorFormat("vendor-private-format"),
                usages = base.preparation.usages,
            ),
        )

        val refused = assertIs<GPUScratchTextureReservationResult.Refused>(
            pool.reserve(unknownFormat, budgetPlan()),
        )

        assertEquals("unsupported.scratch_texture.format_footprint_unknown", refused.diagnostic.code.value)
    }

    @Test
    fun `validates normalized backing limits and checked physical sample bytes`() {
        val pool = GPUScratchTexturePool()
        val logicalWithinLimit = scratchRequest(
            id = "normalized-limit",
            logicalBounds = GPUPixelBounds(0, 0, 1025, 17),
        )

        val backingLimitRefusal = assertIs<GPUScratchTextureReservationResult.Refused>(
            pool.reserve(
                logicalWithinLimit,
                budgetPlan(),
                scratchCapabilities(maxTextureDimension2D = 1200),
            ),
        )
        assertEquals(
            "unsupported.scratch_texture.backing_dimension_exceeded",
            backingLimitRefusal.diagnostic.code.value,
        )
        assertEquals("1536x32", backingLimitRefusal.diagnostic.facts["requestedBackingExtent"])

        val multisampled = pool.reserve(
            scratchRequest(
                id = "multisampled",
                logicalBounds = GPUPixelBounds(0, 0, 17, 17),
            ).copy(
                preparation = texturePreparation(
                    id = "multisampled",
                    logicalBounds = GPUPixelBounds(0, 0, 17, 17),
                    sampleCount = 4,
                    usages = setOf(GPUFrameResourceUsage.RenderAttachment),
                ),
            ),
            budgetPlan(),
        ).acceptedLease()
        assertEquals(16_384, multisampled.physicalBytes)

        val hugeBounds = GPUPixelBounds(0, 0, 1_073_741_824, 1_073_741_824)
        val overflow = assertIs<GPUScratchTextureReservationResult.Refused>(
            pool.reserve(
                scratchRequest(id = "overflow", logicalBounds = hugeBounds).copy(
                    preparation = texturePreparation(
                        id = "overflow",
                        logicalBounds = hugeBounds,
                        sampleCount = 4,
                        usages = setOf(GPUFrameResourceUsage.RenderAttachment),
                        declaredByteSize = 0,
                    ),
                ),
                budgetPlan(configuredBytes = Long.MAX_VALUE),
                scratchCapabilities(maxTextureDimension2D = Int.MAX_VALUE.toLong()),
            ),
        )
        assertEquals("unsupported.scratch_texture.physical_size_overflow", overflow.diagnostic.code.value)

        val normalizationOverflow = assertIs<GPUScratchTextureReservationResult.Refused>(
            pool.reserve(
                scratchRequest(
                    id = "normalization-overflow",
                    logicalBounds = GPUPixelBounds(0, 0, Int.MAX_VALUE, 1),
                ).copy(
                    preparation = texturePreparation(
                        id = "normalization-overflow",
                        logicalBounds = GPUPixelBounds(0, 0, Int.MAX_VALUE, 1),
                        usages = setOf(GPUFrameResourceUsage.RenderAttachment),
                        declaredByteSize = 0,
                    ),
                ),
                budgetPlan(configuredBytes = Long.MAX_VALUE),
                scratchCapabilities(maxTextureDimension2D = Int.MAX_VALUE.toLong()),
            ),
        )
        assertEquals("unsupported.scratch_texture.backing_extent_overflow", normalizationOverflow.diagnostic.code.value)
    }

    @Test
    fun `aliases only half open non-overlapping intervals in one reservation scope`() {
        val pool = GPUScratchTexturePool()
        val budget = budgetPlan()
        val first = pool.reserve(
            scratchRequest(id = "first", scope = "preflight-1", firstStep = 0, lastStepExclusive = 3),
            budget,
        ).acceptedLease()

        val touching = pool.reserve(
            scratchRequest(id = "touching", scope = "preflight-1", firstStep = 3, lastStepExclusive = 5),
            budget,
        ).acceptedLease()
        val partialOverlap = pool.reserve(
            scratchRequest(id = "partial", scope = "preflight-1", firstStep = 2, lastStepExclusive = 4),
            budget,
        ).acceptedLease()
        val contained = pool.reserve(
            scratchRequest(id = "contained", scope = "preflight-1", firstStep = 1, lastStepExclusive = 2),
            budget,
        ).acceptedLease()
        val identical = pool.reserve(
            scratchRequest(id = "identical", scope = "preflight-1", firstStep = 0, lastStepExclusive = 3),
            budget,
        ).acceptedLease()

        assertEquals(first.resourceRef, touching.resourceRef)
        assertNotEquals(first.resourceRef, partialOverlap.resourceRef)
        assertNotEquals(first.resourceRef, contained.resourceRef)
        assertNotEquals(first.resourceRef, identical.resourceRef)
    }

    @Test
    fun `does not reuse across submissions until accepted completion`() {
        val pool = GPUScratchTexturePool()
        val budget = budgetPlan()
        val first = pool.reserve(scratchRequest(id = "first", scope = "frame-1"), budget).acceptedLease()
        val submission = GPUResourceSubmissionID(1)
        assertIs<GPUScratchLifecycleResult.Accepted>(
            pool.markSubmitted("frame-1", submission, first.deviceGeneration),
        )

        val beforeCompletion = pool.reserve(
            scratchRequest(id = "second", scope = "frame-2"),
            budget,
        ).acceptedLease()
        assertNotEquals(first.resourceRef, beforeCompletion.resourceRef)

        assertIs<GPUScratchLifecycleResult.Accepted>(
            pool.acceptCompletion(submission, first.deviceGeneration),
        )
        val afterCompletion = pool.reserve(
            scratchRequest(id = "third", scope = "frame-3"),
            budget,
        ).acceptedLease()
        assertEquals(first.resourceRef, afterCompletion.resourceRef)
    }

    @Test
    fun `late completion callback cannot affect a resource reused by a newer submission`() {
        val pool = GPUScratchTexturePool()
        val budget = budgetPlan()
        val first = pool.reserve(scratchRequest("first", scope = "frame-1"), budget).acceptedLease()
        pool.markSubmitted("frame-1", GPUResourceSubmissionID(1), first.deviceGeneration)
        pool.acceptCompletion(GPUResourceSubmissionID(1), first.deviceGeneration)

        val reused = pool.reserve(scratchRequest("reused", scope = "frame-2"), budget).acceptedLease()
        assertEquals(first.resourceRef, reused.resourceRef)
        pool.markSubmitted("frame-2", GPUResourceSubmissionID(2), reused.deviceGeneration)

        assertIs<GPUScratchLifecycleResult.Refused>(
            pool.rejectCompletion(
                GPUResourceSubmissionID(1),
                reused.deviceGeneration,
                GPUScratchCompletionFailure.Uncertain,
            ),
        )
        assertEquals(GPUScratchTextureState.Submitted, pool.stateOf(reused))
        assertIs<GPUScratchLifecycleResult.Accepted>(
            pool.acceptCompletion(GPUResourceSubmissionID(2), reused.deviceGeneration),
        )
    }

    @Test
    fun `stale scratch lease never resurrects after a newer reservation rolls back`() {
        val pool = GPUScratchTexturePool()
        val first = pool.reserve(scratchRequest("first", scope = "frame-1"), budgetPlan()).acceptedLease()
        pool.rollbackBeforeSubmit("frame-1")
        assertEquals(GPUScratchTextureState.Available, pool.stateOf(first))

        val second = pool.reserve(scratchRequest("second", scope = "frame-2"), budgetPlan()).acceptedLease()
        assertEquals(first.resourceRef, second.resourceRef)
        pool.rollbackBeforeSubmit("frame-2")

        assertEquals(GPUScratchTextureState.Invalidated, pool.stateOf(first))
        assertEquals(GPUScratchTextureState.Available, pool.stateOf(second))
    }

    @Test
    fun `scratch validates known format and usage against observed capabilities`() {
        val pool = GPUScratchTexturePool()
        val base = scratchRequest("capabilities")
        val unsupportedFormat = base.copy(
            preparation = texturePreparation(
                id = "capability-format",
                logicalBounds = base.logicalBounds,
                format = GPUColorFormat("r8unorm"),
                usages = base.preparation.usages,
            ),
        )
        val formatRefusal = assertIs<GPUScratchTextureReservationResult.Refused>(
            pool.reserve(
                unsupportedFormat,
                budgetPlan(),
                scratchCapabilities(
                    supportedFormats = setOf(GPUTextureFormat.RGBA8Unorm),
                    supportedUsage = GPUTextureUsage.RenderAttachment or GPUTextureUsage.TextureBinding,
                ),
            ),
        )
        assertEquals("unsupported.scratch_texture.capability_texture_format", formatRefusal.diagnostic.code.value)

        val storageRequest = scratchRequest(
            id = "capability-storage",
            usages = setOf(GPUFrameResourceUsage.RenderAttachment, GPUFrameResourceUsage.StorageBinding),
        )
        val usageRefusal = assertIs<GPUScratchTextureReservationResult.Refused>(
            pool.reserve(
                storageRequest,
                budgetPlan(),
                scratchCapabilities(
                    supportedFormats = setOf(GPUTextureFormat.RGBA8Unorm),
                    supportedUsage = GPUTextureUsage.RenderAttachment,
                ),
            ),
        )
        assertEquals("unsupported.scratch_texture.capability_texture_usage", usageRefusal.diagnostic.code.value)
    }

    @Test
    fun `scratch forwards exact sample count to observed format capabilities before reservation`() {
        val pool = GPUScratchTexturePool()
        val refused = assertIs<GPUScratchTextureReservationResult.Refused>(
            pool.reserve(
                scratchRequest("capability-samples", sampleCount = 8),
                budgetPlan(),
                scratchCapabilities(
                    supportedFormats = setOf(GPUTextureFormat.RGBA8Unorm),
                    supportedUsage = GPUTextureUsage.RenderAttachment or GPUTextureUsage.TextureBinding,
                    textureFormatSampleSupport = GPUTextureFormatSampleSupport(
                        mapOf(
                            GPUTextureFormat.RGBA8Unorm to GPUTextureSampleCountSupport(
                                renderAttachmentSampleCounts = setOf(1, 4),
                                resolveSourceSampleCounts = setOf(4),
                            ),
                        ),
                    ),
                ),
            ),
        )

        assertEquals(
            "unsupported.scratch_texture.capability_texture_sample_count",
            refused.diagnostic.code.value,
        )
        assertEquals(0, pool.trackedEntryCount)
    }

    @Test
    fun `use token exhaustion terminalizes scratch instead of reusing a stale LRU token`() {
        val ledger = GPUPhysicalPoolBudgetLedger(initialUseToken = Long.MAX_VALUE - 1)
        val pool = GPUScratchTexturePool(ledger)
        val lease = pool.reserve(scratchRequest("token", scope = "frame-token"), budgetPlan()).acceptedLease()

        pool.rollbackBeforeSubmit("frame-token")

        assertEquals(GPUScratchTextureState.Invalidated, pool.stateOf(lease))
        assertEquals(0, pool.telemetry().residentBytes)
        assertEquals(0, pool.trackedEntryCount)
    }

    @Test
    fun `refuses buffer-only usages before creating a scratch texture`() {
        val pool = GPUScratchTexturePool()
        val request = scratchRequest(
            id = "buffer-usage",
            usages = setOf(GPUFrameResourceUsage.MapRead, GPUFrameResourceUsage.Vertex),
        )

        val refused = assertIs<GPUScratchTextureReservationResult.Refused>(
            pool.reserve(request, budgetPlan()),
        )

        assertEquals("unsupported.scratch_texture.usage_invalid", refused.diagnostic.code.value)
        assertEquals(0, pool.telemetry().residentBytes)
    }

    @Test
    fun `unknown failure and device loss quarantine submitted resources`() {
        val pool = GPUScratchTexturePool()
        val budget = budgetPlan()
        val uncertain = pool.reserve(
            scratchRequest(id = "uncertain", scope = "frame-uncertain"),
            budget,
        ).acceptedLease()
        val deviceLost = pool.reserve(
            scratchRequest(id = "device-lost", scope = "frame-device-lost"),
            budget,
        ).acceptedLease()
        val uncertainSubmission = GPUResourceSubmissionID(1)
        val deviceLostSubmission = GPUResourceSubmissionID(2)
        pool.markSubmitted("frame-uncertain", uncertainSubmission, uncertain.deviceGeneration)
        pool.markSubmitted("frame-device-lost", deviceLostSubmission, deviceLost.deviceGeneration)

        assertIs<GPUScratchLifecycleResult.Accepted>(
            pool.rejectCompletion(
                uncertainSubmission,
                uncertain.deviceGeneration,
                GPUScratchCompletionFailure.Uncertain,
            ),
        )
        assertIs<GPUScratchLifecycleResult.Accepted>(
            pool.rejectCompletion(
                deviceLostSubmission,
                deviceLost.deviceGeneration,
                GPUScratchCompletionFailure.DeviceLost,
            ),
        )

        assertEquals(GPUScratchTextureState.Quarantined, pool.stateOf(uncertain))
        assertEquals(GPUScratchTextureState.Quarantined, pool.stateOf(deviceLost))
        assertTrue(pool.evictReclaimableUntil(0).isEmpty())
        val telemetry = pool.telemetry()
        assertEquals(telemetry.residentBytes, telemetry.quarantinedBytes)
        assertEquals(0, telemetry.reclaimableBytes)
    }

    @Test
    fun `rollback releases never submitted reservations in reverse order`() {
        val pool = GPUScratchTexturePool()
        val budget = budgetPlan()
        val leases = listOf("a", "b", "c").mapIndexed { index, id ->
            pool.reserve(
                scratchRequest(
                    id = id,
                    scope = "preflight",
                    firstStep = index * 2,
                    lastStepExclusive = index * 2 + 1,
                ),
                budget,
            ).acceptedLease()
        }

        val rollback = pool.rollbackBeforeSubmit("preflight")

        assertEquals(listOf("c", "b", "a"), rollback.releasedReservationIds)
        assertEquals(0, pool.telemetry().inFlightBytes)
        assertTrue(leases.all { lease -> pool.stateOf(lease) == GPUScratchTextureState.Available })
    }

    @Test
    fun `evicts completed resources by deterministic monotone use and invalidates old generations`() {
        val pool = GPUScratchTexturePool()
        val budget = budgetPlan()
        val first = pool.reserve(scratchRequest(id = "first", scope = "frame-1"), budget).acceptedLease()
        pool.markSubmitted("frame-1", GPUResourceSubmissionID(1), first.deviceGeneration)
        val second = pool.reserve(scratchRequest(id = "second", scope = "frame-2"), budget).acceptedLease()
        pool.markSubmitted("frame-2", GPUResourceSubmissionID(2), second.deviceGeneration)
        pool.acceptCompletion(GPUResourceSubmissionID(1), GPUDeviceGenerationID(7))
        pool.acceptCompletion(GPUResourceSubmissionID(2), GPUDeviceGenerationID(7))

        val touchedFirst = pool.reserve(scratchRequest(id = "touch", scope = "frame-3"), budget).acceptedLease()
        assertEquals(first.resourceRef, touchedFirst.resourceRef)
        pool.rollbackBeforeSubmit("frame-3")

        val evicted = pool.evictReclaimableUntil(first.physicalBytes)
        assertEquals(
            listOf(second.resourceRef),
            evicted.map { it.resourceRef },
        )
        assertEquals(GPUScratchTextureState.Evicted, evicted.single().state)
        assertEquals(GPUScratchTextureState.Invalidated, pool.stateOf(second))

        val invalidated = pool.invalidateGenerationsBefore(GPUDeviceGenerationID(8))
        assertEquals(listOf(first.resourceRef), invalidated.map { it.resourceRef })
        assertEquals(GPUScratchTextureState.Invalidated, pool.stateOf(first))
        val newGeneration = pool.reserve(
            scratchRequest(
                id = "new-generation",
                scope = "frame-4",
                deviceGeneration = GPUDeviceGenerationID(8),
            ),
            budget,
        ).acceptedLease()
        assertNotEquals(first.resourceRef, newGeneration.resourceRef)
        assertEquals(1, pool.trackedEntryCount)
    }

    @Test
    fun `refuses unknown duplicate and generation-mismatched lifecycle signals`() {
        val pool = GPUScratchTexturePool()
        val lease = pool.reserve(
            scratchRequest(id = "lease", scope = "frame-1"),
            budgetPlan(),
        ).acceptedLease()

        assertIs<GPUScratchLifecycleResult.Refused>(
            pool.acceptCompletion(GPUResourceSubmissionID(99), lease.deviceGeneration),
        )
        assertIs<GPUScratchLifecycleResult.Refused>(
            pool.markSubmitted("frame-1", GPUResourceSubmissionID(1), GPUDeviceGenerationID(8)),
        )
        assertEquals(GPUScratchTextureState.Reserved, pool.stateOf(lease))

        assertIs<GPUScratchLifecycleResult.Accepted>(
            pool.markSubmitted("frame-1", GPUResourceSubmissionID(1), lease.deviceGeneration),
        )
        assertIs<GPUScratchLifecycleResult.Refused>(
            pool.markSubmitted("frame-1", GPUResourceSubmissionID(2), lease.deviceGeneration),
        )
        assertIs<GPUScratchLifecycleResult.Refused>(
            pool.acceptCompletion(GPUResourceSubmissionID(1), GPUDeviceGenerationID(8)),
        )
        assertEquals(GPUScratchTextureState.Submitted, pool.stateOf(lease))

        pool.invalidateGenerationsBefore(GPUDeviceGenerationID(8))
        assertEquals(GPUScratchTextureState.Invalidated, pool.stateOf(lease))
        assertEquals(0, pool.telemetry().residentBytes)
        assertIs<GPUScratchLifecycleResult.Refused>(
            pool.acceptCompletion(GPUResourceSubmissionID(1), lease.deviceGeneration),
        )

        val completedPool = GPUScratchTexturePool()
        val completed = completedPool.reserve(
            scratchRequest(id = "completed", scope = "frame-completed"),
            budgetPlan(),
        ).acceptedLease()
        completedPool.markSubmitted(
            "frame-completed",
            GPUResourceSubmissionID(7),
            completed.deviceGeneration,
        )
        assertIs<GPUScratchLifecycleResult.Accepted>(
            completedPool.acceptCompletion(GPUResourceSubmissionID(7), completed.deviceGeneration),
        )
        assertIs<GPUScratchLifecycleResult.Refused>(
            completedPool.acceptCompletion(GPUResourceSubmissionID(7), completed.deviceGeneration),
        )
    }

    @Test
    fun `budget replaces logical scratch estimate with normalized physical bytes and reports all totals`() {
        val pool = GPUScratchTexturePool()
        val budget = budgetPlan(
            configuredBytes = 5_500,
            targetResidentBytes = 1_024,
            logicalScratchEstimate = 1_156,
        )
        val first = pool.reserve(
            scratchRequest(
                id = "first",
                scope = "frame-1",
                logicalBounds = GPUPixelBounds(0, 0, 17, 17),
            ),
            budget,
        ).acceptedLease()

        assertEquals(4_096, first.physicalBytes)
        val refused = assertIs<GPUScratchTextureReservationResult.Refused>(
            pool.reserve(
                scratchRequest(
                    id = "over-budget",
                    scope = "frame-1",
                    logicalBounds = GPUPixelBounds(0, 0, 17, 17),
                    firstStep = 0,
                    lastStepExclusive = 1,
                ),
                budget,
            ),
        )

        assertEquals("unsupported.scratch_texture.aggregate_budget_exceeded", refused.diagnostic.code.value)
        assertEquals("4096", refused.diagnostic.facts["requestedBackingBytes"])
        assertEquals("4096", refused.diagnostic.facts["residentBytes"])
        assertEquals("4096", refused.diagnostic.facts["reservedBytes"])
        assertEquals("0", refused.diagnostic.facts["inFlightBytes"])
        assertEquals("0", refused.diagnostic.facts["reclaimableBytes"])
        assertEquals("0", refused.diagnostic.facts["quarantinedBytes"])
        assertEquals("32x32", refused.diagnostic.facts["requestedBackingExtent"])
        GPUFrameMemoryCategory.entries.forEach { category ->
            assertTrue("category.${category.name}" in refused.diagnostic.facts)
        }

        val telemetry = pool.telemetry()
        assertEquals(4_096, telemetry.residentBytes)
        assertEquals(4_096, telemetry.reservedBytes)
        assertEquals(0, telemetry.inFlightBytes)
        assertEquals(4_096, telemetry.categoryTotals.getValue(GPUFrameMemoryCategory.ReusableScratch))
        assertEquals(0, telemetry.reclaimableBytes)
    }
}

private fun GPUScratchTextureReservationResult.acceptedLease(): GPUScratchTextureLease =
    assertIs<GPUScratchTextureReservationResult.Accepted>(this).lease

private fun scratchRequest(
    id: String,
    scope: String = "preflight",
    logicalBounds: GPUPixelBounds = GPUPixelBounds(0, 0, 17, 17),
    role: GPUFrameResourceRole = GPUFrameResourceRole.FilterTarget,
    usages: Set<GPUFrameResourceUsage> = setOf(
        GPUFrameResourceUsage.RenderAttachment,
        GPUFrameResourceUsage.TextureBinding,
    ),
    deviceGeneration: GPUDeviceGenerationID = GPUDeviceGenerationID(7),
    firstStep: Int = 0,
    lastStepExclusive: Int = 2,
    sampleCount: Int = 1,
): GPUScratchTextureReservationRequest =
    GPUScratchTextureReservationRequest(
        reservationId = id,
        reservationScope = scope,
        preparation = texturePreparation(
            id,
            logicalBounds,
            role = role,
            sampleCount = sampleCount,
            usages = usages,
        ),
        deviceGeneration = deviceGeneration,
        firstStep = firstStep,
        lastStepExclusive = lastStepExclusive,
    )

private fun texturePreparation(
    id: String,
    logicalBounds: GPUPixelBounds,
    role: GPUFrameResourceRole = GPUFrameResourceRole.FilterTarget,
    format: GPUColorFormat = GPUColorFormat("rgba8unorm"),
    sampleCount: Int = 1,
    usages: Set<GPUFrameResourceUsage>,
    declaredByteSize: Long? = null,
): GPUResourcePreparationRequest =
    GPUResourcePreparationRequest(
        resource = GPUFrameTextureRef("texture-$id"),
        descriptor = GPUFrameTextureDescriptor(logicalBounds, format, sampleCount),
        role = role,
        usages = usages,
        lifetime = GPUFrameResourceLifetime.FrameLocal,
        byteSize = declaredByteSize
            ?: logicalBounds.checkedByteSize(bytesPerPixel = 4, sampleCount = sampleCount),
        diagnosticLabel = "scratch-$id",
    )

private fun budgetPlan(
    configuredBytes: Long = 1L shl 30,
    targetResidentBytes: Long = 0,
    logicalScratchEstimate: Long = 0,
): GPUFrameMemoryBudgetPlan {
    val categoryTotals = GPUFrameMemoryCategory.entries.associateWith { category ->
        when (category) {
            GPUFrameMemoryCategory.CanonicalTarget -> targetResidentBytes
            GPUFrameMemoryCategory.ReusableScratch -> logicalScratchEstimate
            else -> 0L
        }
    }
    return GPUFrameMemoryBudgetPlan(
        peakFrameTransientBytes = logicalScratchEstimate,
        targetResidentBytes = targetResidentBytes,
        categoryTotals = categoryTotals,
        deviceLimitFacts = emptyList(),
        configuredAggregateBudgetBytes = configuredBytes,
        diagnostic = null,
    )
}

private fun GPUScratchTexturePool.reserve(
    request: GPUScratchTextureReservationRequest,
    budget: GPUFrameMemoryBudgetPlan,
): GPUScratchTextureReservationResult = reserve(request, budget, scratchCapabilities())

private fun scratchCapabilities(
    maxTextureDimension2D: Long = 16_384,
    supportedFormats: Set<GPUTextureFormat> = emptySet(),
    supportedUsage: GPUTextureUsage? = null,
    textureFormatSampleSupport: GPUTextureFormatSampleSupport = GPUTextureFormatSampleSupport(
        mapOf(
            GPUTextureFormat.RGBA8Unorm to GPUTextureSampleCountSupport(
                renderAttachmentSampleCounts = setOf(1, 4),
                resolveSourceSampleCounts = setOf(4),
            ),
        ),
    ),
): GPUCapabilities =
    GPUCapabilities(
        implementation = GPUImplementationIdentity(
            facadeName = "GPU",
            implementationName = "unit",
            adapterName = "unit-adapter",
            deviceName = "unit-device",
        ),
        facts = emptyList(),
        snapshotId = "scratch-unit",
        supportedTextureFormats = supportedFormats,
        supportedTextureUsage = supportedUsage,
        textureFormatSampleSupport = textureFormatSampleSupport,
        limits = GPULimits(
            maxTextureDimension2D = maxTextureDimension2D,
            copyBytesPerRowAlignment = 256,
            minUniformBufferOffsetAlignment = 256,
            maxBufferSize = 1L shl 30,
        ),
    )
