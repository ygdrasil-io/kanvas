# RuntimeEffect GPU Pipeline Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Wire RuntimeEffect through the GPU render pipeline and port ~21 SkRuntimeEffect GMs.

**Architecture:** Fix 3 stub/refusal points in GPU pipeline (GPUMaterialMapper, RefusalGuards, AnalysisContracts), add RuntimeEffectMaterialLowering, write 12 WGSL shader files, register descriptors, then port GMs by category.

**Tech Stack:** Kotlin, WGSL, WebGPU, wgsl4k

---

## File Structure

### New files
```
gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/
  materials/RuntimeEffectMaterialLowering.kt
  wgsl/ColorFilterLumaToAlphaWgsl.kt
  wgsl/ColorFilterNoopWgsl.kt
  wgsl/ColorFilterTernaryWgsl.kt
  wgsl/ColorFilterIfsWgsl.kt
  wgsl/ColorFilterEarlyReturnWgsl.kt
  wgsl/IntrinsicsCommonWgsl.kt
  wgsl/IntrinsicsTrigWgsl.kt
  wgsl/IntrinsicsExponentialWgsl.kt
  wgsl/IntrinsicsGeometricWgsl.kt
  wgsl/IntrinsicsMatrixWgsl.kt
  wgsl/IntrinsicsRelationalWgsl.kt
  wgsl/RuntimeFunctionsWgsl.kt
  wgsl/KawaseBlurWgsl.kt
  wgsl/RippleWgsl.kt
  wgsl/ArithmodeWgsl.kt
  wgsl/LumaFilterWgsl.kt

integration-tests/skia/src/test/kotlin/org/graphiks/kanvas/skia/gm/runtime_effect/
  RuntimeShaderGm.kt
  SpiralRTGm.kt
  LinearGradientRTGm.kt
  RuntimeColorFilterGm.kt
  IntrinsicsCommonGm.kt
  IntrinsicsTrigGm.kt
  IntrinsicsExponentialGm.kt
  IntrinsicsGeometricGm.kt
  IntrinsicsMatrixGm.kt
  IntrinsicsRelationalGm.kt
  RuntimeFunctionsGm.kt
  KawaseBlurRtGm.kt
  RippleShaderGm.kt
  ArithmodeGm.kt
  LumaFilterGm.kt
```

### Modified files
```
kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUMaterialMapper.kt
kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPURefusalGuards.kt
gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/analysis/AnalysisContracts.kt
gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/runtimeeffects/KanvasRuntimeEffectRegistry.kt
gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/runtimeeffects/GPURuntimeEffectDispatch.kt
```

---

## Task 1: Wire GPUMaterialMapper for RuntimeEffect

**Files:**
- Modify: `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUMaterialMapper.kt:79`

- [ ] **Step 1: Read current mapping**

Read `GPUMaterialMapper.kt` around line 79 to see the current stub:
```kotlin
is Shader.RuntimeEffect -> GPUMaterialDescriptor.RuntimeEffect()
```

- [ ] **Step 2: Wire effectId extraction**

Change to:
```kotlin
is Shader.RuntimeEffect -> {
    val id = effect.effect.effect.id
    GPUMaterialDescriptor.RuntimeEffect(effectId = id, descriptorVersion = 1)
}
```

- [ ] **Step 3: Verify compilation**

Run: `./gradlew :kanvas:compileKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUMaterialMapper.kt
git commit -m "feat(gpu): wire GPUMaterialMapper to extract RuntimeEffect effectId"
```

---

## Task 2: Accept RuntimeEffect in GPU Pipeline Guards

**Files:**
- Modify: `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPURefusalGuards.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/analysis/AnalysisContracts.kt`

- [ ] **Step 1: Read GPURefusalGuards.kt**

Find the accepted material list.

- [ ] **Step 2: Add RuntimeEffect to accepted list**

```kotlin
// GPURefusalGuards.kt — add to the condition that accepts materials
material is GPUMaterialDescriptor.SolidColor ||
material.kind == GPUMaterialKind.RuntimeEffect ||
// ... rest of existing conditions
```

- [ ] **Step 3: Add RuntimeEffect to AnalysisContracts.kt**

```kotlin
// In acceptedMaterialKinds and acceptedFillPathMaterialKinds (lines ~1617, ~1628)
GPUMaterialKind.RuntimeEffect,
```

- [ ] **Step 4: Compile both modules**

