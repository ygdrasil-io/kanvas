# Skia Renderer W0–W2 Foundation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task with review checkpoints.

**Goal:** Établir un dénominateur GM fiable, rendre la géométrie enregistrée immuable et possédée par `:math`, puis introduire une `Scene IR` (représentation intermédiaire de scène) backend-agnostic capable d'enregistrer toutes les GMs éligibles sans modifier le rendu produit par le moteur legacy.

**Architecture:** W0 rend le périmètre observable et retire les exclusions par coût. W1 ajoute les valeurs géométriques canoniques dans `:math`, conserve les façades publiques de compatibilité et fige toute géométrie au moment de l'enregistrement. W2 ajoute `:render-ir`, convertit les `DisplayOp` en snapshots profondément immuables, définit le port backend typé et corrige la direction des dépendances Gradle. Le rendu de production continue d'utiliser le renderer actuel pendant ces trois vagues.

**Tech Stack:** Kotlin 2.x, Kotlin Multiplatform pour `:math`, Kotlin/JVM pour `:render-ir` et `:kanvas`, Gradle Kotlin DSL, JUnit 5, `kotlin.test`, WebGPU/wgpu4k uniquement pour la preuve d'inventaire existante.

**Spec:** [`refactor/specs/2026-08-29-skia-renderer-remediation-design.md`](../specs/2026-08-29-skia-renderer-remediation-design.md)

**Global Constraints:** `font` et le décodage/encodage `codec` restent hors périmètre. Les images RGBA en mémoire restent éligibles. Aucun fallback CPU silencieux. Aucun mélange legacy/nouveau renderer dans une frame. Aucun test ne lit le source, les imports, les packages, les noms de classes internes, le WGSL ou la forme exacte d'un futur `RenderGraph`. Les frontières sont vérifiées par la compilation Gradle et la visibilité Kotlin. Toutes les écritures manuelles utilisent `apply_patch`; toutes les commandes shell sont préfixées par `rtk`.

## Carte des fichiers

### W0 — vérité de référence

- Créer `integration-tests/skia/src/test/kotlin/org/graphiks/kanvas/skia/SkiaGmConformance.kt`.
- Créer `integration-tests/skia/src/test/kotlin/org/graphiks/kanvas/skia/SkiaGmConformanceTest.kt`.
- Modifier `integration-tests/skia/src/test/kotlin/org/graphiks/kanvas/skia/GmCanvas.kt`.
- Modifier `integration-tests/skia/src/test/kotlin/org/graphiks/kanvas/skia/SkiaGmInventory.kt`.
- Modifier `integration-tests/skia/src/test/kotlin/org/graphiks/kanvas/skia/SkiaGmInventoryTest.kt`.
- Modifier `integration-tests/skia/src/test/kotlin/org/graphiks/kanvas/skia/SkiaGmRegistry.kt`.
- Créer ou modifier `integration-tests/skia/src/test/kotlin/org/graphiks/kanvas/skia/SkiaGmRegistryTest.kt`.
- Modifier `integration-tests/skia/src/test/kotlin/org/graphiks/kanvas/skia/gm/image/DrawBitmapRectGm.kt`.
- Modifier `integration-tests/skia/src/test/resources/META-INF/services/org.graphiks.kanvas.skia.SkiaGm`.
- Modifier `integration-tests/skia/test-similarity-scores.properties`.
- Régénérer `reports/gpu-renderer/evidence/gm-inventory/source-inventory.json`.
- Créer `refactor/waves/W00-truth-baseline/status.md`.
- Modifier `refactor/README.md`.

### W1 — géométrie immuable

- Créer `math/geometry/src/commonMain/kotlin/org/graphiks/math/geometry/PathF32.kt`.
- Créer `math/geometry/src/commonMain/kotlin/org/graphiks/math/geometry/PathBuilder.kt`.
- Créer `math/geometry/src/commonMain/kotlin/org/graphiks/math/geometry/PathAnalysisF32.kt`.
- Créer `math/geometry/src/commonMain/kotlin/org/graphiks/math/geometry/PathMeasureF32.kt`.
- Créer `math/geometry/src/commonMain/kotlin/org/graphiks/math/geometry/PathOpsF32.kt`.
- Créer `math/geometry/src/commonMain/kotlin/org/graphiks/math/geometry/RegionF32.kt`.
- Créer les tests homologues sous `math/geometry/src/commonTest/kotlin/org/graphiks/math/geometry/`.
- Créer `math/matrix/src/commonMain/kotlin/org/graphiks/math/matrix/PathTransformsF32.kt`.
- Créer `math/matrix/src/commonTest/kotlin/org/graphiks/math/matrix/PathTransformsF32Test.kt`.
- Modifier `kanvas/src/main/kotlin/org/graphiks/kanvas/geometry/FillType.kt`.
- Modifier `kanvas/src/main/kotlin/org/graphiks/kanvas/geometry/Path.kt`.
- Modifier `kanvas/src/main/kotlin/org/graphiks/kanvas/geometry/PathMeasure.kt`.
- Modifier `kanvas/src/main/kotlin/org/graphiks/kanvas/geometry/PathOps.kt`.
- Modifier `kanvas/src/main/kotlin/org/graphiks/kanvas/geometry/Region.kt`.
- Créer `kanvas/src/main/kotlin/org/graphiks/kanvas/canvas/DisplayOpSnapshot.kt`.
- Modifier `kanvas/src/main/kotlin/org/graphiks/kanvas/canvas/Canvas.kt`.
- Modifier `kanvas/src/main/kotlin/org/graphiks/kanvas/canvas/ClipStack.kt`.
- Modifier `kanvas/src/main/kotlin/org/graphiks/kanvas/canvas/DisplayListBuffer.kt`.
- Modifier `kanvas/src/main/kotlin/org/graphiks/kanvas/picture/Picture.kt`.
- Créer `kanvas/src/main/kotlin/org/graphiks/kanvas/picture/PictureWireV8.kt`.
- Modifier `kanvas/src/main/kotlin/org/graphiks/kanvas/picture/PictureRecorder.kt`.
- Modifier `kanvas/src/test/kotlin/org/graphiks/kanvas/geometry/GeometryTest.kt`.
- Modifier `kanvas/src/test/kotlin/org/graphiks/kanvas/geometry/PathTest.kt`.
- Créer `kanvas/src/test/kotlin/org/graphiks/kanvas/canvas/RecordedGeometrySnapshotTest.kt`.
- Modifier `kanvas/src/test/kotlin/org/graphiks/kanvas/picture/PictureTest.kt`.
- Créer `kanvas/src/test/resources/picture/format-7-path.base64`.
- Créer `refactor/waves/W01-immutable-geometry/status.md`.

### W2 — Scene IR et frontières

- Modifier `settings.gradle.kts`.
- Créer `render-ir/build.gradle.kts`.
- Créer `render-ir/src/main/kotlin/org/graphiks/kanvas/render/ir/CanonicalValue.kt`.
- Créer `render-ir/src/main/kotlin/org/graphiks/kanvas/render/ir/SceneSnapshot.kt`.
- Créer `render-ir/src/main/kotlin/org/graphiks/kanvas/render/ir/SceneCommand.kt`.
- Créer `render-ir/src/main/kotlin/org/graphiks/kanvas/render/ir/GeometryNode.kt`.
- Créer `render-ir/src/main/kotlin/org/graphiks/kanvas/render/ir/MaterialNode.kt`.
- Créer `render-ir/src/main/kotlin/org/graphiks/kanvas/render/ir/EffectNode.kt`.
- Créer `render-ir/src/main/kotlin/org/graphiks/kanvas/render/ir/ResourceSnapshot.kt`.
- Créer `render-ir/src/main/kotlin/org/graphiks/kanvas/render/ir/RenderBackend.kt`.
- Créer `render-ir/src/main/kotlin/org/graphiks/kanvas/render/ir/SceneArchiveCodec.kt`.
- Créer les tests homologues sous `render-ir/src/test/kotlin/org/graphiks/kanvas/render/ir/`.
- Créer `kanvas/src/main/kotlin/org/graphiks/kanvas/render/ir/DisplayOpSceneAdapter.kt`.
- Créer `kanvas/src/main/kotlin/org/graphiks/kanvas/render/ir/PaintSceneAdapter.kt`.
- Créer `kanvas/src/main/kotlin/org/graphiks/kanvas/render/ir/ResourceSceneAdapter.kt`.
- Créer `kanvas/src/main/kotlin/org/graphiks/kanvas/render/ir/SceneDisplayOpAdapter.kt`.
- Créer les tests homologues sous `kanvas/src/test/kotlin/org/graphiks/kanvas/render/ir/`.
- Modifier `kanvas/src/main/kotlin/org/graphiks/kanvas/picture/Picture.kt` pour déléguer le format 8 à `SceneArchiveCodec` tout en conservant le reader legacy 1–7.
- Modifier `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/Surface.kt`.
- Modifier `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/RenderConfig.kt`.
- Créer `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/PreparedImageRoute.kt`.
- Modifier `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUPreparedDrawImageLowerer.kt` pour adapter le type public neutre vers le type GPU interne.
- Modifier `kanvas/build.gradle.kts`.
- Modifier `integration-tests/skia/build.gradle.kts`.
- Créer `integration-tests/skia/src/test/kotlin/org/graphiks/kanvas/skia/SkiaGmSceneCaptureTest.kt`.
- Internaliser les déclarations d'adapter exposées dans les fichiers listés à la tâche 14.
- Créer `refactor/waves/W02-scene-ir/status.md`.
- Modifier `refactor/README.md`.

