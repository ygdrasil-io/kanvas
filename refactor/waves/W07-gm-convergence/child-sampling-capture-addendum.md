# Child image recording snapshot — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development. Steps use checkbox syntax for tracking. Contrôleur seul pour GPU et Git.

**Goal:** Restaurer la capture de `child_sampling_rt` sans perdre son producteur encodé ni assouplir les garanties natives.
**Architecture:** Réutiliser les références externes sans pixels déjà prévues par `SceneRecordingScope`, avec une identité logique versionnée qui distingue le domaine du producteur. Ajouter une opération de snapshot complet explicitement propre; conserver les deux opérations ordinaires. Aucun nouveau renderer ni résolveur.
**Tech Stack:** Kotlin, Surface, Image, IR de scène, Picture, GPU natif, JUnit.
**Spec:** Contrat ci-dessous; avis architectural Astra ciblé, puis arbitrage du contrôleur sous carte blanche W7.

## Contrat

Un snapshot recording-only désigne une image externe non résolue. Il ne possède pas de pixels, ne retient pas la scène enfant pour son replay autonome et ne promet pas la propreté d'un rendu non exécuté. Sa capture valide la scène comme auparavant; `Surface.render` reste interdit dans ce scope.

Son `sourceId` opaque suit `scene-recording:v2:<compositionDomain.name>:<sceneCanonicalId>:<selection>`, pour LINEAR et SRGB_ENCODED. La sélection reste `full` ou `subset:sx,sy,sw,sh` après la normalisation entière actuelle. Format, dimensions, couleur et alpha restent les métadonnées externes existantes. Ce domaine qualifie la provenance du snapshot, pas le `SceneSnapshot` lui-même. Pas de parsing de cet identifiant dans les consommateurs.

Les références encodées sont SRGB/PREMUL/SOURCE_SPACE, sans pixels. La représentation LINEAR sans pixels reste une référence historique non résolue: aucune affirmation nouvelle sur les octets futurs. Les identités distinctes doivent survivre à une capture avec deux enfants runtime et au round-trip Picture. Un consommateur natif sans résolveur doit toujours refuser la ressource.

`Surface.makeCleanImageSnapshot(): Image` possède deux branches internes: dans le scope, référence externe valide; hors scope, un seul rendu, zéro diagnostic, zéro refus, dimensions/format attendus, puis conversion conservant couleur et prémultiplication authentiques. Aucun overload strict subset sans consommateur. Les deux `makeImageSnapshot` ordinaires conservent leur sémantique native: un diagnostic de routage destination-read peut accompagner des pixels valides. `render`, `toImage`, les seuils et les capacités GPU ne changent pas.

Le GM appelle inconditionnellement l'opération stricte. Son producteur reste transparent 100×100 SRGB_ENCODED, ligne rouge AA STROKE largeur1 (0,0)→(100,100). Aucun branchement de scope dans le GM, faux pixel, fallback CPU ou cache préalable GPU.

## Global Constraints

- Fonts, codecs externes et jpg-color-cube hors périmètre.
- Objets géométriques dans math; nomenclature I/F32/64; réutiliser les types existants.
- Aucun test d'infrastructure/source-text/mock/forwarding ni GPU skip.
- Un runtime natif à la fois; audit complet terminal/log/exit/inventaire/événements/XML/stdout/stderr/JSONL avant toute action suivante.
- Références, seuils, budgets, MSAA, corpus historique et plafond SetupBlocked11 inchangés.
- Aucun merge, force-push, cleanup ou clôture W7; tous les reçus et diagnostics protégés conservés.

## Review Focus

1. Même scène dans deux domaines: deux identités externes, sans déduplication après archive.
2. Snapshot détaché d'une mutation ultérieure, subsets normalisés distincts, format RGBA/BGRA conservé.
3. Diagnostic destination-read réussi: snapshot ordinaire valide, snapshot propre rejeté.
4. Ressource externe non résolue: refus natif, pas de pixels substitués; récupération après discard.
5. Capture réelle du GM et pixels encodés: retour à captured, contrôles littéraux et replay complet inchangés.

### Task 1 extension: capture compatible et snapshot propre

**Files:** Surface.kt, SceneRecordingScope.kt (documentation seulement), ChildSamplingRTGm.kt; SceneRecordingScopeTest.kt, W7SurfaceCompositionPixelTest.kt, SkiaGmSceneCaptureTest.kt, W7ChildSamplingPortSurfacePixelTest.kt. Chemins complets dans le brief privé du contrôleur.
**Interfaces:** Consumes les APIs existantes de snapshot/capture, ResourceSceneAdapter, Picture.toByteArray/fromByteArray. Produces `Surface.makeCleanImageSnapshot(): Image`; identité externe versionnée, pas de champ IR ni format archive nouveau.

