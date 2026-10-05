# W3 GPU Plan First Slice — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:subagent-driven-development` to implement this plan task-by-task with review checkpoints. Use Terra for implementation and Sol only for reviews.

**Goal:** Faire rendre par le produit une première frame compositionnelle `SceneSnapshot -> RenderGraph -> GPU` pour les rectangles solides pixel-aligned, clips simples et `SrcOver`, tout en conservant un fallback whole-frame strict vers le renderer legacy pour les scènes non migrées.

**Architecture:** Ajouter un module JVM pur `:gpu-plan` entre `:render-ir` et `:gpu-renderer`. Le planner y ferme les décisions sémantiques, géométriques, colorimétriques, de ressources, passes, lifetimes et budget. `:gpu-renderer` vérifie et abaisse ce plan sans replanifier, puis l’exécute dans un `RenderContext` process-scoped. `:kanvas` applique une shallow gate avant capture et choisit atomiquement le nouveau chemin ou le legacy.

**Tech Stack:** Kotlin 2.x/JVM, Gradle Kotlin DSL, JUnit 5, `kotlin.test`, coroutines Kotlin, contrats WebGPU existants confinés à `:gpu-renderer`, géométrie `SizeI32`/`RectI32` fournie par `:math`.

**Spec:** [`refactor/specs/2026-09-02-w3-gpu-plan-first-slice-design.md`](../specs/2026-09-02-w3-gpu-plan-first-slice-design.md)

**Global Constraints:** `font` et `codec` sont hors périmètre. Ne jamais exécuter `jpg-color-cube`. Aucun fallback CPU silencieux et aucun mélange legacy/nouveau chemin dans une frame. Les objets géométriques restent dans `:math` et suivent la nomenclature `I32`/`I64`/`F32`/`F64`; `:gpu-plan` ne crée aucune algèbre géométrique parallèle. Aucun test d’infrastructure, de réflexion, de parsing du source, d’appel de méthode privée ou de snapshot de la forme interne exacte du graphe/WGSL. Tous les changements de comportement suivent RED–GREEN–REFACTOR. Toutes les commandes shell sont préfixées par `rtk`; toutes les éditions manuelles passent par `apply_patch`.

## Progression

- [ ] Task 1 — Rendre le port backend générique sur son output
- [ ] Task 2 — Créer les contrats handle-free de `:gpu-plan`
- [ ] Task 3 — Compiler la capability W3 en `RenderGraph`
- [ ] Task 4 — Abaisser le plan sans replanification dans `:gpu-renderer`
- [ ] Task 5 — Exécuter le plan avec un `RenderContext` propriétaire
- [ ] Task 6 — Router atomiquement `Surface.render()`
- [ ] Task 7 — Prouver les pixels et publier le statut W3

## Carte des dépendances entre tâches

```text
Task 1 ──┐
         ├── Task 5 ── Task 6 ── Task 7
Task 2 ──┴── Task 3 ── Task 4 ──┘
```

Les tâches sont exécutées séquentiellement. Chaque commit doit compiler avant la tâche suivante afin que les reviews puissent attribuer sans ambiguïté un défaut à la tranche qui l’a introduit.

## Task 1 — Rendre le port backend générique sur son output

**Fichiers :**

- Modifier `render-ir/src/main/kotlin/org/graphiks/kanvas/render/ir/RenderBackend.kt`.
- Modifier `render-ir/src/test/kotlin/org/graphiks/kanvas/render/ir/RenderBackendResultTest.kt`.

**Étape 1 — Écrire le test rouge du résultat typé**

Ajouter une implémentation de test handle-free et vérifier que `Completed` restitue exactement cet output, tandis que les issues d’échec restent assignables via `Nothing` :

```kotlin
private data class TestOutput(val bytes: List<Int>) : RenderOutput

@Test
fun `completed execution carries its immutable typed output`() {
    val output = TestOutput(listOf(1, 2, 3, 4))
    val result: RenderExecutionResult<TestOutput> =
        RenderExecutionResult.Completed(output)

    assertSame(output, assertIs<RenderExecutionResult.Completed<*>>(result).output)
}

@Test
fun `failure outcomes remain covariant for every output`() {
    val failure: RenderExecutionResult<TestOutput> =
        RenderExecutionResult.InvalidPlan(listOf(testDiagnostic("invalid.plan")))

    assertIs<RenderExecutionResult.InvalidPlan>(failure)
}
```

Exécuter :

```bash
rtk ./gradlew :render-ir:test --tests '*RenderBackendResultTest*'
```

Résultat attendu : compilation rouge, car `RenderOutput` et les paramètres de type n’existent pas.

**Étape 2 — Généraliser le port**

Remplacer les contrats par :

```kotlin
public interface RenderOutput

public interface RenderBackend<P : Any, O : RenderOutput> {
    public fun plan(scene: SceneSnapshot, target: RenderTargetDescriptor): RenderPlanResult<P>
    public fun submit(plan: P): RenderSubmission<O>
}

public interface RenderSubmission<out O : RenderOutput> {
    public val id: SubmissionId
    public suspend fun await(): RenderExecutionResult<O>
}

public sealed interface RenderExecutionResult<out O : RenderOutput> {
    public data class Completed<O : RenderOutput>(public val output: O) : RenderExecutionResult<O>
    public class UnsupportedCapability(diagnostics: List<RenderDiagnostic>) : RenderExecutionResult<Nothing>
    public class InvalidPlan(diagnostics: List<RenderDiagnostic>) : RenderExecutionResult<Nothing>
    public class ResourceLimitExceeded(diagnostics: List<RenderDiagnostic>) : RenderExecutionResult<Nothing>
    public class DeviceFailure(diagnostics: List<RenderDiagnostic>) : RenderExecutionResult<Nothing>
}
```

Conserver la copie immutable des diagnostics et toutes leurs validations actuelles. Ne placer aucun type GPU dans `:render-ir`.

**Étape 3 — Vérifier et committer**

```bash
rtk ./gradlew :render-ir:test
rtk git add render-ir/src/main/kotlin/org/graphiks/kanvas/render/ir/RenderBackend.kt render-ir/src/test/kotlin/org/graphiks/kanvas/render/ir/RenderBackendResultTest.kt
rtk git commit -m "refactor: type render backend outputs"
```

## Task 2 — Créer les contrats handle-free de `:gpu-plan`

**Fichiers :**

- Modifier `settings.gradle.kts`.
- Créer `gpu-plan/build.gradle.kts`.
- Créer `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/PlanIdentity.kt`.
- Créer `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/PlanCapabilities.kt`.
- Créer `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/PlanResources.kt`.
- Créer `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/PlanPasses.kt`.
- Créer `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/PlanBudget.kt`.
- Créer `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/RenderGraph.kt`.
- Créer `gpu-plan/src/test/kotlin/org/graphiks/kanvas/gpu/plan/RenderGraphContractTest.kt`.
- Créer `gpu-plan/src/test/kotlin/org/graphiks/kanvas/gpu/plan/PlanMemoryBudgetTest.kt`.

