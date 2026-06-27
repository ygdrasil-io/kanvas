# Custom Runtime Effect Pipeline

Status: Draft
Date: 2026-06-27

## Purpose

This spec defines the **custom runtime effect pipeline** for the GPU-first renderer, enabling users to provide **custom WGSL shaders** while maintaining Kanvas' architectural constraints:

- **No dynamic SkSL compilation** (architectural decision).
- **Strict validation via `wgsl4k`** for all custom WGSL.
- **Isolation from registered effects** (separate registry, no cache sharing).
- **Stable diagnostics** for failures (no silent fallbacks).

This spec extends the existing runtime effect system (`27-registered-runtime-effects-registry.md`) to support **user-provided WGSL** while preserving security, performance, and maintainability.

---

## Source Specs

This spec depends on:

- `27-registered-runtime-effects-registry.md` for the base runtime effect registry and descriptor model.
- `03-material-key-wgsl.md` for `MaterialKey`, `WGSLModule`, and material/WGSL boundaries.
- `05-routing-policy.md` for `GPUNative`, `CPUReferenceOnly`, and `RefuseDiagnostic`.
- `11-wgsl-layout-binding-abi.md` for uniform/resource layout and WGSL reflection.
- `13-performance-telemetry-cache-gates.md` for telemetry, budgets, and cache policies.
- `17-payload-gathering-and-slots.md` for uniform/resource payload handling.

---

## Non-Goals

- **Dynamic SkSL compilation**: Custom effects must be provided as **WGSL source strings**, not SkSL.
- **Silent fallbacks**: If validation fails, the effect **must refuse explicitly** (no CPU fallback or solid color).
- **Shared caches**: Custom effects **do not share caches** with registered effects.
- **Promoted routes**: Custom effects **cannot be promoted** to `GPUNative` without explicit validation.
- **Arbitrary code execution**: Custom WGSL is **validated and sandboxed** (see `35-custom-runtime-effect-security.md`).

---

## Core Objects

### `GPUCustomRuntimeEffectID`

A unique identifier for a custom runtime effect, generated from:
- The **WGSL source hash**.
- The **uniform schema hash**.
- The **child slot hash**.

Format: `custom.<hash>` (e.g., `custom.a1b2c3d4`).

```kotlin
data class GPUCustomRuntimeEffectID(val value: String)
```

---

### `GPUCustomRuntimeEffectDescriptor`

Extends `GPURuntimeEffectDescriptor` to support custom WGSL. **Not interchangeable** with registered descriptors.

```kotlin
sealed class GPURuntimeEffectDescriptor {
    val id: GPURuntimeEffectID
    val kind: GPURuntimeEffectDescriptorKind // REGISTERED or CUSTOM
    val uniformSchema: GPURuntimeEffectUniformSchema
    val childSlots: List<GPURuntimeEffectChildSlotPlan>
    val wgslPlan: GPURuntimeEffectWGSLPlan
    val cpuOracle: GPURuntimeEffectCPUOracle? // Optional for custom effects
}

data class GPUCustomRuntimeEffectDescriptor(
    override val id: GPUCustomRuntimeEffectID,
    override val uniformSchema: GPURuntimeEffectUniformSchema,
    override val childSlots: List<GPURuntimeEffectChildSlotPlan>,
    override val wgslPlan: GPUCustomRuntimeEffectWGSLPlan,
    val sourceProvenance: String, // e.g., "user_shader_123"
    val validationStatus: GPUCustomRuntimeEffectValidationStatus,
    override val cpuOracle: GPURuntimeEffectCPUOracle? = null,
) : GPURuntimeEffectDescriptor() {
    override val kind: GPURuntimeEffectDescriptorKind = GPURuntimeEffectDescriptorKind.CUSTOM
}
```

---

### `GPUCustomRuntimeEffectWGSLPlan`

Contains the **custom WGSL source** and its validation/reflection results.

