package org.graphiks.math.codegen

internal data class SemanticPrimitiveModel(
    val spec: PrimitiveSpec,
    val scalar: ScalarSpec,
    val components: List<String>,
    val typeName: String,
    val representation: ImmutableRepresentation,
) {
    val mutableTypeName: String get() = "Mutable$typeName"
    val packageName: String get() = "org.graphiks.math.${spec.semantic.name.lowercase()}"
    val generatedRoot: String
        get() = when (spec.semantic) {
            Semantic.POINT -> "math/geometry/src/generated/kotlin"
            Semantic.VECTOR -> "math/vector/src/generated/kotlin"
        }
}

internal object SemanticModel {
    fun expand(schema: PrimitiveSchema): List<SemanticPrimitiveModel> {
        SchemaValidator.validate(schema)
        val scalarsById = schema.scalars.associateBy { it.id }
        return schema.primitives
            .map { primitive ->
                val representation = when (primitive.targetRepresentation) {
                    ImmutableRepresentation.FINAL_CLASS -> ImmutableRepresentation.FINAL_CLASS
                    ImmutableRepresentation.MULTI_FIELD_VALUE ->
                        requireNotNull(primitive.fallbackRepresentation)
                }
                SemanticPrimitiveModel(
                    spec = primitive,
                    scalar = scalarsById.getValue(primitive.scalar),
                    components = componentNames(primitive.dimension),
                    typeName = primitive.semantic.name.lowercase().replaceFirstChar(Char::titlecase) +
                        primitive.dimension + primitive.scalar,
                    representation = representation,
                )
            }
            .sortedWith(compareBy({ it.packageName }, { it.typeName }))
    }
}