**Étape 1 — Brancher le module et écrire les tests rouges**

Ajouter `include(":gpu-plan")`. Le module utilise `api(project(":render-ir"))`, `implementation(kotlin("stdlib"))`, `testImplementation(kotlin("test"))` et JUnit 5. Il ne dépend ni de `:gpu-renderer`, ni de `:kanvas`, ni de WebGPU, font ou codec.

Tester par les APIs publiques :

```kotlin
@Test
fun `rectangles and resource collections are defensive snapshots`() {
    val source = RectI32(1, 2, 5, 7)
    val draw = SolidRectDraw.of(
        commandIndex = 0,
        color = ColorF32.of(0.25f, 0.0f, 0.0f, 0.5f),
        visibleBounds = source,
        scissor = source,
    )
    source.left = 99

    assertEquals(RectI32(1, 2, 5, 7), draw.copyVisibleBounds())
    val leaked = draw.copyVisibleBounds()
    leaked.left = 88
    assertEquals(RectI32(1, 2, 5, 7), draw.copyVisibleBounds())
}

@Test
fun `graph rejects duplicate identities and dangling pass resources`() {
    assertFailsWith<IllegalArgumentException> { graphWithDuplicateResourceIds() }
    assertFailsWith<IllegalArgumentException> { graphWithDanglingReadbackSource() }
}

@Test
fun `capability formats and graph planning inputs are immutable snapshots`() {
    val formats = mutableSetOf(PlanLogicalColorFormat.RGBA8_UNORM_SRGB_LINEAR_PREMUL)
    val capabilities = supportedCapabilities(formats)
    val graph = validGraph(capabilities = capabilities, budget = PlanBudget(4096))
    formats.clear()

    assertEquals(setOf(PlanLogicalColorFormat.RGBA8_UNORM_SRGB_LINEAR_PREMUL), graph.capabilities.supportedFormats())
    assertFailsWith<UnsupportedOperationException> {
        (graph.capabilities.supportedFormats() as MutableSet<PlanLogicalColorFormat>).clear()
    }
    assertEquals(4096, graph.budget.maxFrameLocalBytes)
}

@Test
fun `blank plan resource and pass identities are rejected`() {
    assertFailsWith<IllegalArgumentException> { PlanId("") }
    assertFailsWith<IllegalArgumentException> { PlanResourceId(" ") }
    assertFailsWith<IllegalArgumentException> { PlanPassId("\t") }
}
```

Tester le budget avec un target 17×3, quatre bytes par pixel et un alignement de ligne 256 : `bytesPerRow == 256`, staging `== 768`, target `== 204`, pic `== 972`. Tester aussi overflow arithmétique et limite strictement inférieure au pic.

```bash
rtk ./gradlew :gpu-plan:test
```

Résultat attendu : rouge, le module et les contrats n’existent pas.

**Étape 2 — Implémenter l’algèbre publique fermée**

Exposer les types suivants :

```kotlin
@JvmInline public value class PlanId(public val value: String)
@JvmInline public value class PlanResourceId(public val value: String)
@JvmInline public value class PlanPassId(public val value: String)

public enum class PlanLogicalColorFormat { RGBA8_UNORM_SRGB_LINEAR_PREMUL }
public enum class PlanResourceKind { Texture2D, Buffer }
public enum class PlanResourceRole { LogicalTarget, ReadbackStaging }
public enum class PlanResourceUsage { RenderAttachment, CopySource, CopyDestination, MapRead }
public enum class PlanResourceLifetime { FrameLocal }

public class PlanCapabilitySnapshot private constructor(
    public val deviceGeneration: Long,
    public val maxTextureDimension2D: Int,
    public val maxBufferSizeBytes: Long,
    public val copyBytesPerRowAlignment: Int,
    supportedFormats: Set<PlanLogicalColorFormat>,
) {
    public fun supportedFormats(): Set<PlanLogicalColorFormat>

    public companion object {
        public fun of(
            deviceGeneration: Long,
            maxTextureDimension2D: Int,
            maxBufferSizeBytes: Long,
            copyBytesPerRowAlignment: Int,
            supportedFormats: Set<PlanLogicalColorFormat>,
        ): PlanCapabilitySnapshot
    }
}

public data class PlanBudget(val maxFrameLocalBytes: Long)
```

Valider les valeurs positives, copier le `Set`, et utiliser `SizeI32` ou un `Long` byte-size selon le kind. Le contrat ressource est :

```kotlin
public class PlanResource private constructor(
    public val id: PlanResourceId,
    public val role: PlanResourceRole,
    public val ordinal: Int,
    public val kind: PlanResourceKind,
    public val format: PlanLogicalColorFormat?,
    extent: SizeI32?,
    public val byteSize: Long,
    usages: Set<PlanResourceUsage>,
    public val lifetime: PlanResourceLifetime,
    public val firstPassIndex: Int,
    public val lastPassIndexExclusive: Int,
) {
    public fun copyExtent(): SizeI32?
    public fun usages(): Set<PlanResourceUsage>

    public companion object {
        public fun of(
            role: PlanResourceRole,
            ordinal: Int,
            kind: PlanResourceKind,
            format: PlanLogicalColorFormat?,
            extent: SizeI32?,
            byteSize: Long,
            usages: Set<PlanResourceUsage>,
            lifetime: PlanResourceLifetime,
            firstPassIndex: Int,
            lastPassIndexExclusive: Int,
        ): PlanResource
    }
}
```

`copyExtent()` et `usages` retournent des snapshots. La factory `PlanResource.of` impose format+extent à `Texture2D`, absence de format+extent à `Buffer`, ordinal non négatif, byte-size positif et intervalle non vide; elle dérive l’ID de `role + ordinal` au lieu de le recevoir librement. La génération device est non négative; dimensions, taille buffer et alignement sont strictement positifs.

Fermer les décisions W3 :