- [x] Ajouter d'abord les tests des APIs existantes: capture positive du vrai GM; full/subset recording dans les deux domaines, stabilité/détachement/métadonnées; distinction des ressources runtime après capture et round-trip Picture. Remplacer le contrat négatif encodé par des assertions positives concrètes, pas par suppression du test.
- [x] Le contrôleur exécute le RED de ces tests via les sélecteurs existants, borne240. Les échecs attendus concernent le refus encodé et le GM non capturé; les suites doivent compiler. Audit complet avant reprise.
- [x] Ajouter ensuite les tests de l'API stricte non encore définie: vraie destination-read DARKEN avec pixels rouges opaques indépendants, diagnostics présents sans refus; snapshots ordinaires full/subset réussis; snapshot propre rejeté. Refus d'une image externe, discard puis snapshot propre rouge valide; conservation des pixels/métadonnées SOURCE_SPACE du producteur encodé.
- [x] Le contrôleur constate le RED de compilation pour API absente; zéro test exécuté doit être déclaré, sans l'appeler preuve pixel/native. Les obligations de capture ont leur RED exécutable séparé.
- [x] Implémenter uniquement le contrat Surface; modifier le GM vers l'opération stricte. Garder tous les oracles natifs existants et les cinq tests de scope.
- [ ] Exécuter les classes ciblées et les témoins de composition existants, puis l'audit de capture exhaustif inchangé. D7 a qualifié le port natif/historique (4 PASS); D8 a confirmé 443/431/11/1 sans toucher aux plafonds. D9 a donné 32 PASS et le seul échec point connu avant pixels; la branche stricte de rejet d'un résultat diagnostic-bearing réussi reste non exercée, donc cette étape de compatibilité n'est pas close.
- [x] Requalifier le cas77 sur le nouveau commit source; comparer aux octets/résultats681, régénérer uniquement PNG/score cibles si requis et mettre le suivi à jour. D10 est byte-identique à 681; aucune régénération n'était nécessaire et aucun agrégat global n'a été rafraîchi.
- [x] Revue Sol indépendante reçue : `C0 / I1 / M2`, `Needs fixes`. Après correction M1, un checkpoint draft explicitement incomplet est défendable `With fixes`; la décision de publication reste au contrôleur. I1 bloque la complétion et le merge. L'écart d'intensité, la provenance exacte de la référence et l'état global RED restent ouverts.

Self-review: contrat couvert par les étapes; une seule opération stricte complète, aucune politique globale ni nouvelle géométrie. Les tests distinguent identité, archivage, refus et pixels observables. Les deux RED ne sont pas confondus; le second constate une API absente et ne qualifie aucun rendu.

## Prérequis W7 distinct : host préparé du point carré

Le témoin public `DrawPoint(16,16)`, largeur32, extrémité SQUARE, DARKEN, échoue avant les pixels sur l'égalité paquets matériau/templates géométriques scellés. La récupération après refus d'une vraie référence recording-only passe. Aucun résultat non propre exploitable n'a été obtenu : le rejet des diagnostics par l'API stricte n'est pas encore qualifié. L'avis ciblé Astra recommande de conserver le point et les oracles, sans essayer d'autres producteurs.

- [x] Enrichir le refus existant avec les identités manquantes/excédentaires, commande, autorité structurelle/ABI, mapping et présence de WGSL géométrique pur. Préserver l'invariant, le code et la sévérité; aucune allocation native ou nouvelle API.
- [x] Réexécuter uniquement ce même témoin public et auditer intégralement le reçu pour identifier le propriétaire défaillant. Le reçu trouve le vrai `path-stencil-cover` DARKEN manquant, ABI W5b nulle et WGSL géométrique indisponible; le refus reste RED avant pixels.
- [x] Écrire avant correctif le contrôle natif bleu opaque suivi du même point rouge DARKEN : noir opaque littéral, deux frames identiques. Il doit distinguer le véritable blend d'un SRC_OVER substitué. Le test dédié existe; son run D6 échoue au même host preflight avant pixels, donc le contrôle noir/replay reste non observé. DARKEN opaque est idempotent: ce contrôle seul ne prouve pas l'absence de double application.
- [ ] Corriger le raccordement de l'autorité/recette géométrique à son propriétaire authentique, puis valider ces pixels, les diagnostics réels, les snapshots ordinaires et le refus strict. Ne pas retirer un consommateur réel, fabriquer un template, composer le blend deux fois ou remplacer le renderer.
- [ ] Si une nouvelle famille shader, une ABI ou une extension de matérialisation est nécessaire, écrire un design distinct avant de l'implémenter. Revue Sol indépendante du lot complet avant publication; aucune qualification implicite de W7/global.

Décision au checkpoint `df89f3b4581c146c5e24ec12623c38a5d9d58800`: l'ancien blocage recording-only est corrigé par les références externes versionnées/domain-qualified et l'API opt-in de snapshot natif propre; les API ordinaires et le plafond de capture sont inchangés. D7/D8 sont qualifiés, mais D9 garde le point RED avant pixels et la preuve de rejet strict d'un `RenderResult` diagnostic-bearing non propre reste absente. La revue Sol conclut `C0 / I1 / M2`, `Needs fixes`; après correction M1, un checkpoint draft explicitement incomplet est défendable `With fixes`, sous réserve que I1 et les RED restent visibles. I1 bloque la complétion et le merge, et la publication effective demeure à la décision du contrôleur. L'ownership W5b du vrai color cover (source, version/snapshot destination et layout) est un lot distinct différé; ni patch de sealer seul ni retrait du consommateur n'est accepté. Les quatre dettes `SurfaceSceneSnapshotTest` restent visibles comme résultats historiques de la première exécution à 45 tests, non rerun dans D9. Pas de suppression/skip/oracle assoupli, merge, GREEN global, complétion du plan ou clôture W7.
