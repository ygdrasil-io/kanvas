package org.graphiks.kanvas.gpu.renderer.execution

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUCapabilities
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUDeviceGenerationID
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUImplementationIdentity
import org.graphiks.kanvas.gpu.renderer.capabilities.GPULimits
import org.graphiks.kanvas.gpu.renderer.capabilities.GPURendererFeature
import org.graphiks.kanvas.gpu.renderer.color.GPUColorInterpretation
import org.graphiks.kanvas.gpu.renderer.coordinates.GPUPixelBounds
import org.graphiks.kanvas.gpu.renderer.diagnostics.GPUDiagnostic
import org.graphiks.kanvas.gpu.renderer.diagnostics.GPUDiagnosticCode
import org.graphiks.kanvas.gpu.renderer.diagnostics.GPUDiagnosticDomain
import org.graphiks.kanvas.gpu.renderer.diagnostics.GPUDiagnosticSeverity
import org.graphiks.kanvas.gpu.renderer.recording.GPUFrameReadbackRequest
import org.graphiks.kanvas.gpu.renderer.recording.GPUReadbackPixelFormat
import org.graphiks.kanvas.gpu.renderer.recording.GPUReadbackRequestID
import org.graphiks.kanvas.gpu.renderer.resources.GPUBufferResourceRef
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceUsage
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameMemoryBudgetPlan
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameMemoryCategory
import org.graphiks.kanvas.gpu.renderer.resources.GPUReadbackCompletionFailure
import org.graphiks.kanvas.gpu.renderer.resources.GPUReadbackMapFailureSafety
import org.graphiks.kanvas.gpu.renderer.resources.GPUReadbackStagingDescriptorContract
import org.graphiks.kanvas.gpu.renderer.resources.GPUReadbackStagingLifecycleResult
import org.graphiks.kanvas.gpu.renderer.resources.GPUReadbackStagingPool
import org.graphiks.kanvas.gpu.renderer.resources.GPUReadbackStagingReservationRequest
import org.graphiks.kanvas.gpu.renderer.resources.GPUReadbackStagingReservationResult
import org.graphiks.kanvas.gpu.renderer.resources.GPUReadbackStagingState
import org.graphiks.kanvas.gpu.renderer.resources.GPUResourceSubmissionID
import org.graphiks.kanvas.gpu.renderer.resources.GPUPhysicalPoolBudgetLedger

class GPUReadbackLayoutTest {
    @Test
    fun `plans exact Dawn minimum for 256 and 512 byte row alignments`() {
        val request = readbackRequest(width = 129, height = 3)

        val aligned256 = planned(request, capabilities(alignment = 256))
        val aligned512 = planned(request, capabilities(alignment = 512))

        assertEquals(516, aligned256.layout.unpaddedBytesPerRow)
        assertEquals(768, aligned256.layout.paddedBytesPerRow)
        assertEquals(2_052, aligned256.layout.totalBufferBytes)
        assertEquals(256, aligned256.layout.copyBytesPerRowAlignment)
        assertEquals(3, aligned256.layout.rowsPerImage)

        assertEquals(516, aligned512.layout.unpaddedBytesPerRow)
        assertEquals(1_024, aligned512.layout.paddedBytesPerRow)
        assertEquals(2_564, aligned512.layout.totalBufferBytes)
        assertEquals(512, aligned512.layout.copyBytesPerRowAlignment)
        assertEquals(3, aligned512.layout.rowsPerImage)
    }

    @Test
    fun `preserves an aligned caller offset and does not pad the final row`() {
        val plan = planned(
            request = readbackRequest(width = 3, height = 2, bufferOffsetBytes = 4),
            capabilities = capabilities(alignment = 256),
        )

        assertEquals(3, plan.layout.width)
        assertEquals(2, plan.layout.height)
        assertEquals(4, plan.layout.bytesPerPixel)
        assertEquals(12, plan.layout.unpaddedBytesPerRow)
        assertEquals(256, plan.layout.paddedBytesPerRow)
        assertEquals(4, plan.layout.bufferOffset)
        assertEquals(272, plan.layout.totalBufferBytes)
        assertEquals(0, plan.stagingDescriptor.mapOffsetBytes)

        val oneRow = planned(
            request = readbackRequest(width = 3, height = 1, bufferOffsetBytes = 8),
            capabilities = capabilities(alignment = 256),
        )
        assertEquals(20, oneRow.layout.totalBufferBytes)
    }

