package org.graphiks.math.codegen

internal enum class Semantic { POINT, VECTOR }

internal enum class ScalarId { F32, F64, I32 }

internal enum class ArithmeticPolicy { IEEE_754, SATURATING }

internal enum class Capability { DIVIDE, NORMALIZE, FINITE_CHECK }

internal enum class ImmutableRepresentation { MULTI_FIELD_VALUE, FINAL_CLASS }

internal data class ScalarSpec(
    val id: ScalarId,
    val kotlinType: String,
    val arithmetic: ArithmeticPolicy,
    val accumulatorType: String? = null,
)

internal data class PrimitiveSpec(
    val semantic: Semantic,
    val dimension: Int,
    val scalar: ScalarId,
    val capabilities: Set<Capability>,
    val targetRepresentation: ImmutableRepresentation = ImmutableRepresentation.MULTI_FIELD_VALUE,
    val fallbackRepresentation: ImmutableRepresentation? = ImmutableRepresentation.FINAL_CLASS,
    val generateImmutable: Boolean = true,
    val generateMutable: Boolean = false,
)

internal data class PrimitiveSchema(
    val scalars: List<ScalarSpec>,
    val primitives: List<PrimitiveSpec>,
)