## Task 1 — Réparer le registre GM réel

**Fichiers :**

- Modifier `integration-tests/skia/src/test/resources/META-INF/services/org.graphiks.kanvas.skia.SkiaGm`.
- Modifier `integration-tests/skia/src/test/kotlin/org/graphiks/kanvas/skia/gm/image/DrawBitmapRectGm.kt`.
- Créer ou modifier `integration-tests/skia/src/test/kotlin/org/graphiks/kanvas/skia/SkiaGmRegistryTest.kt`.

**Étape 1 — Écrire le test comportemental rouge**

Ajouter un test qui charge le registre de production, exige que chaque provider soit instanciable et que tous les noms GM soient uniques :

```kotlin
@Test
fun `production GM registry contains only loadable unique entries`() {
    val entries = SkiaGmRegistry.entries()
    assertTrue(entries.isNotEmpty())
    assertEquals(emptyList(), entries.filter { it.gm == null }.map { it.provider })
    val names = entries.map { requireNotNull(it.gm).name }
    assertEquals(names.size, names.toSet().size)
}
```

Ce test exerce le comportement du harness de conformance via `SkiaGmRegistry.entries()` ; il ne lit ni le fichier service, ni les sources, ni les noms de classes attendus.

Exécuter :

```bash
rtk ./gradlew :integration-tests:skia:test --tests '*SkiaGmRegistryTest*'
```

Résultat attendu : échec listant les sept providers abstraits ou sans constructeur vide actuels.

**Étape 2 — Remplacer les providers invalides par leurs variantes concrètes**

Dans le service loader :

- retirer `MatrixConvolutionGm`, déjà couvert par ses six variantes concrètes enregistrées ;
- remplacer `ComplexClip2Gm` par `ComplexClip2RectGm`, `ComplexClip2RectAaGm`, `ComplexClip2RRectGm`, `ComplexClip2RRectAaGm`, `ComplexClip2PathGm` et `ComplexClip2PathAaGm` ;
- remplacer `AnimCodecPlayerExifGm` par `AnimCodecPlayerExifRequiredWebpGm`, `AnimCodecPlayerExifRequiredGifGm` et `AnimCodecPlayerExifStoplightHWebpGm` ;
- remplacer `InnerShapesGm` par `InnerShapesAaGm` et `InnerShapesBwGm` ;
- remplacer `TrickyCubicStrokesGm` par `TrickyCubicStrokesButtMiterGm` et `TrickyCubicStrokesRoundCapsGm` ;
- remplacer `GlyphPosGm` par les six classes `GlyphPosHbGm`, `GlyphPosNbGm`, `GlyphPosHsGm`, `GlyphPosNsGm`, `GlyphPosHfGm` et `GlyphPosNfGm` ;
- remplacer `DrawBitmapRectGm` par quatre variantes nommées.

Rendre `DrawBitmapRectGm` héritable et ajouter :

```kotlin
class DrawBitmapRectBitmapGm : DrawBitmapRectGm(Variant.BITMAP)
class DrawBitmapRectSubsetGm : DrawBitmapRectGm(Variant.BITMAP_SUBSET)
class DrawImageRectGm : DrawBitmapRectGm(Variant.IMAGE)
class DrawImageRectSubsetGm : DrawBitmapRectGm(Variant.IMAGE_SUBSET)
```

Enregistrer ces quatre classes, pas la classe paramétrée.

**Étape 3 — Vérifier et committer**

```bash
rtk ./gradlew :integration-tests:skia:test --tests '*SkiaGmRegistryTest*'
rtk git add integration-tests/skia/src/test/resources/META-INF/services/org.graphiks.kanvas.skia.SkiaGm integration-tests/skia/src/test/kotlin/org/graphiks/kanvas/skia/gm/image/DrawBitmapRectGm.kt integration-tests/skia/src/test/kotlin/org/graphiks/kanvas/skia/SkiaGmRegistryTest.kt
rtk git commit -m "test: make Skia GM registry fully loadable"
```

Résultat attendu : test vert, 631 GMs instanciables, aucun provider `UNKNOWN`.

## Task 2 — Introduire la classification de conformance et l'inventaire v3

**Fichiers :**

- Créer `integration-tests/skia/src/test/kotlin/org/graphiks/kanvas/skia/SkiaGmConformance.kt`.
- Créer `integration-tests/skia/src/test/kotlin/org/graphiks/kanvas/skia/SkiaGmConformanceTest.kt`.
- Modifier `integration-tests/skia/src/test/kotlin/org/graphiks/kanvas/skia/GmCanvas.kt`.
- Modifier `integration-tests/skia/src/test/kotlin/org/graphiks/kanvas/skia/SkiaGmRenderer.kt`.
- Modifier `integration-tests/skia/src/test/kotlin/org/graphiks/kanvas/skia/SkiaGmInventory.kt`.
- Modifier `integration-tests/skia/src/test/kotlin/org/graphiks/kanvas/skia/SkiaGmInventoryTest.kt`.

**Étape 1 — Écrire les invariants rouges**

Tester les comportements suivants :

- une GM obtient exactement une décision ;
- `eligible` n'a ni raison ni owner ;
- `excluded-font` et `excluded-codec` exigent une raison non vide ;
- `accepted-skia-gap` exige une raison et un owner ;
- un coût `BLOCKING` n'exclut jamais une GM éligible ;
- une utilisation observée de `drawString`, `drawGlyphs` ou `drawTextBlob` classe la GM `excluded-font` avant soumission ;
- une entrée explicitement codec est `excluded-codec` sans appeler `Surface.render()` ;
- le JSON v3 expose scope, raison, owner et synthèses déterministes.

Le contrat central doit être :

```kotlin
enum class GmConformanceScope(val wireName: String) {
    ELIGIBLE("eligible"),
    EXCLUDED_FONT("excluded-font"),
    EXCLUDED_CODEC("excluded-codec"),
    ACCEPTED_SKIA_GAP("accepted-skia-gap"),
}

data class GmConformanceDecision(
    val scope: GmConformanceScope,
    val reason: String? = null,
    val owner: String? = null,
) {
    init {
        when (scope) {
            GmConformanceScope.ELIGIBLE -> require(reason == null && owner == null)
            GmConformanceScope.EXCLUDED_FONT,
            GmConformanceScope.EXCLUDED_CODEC,
            -> require(!reason.isNullOrBlank() && owner == null)
            GmConformanceScope.ACCEPTED_SKIA_GAP ->
                require(!reason.isNullOrBlank() && !owner.isNullOrBlank())
        }
    }

    val mustAttempt: Boolean
        get() = scope == GmConformanceScope.ELIGIBLE ||
            scope == GmConformanceScope.ACCEPTED_SKIA_GAP
}
```

Exécuter :

```bash
rtk ./gradlew :integration-tests:skia:test --tests '*SkiaGmConformanceTest*' --tests '*SkiaGmInventoryTest*'
```

Résultat attendu : échec car la classification et les champs v3 n'existent pas.

**Étape 2 — Capturer la dépendance font comme comportement du harness**

Ajouter à `GmCanvas` un ensemble privé de dépendances externes et le marquer dans `drawString`, ses variantes alignées via délégation, `drawGlyphs` et `drawTextBlob` :

```kotlin
enum class GmExternalDependency { FONT }

private val observedDependencies = linkedSetOf<GmExternalDependency>()

internal fun observedExternalDependencies(): Set<GmExternalDependency> =
    observedDependencies.toSet()
```

Le signal est lu après `gm.draw` et avant l'unique appel à `Surface.render()`. Une famille `TEXT` est exclue avant enregistrement. Une GM non-TEXT qui produit du texte est exclue après enregistrement mais avant soumission. Le test porte sur l'absence observable de soumission, jamais sur l'appel d'une méthode interne.

**Étape 3 — Ajouter le catalogue codec explicite**

`SkiaGmConformance` contient un ensemble fermé de noms GM correspondant aux sources de l'annexe A. La priorité de décision est : codec explicite, famille/usage font, accepted gap, eligible. L'ensemble `accepted-skia-gap` est vide en W0.