    @Test
    fun `rejects invalid row alignment without hard coding 256`() {
        val refused = assertIs<GPUReadbackLayoutPlan.Refused>(
            GPUReadbackLayoutPlanner().plan(
                readbackRequest(width = 4, height = 4),
                capabilities(alignment = 384),
            ),
        )

        assertEquals("unsupported.readback.row_alignment_invalid", refused.diagnostic.code.value)
        assertEquals("384", refused.diagnostic.facts["copyBytesPerRowAlignment"])

        assertFailsWith<IllegalArgumentException> {
            limits(alignment = 0)
        }
        assertFailsWith<IllegalArgumentException> {
            limits(alignment = -256)
        }
    }

    @Test
    fun `rejects a misaligned RGBA8 offset instead of rounding it`() {
        val refused = assertIs<GPUReadbackLayoutPlan.Refused>(
            GPUReadbackLayoutPlanner().plan(
                readbackRequest(width = 7, height = 2, bufferOffsetBytes = 2),
                capabilities(),
            ),
        )

        assertEquals("unsupported.readback.buffer_offset_alignment", refused.diagnostic.code.value)
        assertEquals("2", refused.diagnostic.facts["bufferOffsetBytes"])
        assertEquals("4", refused.diagnostic.facts["requiredAlignmentBytes"])
    }

    @Test
    fun `refuses empty bounds and unavailable readback limits with typed diagnostics`() {
        val empty = assertIs<GPUReadbackLayoutPlan.Refused>(
            GPUReadbackLayoutPlanner().plan(
                readbackRequest(bounds = GPUPixelBounds(0, 0, 0, 4)),
                capabilities(),
            ),
        )
        assertEquals("unsupported.readback.bounds_empty", empty.diagnostic.code.value)

        val limitsMissing = assertIs<GPUReadbackLayoutPlan.Refused>(
            GPUReadbackLayoutPlanner().plan(
                readbackRequest(width = 4, height = 4),
                capabilities(limits = null),
            ),
        )
        assertEquals("unsupported.readback.limits_unavailable", limitsMissing.diagnostic.code.value)

        val maxBufferMissing = assertIs<GPUReadbackLayoutPlan.Refused>(
            GPUReadbackLayoutPlanner().plan(
                readbackRequest(width = 4, height = 4),
                capabilities(maxBufferSize = null),
            ),
        )
        assertEquals(
            "unsupported.readback.max_buffer_size_unavailable",
            maxBufferMissing.diagnostic.code.value,
        )

        val featureMissing = assertIs<GPUReadbackLayoutPlan.Refused>(
            GPUReadbackLayoutPlanner().plan(
                readbackRequest(width = 4, height = 4),
                capabilities(rendererFeatures = setOf(GPURendererFeature.RenderPass)),
            ),
        )
        assertEquals("unsupported.readback.capability_unavailable", featureMissing.diagnostic.code.value)
    }

    @Test
    fun `accepts the exact max buffer boundary and refuses one byte below it`() {
        val request = readbackRequest(width = 65, height = 2)
        val exact = planned(request, capabilities(maxBufferSize = 772))

        assertEquals(260, exact.layout.unpaddedBytesPerRow)
        assertEquals(512, exact.layout.paddedBytesPerRow)
        assertEquals(772, exact.layout.totalBufferBytes)

        val refused = assertIs<GPUReadbackLayoutPlan.Refused>(
            GPUReadbackLayoutPlanner().plan(request, capabilities(maxBufferSize = 771)),
        )
        assertEquals("unsupported.readback.max_buffer_size_exceeded", refused.diagnostic.code.value)
        assertEquals("772", refused.diagnostic.facts["totalBufferBytes"])
        assertEquals("771", refused.diagnostic.facts["maxBufferSize"])
    }

    @Test
    fun `refuses WebGPU row fields above UInt before narrowing`() {
        val bytesPerRowOverflow = assertIs<GPUReadbackLayoutPlan.Refused>(
            GPUReadbackLayoutPlanner().plan(
                readbackRequest(width = 1_073_741_824, height = 1),
                capabilities(maxBufferSize = Long.MAX_VALUE),
            ),
        )
        assertEquals("unsupported.readback.row_field_uint_overflow", bytesPerRowOverflow.diagnostic.code.value)
        assertEquals("4294967296", bytesPerRowOverflow.diagnostic.facts["unpaddedBytesPerRow"])

        val alignedRowOverflow = assertIs<GPUReadbackLayoutPlan.Refused>(
            GPUReadbackLayoutPlanner().plan(
                readbackRequest(width = 1, height = 1),
                capabilities(alignment = 1L shl 32, maxBufferSize = Long.MAX_VALUE),
            ),
        )
        assertEquals("unsupported.readback.row_field_uint_overflow", alignedRowOverflow.diagnostic.code.value)
        assertEquals("4294967296", alignedRowOverflow.diagnostic.facts["paddedBytesPerRow"])
    }