```kotlin
data class GPUCustomRuntimeEffectWGSLPlan(
    val source: String, // The custom WGSL source code
    val entryPoint: String, // e.g., "main"
    val reflection: WGSLReflectionResult, // From wgsl4k
    val validationReport: WGSLValidationReport, // From wgsl4k
) : GPURuntimeEffectWGSLPlan()
```

---

### `GPUCustomRuntimeEffectValidationStatus`

Status of a custom effect's validation.

```kotlin
enum class GPUCustomRuntimeEffectValidationStatus {
    PENDING,   // Not yet validated
    VALID,      // Validated and ready for execution
    INVALID,    // Validation failed (syntax, layout, or security)
}
```

---

### `GPUCustomRuntimeEffectRegistry`

A **separate registry** for custom effects, isolated from `GPURuntimeEffectRegistry`.

```kotlin
class GPUCustomRuntimeEffectRegistry(
    private val validator: WGSLValidator, // Uses wgsl4k
    private val reflectionProvider: WGSLReflectionProvider,
) {
    private val descriptors: MutableMap<GPUCustomRuntimeEffectID, GPUCustomRuntimeEffectDescriptor> = mutableMapOf()

    // Register a new custom effect
    fun registerCustomEffect(
        source: String,
        uniformSchema: GPURuntimeEffectUniformSchema,
        childSlots: List<GPURuntimeEffectChildSlotPlan>,
        sourceProvenance: String,
    ): Result<GPUCustomRuntimeEffectID> {
        // 1. Generate a unique ID
        val id = generateCustomEffectID(source, uniformSchema, childSlots)

        // 2. Validate the WGSL via wgsl4k
        val validationResult = validator.validate(source)
        if (!validationResult.isValid) {
            return Result.failure(GPUCustomRuntimeEffectValidationError(validationResult.errors))
        }

        // 3. Extract reflection
        val reflection = reflectionProvider.reflect(source)
        val wgslPlan = GPUCustomRuntimeEffectWGSLPlan(
            source = source,
            entryPoint = "main", // Or extracted from reflection
            reflection = reflection,
            validationReport = validationResult,
        )

        // 4. Create the descriptor
        val descriptor = GPUCustomRuntimeEffectDescriptor(
            id = id,
            uniformSchema = uniformSchema,
            childSlots = childSlots,
            wgslPlan = wgslPlan,
            sourceProvenance = sourceProvenance,
            validationStatus = GPUCustomRuntimeEffectValidationStatus.VALID,
            cpuOracle = null, // No CPU oracle by default
        )

        // 5. Add to registry
        descriptors[id] = descriptor
        return Result.success(id)
    }

    // Get a descriptor by ID
    fun getDescriptor(id: GPUCustomRuntimeEffectID): GPUCustomRuntimeEffectDescriptor? {
        return descriptors[id]
    }

    // Unregister a custom effect
    fun unregisterCustomEffect(id: GPUCustomRuntimeEffectID) {
        descriptors.remove(id)
    }

    // Check if an effect is registered
    fun isRegistered(id: GPUCustomRuntimeEffectID): Boolean {
        return descriptors.containsKey(id)
    }

    private fun generateCustomEffectID(
        source: String,
        uniformSchema: GPURuntimeEffectUniformSchema,
        childSlots: List<GPURuntimeEffectChildSlotPlan>,
    ): GPUCustomRuntimeEffectID {
        val hashInput = "$source|${uniformSchema.hash}|${childSlots.hash}"
        return GPUCustomRuntimeEffectID("custom.${hashInput.hashCode()}")
    }
}
```

---

## Validation Rules

### Mandatory Checks for Custom WGSL

| Check | Description | Failure Diagnostic |
|-------|-------------|---------------------|
| **`wgsl4k` Syntax Validation** | Ensures the WGSL is syntactically correct. | `custom-wgsl.syntax-error` |
| **Layout Reflection** | Validates that uniforms/bindings match the declared schema. | `custom-wgsl.layout-mismatch` |
| **Resource Limits** | Ensures the shader does not exceed budgets (e.g., max textures, uniform size). | `custom-wgsl.budget-exceeded` |
| **Security Checks** | Blocks unsafe features (e.g., unbounded `storageBuffer`, `atomic`). | `custom-wgsl.unsafe-feature` |
| **Device Compatibility** | Ensures the WGSL is supported by the target WebGPU device. | `custom-wgsl.device-unsupported` |

