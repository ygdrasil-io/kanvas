# KFONT-M11-006 GPUTextSubRunPlan Evidence

Date: 2026-06-23
Status: implemented, locally revalidated.
Ticket: `.upstream/specs/pure-kotlin-text/tickets/M11-gpu-handoff/KFONT-M11-006-add-gputextsubrunplan-splitting-tests.md`

## Scope

This slice adds deterministic `GPUTextSubRunPlan` evidence for splitting one
typed text command into renderer-consumable subruns. It stays planning-only:
no renderer resource materialization, no executed upload, no broad GPU text
support, and no `dftext` retirement are claimed.

## Files

- `font/gpu-api/src/main/kotlin/org/graphiks/kanvas/glyph/gpu/GPUTextSubRunPlan.kt`
- `font/gpu-api/src/test/kotlin/org/graphiks/kanvas/glyph/gpu/GPUTextSubRunPlanTest.kt`
- `reports/pure-kotlin-text/gpu-text-subrun-plan.json`
- `reports/pure-kotlin-text/dump-evidence-index.json`
- `reports/pure-kotlin-text/fixture-evidence-manifest.json`
- `.upstream/specs/pure-kotlin-text/tickets/M11-gpu-handoff/KFONT-M11-006-add-gputextsubrunplan-splitting-tests.md`
- `.upstream/specs/pure-kotlin-text/tickets/M11-gpu-handoff/README.md`
- `.upstream/specs/pure-kotlin-text/tickets/STATUS.md`

## Evidence

- `defaultGPUTextSubRunPlanReport()` emits checked-in
  `gpu-text-subrun-plan.json` with three deterministic scenarios:
  - atlas page/generation split, preserving source glyph order and
    upload-before-sample token labels;
  - clip/layer/destination-read barriers plus instance-budget refusal;
  - mixed A8/SDF/COLR/bitmap representation split with stable
    `unsupported.text.*` diagnostics for refused subruns.
- The report records source glyph ranges, split reasons, atlas page/generation
  facts, material/clip/layer keys, destination-read flags, ordering tokens,
  route outcomes, handoff diagnostics, renderer diagnostics, and non-claims.
- `GPUTextSubRunPlanTest` pins deterministic JSON, split order, refusal
  diagnostics, non-promotion, and forbidden payload token absence.

## TDD Evidence

Red result:

```bash
rtk ./gradlew --no-daemon :font:gpu-api:test --tests '*GPUTextSubRunPlan*'
```

Result: failed as expected at `:font:gpu-api:compileTestKotlin` because
`defaultGPUTextSubRunPlanReportJson`, `defaultGPUTextSubRunPlanReport`, and
the subrun planning types did not exist.

Green result:

```bash
rtk ./gradlew --no-daemon :font:gpu-api:test --tests '*GPUTextSubRunPlan*'
```

Result: passed after adding the planning contract, deterministic dump, and
focused tests.

## Remaining Gate

KFONT-M11-006 closes only subrun splitting evidence. It does not claim
resource/upload/instance/binding expansion, upload-before-sample execution,
route-specific WGSL validation, `MaterialKey` leakage validation, SDF/outline/
color/bitmap/SVG support, broad GPU text support, or `dftext` retirement.
`KFONT-M11-007` is now ready to own resource/upload/instance/binding
contracts. Ordering, WGSL validation, and `MaterialKey` leakage remain owned
by `KFONT-M11-008` through `KFONT-M11-010` after that prerequisite lands.