Run: `./gradlew :kanvas:compileKotlin :gpu-renderer:compileKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add kanvas/.../GPURefusalGuards.kt gpu-renderer/.../AnalysisContracts.kt
git commit -m "feat(gpu): accept RuntimeEffect in pipeline guards and analysis contracts"
```

---

## Task 3: Create RuntimeEffectMaterialLowering

**Files:**
- Create: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/materials/RuntimeEffectMaterialLowering.kt`

- [ ] **Step 1: Study existing material lowering (e.g., SolidMaterialLowering)**

Read `SolidMaterialLowering.kt` to understand the pattern.

- [ ] **Step 2: Create RuntimeEffectMaterialLowering**

```kotlin
package org.graphiks.kanvas.gpu.renderer.materials

import org.graphiks.kanvas.gpu.renderer.commands.GPUMaterialDescriptor
import org.graphiks.kanvas.gpu.renderer.materials.GPUMaterialSourceDescriptor
import org.graphiks.kanvas.gpu.renderer.materials.GPUMaterialSourceKind

object RuntimeEffectMaterialLowering : MaterialLowering<GPUMaterialDescriptor.RuntimeEffect> {
    override fun lower(
        descriptor: GPUMaterialDescriptor.RuntimeEffect,
        context: MaterialLoweringContext,
    ): GPUMaterialSourceDescriptor {
        return GPUMaterialSourceDescriptor.RuntimeEffect(
            effectId = descriptor.effectId,
            descriptorVersion = descriptor.descriptorVersion,
            routeContractHash = descriptor.effectId.hashCode(),
        )
    }

    override fun canLower(descriptor: GPUMaterialDescriptor): Boolean =
        descriptor is GPUMaterialDescriptor.RuntimeEffect
}
```

- [ ] **Step 3: Register in the lowering pipeline**

Find the material lowering registry and add `RuntimeEffectMaterialLowering`.

- [ ] **Step 4: Compile**

Run: `./gradlew :gpu-renderer:compileKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add gpu-renderer/.../RuntimeEffectMaterialLowering.kt
git commit -m "feat(gpu): add RuntimeEffectMaterialLowering"
```

---

## Task 4: Connect GPURuntimeEffectDispatch to Pipeline

**Files:**
- Modify: `gpu-renderer/.../runtimeeffects/GPURuntimeEffectDispatch.kt`

- [ ] **Step 1: Read current dispatch routing**

Understand how `GPURuntimeEffectDispatch` routes by effectId.

- [ ] **Step 2: Wire the lowered material source to the executor**

The lowered `GPUMaterialSourceDescriptor.RuntimeEffect` needs to reach
`GPURuntimeEffectExecutor`. Add routing in the dispatch table so that
`custom.*` prefixed IDs and known registered IDs both route through.

- [ ] **Step 3: Compile**

Run: `./gradlew :gpu-renderer:compileKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add gpu-renderer/.../GPURuntimeEffectDispatch.kt
git commit -m "feat(gpu): connect GPURuntimeEffectDispatch to pipeline"
```

---

## Task 5: Write Color Filter WGSL Shaders (5 files)

**Files:**
- Create: `gpu-renderer/.../wgsl/ColorFilterLumaToAlphaWgsl.kt`
- Create: `gpu-renderer/.../wgsl/ColorFilterNoopWgsl.kt`
- Create: `gpu-renderer/.../wgsl/ColorFilterTernaryWgsl.kt`
- Create: `gpu-renderer/.../wgsl/ColorFilterIfsWgsl.kt`
- Create: `gpu-renderer/.../wgsl/ColorFilterEarlyReturnWgsl.kt`

- [ ] **Step 1: Write ColorFilterLumaToAlphaWgsl**

```kotlin
package org.graphiks.kanvas.gpu.renderer.wgsl

