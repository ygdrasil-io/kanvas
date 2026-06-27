# Custom Runtime Effect Security

Status: Draft
Date: 2026-06-27

## Purpose

This spec defines **security constraints** for custom WGSL runtime effects in the GPU-first renderer. It ensures that user-provided WGSL:

1. **Cannot compromise system stability** (e.g., no infinite loops, no resource exhaustion).
2. **Cannot access unauthorized resources** (e.g., no external textures, no device memory corruption).
3. **Is validated before execution** (via `wgsl4k` and additional Kanvas checks).
4. **Provides stable diagnostics** for security violations.

This spec is a **hard requirement** for `34-custom-runtime-effect-pipeline.md`.

---

## Source Specs

This spec depends on:

- `34-custom-runtime-effect-pipeline.md` for the custom runtime effect pipeline.
- `11-wgsl-layout-binding-abi.md` for binding and resource layout rules.
- `13-performance-telemetry-cache-gates.md` for resource budgets.

---

## Threat Model

### Assumptions

- **User-provided WGSL is untrusted**: Treat all custom WGSL as potentially malicious.
- **WebGPU is sandboxed**: The underlying WebGPU implementation (e.g., browser, native) provides basic isolation.
- **Kanvas controls the execution environment**: We can enforce limits on resources, time, and features.

### Threats

| Threat | Description | Mitigation |
|--------|-------------|------------|
| **Resource Exhaustion** | Shader uses excessive textures, buffers, or uniforms. | Enforce strict resource limits. |
| **Infinite Loops** | Shader contains loops that never terminate. | WebGPU timeouts + Kanvas validation. |
| **Memory Corruption** | Shader writes to invalid memory (e.g., out-of-bounds). | Validate all buffer/texture accesses. |
| **Denial of Service** | Shader crashes the GPU driver or device. | Isolate custom effects in a separate pipeline. |
| **Information Leakage** | Shader reads sensitive data (e.g., other textures). | Restrict texture/buffer access to declared inputs. |
| **Unsupported Features** | Shader uses features not supported by the device. | Validate against device capabilities. |

---

## Blocked WGSL Features

The following WGSL features **must be blocked** for custom effects:

### Storage and Memory

| Feature | Reason | Diagnostic Code |
|---------|--------|------------------|
| `storageBuffer` without bounds | Can lead to memory corruption. | `custom-wgsl.unsafe-storage-buffer` |
| `read_write` storage buffers | Allows arbitrary memory writes. | `custom-wgsl.unsafe-read-write-buffer` |
| `atomic` operations | Can cause race conditions or undefined behavior. | `custom-wgsl.unsafe-atomic` |
| `ptr` operations | Low-level memory manipulation. | `custom-wgsl.unsafe-ptr` |

### Control Flow

| Feature | Reason | Diagnostic Code |
|---------|--------|------------------|
| Recursive functions | Can cause stack overflow. | `custom-wgsl.unsafe-recursion` |
| Unbounded loops | Can cause infinite execution. | `custom-wgsl.unsafe-loop` |

### Resource Access

| Feature | Reason | Diagnostic Code |
|---------|--------|------------------|
| `textureSample` with dynamic coordinates | Can sample arbitrary textures. | `custom-wgsl.unsafe-dynamic-sampling` |
| `textureStore` | Can modify textures arbitrarily. | `custom-wgsl.unsafe-texture-store` |
| `bindGroup` with dynamic indices | Can access unbound resources. | `custom-wgsl.unsafe-dynamic-binding` |

### Other Features

| Feature | Reason | Diagnostic Code |
|---------|--------|------------------|
| `rayQuery` | Ray tracing is not supported for custom effects. | `custom-wgsl.unsafe-ray-query` |
| `compute` shaders | Compute shaders require additional validation. | `custom-wgsl.unsafe-compute` |
| `workgroup` builtins | Can cause undefined behavior if misused. | `custom-wgsl.unsafe-workgroup` |

---

## Resource Limits