```kotlin
public enum class CoveragePlan { FullOrScissor }
public enum class SamplePlan { SingleSample }
public enum class BlendPlan { SrcOver }
public enum class AttachmentLoadPlan { ClearTransparent }
public enum class AttachmentStorePlan { Store }
public enum class PlanPassRole { MainRender, TextureCopy, Filter, Resolve, Readback }

public sealed interface PlanPass {
    public val id: PlanPassId
    public val role: PlanPassRole
    public val ordinal: Int
    public class RenderPass(
        override val ordinal: Int,
        val target: PlanResourceId,
        draws: List<SolidRectDraw>,
        val load: AttachmentLoadPlan,
        val store: AttachmentStorePlan,
    ) : PlanPass {
        override val role: PlanPassRole = PlanPassRole.MainRender
        override val id: PlanPassId = planPassId(role, ordinal)
        public fun draws(): List<SolidRectDraw>
    }
    public data class TextureCopy(
        override val ordinal: Int,
        val source: PlanResourceId,
        val destination: PlanResourceId,
    ) : PlanPass {
        override val role: PlanPassRole = PlanPassRole.TextureCopy
        override val id: PlanPassId = planPassId(role, ordinal)
    }
    public class FilterPass(
        override val ordinal: Int,
        inputs: List<PlanResourceId>,
        val output: PlanResourceId,
    ) : PlanPass {
        override val role: PlanPassRole = PlanPassRole.Filter
        override val id: PlanPassId = planPassId(role, ordinal)
        public fun inputs(): List<PlanResourceId>
    }
    public data class ResolvePass(
        override val ordinal: Int,
        val source: PlanResourceId,
        val destination: PlanResourceId,
    ) : PlanPass {
        override val role: PlanPassRole = PlanPassRole.Resolve
        override val id: PlanPassId = planPassId(role, ordinal)
    }
    public data class ReadbackPass(
        override val ordinal: Int,
        val source: PlanResourceId,
        val staging: PlanResourceId,
        val bytesPerRow: Long,
    ) : PlanPass {
        override val role: PlanPassRole = PlanPassRole.Readback
        override val id: PlanPassId = planPassId(role, ordinal)
    }
}

public data class PlanPassDependency(
    val before: PlanPassId,
    val after: PlanPassId,
)
```

`SolidRectDraw` porte `commandIndex`, `ColorF32`, `visibleBounds`, `scissor`, `CoveragePlan`, `SamplePlan` et `BlendPlan`; il doit copier `RectI32` à l’entrée et à chaque getter. `PlanPass.RenderPass` et `FilterPass` copient leurs listes. Les constructors de passes reçoivent uniquement l’ordinal et dérivent eux-mêmes `id` de `role + ordinal`; ils ne permettent donc pas de fournir une identité divergente.

La factory du graphe est fermée par :

```kotlin
public class RenderGraph private constructor(
    public val id: PlanId,
    public val capabilityId: String,
    public val targetExtent: SizeI32,
    public val colorFormat: PlanLogicalColorFormat,
    public val capabilities: PlanCapabilitySnapshot,
    public val budget: PlanBudget,
    public val visualCommandCount: Int,
    resources: List<PlanResource>,
    passes: List<PlanPass>,
    dependencies: List<PlanPassDependency>,
    public val peakFrameLocalBytes: Long,
) {
    public fun resources(): List<PlanResource>
    public fun passes(): List<PlanPass>
    public fun dependencies(): List<PlanPassDependency>

    public companion object {
        public fun of(
            id: PlanId,
            capabilityId: String,
            targetExtent: SizeI32,
            colorFormat: PlanLogicalColorFormat,
            capabilities: PlanCapabilitySnapshot,
            budget: PlanBudget,
            visualCommandCount: Int,
            resources: List<PlanResource>,
            passes: List<PlanPass>,
            dependencies: List<PlanPassDependency>,
            peakFrameLocalBytes: Long,
        ): RenderGraph
    }
}
```

Les trois value classes d’identité refusent toute valeur blank à la construction. `PlanCapabilitySnapshot.of` conserve une copie non modifiable du set et implémente equality/hashCode sur ses valeurs. `RenderGraph.of` conserve les snapshots de capabilities et budget qui ont produit le plan, copie ses listes puis valide : IDs de ressources et passes uniques, références existantes, ordinals uniques par rôle, indices de lifetime dans les bornes, dépendances non réflexives allant d’une passe antérieure vers une passe postérieure, coûts non négatifs, et pic mémoire égal au calcul vérifié. Le test d’identité crée deux ressources et deux passes de même rôle mais d’ordinals différents et vérifie leurs IDs distincts/stables; les draws ne possèdent pas d’identité propre.

Le calcul de budget utilise `Math.multiplyExact`/`Math.addExact` et un alignement vérifié :

```kotlin
public sealed interface PlanMemoryBudgetResult {
    public data class WithinBudget(val peakBytes: Long, val readbackBytesPerRow: Long) : PlanMemoryBudgetResult
    public data class Exceeded(val requiredBytes: Long, val limitBytes: Long) : PlanMemoryBudgetResult
    public data class Invalid(val code: String) : PlanMemoryBudgetResult
}
```

**Étape 3 — Vérifier les frontières et committer**

```bash
rtk ./gradlew :gpu-plan:test :render-ir:test
rtk git add settings.gradle.kts gpu-plan
rtk git commit -m "feat: add handle-free gpu plan contracts"
```

## Task 3 — Compiler la capability W3 en `RenderGraph`

**Fichiers :**

- Créer `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/GpuPlanCompiler.kt`.
- Créer `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/W3SolidRectPlanCompiler.kt`.
- Créer `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/W3PlanDiagnostics.kt`.
- Créer `gpu-plan/src/test/kotlin/org/graphiks/kanvas/gpu/plan/W3SolidRectPlanCompilerTest.kt`.

**Étape 1 — Écrire les tests rouges de classification publique**

Construire les scènes par `SceneSnapshot.of` et vérifier les issues, jamais une méthode privée :

```kotlin
@Test
fun `two overlapping translucent solid rects produce a ready graph`() {
    val result = compiler.plan(
        scene = sceneOf(solidRect(0f, 0f, 8f, 8f, 0x80FF0000u), solidRect(4f, 0f, 12f, 8f, 0x800000FFu)),
        target = target(16, 8),
        capabilities = supportedCapabilities(),
        budget = PlanBudget(1L shl 30),
    )

    val graph = assertIs<RenderPlanResult.Ready<RenderGraph>>(result).plan
    assertEquals(2, graph.visualCommandCount)
    assertEquals(W3SolidRectPlanCompiler.CAPABILITY_ID, graph.capabilityId)
}
```

Couvrir dans le même fichier :

- `BlendNode.SrcOver`, `BlendNode.Mode(SRC_OVER)` et `BlendNode.Paint(SRC_OVER, null)` ;
- transform identity et scale/translate donnant des limites entières ;
- clip vide et `DeviceRect` pixel-aligned ;
- `SetTransform`, `SetClip` et `Annotation` finis conservés comme provenance sans draw additionnel ;
- `DrawColor(SRC_OVER)` couvrant la cible ;
- conversion straight-alpha sRGB vers linear-premultiplied `ColorF32` ;
- coverage hard-edge et antialiased admises uniquement lorsque les bounds résolues sont pixel-aligned ;
- table de refus couvrant chaque champ non W3 de `PaintNode`, `resource`, `operationBlendMode` et `EffectStack` ;
- table de refus `DrawColor` pour mode non `SRC_OVER`, transform non identity et clip non simple ;
- scène/target non-sRGB en `GapNotMigrated` ;
- stroke, gradient, path, `Clear`, clip complexe, frame vide et frame entièrement clipped-out en `GapNotMigrated` ;
- incohérence scene/target en `InvalidScene` ;
- capability device ou format absente après reconnaissance sémantique en `GapOnPromotedScope` ;
- dépassement mémoire en `ResourceLimitExceeded` ;
- label target différent donnant le même `PlanId` ;
- changement séparé de scene, extent/color space, capability ou budget modifiant le `PlanId` ;
- ressources et passes sémantiquement identiques conservant des IDs distincts par rôle+ordinal ;
- mutation d’un `RectI32` retourné n’altérant pas le plan.

