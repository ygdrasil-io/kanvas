# GPU Renderer M7-001 Runtime Effect Descriptor Gate

Date: 2026-06-17
Branch: `codex/kgpu-m7-001-runtime-effect-descriptor`
Ticket: `KGPU-M7-001`

## Ticket Status

| Ticket | Status | Evidence | Remaining gate |
|---|---|---|---|
| KGPU-M7-001 | `review` | Added `GPURuntimeEffectDescriptorRoutePlanner`, registry snapshot and lookup contracts, descriptor WGSL evidence linkage, material route evidence, material-key boundary hashes, runtime-effect material source descriptors, and `RegisteredRuntimeEffectRouteTest`. | Independent review acceptance is still required before `done`. Adapter-backed runtime-effect execution, readback evidence, product activation, arbitrary SkSL/WGSL input, child runtime effects, blenders, filter runtime effects, live editing, and broad runtime-effect compatibility remain unpromoted. |

## Evidence

- `RegisteredRuntimeEffectRouteTest` records the
  `gpu-renderer.runtime-effect.registered` row with `routeKind=GPUNative`,
  `classification=DependencyGated`, `promoted=false`,
  `productActivation=false`, and `materialized=false`.
- The accepted `runtime.simple.color` fixture dumps registry version and
  generation, descriptor ID/version, uniform schema and packing, uniform block
  byte size, parser-validated wgsl4k source/module/reflection facts, reflected
  uniform layout cross-check, canonical 64-hex `sha256:` CPU oracle evidence
  hash, material route snippet identity, payload hash, material-key boundary
  hash, and non-terminal `accepted.runtime_effect.registered_descriptor`
  diagnostic.
- The material-key boundary hash excludes uniform values, CPU oracle output,
  and dynamic source text.
- Unsupported variants refuse with stable diagnostics:
  `unsupported.runtime_effect.unregistered_descriptor`,
  `unsupported.runtime_effect.descriptor_collision`,
  `unsupported.runtime_effect.dynamic_sksl_forbidden`,
  `unsupported.runtime_effect.kind_mismatch` for both wrong placement and
  missing explicit placement opt-in,
  `unsupported.runtime_effect.wgsl_reflection` for rejected or mismatched WGSL
  descriptor/schema evidence, and
  `unsupported.runtime_effect.cpu_oracle_missing` for missing or non-canonical
  64-hex `sha256:` oracle evidence.
- The route consumes the reviewed wgsl4k runtime-effect report shape from
  `reports/wgsl4k-evolution/generated/runtime-effect-wgsl-reflection.json`
  and keeps `routePromotion=not-promoted` semantics intact.

## Validations

```bash
rtk ./gradlew --no-daemon -Dkotlin.compiler.execution.strategy=in-process :gpu-renderer:test --tests org.graphiks.kanvas.gpu.renderer.runtimeeffects.RegisteredRuntimeEffectRouteTest
rtk ./gradlew --no-daemon --rerun-tasks -Dkotlin.compiler.execution.strategy=in-process :gpu-renderer:check
rtk ./gradlew --no-daemon --rerun-tasks -Dkotlin.compiler.execution.strategy=in-process :gpu-raster:test --tests '*Runtime*' --tests '*Blend*' --tests '*Color*'
rtk git diff --check
rtk awk '/^status: / {count[$2]++} END {for (s in count) print s, count[s]}' .upstream/specs/gpu-renderer/tickets/M*-*/KGPU-*.md
```

The targeted test first failed in RED state because the descriptor route
planner, registry snapshot, WGSL evidence, route plan, route placement, and
runtime-effect material source contracts did not exist. After implementation,
the targeted test, full `:gpu-renderer:check --rerun-tasks`, M7 `gpu-raster`
Runtime/Blend/Color validation bundle with `--rerun-tasks`, `rtk git diff
--check`, and the status-count command passed.

Current status count after moving KGPU-M7-001 to `review`:

```text
blocked 11
done 34
review 1
```

## Review

Local pre-PR review scope:

- check that the accepted route is limited to registered descriptor ID lookup;
- check that duplicate descriptors refuse as descriptor collisions rather than
  looking unregistered;
- check that dynamic SkSL/source-string input refuses before route evidence;
- check that wrong placement refuses instead of silently reusing material
  support in filter/blender contexts;
- check that native-supported descriptors still require explicit placement
  opt-in before a material route is accepted;
- check that wgsl4k comparison rejection and descriptor/module mismatch block
  the route;
- check that reflected uniform layout facts match the descriptor uniform schema;
- check that CPU oracle evidence is linked, canonical, and required;
- check that material-key boundary facts exclude uniform values, CPU oracle
  output, and dynamic source text;
- check that non-claims prevent product activation or adapter-backed support
  claims.

Independent pre-PR review `019ed557-9f73-7601-a48c-e442a75e3be8` found P2
gaps in CPU oracle hash validation and registry collision diagnostics plus a P3
uniform-schema linkage gap. This branch fixed those by requiring canonical
64-hex `sha256:` oracle evidence, refusing duplicate descriptor IDs with
`unsupported.runtime_effect.descriptor_collision`, and matching reflected WGSL
uniform layout facts against the descriptor schema before accepting the route.

Independent review is still required before changing the ticket from `review`
to `done`.

## Non-Claims

- No product route activation.
- No adapter-backed runtime-effect execution.
- No WebGPU readback evidence.
- No arbitrary SkSL or dynamic shader compilation.
- No arbitrary WGSL descriptor input.
- No child runtime-effect support.
- No runtime blender support.
- No filter runtime-effect support.
- No live runtime-effect editing.
- No broad runtime-effect compatibility.
- No release-blocking or readiness movement.
