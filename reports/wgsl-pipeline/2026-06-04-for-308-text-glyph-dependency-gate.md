# FOR-308 Text/Glyph Dependency Gate

Linear: `FOR-308`

Source memory:
`global/kanvas/ticket-drafts/draft-for-next-text-glyph-dependency-gate-ticket`

Decision: `TEXT_GLYPH_DEPENDENCY_GATE_APPLIED`

## Result

FOR-308 applies a text/glyph dependency gate. Existing simple text rows remain
limited to outline/path glyph rendering. Complex shaping, emoji/color glyph,
and missing-glyph/fallback-family rows remain expected unsupported until real
font/text deliveries land with complete evidence.

This ticket does not change renderer code, shader code, font backend behavior,
scene status, fallback reasons, thresholds, or readiness score.

## Supported Boundary Preserved

| Scene id | Status | GPU route | Fallback reason |
|---|---|---|---|
| `font-latin-outline-drawstring` | `pass` | `webgpu.text.outline.simple-latin` | `none` |
| `font-textblob-positioned-glyph-run` | `pass` | `webgpu.text.outline.positioned-glyph-run` | `none` |
| `font-kerning-style-fixture` | `pass` | `webgpu.text.outline.kerning-style-fixture` | `none` |
| `m66-font-latin-outline-cpu-oracle` | `pass` | `webgpu.text.outline.simple-latin` | `none` |
| `m66-font-positioned-glyph-run-cpu-oracle` | `pass` | `webgpu.text.outline.positioned-glyph-run` | `none` |
| `m66-font-kerning-style-cpu-oracle` | `pass` | `webgpu.text.outline.kerning-style-fixture` | `none` |

## Refusals Preserved

| Scene id | Status | GPU route | Fallback reason |
|---|---|---|---|
| `font-emoji-color-glyph-refusal` | `expected-unsupported` | `webgpu.text.refuse` | `font.color-glyph-emoji-unsupported` |
| `font-complex-shaping-refusal` | `expected-unsupported` | `webgpu.text.refuse` | `font.complex-shaping-requires-explicit-shaper` |
| `m52-color-emoji-blendmodes-refusal` | `expected-unsupported` | `webgpu.text.refuse` | `font.color-glyph-emoji-unsupported` |
| `m62-missing-glyph-fallback-refusal` | `expected-unsupported` | `webgpu.text.refuse.missing-glyph-fallback` | `font.missing-glyph-fallback-unsupported` |
| `m66-font-complex-shaping-refusal` | `expected-unsupported` | `webgpu.text.refuse.complex-shaping` | `font.complex-shaping-requires-explicit-shaper` |

## Forbidden Substitutes

- hidden HarfBuzz/FreeType/Fontations/CoreText/DirectWrite/fontconfig/JNI dependency
- native emoji renderer
- platform font fallback
- different font or glyph substitution
- glyph atlas, mask, SDF, or LCD claim without artifacts
- fallback reason or status relabel without implementation evidence

## Policy Cases

| Case | Decision | Allowed | Reason |
|---|---|---:|---|
| FOR-308 diagnostic dependency gate | `diagnostic-gate` | True | Diagnostic guard is allowed because it preserves existing support and refusal boundaries. |
| Hidden shaping or font library substitute is forbidden | `forbidden` | False | Hidden dependency, native API, or font/glyph substitution cannot clear text/glyph gates. |
| Native emoji or platform fallback is forbidden | `forbidden` | False | Hidden dependency, native API, or font/glyph substitution cannot clear text/glyph gates. |
| Different font or glyph substitution is forbidden | `forbidden` | False | Hidden dependency, native API, or font/glyph substitution cannot clear text/glyph gates. |
| Fallback relabel without implementation proof is forbidden | `forbidden` | False | Changing a refusal reason or status requires a dedicated implementation proof. |
| Atlas or glyph mask claim without artifacts is forbidden | `forbidden` | False | Atlas/mask/SDF/LCD claims require explicit artifacts and diagnostics. |
| Support claim without full proof is ambiguous | `ambiguous` | False | Support claim lacks the complete text/glyph promotion proof set. |
| Complete local proof can become a future promotion candidate | `future-promotion-candidate` | True | Complete proof belongs in a future implementation ticket, not this gate-only ticket. |

## Required Future Promotion Proof

- owning font spec section
- font fixture and provenance
- text input and shaping mode
- glyph diagnostics
- reference or CPU oracle
- CPU artifact and stats
- adapter-backed GPU artifact and stats when support is claimed
- CPU/GPU diff artifacts when support is claimed
- route diagnostics
- stable fallback policy for non-selected rows
- focused font or WebGPU text tests

## Validation

- `rtk python3 scripts/validate_for308_text_glyph_dependency_gate.py`
- `rtk ./gradlew pipelineSceneDashboardGate`
- `rtk python3 -m json.tool reports/wgsl-pipeline/scenes/artifacts/text-glyph-dependency-gate-for308/text-glyph-dependency-gate-for308.json`
- `rtk git diff --check origin/master...HEAD`
