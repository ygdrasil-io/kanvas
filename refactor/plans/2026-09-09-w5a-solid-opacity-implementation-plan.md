# W5a Common Solid and Opacity Material Implementation Plan

## Task 1 implementation note

The in-scope public `Shader.SolidColor`, `Shader.Opacity`, `Paint`, and `Picture` inputs are immutable value objects.  The only directly mutable public upstream pixel carrier found during Task 1 is `Bitmap.pixels`, which is consumed by `Shader.Image` and is explicitly outside the W5a Solid/Opacity subset.  A post-capture mutation test for Solid/Opacity would therefore require introducing mutability or Image sampling outside this task.  The retained strongest public proof records a non-trivial nested-opacity Picture, serializes/restores it, replays it, and verifies pixels against the independent envelope.

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development to implement this plan task-by-task. Each implementation task follows RED → GREEN → refactor and is followed by a read-only Sol spec/quality review.

**Goal:** Introduire l'autorité material plan-first commune de W5 et fermer Solid + Opacity sur toutes les familles déjà admises par les routes préparées, sans changer la géométrie W4 ni conserver un fallback material silencieux sur les lanes promues.

**Architecture:** `:gpu-plan` construit une seule fois un `EffectiveMaterialPlan`, un `MaterialProgramPlan` et un `MaterialBindingPlan` immuables à partir des faits sémantiques du draw. Les draws W5 référencent une table material scellée du `RenderGraph`; les witnesses W3/W4 historiques passent uniquement par un adaptateur legacy explicite. `:gpu-renderer` matérialise le plan scellé et les routes Rect/RRect/Path/Points/Text/Vertices consomment la même autorité Solid/Opacity, sans reconstruire leur propre descripteur.

**Tech Stack:** Kotlin/JVM/JS, `:render-ir`, `:gpu-plan`, `:gpu-renderer`, `:kanvas`, WebGPU/WGPU4K, Gradle, `rtk`.

**Spec:** `refactor/specs/2026-09-09-w5-material-graph-design.md`

## Global Constraints

- Branche locale : `codex/w5a-solid-opacity`, empilée sur `codex/w5-material-graph`; la future PR cible la branche de design W5.
- Exécution continue : les tâches 1–8 sont enchaînées sans checkpoint utilisateur; arrêt uniquement sur contradiction du contrat, mutation externe irréversible ou capability physique réellement absente.
- W5a couvre uniquement `Transparent`, `Solid` et `Opacity`; blends hors `SRC_OVER`, gradients, images, matrices locales, filters, noise et runtime effects restent aux tranches suivantes.
- W5a ajoute le wrapper public `Shader.Opacity(shader, alphaF32)`, snapshoté et sérialisé vers `MaterialNode.Opacity`; l'alpha fini `[0,1]` est validé avant copie du child.
- Le contrat interne est RGBA linéaire prémultiplié. Pour un shader Solid, l'alpha du `Paint.color` module exactement une fois les quatre composantes; son RGB est ignoré.
- `Opacity(child, 1)` se neutralise; deux opacités finies adjacentes se multiplient dans l'ordre F32 normatif; zéro devient transparent seulement lorsque la distinction reste inobservable sous `SRC_OVER` sans effet.
- Un draw W5 possède exactement une `MaterialPlanRef` et une autorité de blend. Il ne possède pas simultanément une couleur legacy et une référence material.
- Les witnesses W3/W4 historiques et leurs résultats publics restent valides. Leur `ColorF32` n'est accessible qu'au travers d'un adaptateur legacy versionné et ne peut pas être produit par un compiler W5.
- Structure et valeurs sont séparées : couleurs/alpha changent les bindings, jamais l'identité structurelle du programme.
- Toute table, liste et payload publié est défensivement snapshoté avant `Ready`; aucun handle, WGSL ou type WebGPU n'entre dans `:gpu-plan`.
- Les objets géométriques restent exclusivement dans `:math:geometry`; l'orchestration des transforms reste dans `:math:matrix`; toute nouvelle valeur numérique publique suit I32/I64/F32/F64.
- Aucun fallback material silencieux après sélection W5. Un refus est typé avant `Ready`, ou terminal après ownership de la frame; une frame refusée n'alloue rien.
- Tests comportementaux publics uniquement : `Surface`, `Canvas`, `Picture`, `render()`, pixels et diagnostics publics. Aucun test de structure, source shape, private/internal, reflection, call count, cache identity, scope/counter ou infrastructure du code.
- Les tests doivent être mutation-sensitive : une suppression de l'opacity, une double application de l'alpha, une confusion sRGB/linear, une autorité couleur concurrente ou une reconstruction après capture doit les faire échouer.
- Aucun test ou changement `font`, codec, GM Skia, dashboard, baseline, `:integration-tests:skia` ou `jpg-color-cube`.
- Un fresh implementation agent adapté par tâche; Sol uniquement pour les reviews spec/quality read-only après chaque commit. Le controller ne corrige pas lui-même un finding d'agent : il redonne la tâche à l'implementer jusqu'à `READY`.