Custom effects **must not exceed** the following limits:

### Uniforms and Bindings

| Resource | Limit | Diagnostic Code |
|----------|-------|------------------|
| Uniforms | 16 | `custom-wgsl.uniform-count-exceeded` |
| Bind groups | 4 | `custom-wgsl.bind-group-count-exceeded` |
| Bindings per group | 8 | `custom-wgsl.binding-count-exceeded` |
| Uniform buffer size | 16 KB | `custom-wgsl.uniform-buffer-size-exceeded` |
| Storage buffer size | 64 KB | `custom-wgsl.storage-buffer-size-exceeded` |

### Textures and Samplers

| Resource | Limit | Diagnostic Code |
|----------|-------|------------------|
| Textures | 8 | `custom-wgsl.texture-count-exceeded` |
| Samplers | 4 | `custom-wgsl.sampler-count-exceeded` |
| Texture dimensions | 4096x4096 | `custom-wgsl.texture-dimension-exceeded` |

### Control Flow

| Resource | Limit | Diagnostic Code |
|----------|-------|------------------|
| Loop iterations | 1024 | `custom-wgsl.loop-iteration-exceeded` |
| Function depth | 8 | `custom-wgsl.function-depth-exceeded` |

---

## Validation Rules

### Static Validation (via `wgsl4k`)

1. **Parse the WGSL**: Ensure it is syntactically valid.
2. **Check for blocked features**: Scan the AST for disallowed constructs (e.g., `atomic`, `storageBuffer` without bounds).
3. **Validate resource usage**: Count uniforms, textures, and bindings against limits.

### Dynamic Validation (Runtime)

1. **Device Capability Check**: Ensure the WGSL is supported by the target device.
2. **Resource Binding Validation**: Verify that all bindings are within declared limits.
3. **Uniform Packing**: Ensure uniforms fit within the allowed buffer sizes.

---

## Security Validation Implementation

### `WGSLSecurityValidator`