### Example Validation

```kotlin
fun validateCustomWGSL(source: String): WGSLValidationReport {
    val parser = WGSLParser() // Uses wgsl4k
    val module = parser.parse(source)
    val diagnostics = mutableListOf<WGSLDiagnostic>()

    // 1. Syntax check
    if (module.hasErrors) {
        diagnostics.addAll(module.errors.map { WGSLDiagnostic.ERROR(it) })
        return WGSLValidationReport(isValid = false, diagnostics = diagnostics)
    }

    // 2. Resource limits
    if (module.uniforms.size > MAX_UNIFORMS) {
        diagnostics.add(WGSLDiagnostic.ERROR("uniform-count-exceeded"))
    }

    // 3. Security checks
    if (module.usesUnsafeFeatures()) {
        diagnostics.add(WGSLDiagnostic.ERROR("unsafe-feature"))
    }

    return WGSLValidationReport(
        isValid = diagnostics.isEmpty(),
        diagnostics = diagnostics,
    )
}
```

---

## Execution Pipeline

### Route: `GPUCustomRuntimeEffect`

Custom effects use a **dedicated route** to avoid conflicts with registered effects:

```kotlin
enum class GPURouteKind {
    GPUNative,
    CPUPreparedGPU,
    CPUReferenceOnly,
    RefuseDiagnostic,
    CustomRuntimeEffect, // New route for custom effects
}
```

### Integration with `GPUMaterialDictionary`

Custom effects can be used as:
- **Material source** (`sourceRoot`).
- **Color filter** (`filterRoot`).
- **Blender** (`shaderBlendRoot`).

**Restriction**: Custom effects **cannot be folded** into shared caches.

### Execution Flow

1. **Lookup**: The renderer checks `GPUCustomRuntimeEffectRegistry` for the effect ID.
2. **Validation**: If not registered or invalid, **refuse explicitly**.
3. **Pipeline Creation**: If valid, compile the WGSL into a WebGPU module.
4. **Uniform Packing**: Pack uniforms according to the declared schema.
5. **Execution**: Dispatch the shader with the packed uniforms.

---

## Integration with Existing Specs

### Modifications to Other Specs

| Spec | Modification |
|------|--------------|
| `27-registered-runtime-effects-registry.md` | Add section for `GPUCustomRuntimeEffectRegistry`. |
| `03-material-key-wgsl.md` | Extend `MaterialKey` to support custom effect IDs. |
| `05-routing-policy.md` | Add `CustomRuntimeEffect` as a new route. |
| `11-wgsl-layout-binding-abi.md` | Add checks for custom effect layouts. |
| `13-performance-telemetry-cache-gates.md` | Add counters for custom effects (e.g., `custom_runtime_effect.count`). |

---

## Diagnostics and Refusals

### Stable Error Codes for Custom WGSL

| Error Code | Description | Example Message |
|------------|-------------|-----------------|
| `custom-wgsl.syntax-error` | WGSL syntax error. | "Invalid WGSL: expected 'fn' at line 10, column 5" |
| `custom-wgsl.layout-mismatch` | Layout incompatible with declared schema. | "Uniform 'time' not found in WGSL reflection" |
| `custom-wgsl.unsafe-feature` | Uses a blocked feature (e.g., `atomic`). | "Feature 'atomic' is not allowed in custom WGSL" |
| `custom-wgsl.budget-exceeded` | Exceeds resource limits. | "Shader uses 17 textures (max: 16)" |
| `custom-wgsl.device-unsupported` | WGSL not supported by the device. | "Device does not support storage buffers" |
| `custom-wgsl.not-registered` | Effect not registered. | "Custom effect 'custom.a1b2c3d4' not found" |

### Example Diagnostic Output