## Gate W5a

La tranche n'est fermée que lorsque les cellules publiques suivantes passent avec alpha non trivial, mutation post-capture et `SRC_OVER` :

| Famille | Preuve W5a |
| --- | --- |
| Rect | intégral hard-edge et fractionnaire analytic AA |
| RRect | analytic AA avec coins non triviaux |
| Path fill | direct triangle et stencil cover |
| Path stroke | stroke et hairline sur les capabilities W4 déjà admises |
| Point(s) | point et séquence multi-point préparés |
| Text | glyph/run déjà résolu; aucune génération de font |
| Vertices/Mesh | triangle préparé avec et sans couleurs vertex |

Une frame publique mixte doit également combiner au minimum Rect, RRect et Path avec deux valeurs d'alpha différentes, puis produire les mêmes pixels après mutation des objets publics sources.

## Shared Interfaces

`gpu-plan` :

```kotlin
public value class MaterialPlanRef(public val indexI32: Int)

public sealed interface MaterialProgramPlan {
    public val versionI32: Int
    public val structuralId: MaterialProgramPlanId

    /** Graphe d'opérations F32 fini utilisé à la fois par le générateur et l'oracle. */
    public fun copyNumericOperationGraphV1(): NumericOperationGraphV1

    public data object TransparentV1 : MaterialProgramPlan
    public data object SolidLinearPremulV1 : MaterialProgramPlan
    public class OpacityV1(public val child: MaterialPlanRef) : MaterialProgramPlan
}

public sealed interface MaterialBindingPlan {
    public val versionI32: Int

    public class EmptyV1 : MaterialBindingPlan
    public class SolidRgbaF32V1 private constructor(rgbaF32: ColorF32) : MaterialBindingPlan {
        public fun copyRgbaF32(): ColorF32
    }
    public class OpacityF32V1 private constructor(public val alphaF32: Float) : MaterialBindingPlan
}

public data class MaterialPlanEntry(
    public val program: MaterialProgramPlan,
    public val bindings: MaterialBindingPlan,
)

public class MaterialPlanTable private constructor(entries: List<MaterialPlanEntry>) {
    public val sizeI32: Int
    public fun entry(ref: MaterialPlanRef): MaterialPlanEntry
    public fun entries(): List<MaterialPlanEntry>
}

public sealed interface PlanDrawMaterialAuthority {
    public data class MaterialV1(public val ref: MaterialPlanRef) : PlanDrawMaterialAuthority
    public class LegacyColorV1 private constructor(colorF32: ColorF32) : PlanDrawMaterialAuthority
}
```

Le nom final peut être ajusté lors de l'implémentation, mais les invariants sont obligatoires : référence indexée I32, version explicite, programme sans valeur dynamique, bindings sans WGSL/handle, snapshot défensif, union exclusive legacy/material, et `NumericOperationGraphV1` scellé couvrant conversion sRGB, prémultiplication, opacity, coverage, `SRC_OVER`, clamp et quantification.

## File Map