```kotlin
class WGSLSecurityValidator(
    private val deviceCapabilities: WGSLDeviceCapabilities,
) {
    fun validateSecurity(module: WGSLModule): WGSLSecurityValidationReport {
        val errors = mutableListOf<WGSLSecurityError>()

        // 1. Check for blocked features
        errors.addAll(checkBlockedFeatures(module))

        // 2. Check resource limits
        errors.addAll(checkResourceLimits(module))

        // 3. Check device capabilities
        errors.addAll(checkDeviceCapabilities(module, deviceCapabilities))

        return WGSLSecurityValidationReport(
            isSecure = errors.isEmpty(),
            errors = errors,
        )
    }

    private fun checkBlockedFeatures(module: WGSLModule): List<WGSLSecurityError> {
        val errors = mutableListOf<WGSLSecurityError>()

        // Check for atomic operations
        if (module.usesAtomics()) {
            errors.add(WGSLSecurityError(
                code = "custom-wgsl.unsafe-atomic",
                message = "Atomic operations are not allowed in custom WGSL",
                severity = WGSLSecurityErrorSeverity.ERROR,
            ))
        }

        // Check for unbounded storage buffers
        module.storageBuffers.forEach { buffer ->
            if (!buffer.hasExplicitSize()) {
                errors.add(WGSLSecurityError(
                    code = "custom-wgsl.unsafe-storage-buffer",
                    message = "Storage buffer '${buffer.name}' must have an explicit size",
                    severity = WGSLSecurityErrorSeverity.ERROR,
                ))
            }
        }

        // Check for read_write storage buffers
        module.storageBuffers.forEach { buffer ->
            if (buffer.accessMode == WGSLStorageAccessMode.READ_WRITE) {
                errors.add(WGSLSecurityError(
                    code = "custom-wgsl.unsafe-read-write-buffer",
                    message = "Read-write storage buffers are not allowed in custom WGSL",
                    severity = WGSLSecurityErrorSeverity.ERROR,
                ))
            }
        }

        // Check for ptr operations
        if (module.usesPtrOperations()) {
            errors.add(WGSLSecurityError(
                code = "custom-wgsl.unsafe-ptr",
                message = "Pointer operations are not allowed in custom WGSL",
                severity = WGSLSecurityErrorSeverity.ERROR,
            ))
        }

        // Check for recursive functions
        if (module.hasRecursiveFunctions()) {
            errors.add(WGSLSecurityError(
                code = "custom-wgsl.unsafe-recursion",
                message = "Recursive functions are not allowed in custom WGSL",
                severity = WGSLSecurityErrorSeverity.ERROR,
            ))
        }

        return errors
    }

    private fun checkResourceLimits(module: WGSLModule): List<WGSLSecurityError> {
        val errors = mutableListOf<WGSLSecurityError>()

        // Check uniform count
        if (module.uniforms.size > MAX_UNIFORMS) {
            errors.add(WGSLSecurityError(
                code = "custom-wgsl.uniform-count-exceeded",
                message = "Shader uses ${module.uniforms.size} uniforms (max: $MAX_UNIFORMS)",
                severity = WGSLSecurityErrorSeverity.ERROR,
            ))
        }

        // Check texture count
        if (module.textures.size > MAX_TEXTURES) {
            errors.add(WGSLSecurityError(
                code = "custom-wgsl.texture-count-exceeded",
                message = "Shader uses ${module.textures.size} textures (max: $MAX_TEXTURES)",
                severity = WGSLSecurityErrorSeverity.ERROR,
            ))
        }

        // Check bind group count
        if (module.bindGroups.size > MAX_BIND_GROUPS) {
            errors.add(WGSLSecurityError(
                code = "custom-wgsl.bind-group-count-exceeded",
                message = "Shader uses ${module.bindGroups.size} bind groups (max: $MAX_BIND_GROUPS)",
                severity = WGSLSecurityErrorSeverity.ERROR,
            ))
        }

        return errors
    }

    private fun checkDeviceCapabilities(
        module: WGSLModule,
        capabilities: WGSLDeviceCapabilities,
    ): List<WGSLSecurityError> {
        val errors = mutableListOf<WGSLSecurityError>()

        // Check for unsupported features
        if (module.usesRayQuery() && !capabilities.supportsRayQuery) {
            errors.add(WGSLSecurityError(
                code = "custom-wgsl.unsafe-ray-query",
                message = "Device does not support ray query",
                severity = WGSLSecurityErrorSeverity.ERROR,
            ))
        }

        return errors
    }
}

data class WGSLSecurityValidationReport(
    val isSecure: Boolean,
    val errors: List<WGSLSecurityError>,
)

data class WGSLSecurityError(
    val code: String,
    val message: String,
    val severity: WGSLSecurityErrorSeverity,
)

enum class WGSLSecurityErrorSeverity {
    ERROR,   // Must refuse
    WARNING, // Log but allow
}

// Constants for resource limits
const val MAX_UNIFORMS = 16
const val MAX_TEXTURES = 8
const val MAX_BIND_GROUPS = 4
const val MAX_BINDINGS_PER_GROUP = 8
const val MAX_UNIFORM_BUFFER_SIZE = 16 * 1024 // 16 KB
const val MAX_STORAGE_BUFFER_SIZE = 64 * 1024 // 64 KB
```

---

## Integration with Custom Effect Pipeline

### Modified `GPUCustomRuntimeEffectRegistry`