```bash
rtk ./gradlew :gpu-plan:test --tests '*W3SolidRectPlanCompilerTest*'
```

Résultat attendu : rouge, le compiler n’existe pas.

**Étape 2 — Exposer le compiler et ses codes stables**

```kotlin
public interface GpuPlanCompiler {
    public fun plan(
        scene: SceneSnapshot,
        target: RenderTargetDescriptor,
        capabilities: PlanCapabilitySnapshot,
        budget: PlanBudget,
    ): RenderPlanResult<RenderGraph>
}

public class W3SolidRectPlanCompiler : GpuPlanCompiler {
    public companion object {
        public const val CAPABILITY_ID: String =
            "solid-rect-pixel-aligned-simple-clip-src-over-srgb-v1"
    }
}
```

Définir dans `W3PlanDiagnostics.kt` des `RenderDiagnosticCode` stables pour : `w3.command.not_migrated`, `w3.geometry.not_pixel_aligned`, `w3.clip.not_pixel_aligned`, `w3.scene.invalid`, `w3.size.overflow`, `w3.capability.texture_dimension`, `w3.capability.buffer_size`, `w3.capability.format`, `w3.budget.frame_local_exceeded` et `w3.plan.identity_invalid`.

**Étape 3 — Séparer classification et construction**

Faire une première passe qui reconnaît uniquement les axes sémantiques de la section 9 de la spec. Tant qu’un axe n’est pas promu, retourner `GapNotMigrated`. Une fois toute la frame reconnue, les contradictions target/device/format/identity deviennent `GapOnPromotedScope`; budget et scène invalide gardent leurs catégories dédiées.

La construction doit :

1. vérifier equality extent/color space ;
2. admettre `matrix.isIdentity || matrix.isScaleTranslate()`, transformer explicitement les quatre coins, prendre min/max par axe pour supporter les scales négatifs, puis refuser les résultats dégénérés, non finis ou non entiers ;
3. intersecter target, géométrie et clip dans cet ordre ;
4. convertir `ColorARGB` en linear-premultiplied avec `ColorTransferFunction.sRgb.toLinear` ;
5. créer une texture target et un staging aligné ;
6. calculer le pic frame-local avant de construire le graphe ;
7. émettre un `RenderPass` clear-transparent/store puis un `ReadbackPass` ;
8. dériver `PlanId` de scene, extent/color space, capabilities et budget, en excluant `target.label`.

**Étape 4 — Vérifier et committer**

```bash
rtk ./gradlew :gpu-plan:test :render-ir:test
rtk git add gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/GpuPlanCompiler.kt gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/W3SolidRectPlanCompiler.kt gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/W3PlanDiagnostics.kt gpu-plan/src/test/kotlin/org/graphiks/kanvas/gpu/plan/W3SolidRectPlanCompilerTest.kt
rtk git commit -m "feat: compile W3 solid rect render graphs"
```

## Task 4 — Abaisser le plan sans replanification dans `:gpu-renderer`

**Fichiers :**

- Modifier `gpu-renderer/build.gradle.kts`.
- Modifier `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/recording/GPUCorePrimitivePreparedFrameTaskListBuilder.kt`.
- Créer `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/planning/GpuPlanCapabilityAdapter.kt`.
- Créer `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/planning/GpuPlanTaskListLowerer.kt`.
- Créer `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/planning/GpuPlanLoweringResult.kt`.
- Créer `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/planning/GpuPlanCapabilityAdapterTest.kt`.
- Créer `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/planning/GpuPlanTaskListLowererTest.kt`.

**Étape 1 — Écrire les tests rouges du boundary renderer**

Ajouter `implementation(project(":gpu-plan"))`. Tester que l’adapter transforme une capability native supportée en snapshot neutre, et refuse proprement les limites absentes/incohérentes.

Tester ensuite le comportement public du lowerer :

```kotlin
@Test
fun `ready W3 graph lowers to a frame accepted by GPUFramePlanner`() {
    val lowered = lowerer.lower(validRequest(readyGraph()))
    val taskList = assertIs<GpuPlanLoweringResult.Lowered>(lowered).taskList

    val framePlan = GPUFramePlanner.plan(taskList)
    assertFalse(framePlan.atomicallyRefused)
    assertTrue(framePlan.steps.isNotEmpty())
}

@Test
fun `unsupported planned decisions are rejected instead of replanned`() {
    val result = lowerer.lower(requestWithUnsupportedPassFamily())

    assertEquals(
        "w3.lowering.incompatible_plan",
        assertIs<GpuPlanLoweringResult.InvalidPlan>(result).diagnostic.code.value,
    )
}
```

Le second fixture est construit par le contract-test helper du package test; il n’altère pas un objet privé et ne vérifie ni appels internes, ni nombre exact de tâches.

Ajouter deux cas RED : génération du `GpuPlanLoweringRequest` différente de `graph.capabilities.deviceGeneration` → `UnsupportedCapability`; budget courant ou `GPUFrameMemoryBudgetPlan` différent de `graph.budget`/`peakFrameLocalBytes` → `InvalidPlan`. Ils prouvent que le lowerer consomme les snapshots du graphe au lieu de décoder ou faire confiance au seul `PlanId`.

```bash
rtk ./gradlew :gpu-renderer:test --tests '*GpuPlanCapabilityAdapterTest*' --tests '*GpuPlanTaskListLowererTest*'
```

Résultat attendu : rouge, adapter et lowerer absents.

**Étape 2 — Adapter les capabilities sans fuite WebGPU**

Exposer :

```kotlin
public sealed interface GpuPlanCapabilityAdapterResult {
    public data class Supported(val snapshot: PlanCapabilitySnapshot) : GpuPlanCapabilityAdapterResult
    public data class Unsupported(val diagnostic: RenderDiagnostic) : GpuPlanCapabilityAdapterResult
}

public fun GPUCapabilities.toPlanCapabilitySnapshot(
    deviceGeneration: GPUDeviceGenerationID,
): GpuPlanCapabilityAdapterResult
```

Ici `GPUCapabilities` désigne explicitement `org.graphiks.kanvas.gpu.renderer.capabilities.GPUCapabilities`. Mapper la génération fournie par `GPUBackendSession.deviceGeneration`, dimension 2D, max buffer, alignement copy-row et présence de `RGBA8UnormSrgb`. Aucun type WebGPU ne sort de ce fichier. Le `GpuPlanLoweringRequest` reçoit aussi cette génération réelle et refuse un graphe planifié pour une autre génération.

**Étape 3 — Ajouter une entrée preplanned à l’assembleur**