    @Test
    fun `refuses checked Long overflow in exact minimum buffer size`() {
        val offset = Long.MAX_VALUE - 3
        val refused = assertIs<GPUReadbackLayoutPlan.Refused>(
            GPUReadbackLayoutPlanner().plan(
                readbackRequest(width = 1, height = 1, bufferOffsetBytes = offset),
                capabilities(maxBufferSize = Long.MAX_VALUE),
            ),
        )

        assertEquals("unsupported.readback.buffer_size_overflow", refused.diagnostic.code.value)
        assertEquals(offset.toString(), refused.diagnostic.facts["bufferOffsetBytes"])
    }

    @Test
    fun `refuses tightly packed and padded host buffers above ByteArray limits`() {
        val packedTooLarge = assertIs<GPUReadbackLayoutPlan.Refused>(
            GPUReadbackLayoutPlanner().plan(
                readbackRequest(width = 268_435_456, height = 2),
                capabilities(maxBufferSize = Long.MAX_VALUE),
            ),
        )
        assertEquals("unsupported.readback.host_packed_output_too_large", packedTooLarge.diagnostic.code.value)
        assertEquals("2147483648", packedTooLarge.diagnostic.facts["packedOutputBytes"])

        val paddedTooLarge = assertIs<GPUReadbackLayoutPlan.Refused>(
            GPUReadbackLayoutPlanner().plan(
                readbackRequest(width = 1, height = 3),
                capabilities(
                    alignment = 1L shl 30,
                    maxBufferSize = Long.MAX_VALUE,
                ),
            ),
        )
        assertEquals("unsupported.readback.host_mapped_input_too_large", paddedTooLarge.diagnostic.code.value)
        assertEquals("2147483652", paddedTooLarge.diagnostic.facts["totalBufferBytes"])
    }

    @Test
    fun `produces an exact immutable MapRead CopyDestination staging descriptor`() {
        val planned = planned(
            request = readbackRequest(width = 65, height = 2, bufferOffsetBytes = 4),
            capabilities = capabilities(maxBufferSize = 4_096),
        )

        assertEquals(planned.layout.totalBufferBytes, planned.stagingDescriptor.minimumBufferBytes)
        assertEquals(0, planned.stagingDescriptor.mapOffsetBytes)
        assertEquals(
            setOf(GPUFrameResourceUsage.MapRead, GPUFrameResourceUsage.CopyDestination),
            planned.stagingDescriptor.usages,
        )
        assertFailsWith<UnsupportedOperationException> {
            @Suppress("UNCHECKED_CAST")
            (planned.stagingDescriptor.usages as MutableSet<GPUFrameResourceUsage>) +=
                GPUFrameResourceUsage.Storage
        }
    }

    @Test
    fun `depadding maps from zero and copies only unpadded row bytes from the preserved offset`() {
        val layout = planned(
            request = readbackRequest(width = 3, height = 2, bufferOffsetBytes = 4),
            capabilities = capabilities(),
        ).layout
        val mappedFromZero = ByteArray(layout.totalBufferBytes.toInt())
        val expected = ByteArray(24) { index -> (index + 1).toByte() }
        expected.copyInto(mappedFromZero, destinationOffset = 4, startIndex = 0, endIndex = 12)
        expected.copyInto(mappedFromZero, destinationOffset = 260, startIndex = 12, endIndex = 24)

        val depadded = assertIs<GPUReadbackDepadPlan.Depadded>(
            GPUReadbackLayoutDepadder.depad(mappedFromZero, layout),
        )
        mappedFromZero[4] = 99

        assertContentEquals(expected, depadded.copyBytes())
        val callerCopy = depadded.copyBytes()
        callerCopy[0] = 88
        assertContentEquals(expected, depadded.copyBytes())
    }

    @Test
    fun `depadding refuses a mapped range shorter than the exact Dawn minimum`() {
        val layout = planned(
            request = readbackRequest(width = 3, height = 2, bufferOffsetBytes = 4),
            capabilities = capabilities(),
        ).layout
        val refused = assertIs<GPUReadbackDepadPlan.Refused>(
            GPUReadbackLayoutDepadder.depad(
                mappedFromZero = ByteArray(layout.totalBufferBytes.toInt() - 1),
                layout = layout,
            ),
        )

        assertEquals("unsupported.readback.mapped_range_too_small", refused.diagnostic.code.value)
        assertEquals(layout.totalBufferBytes.toString(), refused.diagnostic.facts["requiredBytes"])
    }

