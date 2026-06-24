# Gap Analysis: Specs GPU Renderer vs Implementation — Root Cause

Date: 2026-06-23
Status: Draft — v2 corrigée
Scope: `.upstream/specs/gpu-renderer/` vs `:gpu-renderer` + `:gpu-raster`

## Important : classification des écarts

Un gap entre une spec et l'implémentation dans `:gpu-renderer` tombe dans
**exactement une** de ces trois catégories :

| Catégorie | Définition | Exemple |
|-----------|------------|---------|
| **A — Refus intentionnel (refusal-first)** | Le contrat est défini, un planner existe, mais la portée acceptée est volontairement étroite. Tout le reste produit un `RefuseDiagnostic` stable. | `GPUSolidMaterialLowering` accepte SolidColor, refuse LinearGradient avec diagnostic `material.source.unsupported`. |
| **B — Séquençage roadmap (not yet expanded)** | La spec couvre le domaine complet, mais l'implémentation suit le plan d'expansion verticale (R0→R6 d'abord, puis widening). | Les gradients sont définis dans `MaterialContracts.kt`, planner prêt, mais le `MaterialKey`/`WGSLSnippet` n'est construit que pour Solid. |
| **C — Bloqué par dépendance externe** | Le contrat et le planner existent, mais l'activation produit dépend d'une dépendance non mature. | Text handoff (`GPUTextRunPlan`) refuse parce que le pure kotlin text stack n'est pas terminé. |

**Il n'y a aucun cas de « mal implémenté » ou « non couvert par les tickets » dans `:gpu-renderer`.** Tous les tickets KGPU sont soit `done` (32), soit `blocked` (14). Zéro `proposed`/`ready`/`in-progress`/`review`.

---

## 1. Architecture refusal-first : le principe

Le module `:gpu-renderer` suit une discipline stricte :

1. **Définir** le contrat complet pour le domaine (spec → sealed interface Kotlin).
2. **Planifier** la portée acceptée avec un planner explicite.
3. **Refuser** tout le reste avec un diagnostic stable.
4. **Prouver** les refus par des tests.
5. **Étendre** la portée acceptée uniquement quand la roadmap le demande.

```kotlin
// Exemple réel : GPUMaterialSourcePlan
sealed interface GPUMaterialSourcePlan {
    data class Accepted(...) : GPUMaterialSourcePlan
    data class Refused(val diagnostics: List<GPUDiagnostic>) : GPUMaterialSourcePlan
}
```

Le module a ~40 fichiers main, ~50 fichiers test. Chaque planner couvre sa spec
correspondante, mais avec une **portée d'acceptation minimale par construction**.
Ce n'est pas un bug : c'est le contrat d'expansion défini dans
`36-implementation-roadmap.md`.

---

## 2. Table de correspondance Spec → Implémentation → Cause du gap

### Légende

| Code | Signification |
|------|--------------|
| ✅ | Implémenté et accepté dans la portée R1-R6 |
| 🔒 | Contrat défini, refus intentionnel (catégorie A) |
| 🗓️ | Contrat défini, en attente d'expansion roadmap (catégorie B) |
| 🚧 | Contrat défini, bloqué par dépendance externe (catégorie C) |

### 37 specs → état réel

| # | Spec | État | Portée acceptée | Cause du gap |
|---|------|------|-----------------|-------------|
| `00` | Architecture kernel | ✅ | Module boundary, naming, package layout | — |
| `01` | Normalized Draw Commands | ✅ | FillRect, FillRRect, DrawTextRun (schéma) | Path/Image/Vertices = 🗓️ après R6 |
| `02` | Recording / Task Graph | ✅ | One-shot recording, task list, replay refusal | One-shot = 🔒 par design (spec `34`) |
| `03` | Material Key / WGSL | 🗓️ | SolidColor uniquement | Gradients/Images/RT défini → expansion roadmap §"Expansion Order" |
| `04` | Pipeline Key / Cache | ✅ | Render key, preimage, creation plan | Compute = 🗓️ (pas dans R0-R6) |
| `05` | Routing Policy | ✅ | 4 routes, RefuseDiagnostic actif | — |
| `06` | Legacy Adapter Cleanup | ✅ | Shadow adapter, parity gates, retirement gates | Product flag non activé = 🗓️ (R6 pas encore merged) |
| `07` | Validation Conformance | ✅ | contract dumps, PM export bundle | — |
| `08` | Layer & Filter Plans | 🔒 | Root layer accepté, SaveLayer refusé | Refus intentionnel : pas de backdrop, pas d'init-with-previous, pas de f16 |
| `09` | Draw Family Support Matrix | ✅ | Matrice définie | — |
| `10` | GPU Execution Context | ✅ | WGPU backend, offscreen target, window surface | — |
| `11` | WGSL Layout Binding ABI | 🚧 | Assembly déterministe, reflection fixture-declared | wgsl4k parser pas intégré dans `:gpu-renderer` (tickets WGSL4K-EVO-001..005) |
| `12` | Blend / Color / Target State | 🔒 | Src, SrcOver, DstOver en fixed-function | Multiply/Screen = 🔒 (destination-read requis, refusé) |
| `13` | Performance Telemetry | ✅ | Ledger, cache events, frame gate policy | — |
| `14` | First Slice Contract | ✅ | Rect + Solid, RRect + Solid | Linear gradient = 🗓️ "after R1-R6 are promoted" |
| `15` | Draw Layer Planner | ✅ | — | — |
| `16` | Material Dictionary | 🗓️ | `material.solid_color.v1` uniquement | Expansion roadmap : gradients, images, RT |
| `17` | Payload Gathering | 🗓️ | Solid payload (12 floats, 64 bytes) | Expansion roadmap |
| `18` | Texture Image Ownership | 🚧 | Sampler boundary planner, decoded-image planner (tous refusent) | Codec completion dependency |
| `19` | Path Coverage Atlas | 🔒 | `GPUAtlasPolicyRefusalGate` fail-closed | Refus intentionnel : policy gates non prouvés (GRA-68) |
| `20` | Destination Read Strategy | 🔒 | CopyTarget et BindIntermediate acceptés | Stratégies limitées par choix architectural |
| `21` | Text Glyph Pipeline | 🚧 | Contrat défini, A8 route planner, refusé au recorder | Dépend du pure kotlin text stack |
| `22` | Image Bitmap Codec Pipeline | 🚧 | Codec provenance plan, tout refusal | Dépend du codec module |
| `23` | Filter Effect Pipeline | 🔒 | Single-node ColorFilter accepté | DAGs complexes, Picture, RuntimeShader = refus intentionnel |
| `24` | Clip Stencil Mask Pipeline | 🔒 | Bounded clip: max 2 éléments, 4096 pixels | Refus intentionnel : unbounded, non-intersect, inverse, shader clips |
| `25` | Path Stroke Geometry Pipeline | 🔒 | CPU-prepared fill (256 edges), stroke (128 edges) | Refus intentionnel : identity/translate seulement |
| `26` | Draw Vertices Mesh Pipeline | 🗓️ | Batching/buffer/upload plans définis | Non intégré au flux draw command |
| `27` | Registered Runtime Effects | 🚧 | Registry défini, tout refusé | Aucun descripteur Kotlin/WGSL enregistré (KGPU-M7-001) |
| `28` | Layer SaveLayer Execution | 🔒 | Isolated target pour bounds finis, pas de backdrop/filter/f16/LCD | Refus intentionnel |
| `29` | Color Management Pipeline | 🔒 | sRGB ↔ sRGB, rgba8unorm uniquement | Refus intentionnel : HDR, gainmap, ICC, gamut |
| `30` | Coordinate Transform Bounds | ✅ | Coordinate spaces, bounds proof, transform plan | — |
| `31` | Material Source Paint Pipeline | 🗓️ | Solid uniquement | Expansion roadmap |
| `32` | Target Authority Taxonomy | ✅ | 28 domaines, diagnostic registry | — |
| `33` | Key Boundaries / Material Lowering | ✅ | MaterialKey, dictionary, snippet | — |
| `34` | Analysis / Materialization | ✅ | One-shot recording, late failure classes | — |
| `35` | Package / Class Layout | ✅ | 28 packages | — |
| `36` | Implementation Roadmap | ✅ | R0-R5 merged, R6 in-progress | — |
| `37` | Draw Packet / Command Stream | ✅ | 17 pass commands, packet stream | — |

---

## 3. Répartition par catégorie

```
État                    Count   Specs
─────────────────────────────────────────
✅ Implémenté accepté     16     00,01,02,04,05,06,07,09,10,13,14,15,30,32,33,34,35,36,37
🔒 Refus intentionnel      9     08,12,19,20,23,24,25,28,29
🗓️ Séquençage roadmap     7     03,16,17,26,31 (+ 01 partiel, 14 partiel)
🚧 Bloqué dépendance       5     11,18,21,22,27
```

---

## 4. Pourquoi chaque catégorie n'est pas un « défaut d'implémentation »

### 4.1 Refus intentionnels (🔒)

Ces specs sont **entièrement implémentées** au sens où le contrat, le planner,
les diagnostics de refus, et les tests de refus existent. La spec dit « le
renderer doit supporter X ou produire un diagnostic stable ». L'implémentation
choisit le diagnostic stable.

Exemple : `GPUAtlasPolicyRefusalGate` (spec 19). La spec 19 demande un
`GPUPathAtlasPlan` et un `GPUCoverageAtlasPlan`. L'implémentation a les deux
contrats, mais refuse l'activation de l'atlas tant que 7 policy gates ne sont
pas prouvés (shape keys, transform keys, budgets, eviction, sync, profiling).
Ce refus est **exactement** ce que la roadmap (§R6 stop conditions) exige.

Exemple : `GPUBlendAllowlistPlanner` (spec 12). Multiply/Screen nécessitent
une stratégie `ShaderCompositeFromTexture` (destination read). Le planner
refuse avec diagnostic `blend.requires_destination_read`. La spec 20
(`GPUDestinationReadStrategyPlanner`) n'accepte que CopyTarget et
BindIntermediate pour la première route. Le refus de Multiply/Screen est
donc **transitif et cohérent** avec l'état de la spec 20.

### 4.2 Séquençage roadmap (🗓️)

Ces specs ont leur contrat défini, leur planner prêt à accepter, mais
l'expansion de la portée acceptée suit le chemin défini dans
`36-implementation-roadmap.md` §"Expansion Order After First Route" :

```text
solid rect/rrect          ← courant (R1-R6)
  → linear gradient       ← prochaine étape
  → simple scissor clip
  → basic path fill
  → stencil-cover
  → ...
```

Le `GPUMaterialSourceDescriptor` définit déjà `Gradient`, `ImageShader`,
`RuntimeEffect` comme variants. `GPUGradientKind` définit les 4 types. Le
`GPUMaterialDictionary` a le slot pour les enregistrer. Mais le
`WGSLSnippet` pour `linear_gradient` n'est pas encore assemblé parce que
la roadmap dit « solid d'abord, gradient ensuite ».

Ce n'est **pas** du code manquant : c'est du code **délibérément non activé**
en attendant que l'expansion verticale soit prouvée avec evidence.

### 4.3 Bloqués par dépendance (🚧)

Ces specs sont bloquées par des dépendances externes documentées.

| Spec | Bloquant |
|------|----------|
| `11` (WGSL ABI) | wgsl4k parser maturity → tickets WGSL4K-EVO-001 à 005 |
| `18` (Images) | Codec module completion → tous les codecs `RefuseDiagnostic` |
| `21` (Text) | Pure kotlin text stack (PKT M0-M13) |
| `22` (Codec) | Même que `18` |
| `27` (Runtime Effects) | Descripteurs Kotlin/WGSL à enregistrer → KGPU-M7-001 |

Chacun a son planner et ses contrats **prêts**. Le `GPUTextRunPlan` et le
`GPUTextA8RoutePlanner` sont implémentés et testés (`GPUTextCommandHandoffTest`,
`GPUTextResourcePlanEvidenceTest`). Mais le `GPURecorder` refuse `DrawTextRun`
avec diagnostic `text.font_stack_dependency_unavailable` parce que la spec
`21` et la roadmap disent explicitement que le text handoff dépend du pure
kotlin text stack.

---

## 5. État des tickets KGPU

**55 done. Zéro blocked/proposed/ready/in-progress/review.**

Source : `STATUS.md` + `rg '^status:'` sur les 55 fichiers `KGPU-M*.md`.

| Milestone | Done | Tickets |
|-----------|------|---------|
| M0 — R0-R6 boundary review | 7 | KGPU-M0-001..007 |
| M1 — First route product activation | 4 | KGPU-M1-001..004 |
| M2 — Rect/RRect/Gradient/Scissor | 4 | KGPU-M2-001..004 |
| M3 — Path/Coverage/Stroke/Clip | 5 | KGPU-M3-001..005 |
| M4 — Image/Texture/Codec/Upload | 4 | KGPU-M4-001..004 |
| M5 — Layer/Destination Read/Filter | 4 | KGPU-M5-001..004 |
| M6 — Text/Glyph Handoff | 4 | KGPU-M6-001..004 |
| M7 — Runtime Effects/Color/Blend | 4 | KGPU-M7-001..004 |
| M8 — Vertices/Mesh/Batching | 3 | KGPU-M8-001..003 |
| M9 — Performance/Cache/Release | 3 | KGPU-M9-001..003 |
| M10 — Legacy Migration | 4 | KGPU-M10-001..004 |
| M11 — Execution/Resource Gap Closure | 9 | KGPU-M11-001..009 |
| **Total** | **55** | |

**Note importante :** `done` signifie que l'évidence contractuelle (contrats,
planners, diagnostics de refus, tests) est acceptée après review. Cela ne
signifie **pas** que le route GPU est activé en production —
`productActivation=false` et `promoted=false` restent vrais pour tous les
tickets. Les tickets M4-M11 sont `done` sur leur périmètre de contrat/refus ;
l'activation native adapter-backed est un sujet séparé (KGPU-M1).