Ajouter à l’assembleur core-primitive le request exact suivant :

```kotlin
internal data class GPUCorePrimitivePreplannedFrameRequest(
    val planId: PlanId,
    val baseTaskList: GPUTaskList,
    val target: GPUFrameTargetRef,
    val targetBounds: GPUPixelBounds,
    val targetPreparation: GPUResourcePreparationRequest,
    val staging: GPUFrameBufferRef,
    val stagingPreparation: GPUResourcePreparationRequest,
    val readbackRequest: GPUFrameReadbackRequest,
    val memoryBudget: GPUFrameMemoryBudgetPlan,
    val renderPassId: PlanPassId,
    val readbackPassId: PlanPassId,
)

internal fun GPUCorePrimitivePreparedFrameTaskListAssembler.buildPreplanned(
    request: GPUCorePrimitivePreplannedFrameRequest,
): GPUCorePrimitivePreparedFrameResult
```

`buildPreplanned` exige un `baseTaskList` render-only avec un unique `GPUTask.Render`, conserve ses packets sans les réécrire, puis ajoute exactement un `GPUTask.PrepareResources` contenant target+staging et un `GPUTask.Readback`. Les IDs de tâches sont dérivés du PlanId/rôle de passe, avec deux dépendances : prepare → render pour la disponibilité des ressources, puis render → readback correspondant au `PlanPassDependency`. Le `GPUCorePrimitivePreparedFrameResult` existant est le résultat fermé : `Recorded(taskList)` ou `Refused(diagnostic)`.

Le mapping des ressources est fermé :

- logical target → `GPUFrameTargetRef`, `GPUFrameTextureDescriptor(targetBounds, RGBA8UnormSrgb, 1)`, rôle `SceneTarget`, usages `RenderAttachment + CopySource`, lifetime `FrameLocal`, coût identique au plan ;
- readback staging → `GPUFrameBufferRef`, `GPUFrameBufferDescriptor(byteSize, copyBytesPerRowAlignment)`, rôle `ReadbackStaging`, usages `CopyDestination + MapRead`, lifetime `FrameLocal`, coût identique au plan ;
- allocation target → `GPUFrameMemoryCategory.CanonicalTarget`/`Texture2D` ; allocation staging → `GPUFrameMemoryCategory.ReadbackStaging`/`Buffer` ;
- `targetResidentBytes + peakFrameTransientBytes == RenderGraph.peakFrameLocalBytes`, `configuredAggregateBudgetBytes == RenderGraph.budget.maxFrameLocalBytes`, et absence de diagnostic budget ;
- render pass → clear transparent/store, `SingleSampleFrame`, ordered packets issus des draws ; readback pass → `GPUFrameReadbackRequest` RGBA, tandis que le staging byte-size/alignment matérialise le `bytesPerRow` déjà validé par le graphe ;
- la seule dépendance de passes W3 doit être `MainRender(0) -> Readback(0)` et devient l’edge render → readback, sans en inventer une autre entre passes.

L’entrée `buildPreplanned` valide leurs concordances puis attache prepare/readback/dépendances. Elle ne recalcule ni readback layout, ni memory budget, et ne promeut jamais vers MSAA; elle ne choisit ni clip, blend, coverage ou load/store.

L’entrée legacy existante conserve son comportement. La nouvelle entrée échoue avec un diagnostic `w3.lowering.incompatible_plan` si une décision n’est pas exactement `FullOrScissor`, `SingleSample`, `SrcOver`, clear-transparent/store ou si ressources, lifetimes, coûts, IDs ou dépendances diffèrent du plan.

**Étape 4 — Traduire le `RenderGraph`**

```kotlin
public data class GpuPlanLoweringRequest(
    val graph: RenderGraph,
    val capabilities: GPUCapabilities,
    val deviceGeneration: GPUDeviceGenerationID,
    val currentBudget: PlanBudget,
    val frameId: GPUFrameID,
    val recordingId: GPURecordingID,
)

public sealed interface GpuPlanLoweringResult {
    public data class Lowered(
        val taskList: GPUTaskList,
        val readbackRequestId: String,
    ) : GpuPlanLoweringResult
    public data class InvalidPlan(val diagnostic: RenderDiagnostic) : GpuPlanLoweringResult
    public data class UnsupportedCapability(val diagnostic: RenderDiagnostic) : GpuPlanLoweringResult
}
```

Avant toute traduction, comparer `graph.capabilities` et `graph.budget` aux snapshots courants reçus par le lowerer; une génération ou une capability stale est `UnsupportedCapability`, une divergence interne du budget est `InvalidPlan`. Pour chaque `SolidRectDraw`, construire un `GPUCorePrimitivePayloadInput`, passer par `GPUCorePrimitivePayloadGatherer.gatherSemantic`, fixer le clip à NoClip/Scissor, le blend natif à canonical `SrcOver`, le sample plan à `SingleSampleFrame` et la coverage à `FullOrScissor`. Construire le render-only `baseTaskList`, les deux preparations, les deux allocations et la readback request directement depuis les ressources/passes/lifetimes/dépendance du graphe, puis appeler `buildPreplanned`. Toute valeur inconnue ou divergence est un refus typé; le lowerer ne retombe pas sur un builder général.

**Étape 5 — Vérifier et committer**

```bash
rtk ./gradlew :gpu-plan:test
rtk ./gradlew :gpu-renderer:test --tests '*GpuPlan*'
rtk ./gradlew :gpu-renderer:test --tests '*GPUCorePrimitivePreparedFrameTaskListBuilderTest*'
rtk git add gpu-renderer/build.gradle.kts gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/recording/GPUCorePrimitivePreparedFrameTaskListBuilder.kt gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/planning/GpuPlanCapabilityAdapter.kt gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/planning/GpuPlanTaskListLowerer.kt gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/planning/GpuPlanLoweringResult.kt gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/planning/GpuPlanCapabilityAdapterTest.kt gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/planning/GpuPlanTaskListLowererTest.kt
rtk git commit -m "feat: lower gpu plans into prepared frame tasks"
```

## Task 5 — Exécuter le plan avec un `RenderContext` propriétaire

**Fichiers :**

- Créer `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/planning/GpuFrameOutput.kt`.
- Créer `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/planning/GpuRenderRuntimePorts.kt`.
- Créer `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/planning/GpuRenderContext.kt`.
- Créer `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/planning/GpuRenderBackend.kt`.
- Créer `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/planning/GpuFrameOutputTest.kt`.
- Créer `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/planning/GpuRenderContextTest.kt`.
- Créer `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/planning/GpuRenderBackendTest.kt`.

**Étape 1 — Écrire les tests rouges d’ownership et completion**

Tester par les contrats publics du nouveau composant :

