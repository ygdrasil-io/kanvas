package org.graphiks.kanvas.gpu.renderer.passes

import java.util.Collections
import org.graphiks.kanvas.gpu.plan.AttachmentLoadPlan
import org.graphiks.kanvas.gpu.plan.AttachmentStorePlan
import org.graphiks.kanvas.gpu.plan.PlanPass
import org.graphiks.kanvas.gpu.plan.SamplePlan

/** Immutable W4d.2-specific continuation facts for one validated four-sample frame. */
public class GPUW4dPathSampleContinuationAuthority private constructor(
    public val version: String,
    transitions: List<GPUW4dPathSampleContinuationTransition>,
) {
    public val transitions: List<GPUW4dPathSampleContinuationTransition> =
        Collections.unmodifiableList(transitions.toList())

    internal fun revalidates(pathPasses: List<PlanPass.PathRenderPass>): Boolean =
        version == VERSION && transitions == transitionsFromValidated(pathPasses)

    internal companion object {
        const val VERSION: String = "w4d.2-path-sample-continuation-v1"

        fun issueFromValidated(pathPasses: List<PlanPass.PathRenderPass>): GPUW4dPathSampleContinuationAuthority {
            val transitions = requireNotNull(transitionsFromValidated(pathPasses)) {
                "W4d.2 continuation requires exact four-sample load/store/resolve facts"
            }
            return GPUW4dPathSampleContinuationAuthority(VERSION, transitions)
        }

        private fun transitionsFromValidated(
            pathPasses: List<PlanPass.PathRenderPass>,
        ): List<GPUW4dPathSampleContinuationTransition>? {
            val multisample = pathPasses.filter { pass -> pass.draw.sample == SamplePlan.Multisample4 }
            if (multisample.isEmpty()) return null
            if (multisample.any { it.store != AttachmentStorePlan.Store }) return null
            if (multisample.first().load != AttachmentLoadPlan.ClearTransparent ||
                multisample.drop(1).any { it.load != AttachmentLoadPlan.Load }
            ) return null
            if (multisample.dropLast(1).any { it.resolveTarget != null } ||
                multisample.last().resolveTarget == null
            ) return null
            return multisample.mapIndexed { index, pass ->
                GPUW4dPathSampleContinuationTransition(
                    pathPassId = pass.id.value,
                    commandIdValue = pass.draw.commandIndex,
                    loadTransition = if (index == 0) {
                        GPUSampleLoadTransition.FreshClear
                    } else {
                        GPUSampleLoadTransition.RetainedLoad
                    },
                    storeAction = GPUSampleStoreAction.Store,
                    resolveAction = if (index == multisample.lastIndex) {
                        GPUSampleResolveAction.ResolveCanonical
                    } else {
                        GPUSampleResolveAction.Skip
                    },
                )
            }
        }
    }
}

/** One sealed segment in the W4d.2 continuation sequence. */
public data class GPUW4dPathSampleContinuationTransition(
    public val pathPassId: String,
    public val commandIdValue: Int,
    public val loadTransition: GPUSampleLoadTransition,
    public val storeAction: GPUSampleStoreAction,
    public val resolveAction: GPUSampleResolveAction,
)
