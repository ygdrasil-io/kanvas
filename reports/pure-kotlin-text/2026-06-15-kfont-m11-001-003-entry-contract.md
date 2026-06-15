# KFONT M11 001-003 Entry Contract Evidence

Date: 2026-06-15

## Scope

| Ticket | Status | Evidence |
|---|---|---|
| `KFONT-M11-001` | `review` | `TextGPUArtifactRegistry`, deterministic registry dump, default descriptor order, descriptor compact hashes, unregistered artifact refusal, and defensive descriptor snapshots. |
| `KFONT-M11-002` | `review` | `TextPayloadLeakReport`, positive no-`Sk*` fixture, registry descriptor scans, forbidden-field fixtures, stable diagnostics, scan-order JSON, and mutation-proof report snapshots. |
| `KFONT-M11-003` | `review` | `DrawTextRunPayload`, deterministic payload dump, no-leakage report integration, non-claim guards, nested glyph-run snapshots, and `*DrawTextRun*` validation coverage. |

## Implemented Contracts

- `font/gpu-api/src/main/kotlin/org/graphiks/kanvas/glyph/gpu/GPUTextArtifactRegistry.kt`
- `font/gpu-api/src/main/kotlin/org/graphiks/kanvas/glyph/gpu/GPUTextLeakValidation.kt`
- `font/gpu-api/src/main/kotlin/org/graphiks/kanvas/glyph/gpu/GPUTextDrawPayload.kt`

## Test Evidence

- `font/gpu-api/src/test/kotlin/org/graphiks/kanvas/glyph/gpu/GPUTextArtifactRegistryTest.kt`
- `font/gpu-api/src/test/kotlin/org/graphiks/kanvas/glyph/gpu/GPUTextNoSkLeakageValidationTest.kt`
- `font/gpu-api/src/test/kotlin/org/graphiks/kanvas/glyph/gpu/DrawTextRunPayloadTest.kt`

Fixture naming note: evidence names such as `text-gpu-no-sk-leakage-report.json`
and `draw-text-run-payload.json` refer to canonical JSON fixtures asserted
inline in the tests above. This PR A evidence update does not add standalone
generated JSON files under `reports/`; future route or PM packaging tickets may
promote those inline fixtures into checked-in generated artifacts.

The tests cover:

- deterministic artifact registry order and descriptor compact hashes;
- target route family metadata for all seven descriptors:
  `AtlasMaskSample`, `AtlasSDFSample`, `DependencyGated`,
  `OutlinePathRoute`, `ColorGlyphCompositeRoute`,
  `BitmapGlyphTextureRoute`, and `SVGGlyphVectorRoute`;
- `GlyphUploadPlan` route metadata as a planning dependency, not an execution
  route;
- descriptor missing, stale, and budget diagnostics in canonical dumps,
  compact hashes, and no-`Sk*` scans;
- unregistered artifact diagnostics;
- registry descriptor no-`Sk*` leakage reports, including a negative descriptor
  fixture for `SkFont`, `fontBytes`, and raw GPU handle fields;
- no-`Sk*` positive and negative fixtures;
- value-level payload scans for stringified or opaque `Sk*`, `fontBytes`,
  `NativeFontHandle`, `GPUHandle`, `GPUTexture`, `GPUBuffer`, `GPUDevice`,
  `WGPUTexture`, `TextureView`, and `BindGroup` markers;
- domain wrapper type names such as `GPUTextArtifactID`,
  `GPUTextArtifactGeneration`, and `GPUTextLayoutResultID` are not raw GPU
  handle leaks;
- full CPU-rendered text texture refusal;
- nondumpable payload refusal with `unsupported.text.payload_nondumpable`;
- report and payload mutation snapshots;
- exact canonical JSON escaping/order fixtures;
- `DrawTextRunPayload` non-claim guards.

## Review Evidence

Fresh subagent review checkpoints:

- `KFONT-M11-001` spec review: accepted after validating descriptor order,
  unregistered refusal diagnostics, and non-promoted route metadata.
- `KFONT-M11-001` code-quality review: accepted after hardening descriptor
  list snapshots and structural route assertions.
- `KFONT-M11-002` spec review: accepted.
- `KFONT-M11-002` code-quality review: accepted after replacing the report
  `data class` with a snapshotting class and aligning JSON order with scan
  order.
- `KFONT-M11-003` spec review: accepted after renaming tests so
  `--tests '*DrawTextRun*'` discovers the ticket validation lane.
- `KFONT-M11-003` code-quality review: accepted.

## Validation

Fresh commands:

```bash
rtk ./gradlew --no-daemon :font:gpu-api:test --tests '*ArtifactRegistry*'
rtk ./gradlew --no-daemon :font:gpu-api:test --tests '*NoSkLeakage*'
rtk ./gradlew --no-daemon :font:gpu-api:test --tests '*DrawTextRun*'
rtk ./gradlew --no-daemon :font:gpu-api:test
rtk git diff --check
```

## Non-Claims

- No GPU text product support is promoted.
- Target route family metadata remains descriptor metadata only; it does not
  activate route execution.
- No WebGPU upload, binding, command submission, or readback is claimed.
- No `KGPU-M6-002` or `KGPU-M6-003` status is changed by this evidence.
- No A8/SDF/outline/color/bitmap/SVG glyph rendering support is claimed.
- No CPU-rendered full text texture fallback is introduced.
- `dftext`, `scaledemoji_rendering`, and `coloremoji_blendmodes` remain open.