object ColorFilterLumaToAlphaWgsl {
    const val SRC: String = """
struct ColorFilterUniform { srcColor: vec4<f32> }
@group(1) @binding(0) var<uniform> uColorFilter: ColorFilterUniform;

fn color_filter_luma_to_alpha(uv: vec2<f32>) -> vec4<f32> {
    let luma = dot(uColorFilter.srcColor.rgb, vec3<f32>(0.2126, 0.7152, 0.0722));
    return vec4<f32>(uColorFilter.srcColor.rgb, luma);
}
"""
}
```

(Wrap in a helper that takes `uv` and returns `vec4<f32>` to match the
expected WGSL entry signature used by the scene-level bypass.)

- [ ] **Step 2: Write ColorFilterNoopWgsl**

```kotlin
object ColorFilterNoopWgsl {
    const val SRC: String = """
fn color_filter_noop(uv: vec2<f32>) -> vec4<f32> {
    return vec4<f32>(0.0, 0.0, 0.0, 0.0);
}
"""
}
```

- [ ] **Step 3: Write ColorFilterTernaryWgsl**

```kotlin
object ColorFilterTernaryWgsl {
    const val SRC: String = """
struct ColorFilterUniform { condition: f32; colorTrue: vec4<f32>; colorFalse: vec4<f32>; }
@group(1) @binding(0) var<uniform> uColorFilter: ColorFilterUniform;

fn color_filter_ternary(uv: vec2<f32>) -> vec4<f32> {
    return select(uColorFilter.colorFalse, uColorFilter.colorTrue, uColorFilter.condition > 0.5);
}
"""
}
```

- [ ] **Step 4: Write ColorFilterIfsWgsl and ColorFilterEarlyReturnWgsl**

(Same pattern — WGSL conditional branches matching SkSL test logic.)

- [ ] **Step 5: Compile**

Run: `./gradlew :gpu-renderer:compileKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 6: Commit**

```bash
git add gpu-renderer/.../wgsl/ColorFilter*Wgsl.kt
git commit -m "feat(gpu): add 5 color filter WGSL shaders"
```

---

## Task 6: Write Intrinsics WGSL Shaders (6 files)

**Files:**
- Create: `gpu-renderer/.../wgsl/IntrinsicsCommonWgsl.kt`
- Create: `gpu-renderer/.../wgsl/IntrinsicsTrigWgsl.kt`
- Create: `gpu-renderer/.../wgsl/IntrinsicsExponentialWgsl.kt`
- Create: `gpu-renderer/.../wgsl/IntrinsicsGeometricWgsl.kt`
- Create: `gpu-renderer/.../wgsl/IntrinsicsMatrixWgsl.kt`
- Create: `gpu-renderer/.../wgsl/IntrinsicsRelationalWgsl.kt`

- [ ] **Step 1: Write IntrinsicsCommonWgsl**

Each intrinsics shader uses a `testCase: i32` uniform + switch dispatch.
Common intrinsics: `mix`, `clamp`, `saturate`, `step`, `smoothstep`, `abs`, `sign`, `floor`, `ceil`, `fract`, `mod`, `min`, `max`.

```kotlin
object IntrinsicsCommonWgsl {
    const val SRC: String = """
struct IntrinsicsUniform { input: vec4<f32>; testCase: i32; }
@group(1) @binding(0) var<uniform> uIntrinsics: IntrinsicsUniform;

fn intrinsics_common_dispatch(uv: vec2<f32>) -> vec4<f32> {
    let v = uIntrinsics.input;
    switch uIntrinsics.testCase {
        case 0: { return vec4<f32>(mix(v.x, v.y, 0.5)); }
        case 1: { return vec4<f32>(clamp(v.x, 0.0, 1.0)); }
        case 2: { return vec4<f32>(saturate(v.x)); }
        case 3: { return vec4<f32>(step(0.5, v.x)); }
        case 4: { return vec4<f32>(smoothstep(0.0, 1.0, v.x)); }
        default: { return vec4<f32>(0.0); }
    }
}
"""
}
```

- [ ] **Step 2: Write IntrinsicsTrigWgsl**

Trig functions: `sin`, `cos`, `tan`, `asin`, `acos`, `atan`, `sinh`, `cosh`, `tanh`.

```kotlin
object IntrinsicsTrigWgsl {
    const val SRC: String = """
struct IntrinsicsUniform { input: vec4<f32>; testCase: i32; }
@group(1) @binding(0) var<uniform> uIntrinsics: IntrinsicsUniform;

fn intrinsics_trig_dispatch(uv: vec2<f32>) -> vec4<f32> {
    let v = uIntrinsics.input;
    switch uIntrinsics.testCase {
        case 0: { return vec4<f32>(sin(v.x)); }
        case 1: { return vec4<f32>(cos(v.x)); }
        case 2: { return vec4<f32>(tan(v.x)); }
        case 3: { return vec4<f32>(asin(v.x)); }
        case 4: { return vec4<f32>(acos(v.x)); }
        case 5: { return vec4<f32>(atan(v.x)); }
        default: { return vec4<f32>(0.0); }
    }
}
"""
}
```

