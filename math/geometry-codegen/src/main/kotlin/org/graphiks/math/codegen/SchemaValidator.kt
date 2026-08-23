package org.graphiks.math.codegen

internal class SchemaValidationException(message: String) : IllegalArgumentException(message)

internal object SchemaValidator {
    fun validate(schema: PrimitiveSchema) {
        validateScalars(schema.scalars)

        val primitives = schema.primitives.sortedWith(
            compareBy<PrimitiveSpec>({ it.semantic }, { it.dimension }, { it.scalar }),
        )
        primitives.forEach { primitive ->
            componentNames(primitive.dimension)
            validatePrimitive(primitive, schema.scalars)
        }
        validatePointVectorPairs(primitives)
        validateGeneratedOutputPaths(primitives)
        validateGeneratedNames(primitives)
    }

    private fun validateScalars(scalars: List<ScalarSpec>) {
        scalars.groupBy { it.id }
            .filterValues { it.size > 1 }
            .keys
            .sorted()
            .firstOrNull()
            ?.let { scalar ->
                throw SchemaValidationException(
                    "scalar $scalar is declared more than once; keep one ScalarSpec per scalar",
                )
            }
    }

    private fun validatePrimitive(primitive: PrimitiveSpec, scalars: List<ScalarSpec>) {
        val name = typeName(primitive)
        if (scalars.none { it.id == primitive.scalar }) {
            throw SchemaValidationException(
                "$name uses undefined scalar ${primitive.scalar}; add a ScalarSpec for ${primitive.scalar}",
            )
        }
        if (primitive.scalar == ScalarId.I32 && Capability.NORMALIZE in primitive.capabilities) {
            throw SchemaValidationException(
                "$name cannot use NORMALIZE with scalar I32; remove NORMALIZE",
            )
        }
        if (primitive.generateMutable && !primitive.generateImmutable) {
            throw SchemaValidationException(
                "$name cannot generate mutable without immutable; enable generateImmutable",
            )
        }
        if (!isAvailable(primitive.targetRepresentation) && primitive.fallbackRepresentation == null) {
            throw SchemaValidationException(
                "$name requires fallback representation when ${primitive.targetRepresentation} is unavailable",
            )
        }
    }

    private fun validatePointVectorPairs(primitives: List<PrimitiveSpec>) {
        val primitiveKeys = primitives.mapTo(mutableSetOf()) { it.key() }
        primitives.filter { it.semantic == Semantic.POINT }
            .firstOrNull { point -> point.vectorKey() !in primitiveKeys }
            ?.let { point ->
                throw SchemaValidationException(
                    "${typeName(point)} requires ${typeName(point.copy(semantic = Semantic.VECTOR))}; " +
                        "add the matching VECTOR primitive",
                )
            }
    }

    private fun validateGeneratedNames(primitives: List<PrimitiveSpec>) {
        primitives.groupBy(::typeName)
            .filterValues { it.size > 1 }
            .keys
            .sorted()
            .firstOrNull()
            ?.let { name ->
                throw SchemaValidationException("generated type name $name is declared more than once")
            }
    }

    private fun validateGeneratedOutputPaths(primitives: List<PrimitiveSpec>) {
        primitives.flatMap { primitive -> generatedOutputPaths(primitive) }
            .groupingBy { it }
            .eachCount()
            .filterValues { it > 1 }
            .keys
            .sorted()
            .firstOrNull()
            ?.let { path ->
                throw SchemaValidationException("generated output path $path is declared more than once")
            }
    }

    private fun isAvailable(representation: ImmutableRepresentation): Boolean =
        representation == ImmutableRepresentation.MULTI_FIELD_VALUE
}

internal fun componentNames(dimension: Int): List<String> = when (dimension) {
    2 -> listOf("x", "y")
    3 -> listOf("x", "y", "z")
    4 -> listOf("x", "y", "z", "w")
    else -> throw SchemaValidationException(
        "dimension $dimension has no component names; use a dimension in 2..4",
    )
}

private fun PrimitiveSpec.key(): Triple<Semantic, Int, ScalarId> = Triple(semantic, dimension, scalar)

private fun PrimitiveSpec.vectorKey(): Triple<Semantic, Int, ScalarId> =
    Triple(Semantic.VECTOR, dimension, scalar)

private fun typeName(primitive: PrimitiveSpec): String =
    primitive.semantic.name.lowercase().replaceFirstChar(Char::titlecase) +
        primitive.dimension +
        primitive.scalar

private fun generatedOutputPaths(primitive: PrimitiveSpec): List<String> = buildList {
    val directory = primitive.semantic.name.lowercase()
    val name = typeName(primitive)
    if (primitive.generateImmutable) add("$directory/$name.kt")
    if (primitive.generateMutable) add("$directory/Mutable$name.kt")
}