| Responsabilité | Créations principales | Modifications principales |
| --- | --- | --- |
| Plans material | `gpu-plan/.../MaterialPlan.kt`, `EffectiveMaterialPlanner.kt`, `W5aPlanDiagnostics.kt` | `PlanPasses.kt`, `RenderGraph.kt`, identities/seals |
| Contrat numérique | `gpu-plan/.../NumericOperationGraphV1.kt`, `kanvas/.../surface/WgslFloatEnvelopeV1Oracle.kt` | générateur material commun |
| API opacity | — | `Shader.kt`, `DisplayOpSnapshot.kt`, `PaintSceneAdapter.kt`, codec `Picture` |
| Géométries W4 | — | compilers W3/W4a/W4b/W4c/W4d/W4e et witnesses associés |
| Materializer commun | `gpu-renderer/.../planning/W5aMaterialPlanLowerer.kt` | `GPUPreparedMaterialProgramCompiler`, lowerers W3/W4 |
| Points | — | route core primitive préparée et capture/admission si nécessaire |
| Text | — | `GPUPreparedTextLowerer`, payload/composer material |
| Vertices | — | `GPUPreparedVerticesLowerer`, payload/composer material |
| Preuves publiques | `kanvas/.../surface/W5aSolidOpacityCpuOracle.kt`, `W5aMaterialSurfacePixelTest.kt` | tests publics existants si une fixture y appartient déjà |
| Suivi | ce plan, `refactor/waves/W05-material-graph/status.md` | `refactor/README.md` |

---

### Task 1: Autorité EffectiveMaterial Solid/Opacity et premier Rect public

**Files:**
- Create: `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/MaterialPlan.kt`
- Create: `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/EffectiveMaterialPlanner.kt`
- Create: `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/W5aPlanDiagnostics.kt`
- Create: `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/NumericOperationGraphV1.kt`
- Create: `kanvas/src/test/kotlin/org/graphiks/kanvas/surface/W5aSolidOpacityCpuOracle.kt`
- Create: `kanvas/src/test/kotlin/org/graphiks/kanvas/surface/WgslFloatEnvelopeV1Oracle.kt`
- Create: `kanvas/src/test/kotlin/org/graphiks/kanvas/surface/W5aMaterialSurfacePixelTest.kt`
- Modify: `kanvas/src/main/kotlin/org/graphiks/kanvas/paint/Shader.kt`
- Modify: `kanvas/src/main/kotlin/org/graphiks/kanvas/canvas/DisplayOpSnapshot.kt`
- Modify: `kanvas/src/main/kotlin/org/graphiks/kanvas/render/ir/PaintSceneAdapter.kt`
- Modify: `kanvas/src/main/kotlin/org/graphiks/kanvas/picture/Picture.kt`
- Modify: `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/PlanPasses.kt`
- Modify: `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/RenderGraph.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/materials/GPUPreparedMaterialProgram.kt`
- Modify: Rect planner/lowerer files reached by the failing public fixture

- [x] Add public RED tests rendering an integral Rect with `Shader.Opacity(Shader.SolidColor(...), alpha)` nested at least twice, non-trivial shader alpha and non-trivial `Paint.color.alpha`; cover opacity zero, one and a non-trivial product.
- [x] Add a `Picture` round-trip fixture proving that the public Opacity wrapper and its F32 alpha survive serialization before rendering.
- [x] Compare every channel to the independent `WgslFloatEnvelopeV1` oracle: only a singleton or two adjacent RGBA8 codes may pass. Reject a fixture whose envelope is wider or non-adjacent; do not assert a host-language exact float as the portable contract.
- [x] Retain the strongest available immutable-input proof described above: rebinding caller variables after recording and public Picture round-trip cannot change the captured source. Mutable Path, vertices and glyph collections provide the subsequent mutation-sensitive proofs; no mutable Solid/Opacity API was fabricated.
- [x] Run only the new public test and record the semantic failure, not an implementation detail.
- [x] Add the public `Shader.Opacity`, bounded snapshot traversal, IR mapping and versioned Picture encoding before implementing its planner path.
- [x] Implement iterative bounded normalization of `DrawNode` into `EffectiveMaterialPlan` for Solid/Opacity/Transparent; reject all other nodes with stable W5a diagnostics. Preserve public wrapper order until plan canonicalization, then neutralize one, combine adjacent finite opacities, and reduce zero only under the exact W5a `SRC_OVER`/no-effect condition.
- [x] Seal `NumericOperationGraphV1` in every W5a program and use that same graph to generate the backend expression and to drive the independent arbitrary-precision/outward-rounded test oracle. Model F32 rounding, FTZ, allowed reassociation/fusion, clamp and UNORM8 quantization.
- [x] Implement immutable program/binding entries and a sealed table. Deduplicate structure independently from values; do not deduplicate distinct binding payloads by structure alone.
- [x] Replace the first promoted Rect draw's color authority by `MaterialPlanRef`; retain the historical W3/W4 path only through `LegacyColorV1`.
- [x] Add the renderer adapter which consumes the sealed DAG and raw bindings in the color-writing fragment source stage, retaining the proven geometry/coverage/fixed-function stages without a reconstructed material descriptor.
- [x] Run the new public test, then relevant existing W3/W4 Rect public pixel tests.
- [x] Refactor only after GREEN; run `rtk git diff --check` and commit `feat(gpu-plan): establish W5a solid material authority`.