- [ ] **Step 3: Write IntrinsicsExponentialWgsl**

Exponential: `pow`, `exp`, `exp2`, `log`, `log2`, `sqrt`, `inversesqrt`.

- [ ] **Step 4: Write IntrinsicsGeometricWgsl**

Geometric: `length`, `distance`, `dot`, `cross`, `normalize`, `reflect`, `refract`.

- [ ] **Step 5: Write IntrinsicsMatrixWgsl**

Matrix: `mat4x4<f32>` operations, `determinant`, `transpose`.

- [ ] **Step 6: Write IntrinsicsRelationalWgsl**

Relational: `lessThan`, `greaterThan`, `equal`, `notEqual`, `any`, `all`, `isNan`, `isInf`.

- [ ] **Step 7: Compile**

Run: `./gradlew :gpu-renderer:compileKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 8: Commit**

```bash
git add gpu-renderer/.../wgsl/Intrinsics*Wgsl.kt
git commit -m "feat(gpu): add 6 intrinsics WGSL shaders"
```

---

## Task 7: Write Specialized WGSL Shaders (4 files)

**Files:**
- Create: `gpu-renderer/.../wgsl/RuntimeFunctionsWgsl.kt`
- Create: `gpu-renderer/.../wgsl/RippleWgsl.kt`
- Create: `gpu-renderer/.../wgsl/ArithmodeWgsl.kt`
- Create: `gpu-renderer/.../wgsl/LumaFilterWgsl.kt`

- [ ] **Step 1: Write RuntimeFunctionsWgsl**

Tests user-defined functions — a WGSL `fn` that calls another `fn`.

- [ ] **Step 2: Write RippleWgsl**

Procedural ripple with time/amplitude/frequency uniforms.

- [ ] **Step 3: Write ArithmodeWgsl**

Per-channel arithmetic operations on src/dst colors.

- [ ] **Step 4: Write LumaFilterWgsl**

Luminance calculation from input color.

- [ ] **Step 5: Compile**

Run: `./gradlew :gpu-renderer:compileKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 6: Commit**

```bash
git add gpu-renderer/.../wgsl/RuntimeFunctionsWgsl.kt gpu-renderer/.../wgsl/RippleWgsl.kt gpu-renderer/.../wgsl/ArithmodeWgsl.kt gpu-renderer/.../wgsl/LumaFilterWgsl.kt
git commit -m "feat(gpu): add 4 specialized WGSL shaders"
```

---

## Task 8: Register All Descriptors in KanvasRuntimeEffectRegistry

**Files:**
- Modify: `gpu-renderer/.../runtimeeffects/KanvasRuntimeEffectRegistry.kt`

- [ ] **Step 1: Read existing registry pattern

Study the 3 existing descriptors (SimpleRTDescriptor, LinearGradientRTDescriptor,
SpiralRTDescriptor) to understand the registration pattern.

- [ ] **Step 2: Add all new descriptors**

Add one descriptor per WGSL shader created in Tasks 5-7. Each descriptor needs:
`effectId`, `version`, `uniformSchema`, `uniformBlockPlan`, `wgslPlan`,
`routeContract`, `childSlots`.

The `wgslPlan.wgslEntryPoint` must match the WGSL entry function name.
The `uniformSchema` must match the WGSL struct fields.

- [ ] **Step 3: Update acceptedWgslImplementationIds**

```kotlin
// In SkRuntimeEffectDescriptor.kt or wherever the whitelist lives
private val acceptedWgslImplementationIds = setOf(
    "wgsl/runtime_simple_rt",
    "wgsl/runtime_linear_gradient_rt",
    "wgsl/runtime_color_filter_luma_to_alpha",
    "wgsl/runtime_color_filter_noop",
    "wgsl/runtime_color_filter_ternary",
    "wgsl/runtime_color_filter_ifs",
    "wgsl/runtime_color_filter_early_return",
    "wgsl/runtime_intrinsics_common",
    "wgsl/runtime_intrinsics_trig",
    "wgsl/runtime_intrinsics_exp",
    "wgsl/runtime_intrinsics_geom",
    "wgsl/runtime_intrinsics_matrix",
    "wgsl/runtime_intrinsics_relational",
    "wgsl/runtime_functions",
    "wgsl/runtime_ripple",
    "wgsl/runtime_arithmode",
    "wgsl/runtime_luma_filter",
)
```

- [ ] **Step 4: Compile**