    @Test
    fun `depadding refuses an internally malformed layout instead of throwing`() {
        val malformed = GPUReadbackLayout(
            width = 4,
            height = 2,
            bytesPerPixel = 4,
            copyBytesPerRowAlignment = 256,
            unpaddedBytesPerRow = 16,
            paddedBytesPerRow = 8,
            rowsPerImage = 1,
            bufferOffset = -4,
            totalBufferBytes = 12,
        )

        val refused = assertIs<GPUReadbackDepadPlan.Refused>(
            GPUReadbackLayoutDepadder.depad(ByteArray(12), malformed),
        )
        assertEquals("unsupported.readback.layout_invalid", refused.diagnostic.code.value)
    }

    @Test
    fun `output staging is not reused after GPU completion until depad and unmap release`() {
        val pool = GPUReadbackStagingPool()
        val descriptor = planned(readbackRequest(width = 4, height = 2), capabilities()).stagingDescriptor
        val generation = GPUDeviceGenerationID(7)
        val first = pool.reserve(
            stagingRequest("first", "frame-1", descriptor, generation),
        ).acceptedLease()
        assertIs<GPUBufferResourceRef>(first.resourceRef)
        assertEquals(GPUReadbackStagingState.Reserved, pool.stateOf(first))

        val submission = GPUResourceSubmissionID(1)
        assertIs<GPUReadbackStagingLifecycleResult.Accepted>(
            pool.markSubmitted(listOf(first), submission),
        )
        assertEquals(GPUReadbackStagingState.Submitted, pool.stateOf(first))
        assertIs<GPUReadbackStagingLifecycleResult.Accepted>(
            pool.acceptGPUCompletion(submission, generation),
        )
        assertEquals(GPUReadbackStagingState.GPUCompletedMappingPending, pool.stateOf(first))

        val beforeMapping = pool.reserve(
            stagingRequest("before-mapping", "frame-2", descriptor, generation),
        ).acceptedLease()
        assertNotEquals(first.resourceRef, beforeMapping.resourceRef)

        assertIs<GPUReadbackStagingLifecycleResult.Accepted>(pool.markMapped(first))
        assertEquals(GPUReadbackStagingState.Mapped, pool.stateOf(first))
        assertIs<GPUReadbackStagingLifecycleResult.Accepted>(pool.markDepadded(first))
        assertEquals(GPUReadbackStagingState.Depadded, pool.stateOf(first))

        val beforeUnmap = pool.reserve(
            stagingRequest("before-unmap", "frame-3", descriptor, generation),
        ).acceptedLease()
        assertNotEquals(first.resourceRef, beforeUnmap.resourceRef)

        assertIs<GPUReadbackStagingLifecycleResult.Accepted>(pool.releaseAfterUnmap(first))
        assertEquals(GPUReadbackStagingState.Releasable, pool.stateOf(first))
        val afterRelease = pool.reserve(
            stagingRequest("after-release", "frame-4", descriptor, generation),
        ).acceptedLease()
        assertEquals(first.resourceRef, afterRelease.resourceRef)
    }

    @Test
    fun `readback staging keeps pooled backing size separate from the logical Dawn minimum`() {
        val pool = GPUReadbackStagingPool()
        val planned = planned(readbackRequest(width = 65, height = 2), capabilities())
        val lease = pool.reserve(
            stagingRequest(
                id = "larger-backing",
                ownerScope = "frame-1",
                descriptor = planned.stagingDescriptor,
                generation = GPUDeviceGenerationID(7),
                backingBufferBytes = 1_024,
            ),
        ).acceptedLease()

        assertEquals(772, planned.layout.totalBufferBytes)
        assertEquals(772, lease.logicalMinimumBytes)
        assertEquals(1_024, lease.backingBufferBytes)
        assertNotEquals(lease.logicalMinimumBytes, lease.backingBufferBytes)
    }

