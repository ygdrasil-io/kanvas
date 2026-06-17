# GPU Renderer M7-002 Runtime Effect Refusal Matrix

Date: 2026-06-17
Branch: `codex/kgpu-m7-002-runtime-refusals`
Ticket: `KGPU-M7-002`

## Ticket Status

| Ticket | Status | Evidence | Remaining gate |
|---|---|---|---|
| KGPU-M7-002 | `done` | Added `GPURuntimeEffectRefusalMatrix`, PM-visible source/child/placement refusal rows, stable refusal facts, descriptor-anchor diagnostics, and `RuntimeEffectRefusalMatrixTest`. | Independent re-review accepted the evidence with no remaining P0/P1/P2 blockers. Arbitrary SkSL/WGSL input, child runtime effects, unsupported placement support, product activation, adapter-backed execution, and broad runtime-effect compatibility remain unpromoted. |

## Evidence

- `RuntimeEffectRefusalMatrixTest` records the
  `gpu-renderer.runtime-effect-refusals` row with
  `classification=RefuseRequired`, `routeKind=RefuseDiagnostic`,
  `promotable=false`, and `productActivation=false`.
- The accepted descriptor anchor is the reviewed KGPU-M7-001
  `runtime.simple.color@1` descriptor boundary. The matrix does not route that
  descriptor; it uses the boundary only to explain why broader runtime-effect
  shapes remain refused.
- Unsupported variants refuse with stable diagnostics:
  `unsupported.runtime_effect.dynamic_sksl_forbidden`,
  `unsupported.runtime_effect.dynamic_wgsl_forbidden`,
  `unsupported.runtime_effect.compatibility_key_unknown`,
  `unsupported.runtime_effect.child_count`,
  `unsupported.runtime_effect.child_missing`,
  `unsupported.runtime_effect.child_kind`,
  `unsupported.runtime_effect.child_sample_radius`,
  `unsupported.runtime_effect.kind_mismatch`,
  `unsupported.runtime_effect.unregistered_descriptor`, and
  `unsupported.runtime_effect.descriptor_collision`.
- PM dump lines expose dashboard categories: `source`, `child`, and
  `placement`.
- PM dump lines preserve the stable refusal facts required for review:
  `keyHash`, child `slotName`, `acceptedSourceKinds`, `childSourceKind`,
  `sampleUsage`, `requestedPlacement`, `acceptedPlacements`, and
  `descriptorMatches`.
- The non-claim dump states that arbitrary SkSL, arbitrary WGSL, children,
  unsupported placement support, and product activation remain false.

## Validations

```bash
rtk ./gradlew --no-daemon -Dkotlin.compiler.execution.strategy=in-process :gpu-renderer:test --tests org.graphiks.kanvas.gpu.renderer.runtimeeffects.RuntimeEffectRefusalMatrixTest
rtk ./gradlew --no-daemon --rerun-tasks -Dkotlin.compiler.execution.strategy=in-process :gpu-renderer:check
rtk ./gradlew --no-daemon --rerun-tasks -Dkotlin.compiler.execution.strategy=in-process :gpu-raster:test --tests '*Runtime*' --tests '*Blend*' --tests '*Color*'
rtk git diff --check
rtk awk '/^status: / {count[$2]++} END {for (s in count) print s, count[s]}' .upstream/specs/gpu-renderer/tickets/M*-*/KGPU-*.md
```

The targeted test first failed in RED state because the refusal matrix,
source-kind enum, input, row, and report contracts did not exist. After
implementation, the targeted test passed. A follow-up RED expansion added
compatibility-key, missing-child, wrong-child-kind, and child-sample-radius
cases before the classifier was extended. Review remediation then added a RED
for stable dump facts and descriptor-collision evidence; the targeted test
failed on the old dump format, then passed after adding per-row facts and the
collision case. The targeted test, full `:gpu-renderer:check --rerun-tasks`,
M7 `gpu-raster` Runtime/Blend/Color bundle with `--rerun-tasks`,
`rtk git diff --check`, and the status-count command passed.

Current status count after moving KGPU-M7-002 to `done`:

```text
blocked 8
done 38
review 0
```

## Review

Local pre-PR review scope:

- check that arbitrary SkSL and arbitrary WGSL sources refuse explicitly;
- check that child-slot rows do not imply child runtime-effect support;
- check that unsupported placements refuse instead of reusing material-source
  descriptor support;
- check that descriptor-anchor lookup failure remains visible as a refusal;
- check that PM rows are `RefuseRequired` and never promotable;
- check that non-claims prevent product activation or broad runtime-effect
  support claims.

Independent review `019ed5af-9775-7e60-a0b6-796612671fbb` found two evidence
gaps before `done`: missing stable refusal facts in dump lines and missing
descriptor-collision coverage. Both were remediated with failing tests first.
Independent re-review `019ed5b8-bffe-7f52-a451-39c6a61f5ed4` found no
remaining P0/P1/P2 blockers, confirmed the previous findings were resolved, and
found no hidden support or product-activation claim.

## Non-Claims

- No product route activation.
- No adapter-backed runtime-effect execution.
- No arbitrary SkSL or dynamic shader compilation.
- No arbitrary WGSL descriptor input.
- No child runtime-effect support.
- No unsupported placement support.
- No runtime blender support.
- No filter runtime-effect support.
- No live runtime-effect editing.
- No broad runtime-effect compatibility.
- No release-blocking or readiness movement.
