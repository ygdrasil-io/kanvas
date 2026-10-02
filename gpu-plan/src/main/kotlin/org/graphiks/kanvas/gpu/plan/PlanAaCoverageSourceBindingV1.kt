package org.graphiks.kanvas.gpu.plan

import org.graphiks.math.geometry.Point2I32
import org.graphiks.math.geometry.SizeI32

/** Closed W6 input sum: historical W4d coverage or W7 inverse-domain coverage only. */
public sealed interface PlanAaCoverageSourceBindingV1 {
    public val ownerPassId: PlanPassId
    public val commandIndexI32: Int
    public val sourceCapabilityId: String
    public val recipe: NativeSiteRecipeV1
    public fun sourcePassIds(): List<PlanPassId>
    public fun passes(): List<PlanPass.PathRenderPass>
    public fun resourceRemapping(): Map<PlanResourceId, PlanResourceId>
    public fun resources(): List<PlanResource>
    public fun copyExtentI32(): SizeI32
    public fun copyOriginDeviceI32(): Point2I32
}