```kotlin
class GPUCustomRuntimeEffectRegistry(
    private val validator: WGSLValidator, // wgsl4k
    private val reflectionProvider: WGSLReflectionProvider,
    private val securityValidator: WGSLSecurityValidator, // NEW
    private val deviceCapabilities: WGSLDeviceCapabilities, // NEW
) {
    // ... existing code ...

    fun registerCustomEffect(
        source: String,
        uniformSchema: GPURuntimeEffectUniformSchema,
        childSlots: List<GPURuntimeEffectChildSlotPlan>,
        sourceProvenance: String,
    ): Result<GPUCustomRuntimeEffectID> {
        // 1. Parse and validate syntax via wgsl4k
        val module = validator.parse(source)
        if (module.hasErrors) {
            return Result.failure(GPUCustomRuntimeEffectValidationError(
                code = "custom-wgsl.syntax-error",
                message = "WGSL syntax error: ${module.errors.joinToString()}",
            ))
        }

        // 2. Validate security
        val securityReport = securityValidator.validateSecurity(module)
        if (!securityReport.isSecure) {
            return Result.failure(GPUCustomRuntimeEffectValidationError(
                code = securityReport.errors.first().code,
                message = "WGSL security validation failed: ${securityReport.errors.joinToString()}",
            ))
        }

        // 3. Validate resource limits
        if (module.uniforms.size > MAX_UNIFORMS) {
            return Result.failure(GPUCustomRuntimeEffectValidationError(
                code = "custom-wgsl.uniform-count-exceeded",
                message = "Shader uses ${module.uniforms.size} uniforms (max: $MAX_UNIFORMS)",
            ))
        }

        // 4. Extract reflection and create descriptor
        val reflection = reflectionProvider.reflect(source)
        val wgslPlan = GPUCustomRuntimeEffectWGSLPlan(
            source = source,
            entryPoint = "main",
            reflection = reflection,
            validationReport = WGSLValidationReport(
                isValid = true,
                diagnostics = emptyList(),
            ),
        )

        val descriptor = GPUCustomRuntimeEffectDescriptor(
            id = generateCustomEffectID(source, uniformSchema, childSlots),
            uniformSchema = uniformSchema,
            childSlots = childSlots,
            wgslPlan = wgslPlan,
            sourceProvenance = sourceProvenance,
            validationStatus = GPUCustomRuntimeEffectValidationStatus.VALID,
            cpuOracle = null,
        )

        descriptors[descriptor.id] = descriptor
        return Result.success(descriptor.id)
    }
}
```

---

## Device Capabilities

### `WGSLDeviceCapabilities`

```kotlin
data class WGSLDeviceCapabilities(
    val supportsRayQuery: Boolean = false,
    val supportsStorageBuffers: Boolean = true,
    val supportsAtomics: Boolean = false,
    val maxTextureDimensions: Int = 4096,
    val maxUniformBufferSize: Int = MAX_UNIFORM_BUFFER_SIZE,
    val maxStorageBufferSize: Int = MAX_STORAGE_BUFFER_SIZE,
    // ... other capabilities ...
)
```

---

## Example: Secure vs. Insecure WGSL

### ✅ Secure WGSL Example

```wgsl
// Safe: Uses only allowed features and stays within limits
@group(0) @binding(0)
var<uniform> time: f32;
@group(0) @binding(1)
var<uniform> resolution: vec2<f32>;

@fragment
fn main(@location(0) uv: vec2<f32>) -> @location(0) vec4<f32> {
    let normalizedUv = uv * 2.0 - 1.0;
    let waveOffset = sin(normalizedUv.y * 10.0 + time) * 0.1;
    let distortedUv = normalizedUv + vec2<f32>(waveOffset, 0.0);
    return vec4<f32>(distortedUv * 0.5 + 0.5, 1.0);
}
```

### ❌ Insecure WGSL Examples

#### Example 1: Atomic Operations

```wgsl
// BLOCKED: Uses atomic operations
@group(0) @binding(0)
var<storage, read_write> counter: atomic<u32>;

@compute @workgroup_size(1)
fn main() {
    atomicAdd(&counter, 1u); // ERROR: custom-wgsl.unsafe-atomic
}
```

#### Example 2: Unbounded Storage Buffer

```wgsl
// BLOCKED: Storage buffer without explicit size
@group(0) @binding(0)
var<storage, read_write> data: array<u32>; // ERROR: custom-wgsl.unsafe-storage-buffer

@compute @workgroup_size(1)
fn main() {
    data[0] = 42u;
}
```