    @Test
    fun `map failure releases only with a safe unmap proof otherwise quarantines`() {
        val descriptor = planned(readbackRequest(width = 4, height = 2), capabilities()).stagingDescriptor
        val generation = GPUDeviceGenerationID(7)

        val safePool = GPUReadbackStagingPool()
        val safe = safePool.reserve(stagingRequest("safe", "frame-safe", descriptor, generation)).acceptedLease()
        safePool.markSubmitted(listOf(safe), GPUResourceSubmissionID(1))
        safePool.acceptGPUCompletion(GPUResourceSubmissionID(1), generation)
        assertIs<GPUReadbackStagingLifecycleResult.Accepted>(
            safePool.markMapFailed(safe, GPUReadbackMapFailureSafety.SafeUnmapAndReleaseProven),
        )
        assertEquals(GPUReadbackStagingState.Releasable, safePool.stateOf(safe))

        val uncertainPool = GPUReadbackStagingPool()
        val uncertain = uncertainPool.reserve(
            stagingRequest("uncertain", "frame-uncertain", descriptor, generation),
        ).acceptedLease()
        uncertainPool.markSubmitted(listOf(uncertain), GPUResourceSubmissionID(2))
        uncertainPool.acceptGPUCompletion(GPUResourceSubmissionID(2), generation)
        assertIs<GPUReadbackStagingLifecycleResult.Accepted>(
            uncertainPool.markMapFailed(uncertain, GPUReadbackMapFailureSafety.ReleaseUncertain),
        )
        assertEquals(GPUReadbackStagingState.Quarantined, uncertainPool.stateOf(uncertain))

        val deviceLostPool = GPUReadbackStagingPool()
        val deviceLost = deviceLostPool.reserve(
            stagingRequest("device-lost", "frame-lost", descriptor, generation),
        ).acceptedLease()
        deviceLostPool.markSubmitted(listOf(deviceLost), GPUResourceSubmissionID(3))
        deviceLostPool.acceptGPUCompletion(GPUResourceSubmissionID(3), generation)
        assertIs<GPUReadbackStagingLifecycleResult.Accepted>(
            deviceLostPool.markMapFailed(deviceLost, GPUReadbackMapFailureSafety.DeviceLost),
        )
        assertEquals(GPUReadbackStagingState.Quarantined, deviceLostPool.stateOf(deviceLost))
    }

    @Test
    fun `unknown stale and duplicate completion cannot advance output ownership`() {
        val pool = GPUReadbackStagingPool()
        val descriptor = planned(readbackRequest(width = 4, height = 2), capabilities()).stagingDescriptor
        val generation = GPUDeviceGenerationID(7)
        val lease = pool.reserve(stagingRequest("lease", "frame-1", descriptor, generation)).acceptedLease()
        val submission = GPUResourceSubmissionID(1)
        pool.markSubmitted(listOf(lease), submission)

        assertIs<GPUReadbackStagingLifecycleResult.Refused>(
            pool.acceptGPUCompletion(GPUResourceSubmissionID(99), generation),
        )
        assertIs<GPUReadbackStagingLifecycleResult.Refused>(
            pool.acceptGPUCompletion(submission, GPUDeviceGenerationID(8)),
        )
        assertIs<GPUReadbackStagingLifecycleResult.Refused>(pool.markMapped(lease))
        assertEquals(GPUReadbackStagingState.Submitted, pool.stateOf(lease))

        assertIs<GPUReadbackStagingLifecycleResult.Accepted>(
            pool.acceptGPUCompletion(submission, generation),
        )
        assertIs<GPUReadbackStagingLifecycleResult.Refused>(
            pool.acceptGPUCompletion(submission, generation),
        )
        assertEquals(GPUReadbackStagingState.GPUCompletedMappingPending, pool.stateOf(lease))
    }

    @Test
    fun `readback staging rejects malformed public descriptor facts before accounting`() {
        val malformed = object : GPUReadbackStagingDescriptorContract {
            override val minimumBufferBytes = -100L
            override val maxBufferSize = -1L
            override val mapOffsetBytes = 0L
            override val usages = setOf(
                GPUFrameResourceUsage.MapRead,
                GPUFrameResourceUsage.CopyDestination,
            )
        }
        val pool = GPUReadbackStagingPool()

        val refused = assertIs<GPUReadbackStagingReservationResult.Refused>(
            pool.reserve(
                GPUReadbackStagingReservationRequest(
                    reservationId = "malformed",
                    ownerScope = "frame-1",
                    deviceGeneration = GPUDeviceGenerationID(7),
                    descriptor = malformed,
                    backingBufferBytes = -50,
                    budgetPlan = readbackBudgetPlan(),
                ),
            ),
        )

        assertEquals("unsupported.readback_staging.minimum_buffer_invalid", refused.diagnostic.code.value)
        assertEquals(0, pool.telemetry().residentBytes)
    }