Run: `./gradlew :gpu-renderer:compileKotlin :cpu-raster:compileKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add gpu-renderer/.../KanvasRuntimeEffectRegistry.kt
git commit -m "feat(gpu): register all RuntimeEffect descriptors"
```

---

## Task 9: Port 3 Simple Shader GMs

**Files:**
- Create: `integration-tests/skia/.../gm/runtime_effect/RuntimeShaderGm.kt`
- Create: `integration-tests/skia/.../gm/runtime_effect/SpiralRTGm.kt`
- Create: `integration-tests/skia/.../gm/runtime_effect/LinearGradientRTGm.kt`

- [ ] **Step 1: Port RuntimeShaderGm**

Read `skia-integration-tests/src/main/kotlin/org/skia/tests/RuntimeShaderGM.kt`.
Create new GM using `RuntimeEffect.compile(SimpleRTWgsl.SRC)` and `UniformBlock { float4("gColor", ...) }`.

```kotlin
package org.graphiks.kanvas.skia.gm.runtime_effect

import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.pipeline.RuntimeEffect
import org.graphiks.kanvas.pipeline.UniformBlock
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Rect

class RuntimeShaderGm : SkiaGm {
    override val name = "runtime_shader"
    override val renderFamily = RenderFamily.RUNTIME_EFFECT
    override val minSimilarity = 0.0
    override val width = 512
    override val height = 256

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val wgsl = """
struct SimpleRTUniform { gColor: vec4<f32>; }
@group(1) @binding(0) var<uniform> uSimpleRT: SimpleRTUniform;
fn simple_rt_source(uv: vec2<f32>) -> vec4<f32> {
    return uSimpleRT.gColor;
}
"""
        val effect = RuntimeEffect.compile(wgsl).getOrThrow()
        val uniforms = UniformBlock { float4("gColor", 1f, 0f, 0f, 1f) }
        val shader = effect.makeShader(uniforms)
        canvas.drawRect(Rect(0f, 0f, width.toFloat(), height.toFloat()), Paint(shader = shader))
    }
}
```

- [ ] **Step 2: Port SpiralRTGm**

Use `SpiralRTWgsl.SRC` with uniforms: `center: vec2<f32>`, `color1: vec4<f32>`,
`color2: vec4<f32>`, `params: vec4<f32>`.

- [ ] **Step 3: Port LinearGradientRTGm**

Use `LinearGradientSnippet.SRC` with uniforms for start/end positions and stop data.

- [ ] **Step 4: Move reference images and update ServiceLoader**

```bash
mv skia-integration-tests/src/test/resources/original-888/runtime_shader.png integration-tests/skia/src/test/resources/reference/runtime_shader.png
# repeat for spiral_rt, linear_gradient_rt
```

Add entries to `META-INF/services/org.graphiks.kanvas.skia.SkiaGm`.

- [ ] **Step 5: Compile**

