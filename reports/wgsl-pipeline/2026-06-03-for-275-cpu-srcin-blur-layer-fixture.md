# FOR-275 CPU SrcIn Blur Layer Fixture

Linear: `FOR-275`

Scene cible: `m60-bounded-nested-rrect-clip`

Decision: `KEEP_EXPECTED_UNSUPPORTED`

FOR-275 ajoute une fixture CPU minimale pour `SkBlurMaskFilter(kNormal)` +
`SkColorFilters.Blend(RED, kSrcIn)`. Le renderer n'est pas modifie. La scene
M60 reste `expected-unsupported` avec fallback `coverage.nested-clip-visual-parity-below-threshold`:
CPU/reference `97.31` et GPU/reference
`98.48` restent sous le seuil strict
`99.95`.

## Fixture

| Element | Valeur |
|---|---:|
| Dimensions | 96 x 72 |
| Sigma | 1.366025 |
| Support rouge blur non clippe | 2292 |
| Support rouge conserve sous clip difference | 1104 |
| Pixels rouges perdus vers blanc/fond | 1164 |
| Part blanc/fond des pixels perdus | 100.0% |

## Separation Des Hypotheses

| Hypothese | Mesure | Verdict |
|---|---:|---|
| `saveLayer` / retention de fond | 0 pixel different du rendu direct | Non reproduit dans la fixture minimale |
| Etendue masque/flou sous clip actif | 1164 pixels rouges perdus vers blanc/fond | Reproduit |
| Blanc introduit seulement par layer | 0 pixel | Refuse |

## Sample Signe

| Pixel | Unclipped RGBA | Direct RGBA | Layered RGBA | Direct-unclipped | Layered-direct |
|---|---|---|---|---|---|
| 45,20 | `[255, 0, 0, 255]` | `[255, 255, 255, 255]` | `[255, 255, 255, 255]` | `[0, 255, 255, 0]` | `[0, 0, 0, 0]` |

## Conclusion

Hypothese dominante fixture:
`MASK_EXTENT_OR_ACTIVE_CLIP_TRUNCATION_REPRODUCED_LAYER_BACKGROUND_NOT_REPRODUCED`.

Prochaine action: auditer ou corriger de facon bornee l'ordre CPU
mask-filter/clip pour `SkBlurMaskFilter(kNormal)` avant toute promotion M60.
Ne pas promouvoir la scene tant que CPU/reference et GPU/reference ne prouvent
pas simultanement `99.95` avec routes et stats.

## Preservation

- `expected-unsupported` conserve.
- Fallback `coverage.nested-clip-visual-parity-below-threshold` conserve.
- Fallback `image-filter.crop-input-nonnull-prepass-required` conserve.
- Aucun readback/fallback, support large clip-stack, Ganesh, Graphite ou SkSL
  compiler ajoute.

## Validation

```text
rtk ./gradlew --no-daemon :kanvas-skia:test --tests org.skia.core.For275CpuSrcInBlurLayerFixtureTest
rtk python3 scripts/validate_for275_cpu_srcin_blur_layer_fixture.py
rtk python3 scripts/validate_for274_nested_rrect_cpu_reference_layer_audit.py
rtk python3 scripts/validate_for273_webgpu_solid_blur_srcin_fold.py
rtk ./gradlew --no-daemon pipelineSceneDashboardGate
rtk git diff --check
```

Machine artifact:
`reports/wgsl-pipeline/scenes/artifacts/m60-bounded-nested-rrect-clip/cpu-srcin-blur-layer-fixture-for275.json`