    @Test
    fun `readback staging snapshots descriptor getters exactly once before validation`() {
        var minimumReads = 0
        var maxReads = 0
        val changing = object : GPUReadbackStagingDescriptorContract {
            override val minimumBufferBytes: Long
                get() = if (minimumReads++ == 0) 260 else 1
            override val maxBufferSize: Long
                get() = if (maxReads++ == 0) 4_096 else 1
            override val mapOffsetBytes = 0L
            override val usages = setOf(
                GPUFrameResourceUsage.MapRead,
                GPUFrameResourceUsage.CopyDestination,
            )
        }

        val lease = GPUReadbackStagingPool().reserve(
            GPUReadbackStagingReservationRequest(
                reservationId = "changing",
                ownerScope = "frame-1",
                deviceGeneration = GPUDeviceGenerationID(7),
                descriptor = changing,
                backingBufferBytes = 260,
                budgetPlan = readbackBudgetPlan(),
            ),
        ).acceptedLease()

        assertEquals(260, lease.logicalMinimumBytes)
        assertEquals(1, minimumReads)
        assertEquals(1, maxReads)
    }

    @Test
    fun `readback preserves an existing frame budget refusal cause`() {
        val descriptor = planned(readbackRequest(width = 4, height = 2), capabilities()).stagingDescriptor
        val frameDiagnostic = GPUDiagnostic(
            code = GPUDiagnosticCode("unsupported.frame_memory.accounting_overflow"),
            domain = GPUDiagnosticDomain.Resources,
            severity = GPUDiagnosticSeverity.Error,
            message = "frame accounting overflow",
            facts = emptyMap(),
        )
        val refused = assertIs<GPUReadbackStagingReservationResult.Refused>(
            GPUReadbackStagingPool().reserve(
                stagingRequest("budget-refused", "frame-1", descriptor, GPUDeviceGenerationID(7)).copy(
                    budgetPlan = readbackBudgetPlan(diagnostic = frameDiagnostic),
                ),
            ),
        )

        assertEquals("unsupported.readback_staging.frame_budget_invalid", refused.diagnostic.code.value)
        assertEquals(frameDiagnostic.code.value, refused.diagnostic.facts["frameBudgetCode"])
    }

    @Test
    fun `use token exhaustion terminalizes readback staging instead of reusing stale LRU`() {
        val ledger = GPUPhysicalPoolBudgetLedger(initialUseToken = Long.MAX_VALUE - 1)
        val pool = GPUReadbackStagingPool(ledger)
        val descriptor = planned(readbackRequest(width = 4, height = 2), capabilities()).stagingDescriptor
        val lease = pool.reserve(
            stagingRequest("token", "frame-token", descriptor, GPUDeviceGenerationID(7)),
        ).acceptedLease()

        pool.rollbackBeforeSubmit("frame-token")

        assertEquals(GPUReadbackStagingState.Invalidated, pool.stateOf(lease))
        assertEquals(0, pool.telemetry().residentBytes)
        assertEquals(0, pool.trackedEntryCount)
    }

    @Test
    fun `readback rollback is LIFO and makes never submitted staging reclaimable`() {
        val pool = GPUReadbackStagingPool()
        val descriptor = planned(readbackRequest(width = 4, height = 2), capabilities()).stagingDescriptor
        val generation = GPUDeviceGenerationID(7)
        val first = pool.reserve(stagingRequest("first", "preflight", descriptor, generation)).acceptedLease()
        val second = pool.reserve(stagingRequest("second", "preflight", descriptor, generation)).acceptedLease()

        val rollback = pool.rollbackBeforeSubmit("preflight")

        assertEquals(listOf("second", "first"), rollback.releasedReservationIds)
        assertEquals(GPUReadbackStagingState.Invalidated, pool.stateOf(first))
        assertEquals(GPUReadbackStagingState.Invalidated, pool.stateOf(second))
        assertEquals(first.backingBufferBytes + second.backingBufferBytes, pool.telemetry().reclaimableBytes)
    }

    @Test
    fun `failed uncertain and device lost GPU completion quarantine output staging with evidence`() {
        GPUReadbackCompletionFailure.entries.forEachIndexed { index, failure ->
            val pool = GPUReadbackStagingPool()
            val descriptor = planned(readbackRequest(width = 4, height = 2), capabilities()).stagingDescriptor
            val generation = GPUDeviceGenerationID(7)
            val lease = pool.reserve(
                stagingRequest("failure-$failure", "frame-$failure", descriptor, generation),
            ).acceptedLease()
            val submission = GPUResourceSubmissionID((index + 1).toLong())
            pool.markSubmitted(listOf(lease), submission)

            assertIs<GPUReadbackStagingLifecycleResult.Accepted>(
                pool.rejectGPUCompletion(submission, generation, failure),
            )
            assertEquals(GPUReadbackStagingState.Quarantined, pool.stateOf(lease))
            assertEquals(failure, pool.snapshotOf(lease).completionFailure)
            assertTrue(pool.evictReclaimableUntil(0).isEmpty())
        }
    }

