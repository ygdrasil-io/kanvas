package org.graphiks.kanvas.gpu.renderer.intermediates

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class GPUIntermediatePlanContractsTest {
    @Test
    fun `plan dump exposes ordered destination copy render and composite steps`() {
        val descriptor = GPUIntermediateTextureDescriptor(
            label = "intermediate:dst-copy:cmd-7",
            purpose = GPUIntermediatePurpose.DestinationCopy,
            descriptorHash = "sha256:dst-copy",
            sourceTargetLabel = "surface:main",
            boundsLabel = "bounds:cmd-7",
            width = 64,
            height = 32,
            formatClass = "rgba8unorm",
            usageLabels = listOf("copy_dst", "texture_binding"),
            sampleCount = 1,
            generation = 3,
            lifetimeClass = "pass-local",
            ownerScope = "target:main",
            byteEstimate = 8192,
        )
        val plan = GPUIntermediatePlan(
            planId = "intermediate-plan:screen-blend",
            targetId = "target:main",
            steps = listOf(
                GPUIntermediatePlanStep.CreateIntermediate(descriptor),
                GPUIntermediatePlanStep.CopyDestination(
                    sourceLabel = "surface:main",
                    destination = descriptor,
                    boundsLabel = "bounds:cmd-7",
                    tokenLabel = "dst-token:cmd-7:3",
                    passSplitRequired = true,
                    copyBeforeSample = true,
                ),
                GPUIntermediatePlanStep.BindIntermediate(
                    descriptor = descriptor,
                    bindingLabel = "dst-read:cmd-7",
                    layoutHash = "layout:dst-read",
                ),
                GPUIntermediatePlanStep.RenderToTarget(
                    commandId = "cmd-7",
                    targetLabel = "surface:main",
                    routeLabel = "shader-blend:Screen",
                    orderingToken = "order:cmd-7",
                ),
            ),
            telemetry = GPUIntermediateTelemetry(
                destinationReadCopies = 1,
                copiedBytes = 8192,
                passSplits = 1,
                intermediatesCreated = 1,
                liveIntermediateBytes = 8192,
            ),
        )

        assertEquals(
            listOf(
                "intermediate.plan id=intermediate-plan:screen-blend target=target:main steps=4 diagnostics=none",
                "intermediate.create label=intermediate:dst-copy:cmd-7 purpose=DestinationCopy descriptor=sha256:dst-copy source=surface:main bounds=bounds:cmd-7 size=64x32 format=rgba8unorm sampleCount=1 generation=3 usage=copy_dst,texture_binding lifetime=pass-local owner=target:main bytes=8192",
                "intermediate.copy source=surface:main destination=intermediate:dst-copy:cmd-7 bounds=bounds:cmd-7 token=dst-token:cmd-7:3 split=true copyBeforeSample=true",
                "intermediate.bind label=intermediate:dst-copy:cmd-7 binding=dst-read:cmd-7 layout=layout:dst-read",
                "intermediate.render command=cmd-7 target=surface:main route=shader-blend:Screen ordering=order:cmd-7",
                "intermediate.telemetry destinationReadCopies=1 destinationReadIntermediateBinds=0 copiedBytes=8192 passSplits=1 intermediatesCreated=1 intermediatesReused=0 intermediatesRefused=0 liveIntermediateBytes=8192 layerTargets=0 layerComposites=0 msaaTargets=0 msaaResolves=0",
            ),
            plan.dumpLines(),
        )
    }

    @Test
    fun `accepted plans cannot mix accepted steps with terminal refusal`() {
        assertFailsWith<IllegalArgumentException> {
            GPUIntermediatePlan(
                planId = "intermediate-plan:invalid",
                targetId = "target:main",
                steps = listOf(
                    GPUIntermediatePlanStep.RenderToTarget(
                        commandId = "cmd-1",
                        targetLabel = "surface:main",
                        routeLabel = "fixed-function:srcOver",
                        orderingToken = "order:cmd-1",
                    ),
                    GPUIntermediatePlanStep.Refuse(
                        scopeLabel = "cmd-2",
                        reasonCode = "unsupported.destination_read.active_attachment_sampled",
                    ),
                ),
            )
        }
    }
}