Une exclusion codec décrit la dépendance exacte avec `reason = "direct-codec-decode-or-encode"`. Une exclusion font utilise `reason = "direct-font-output"`. Une image créée par `Image.fromPixels`, un `Bitmap`, une snapshot de `Surface` ou un tableau RGBA ne déclenche aucune exclusion.

**Étape 4 — Produire le schéma `gpu-gm-inventory-v3`**

Étendre chaque ligne avec :

```kotlin
val conformanceScope: String
val conformanceReason: String?
val conformanceOwner: String?
```

Ajouter une synthèse déterministe contenant : nombre enregistré, population à tenter, compte par scope, compte par famille et regroupement des premiers diagnostics terminaux. Supprimer entièrement la branche `gm.renderCost == RenderCost.BLOCKING`. Pour une décision `mustAttempt`, appeler exactement une fois la frontière `Surface.render()` après setup réussi.

**Étape 5 — Vérifier et committer**

```bash
rtk ./gradlew :integration-tests:skia:test --tests '*SkiaGmConformanceTest*' --tests '*SkiaGmInventoryTest*'
rtk git add integration-tests/skia/src/test/kotlin/org/graphiks/kanvas/skia
rtk git commit -m "feat: classify the Skia GM conformance population"
```

Résultat attendu : tests verts ; aucune route `excluded:blocking-by-policy` ne peut être produite.

## Task 3 — Réconcilier les scores et figer W00

**Fichiers :**

- Modifier `integration-tests/skia/test-similarity-scores.properties`.
- Modifier `integration-tests/skia/src/test/kotlin/org/graphiks/kanvas/skia/SkiaGmInventory.kt`.
- Régénérer `reports/gpu-renderer/evidence/gm-inventory/source-inventory.json`.
- Créer `refactor/waves/W00-truth-baseline/status.md`.
- Modifier `refactor/README.md`.

**Étape 1 — Rendre l'audit strict**

Remplacer l'appel actuel par :

```kotlin
val inventory = buildSkiaGmInventory(
    gms = rows,
    referenceDir = File("src/test/resources/reference"),
    scoresFile = scoreFile,
    renderEvidence = evidence,
    allowOrphanScores = false,
)
require(scoreAudit.strict) {
    "Orphan GM scores: ${scoreAudit.orphanRows.joinToString()}"
}
```

Exécuter d'abord :

```bash
rtk jq -r '.scoreAudit.orphanRows[]' reports/gpu-renderer/evidence/gm-inventory/source-inventory.json
```

Supprimer de `test-similarity-scores.properties` chaque clé encore orpheline après la réparation du registre. Ne modifier aucune valeur conservée et ne créer aucun score pour rendre une GM verte.

**Étape 2 — Régénérer l'inventaire**

```bash
rtk ./gradlew :integration-tests:skia:generateSkiaGmInventory
rtk jq -e '.schemaVersion == "gpu-gm-inventory-v3" and .scoreAudit.strict == true and (.rows | length) == 631 and ([.rows[] | select(.family == "UNKNOWN")] | length) == 0 and ([.rows[] | select(.route == "excluded:blocking-by-policy")] | length) == 0' reports/gpu-renderer/evidence/gm-inventory/source-inventory.json
```

Résultat attendu : `BUILD SUCCESSFUL`, puis `true`.

**Étape 3 — Écrire le statut W00**

`refactor/waves/W00-truth-baseline/status.md` enregistre :

- le commit et la date ;
- les 631 GMs enregistrées ;
- les comptes exacts par scope et famille lus dans le JSON v3 ;
- le nombre attempted/rendered/terminal/setup-failed ;
- les diagnostics terminaux groupés ;
- la politique pixel inchangée ;
- la commande de régénération et son résultat ;
- la gate W00 cochée uniquement si le score audit est strict, le registre charge intégralement et aucune exclusion générique ne subsiste.

Ajouter le lien dans `refactor/README.md`.

**Étape 4 — Committer**

```bash
rtk git add integration-tests/skia/test-similarity-scores.properties reports/gpu-renderer/evidence/gm-inventory/source-inventory.json refactor/waves/W00-truth-baseline/status.md refactor/README.md
rtk git commit -m "docs: freeze the W00 Skia truth baseline"
```

## Task 4 — Ajouter `PathF32` et `PathBuilder` dans `:math:geometry`

**Fichiers :**

- Créer `math/geometry/src/commonMain/kotlin/org/graphiks/math/geometry/PathF32.kt`.
- Créer `math/geometry/src/commonMain/kotlin/org/graphiks/math/geometry/PathBuilder.kt`.
- Créer `math/geometry/src/commonTest/kotlin/org/graphiks/math/geometry/PathF32Test.kt`.
- Créer `math/geometry/src/commonTest/kotlin/org/graphiks/math/geometry/PathBuilderTest.kt`.

**Étape 1 — Écrire les tests rouges**

Tester : tous les verbes, plusieurs contours, les quatre fill rules, `addRect`, `addOval`, `addRRect`, `addPath`, l'égalité structurelle et surtout l'absence de mutation après `build()`.

```kotlin
@Test
fun `build freezes previous path values`() {
    val builder = PathBuilder().moveTo(1f, 2f).lineTo(3f, 4f)
    val first = builder.build()
    builder.lineTo(5f, 6f)

    assertEquals(2, first.segmentCount)
    assertEquals(PathSegmentF32.LineTo(Point2F32(3f, 4f)), first.segmentAt(1))
}
```

```bash
rtk ./gradlew :math:geometry:jvmTest --tests '*PathF32Test*' --tests '*PathBuilderTest*'
```

Résultat attendu : échec de compilation, les types n'existent pas.

**Étape 2 — Implémenter les valeurs canoniques**

Le contrat public de `:math:geometry` est :

```kotlin
enum class FillRule { WINDING, EVEN_ODD, INVERSE_WINDING, INVERSE_EVEN_ODD }

sealed interface PathSegmentF32 {
    data class MoveTo(val point: Point2F32) : PathSegmentF32
    data class LineTo(val point: Point2F32) : PathSegmentF32
    data class QuadTo(val control: Point2F32, val point: Point2F32) : PathSegmentF32
    data class CubicTo(
        val control1: Point2F32,
        val control2: Point2F32,
        val point: Point2F32,
    ) : PathSegmentF32
    data class ArcTo(
        val radius: Vector2F32,
        val xAxisRotation: Float,
        val largeArc: Boolean,
        val sweep: Boolean,
        val point: Point2F32,
    ) : PathSegmentF32
    data object Close : PathSegmentF32
}

class PathF32 internal constructor(
    val fillRule: FillRule,
    segments: Collection<PathSegmentF32>,
) : Iterable<PathSegmentF32> {
    private val values = segments.toList()
    val segmentCount: Int get() = values.size
    fun segmentAt(index: Int): PathSegmentF32 = values[index]
    override fun iterator(): Iterator<PathSegmentF32> = values.iterator()
}
```

`PathF32` implémente `equals`, `hashCode` et `toString` sur les valeurs copiées. Il n'expose ni `MutableList`, ni tableau mutable, ni setter. `PathBuilder.build()` effectue une copie et chaque convenience builder retourne `this`.

**Étape 3 — Vérifier JVM et JS, puis committer**

```bash
rtk ./gradlew :math:geometry:jvmTest :math:geometry:jsNodeTest
rtk git add math/geometry/src/commonMain/kotlin/org/graphiks/math/geometry/PathF32.kt math/geometry/src/commonMain/kotlin/org/graphiks/math/geometry/PathBuilder.kt math/geometry/src/commonTest/kotlin/org/graphiks/math/geometry/PathF32Test.kt math/geometry/src/commonTest/kotlin/org/graphiks/math/geometry/PathBuilderTest.kt
rtk git commit -m "feat(math): add immutable path geometry"
```

## Task 5 — Déplacer analyses, régions, mesures et opérations neutres dans `:math`

**Fichiers :**

- Créer `math/geometry/src/commonMain/kotlin/org/graphiks/math/geometry/PathAnalysisF32.kt`.
- Créer `math/geometry/src/commonMain/kotlin/org/graphiks/math/geometry/PathMeasureF32.kt`.
- Créer `math/geometry/src/commonMain/kotlin/org/graphiks/math/geometry/PathOpsF32.kt`.
- Créer `math/geometry/src/commonMain/kotlin/org/graphiks/math/geometry/RegionF32.kt`.
- Créer `math/geometry/src/commonTest/kotlin/org/graphiks/math/geometry/PathAnalysisF32Test.kt`.
- Créer `math/geometry/src/commonTest/kotlin/org/graphiks/math/geometry/PathMeasureF32Test.kt`.
- Créer `math/geometry/src/commonTest/kotlin/org/graphiks/math/geometry/PathOpsF32Test.kt`.
- Créer `math/geometry/src/commonTest/kotlin/org/graphiks/math/geometry/RegionF32Test.kt`.

**Étape 1 — Porter les tests de comportement avant les algorithmes**