    @Test
    fun `mixed generation or owner submission is refused atomically`() {
        val pool = GPUReadbackStagingPool()
        val descriptor = planned(readbackRequest(width = 4, height = 2), capabilities()).stagingDescriptor
        val generation7 = pool.reserve(
            stagingRequest("generation-7", "frame-1", descriptor, GPUDeviceGenerationID(7)),
        ).acceptedLease()
        val generation8 = pool.reserve(
            stagingRequest("generation-8", "frame-1", descriptor, GPUDeviceGenerationID(8)),
        ).acceptedLease()
        val otherOwner = pool.reserve(
            stagingRequest("other-owner", "frame-2", descriptor, GPUDeviceGenerationID(7)),
        ).acceptedLease()

        assertIs<GPUReadbackStagingLifecycleResult.Refused>(
            pool.markSubmitted(listOf(generation7, generation8), GPUResourceSubmissionID(1)),
        )
        assertIs<GPUReadbackStagingLifecycleResult.Refused>(
            pool.markSubmitted(listOf(generation7, otherOwner), GPUResourceSubmissionID(2)),
        )
        assertEquals(GPUReadbackStagingState.Reserved, pool.stateOf(generation7))
        assertEquals(GPUReadbackStagingState.Reserved, pool.stateOf(generation8))
        assertEquals(GPUReadbackStagingState.Reserved, pool.stateOf(otherOwner))
    }

    @Test
    fun `late readback callback cannot affect a staging resource reused by a newer submission`() {
        val pool = GPUReadbackStagingPool()
        val descriptor = planned(readbackRequest(width = 4, height = 2), capabilities()).stagingDescriptor
        val generation = GPUDeviceGenerationID(7)
        val first = pool.reserve(stagingRequest("first", "frame-1", descriptor, generation)).acceptedLease()
        pool.markSubmitted(listOf(first), GPUResourceSubmissionID(1))
        pool.acceptGPUCompletion(GPUResourceSubmissionID(1), generation)
        pool.markMapped(first)
        pool.markDepadded(first)
        pool.releaseAfterUnmap(first)
        val reused = pool.reserve(stagingRequest("reused", "frame-2", descriptor, generation)).acceptedLease()
        assertEquals(first.resourceRef, reused.resourceRef)
        assertEquals(GPUReadbackStagingState.Invalidated, pool.stateOf(first))
        pool.markSubmitted(listOf(reused), GPUResourceSubmissionID(2))

        assertIs<GPUReadbackStagingLifecycleResult.Refused>(
            pool.rejectGPUCompletion(
                GPUResourceSubmissionID(1),
                generation,
                GPUReadbackCompletionFailure.Uncertain,
            ),
        )
        assertEquals(GPUReadbackStagingState.Submitted, pool.stateOf(reused))
    }

    @Test
    fun `readback staging evicts only releasable LRU and invalidates old device generations`() {
        val pool = GPUReadbackStagingPool()
        val descriptor = planned(readbackRequest(width = 4, height = 2), capabilities()).stagingDescriptor
        val generation7 = GPUDeviceGenerationID(7)
        val smaller = pool.reserve(
            stagingRequest("smaller", "frame-1", descriptor, generation7, backingBufferBytes = 512),
        ).acceptedLease()
        pool.markSubmitted(listOf(smaller), GPUResourceSubmissionID(1))
        pool.acceptGPUCompletion(GPUResourceSubmissionID(1), generation7)
        pool.markMapped(smaller)
        pool.markDepadded(smaller)
        pool.releaseAfterUnmap(smaller)
        val larger = pool.reserve(
            stagingRequest("larger", "frame-2", descriptor, generation7, backingBufferBytes = 1_024),
        ).acceptedLease()
        pool.markSubmitted(listOf(larger), GPUResourceSubmissionID(2))
        pool.acceptGPUCompletion(GPUResourceSubmissionID(2), generation7)
        pool.markMapped(larger)
        pool.markDepadded(larger)
        pool.releaseAfterUnmap(larger)

        val touchedSmaller = pool.reserve(
            stagingRequest("touch-smaller", "frame-3", descriptor, generation7, backingBufferBytes = 512),
        ).acceptedLease()
        pool.rollbackBeforeSubmit("frame-3")
        assertEquals(smaller.resourceRef, touchedSmaller.resourceRef)
        val evicted = pool.evictReclaimableUntil(512)
        assertEquals(listOf(larger.resourceRef), evicted.map { it.resourceRef })
        assertEquals(512, pool.telemetry().residentBytes)

        val generation8Lease = pool.reserve(
            stagingRequest("generation-8", "frame-4", descriptor, GPUDeviceGenerationID(8), 512),
        ).acceptedLease()
        val invalidated = pool.invalidateGenerationsBefore(GPUDeviceGenerationID(8))
        assertEquals(listOf(smaller.resourceRef), invalidated.map { it.resourceRef })
        assertEquals(GPUReadbackStagingState.Invalidated, pool.stateOf(touchedSmaller))
        assertEquals(GPUReadbackStagingState.Reserved, pool.stateOf(generation8Lease))
        assertEquals(512, pool.telemetry().residentBytes)
        assertEquals(1, pool.trackedEntryCount)
    }
}

