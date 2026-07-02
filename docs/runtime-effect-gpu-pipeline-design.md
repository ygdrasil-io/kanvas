# RuntimeEffect GPU Pipeline — Design

## Scope

Wire the existing (but detached) RuntimeEffect GPU pipeline into the standard
render dispatch, then port ~23 SkRuntimeEffect GMs to the new
`integration-tests/skia` framework using the wired pipeline.

## Motivation

- ~23 GMs blocked on a pipeline that is 80% designed but never connected
- The scene-level bypass (`RectOnlyOffscreenRenderer`) proves RGBA rendering works
- Each GM needs a WGSL shader + descriptor + proper GPU material routing

---

## 1. GPU Pipeline Wiring

### Current State (stub)

```
Shader.RuntimeEffect(effect, uniforms)
  → GPUMaterialMapper → RuntimeEffect("", 1)      ← data thrown away
  → GPURefusalGuards → refused (not in accepted list)
  → AnalysisContracts → refused
  → never reaches MaterialLowering
```

### Target State

```
Shader.RuntimeEffect(effect, uniforms)
  → GPUMaterialMapper → RuntimeEffect(effectId, version)  ← real data extracted
  → GPURefusalGuards → ACCEPTED
  → AnalysisContracts → ACCEPTED
  → RuntimeEffectMaterialLowering → MaterialSource.RuntimeEffect
  → GPURuntimeEffectDescriptorRoutePlanner → route plan
  → GPURuntimeEffectExecutor → GPU commands
```

### Changes

| File | Change |
|------|--------|
| `GPUMaterialMapper.kt:79` | Extract `effect.effect.id` from `Shader.RuntimeEffect` |
| `GPURefusalGuards.kt` | Add `RuntimeEffect` to accepted material kinds |
| `AnalysisContracts.kt:1617,1628` | Add `RuntimeEffect` to `acceptedMaterialKinds` + `acceptedFillPathMaterialKinds` |
| `RuntimeEffectMaterialLowering.kt` | **New** — map descriptor to source with WGSL routing |
| `GPURuntimeEffectDispatch.kt` | Connect executor to lowered material |

---

## 2. WGSL Shaders

### Already exist (3)
- `SimpleRTWgsl` — solid color fill
- `LinearGradientSnippet` — gradient color
- `SpiralRTWgsl` — procedural spiral

### New shaders (12)

Each intrinsics shader uses an `opcode: i32` uniform + `switch` to dispatch.

| ID | WGSL File | Uniforms |
|----|-----------|----------|
| `runtime.color_filter_luma_to_alpha` | `ColorFilterLumaToAlphaWgsl` | `color: vec4<f32>` |
| `runtime.color_filter_noop` | `ColorFilterNoopWgsl` | — |
| `runtime.color_filter_ternary` | `ColorFilterTernaryWgsl` | `condition: f32` |
| `runtime.color_filter_ifs` | `ColorFilterIfsWgsl` | `value: f32` |
| `runtime.color_filter_early_return` | `ColorFilterEarlyReturnWgsl` | `threshold: f32` |
| `runtime.intrinsics_common` | `IntrinsicsCommonWgsl` | `input: vec4<f32>`, `opcode: i32` |
| `runtime.intrinsics_trig` | `IntrinsicsTrigWgsl` | `input: vec4<f32>`, `opcode: i32` |
| `runtime.intrinsics_exp` | `IntrinsicsExponentialWgsl` | `input: vec4<f32>`, `opcode: i32` |
| `runtime.intrinsics_geom` | `IntrinsicsGeometricWgsl` | `input: vec4<f32>`, `opcode: i32` |
| `runtime.intrinsics_matrix` | `IntrinsicsMatrixWgsl` | `input: mat4x4<f32>`, `opcode: i32` |
| `runtime.intrinsics_relational` | `IntrinsicsRelationalWgsl` | `input: vec4<f32>`, `opcode: i32` |
| `runtime.functions` | `RuntimeFunctionsWgsl` | `input: vec4<f32>` |
| `runtime.kawase_blur` | `KawaseBlurWgsl` | `texture`, `offset: vec2<f32>` |
| `runtime.ripple` | `RippleWgsl` | `time: f32`, `amplitude: f32`, `freq: f32` |
| `runtime.arithmode` | `ArithmodeWgsl` | `src: vec4<f32>`, `dst: vec4<f32>` |
| `runtime.luma_filter` | `LumaFilterWgsl` | `input: vec4<f32>` |