- `GpuFrameOutput` copie bytes, diagnostics, structural steps, counters et scope kinds à l’entrée et à la sortie ;
- deux `await()` sur la même submission retournent la même issue immutable ;
- annuler un coroutine waiter n’annule pas le travail GPU déjà soumis ; un nouvel `await()` observe encore la completion ;
- cleanup s’exécute exactement une fois après succès ou échec de la completion sous-jacente ;
- deux submits de la même clé de session sont sérialisés ; deux clés distinctes progressent indépendamment ;
- `close()` ferme les sessions avant le backend ;
- un changement de génération évince la session stale ;
- une divergence capability entre plan et session donne `InvalidPlan`, sans submit.
- un target préparé est créé une seule fois puis réutilisé pour deux frames séquentielles de même clé ;
- après device loss, le prochain planning utilise un nouveau snapshot de génération et ne réutilise aucun plan/session stale ;
- les échecs device, submit et readback produisent respectivement les codes `w3.execution.device_failure`, `w3.execution.submit_failure` et `w3.execution.readback_failure`.
- une issue `DeviceFailure` appelle le dispose réel de l’owner une fois; les issues submit/readback ordinaires ne le font pas.

Ces tests utilisent des implémentations fake des ports de session/completion injectés dans le constructeur; ils observent les résultats, l’ordre de completion et les ressources libérées, jamais les appels d’une méthode privée.

```bash
rtk ./gradlew :gpu-renderer:test --tests '*GpuFrameOutputTest*' --tests '*GpuRenderContextTest*' --tests '*GpuRenderBackendTest*'
```

Résultat attendu : rouge, les contrats n’existent pas.

**Étape 2 — Créer l’output handle-free**

```kotlin
public enum class GpuFrameChannelOrder { RGBA }

public data class GpuFrameMetrics(
    val opsDispatched: Int,
    val pipelineCount: Int,
    val drawCallCount: Int,
    val coverage: Float,
    val coverageMeasured: Boolean,
)

public class GpuFrameOutput private constructor(
    public val width: Int,
    public val height: Int,
    public val rowStrideBytes: Int,
    public val channelOrder: GpuFrameChannelOrder,
    bytes: ByteArray,
    public val metrics: GpuFrameMetrics,
    diagnostics: List<RenderDiagnostic>,
    structuralSteps: List<String>,
    nativeEvidenceCounters: Map<String, Long>,
    nativeEvidenceScopeKinds: List<String>,
) : RenderOutput {
    public fun copyBytes(): ByteArray
    public fun diagnostics(): List<RenderDiagnostic>
    public fun structuralSteps(): List<String>
    public fun nativeEvidenceCounters(): Map<String, Long>
    public fun nativeEvidenceScopeKinds(): List<String>

    public companion object {
        public fun of(
            width: Int,
            height: Int,
            rowStrideBytes: Int,
            channelOrder: GpuFrameChannelOrder,
            bytes: ByteArray,
            metrics: GpuFrameMetrics,
            diagnostics: List<RenderDiagnostic>,
            structuralSteps: List<String>,
            nativeEvidenceCounters: Map<String, Long>,
            nativeEvidenceScopeKinds: List<String>,
        ): GpuFrameOutput
    }
}
```

La factory valide dimensions positives, `rowStrideBytes == width * 4` par arithmétique checked, `bytes.size == rowStrideBytes * height`, compteurs non négatifs, `coverage in 0f..1f`, puis copie bytes et collections. Le padding WebGPU n’existe que dans le staging planifié; la completion remet toujours au produit des lignes tight. Chaque getter de collection retourne une vue immutable snapshot. Ne jamais exposer session, texture, buffer, future native ou Throwable.

**Étape 3 — Créer le context et le backend**

Définir d’abord la target liée au backend :

```kotlin
public data class GpuRenderTargetConfig(
    val extent: SceneExtent,
    val colorSpace: ColorSpace,
    val frameLocalBudgetBytes: Long,
    val internalFormat: PlanLogicalColorFormat =
        PlanLogicalColorFormat.RGBA8_UNORM_SRGB_LINEAR_PREMUL,
)
```

Le constructeur exige sRGB, une limite positive et le seul format interne W3. Définir ensuite les ports injectables dans `GpuRenderRuntimePorts.kt` :

```kotlin
internal interface GpuBackendRuntimeOwnerPort : AutoCloseable {
    fun createOrNull(): GpuBackendSessionPort?
    fun disposeGeneration(deviceGeneration: GPUDeviceGenerationID)
}

internal interface GpuBackendSessionPort : AutoCloseable {
    val deviceGeneration: GPUDeviceGenerationID
    val capabilities: GPUCapabilities?
    fun prepareSceneFrameSession(request: GPUOffscreenTargetRequest): GpuPreparedSceneSessionPort
}

internal interface GpuPreparedSceneSessionPort : AutoCloseable {
    val deviceGeneration: GPUDeviceGenerationID
    fun renderFrame(
        taskList: GPUTaskList,
        outputRequest: GPUSceneFrameOutputRequest,
    ): GpuPreparedFrameHandle
}

internal data class GpuPreparedFrameHandle(
    val immediateState: GPUFrameImmediateState,
    val completion: CompletionStage<GPUPreparedSceneCompletedFrameResult>,
)

internal fun interface GpuCompletionAwaiter {
    suspend fun await(
        completion: CompletionStage<GPUPreparedSceneCompletedFrameResult>,
    ): GPUPreparedSceneCompletedFrameResult
}
```

Fournir dans le même fichier les adapters de production :

- `DefaultGpuBackendRuntimeOwner` ouvre le wrapper via `GPUBackendRuntimeFactory.createOrNull()` ;
- `GPUBackendSession.prepareSceneFrameSession` est adapté vers `GpuPreparedSceneSessionPort` ;
- `disposeGeneration` vérifie la génération courante, ferme les sessions préparées du wrapper, appelle `GPUBackendRuntimeFactory.dispose()`, puis vide la session afin que le prochain `createOrNull()` recrée réellement le runtime ;
- `close()` applique le même ordre sessions → `GPUBackendRuntimeFactory.dispose()` et devient idempotent ;
- l’awaiter de `CompletionStage` n’appelle jamais `cancel` sur la future source.

Le `NonClosingSession` renvoyé par la factory native n’est donc jamais considéré comme la frontière finale d’ownership : la libération process-wide passe explicitement par `dispose()`. `GpuRenderContext` reçoit `GpuBackendRuntimeOwnerPort` et `GpuCompletionAwaiter` dans son constructeur internal; les defaults pointent sur ces adapters. Les tests fournissent leurs fakes à ces deux interfaces.

`GpuRenderContext` possède le `GpuBackendSessionPort`, son snapshot de capability et une map de sessions préparées indexée par :

```kotlin
public data class GpuRenderSessionKey(
    val deviceGeneration: Long,
    val width: Int,
    val height: Int,
    val internalFormat: PlanLogicalColorFormat,
)
```