private fun planned(
    request: GPUFrameReadbackRequest,
    capabilities: GPUCapabilities,
): GPUReadbackLayoutPlan.Planned =
    assertIs<GPUReadbackLayoutPlan.Planned>(GPUReadbackLayoutPlanner().plan(request, capabilities))

private fun readbackRequest(
    width: Int = 1,
    height: Int = 1,
    bufferOffsetBytes: Long = 0,
    bounds: GPUPixelBounds = GPUPixelBounds(0, 0, width, height),
): GPUFrameReadbackRequest = GPUFrameReadbackRequest(
    requestId = GPUReadbackRequestID("readback-$width-$height-$bufferOffsetBytes"),
    sourceBounds = bounds,
    pixelFormat = GPUReadbackPixelFormat.Rgba8Unorm,
    outputColorInterpretation = GPUColorInterpretation("srgb-premul"),
    bufferOffsetBytes = bufferOffsetBytes,
)

private fun capabilities(
    alignment: Long = 256,
    maxBufferSize: Long? = 1L shl 30,
    limits: GPULimits? = limits(alignment = alignment, maxBufferSize = maxBufferSize),
    rendererFeatures: Set<GPURendererFeature> = setOf(GPURendererFeature.Readback),
): GPUCapabilities = GPUCapabilities(
    implementation = GPUImplementationIdentity(
        facadeName = "GPU",
        implementationName = "unit",
        adapterName = "unit-adapter",
        deviceName = "unit-device",
    ),
    facts = limits?.capabilityFacts("readback-test").orEmpty(),
    snapshotId = "readback-test-$alignment-${maxBufferSize ?: "unknown"}",
    limits = limits,
    rendererFeatures = rendererFeatures,
)

private fun limits(
    alignment: Long = 256,
    maxBufferSize: Long? = 1L shl 30,
): GPULimits = GPULimits(
    maxTextureDimension2D = Int.MAX_VALUE.toLong(),
    copyBytesPerRowAlignment = alignment,
    minUniformBufferOffsetAlignment = 256,
    source = "device.limits",
    maxBufferSize = maxBufferSize,
)

private fun stagingRequest(
    id: String,
    ownerScope: String,
    descriptor: GPUReadbackStagingDescriptor,
    generation: GPUDeviceGenerationID,
    backingBufferBytes: Long = descriptor.minimumBufferBytes,
): GPUReadbackStagingReservationRequest = GPUReadbackStagingReservationRequest(
    reservationId = id,
    ownerScope = ownerScope,
    deviceGeneration = generation,
    descriptor = descriptor,
    backingBufferBytes = backingBufferBytes,
    budgetPlan = readbackBudgetPlan(),
)

private fun readbackBudgetPlan(
    configuredBytes: Long = 1L shl 30,
    diagnostic: GPUDiagnostic? = null,
): GPUFrameMemoryBudgetPlan =
    GPUFrameMemoryBudgetPlan(
        peakFrameTransientBytes = 0,
        targetResidentBytes = 0,
        categoryTotals = GPUFrameMemoryCategory.entries.associateWith { 0L },
        deviceLimitFacts = emptyList(),
        configuredAggregateBudgetBytes = configuredBytes,
        diagnostic = diagnostic,
    )

private fun GPUReadbackStagingReservationResult.acceptedLease() =
    assertIs<GPUReadbackStagingReservationResult.Accepted>(this).lease
