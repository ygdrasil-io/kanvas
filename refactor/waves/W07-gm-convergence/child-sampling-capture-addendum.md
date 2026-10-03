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
- [x] Exécuter les classes ciblées et les témoins de composition existants, puis l'audit de capture exhaustif inchangé. D7 a qualifié le port natif/historique (4 PASS); D8 a confirmé 443/431/11/1 sans toucher aux plafonds. D9 a donné 32 PASS et un échec point historique avant pixels; F6 a ensuite qualifié les 52 tests ciblés (52 PASS), notamment le rejet strict, et F7 les 8 tests d'intégration (8 PASS) avec la même partition 443/431/11/1. F8 a reproduit la mesure ciblée au source `73fc10a`. Ces reçus ferment l'exécution ciblée, pas la revue indépendante ni le statut global.
- [x] Requalifier le cas77 sur le nouveau commit source; comparer aux octets/résultats681, régénérer uniquement PNG/score cibles si requis et mettre le suivi à jour. D10 est byte-identique à 681; aucune régénération n'était nécessaire et aucun agrégat global n'a été rafraîchi.
- [x] La revue Sol originale (avant F6) a signalé I1; la revue de correction round1 a conclu `C0 / nouvel I2 Important / M0 nouveau`, M2 hérité, et marqué I1 `ADDRESSED`. Elle permettait un checkpoint draft incomplet `With fixes` après M1. La PR #2441 est ouverte en draft. F12C qualifie maintenant I2 par 54/54 PASS, y compris les refus typed d’initialiseur et le contrat de refus Clear. Une revue indépendante round2 du checkpoint tests `0d456121` et des documents mis à jour reste attendue. Aucune approbation de tâche ou merge n’est revendiquée; provenance de référence, delta d’intensité et état global restent ouverts.

Self-review: contrat couvert par les étapes; une seule opération stricte complète, aucune politique globale ni nouvelle géométrie. Les tests distinguent identité, archivage, refus et pixels observables. Les deux RED ne sont pas confondus; le second constate une API absente et ne qualifie aucun rendu.

## Prérequis W7 distinct : host préparé du point carré

Les RED D5/D6 avant le correctif sont historiques : le témoin public `DrawPoint(16,16)`, largeur32, extrémité SQUARE, DARKEN échouait alors avant les pixels sur l’égalité paquets/templates géométriques scellés. F6 a qualifié le point carré borné et son témoin bleu/rouge/bleu/replay; F12C a ensuite confirmé les refus typed d’initialiseur et la préservation du Clear public. La récupération après refus d’une vraie référence recording-only passe. Ces résultats ne donnent pas d’ownership général au stencil conservé.

- [x] Enrichir le refus existant avec les identités manquantes/excédentaires, commande, autorité structurelle/ABI, mapping et présence de WGSL géométrique pur. Préserver l'invariant, le code et la sévérité; aucune allocation native ou nouvelle API.
- [x] Réexécuter uniquement ce même témoin public et auditer intégralement le reçu pour identifier le propriétaire défaillant. Le reçu trouve le vrai `path-stencil-cover` DARKEN manquant, ABI W5b nulle et WGSL géométrique indisponible; le refus reste RED avant pixels.
- [x] Écrire avant correctif le contrôle natif bleu opaque suivi du même point rouge DARKEN : noir opaque littéral, deux frames identiques. Il doit distinguer le véritable blend d'un SRC_OVER substitué. Le test dédié existe; son run D6 échoue au même host preflight avant pixels, donc le contrôle noir/replay reste non observé. DARKEN opaque est idempotent: ce contrôle seul ne prouve pas l'absence de double application.
- [x] Corriger le raccordement de l'autorité/recette géométrique à son propriétaire authentique, puis valider ces pixels, les diagnostics réels, les snapshots ordinaires et le refus strict. F6 qualifie le domaine borné du point carré et ses témoins pixels/replay/snapshot; les témoins D5/D6 restent les reçus RED antérieurs. Cela ne qualifie pas l'ownership général du stencil conservé. Aucun consommateur réel n'a été retiré, aucun template fabriqué, et le renderer n'a pas été remplacé.
- [ ] Si une nouvelle famille shader, une ABI ou une extension de matérialisation est nécessaire, écrire un design distinct avant de l'implémenter. Revue Sol indépendante du lot complet avant publication; aucune qualification implicite de W7/global.

Décision actuelle : la correction recording-only et le snapshot propre opt-in restent qualifiés; APIs ordinaires et plafond de capture inchangés. F6 (52/52), F7 (8/8), F8 (parité case77 identique) et F12C (54/54) sont des résultats ciblés, pas un GREEN global. Le point carré est limité au domaine documenté. `Clear(Blue)` avant le point DARKEN est actuellement refusé avec `invalid.w5b.prepared-points` et ses opérations restent intactes; l’ajout d’une source-material authority pour Clear serait une extension distincte, non qualifiée. Les tests typed refusent les claims d’initialiseur/mismatch de capture avec les diagnostics observés; ils ne prouvent pas séparément clip/scissor ni l’ordre isolé. La revue originale avait I1; la revue de correction round1 l’a marqué `ADDRESSED` et signalé un nouvel I2 Important (M2 hérité), maintenant qualifié par F12C. La revue indépendante round2 reste en attente. La PR #2441 reste un draft; le commit source `73fc10a` et les tests `0d456121` ne sont pas un statut d’approbation, merge ou complétion de tâche. Les quatre dettes `SurfaceSceneSnapshotTest`, les 11 captures bloquées et l’invalide, warnings, exclusions, révision de référence inconnue, écart d’intensité non attribué et ownership stencil général restent visibles.
