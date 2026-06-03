# FOR-285 M60 Post-Payload Residual

Linear: `FOR-285`

Scene: `m60-bounded-nested-rrect-clip`

Decision: `NEXT_FIX_CPU_ACTIVE_AA_DIFFERENCE_CLIP_STORE_ORDER_NOT_PAYLOAD`

Residual classification: `POST_DISPATCH_LAYER_CLIP_STORE_ORDER_RESIDUAL`

Next action: `INSTRUMENT_CPU_MASK_FILTER_A8_SRCIN_RUNTIME_STORE_UNDER_ACTIVE_AA_DIFFERENCE_CLIP`

## Resultat Court

FOR-284 a corrige le contrat local CPU A8 solid +
`SkColorFilters.Blend(..., kSrcIn)`, mais les artefacts M60 post-FOR-284
gardent un delta nul sur la pleine scene. La route source atteint le callsite
corrige; les compteurs recalcules depuis les PNG correspondent aux compteurs
post-FOR-284. Le residu actionnable est donc l'ordre composite/store sous
clip AA actif, pas un nouveau changement global de payload, `blend`, `setPixel`
ou WebGPU.

## Pleine Scene Avant / Apres FOR-284

| Mesure | Avant FOR-284 | Post-FOR-284 | Delta |
|---|---:|---:|---:|
| CPU similarity | 97.31 | 97.31 | 0.0 |
| CPU matching pixels | 908439 | 908439 | 0.0 |
| CPU max channel delta | 237 | 237 | 0.0 |
| CPU/reference >32 | 15726 | 15726 | 0.0 |
| GPU similarity | 98.48 | 98.48 | 0.0 |
| GPU/reference >32 | 2869 | 2869 | 0.0 |

## Fenetres FOR-278: Buckets Rouge / Blanc

| Zone | Cibles CPU/ref >32 | CPU blanc/layer | CPU red-tint | Reference red-tint | GPU red-tint |
|---|---:|---:|---:|---:|---:|
| `draw_oval_outer_boundary` | 59 | 59 | 0 | 32 | 34 |
| `difference_oval_inner_boundary` | 18 | 14 | 0 | 12 | 12 |
| `halo_interior` | 12 | 5 | 0 | 12 | 12 |

Combined target pixels: 89.
CPU combined white/layer: 78.
CPU combined red-tint: 0.
Reference combined red-tint: 56.

## Comparaison FOR-283 / FOR-284

| Preuve | Valeur |
|---|---:|
| FOR-283 target pixels | 89 |
| FOR-283 dispatch red payload | 89 |
| FOR-283 non-zero cov | 89 |
| FOR-283 dst white before blend | 89 |
| FOR-283 blend red tint before store | 89 |
| FOR-283 blend red dominant before store | 82 |
| FOR-283 CPU readback red dominant | 9 |
| FOR-283 CPU readback white/layer | 78 |
| FOR-284 patch target pixels | 89 |
| FOR-284 patch dispatch red payload baseline | 89 |

## Route Et Diagnostics

| Champ | Valeur |
|---|---|
| CPU route | `cpu.coverage.nested-rrect-clip-oracle` |
| GPU route | `webgpu.coverage.nested-rrect-clip.expected-unsupported` |
| GPU status | `expected-unsupported` |
| Fallback visual parity | `coverage.nested-clip-visual-parity-below-threshold` |
| Fallback crop | `image-filter.crop-input-nonnull-prepass-required` |
| Source route drawRRect -> drawPathWithMaskFilter | `True` |
| FOR-284 SrcIn branch present | `True` |

## Decision

`NEXT_FIX_CPU_ACTIVE_AA_DIFFERENCE_CLIP_STORE_ORDER_NOT_PAYLOAD`.

Cause retenue: `POST_DISPATCH_LAYER_CLIP_STORE_ORDER_RESIDUAL`. Route differente: non. Masque ou
extent nul: non sur les fenetres bornees. Artefact obsolete: non pour les
preuves versionnees, car les PNG recalculent CPU/reference >32 =
15726.

La prochaine correction doit instrumenter puis corriger le store/composite CPU
du draw mask-filter A8 SrcIn sous `activeAaClip` difference, en observant les
arguments runtime de `dispatchBlend`, la couverture appliquee dans `blend`,
la valeur ecrite par `setPixel`, et la valeur relue apres le draw. M60 reste
non promue avec `coverage.nested-clip-visual-parity-below-threshold` et
`image-filter.crop-input-nonnull-prepass-required` restent conserves.

Artefact: `reports/wgsl-pipeline/scenes/artifacts/m60-bounded-nested-rrect-clip/m60-post-payload-residual-for285.json`
