package org.graphiks.kanvas.gpu.plan

public class ColorNumericAuthorityV1 private constructor(
    public val canonicalIdentity: String,
    public val outputSourceProof: ColorSourceProofV1,
    private val execution: ColorFilterExecutionPlanV1,
    private val source: ColorSourceProofV1,
) {
    public fun authenticates(execution: ColorFilterExecutionPlanV1, source: ColorSourceProofV1): Boolean =
        this.execution === execution && this.source === source
    internal companion object {
        fun seal(execution: ColorFilterExecutionPlanV1, source: ColorSourceProofV1): ColorNumericAuthorityV1? {
            val output = ColorSourceProofV1.compose(source,execution) ?: return null
            return ColorNumericAuthorityV1("color-numeric-v1:${source.canonicalIdentity}:${execution.canonicalIdentity}:${output.canonicalIdentity}",
                output,execution,source)
        }
    }
}

public class ColorFilteredProgramV4(public val child: MaterialProgramPlan,
    public val filterStructureIdentity: String) : MaterialProgramPlan {
    override val versionI32: Int = 4
    override val structuralId: MaterialProgramPlanId = MaterialProgramPlanId("color-filter-v4:${child.structuralId.value}:$filterStructureIdentity")
    override fun copyNumericOperationGraphV1(): NumericOperationGraphV1 = NumericOperationGraphV1.colorSourceV4()
}

public class ColorFilterBindingV4 private constructor(public val execution: ColorFilterExecutionPlanV1,
    public val sourceProof: ColorSourceProofV1, public val numericAuthority: ColorNumericAuthorityV1) : MaterialBindingPlan {
    override val versionI32: Int = 4
    public val canonicalIdentity: String = "color-binding-v4:${execution.canonicalIdentity}:${numericAuthority.canonicalIdentity}"
    internal companion object {
        fun seal(execution: ColorFilterExecutionPlanV1, source: ColorSourceProofV1,
            numeric: ColorNumericAuthorityV1): ColorFilterBindingV4 {
            require(numeric.authenticates(execution,source)) { W5fPlanDiagnostics.Schema }
            return ColorFilterBindingV4(execution,source,numeric)
        }
    }
}