### Task 2: Rect fractionnaire et RRect sur la même autorité

**Files:**
- Modify: `gpu-plan/.../W4aAnalyticRectPlanCompiler.kt`
- Modify: `gpu-plan/.../W4bAnalyticRRectPlanCompiler.kt`
- Modify: `gpu-renderer/.../planning/W4aAnalyticRectGraphLowerer.kt`
- Modify: `gpu-renderer/.../planning/W4bAnalyticRRectGraphLowerer.kt`
- Modify: `kanvas/.../surface/W5aMaterialSurfacePixelTest.kt`

- [x] Add RED public pixel fixtures for a fractional Rect and non-trivial RRect, each with shader alpha × paint alpha and partial coverage.
- [x] Make the independent oracle apply coverage after SRC_OVER: `dst + coverage * (blend(src,dst)-dst)`.
- [x] Migrate both draw families to `MaterialPlanRef` while preserving geometry, scissor, sample and coverage facts byte-for-byte.
- [x] Version the promoted witness/capability; never authenticate a W5 draw with a historical W4 witness.
- [x] Verify mutation after Picture/recording cannot change bindings.
- [x] Run new tests plus public W4a/W4b pixel suites; commit `feat(gpu-plan): share W5a material across rect families`.

### Task 3: Path fill, stroke et hairline

**Files:**
- Modify: W4c/W4d/W4e compiler and witness files under `gpu-plan/.../plan/`
- Modify: W4c/W4d/W4e lowerers and authorities under `gpu-renderer/.../planning/` and `.../passes/`
- Modify: `kanvas/.../surface/W5aMaterialSurfacePixelTest.kt`

- [x] Add RED public fixtures for direct-triangle fill, stencil-cover fill, stroke and hairline using Solid shader + paint opacity.
- [x] Cover hard-edge cells and retain the explicit public capability skips for unavailable AA4 cells; no AA4 capability is injected and skipped cells are not claimed as executed.
- [x] Preserve the `MaterialPlanRef` on the logical draw identity through path wrappers without copying `ColorF32` back into the draw.
- [x] Bind and evaluate the material only in color-writing phases (direct color, color cover and binary color cover). Stencil and mask producers consume geometry/coverage only and must neither bind nor evaluate material resources.
- [x] Run new tests plus public W4c/W4d/W4e path suites; commit `feat(gpu-plan): apply W5a material to prepared paths`.

### Task 4: Point et Points préparés

**Files:**
- Modify: point capture/admission/planning files selected by repository search
- Modify: `gpu-renderer/.../recording/GPUCorePrimitivePreparedFrameTaskListBuilder.kt`
- Modify: point materialization/payload files selected by the prepared route
- Modify: `kanvas/.../surface/W5aMaterialSurfacePixelTest.kt`

- [x] Add RED public `drawPoint` and multi-`drawPoints` fixtures with non-trivial alpha, including three successive commands so ordering cannot be hidden.
- [x] Feed the common `EffectiveMaterialPlanner` from the immutable draw snapshot; remove any point-local solid descriptor creation for the promoted cases.
- [x] Keep point topology and coverage unchanged. Any unsupported blend still belongs to W5b and must refuse/fall through before W5 ownership according to the existing route contract.
- [x] Compare public pixels only; do not assert prepared-route counters or scopes.
- [x] Run the bounded SRC_OVER point fixtures in `W5aMaterialSurfacePixelTest`; commit the prepared-point migration. No broader blend suite is claimed as verification.

### Task 5: Text pré-résolu, sans travail font

**Files:**
- Modify: `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUPreparedTextLowerer.kt`
- Modify: `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUPreparedTextContracts.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/wgsl/GPUPreparedTextShaderComposer.kt`
- Modify: text payload/material files reached by compiler errors
- Modify: `kanvas/.../surface/W5aMaterialSurfacePixelTest.kt`

