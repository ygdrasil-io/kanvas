# KAN-003 Caps/Joins AA Evidence

KAN-003 closes caps/joins AA as `visible-non-supportable` for the current
bounded scene. It uses existing M60 and FOR-267 evidence to keep the visible
stroke cap/join gap explicit without promoting WebGPU support.

## Result

The evidence pack lives at
`reports/wgsl-pipeline/scenes/artifacts/kan-003-caps-joins-aa/kan-003-caps-joins-aa.json`.

| Role | Scene or probe | Status | Evidence |
|---|---|---|---|
| Visible caps/joins scene | `m60-bounded-stroke-cap-join` | `expected-unsupported` | Reference, CPU, CPU diff, route, stats, and diagnostic WebGPU image/diff artifacts exist. |
| Production WebGPU route | `m60-bounded-stroke-cap-join` | `expected-unsupported` | Normal route remains `webgpu.coverage.refuse` with `coverage.stroke-cap-join-visual-parity-below-threshold`. |
| Dashboard row | `m60-nested-clip-path-aa-promotion.json` | `expected-unsupported` | Row keeps stroke cap/join tags, CPU oracle route, stable GPU refusal, and residual root cause. |
| Residual localization | FOR-266 | `KEEP_DIAGNOSTIC` | Residual remains localized to `coverage.stroke-cap-join-aa-residual`. |
| Round cap/join equivalence | FOR-267 | `KEEP_DIAGNOSTIC` | Bounded correction remains refused because CPU/GPU boundary-cell coverage equivalence is not proven. |

## Bounded Facts

The selected scene is intentionally narrow:

- edge count: `18 / 256`;
- path verb count: `9 / 96`;
- stroke width: `10.0`;
- caps: `butt`, `round`, `square`;
- joins: `bevel`, `round`, `bevel`;
- support threshold: `99.95`;
- diagnostic route: `webgpu.coverage.stroke-cap-join.experimental-render`;
- production route: `webgpu.coverage.refuse`.

The target-color-space blend evidence remains diagnostic-only. It does not
change the production default, and it does not globally enable
`targetColorSpaceBlend`.

## Decision

No stroke cap/join WebGPU support claim is added.

No threshold is lowered.

No shared coverage production behavior is changed.

No broad stroke, hairline, dash, cubic stroke, arbitrary Path AA, Ganesh,
Graphite, SkSL compiler, SkSL IR, or SkSL VM work is introduced.

The current closure is a visible refusal:

```text
coverage.stroke-cap-join-visual-parity-below-threshold
```

with remaining boundary:

```text
coverage.stroke-cap-join-aa-residual
```

## Validation

```bash
rtk python3 scripts/validate_kan003_caps_joins_aa.py /Users/chaos/.codex/worktrees/7ac1/kanvas
rtk ./gradlew --no-daemon :validateKan003CapsJoinsAa
rtk python3 scripts/validate_for266_stroke_cap_join_aa_residual.py
rtk python3 scripts/validate_for267_round_cap_join_coverage_equivalence.py
rtk ./gradlew --no-daemon :pipelineSceneDashboardGate :pipelinePmBundle
rtk git diff --check
```