Porter les cas actuels de bounds, `contains`, détection rect/oval/rrect/line, convexité, interpolation, mesure multi-contour, union/intersection/difference/xor et régions. Ajouter inverse fill et orientations de contour. Les résultats sont comparés comme géométrie ou par requêtes de points, pas par implémentation algorithmique.

```bash
rtk ./gradlew :math:geometry:jvmTest --tests '*PathAnalysisF32Test*' --tests '*PathMeasureF32Test*' --tests '*PathOpsF32Test*' --tests '*RegionF32Test*'
```

Résultat attendu : tests rouges jusqu'au port des comportements.

**Étape 2 — Implémenter les contrats neutres**

Utiliser les types suivants dans `:math:geometry` :

```kotlin
data class PathTopology(
    val contourCount: Int,
    val closedContourCount: Int,
    val orientation: ContourOrientation,
    val inverseFill: Boolean,
)

enum class ContourOrientation { CLOCKWISE, COUNTER_CLOCKWISE, MIXED, UNDEFINED }
enum class PathBooleanOp { DIFFERENCE, INTERSECT, UNION, XOR, REVERSE_DIFFERENCE }
enum class RegionBooleanOp { DIFFERENCE, INTERSECT, UNION, XOR, REVERSE_DIFFERENCE, REPLACE }
```

`RegionF32` est une valeur immuable ; chaque opération retourne une nouvelle région. `PathOpsF32` consomme et retourne exclusivement `PathF32`. `PathMeasureF32` ne dépend ni de `kanvas`, ni d'une matrice, ni du renderer.

**Étape 3 — Vérifier et committer**

```bash
rtk ./gradlew :math:geometry:jvmTest :math:geometry:jsNodeTest
rtk git add math/geometry/src/commonMain/kotlin/org/graphiks/math/geometry math/geometry/src/commonTest/kotlin/org/graphiks/math/geometry
rtk git commit -m "refactor(math): own neutral path operations"
```

## Task 6 — Placer les transformations géométriques dans `:math:matrix`

**Fichiers :**

- Créer `math/matrix/src/commonMain/kotlin/org/graphiks/math/matrix/PathTransformsF32.kt`.
- Créer `math/matrix/src/commonTest/kotlin/org/graphiks/math/matrix/PathTransformsF32Test.kt`.

**Étape 1 — Écrire les tests rouges**

Porter les cas existants d'arc sous translation, scale non uniforme, skew, miroir et perspective. Vérifier les endpoints, rayons, rotation et inversion de sweep ; tester aussi rect, oval et chemins multi-contours.

```kotlin
@Test
fun `mirroring a path reverses arc sweep without changing source`() {
    val source = PathBuilder()
        .moveTo(5f, 0f)
        .arcTo(5f, 8f, 15f, largeArc = true, sweep = true, x = 4f, y = 6f)
        .build()

    val mapped = Matrix3x3F32.scaling(-1f, 1f).map(source)
    val arc = mapped.segmentAt(1) as PathSegmentF32.ArcTo

    assertFalse(arc.sweep)
    assertTrue((source.segmentAt(1) as PathSegmentF32.ArcTo).sweep)
}
```

**Étape 2 — Implémenter sans cycle de module**

```kotlin
fun Matrix3x3F32.map(path: PathF32): PathF32

fun PathF32.transformedBy(matrix: Matrix3x3F32): PathF32 = matrix.map(this)
```

Extraire la logique canonique d'ellipse d'arc actuellement dans `kanvas.geometry.Path`. `:math:geometry` ne reçoit aucune dépendance vers `:math:matrix`.

**Étape 3 — Vérifier et committer**

```bash
rtk ./gradlew :math:matrix:jvmTest :math:matrix:jsNodeTest
rtk git add math/matrix/src/commonMain/kotlin/org/graphiks/math/matrix/PathTransformsF32.kt math/matrix/src/commonTest/kotlin/org/graphiks/math/matrix/PathTransformsF32Test.kt
rtk git commit -m "refactor(math): move path transforms to matrix"
```

## Task 7 — Brancher les façades de compatibilité et figer les opérations enregistrées

**Fichiers :**

- Modifier `kanvas/src/main/kotlin/org/graphiks/kanvas/geometry/FillType.kt`.
- Modifier `kanvas/src/main/kotlin/org/graphiks/kanvas/geometry/Path.kt`.
- Modifier `kanvas/src/main/kotlin/org/graphiks/kanvas/geometry/PathMeasure.kt`.
- Modifier `kanvas/src/main/kotlin/org/graphiks/kanvas/geometry/PathOps.kt`.
- Modifier `kanvas/src/main/kotlin/org/graphiks/kanvas/geometry/Region.kt`.
- Créer `kanvas/src/main/kotlin/org/graphiks/kanvas/canvas/DisplayOpSnapshot.kt`.
- Modifier `kanvas/src/main/kotlin/org/graphiks/kanvas/canvas/Canvas.kt`.
- Modifier `kanvas/src/main/kotlin/org/graphiks/kanvas/canvas/ClipStack.kt`.
- Modifier `kanvas/src/main/kotlin/org/graphiks/kanvas/canvas/DisplayListBuffer.kt`.
- Modifier `kanvas/src/main/kotlin/org/graphiks/kanvas/picture/PictureRecorder.kt`.
- Modifier `kanvas/src/test/kotlin/org/graphiks/kanvas/geometry/GeometryTest.kt`.
- Modifier `kanvas/src/test/kotlin/org/graphiks/kanvas/geometry/PathTest.kt`.
- Créer `kanvas/src/test/kotlin/org/graphiks/kanvas/canvas/RecordedGeometrySnapshotTest.kt`.

**Étape 1 — Écrire les régressions rouges de mutation**

Tester quatre frontières observables : mutation du path après `drawPath`, mutation du path de clip, mutation d'un path contenu dans `PathEffect.Path1D` et mutation d'un objet obtenu depuis un premier `snapshotOps()`.

```kotlin
@Test
fun `mutating a source path after draw cannot change recorded operations`() {
    val surface = Surface(32, 32)
    val source = Path().addRect(RectF32.ofLTRB(1f, 2f, 9f, 10f))
    surface.canvas().drawPath(source, Paint.fill(ColorARGB.Red))

    source.reset().addRect(RectF32.ofLTRB(20f, 20f, 30f, 30f))

    val recorded = assertIs<DisplayOp.DrawPath>(surface.snapshotOps().single())
    assertEquals(RectF32.ofLTRB(1f, 2f, 9f, 10f), recorded.path.computeBounds())
}
```

```bash
rtk ./gradlew :kanvas:test --tests '*RecordedGeometrySnapshotTest*'
```

Résultat attendu : au moins le path identity et le clip identity échouent avec le stockage actuel par référence.

**Étape 2 — Conserver l'API mutable, déléguer aux valeurs `:math`**

Ajouter les conversions de compatibilité :

```kotlin
internal fun Path.toPathF32(): PathF32
internal fun PathF32.toCompatibilityPath(): Path
```

`FillType` mappe explicitement vers `FillRule`. `PathMeasure`, `PathOps` et `Region` deviennent des façades qui convertissent vers les implémentations `:math`. Les signatures publiques actuelles restent inchangées.

**Étape 3 — Snapshotter toutes les géométries au buffer**

Ajouter :

```kotlin
internal fun DisplayOp.snapshotGeometry(): DisplayOp
internal fun ClipStack.snapshotGeometry(): ClipStack
internal fun Paint.snapshotGeometry(): Paint
```

La copie couvre path, rect/rrect mutables, listes de points, vertices/mesh, lattice, atlas transforms/rects, `PathEffect.Path1D`, `PathEffect.Path2D`, bounds de layer et Pictures imbriquées. `append` stocke une copie ; `ops()` retourne une nouvelle copie. Les images et graphes de matériau seront figés intégralement en W2, mais leurs géométries embarquées le sont dès W1.

**Étape 4 — Vérifier la compatibilité et committer**

```bash
rtk ./gradlew :kanvas:test --tests '*RecordedGeometrySnapshotTest*' --tests '*PathTest*' --tests '*GeometryTest*' --tests '*CanvasTest*' --tests '*ClipStackTest*'
rtk git add kanvas/src/main/kotlin/org/graphiks/kanvas/geometry kanvas/src/main/kotlin/org/graphiks/kanvas/canvas kanvas/src/main/kotlin/org/graphiks/kanvas/picture/PictureRecorder.kt kanvas/src/test/kotlin/org/graphiks/kanvas
rtk git commit -m "refactor: freeze geometry at Canvas recording"
```

## Task 8 — Écrire `Picture` v8 et conserver le reader v7

**Fichiers :**