Chaque entrée possède son mutex/file de submissions et un `GpuPreparedSceneSessionPort`. `close()` interdit de nouvelles submissions, draine/ferme les sessions puis ferme l’owner backend. Exposer `invalidateDeviceGeneration(deviceGeneration: GPUDeviceGenerationID)` : il ferme/retire toutes les sessions de cette génération, appelle `runtimeOwner.disposeGeneration`, invalide le snapshot courant et force l’owner à ouvrir une nouvelle session avant le prochain planning.

Le backend est lié à un target interne `rgba8unorm-srgb` :

```kotlin
public class GpuRenderBackend(
    private val compiler: GpuPlanCompiler,
    private val context: GpuRenderContext,
    private val targetConfig: GpuRenderTargetConfig,
) : RenderBackend<RenderGraph, GpuFrameOutput>
```

`plan()` délègue au compiler avec le snapshot/context et le budget du target config. `submit()` valide de nouveau PlanId/génération, abaisse le plan, appelle `GPUPreparedSceneFrameSession.renderFrame` avec une output request `ReadbackRgba`, puis transforme l’unique completion en `RenderExecutionResult<GpuFrameOutput>`. La completion enregistrée est responsable du cleanup même si le waiter coroutine est annulé. Une indisponibilité initiale (`createOrNull() == null`) retourne `DeviceFailure` sans invalidation, puisqu’aucune génération n’existe. Un diagnostic natif de device loss ou une génération changée sur une session déjà ouverte appelle `invalidateDeviceGeneration(generationExistante)` avant de compléter en `DeviceFailure`. Un simple refus de plan, submit ou readback ne dispose pas le device.

**Étape 4 — Vérifier et committer**

```bash
rtk ./gradlew :render-ir:test :gpu-plan:test
rtk ./gradlew :gpu-renderer:test --tests '*Gpu*Plan*' --tests '*GpuRender*' --tests '*GpuFrameOutputTest*'
rtk git add gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/planning/GpuFrameOutput.kt gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/planning/GpuRenderRuntimePorts.kt gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/planning/GpuRenderContext.kt gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/planning/GpuRenderBackend.kt gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/planning/GpuFrameOutputTest.kt gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/planning/GpuRenderContextTest.kt gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/planning/GpuRenderBackendTest.kt
rtk git commit -m "feat: execute gpu plans through render context"
```

## Task 6 — Router atomiquement `Surface.render()`

**Fichiers :**

- Modifier `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/RenderConfig.kt`.
- Modifier `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/Surface.kt`.
- Modifier `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPURenderer.kt`.
- Créer `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUPlanSurfaceShallowGate.kt`.
- Créer `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUPlanSurfaceRouter.kt`.
- Créer `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUPlanRenderContextOwner.kt`.
- Créer `kanvas/src/test/kotlin/org/graphiks/kanvas/surface/RenderConfigFrameBudgetTest.kt`.
- Créer `kanvas/src/test/kotlin/org/graphiks/kanvas/surface/gpu/GPUPlanSurfaceRouterTest.kt`.
- Modifier `kanvas/src/test/kotlin/org/graphiks/kanvas/surface/SurfaceTest.kt`.

**Étape 1 — Écrire les tests rouges du budget et de la shallow gate**

Vérifier `RenderConfig.DEFAULT.frameLocalBudgetBytes == 1L shl 30`, le parsing de `kanvas.render.frameLocalBudgetBytes`, et le rejet des valeurs nulles/négatives au constructeur.

Exercer la shallow gate uniquement par le résultat produit du routeur/`Surface` : seules `DrawRect`, `DrawColor`, `SetTransform`, `SetClip`, `Annotation` sont candidates; `Clear` et toute autre variante conservent les pixels/diagnostics legacy; 512 commandes sont admises et 513 prennent le legacy. Ne pas tester directement la classe interne ni affirmer quel composant a été appelé. La gate n’accepte ni scene ni backend dans son API, ce qui empêche tout lowering/capture caché par construction.

```bash
rtk ./gradlew :kanvas:test --tests '*RenderConfigFrameBudgetTest*' --tests '*GPUPlanSurfaceRouterTest*'
```

Résultat attendu : rouge, budget et routeur absents.

**Étape 2 — Ajouter le budget produit**

Ajouter :

```kotlin
val frameLocalBudgetBytes: Long = 1L shl 30
```

Valider strictement `> 0`, lire la property par `toLongOrNull()` et retomber sur la valeur par défaut si elle est absente ou non parsable. Une valeur parsée `<= 0` doit échouer à la construction au lieu de subir un coercion silencieuse.

**Étape 3 — Implémenter l’ordre de routage fermé**

Définir dans `GPUPlanSurfaceRouter.kt` le seul seam de capture nécessaire aux tests de classification :

```kotlin
internal fun interface SceneCapturePort {
    fun capture(
        operations: List<DisplayOp>,
        extent: SceneExtent,
        colorSpace: ColorSpace,
        limits: SceneCaptureLimits,
    ): SceneCaptureResult
}
```

Le constructeur internal du routeur accepte ce port avec un default déléguant à `DisplayOpSceneAdapter.capture`. Il accepte aussi des `SceneCaptureLimits`, afin que le produit garde `SceneCaptureLimits.DEFAULT` et que les tests puissent produire une limite déterministe sans inspecter l’implémentation.

Le routeur prend les `DisplayOp` originaux et applique exactement :

```text
dimensions valides
  -> shallow gate des DisplayOp et de la configuration physique
  -> capture Scene IR
  -> backend.plan
     Ready              -> backend.submit/await, jamais legacy ensuite
     GapNotMigrated     -> legacy avec les DisplayOp originaux
     GapOnPromotedScope -> terminal
     InvalidScene       -> terminal
     ResourceLimit      -> terminal
```

La vérification physique fait partie de la shallow gate, après la validation des dimensions et avant capture. Pour W3, seule `RenderConfig.gpuColorFormat == GPUColorFormat.RGBA8_UNORM_SRGB` passe cette pré-admission. Toute autre configuration physique reste hors capability et utilise le legacy avant capture, indépendamment du `PixelFormat` public RGBA/BGRA qui sera traité au readback boundary.

Les limites de nœuds/ressources de capture deviennent `GapNotMigrated`; non-finite, cardinalités et corruptions deviennent `InvalidScene`. L’owner crée paresseusement un `GpuRenderContext` process-scoped. Le nouveau chemin ne prend jamais `GPUPreparedSurfaceRuntimeOwner.lock`; seul l’appel de fallback reste dans `GPUPreparedSurfaceProductEntry`.

Ajouter des tests table-driven sur les codes réels de `SceneCaptureResult.Invalid` : `scene-node-limit`, `scene-resource-limit` et `graph-node-limit` donnent le fallback legacy; `non-finite-value`, `atlas-cardinality`, `scene-capture-invalid` et les autres corruptions donnent un terminal `InvalidScene`. Pour `scene-resource-limit`, que la shallow gate rend volontairement impossible à produire avec une vraie opération W3, le test injecte un `SceneCapturePort` retournant le résultat typé. Il observe uniquement l’issue/pixels du routeur, jamais qu’une méthode précise a été appelée.

