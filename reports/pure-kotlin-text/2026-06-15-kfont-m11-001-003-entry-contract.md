# KFONT M11 001-003 Entry Contract Evidence

Date: 2026-06-15

## Scope

| Ticket | Status | Evidence |
|---|---|---|
| `KFONT-M11-001` | `review` | `TextGPUArtifactRegistry`, deterministic registry dump, default descriptor order, descriptor compact hashes, unregistered artifact refusal, and defensive descriptor snapshots. |
| `KFONT-M11-002` | `review` | `TextPayloadLeakReport`, deterministic `payloadHash` evidence, positive no-`Sk*` fixture, concrete `TextGPUArtifactBundle.noSkLeakageReport()` field scans, registry descriptor scans, forbidden-field fixtures, stable diagnostics, scan-order JSON, and mutation-proof report snapshots. |
| `KFONT-M11-003` | `review` | `DrawTextRunPayload`, deterministic payload dump with enriched artifact refs, UUID-backed upload/diagnostic refs, no-leakage report integration, non-claim guards, nested glyph-run snapshots, and `*DrawTextRun*` validation coverage. |

## Implemented Contracts

- `font/gpu-api/src/main/kotlin/org/graphiks/kanvas/glyph/gpu/GPUTextArtifactRegistry.kt`
- `font/gpu-api/src/main/kotlin/org/graphiks/kanvas/glyph/gpu/GPUTextArtifacts.kt`
- `font/gpu-api/src/main/kotlin/org/graphiks/kanvas/glyph/gpu/GPUTextLeakValidation.kt`
- `font/gpu-api/src/main/kotlin/org/graphiks/kanvas/glyph/gpu/GPUTextDrawPayload.kt`

## Test Evidence

- `font/gpu-api/src/test/kotlin/org/graphiks/kanvas/glyph/gpu/GPUTextArtifactRegistryTest.kt`
- `font/gpu-api/src/test/kotlin/org/graphiks/kanvas/glyph/gpu/GPUTextArtifactsSurfaceTest.kt`
- `font/gpu-api/src/test/kotlin/org/graphiks/kanvas/glyph/gpu/GPUTextNoSkLeakageValidationTest.kt`
- `font/gpu-api/src/test/kotlin/org/graphiks/kanvas/glyph/gpu/DrawTextRunPayloadTest.kt`

Fixture naming note: evidence names such as `text-gpu-no-sk-leakage-report.json`
and `draw-text-run-payload.json` refer to canonical JSON fixtures asserted
inline in the tests above. This PR A evidence update does not add standalone
generated JSON files under `reports/`; future route or PM packaging tickets may
promote those inline fixtures into checked-in generated artifacts.

The tests cover:

- deterministic artifact registry order and descriptor compact hashes;
- artifact references carrying artifact type, artifact key hash, registry
  invalidation facts, and scoped diagnostic facts;
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
- concrete `TextGPUArtifactBundle.noSkLeakageReport()` scans over real bundle
  fields, generated artifact references, diagnostics, upload ranges, glyph
  plans, and atlas facts;
- no-`Sk*` positive and negative fixtures;
- value-level payload scans for stringified or opaque `Sk*`, `fontBytes`,
  `NativeFontHandle`, `GPUHandle`, `GPUTexture`, `GPUBuffer`, `GPUDevice`,
  `WGPUTexture`, `TextureView`, and `BindGroup` markers;
- domain wrapper type names such as `GPUTextArtifactID`,
  `GPUTextArtifactGeneration`, and `GPUTextLayoutResultID` are not raw GPU
  handle leaks;
- full CPU-rendered text texture refusal;
- nondumpable payload refusal with `unsupported.text.payload_nondumpable`;
- deterministic `TextPayloadLeakReport.payloadHash` values derived from
  payload kind and scanned field facts;
- UUID-backed `GPUTextUploadDependencyID` and `GPUTextRouteDiagnosticID`
  wrappers carried through scanable upload labels and route diagnostic
  code/message refs;
- report and payload mutation snapshots;
- exact canonical JSON escaping/order fixtures;
- `DrawTextRunPayload` non-claim guards.
- a generic future `GPUTextSubRunPlan` field-list fixture that validates the
  scanner can cover future subrun payloads without implementing or claiming
  subrun planning in PR A.

## Review Evidence

Fresh subagent review checkpoints found and remediated these PR A gaps before
this evidence was published:

- Registry descriptor snapshots and test discovery filters were hardened.
- `DrawTextRunPayload` leakage validation was changed from static type facts
  to real value scans.
- Generic stringified `Sk*` markers, raw GPU handles, CPU-rendered full text
  textures, and nondumpable payload markers were covered by stable diagnostics.
- Registry descriptors were added to the no-`Sk*` scan surface.
- Descriptor compact hashes, route family metadata, and missing/stale/budget
  diagnostics were added as registry metadata only.
- Artifact references were enriched with artifact type, artifact key hash,
  invalidation facts, and scoped diagnostics.
- Concrete `TextGPUArtifactBundle` scan fixtures were added; the future
  `GPUTextSubRunPlan` coverage remains a generic field-list fixture only, with
  no production subrun planning claim in PR A.

## Validation

Fresh commands:

```bash
rtk ./gradlew --no-daemon :font:gpu-api:test --tests '*ArtifactRegistry*'
rtk ./gradlew --no-daemon :font:gpu-api:test --tests '*NoSkLeakage*'
rtk ./gradlew --no-daemon :font:gpu-api:test --tests '*DrawTextRun*'
rtk ./gradlew --no-daemon :font:gpu-api:test --tests '*TextArtifactsSurface*'
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
