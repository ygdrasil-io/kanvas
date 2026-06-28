package org.graphiks.kanvas.gpu.renderer.runtimeeffects

enum class GPURuntimeEffectLiveParameterType {
    Float,
    Float2,
    Float3,
    Float4,
    Int,
    Color,
}

sealed class GPURuntimeEffectLiveValue {
    data class FloatValue(val value: Float) : GPURuntimeEffectLiveValue()
    data class Float2Value(val x: Float, val y: Float) : GPURuntimeEffectLiveValue()
    data class Float3Value(val x: Float, val y: Float, val z: Float) : GPURuntimeEffectLiveValue()
    data class Float4Value(val x: Float, val y: Float, val z: Float, val w: Float) : GPURuntimeEffectLiveValue()
    data class IntValue(val value: Int) : GPURuntimeEffectLiveValue()
    data class ColorValue(val r: Float, val g: Float, val b: Float, val a: Float) : GPURuntimeEffectLiveValue()

    fun serialize(): String = when (this) {
        is FloatValue -> "float:$value"
        is Float2Value -> "float2:$x,$y"
        is Float3Value -> "float3:$x,$y,$z"
        is Float4Value -> "float4:$x,$y,$z,$w"
        is IntValue -> "int:$value"
        is ColorValue -> "color:$r,$g,$b,$a"
    }

    companion object {
        fun deserialize(raw: String): GPURuntimeEffectLiveValue {
            val parts = raw.split(":", limit = 2)
            require(parts.size == 2) { "Invalid serialized value: $raw" }
            return when (parts[0]) {
                "float" -> {
                    val values = parts[1].split(",").map { it.toFloat() }
                    require(values.size == 1)
                    FloatValue(values[0])
                }
                "float2" -> {
                    val values = parts[1].split(",").map { it.toFloat() }
                    require(values.size == 2)
                    Float2Value(values[0], values[1])
                }
                "float3" -> {
                    val values = parts[1].split(",").map { it.toFloat() }
                    require(values.size == 3)
                    Float3Value(values[0], values[1], values[2])
                }
                "float4" -> {
                    val values = parts[1].split(",").map { it.toFloat() }
                    require(values.size == 4)
                    Float4Value(values[0], values[1], values[2], values[3])
                }
                "int" -> {
                    IntValue(parts[1].toInt())
                }
                "color" -> {
                    val values = parts[1].split(",").map { it.toFloat() }
                    require(values.size == 4)
                    ColorValue(values[0], values[1], values[2], values[3])
                }
                else -> throw IllegalArgumentException("Unknown value type: ${parts[0]}")
            }
        }
    }
}

data class GPURuntimeEffectLiveParameterSchema(
    val parameters: List<GPURuntimeEffectLiveParameter>,
) {
    data class GPURuntimeEffectLiveParameter(
        val id: String,
        val displayName: String,
        val type: GPURuntimeEffectLiveParameterType,
        val default: GPURuntimeEffectLiveValue,
        val min: GPURuntimeEffectLiveValue?,
        val max: GPURuntimeEffectLiveValue?,
        val step: GPURuntimeEffectLiveValue?,
    )
}

data class GPURuntimeEffectLiveParameterBinding(
    val parameterId: String,
    val uniformOffsetBytes: Int,
)

data class GPURuntimeEffectLiveState(
    val values: Map<String, GPURuntimeEffectLiveValue>,
    val dirtyFlags: Set<String>,
    val generationCounter: ULong,
)

data class GPURuntimeEffectLiveControlPlan(
    val schema: GPURuntimeEffectLiveParameterSchema,
    val bindings: List<GPURuntimeEffectLiveParameterBinding>,
    val state: GPURuntimeEffectLiveState,
) {
    fun setParameter(id: String, value: GPURuntimeEffectLiveValue): GPURuntimeEffectLiveState {
        val currentValue = state.values[id]
        if (currentValue == value) {
            return state
        }
        return state.copy(
            values = state.values + (id to value),
            dirtyFlags = state.dirtyFlags + id,
            generationCounter = state.generationCounter + 1uL,
        )
    }

    fun resetToDefaults(): GPURuntimeEffectLiveState {
        val defaultValues = schema.parameters.associate { it.id to it.default }
        val changed = defaultValues.any { (id, default) -> state.values[id] != default }
        return state.copy(
            values = defaultValues,
            dirtyFlags = emptySet(),
            generationCounter = if (changed) state.generationCounter + 1uL else state.generationCounter,
        )
    }

    fun serializePreset(): String {
        val valuesPart = state.values.entries.joinToString(";") { (id, value) ->
            "$id=${value.serialize()}"
        }
        val dirtyPart = state.dirtyFlags.joinToString(",")
        return "v1|$valuesPart|$dirtyPart|${state.generationCounter}"
    }

    fun deserializePreset(preset: String): GPURuntimeEffectLiveState {
        val parts = preset.split("|", limit = 4)
        require(parts.size == 4 && parts[0] == "v1") { "Invalid preset format: $preset" }

        val valuesPart = parts[1]
        val dirtyPart = parts[2]
        val generationPart = parts[3]

        val values = if (valuesPart.isBlank()) {
            emptyMap()
        } else {
            valuesPart.split(";").associate { entry ->
                val (id, raw) = entry.split("=", limit = 2)
                id to GPURuntimeEffectLiveValue.deserialize(raw)
            }
        }

        val dirtyFlags = if (dirtyPart.isBlank()) {
            emptySet()
        } else {
            dirtyPart.split(",").toSet()
        }

        val generationCounter = generationPart.toULong()

        return GPURuntimeEffectLiveState(
            values = values,
            dirtyFlags = dirtyFlags,
            generationCounter = generationCounter,
        )
    }
}
