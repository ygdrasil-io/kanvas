package org.graphiks.kanvas.gpu.renderer.runtimeeffects

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class GpuLiveParameterTest {

    @Test
    fun `create live parameter schema with float int and color parameters`() {
        val params = listOf(
            GPURuntimeEffectLiveParameterSchema.GPURuntimeEffectLiveParameter(
                id = "brightness",
                displayName = "Brightness",
                type = GPURuntimeEffectLiveParameterType.Float,
                default = GPURuntimeEffectLiveValue.FloatValue(1.0f),
                min = GPURuntimeEffectLiveValue.FloatValue(0.0f),
                max = GPURuntimeEffectLiveValue.FloatValue(2.0f),
                step = GPURuntimeEffectLiveValue.FloatValue(0.01f),
            ),
            GPURuntimeEffectLiveParameterSchema.GPURuntimeEffectLiveParameter(
                id = "iterations",
                displayName = "Iterations",
                type = GPURuntimeEffectLiveParameterType.Int,
                default = GPURuntimeEffectLiveValue.IntValue(4),
                min = GPURuntimeEffectLiveValue.IntValue(1),
                max = GPURuntimeEffectLiveValue.IntValue(10),
                step = GPURuntimeEffectLiveValue.IntValue(1),
            ),
            GPURuntimeEffectLiveParameterSchema.GPURuntimeEffectLiveParameter(
                id = "tint",
                displayName = "Tint Color",
                type = GPURuntimeEffectLiveParameterType.Color,
                default = GPURuntimeEffectLiveValue.ColorValue(1.0f, 1.0f, 1.0f, 1.0f),
                min = null,
                max = null,
                step = null,
            ),
        )
        val schema = GPURuntimeEffectLiveParameterSchema(params)
        assertEquals(3, schema.parameters.size)
        assertEquals("brightness", schema.parameters[0].id)
        assertEquals(GPURuntimeEffectLiveParameterType.Float, schema.parameters[0].type)
        assertEquals("iterations", schema.parameters[1].id)
        assertEquals(GPURuntimeEffectLiveParameterType.Int, schema.parameters[1].type)
        assertEquals("tint", schema.parameters[2].id)
        assertEquals(GPURuntimeEffectLiveParameterType.Color, schema.parameters[2].type)
    }

    @Test
    fun `dirty tracking marks parameter dirty on value change and increments generation counter`() {
        val schema = createTestSchema()
        val bindings = schema.parameters.mapIndexed { index, param ->
            GPURuntimeEffectLiveParameterBinding(param.id, index * 16)
        }
        val initialState = GPURuntimeEffectLiveState(
            values = schema.parameters.associate { it.id to it.default },
            dirtyFlags = emptySet(),
            generationCounter = 0uL,
        )
        val plan = GPURuntimeEffectLiveControlPlan(schema, bindings, initialState)

        val updatedState = plan.setParameter("brightness", GPURuntimeEffectLiveValue.FloatValue(0.5f))
        assertEquals(GPURuntimeEffectLiveValue.FloatValue(0.5f), updatedState.values["brightness"])
        assertTrue("brightness" in updatedState.dirtyFlags)
        assertEquals(1uL, updatedState.generationCounter)
    }

    @Test
    fun `dirty tracking does not mark unchanged parameter dirty`() {
        val schema = createTestSchema()
        val bindings = schema.parameters.mapIndexed { index, param ->
            GPURuntimeEffectLiveParameterBinding(param.id, index * 16)
        }
        val initialState = GPURuntimeEffectLiveState(
            values = schema.parameters.associate { it.id to it.default },
            dirtyFlags = emptySet(),
            generationCounter = 0uL,
        )
        val plan = GPURuntimeEffectLiveControlPlan(schema, bindings, initialState)

        val updatedState = plan.setParameter("brightness", GPURuntimeEffectLiveValue.FloatValue(1.0f))
        assertFalse("brightness" in updatedState.dirtyFlags)
        assertEquals(0uL, updatedState.generationCounter)
    }

    @Test
    fun `preset round trip serialize deserialize produces identical state`() {
        val schema = createTestSchema()
        val bindings = schema.parameters.mapIndexed { index, param ->
            GPURuntimeEffectLiveParameterBinding(param.id, index * 16)
        }
        val initialState = GPURuntimeEffectLiveState(
            values = schema.parameters.associate { it.id to it.default },
            dirtyFlags = setOf("brightness"),
            generationCounter = 3uL,
        )
        val plan = GPURuntimeEffectLiveControlPlan(schema, bindings, initialState)

        val preset = plan.serializePreset()
        assertNotNull(preset)
        assertTrue(preset.isNotBlank())

        val restored = plan.deserializePreset(preset)
        assertEquals(initialState.values, restored.values)
        assertEquals(initialState.dirtyFlags, restored.dirtyFlags)
        assertEquals(initialState.generationCounter, restored.generationCounter)
    }

    @Test
    fun `preset round trip with multiple parameters preserves all values`() {
        val schema = createTestSchema()
        val bindings = schema.parameters.mapIndexed { index, param ->
            GPURuntimeEffectLiveParameterBinding(param.id, index * 16)
        }
        val modifiedValues = mapOf<String, GPURuntimeEffectLiveValue>(
            "brightness" to GPURuntimeEffectLiveValue.FloatValue(0.3f),
            "iterations" to GPURuntimeEffectLiveValue.IntValue(7),
            "tint" to GPURuntimeEffectLiveValue.ColorValue(0.2f, 0.8f, 0.4f, 0.9f),
        )
        val initialState = GPURuntimeEffectLiveState(
            values = modifiedValues,
            dirtyFlags = modifiedValues.keys,
            generationCounter = 5uL,
        )
        val plan = GPURuntimeEffectLiveControlPlan(schema, bindings, initialState)

        val preset = plan.serializePreset()
        val restored = plan.deserializePreset(preset)

        assertEquals(GPURuntimeEffectLiveValue.FloatValue(0.3f), restored.values["brightness"])
        assertEquals(GPURuntimeEffectLiveValue.IntValue(7), restored.values["iterations"])
        assertEquals(GPURuntimeEffectLiveValue.ColorValue(0.2f, 0.8f, 0.4f, 0.9f), restored.values["tint"])
        assertEquals(modifiedValues.keys, restored.dirtyFlags)
        assertEquals(5uL, restored.generationCounter)
    }

    @Test
    fun `reset to defaults clears dirty flags and restores defaults`() {
        val schema = createTestSchema()
        val bindings = schema.parameters.mapIndexed { index, param ->
            GPURuntimeEffectLiveParameterBinding(param.id, index * 16)
        }
        val modifiedValues = mapOf<String, GPURuntimeEffectLiveValue>(
            "brightness" to GPURuntimeEffectLiveValue.FloatValue(0.3f),
            "iterations" to GPURuntimeEffectLiveValue.IntValue(7),
            "tint" to GPURuntimeEffectLiveValue.ColorValue(0.2f, 0.8f, 0.4f, 0.9f),
        )
        val initialState = GPURuntimeEffectLiveState(
            values = modifiedValues,
            dirtyFlags = modifiedValues.keys,
            generationCounter = 5uL,
        )
        val plan = GPURuntimeEffectLiveControlPlan(schema, bindings, initialState)

        val resetState = plan.resetToDefaults()
        assertEquals(schema.parameters.associate { it.id to it.default }, resetState.values)
        assertTrue(resetState.dirtyFlags.isEmpty())
        assertEquals(6uL, resetState.generationCounter)
    }

    @Test
    fun `setParameter with float2 float3 float4 types`() {
        val schema = GPURuntimeEffectLiveParameterSchema(
            listOf(
                GPURuntimeEffectLiveParameterSchema.GPURuntimeEffectLiveParameter(
                    id = "offset",
                    displayName = "Offset",
                    type = GPURuntimeEffectLiveParameterType.Float2,
                    default = GPURuntimeEffectLiveValue.Float2Value(0.0f, 0.0f),
                    min = null,
                    max = null,
                    step = null,
                ),
                GPURuntimeEffectLiveParameterSchema.GPURuntimeEffectLiveParameter(
                    id = "position",
                    displayName = "Position",
                    type = GPURuntimeEffectLiveParameterType.Float3,
                    default = GPURuntimeEffectLiveValue.Float3Value(0.0f, 0.0f, 0.0f),
                    min = null,
                    max = null,
                    step = null,
                ),
                GPURuntimeEffectLiveParameterSchema.GPURuntimeEffectLiveParameter(
                    id = "color4",
                    displayName = "Color4",
                    type = GPURuntimeEffectLiveParameterType.Float4,
                    default = GPURuntimeEffectLiveValue.Float4Value(1.0f, 1.0f, 1.0f, 1.0f),
                    min = null,
                    max = null,
                    step = null,
                ),
            )
        )
        assertEquals(3, schema.parameters.size)

        val bindings = schema.parameters.mapIndexed { index, param ->
            GPURuntimeEffectLiveParameterBinding(param.id, index * 16)
        }
        val initialState = GPURuntimeEffectLiveState(
            values = schema.parameters.associate { it.id to it.default },
            dirtyFlags = emptySet(),
            generationCounter = 0uL,
        )
        val plan = GPURuntimeEffectLiveControlPlan(schema, bindings, initialState)

        val s1 = plan.setParameter("offset", GPURuntimeEffectLiveValue.Float2Value(1.0f, 2.0f))
        assertEquals(GPURuntimeEffectLiveValue.Float2Value(1.0f, 2.0f), s1.values["offset"])
        assertTrue("offset" in s1.dirtyFlags)

        val s2 = plan.setParameter("position", GPURuntimeEffectLiveValue.Float3Value(3.0f, 4.0f, 5.0f))
        assertTrue("position" in s2.dirtyFlags)

        val s3 = plan.setParameter("color4", GPURuntimeEffectLiveValue.Float4Value(0.5f, 0.5f, 0.5f, 0.5f))
        assertTrue("color4" in s3.dirtyFlags)
    }

    @Test
    fun `parameter binding maps to correct byte offsets`() {
        val schema = createTestSchema()
        val bindings = schema.parameters.mapIndexed { index, param ->
            GPURuntimeEffectLiveParameterBinding(param.id, index * 16)
        }
        assertEquals(3, bindings.size)
        assertEquals("brightness", bindings[0].parameterId)
        assertEquals(0, bindings[0].uniformOffsetBytes)
        assertEquals("iterations", bindings[1].parameterId)
        assertEquals(16, bindings[1].uniformOffsetBytes)
        assertEquals("tint", bindings[2].parameterId)
        assertEquals(32, bindings[2].uniformOffsetBytes)
    }

    @Test
    fun `dirty tracking accumulates multiple changes across sequential sets`() {
        val schema = createTestSchema()
        val bindings = schema.parameters.mapIndexed { index, param ->
            GPURuntimeEffectLiveParameterBinding(param.id, index * 16)
        }
        val initialState = GPURuntimeEffectLiveState(
            values = schema.parameters.associate { it.id to it.default },
            dirtyFlags = emptySet(),
            generationCounter = 0uL,
        )
        val plan = GPURuntimeEffectLiveControlPlan(schema, bindings, initialState)

        val s1 = plan.setParameter("brightness", GPURuntimeEffectLiveValue.FloatValue(0.3f))
        assertTrue("brightness" in s1.dirtyFlags)
        assertEquals(1uL, s1.generationCounter)

        val s2 = GPURuntimeEffectLiveControlPlan(schema, bindings, s1)
            .setParameter("iterations", GPURuntimeEffectLiveValue.IntValue(5))
        assertTrue("brightness" in s2.dirtyFlags)
        assertTrue("iterations" in s2.dirtyFlags)
        assertEquals(2uL, s2.generationCounter)

        val s3 = GPURuntimeEffectLiveControlPlan(schema, bindings, s2)
            .setParameter("tint", GPURuntimeEffectLiveValue.ColorValue(0.5f, 0.5f, 0.5f, 1.0f))
        assertTrue("brightness" in s3.dirtyFlags)
        assertTrue("iterations" in s3.dirtyFlags)
        assertTrue("tint" in s3.dirtyFlags)
        assertEquals(3uL, s3.generationCounter)
    }

    private fun createTestSchema(): GPURuntimeEffectLiveParameterSchema {
        val params = listOf(
            GPURuntimeEffectLiveParameterSchema.GPURuntimeEffectLiveParameter(
                id = "brightness",
                displayName = "Brightness",
                type = GPURuntimeEffectLiveParameterType.Float,
                default = GPURuntimeEffectLiveValue.FloatValue(1.0f),
                min = GPURuntimeEffectLiveValue.FloatValue(0.0f),
                max = GPURuntimeEffectLiveValue.FloatValue(2.0f),
                step = GPURuntimeEffectLiveValue.FloatValue(0.01f),
            ),
            GPURuntimeEffectLiveParameterSchema.GPURuntimeEffectLiveParameter(
                id = "iterations",
                displayName = "Iterations",
                type = GPURuntimeEffectLiveParameterType.Int,
                default = GPURuntimeEffectLiveValue.IntValue(4),
                min = GPURuntimeEffectLiveValue.IntValue(1),
                max = GPURuntimeEffectLiveValue.IntValue(10),
                step = GPURuntimeEffectLiveValue.IntValue(1),
            ),
            GPURuntimeEffectLiveParameterSchema.GPURuntimeEffectLiveParameter(
                id = "tint",
                displayName = "Tint Color",
                type = GPURuntimeEffectLiveParameterType.Color,
                default = GPURuntimeEffectLiveValue.ColorValue(1.0f, 1.0f, 1.0f, 1.0f),
                min = null,
                max = null,
                step = null,
            ),
        )
        return GPURuntimeEffectLiveParameterSchema(params)
    }
}
