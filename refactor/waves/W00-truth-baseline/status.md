# W00 — Baseline de vérité Skia

Date de génération : 2026-08-30  
Commit source : `7f9c8fdd7321efeae6e75b42c455610bbf10f38f`
(`fix: isolate non-fatal GM setup errors`)

## État de la gate W00 : NON ATTEINTE

La preuve générée est complète et le score audit est strict, mais la gate W00
stricte reste **non atteinte** car la quarantaine temporaire de ressources
contient `jpg-color-cube`. Cette ligne ne constitue ni une exclusion `font` ou
`codec`, ni un écart Skia accepté, ni une tentative, ni un rendu, ni un échec
terminal.

La quarantaine publiée est fermée et nominale :

| GM | Scope | Raison | Owner | Route |
| --- | --- | --- | --- | --- |
| `jpg-color-cube` | `quarantined-resource-limit` | `legacy-snapshot-262144-draw-rects-not-practically-renderable` | `legacy-renderer-remediation` | `excluded:quarantined-resource-limit` |

Sa preuve est le run exhaustif interrompu après 1 h 21 min 29 s, toujours
CPU-actif dans `Surface.makeImageSnapshot()` après l'assemblage legacy de
262 144 `drawRect`.

## Inventaire généré

- Schéma : `gpu-gm-inventory-v3`.
- 631 GMs enregistrées ; aucun provider de famille `UNKNOWN`.
- Score audit : strict, 0 score orphelin.
- Aucune route `excluded:blocking-by-policy`.
- `mesh_zero_init` reste `eligible`, avec `setupState = FAILED`,
  `attempted = false`, `terminalFailure = false` et le diagnostic
  `STUB.MESH.GPU_ZERO_INIT`.

### Comptes par scope

| Scope | Nombre |
| --- | ---: |
| `eligible` | 450 |
| `excluded-codec` | 54 |
| `excluded-font` | 126 |
| `quarantined-resource-limit` | 1 |

### Comptes par famille

| Famille | Nombre |
| --- | ---: |
| `BLUR` | 43 |
| `CLIP` | 52 |
| `COLOR` | 7 |
| `COMPOSITE` | 109 |
| `GRADIENT` | 54 |
| `IMAGE` | 159 |
| `MESH` | 16 |
| `PATH` | 86 |
| `RUNTIME_EFFECT` | 23 |
| `TEXT` | 82 |

### Exécution

| Mesure | Nombre |
| --- | ---: |
| Attempted | 379 |
| Rendered (`renderAvailable`) | 84 |
| Échecs terminaux | 295 |
| Échecs de setup | 75 |

## Diagnostics terminaux groupés

Les 295 échecs terminaux se répartissent exactement comme suit :

