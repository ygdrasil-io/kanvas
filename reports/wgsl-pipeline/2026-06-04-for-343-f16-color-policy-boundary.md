# FOR-343 F16 Color Policy Boundary

Linear: `FOR-343`

Decision: `F16_COLOR_POLICY_BOUNDARY_READY_FOR_BROADER_EVIDENCE`

FOR-343 defines the explicit F16 color-policy boundary requested by FOR-342.
It is architecture/evidence only: no renderer behavior is changed, and no
global F16 hook is migrated in this ticket.

## Boundary

The boundary is `cpu-raster-f16-color-policy-boundary`. It keeps `SkBitmap.getPixel` as the
internal oracle and `SkBitmap.getPixelAsSrgb` as the encoded export/readback
boundary. Raw transparent PNG Skia output remains rejected as an implementation
basis.

| axis | candidate signal | future attachment | migration gate |
|---|---|---|---|
| sourceColorSpace | straight sRGB source channels | typed PipelineIR color-space policy block before F16 premul conversion | requires reference/current/candidate samples across arc and non-arc F16 blend scenes |
| alphaQuantization | rounded covered alpha | coverage-to-color boundary with an explicit alpha quantization policy | requires worsened-sample accounting and edge/center sample tables |
| compositingBasis | SrcOver composited over white for comparable evidence | BlendPlan or pipeline blend/store policy selected by typed color domain | requires non-arc Rec.2020 F16 SrcOver blend proof before global hook change |
| exportReadbackBoundary | encoded sRGB export remains explicit and unchanged | readback/export policy boundary after internal SkBitmap.getPixel oracle | requires separate export migration if getPixelAsSrgb semantics change |
| referenceBasis | isolated Skia over-white reference, not raw transparent PNG | evidence artifact schema with reference/current/candidate triples | requires cross-scene reference/current/candidate evidence and raw PNG rejection |

## Future Attachment Points

- `pipeline-ir-color-policy-block`: make source color space, alpha domain, precision, and reference basis auditable before CPU/GPU specialization
- `cpu-raster-f16-premul-conversion-policy`: route F16 source-color policy through a named boundary before touching conversion semantics
- `cpu-raster-f16-blend-policy`: select compositing basis without fixture or coordinate branches
- `encoded-export-readback-policy`: separate internal oracle behavior from encoded export semantics

All attachment points are future-only. Any implementation must arrive through a
separate migration ticket with broader evidence, not through this artifact.

## Required Broader Evidence

| requirement | kind | status | reference/current/candidate |
|---|---|---|---|
| for342-adjacent-arc-prerequisite | arc-adjacent-f16-policy-prerequisite | present-partial-safer-route | yes |
| non-arc-rec2020-f16-src-over-blend-reference-current-candidate | non-arc-f16-blend | missing-required | yes |
| cross-scene-reference-current-candidate-matrix | cross-scene-reference-current-candidate | missing-required | yes |

The non-arc F16 blend requirement and the cross-scene
reference/current/candidate requirement are intentionally still marked missing.
They are gates before any global change to `colorToF16Premul`,
`blendF16PremulMode`, or export semantics.

## Dangerous Routes

| diagnostic | route | status |
|---|---|---|
| `F16_POLICY_UNSAFE_FIXTURE_BRANCH` | fixture-specific renderer branch | rejected |
| `F16_POLICY_UNSAFE_COORDINATE_BRANCH` | coordinate-specific renderer branch | rejected |
| `F16_POLICY_UNSAFE_SELECTED_CELL_SUBSTITUTION` | selected-cell or FOR-327 substitution | rejected |
| `F16_POLICY_UNSAFE_FULL_GM_CROP` | full-GM crop reference | rejected |
| `F16_POLICY_UNSAFE_GLOBAL_HOOK_MUTATION_WITHOUT_BOUNDARY` | mutating colorToF16Premul, blendF16PremulMode, or SkBitmap.getPixelAsSrgb before boundary approval | rejected |

## Imported FOR-342 Evidence

- FOR-342 decision: `CIRCULAR_ARCS_STROKE_BUTT_ADJACENT_F16_COLOR_POLICY_SCOPED_IMPLEMENTATION_PARTIAL_REQUIRES_SAFER_ROUTE`
- Old/current over-white residual: `375`
- Actual-new renderer residual: `375`
- Candidate-new residual: `0`
- Raw transparent PNG basis accepted: `false`

## Non-goals Preserved

- No change to `colorToF16Premul`, `blendF16PremulMode`, `SkBitmap.getPixel`, or
  `SkBitmap.getPixelAsSrgb`.
- No fixture branch, coordinate branch, selected-cell substitution, full-GM crop
  basis, or global hook mutation without boundary.
- No geometry, coverage, GPU, WGSL, threshold, fallback, Kadre, promotion, or
  score change.
- Historical artifacts FOR-329 through FOR-342 remain traceable and are not
  rewritten.

## Artifacts

- JSON: `reports/wgsl-pipeline/scenes/artifacts/f16-color-policy-boundary-for343/f16-color-policy-boundary-for343.json`
- Validator: `scripts/validate_for343_f16_color_policy_boundary.py`
- Report: `reports/wgsl-pipeline/2026-06-04-for-343-f16-color-policy-boundary.md`

## Validation

- `rtk python3 scripts/validate_for343_f16_color_policy_boundary.py`
- `rtk python3 scripts/validate_for342_circular_arcs_stroke_butt_adjacent_f16_color_policy_scoped_implementation.py`
- `rtk python3 scripts/validate_for341_circular_arcs_stroke_butt_adjacent_f16_color_policy_decision.py`
- `rtk ./gradlew --no-daemon pipelineSceneDashboardGate`
- `rtk git diff --check`