Après completion, mapper le buffer tight de `GpuFrameOutput` vers `RenderResult` : RGBA copie directe; BGRA swap déterministe des offsets 0/2 par pixel; sRGB; `opsDispatched` issu des commandes visuelles; `opsRefused = 0`; pipelines/draw calls, diagnostics, structural steps et native evidence proviennent de l’output. Ne pas ajouter de compteur de route.

**Étape 4 — Tester le comportement produit**

Via `Surface.render()` :

- une frame W3 simple retourne un `RenderResult` propre ;
- `Clear` et une frame non migrée gardent leur résultat legacy ;
- une frame de 513 annotations/draws potentiels ne capture pas et garde le legacy ;
- une limite mémoire W3 trop basse termine avec `w3.budget.frame_local_exceeded` ;
- une erreur après `Ready` est terminale et ne produit aucun second rendu observable.

Ne pas créer de spy qui affirme qu’une classe/méthode précise a été appelée. L’absence de second rendu est vérifiée par l’issue publique et les pixels/diagnostics, sans compteur technique.

**Étape 5 — Vérifier et committer**

```bash
rtk ./gradlew :kanvas:test --tests '*RenderConfigFrameBudgetTest*' --tests '*GPUPlanSurfaceRouterTest*' --tests '*SurfaceTest*'
rtk ./gradlew :gpu-plan:test
rtk ./gradlew :gpu-renderer:test --tests '*Gpu*Plan*' --tests '*GpuRender*'
rtk git add kanvas/src/main/kotlin/org/graphiks/kanvas/surface/RenderConfig.kt kanvas/src/main/kotlin/org/graphiks/kanvas/surface/Surface.kt kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPURenderer.kt kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUPlanSurfaceShallowGate.kt kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUPlanSurfaceRouter.kt kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUPlanRenderContextOwner.kt kanvas/src/test/kotlin/org/graphiks/kanvas/surface/RenderConfigFrameBudgetTest.kt kanvas/src/test/kotlin/org/graphiks/kanvas/surface/gpu/GPUPlanSurfaceRouterTest.kt kanvas/src/test/kotlin/org/graphiks/kanvas/surface/SurfaceTest.kt
rtk git commit -m "feat: route W3 frames through gpu plan"
```

## Task 7 — Prouver les pixels et publier le statut W3

**Fichiers :**

- Créer `kanvas/src/test/kotlin/org/graphiks/kanvas/surface/W3SolidRectCpuOracle.kt`.
- Créer `kanvas/src/test/kotlin/org/graphiks/kanvas/surface/GPUPlanSurfacePixelTest.kt`.
- Créer `refactor/waves/W03-gpu-plan/status.md`.
- Modifier `refactor/README.md`.

**Étape 1 — Écrire l’oracle CPU indépendant**

L’oracle part de `ColorARGB`, décode sRGB, prémultiplie, applique `SrcOver` en Float, encode sRGB et quantifie au nearest 8-bit. Il ne dépend ni de `:gpu-plan`, ni de payloads GPU. Les fixtures utilisent des couleurs/couvertures dont la quantification n’est pas à une frontière demi-LSB.

```kotlin
private fun srcOver(src: LinearPremul, dst: LinearPremul): LinearPremul {
    val inverseAlpha = 1f - src.alpha
    return LinearPremul(
        red = src.red + dst.red * inverseAlpha,
        green = src.green + dst.green * inverseAlpha,
        blue = src.blue + dst.blue * inverseAlpha,
        alpha = src.alpha + dst.alpha * inverseAlpha,
    )
}
```

**Étape 2 — Ajouter les preuves pixel exactes**

Par `Surface` public, couvrir :

- deux rectangles opaques partiellement superposés ;
- deux rectangles translucides prouvant `SrcOver` linear-premultiplied ;
- un rectangle limité par `DeviceRect` ;
- `DrawColor(SRC_OVER)` ;
- la même scène en `PixelFormat.RGBA8` et `PixelFormat.BGRA8`, avec égalité exacte à l’oracle après ordre de canaux ;
- une frame manifestement non W3 comparée au résultat legacy attendu.

```bash
rtk ./gradlew :kanvas:test --tests '*GPUPlanSurfacePixelTest*'
```

Résultat attendu avant correction finale : au moins un écart exact révèle toute divergence de color transfer, blend, clip, row stride ou swizzle. Corriger le composant propriétaire de la décision; ne pas assouplir l’égalité pixel.

**Étape 3 — Lancer la gate ciblée**

```bash
rtk ./gradlew :math:geometry:jvmTest :math:geometry:jsTest :math:matrix:jvmTest :math:matrix:jsTest :math:color:jvmTest :math:color:jsTest
rtk ./gradlew :render-ir:test :gpu-plan:test
rtk ./gradlew :gpu-renderer:test --tests '*Gpu*Plan*' --tests '*GpuRender*' --tests '*GPUCorePrimitivePreparedFrameTaskListBuilderTest*'
rtk ./gradlew :kanvas:test --tests '*GPUPlanSurface*' --tests '*SurfaceTest*' --tests '*DisplayOpSceneAdapterTest*'
```

Ne lancer aucun test GM exhaustif et ne jamais lancer `jpg-color-cube` dans cette vague.

**Étape 4 — Vérifier la non-régression Kanvas globale**

```bash
rtk ./gradlew :kanvas:test --rerun-tasks
```

Comparer la sortie à la baseline W0–W2 : 3585 tests, 51 échecs connus, 0 erreur. Aucun nouvel échec n’est accepté. Si le nombre total augmente avec les tests W3, comparer la liste des tests rouges connue, pas uniquement le total.

**Étape 5 — Publier le statut canonique**

Créer `refactor/waves/W03-gpu-plan/status.md` avec : commit, capability exacte, architecture réellement branchée, commandes de preuve et résultats, baseline rouge inchangée, limites restantes pour W4+, exclusion font/codec et quarantaine `jpg-color-cube`. Mettre à jour la table et les liens de `refactor/README.md`. Ne créer aucun rapport intermédiaire supplémentaire.

**Étape 6 — Vérifier et committer**

```bash
rtk git diff --check
rtk git status --short
rtk git add kanvas/src/test/kotlin/org/graphiks/kanvas/surface/W3SolidRectCpuOracle.kt kanvas/src/test/kotlin/org/graphiks/kanvas/surface/GPUPlanSurfacePixelTest.kt refactor/waves/W03-gpu-plan/status.md refactor/README.md
rtk git commit -m "docs: publish W3 gpu plan evidence"
```

## Review finale de branche

Après Task 7, demander une review Sol complète contre la spec et ce plan. Corriger toute finding fondée, relancer la gate ciblée et `rtk git diff --check`, puis appliquer `superpowers:verification-before-completion`. Ne pousser la branche et ne mettre à jour/créer une PR qu’après autorisation explicite de l’utilisateur.