- Modifier `kanvas/src/main/kotlin/org/graphiks/kanvas/picture/Picture.kt`.
- Créer `kanvas/src/main/kotlin/org/graphiks/kanvas/picture/PictureWireV8.kt`.
- Modifier `kanvas/src/test/kotlin/org/graphiks/kanvas/picture/PictureTest.kt`.
- Créer `kanvas/src/test/resources/picture/format-7-path.base64`.
- Créer `refactor/waves/W01-immutable-geometry/status.md`.
- Modifier `refactor/README.md`.

**Étape 1 — Figer une fixture v7 avant de changer le writer**

Avec le writer actuel, encoder une Picture 8×8 contenant un `DrawPath` avec un `Move`, `Line`, `Quad`, `Cubic`, `ArcTo`, `Close`, un fill inverse-even-odd et un `PathEffect.Path1D`. Stocker la Base64 exacte dans `format-7-path.base64`. Le test décode cette ressource via l'API publique et compare la sémantique reconstruite.

Ajouter aussi un test qui exige `8` dans les quatre octets de version du nouveau writer et un round-trip pour chaque enum sérialisée.

```bash
rtk ./gradlew :kanvas:test --tests '*PictureTest*'
```

Résultat attendu après ajout des attentes v8 : échec, le writer écrit encore `7`.

**Étape 2 — Remplacer tous les ordinals du writer v8**

`PictureWireV8.kt` contient exclusivement des mappings `when` explicites. Exemple :

```kotlin
internal fun stablePathSegmentId(segment: PathSegmentF32): Byte = when (segment) {
    is PathSegmentF32.MoveTo -> 1
    is PathSegmentF32.LineTo -> 2
    is PathSegmentF32.QuadTo -> 3
    is PathSegmentF32.CubicTo -> 4
    is PathSegmentF32.ArcTo -> 5
    PathSegmentF32.Close -> 6
}

internal fun stableFillRuleId(fillRule: FillRule): Byte = when (fillRule) {
    FillRule.WINDING -> 1
    FillRule.EVEN_ODD -> 2
    FillRule.INVERSE_WINDING -> 3
    FillRule.INVERSE_EVEN_ODD -> 4
}
```

Appliquer la même règle à `ColorType`, `AlphaType`, color space, paint style, cap/join, slots runtime, blend, tile, blur, channel, interpolation, point/vertex/lattice et clip op. Aucun `.ordinal` n'est utilisé par le writer v8.

Le reader choisit explicitement :

```kotlin
when (version) {
    in 1..7 -> decodeLegacyPicture(reader, version)
    8 -> decodePictureV8(reader)
    else -> null
}
```

Le writer n'écrit que v8. Le reader 1–7 conserve exactement les anciens ordinals et dispositions.

**Étape 3 — Vérifier la gate W01**

```bash
rtk ./gradlew :math:geometry:jvmTest :math:geometry:jsNodeTest :math:matrix:jvmTest :math:matrix:jsNodeTest :kanvas:test
```

Résultat attendu : tous les tests verts ; une mutation post-enregistrement ne modifie ni `Surface.snapshotOps()`, ni une Picture, ni son round-trip.

Renseigner `refactor/waves/W01-immutable-geometry/status.md` avec les commandes, les résultats, la compatibilité v7 et la gate. Ajouter le lien au README.

**Étape 4 — Committer**

```bash
rtk git add kanvas/src/main/kotlin/org/graphiks/kanvas/picture kanvas/src/test/kotlin/org/graphiks/kanvas/picture/PictureTest.kt kanvas/src/test/resources/picture/format-7-path.base64 refactor/waves/W01-immutable-geometry/status.md refactor/README.md
rtk git commit -m "feat: write immutable Picture format 8"
```

## Task 9 — Créer `:render-ir` et ses contrats fondamentaux

**Fichiers :**

- Modifier `settings.gradle.kts`.
- Créer `render-ir/build.gradle.kts`.
- Créer `render-ir/src/main/kotlin/org/graphiks/kanvas/render/ir/CanonicalValue.kt`.
- Créer `render-ir/src/main/kotlin/org/graphiks/kanvas/render/ir/SceneSnapshot.kt`.
- Créer `render-ir/src/main/kotlin/org/graphiks/kanvas/render/ir/SceneCommand.kt`.
- Créer `render-ir/src/main/kotlin/org/graphiks/kanvas/render/ir/GeometryNode.kt`.
- Créer `render-ir/src/main/kotlin/org/graphiks/kanvas/render/ir/MaterialNode.kt` avec les contrats de base étendus à la tâche 10.
- Créer `render-ir/src/main/kotlin/org/graphiks/kanvas/render/ir/EffectNode.kt` avec les contrats de base étendus à la tâche 10.
- Créer `render-ir/src/main/kotlin/org/graphiks/kanvas/render/ir/ResourceSnapshot.kt` avec les contrats de base étendus à la tâche 10.
- Créer `render-ir/src/main/kotlin/org/graphiks/kanvas/render/ir/RenderBackend.kt`.
- Créer `render-ir/src/test/kotlin/org/graphiks/kanvas/render/ir/SceneSnapshotTest.kt`.
- Créer `render-ir/src/test/kotlin/org/graphiks/kanvas/render/ir/RenderBackendResultTest.kt`.

**Étape 1 — Scaffolder le module et écrire les tests rouges**

`render-ir/build.gradle.kts` utilise `buildsrc.convention.kotlin-jvm` et `java-library`, avec :

```kotlin
dependencies {
    api(project(":math:geometry"))
    api(project(":math:matrix"))
    api(project(":math:color"))
    api(project(":color-management"))
    implementation(kotlin("stdlib"))
    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
}
```

Tester la stabilité de l'identité canonique, la copie des collections et les invariants des résultats typés.

```bash
rtk ./gradlew :render-ir:test
```

Résultat attendu : échec de compilation avant création des contrats.

**Étape 2 — Définir la scène et les axes**

Le cœur doit rester handle-free :

```kotlin
class SceneSnapshot private constructor(
    val extent: SceneExtent,
    val colorSpace: ColorSpace,
    commands: Collection<SceneCommand>,
) : Iterable<SceneCommand> {
    private val values = commands.toList()
    val commandCount: Int get() = values.size
    fun commandAt(index: Int): SceneCommand = values[index]
    override fun iterator(): Iterator<SceneCommand> = values.iterator()
    val canonicalId: CanonicalId = CanonicalSceneEncoder.encode(this)

    companion object {
        fun of(
            extent: SceneExtent,
            colorSpace: ColorSpace,
            commands: Collection<SceneCommand>,
        ): SceneSnapshot = SceneSnapshot(extent, colorSpace, commands)
    }
}

data class DrawNode(
    val geometry: GeometryNode,
    val material: MaterialNode,
    val coverage: CoverageRequest,
    val clip: ClipStackNode,
    val blend: BlendNode,
    val effects: EffectStack,
    val transform: Matrix3x3F32,
)
```

Créer dans cette tâche les interfaces fermées et les valeurs neutres minimales de `MaterialNode`, `EffectStack`, `BlendNode`, `ClipStackNode` et `ResourceSnapshot`, afin que `DrawNode` compile sans contrat temporaire. La tâche 10 ajoute leurs variants compositionnels et leurs copies profondes. `GeometryNode` couvre rect, rrect, double-rrect, `PathF32`, points, mesh indexé, image patch, image lattice, atlas et glyph runs déjà résolus. `SceneCommand` couvre draw, clear, begin/end layer, état sérialisable, annotation et readback. Aucun type n'importe `kanvas.canvas`, `gpu-renderer`, WebGPU ou WGSL.

**Étape 3 — Définir le port backend typé**

```kotlin
interface RenderBackend<P : Any> {
    fun plan(scene: SceneSnapshot, target: RenderTargetDescriptor): RenderPlanResult<P>
    fun submit(plan: P): RenderSubmission
}

sealed interface RenderPlanResult<out P : Any> {
    data class Ready<P : Any>(val plan: P) : RenderPlanResult<P>
    data class GapNotMigrated(val diagnostics: List<RenderDiagnostic>) : RenderPlanResult<Nothing>
    data class GapOnPromotedScope(val diagnostics: List<RenderDiagnostic>) : RenderPlanResult<Nothing>
    data class InvalidScene(val diagnostics: List<RenderDiagnostic>) : RenderPlanResult<Nothing>
    data class ResourceLimitExceeded(val diagnostics: List<RenderDiagnostic>) : RenderPlanResult<Nothing>
}

interface RenderSubmission {
    val id: SubmissionId
    suspend fun await(): RenderExecutionResult
}
```

`RenderExecutionResult` contient les cinq issues validées par la spec. Les diagnostics ont code stable, domaine, sévérité et message ; aucun Throwable ou type GPU n'est public.

**Étape 4 — Vérifier et committer**

```bash
rtk ./gradlew :render-ir:test
rtk git add settings.gradle.kts render-ir
rtk git commit -m "feat: introduce backend-neutral scene IR"
```

## Task 10 — Ajouter le material graph, les effets et ressources immuables

**Fichiers :**

