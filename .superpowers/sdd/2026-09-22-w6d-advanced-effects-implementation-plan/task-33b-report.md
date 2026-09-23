# Task 33b report — Lighting degenerates and compositional demand

## Status

Implemented on `codex/w6d-advanced-effects` from reviewed Task 33a head
`2b54184b2`.  The scoped commit is pending controller review.

## Public evidence and TDD

All new expectations are constructed through the independent CPU oracle before
creating a public `Surface`; positive witnesses assert both `Render` and
`Readback` scope kinds.

- Added named public witnesses for a Sobel child touching the output edge
  (clamp), an interior child edge (existing decal witness), transparent-black
  lighting, zero distant/spot direction, coincident point/surface, zero
  specular half vector, negative fractional power, and `pow(0,0)`.  The
  approved design explicitly defines `pow(0,0) = 1`; the new spot witness pins
  its opaque white result rather than contradicting that rule.
- Re-added the `ColorFilter(DistantLitDiffuse(Crop(...)))` public
  characterization.  It passes on the frozen single W6 graph: wrapper demand
  remains unbounded through the terminal consumer and no new graph, target,
  allocator, or renderer-side planning was introduced.
- The finite spot fixture uses location `(-1.8e38,-1.8e38,1)` and target
  `(1.8e38,1.8e38,1)`.  The independent oracle calculates `[222,222,222,255]`
  for its sloped alpha fixture before `Surface` creation.  The original public
  RED reached the shader and recorded XML `channel 0 expected=222 actual=0`.
  This validates both finite admission and the historical oracle rather than
  treating a finite value as non-finite or widening tolerance.

The first two narrowly scoped renderer hypotheses did not resolve the RED:
scaling only after `location - surface`, and lower-casing the WGSL exponent
literal each retained black output.  The causal correction always rewrites a
finite point/spot vector as `location/scale - surface/scale`, with
`scale=max(1,abs(x),abs(y),abs(z))`; no magnitude branch, new recipe ID, or
renderer-side family selection is introduced.  Normalization is invariant
under this common positive scale.  For every nonzero finite F32 scale its
reciprocal is at least `1/Float.MAX_VALUE ≈ 2.94e-39`, above the smallest F32
subnormal, so the reciprocal cannot underflow to zero.  The exact
`[222,222,222,255]` witness then passes.

## Commands and results

| Command | Result |
| --- | --- |
| `rtk ./gradlew :kanvas:test --tests '...finite extreme spot coordinates retain their normalized diffuse contribution'` before correction | XML RED: `channel 0 expected=222 actual=0`; native exit 133 is separate UNKNOWN. |
| Same focused command after correction | JUnit method PASS; native exit 133, UNKNOWN. |
| `rtk ./gradlew :gpu-plan:compileKotlin` | exit 0 |
| `rtk ./gradlew :gpu-renderer:compileKotlin` | exit 0 |
| `rtk ./gradlew :kanvas:compileTestKotlin` | exit 0 |
| `rtk ./gradlew :kanvas:test --tests 'org.graphiks.kanvas.surface.W6dLightingSurfacePixelTest'` | class XML `28/0/0/0`; worker exit 133, UNKNOWN |
| `rtk ./gradlew :kanvas:test --tests 'org.graphiks.kanvas.surface.W6aLayerRestoreSurfacePixelTest'` | class XML `9/0/0/0`; worker exit 133, UNKNOWN |
| `rtk ./gradlew :kanvas:test --tests 'org.graphiks.kanvas.surface.W5hGeometryHLaneSurfacePixelTest'` | console methods passed until native process exit; class XML was not retained, so baseline status is UNKNOWN rather than claimed green. |

## Files

- `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/filters/GPULighting.kt`
- `kanvas/src/test/kotlin/org/graphiks/kanvas/surface/W6dLightingSurfacePixelTest.kt`

## Self-review

- `git diff --check` is clean.
- The correction only alters frozen recipe shader emission; it retains the
  existing FilterPass, FilterTarget, Sobel modes, resource accounting and
  renderer materialization authority.
- No private/static-source, mock, reflection, fake-device, font, codec, GM,
  dashboard, or global Skia test was added.
- Remaining concern: native test process exit 133 persists after all XML
  methods complete, so native execution is UNKNOWN rather than claimed green.
