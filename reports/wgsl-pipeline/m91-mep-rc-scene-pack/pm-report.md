# M91 MEP RC Scene Pack

Date: 2026-06-02
Scope: `FOR-215`, `FOR-216`, `FOR-218`

## Result

The release-candidate MEP scene pack is documented under
`reports/wgsl-pipeline/m91-mep-rc-scene-pack/`. It selects 10 rows from
existing checked-in dashboard and runtime evidence, then gates the selection
with a headless validator. This is an evidence/dashboard slice only; it does not claim new renderer fixes.

## Selection

| Status | Rows | Meaning |
|---|---:|---|
| `supported` | 6 | Existing reference, CPU, GPU, diff, route, and stats artifacts are present. |
| `partial` | 1 | Existing rendered artifacts are present, but support is bounded to the selected route. |
| `expected-unsupported` | 2 | GPU rendering is intentionally refused with stable diagnostics. |
| `blocked-dependency` | 1 | The row is held for a real dependency instead of a short-lived substitute. |

Selected supported rows:

- `solid-rect`
- `linear-gradient-rect`
- `runtime-effect-simple`
- `crop-image-filter-nonnull-prepass`
- `clip-rect-difference`
- `bitmap-subset-local-matrix-repeat`

Partial row:

- `image-filter-compose-cf-matrix-transform`, bounded to the selected composed image-filter route.

Explicit non-rendered rows:

- `path-aa-dashing-edge-budget`: `coverage.edge-count-exceeded`
- `image-filter-crop-nonnull-prepass-required`: `image-filter.crop-input-nonnull-prepass-required`
- `font-complex-shaping-refusal`: `font.complex-shaping-dependency-gated`

## Exclusions

The pack excludes broad image-filter DAGs, broad Path AA, arbitrary clip-stack
parity, broad bitmap/texture/codec coverage, arbitrary runtime shader input,
and complex text shaping. Those are not treated as release-candidate gaps
hidden inside supported rows; they remain explicit unsupported or
dependency-gated outcomes.

## Non-Claims

- WGSL remains the shader implementation target.
- WebGPU remains the GPU backend.
- `SkRuntimeEffect` remains a compatibility facade backed by registered
  Kotlin/WGSL implementations.
- No SkSL compiler, SkSL IR, or SkSL VM is claimed.
- No Ganesh or Graphite port is claimed.
- Kadre native demos remain opt-in; the RC pack validator is headless.
- Existing thresholds are referenced, not weakened.

## Validation

```bash
rtk python3 scripts/validate_mep_rc_scene_pack.py /tmp/kanvas-next-sprints
rtk ./gradlew --no-daemon validateMepRcScenePack
```