- Modifier `render-ir/src/main/kotlin/org/graphiks/kanvas/render/ir/MaterialNode.kt`.
- Modifier `render-ir/src/main/kotlin/org/graphiks/kanvas/render/ir/EffectNode.kt`.
- Modifier `render-ir/src/main/kotlin/org/graphiks/kanvas/render/ir/ResourceSnapshot.kt`.
- Créer `render-ir/src/test/kotlin/org/graphiks/kanvas/render/ir/MaterialNodeTest.kt`.
- Créer `render-ir/src/test/kotlin/org/graphiks/kanvas/render/ir/ResourceSnapshotTest.kt`.

**Étape 1 — Écrire les tests rouges de profondeur**

Construire images, stops, tables, kernels, uniforms, vertices, indices et children depuis des collections mutables ; créer le snapshot ; muter chaque source ; vérifier égalité, identité canonique et valeurs du snapshot inchangées.

```kotlin
@Test
fun `image resource copies caller owned pixels`() {
    val pixels = byteArrayOf(1, 2, 3, 4)
    val resource = ImageResourceSnapshot.rgba8(1, 1, pixels, ColorSpace.SRGB)
    val identity = resource.canonicalId

    pixels.fill(0)

    assertContentEquals(byteArrayOf(1, 2, 3, 4), resource.copyPixels())
    assertEquals(identity, resource.canonicalId)
}
```

**Étape 2 — Implémenter tous les variants sémantiques existants**

`MaterialNode` couvre solid, quatre gradients, image sample, blend shader, runtime effect, local matrix, color filter, opacity, Perlin/fractal noise, working color space et coord clamp. `EffectNode` couvre tous les `ColorFilter`, `MaskFilter`, `PathEffect`, `ImageFilter` et blender publics actuels. Les graphes composés sont récursifs mais soumis à un budget explicite de profondeur et de nœuds.

Les tableaux sont encapsulés :

```kotlin
class ImmutableBytes private constructor(bytes: ByteArray) {
    private val value = bytes.copyOf()
    fun copyToByteArray(): ByteArray = value.copyOf()
    override fun equals(other: Any?): Boolean =
        other is ImmutableBytes && value.contentEquals(other.value)
    override fun hashCode(): Int = value.contentHashCode()

    companion object {
        fun copyOf(bytes: ByteArray): ImmutableBytes = ImmutableBytes(bytes)
    }
}
```

Appliquer le même pattern aux `FloatArray` et `UByteArray`. Les images sans pixels deviennent une ressource explicite `ExternalImageReference`, jamais une texture fictive silencieuse. Un runtime effect conserve id enregistré, ABI, layout des uniforms, valeurs et children ; il ne conserve aucun shader GPU compilé.

**Étape 3 — Vérifier et committer**

```bash
rtk ./gradlew :render-ir:test
rtk git add render-ir/src/main/kotlin/org/graphiks/kanvas/render/ir render-ir/src/test/kotlin/org/graphiks/kanvas/render/ir
rtk git commit -m "feat: add immutable scene material graph"
```

## Task 11 — Adapter tous les `DisplayOp` vers la Scene IR

**Fichiers :**

- Créer `kanvas/src/main/kotlin/org/graphiks/kanvas/render/ir/DisplayOpSceneAdapter.kt`.
- Créer `kanvas/src/main/kotlin/org/graphiks/kanvas/render/ir/PaintSceneAdapter.kt`.
- Créer `kanvas/src/main/kotlin/org/graphiks/kanvas/render/ir/ResourceSceneAdapter.kt`.
- Créer `kanvas/src/main/kotlin/org/graphiks/kanvas/render/ir/SceneDisplayOpAdapter.kt`.
- Créer `kanvas/src/test/kotlin/org/graphiks/kanvas/render/ir/DisplayOpSceneAdapterTest.kt`.
- Créer `kanvas/src/test/kotlin/org/graphiks/kanvas/render/ir/SceneRoundTripTest.kt`.
- Modifier `kanvas/build.gradle.kts` pour ajouter provisoirement `api(project(":render-ir"))` sans encore changer la visibilité GPU.

**Étape 1 — Écrire la matrice comportementale rouge**

Créer au moins une opération valide de chacun des 22 variants `DisplayOp` et vérifier : capture réussie, round-trip sémantique vers `DisplayOp`, ordre conservé, géométrie/matériau/clip/blend/effects conservés. Les assertions portent sur les valeurs publiques reconstruites, pas sur le nombre ou la classe des helpers internes.

Ajouter des mutations après capture pour une image, une table de color filter, un kernel, un dash, des vertices, une lattice et un runtime uniform.

```bash
rtk ./gradlew :kanvas:test --tests '*DisplayOpSceneAdapterTest*' --tests '*SceneRoundTripTest*'
```

Résultat attendu : échec car les adapters n'existent pas.

**Étape 2 — Implémenter la conversion exhaustive**

Le point d'entrée est :

```kotlin
sealed interface SceneCaptureResult {
    data class Captured(val scene: SceneSnapshot) : SceneCaptureResult
    data class Invalid(val diagnostics: List<RenderDiagnostic>) : SceneCaptureResult
}

object DisplayOpSceneAdapter {
    fun capture(
        operations: List<DisplayOp>,
        extent: SceneExtent,
        colorSpace: ColorSpace,
        limits: SceneCaptureLimits = SceneCaptureLimits.DEFAULT,
    ): SceneCaptureResult
}
```

Règles de mapping :

- rect/rrect/double-rrect/path/points/vertices/mesh deviennent `DrawNode` avec axes séparés ;
- image/image-nine/lattice/atlas deviennent géométrie de destination plus `ImageSample` et ressource immutable ;
- text devient un `GlyphRun` neutre avec glyph ids, positions, taille, variations et identité de typeface, sans importer `font` dans `:render-ir` ;
- Picture devient un sous-snapshot récursif borné ;
- `SetTransform` et `SetClip` restent des state markers sérialisables pour garantir le round-trip, les draws portant aussi leur état capturé ;
- layers, annotation, clear et readback gardent leur ordre exact ;
- toute valeur non finie ou structure cyclique retourne `Invalid` avant allocation backend.

**Étape 3 — Vérifier et committer**

```bash
rtk ./gradlew :render-ir:test :kanvas:test --tests '*DisplayOpSceneAdapterTest*' --tests '*SceneRoundTripTest*'
rtk git add kanvas/build.gradle.kts kanvas/src/main/kotlin/org/graphiks/kanvas/render/ir kanvas/src/test/kotlin/org/graphiks/kanvas/render/ir
rtk git commit -m "feat: capture DisplayOps into scene IR"
```

## Task 12 — Transférer la propriété du format Picture v8 à `:render-ir`

**Fichiers :**

- Créer `render-ir/src/main/kotlin/org/graphiks/kanvas/render/ir/SceneArchiveCodec.kt`.
- Créer `render-ir/src/test/kotlin/org/graphiks/kanvas/render/ir/SceneArchiveCodecTest.kt`.
- Modifier `kanvas/src/main/kotlin/org/graphiks/kanvas/picture/Picture.kt`.
- Modifier `kanvas/src/main/kotlin/org/graphiks/kanvas/picture/PictureWireV8.kt`.
- Modifier `kanvas/src/test/kotlin/org/graphiks/kanvas/picture/PictureTest.kt`.

**Étape 1 — Écrire les tests rouges de délégation et compatibilité**

Tester dans `:render-ir` le round-trip binaire de chaque commande et nœud, les IDs stables, les longueurs invalides et versions inconnues. Dans `:kanvas`, garder la fixture v7 et exiger que `Picture.toByteArray()` v8 soit décodable par `SceneArchiveCodec` puis reconstructible en Picture.

**Étape 2 — Déplacer le writer v8**

Le format public reste : magic `KPIC`, version 8, cull/extent, commandes IR. `SceneArchiveCodec` est l'unique writer v8. `PictureWireV8.kt` ne conserve que l'adaptation de façade nécessaire, puis peut être supprimé si toute sa logique a migré. `Picture.kt` conserve l'ancien reader 1–7 et délègue ainsi :

```kotlin
fun toByteArray(): ByteArray {
    val capture = DisplayOpSceneAdapter.capture(ops, cullRect.toExtent(), ColorSpace.SRGB)
    val scene = when (capture) {
        is SceneCaptureResult.Captured -> capture.scene
        is SceneCaptureResult.Invalid -> throw IllegalStateException(
            capture.diagnostics.joinToString { it.code },
        )
    }
    return SceneArchiveCodec.encodePicture(scene, cullRect)
}

fun decodePicture(data: ByteArray): Picture? = when (readPictureVersion(data)) {
    in 1..7 -> decodeLegacyPicture(data)
    8 -> SceneArchiveCodec.decodePicture(data)
        .getOrNull()
        ?.let(SceneDisplayOpAdapter::toPicture)
    else -> null
}
```