- [x] Add a RED public fixture using the existing already-resolved A8 glyph/run fixture; no font implementation, new font generation or font test suite is introduced.
- [x] Require shader alpha × paint alpha exactly once and mutation stability after recording.
- [x] Change the text lowerer to receive the common plan pair/ref and make the renderer compose the existing A8 coverage with the planned Solid/Opacity result.
- [x] Remove the promoted Solid path's direct call that lets text reconstruct its own `GPUMaterialDescriptor`.
- [x] Run the bounded prepared-text public pixel subset; commit `feat(gpu-renderer): consume W5a material in prepared text`.

### Task 6: Vertices/Mesh préparés

**Files:**
- Modify: `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUPreparedVerticesLowerer.kt`
- Modify: `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/PreparedVerticesFrameInventory.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/payloads/GPUPreparedVerticesPayload.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/wgsl/PreparedVerticesShader.kt`
- Modify: `kanvas/.../surface/W5aMaterialSurfacePixelTest.kt`

- [x] Add RED public fixtures for a triangle without vertex colors and a triangle with vertex colors; shader/paint alpha must be applied at the source stage exactly once.
- [x] Compile the draw material through the common planner and pass only the scellé plan to vertices payload/composition.
- [x] Preserve the operation/mesh color-composition order already specified by the IR; do not treat vertex color as a replacement material authority.
- [x] Remove the promoted Solid path's route-local semantic reconstruction and preserve typed refusals for W5b+ material kinds.
- [x] Run bounded public vertices pixel tests; commit `feat(gpu-renderer): consume W5a material in prepared vertices`.

### Task 7: Frame mixte, refus/récupération et suppression des fallbacks concernés

**Files:**
- Modify: `kanvas/.../surface/W5aMaterialSurfacePixelTest.kt`
- Modify: `gpu-renderer` material mapper/compiler files located by `rtk rg 'fallback|child|transparent|Unsupported'`
- Create: `refactor/waves/W05-material-graph/status.md`
- Modify: `refactor/README.md`

- [x] Add a RED public mixed-frame test combining Rect, RRect and Path with distinct planned bindings and draw order.
- [x] Add a public refusal reachable without test injection (for example a W5b-only material on an otherwise W5a-promoted frame), then render a valid W5a frame on the same runtime/backend and require exact recovery; the retained test uses distinct Surface instances, not one reused Surface.
- [x] Remove only fallbacks now owned by W5a: Solid/Opacity must never become a child, arbitrary transparent, or legacy route after W5 selection. Leave later W5 kinds as explicit typed gaps, not semantic substitutions.
- [x] Search all production call sites for alternate Solid/Opacity compilation. Migrate or document every remaining call site; review is the architectural proof, not a source-shape test.
- [x] Update W05 status with gate evidence, exact deferred gaps and no intermediate status files elsewhere.
- [x] Update `refactor/README.md` with the verified W5a implementation gates, explicit AA4 skips and W5b next; reserve global closure for the two Task 8 READY re-reviews.
- [x] Run bounded public regression suites and commit `refactor(material): remove W5a silent fallbacks`.

Correction native Task 7 : `feat(gpu-renderer): compose native W5a material lanes` remplace la conversion Rect/RRect en Path par une capability composite distincte. Les gates publiques sont vertes sur les fixtures dont l'enveloppe numérique indépendante reste bornée; la clôture après reviews reste à Task 8.

Correction globale Task 8 : le DAG scellé génère réellement les opérations material du fragment, avec raw bindings et partition ABI V2 (group 1 non dynamique), sans évaluateur CPU ni compute prépass. Les producteurs stencil/mask n'émettent aucune source material. `W5aPreparedFrameMaterialRegistry` n'acquiert les sources Core/A8/Vertices qu'après admission authentique par les mapper/lowerers, recorder, collecte sémantique et preflight, puis rebase uniquement les émissions/payloads material sans refaire géométrie, atlas ni artifacts. Les candidats paint Core locaux n'épuisent pas la table commune; les refus géométriques précèdent le recorder et conservent leur diagnostic exact. Les nouvelles preuves Surface observent 683 Rect hors cible + Point valide et Rect non finie + Point valide. L'audit de toutes les nouvelles déclarations publiques corrige notamment `premultipliedRgbaF32`, `refsByCommandIdI32` et `sourcePlansByCommandIdI32`.