#### Example 3: Too Many Textures

```wgsl
// BLOCKED: Exceeds texture limit (max: 8)
@group(0) @binding(0) var tex0: texture_2d<f32>;
@group(0) @binding(1) var tex1: texture_2d<f32>;
@group(0) @binding(2) var tex2: texture_2d<f32>;
@group(0) @binding(3) var tex3: texture_2d<f32>;
@group(0) @binding(4) var tex4: texture_2d<f32>;
@group(0) @binding(5) var tex5: texture_2d<f32>;
@group(0) @binding(6) var tex6: texture_2d<f32>;
@group(0) @binding(7) var tex7: texture_2d<f32>;
@group(0) @binding(8) var tex8: texture_2d<f32>; // ERROR: custom-wgsl.texture-count-exceeded

@fragment
fn main(@location(0) uv: vec2<f32>) -> @location(0) vec4<f32> {
    return textureSample(tex0, ...);
}
```

---

## Testing Security Validation

### Unit Tests

```kotlin
class WGSLSecurityValidatorTest {
    @Test
    fun `test atomic operations are blocked`() {
        val wgsl = """
            @group(0) @binding(0)
            var<storage, read_write> counter: atomic<u32>;
            @compute @workgroup_size(1)
            fn main() { atomicAdd(&counter, 1u); }
        """.trimIndent()
        
        val module = parseWGSL(wgsl)
        val validator = WGSLSecurityValidator(deviceCapabilities = WGSLDeviceCapabilities())
        val report = validator.validateSecurity(module)
        
        assertFalse(report.isSecure)
        assertTrue(report.errors.any { it.code == "custom-wgsl.unsafe-atomic" })
    }

    @Test
    fun `test unbounded storage buffer is blocked`() {
        val wgsl = """
            @group(0) @binding(0)
            var<storage, read_write> data: array<u32>;
            @compute @workgroup_size(1)
            fn main() { data[0] = 42u; }
        """.trimIndent()
        
        val module = parseWGSL(wgsl)
        val validator = WGSLSecurityValidator(deviceCapabilities = WGSLDeviceCapabilities())
        val report = validator.validateSecurity(module)
        
        assertFalse(report.isSecure)
        assertTrue(report.errors.any { it.code == "custom-wgsl.unsafe-storage-buffer" })
    }

    @Test
    fun `test secure WGSL passes validation`() {
        val wgsl = """
            @group(0) @binding(0)
            var<uniform> time: f32;
            @fragment
            fn main(@location(0) uv: vec2<f32>) -> @location(0) vec4<f32> {
                return vec4<f32>(uv, 0.5, 1.0);
            }
        """.trimIndent()
        
        val module = parseWGSL(wgsl)
        val validator = WGSLSecurityValidator(deviceCapabilities = WGSLDeviceCapabilities())
        val report = validator.validateSecurity(module)
        
        assertTrue(report.isSecure)
        assertTrue(report.errors.isEmpty())
    }
}
```

---

## Performance Impact

| Check | Overhead | Mitigation |
|-------|----------|------------|
| **Static Validation** | ~1-5ms per shader (via `wgsl4k`). | Cache validation results. |
| **Security Validation** | ~0.1-1ms per shader. | Run in parallel with parsing. |
| **Resource Limits** | Negligible. | Pre-compute limits. |
| **Device Capabilities** | Negligible. | Query once per device. |

---

## Open Questions

1. Should we allow **compute shaders** for custom effects?
   - *Proposal*: No, to avoid complexity. Only fragment/vertex shaders.
2. Should we support **dynamic bindings** (e.g., `@binding(@location(0))`)?
   - *Proposal*: No, to ensure static validation.
3. Should we **log warnings** for non-critical issues (e.g., unused uniforms)?
   - *Proposal*: Yes, but allow execution.

---

## Revision History

| Date | Author | Changes |
|------|--------|---------|
| 2026-06-27 | Vibe | Initial draft. |
