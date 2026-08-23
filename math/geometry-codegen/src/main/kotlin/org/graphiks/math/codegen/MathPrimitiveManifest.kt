package org.graphiks.math.codegen

internal object MathPrimitiveManifest {
    val schema = PrimitiveSchema(
        scalars = listOf(
            ScalarSpec(ScalarId.F32, "Float", ArithmeticPolicy.IEEE_754),
            ScalarSpec(ScalarId.F64, "Double", ArithmeticPolicy.IEEE_754),
            ScalarSpec(ScalarId.I32, "Int", ArithmeticPolicy.SATURATING, accumulatorType = "Long"),
        ),
        primitives = listOf(
            vector(2, ScalarId.F32, generateMutable = true),
            vector(3, ScalarId.F32, generateMutable = true),
            vector(4, ScalarId.F32),
            vector(2, ScalarId.F64, generateMutable = true),
            vector(2, ScalarId.I32),
            point(2, ScalarId.F32, generateMutable = true),
            point(2, ScalarId.F64, generateMutable = true),
            point(2, ScalarId.I32),
            point(3, ScalarId.F32),
        ),
    )

    private fun vector(
        dimension: Int,
        scalar: ScalarId,
        generateMutable: Boolean = false,
    ): PrimitiveSpec = PrimitiveSpec(
        semantic = Semantic.VECTOR,
        dimension = dimension,
        scalar = scalar,
        capabilities = when (scalar) {
            ScalarId.F32,
            ScalarId.F64,
            -> setOf(Capability.DIVIDE, Capability.NORMALIZE, Capability.FINITE_CHECK)

            ScalarId.I32 -> emptySet()
        },
        generateMutable = generateMutable,
    )

    private fun point(
        dimension: Int,
        scalar: ScalarId,
        generateMutable: Boolean = false,
    ): PrimitiveSpec = PrimitiveSpec(
        semantic = Semantic.POINT,
        dimension = dimension,
        scalar = scalar,
        capabilities = if (scalar == ScalarId.I32) emptySet() else setOf(Capability.FINITE_CHECK),
        generateMutable = generateMutable,
    )
}