Le composite ajoute les lanes stroke/hairline W4d authentiques et refuse plus de 512 runs avant toute copie de scène par lane. La preuve 512/513 utilise le même runtime/backend avec une autre Surface pour le refus; la preuve gradient valide→refus→valide utilise trois instances Surface sur le même runtime/backend. Aucune des deux n'est une preuve de récupération d'une unique Surface ayant refusé. Le public `DrawMesh` sans programme normalise vers les prepared vertices; `MeshProgram` conserve son contrat distinct hors promotion W5a. Les deux reviews globales ont demandé cette correction et restent à reprendre, non validées implicitement par les tests.

Continuation numérique autorisée dans cette même vague : le RED public bleu 18 hors singleton 17 a révélé que l'attachement fixed-function devait être borné séparément du `pow` WGSL. La couverture multiplie la source dans le fragment avant les facteurs `One`/`InvSrcAlpha`; D3D11.3 §17.5 ne fixant ni lattice ni schedule, l'oracle ferme le blend à la précision RGBA8 minimale autorisée et F32, avec destination corrélée, sans seuil empirique. Le cas 17/18 retiré de la suite reste documenté comme `Unbounded`/non-gate. Les 13 anciennes fixtures multi-draw devenues `Unbounded` sont remplacées seulement par des témoins endpoint/grille non triviaux qui préservent ordre/mutation et satisfont de nouveau le singleton ou deux codes adjacents; un ordre inversé rendu publiquement est rejeté. Les scènes arbitraires, notamment 9/16 avec deux opacités et alpha Paint 253/255, peuvent rester `Unbounded` : aucune assertion n'est élargie et aucun pipeline destination-read n'est ajouté. Dérivation, sources et limites sont consignées dans [le status W05](../waves/W05-material-graph/status.md).

Vérification de la continuation `7dbaf8cdf672e836f6ec6d77b1734cb68b6669db` et de ses recoveries jusqu'à `9891e117319f6e698924c4dcc471a24e3e6b9169` : compile JVM GREEN, W5a 48 tests (47 passés, 1 skip) et GPUPlan 71 tests (69 passés, 2 skips), soit 119 tests publics, 116 passés, 0 failure/error, 3 skips AA4 authentiques. Les deux re-reviews Task 8 restent pending.

Limite de preuve architecturale : si une évaluation CPU et le fragment GPU appartiennent tous deux à l'enveloppe numérique publique, aucun RED black-box discriminant autorisé n'existe. La production review prouve alors la provenance DAG→fragment; les pixels prouvent l'enveloppe, l'ordre, l'alpha et la capture. Aucun test WGSL/IR/structure n'est ajouté pour fabriquer un RED.

### Task 8: Vérification finale, review indépendante et préparation de la stack

**Files:**
- Modify only files required by verified review findings

- [x] Run `rtk git diff --check`.
- [x] Run compile gates for touched modules on JVM and JS where configured.
- [x] Run the complete `W5aMaterialSurfacePixelTest` and bounded public W3/W4 regression suites covering touched families.
- [x] Do not run font, codec, GM, dashboard, baseline, Skia integration or `jpg-color-cube` suites.
- [ ] Ask a fresh Sol agent for a spec review against this plan and the W5 design; fix every Critical/Important finding through the responsible implementation agent.
- [ ] Ask a different fresh Sol agent for the final quality review; repeat until `READY`.
- [x] Confirm `rtk git status --short`, the commit range from `codex/w5-material-graph`, and that no temporary agent report is tracked.
- [ ] Keep push/PR publication separate from implementation verification; when publication is authorized, create the W5a PR stacked on the W5 design branch and include exact test evidence plus deferred W5b–W5h scope.

## Verification Commands

Commands are refined from the actual test class names created by each task; keep them bounded:

```bash
rtk proxy ./gradlew :gpu-plan:compileKotlin :gpu-renderer:compileKotlin :kanvas:compileKotlin --no-parallel --max-workers=1
rtk proxy ./gradlew :kanvas:test --tests '*W5aMaterialSurfacePixelTest' --tests '*GPUPlanSurfacePixelTest' --no-parallel --max-workers=1
rtk git diff --check
```

`:kanvas` is JVM-only (`buildsrc.convention.kotlin-jvm`); there is no `:kanvas:jsNodeTest` and no JS/infrastructure substitute is run. Historical infrastructure tests receive only mechanical compatibility adaptations, never new W5a evidence.