All placed in `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/wgsl/`.

---

## 3. Descriptor Registration

Each shader gets a `GPURuntimeEffectDescriptor` object + registered in
`KanvasRuntimeEffectRegistry`.

Registration fields: `effectId`, `version`, `uniformSchema` (field spec strings),
`uniformBlockPlan` (size, packing), `wgslPlan` (entry point, module hash),
`routeContract` (accepted placements), `childSlots`.

The `acceptedWgslImplementationIds` set in `SkRuntimeEffectDescriptor.kt` is
updated with each new `wgslImplementationId`.

---

## 4. GM Porting

### Pattern

```kotlin
class XxxGm : SkiaGm {
    override val name = "xxx"
    override val renderFamily = RenderFamily.RUNTIME_EFFECT
    override val minSimilarity = 0.0
    override val width = 512
    override val height = 256

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val wgsl = WGSL_SOURCE.trimIndent()
        val effect = RuntimeEffect.compile(wgsl).getOrThrow()
        val uniforms = UniformBlock { float4("gColor", 1f, 0f, 0f, 1f) }
        val shader = effect.makeShader(uniforms)
        canvas.drawRect(Rect(0f, 0f, width.toFloat(), height.toFloat()),
            Paint(shader = shader))
    }
}
```

### Batch plan

| Batch | GMs | WGSL needed | Priority |
|-------|-----|-------------|----------|
| 1 | `RuntimeShaderGM`, `SpiralRTGM`, `LinearGradientRTGM` | 0 (exist) | P0 |
| 2 | `RuntimeColorFilterGM` (5 variants) | 5 color filter | P0 |
| 3 | 7 intrinsics GMs | 6 intrinsics | P1 |
| 4 | `RuntimeFunctionsGM` | 1 functions | P1 |
| 5 | `KawaseBlurRtGM`, `RippleShaderGM`, `ArithmodeGM`, `LumaFilterGM` | 4 specialized | P1 |
| — | `RuntimeShaderStubsGM` | stub, skip | — |

Each batch: write WGSL → register descriptor → port GM → move ref → cleanup.

---

## 5. Files Summary

### New files
- `gpu-renderer/.../materials/RuntimeEffectMaterialLowering.kt`
- `gpu-renderer/.../wgsl/ColorFilter*Wgsl.kt` (5 files)
- `gpu-renderer/.../wgsl/Intrinsics*Wgsl.kt` (6 files)
- `gpu-renderer/.../wgsl/RuntimeFunctionsWgsl.kt`
- `gpu-renderer/.../wgsl/KawaseBlurWgsl.kt`
- `gpu-renderer/.../wgsl/RippleWgsl.kt`
- `gpu-renderer/.../wgsl/ArithmodeWgsl.kt`
- `gpu-renderer/.../wgsl/LumaFilterWgsl.kt`
- `integration-tests/skia/.../gm/runtime_effect/*Gm.kt` (~21 files)

### Modified files
- `GPUMaterialMapper.kt` — extract effectId
- `GPURefusalGuards.kt` — accept RuntimeEffect
- `AnalysisContracts.kt` — accept RuntimeEffect
- `KanvasRuntimeEffectRegistry.kt` — register new descriptors
- `acceptedWgslImplementationIds` — add new IDs
- ServiceLoader

---

## 6. Risks

- **ColorFilter hook**: `effect.makeColorFilter()` needs the `compileColorFilter` hook
  installed; if missing, color filter GMs fall back to CPU
- **Child shaders** (KawaseBlur): texture sampling in WGSL adds complexity;
  KawaseBlur may need a simpler variant first
- **Arithmode**: blend-mode arithmetic may be replaceable by standard `BlendMode`;
  only needs runtime effect if testing custom transfer functions