| Diagnostic | Nombre |
| --- | ---: |
| `geometry.path.fan_budget_exceeded: Prepared Surface operation could not be lowered.` | 36 |
| `invalid.core_primitive.coverage_sample.stencil_1x_requires_single_sample: Stencil1x requires a single-sample frame and cannot be relabeled as multisample coverage.` | 1 |
| `invalid.frame_plan.destination_read_unbound: Destination-reading packet has no exact Task 5 copy or refusal association` | 1 |
| `invalid.preflight.core_primitive_clip_producer_authority: Resource-backed clip consumer is missing its sealed producer topology.` | 1 |
| `invalid.preflight.core_primitive_direct_geometry_resources: Direct CorePrimitive builder uniform slab seal contradicts current packet or limit authority.` | 3 |
| `invalid.preflight.core_primitive_path_stencil: A mixed direct packet is missing its exact uniform32 slab authority.` | 1 |
| `invalid.preflight.core_primitive_path_stencil: Path stencil CorePrimitive requires exactly one prepared render pass.` | 1 |
| `invalid.preflight.core_primitive_path_stencil_msaa_authority: Path stencil MSAA authority requires one unique path-bearing render scope.` | 1 |
| `invalid.surface.prepared.frame-build-contract: Prepared Surface frame construction violated an internal contract.` | 2 |
| `unsupported.clip.complex_stack: unsupported.clip.complex_stack` | 1 |
| `unsupported.composite.clip: Prepared Surface composite could not be lowered.` | 3 |
| `unsupported.composite.operation: Prepared Surface composite could not be lowered.` | 6 |
| `unsupported.composite.paint: Prepared Surface composite could not be lowered.` | 19 |
| `unsupported.composite.preflight: Prepared Surface composite could not be lowered.` | 1 |
| `unsupported.core_primitive.coverage_sample.scalar_aa_not_promoted: ScalarAA coverage is promoted only by the exact single-sample B3.5b analytic route.` | 26 |
| `unsupported.core_primitive.geometry.invalid: Core primitive geometry cannot be lowered exactly by the current canonical route.` | 9 |
| `unsupported.core_primitive.material.non_solid: Core primitive geometry cannot be lowered exactly by the current canonical route.` | 20 |
| `unsupported.core_primitive.material.path_stencil: Core primitive geometry cannot be lowered exactly by the current canonical route.` | 2 |
| `unsupported.geometry.path_empty_inverse_unbounded: unsupported.geometry.path_empty_inverse_unbounded` | 1 |
| `unsupported.image.atlas.source_blend: Prepared Surface operation could not be lowered.` | 1 |
| `unsupported.image.native_binding: Prepared Surface operation could not be lowered.` | 15 |
| `unsupported.image.nine_geometry: Prepared Surface operation could not be lowered.` | 1 |
| `unsupported.image.pixel.length: Prepared Surface operation could not be lowered.` | 1 |
| `unsupported.image.src_bounds: unsupported.image.src_bounds` | 2 |
| `unsupported.layer.bounds_unbounded: Prepared Surface composite could not be lowered.` | 1 |
| `unsupported.material.gradient_antialias: unsupported.material.gradient_antialias` | 6 |
| `unsupported.material.gradient_tile_mode_unsupported: unsupported.material.gradient_tile_mode_unsupported` | 4 |
| `unsupported.material.mapping.gradient_interpolation: unsupported.material.mapping.gradient_interpolation` | 1 |
| `unsupported.material.mapping.image_alpha_type: Prepared Surface operation could not be lowered.` | 2 |
| `unsupported.material.mapping.image_local_matrix_affine: Prepared Surface operation could not be lowered.` | 1 |
| `unsupported.material.mapping.image_tile_mode: Prepared Surface operation could not be lowered.` | 2 |
| `unsupported.material.mapping.linear_gradient_local_matrix_perspective: unsupported.material.mapping.linear_gradient_local_matrix_perspective` | 1 |
| `unsupported.material.mapping.linear_gradient_stop_count: unsupported.material.mapping.linear_gradient_stop_count` | 18 |
| `unsupported.material.mapping.local_matrix: Prepared Surface operation could not be lowered.` | 4 |
| `unsupported.material.radial_gradient_stop_count: unsupported.material.radial_gradient_stop_count` | 6 |
| `unsupported.material.source_unimplemented: unsupported.material.source_unimplemented` | 19 |
| `unsupported.material.sweep_gradient_stop_count: unsupported.material.sweep_gradient_stop_count` | 4 |
| `unsupported.native-mask-blur.mixed-local-sizes: The top-level mask blur lane serializes one local mask size per frame.` | 2 |
| `unsupported.pipeline.capability_missing: unsupported.pipeline.capability_missing` | 10 |
| `unsupported.prepared-surface.sample-plan: The mixed prepared route admits only single-sample render runs without continuation.` | 1 |
| `unsupported.prepared-surface.vertices-multi-run: Prepared-vertices materialization supports one exact render run per frame.` | 2 |
| `unsupported.recording.core_primitive_analytic_shape_clip: Prepared analytic shapes currently require NoClip or ScissorOnly execution.` | 1 |
| `unsupported.stroke.cap: unsupported.stroke.cap` | 4 |
| `unsupported.stroke.expansion_budget_exceeded: unsupported.stroke.expansion_budget_exceeded` | 1 |
| `unsupported.stroke.join: unsupported.stroke.join` | 1 |
| `unsupported.stroke.rect_anti_alias: Prepared Surface operation could not be lowered.` | 9 |
| `unsupported.stroke.width_budget: unsupported.stroke.width_budget` | 5 |
| `unsupported.stroke.width_invalid: unsupported.stroke.width_invalid` | 25 |
| `unsupported.surface.prepared.mixed-composite-topology: Prepared Surface composite frames cannot cover the picture topology.` | 3 |
| `unsupported.vertices.material: Prepared Surface operation could not be lowered.` | 6 |
| `unsupported.vertices.topology: Prepared Surface operation could not be lowered.` | 1 |

## Politique pixel et commande reproductible

La politique pixel est inchangée : les 553 scores conservés ont exactement la
même valeur qu'avant W00 ; les seules 136 suppressions sont les clés orphelines
auditées, sans ajout de score.

```text
rtk ./gradlew :integration-tests:skia:generateSkiaGmInventory
```

Résultat : `BUILD SUCCESSFUL in 1m 25s`.

La preuve complète est
[`source-inventory.json`](../../../reports/gpu-renderer/evidence/gm-inventory/source-inventory.json).
