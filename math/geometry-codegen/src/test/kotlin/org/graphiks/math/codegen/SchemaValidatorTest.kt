package org.graphiks.math.codegen

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class SchemaValidatorTest {
    @Test
    fun `point requires matching vector`() {
        val schema = schemaOf(primitive(Semantic.POINT, 2, ScalarId.F32))

        val error = assertFailsWith<SchemaValidationException> {
            SchemaValidator.validate(schema)
        }

        assertEquals(
            "Point2F32 requires Vector2F32; add the matching VECTOR primitive",
            error.message,
        )
    }

    @Test
    fun `generated point requires matching immutable vector output`() {
        val schema = schemaOf(
            primitive(Semantic.POINT, 2, ScalarId.F32),
            primitive(
                Semantic.VECTOR,
                2,
                ScalarId.F32,
                generateImmutable = false,
            ),
        )

        val error = assertFailsWith<SchemaValidationException> {
            SchemaValidator.validate(schema)
        }

        assertEquals(
            "Point2F32 requires generated Vector2F32; enable generateImmutable on the matching VECTOR primitive",
            error.message,
        )
    }

    @Test
    fun `disabled point output does not require a vector`() {
        SchemaValidator.validate(
            schemaOf(
                primitive(
                    Semantic.POINT,
                    2,
                    ScalarId.F32,
                    generateImmutable = false,
                ),
            ),
        )
    }

    @Test
    fun `I32 rejects normalization`() {
        val schema = schemaOf(
            primitive(
                Semantic.VECTOR,
                2,
                ScalarId.I32,
                capabilities = setOf(Capability.NORMALIZE),
            ),
        )

        val error = assertFailsWith<SchemaValidationException> {
            SchemaValidator.validate(schema)
        }

        assertEquals(
            "Vector2I32 cannot use NORMALIZE with scalar I32; remove NORMALIZE",
            error.message,
        )
    }

    @Test
    fun `I32 rejects division`() {
        val schema = schemaOf(
            primitive(
                Semantic.VECTOR,
                2,
                ScalarId.I32,
                capabilities = setOf(Capability.DIVIDE),
            ),
        )

        val error = assertFailsWith<SchemaValidationException> {
            SchemaValidator.validate(schema)
        }

        assertEquals(
            "Vector2I32 cannot use DIVIDE with scalar I32; remove DIVIDE",
            error.message,
        )
    }

    @Test
    fun `I32 rejects finite check`() {
        val schema = schemaOf(
            primitive(
                Semantic.VECTOR,
                2,
                ScalarId.I32,
                capabilities = setOf(Capability.FINITE_CHECK),
            ),
        )

        val error = assertFailsWith<SchemaValidationException> {
            SchemaValidator.validate(schema)
        }

        assertEquals(
            "Vector2I32 cannot use FINITE_CHECK with scalar I32; remove FINITE_CHECK",
            error.message,
        )
    }

    @Test
    fun `I32 mutable vector output is rejected explicitly`() {
        val error = assertFailsWith<SchemaValidationException> {
            SchemaValidator.validate(
                schemaOf(
                    primitive(
                        Semantic.VECTOR,
                        2,
                        ScalarId.I32,
                        generateMutable = true,
                    ),
                ),
            )
        }

        assertEquals(
            "Vector2I32 cannot generate mutable output with scalar I32; disable generateMutable or use F32/F64",
            error.message,
        )
    }

    @Test
    fun `I32 mutable point output is rejected explicitly`() {
        val error = assertFailsWith<SchemaValidationException> {
            SchemaValidator.validate(
                schemaOf(
                    primitive(
                        Semantic.VECTOR,
                        2,
                        ScalarId.I32,
                    ),
                    primitive(
                        Semantic.POINT,
                        2,
                        ScalarId.I32,
                        generateMutable = true,
                    ),
                ),
            )
        }

        assertEquals(
            "Point2I32 cannot generate mutable output with scalar I32; disable generateMutable or use F32/F64",
            error.message,
        )
    }

    @Test
    fun `I32 output is limited to supported two dimensional primitives`() {
        val error = assertFailsWith<SchemaValidationException> {
            SchemaValidator.validate(
                schemaOf(primitive(Semantic.VECTOR, 3, ScalarId.I32)),
            )
        }

        assertEquals(
            "Vector3I32 cannot use scalar I32 with dimension 3; use dimension 2",
            error.message,
        )
    }

    @Test
    fun `dimension outside closed component table is rejected`() {
        val error = assertFailsWith<SchemaValidationException> {
            SchemaValidator.validate(schemaOf(primitive(Semantic.VECTOR, 1, ScalarId.F32)))
        }

        assertEquals(
            "dimension 1 has no component names; use a dimension in 2..4",
            error.message,
        )
    }

    @Test
    fun `primitive scalar must be declared`() {
        val error = assertFailsWith<SchemaValidationException> {
            SchemaValidator.validate(
                PrimitiveSchema(
                    scalars = defaultScalars.filterNot { it.id == ScalarId.F64 },
                    primitives = listOf(primitive(Semantic.VECTOR, 2, ScalarId.F64)),
                ),
            )
        }

        assertEquals("Vector2F64 uses undefined scalar F64; add a ScalarSpec for F64", error.message)
    }

    @Test
    fun `scalar identifiers must be unique`() {
        val error = assertFailsWith<SchemaValidationException> {
            SchemaValidator.validate(
                PrimitiveSchema(
                    scalars = defaultScalars + defaultScalars.first(),
                    primitives = listOf(primitive(Semantic.VECTOR, 2, ScalarId.F32)),
                ),
            )
        }

        assertEquals("scalar F32 is declared more than once; keep one ScalarSpec per scalar", error.message)
    }

    @Test
    fun `generated type names must be unique`() {
        val error = assertFailsWith<SchemaValidationException> {
            SchemaValidator.validate(
                schemaOf(
                    primitive(Semantic.VECTOR, 2, ScalarId.F32, generateImmutable = false),
                    primitive(Semantic.VECTOR, 2, ScalarId.F32, generateImmutable = false),
                ),
            )
        }

        assertEquals("generated type name Vector2F32 is declared more than once", error.message)
    }

    @Test
    fun `generated output paths must be unique`() {
        val error = assertFailsWith<SchemaValidationException> {
            SchemaValidator.validate(
                schemaOf(
                    primitive(Semantic.VECTOR, 2, ScalarId.F32, generateMutable = true),
                    primitive(Semantic.VECTOR, 2, ScalarId.F32, generateMutable = true),
                ),
            )
        }

        assertEquals(
            "generated output path vector/MutableVector2F32.kt is declared more than once",
            error.message,
        )
    }

    @Test
    fun `mutable generation requires immutable generation`() {
        val error = assertFailsWith<SchemaValidationException> {
            SchemaValidator.validate(
                schemaOf(
                    primitive(
                        Semantic.VECTOR,
                        2,
                        ScalarId.F32,
                        generateImmutable = false,
                        generateMutable = true,
                    ),
                ),
            )
        }

        assertEquals(
            "Vector2F32 cannot generate mutable without immutable; enable generateImmutable",
            error.message,
        )
    }

    @Test
    fun `future multi field target requires fallback`() {
        val error = assertFailsWith<SchemaValidationException> {
            SchemaValidator.validate(
                schemaOf(
                    primitive(
                        Semantic.VECTOR,
                        2,
                        ScalarId.F32,
                        targetRepresentation = ImmutableRepresentation.MULTI_FIELD_VALUE,
                        fallbackRepresentation = null,
                    ),
                ),
            )
        }

        assertEquals(
            "Vector2F32 requires fallback representation when MULTI_FIELD_VALUE is unavailable",
            error.message,
        )
    }

    @Test
    fun `current final class target does not require fallback`() {
        SchemaValidator.validate(
            schemaOf(
                primitive(
                    Semantic.VECTOR,
                    2,
                    ScalarId.F32,
                    targetRepresentation = ImmutableRepresentation.FINAL_CLASS,
                    fallbackRepresentation = null,
                ),
            ),
        )
    }

    @Test
    fun `vector manifest validates`() {
        SchemaValidator.validate(MathPrimitiveManifest.schema)
    }

    private fun schemaOf(vararg primitives: PrimitiveSpec): PrimitiveSchema =
        PrimitiveSchema(defaultScalars, primitives.toList())

    private fun primitive(
        semantic: Semantic,
        dimension: Int,
        scalar: ScalarId,
        capabilities: Set<Capability> = emptySet(),
        targetRepresentation: ImmutableRepresentation = ImmutableRepresentation.MULTI_FIELD_VALUE,
        fallbackRepresentation: ImmutableRepresentation? = ImmutableRepresentation.FINAL_CLASS,
        generateImmutable: Boolean = true,
        generateMutable: Boolean = false,
    ) = PrimitiveSpec(
        semantic = semantic,
        dimension = dimension,
        scalar = scalar,
        capabilities = capabilities,
        targetRepresentation = targetRepresentation,
        fallbackRepresentation = fallbackRepresentation,
        generateImmutable = generateImmutable,
        generateMutable = generateMutable,
    )

    private companion object {
        val defaultScalars = listOf(
            ScalarSpec(ScalarId.F32, "Float", ArithmeticPolicy.IEEE_754),
            ScalarSpec(ScalarId.F64, "Double", ArithmeticPolicy.IEEE_754),
            ScalarSpec(ScalarId.I32, "Int", ArithmeticPolicy.SATURATING, accumulatorType = "Long"),
        )
    }
}