Le vrai code retourne un résultat typé pour les données v8 invalides ; la façade publique conserve son `Picture?`. Aucun format 1–7 n'est réécrit.

**Étape 3 — Vérifier et committer**

```bash
rtk ./gradlew :render-ir:test :kanvas:test --tests '*PictureTest*' --tests '*SceneArchiveCodecTest*'
rtk git add render-ir/src/main/kotlin/org/graphiks/kanvas/render/ir/SceneArchiveCodec.kt render-ir/src/test/kotlin/org/graphiks/kanvas/render/ir/SceneArchiveCodecTest.kt kanvas/src/main/kotlin/org/graphiks/kanvas/picture kanvas/src/test/kotlin/org/graphiks/kanvas/picture/PictureTest.kt
rtk git commit -m "refactor: move Picture v8 serialization to scene IR"
```

## Task 13 — Exposer la capture de scène et fermer la gate de toutes les GMs éligibles

**Fichiers :**

- Modifier `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/Surface.kt`.
- Créer `kanvas/src/test/kotlin/org/graphiks/kanvas/surface/SurfaceSceneSnapshotTest.kt`.
- Créer `integration-tests/skia/src/test/kotlin/org/graphiks/kanvas/skia/SkiaGmSceneCaptureTest.kt`.
- Modifier `integration-tests/skia/build.gradle.kts` pour dépendre explicitement de `:render-ir`.

**Étape 1 — Écrire les tests rouges**

Ajouter à `Surface` une capture sans rendu :

```kotlin
fun snapshotScene(): SceneCaptureResult = DisplayOpSceneAdapter.capture(
    operations = buffer.ops(),
    extent = SceneExtent(width, height),
    colorSpace = ColorSpace.SRGB,
)
```

Le test unitaire vérifie qu'elle ne soumet rien et que deux captures avant/après mutation externe sont égales.

Le test d'intégration itère le registre W0. Pour chaque décision `mustAttempt`, il exécute le setup/draw sans `Surface.render()`, appelle `snapshotScene()` et exige `Captured`. Il collecte toutes les erreurs et n'échoue qu'après la boucle afin de fournir la liste complète des GMs non capturables.

```kotlin
@Test
fun `every eligible GM records a scene snapshot`() {
    val failures = SkiaGmRegistry.all().mapNotNull { gm ->
        val recording = recordGmForSceneCapture(gm)
        if (!recording.decision.mustAttempt) return@mapNotNull null
        when (val capture = recording.capture) {
            is SceneCaptureResult.Captured -> null
            is SceneCaptureResult.Invalid ->
                "${gm.name}: ${capture.diagnostics.joinToString { it.code }}"
        }
    }
    assertEquals(emptyList(), failures)
}
```

Le helper applique aussi la décision font observée issue de `GmCanvas`; une GM reclassée font ne compte pas comme échec éligible et sa décision est la même que dans l'inventaire W0.

**Étape 2 — Boucler par cause sémantique**

Exécuter :

```bash
rtk ./gradlew :integration-tests:skia:test --tests '*SkiaGmSceneCaptureTest*'
```

Pour chaque groupe d'échecs, compléter un variant IR général ou la copie profonde correspondante. Ne créer ni allowlist de réussite, ni exclusion `blocking`, ni route par GM. Une nouvelle exclusion n'est admise que si elle satisfait strictement `excluded-font` ou `excluded-codec` et elle doit être reflétée dans W00.

Résultat attendu final : test vert pour 100 % de la population `mustAttempt`.

**Étape 3 — Vérifier le rendu inchangé et committer**

La capture est en lecture seule ; `Surface.render()` continue d'appeler uniquement `renderViaGpu` legacy en W2.

```bash
rtk ./gradlew :kanvas:test --tests '*SurfaceSceneSnapshotTest*' :integration-tests:skia:test --tests '*SkiaGmSceneCaptureTest*'
rtk git add kanvas/src/main/kotlin/org/graphiks/kanvas/surface/Surface.kt kanvas/src/test/kotlin/org/graphiks/kanvas/surface/SurfaceSceneSnapshotTest.kt integration-tests/skia/build.gradle.kts integration-tests/skia/src/test/kotlin/org/graphiks/kanvas/skia/SkiaGmSceneCaptureTest.kt
rtk git commit -m "test: prove eligible GMs capture into scene IR"
```

## Task 14 — Corriger les frontières de modules et publier le statut W02

**Fichiers :**

- Modifier `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/RenderConfig.kt`.
- Créer `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/PreparedImageRoute.kt`.
- Modifier `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUPreparedDrawImageLowerer.kt`.
- Internaliser les déclarations d'adapter dans :
  - `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUClipCoveragePlanner.kt` ;
  - `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUCorePrimitiveSemanticBuilder.kt` ;
  - `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUFramePathApiInventory.kt` ;
  - `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUMaterialMapper.kt` ;
  - `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUOpMapper.kt` ;
  - `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUPreparedCompositeCapture.kt` ;
  - `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUPreparedDrawImageLowerer.kt` ;
  - `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUPreparedImageGridLowerer.kt` ;
  - `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUPreparedImageSource.kt` ;
  - `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUPreparedTextContracts.kt` ;
  - `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUPreparedTextFontResolver.kt` ;
  - `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUPreparedTextLowerer.kt` ;
  - `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUPreparedVerticesContracts.kt` ;
  - `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUPreparedVerticesLowerer.kt` ;
  - `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUStroke.kt` ;
  - `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/PreparedTextFrameInventory.kt` ;
  - `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/PreparedVerticesFrameInventory.kt`.
- Modifier `kanvas/build.gradle.kts`.
- Modifier `integration-tests/skia/build.gradle.kts`.
- Créer `refactor/waves/W02-scene-ir/status.md`.
- Modifier `refactor/README.md`.

**Étape 1 — Neutraliser `RenderConfig`**

Créer le type public Kanvas :

```kotlin
enum class PreparedImageRoute {
    GENERIC_NATIVE,
    BOUNDED_NEAREST_1_TO_1,
}
```

Remplacer les constantes tirées de `GPUPathEdgeFanPayloadContract` par des constantes publiques neutres dans `RenderConfig` ou un `RenderLimits` du package `surface`. Mapper `PreparedImageRoute` vers `GPUPreparedImageRouteCapability` uniquement dans l'adapter `surface.gpu`. Les valeurs par défaut et les noms de propriétés système restent inchangés.

**Étape 2 — Fermer la dépendance publique**

Dans `kanvas/build.gradle.kts` :

```kotlin
dependencies {
    api(project(":render-ir"))
    implementation(project(":gpu-renderer"))
}
```

Internaliser les déclarations des 17 fichiers listés ci-dessus lorsqu'elles ne font pas partie de `Canvas`, `Surface`, `Picture`, diagnostics ou résultats publics. Ajouter `implementation(project(":gpu-renderer"))` directement à `integration-tests/skia`, car son harness importe volontairement `GPUBackendRuntimeFactory`. Ne créer aucun test qui inspecte les imports ou les sources.

**Étape 3 — Vérifier par le compilateur et les tests comportementaux**

```bash
rtk ./gradlew :math:geometry:jvmTest :math:geometry:jsNodeTest :math:matrix:jvmTest :math:matrix:jsNodeTest :render-ir:test :kanvas:test :integration-tests:skia:test --tests '*SkiaGmInventoryTest*' --tests '*SkiaGmConformanceTest*' --tests '*SkiaGmSceneCaptureTest*' :integration-tests:gpu-evidence:compileKotlin
rtk ./gradlew :integration-tests:skia:generateSkiaGmInventory
rtk jq -e '.scoreAudit.strict == true and ([.rows[] | select((.conformanceScope == "eligible" or .conformanceScope == "accepted-skia-gap") and .setupState == "NOT_ATTEMPTED")] | length) == 0' reports/gpu-renderer/evidence/gm-inventory/source-inventory.json
```

Résultat attendu : builds verts, inventaire strict, aucune GM de la population de conformance laissée non tentée dans l'inventaire final W02. Un échec de rendu reste une vérité W00/W02 et n'est pas masqué ; seule la capture IR doit être à 100 %.

**Étape 4 — Écrire le statut W02 et committer**

`refactor/waves/W02-scene-ir/status.md` contient :

- l'identité du commit ;
- les modules et dépendances finales ;
- le résultat de capture des 100 % GMs éligibles ;
- les comptes W0 inchangés ou la justification de toute reclassification font/codec ;
- la preuve que `Surface.render()` utilise toujours exclusivement le legacy ;
- les commandes de vérification ;
- la gate W02 cochée sans prétendre que les pixels sont déjà produits par la nouvelle architecture.

Mettre à jour `refactor/README.md`, puis :

```bash
rtk git add kanvas integration-tests/skia/build.gradle.kts reports/gpu-renderer/evidence/gm-inventory/source-inventory.json refactor/waves/W02-scene-ir/status.md refactor/README.md
rtk git commit -m "refactor: close W02 scene IR boundaries"
rtk git status --short
```