```json
{
  "error": "custom-wgsl.validation-failed",
  "message": "WGSL validation failed",
  "details": [
    {
      "severity": "ERROR",
      "message": "expected expression",
      "line": 3,
      "column": 15,
      "length": 1
    }
  ],
  "source": "@fragment\nfn main(...) { ... }"
}
```

---

## Security Considerations

See `35-custom-runtime-effect-security.md` for:
- **Blocked WGSL features** (e.g., `storageBuffer` without bounds, `atomic`).
- **Resource limits** (max textures, uniform size, etc.).
- **Sandboxing** (no access to external resources).

---

## Performance Considerations

| Metric | Expected Impact | Mitigation |
|--------|-----------------|------------|
| **Validation Overhead** | `wgsl4k` parsing adds latency. | Cache validation results. |
| **Memory Usage** | Storing custom descriptors. | Limit the number of custom effects per session. |
| **Cache Isolation** | No sharing with registered effects. | Use a dedicated registry and caches. |
| **Device Compatibility** | Some WGSL may not be supported. | Validate against device capabilities. |

---

## Example: Sinusoidal Wave Effect

### WGSL Source

```wgsl
@group(0) @binding(0)
var<uniform> time: f32;
@group(0) @binding(1)
var<uniform> resolution: vec2<f32>;
@group(0) @binding(2)
var<uniform> amplitude: f32;
@group(0) @binding(3)
var<uniform> frequency: f32;

@fragment
fn main(@location(0) uv: vec2<f32>) -> @location(0) vec4<f32> {
    let normalizedUv = uv * 2.0 - 1.0; // [-1, 1]
    let waveOffset = sin(normalizedUv.y * frequency + time) * amplitude;
    let distortedUv = normalizedUv + vec2<f32>(waveOffset, 0.0);
    let hue = atan2(distortedUv.y, distortedUv.x) * 0.5 + 0.5;
    return vec4<f32>(hue, 0.8, 0.9, 1.0);
}
```

### Kotlin Registration

```kotlin
val customRegistry = GPUCustomRuntimeEffectRegistry(
    validator = WGSLValidator(),
    reflectionProvider = WGSLReflectionProvider(),
)

val result = customRegistry.registerCustomEffect(
    source = sinusoidalWaveWGSL,
    uniformSchema = GPURuntimeEffectUniformSchema(
        uniforms = listOf(
            GPUUniform("time", GPUUniformType.F32),
            GPUUniform("resolution", GPUUniformType.VEC2_F32),
            GPUUniform("amplitude", GPUUniformType.F32),
            GPUUniform("frequency", GPUUniformType.F32),
        )
    ),
    childSlots = emptyList(),
    sourceProvenance = "example.sinusoidal_wave",
)

if (result.isSuccess) {
    val effectId = result.getOrThrow()
    // Use effectId in a material or filter
}
```

---

## Roadmap

| Step | Description | Priority |
|------|-------------|----------|
| 1 | Finalize `34-custom-runtime-effect-pipeline.md` and `35-custom-runtime-effect-security.md`. | P0 |
| 2 | Implement `GPUCustomRuntimeEffectRegistry` in `gpu-renderer`. | P0 |
| 3 | Integrate `wgsl4k` for validation and reflection. | P0 |
| 4 | Modify `GPUMaterialDictionary` and `GPUFilterEffectPipeline` to support custom effects. | P1 |
| 5 | Add tests for valid/invalid custom WGSL. | P1 |
| 6 | Document the public API for users. | P2 |

---

## Open Questions

1. Should custom effects support **live editing** (like registered effects in M87)?
   - *Proposal*: No, to avoid complexity. Custom effects are static after registration.
2. Should custom effects be **serializable** (e.g., saved to disk)?
   - *Proposal*: Yes, but only if the WGSL source is preserved (not just the compiled module).
3. Should custom effects support **child shaders**?
   - *Proposal*: Yes, but with stricter validation (e.g., no recursive child chains).

---

## Revision History

| Date | Author | Changes |
|------|--------|---------|
| 2026-06-27 | Vibe | Initial draft. |