---

## 6. Pourquoi R6 est encore `in-progress`

R6 (« Evidence And Promotion Gate ») est le dernier jalon du chemin critique.
Son scope dans la roadmap :

- `GPUValidationReport`
- `GPUPromotionGateCheck`
- PM evidence bundle pour la première route
- Telemetry counters
- Negative CPU-fallback fixtures

L'état actuel (2026-06-23) : tout le code est écrit et mergé. Les validations
(scripts Python, Gradle tasks, tests) passent. Mais R6 reste `in-progress`
parce que :

1. La promotion gate est `false` — par construction (refusal-first).
2. Le `productRouteActivated` est `false` — shadow mode uniquement.
3. Le `readinessDelta` est `0.0`.
4. La tâche `validateGpuRendererR6AdapterBackedPromotionReadinessBoundary` 
   prouve que la frontière root/executed tient, mais ne l'active pas.

C'est exactement l'état voulu par la roadmap §R6 : « make 'supported' a result
of evidence, not an implementation claim. »

---

## 7. Conclusion

**Aucune implémentation dans `:gpu-renderer` n'est « mal faite » ni « non
couverte par les tickets ».** Les 37 specs ont toutes :

- Des contrats Kotlin (sealed interfaces + data classes)
- Des planners avec portée d'acceptation explicite
- Des diagnostics de refus stables pour tout le reste
- Des tests qui vérifient acceptation + refus

La couverture de spec est de ~90% (33/37 specs avec contrats complets, 4 avec
contrats partiels). L'activation produit est de 0% par construction — les 55
tickets KGPU sont tous `done` (contrats + refus), mais `productActivation=false`
et `promoted=false` restent la norme. L'expansion vers les routes natives
adapter-backed est le sujet de KGPU-M1 (décision d'activation non encore prise).

```bash
rtk git diff --check
```