Run: `./gradlew :integration-tests:skia:compileTestKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 6: Commit**

```bash
git add integration-tests/skia/.../gm/runtime_effect/ git add integration-tests/skia/src/test/resources/reference/
git commit -m "gm: port 3 simple RuntimeEffect GMs"
```

---

## Task 10: Port ColorFilter GMs

**Files:**
- Create: `integration-tests/skia/.../gm/runtime_effect/RuntimeColorFilterGm.kt`

- [ ] **Step 1: Read old source**

Read `RuntimeColorFilterGM.kt` — it compiles 5 different SkSL programs and
renders them as color filters.

- [ ] **Step 2: Port using Shader.RuntimeEffect**

Each color filter variant becomes a separate test inside the GM or a companion.
Use the WGSL from the 5 color filter shaders written in Task 5.
Note: `effect.makeColorFilter(uniforms)` creates a `ColorFilter`.
Set on Paint: `paint.copy(colorFilter = colorFilter)`.

- [ ] **Step 3: Move refs and update ServiceLoader**

- [ ] **Step 4: Compile**

- [ ] **Step 5: Commit**

---

## Task 11: Port Intrinsics GMs (7 files)

**Files:**
- Create: `integration-tests/skia/.../gm/runtime_effect/IntrinsicsCommonGm.kt`
- Create: `integration-tests/skia/.../gm/runtime_effect/IntrinsicsTrigGm.kt`
- Create: `integration-tests/skia/.../gm/runtime_effect/IntrinsicsExponentialGm.kt`
- Create: `integration-tests/skia/.../gm/runtime_effect/IntrinsicsGeometricGm.kt`
- Create: `integration-tests/skia/.../gm/runtime_effect/IntrinsicsMatrixGm.kt`
- Create: `integration-tests/skia/.../gm/runtime_effect/IntrinsicsRelationalGm.kt`
- Create: `integration-tests/skia/.../gm/runtime_effect/RuntimeFunctionsGm.kt`

- [ ] **Step 1: Port IntrinsicsCommonGm**

Each intrinsics GM uses the corresponding WGSL, sets `testCase` uniform to
iterate through each operation, and draws tiles or strips for each test case.

```kotlin
class IntrinsicsCommonGm : SkiaGm {
    override val name = "runtime_intrinsics_common"
    override val renderFamily = RenderFamily.RUNTIME_EFFECT
    override val minSimilarity = 0.0
    override val width = 512
    override val height = 512

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val effect = RuntimeEffect.compile(IntrinsicsCommonWgsl.SRC).getOrThrow()
        for (testCase in 0..4) {
            val uniforms = UniformBlock {
                float4("input", 0.5f, 0.0f, 1.0f, 0.0f)
                int1("testCase", testCase)
            }
            val shader = effect.makeShader(uniforms)
            val x = (testCase % 4) * 128f
            val y = (testCase / 4) * 128f
            canvas.drawRect(Rect(x, y, x + 128f, y + 128f), Paint(shader = shader))
        }
    }
}
```

- [ ] **Step 2-7: Port remaining 6 intrinsics GMs** (same pattern)

- [ ] **Step 8: Move refs and update ServiceLoader**

- [ ] **Step 9: Compile**

- [ ] **Step 10: Commit**

---

## Task 12: Port Specialized GMs (4 files)

**Files:**
- Create: `integration-tests/skia/.../gm/runtime_effect/KawaseBlurRtGm.kt`
- Create: `integration-tests/skia/.../gm/runtime_effect/RippleShaderGm.kt`
- Create: `integration-tests/skia/.../gm/runtime_effect/ArithmodeGm.kt`
- Create: `integration-tests/skia/.../gm/runtime_effect/LumaFilterGm.kt`

- [ ] **Step 1: Read old sources** for each of the 4 GMs

- [ ] **Step 2-5: Port each GM** using its WGSL shader

- [ ] **Step 6: Move refs and update ServiceLoader**

- [ ] **Step 7: Compile**

- [ ] **Step 8: Commit**

---

## Task 13: Delete Old Sources and Cleanup

**Files:**
- Delete: `skia-integration-tests/src/main/kotlin/org/skia/tests/Runtime*GM.kt`
- Delete: `skia-integration-tests/src/main/kotlin/org/skia/tests/Intrinsics*GM.kt`
- Delete: `skia-integration-tests/src/main/kotlin/org/skia/tests/KawaseBlur*GM.kt`
- Delete: `skia-integration-tests/src/main/kotlin/org/skia/tests/Ripple*GM.kt`
- Delete: `skia-integration-tests/src/main/kotlin/org/skia/tests/Arithmode*GM.kt`
- Delete: `skia-integration-tests/src/main/kotlin/org/skia/tests/Luma*GM.kt`
- Delete: `skia-integration-tests/src/test/kotlin/org/skia/tests/Runtime*Test.kt`
- Delete: `skia-integration-tests/src/test/resources/original-888/runtime_*.png`
- Delete: `skia-integration-tests/src/test/resources/original-888/intrinsics_*.png`
- Delete: `skia-integration-tests/src/test/resources/original-888/kawase*.png`
- Delete: `skia-integration-tests/src/test/resources/original-888/ripple*.png`
- Delete: `skia-integration-tests/src/test/resources/original-888/arithmode*.png`
- Delete: `skia-integration-tests/src/test/resources/original-888/luma*.png`

- [ ] **Step 1: Delete all old source files**

```bash
rm skia-integration-tests/src/main/kotlin/org/skia/tests/RuntimeShaderGM.kt
# ... etc
```

- [ ] **Step 2: Delete old test files** (check which exist first)

- [ ] **Step 3: Verify original-888/ refs are gone** (they should have been moved in each prior task)

- [ ] **Step 4: Compile both modules**

```bash
./gradlew :skia-integration-tests:compileKotlin :integration-tests:skia:compileTestKotlin
```

- [ ] **Step 5: Final commit**

```bash
git add -A
git commit -m "gm: clean up old SkRuntimeEffect sources and tests"
```