Résultat attendu : commit créé et worktree propre.

## Annexe A — Sources GM à classer `excluded-codec` en W0

Le catalogue codec est alimenté par les GMs enregistrées provenant exactement de ces sources. Lorsqu'un fichier déclare plusieurs variantes enregistrées, chaque nom produit est inclus. Une GM peut revenir à `eligible` dans une vague ultérieure uniquement après remplacement de son décodage/encodage par une fixture RGBA en mémoire et revue de la décision W00.

### Composite

- `integration-tests/skia/src/test/kotlin/org/graphiks/kanvas/skia/gm/composite/ClipShaderDifferenceGm.kt`
- `integration-tests/skia/src/test/kotlin/org/graphiks/kanvas/skia/gm/composite/ClipShaderLayerGm.kt`
- `integration-tests/skia/src/test/kotlin/org/graphiks/kanvas/skia/gm/composite/ClipShaderPerspGm.kt`
- `integration-tests/skia/src/test/kotlin/org/graphiks/kanvas/skia/gm/composite/ClipShaderSimpleGm.kt`
- `integration-tests/skia/src/test/kotlin/org/graphiks/kanvas/skia/gm/composite/DestcolorGm.kt`
- `integration-tests/skia/src/test/kotlin/org/graphiks/kanvas/skia/gm/composite/DuckyYuvBlendGm.kt`
- `integration-tests/skia/src/test/kotlin/org/graphiks/kanvas/skia/gm/composite/EncodeGm.kt`
- `integration-tests/skia/src/test/kotlin/org/graphiks/kanvas/skia/gm/composite/HSLColorFilterGm.kt`
- `integration-tests/skia/src/test/kotlin/org/graphiks/kanvas/skia/gm/composite/HslGm.kt`
- `integration-tests/skia/src/test/kotlin/org/graphiks/kanvas/skia/gm/composite/ImageFilterComposedTransformGm.kt`
- `integration-tests/skia/src/test/kotlin/org/graphiks/kanvas/skia/gm/composite/ImageFilterConvolveSubsetGm.kt`
- `integration-tests/skia/src/test/kotlin/org/graphiks/kanvas/skia/gm/composite/ImageFiltersEffectOrderGm.kt`
- `integration-tests/skia/src/test/kotlin/org/graphiks/kanvas/skia/gm/composite/ImageFiltersTransformedGm.kt`
- `integration-tests/skia/src/test/kotlin/org/graphiks/kanvas/skia/gm/composite/PatchImageGm.kt`
- `integration-tests/skia/src/test/kotlin/org/graphiks/kanvas/skia/gm/composite/PatchImagePerspGm.kt`
- `integration-tests/skia/src/test/kotlin/org/graphiks/kanvas/skia/gm/composite/SaveLayerGm.kt`

### Image

- `integration-tests/skia/src/test/kotlin/org/graphiks/kanvas/skia/gm/image/AllBitmapConfigsGm.kt`
- `integration-tests/skia/src/test/kotlin/org/graphiks/kanvas/skia/gm/image/AnimCodecPlayerExifGm.kt`
- `integration-tests/skia/src/test/kotlin/org/graphiks/kanvas/skia/gm/image/AnimatedGifGm.kt`
- `integration-tests/skia/src/test/kotlin/org/graphiks/kanvas/skia/gm/image/BitmapImageGm.kt`
- `integration-tests/skia/src/test/kotlin/org/graphiks/kanvas/skia/gm/image/BitmapSubsetShaderGm.kt`
- `integration-tests/skia/src/test/kotlin/org/graphiks/kanvas/skia/gm/image/ColorWheelAlphaTypesGm.kt`
- `integration-tests/skia/src/test/kotlin/org/graphiks/kanvas/skia/gm/image/ColorWheelGm.kt`
- `integration-tests/skia/src/test/kotlin/org/graphiks/kanvas/skia/gm/image/CompositorQuadsImageGm.kt`
- `integration-tests/skia/src/test/kotlin/org/graphiks/kanvas/skia/gm/image/CoordClampShaderGm.kt`
- `integration-tests/skia/src/test/kotlin/org/graphiks/kanvas/skia/gm/image/CopyTo4444Gm.kt`
- `integration-tests/skia/src/test/kotlin/org/graphiks/kanvas/skia/gm/image/DrawBitmapRectSkbug4734Gm.kt`
- `integration-tests/skia/src/test/kotlin/org/graphiks/kanvas/skia/gm/image/EncodeAlphaJpegGm.kt`
- `integration-tests/skia/src/test/kotlin/org/graphiks/kanvas/skia/gm/image/EncodeColorTypesGm.kt`
- `integration-tests/skia/src/test/kotlin/org/graphiks/kanvas/skia/gm/image/EncodePlatformGm.kt`
- `integration-tests/skia/src/test/kotlin/org/graphiks/kanvas/skia/gm/image/EncodeSrgbGm.kt`
- `integration-tests/skia/src/test/kotlin/org/graphiks/kanvas/skia/gm/image/FilterIndiaBoxGm.kt`
- `integration-tests/skia/src/test/kotlin/org/graphiks/kanvas/skia/gm/image/GrayscaleJpgGm.kt`
- `integration-tests/skia/src/test/kotlin/org/graphiks/kanvas/skia/gm/image/ImageFilterTransformedImageGm.kt`
- `integration-tests/skia/src/test/kotlin/org/graphiks/kanvas/skia/gm/image/ImageMakeWithFilterGm.kt`
- `integration-tests/skia/src/test/kotlin/org/graphiks/kanvas/skia/gm/image/ImageshaderTinyscaleGm.kt`
- `integration-tests/skia/src/test/kotlin/org/graphiks/kanvas/skia/gm/image/LocalMatrixImageShaderFilteringGm.kt`
- `integration-tests/skia/src/test/kotlin/org/graphiks/kanvas/skia/gm/image/LocalMatrixShaderPerspGm.kt`
- `integration-tests/skia/src/test/kotlin/org/graphiks/kanvas/skia/gm/image/LocalMatrixShaderRTGm.kt`
- `integration-tests/skia/src/test/kotlin/org/graphiks/kanvas/skia/gm/image/LocalmatrixOrderGm.kt`
- `integration-tests/skia/src/test/kotlin/org/graphiks/kanvas/skia/gm/image/MakeColorSpaceGm.kt`
- `integration-tests/skia/src/test/kotlin/org/graphiks/kanvas/skia/gm/image/MakeRasterImageGm.kt`
- `integration-tests/skia/src/test/kotlin/org/graphiks/kanvas/skia/gm/image/PerspImagesGm.kt`
- `integration-tests/skia/src/test/kotlin/org/graphiks/kanvas/skia/gm/image/ReadPixelsCodecGm.kt`
- `integration-tests/skia/src/test/kotlin/org/graphiks/kanvas/skia/gm/image/ReinterpretColorSpaceGm.kt`
- `integration-tests/skia/src/test/kotlin/org/graphiks/kanvas/skia/gm/image/RepeatedBitmapGm.kt`
- `integration-tests/skia/src/test/kotlin/org/graphiks/kanvas/skia/gm/image/RepeatedBitmapJpgGm.kt`
- `integration-tests/skia/src/test/kotlin/org/graphiks/kanvas/skia/gm/image/ShowMipLevelsGm.kt`

### Mesh

- `integration-tests/skia/src/test/kotlin/org/graphiks/kanvas/skia/gm/mesh/MeshWithEffectsGm.kt`
- `integration-tests/skia/src/test/kotlin/org/graphiks/kanvas/skia/gm/mesh/MeshWithImageGm.kt`
- `integration-tests/skia/src/test/kotlin/org/graphiks/kanvas/skia/gm/mesh/MeshWithPaintColorGm.kt`
- `integration-tests/skia/src/test/kotlin/org/graphiks/kanvas/skia/gm/mesh/MeshWithPaintImageGm.kt`

## Définition de fin W0–W2

Le plan est terminé uniquement lorsque :

- le registre contient 631 GMs instanciables et aucun provider inconnu ;
- l'inventaire v3 est strict, sans score orphelin et sans exclusion `blocking` ;
- chaque GM possède exactement une classification font/codec/eligible/accepted gap ;
- les objets géométriques canoniques et leurs opérations résident dans `:math` ;
- une mutation externe ne change jamais une opération enregistrée ou une Picture ;
- le writer Picture écrit v8 avec IDs stables et le reader lit encore v7 ;
- `:render-ir` ne dépend ni de `:kanvas`, ni de `:gpu-renderer` ;
- toutes les GMs éligibles produisent une `SceneSnapshot` ;
- `:kanvas` dépend de `:gpu-renderer` en `implementation`, sans type GPU dans son API publique ;
- les tests sont exclusivement comportementaux ;
- le rendu public reste legacy jusqu'au plan W3.
