# État W05 — material graph, final blends et adressage des gradients W5d

W5d est close au niveau fonctionnel sur `codex/w5d-gradient-addressing`, empilée sur W5c : Linear/Radial/Sweep/Conical SRGB, CLAMP/REPEAT/MIRROR/DECAL, `WithLocalMatrix`, `CoordClamp`, coordonnées ordonnées et moyenne dégénérée sont promus sur Rect, RRect analytique, Path fill et Path stroke. Task 7 et son correctif de revue ferment la frame mixte et le budget des allocations matérielles uniques par preuves publiques. Le 12 septembre 2026, la sélection conjointe W5d/W5c/W5b/W5a compte 162 méthodes, 160 passées, deux skips AA4 et aucune failure/error XML. Gradle reste en échec après les assertions, exit 1, avec worker natif exit 133; ce n'est pas un succès de commande.

## Clôture W5d Task 7 — agrégats et preuves publiques

Les cinq méthodes ajoutées sont `mixedFramePreservesOrderAcrossFamiliesTilesWrappersLanesAndBlends`, `coordinateUniformBudgetRefusesPreciselyAndRecovers`, `mixedCoordinateTopologiesRemainSemanticallyDistinct`, `capturedSubsetsAndStopsIgnorePostRecordMutation` et `authenticAa4OrPreciseSkip`. La frame mixte superpose Linear REPEAT Rect → Radial MIRROR RRect → Sweep DECAL Path fill → Conical CLAMP Path stroke, avec matrices locales, clamps, `Opacity(0.5)` et `DIFFERENCE`. Les témoins de chaque lane, la transparence au-dessus du seam Sweep et l'ordre inverse sont vérifiés par pixels; le pixel composé utilise l'enveloppe indépendante W5b existante. Aucun program key privé n'est inspecté. Deux topologies clamp/matrix coexistent dans une frame rouge/bleue, rendue trois fois. Une `Picture` reste rouge après mutation du subset et des stops appelants.

Le workload de budget final contient 64 lanes Rect/RRect alternées : 32 gradients à deux stops et 20 paires matrix/clamp, puis 32 Solid identiques. Tous les contrôles utilisent la même Surface 11×1. Il rend sous 4 MiB, refuse à `RenderConfig.frameLocalBudgetBytes = 1_590_000` avec `resource.material.gradient.coordinate-uniform-budget`, puis récupère sur le même runtime; le cycle est répété. Au même budget, le workload sans wrappers et celui répétant la même source wrapped rendent exactement les mêmes pixels rouges. Les petits seuils historiques 65 536/49 700 étaient perturbés par le staging déjà résident dans l'ordre de suite : le ruling a imposé une fenêtre causale au-dessus de ce high-water public, sans modification de pool, reset ou harness. Les extents distincts évitent seulement le gap target-ID différé.

Deux témoins publics adjacents ferment la revue : `coordinateBudgetPreservesNonUniformOwnerDiagnostic` garde `resource-limit.w5b.destination-budget` à 49 152 quand le non-uniforme ne tient déjà plus ; `w5aOnlyCompositeBudgetsUniqueSourcesAndRecovers` force 64 lanes Solid/Opacity SRC_OVER, admet la source identique à 1 574 000, refuse les valeurs distinctes avec `w5a.composite.unsupported` et récupère à 4 MiB. Le RED final contre `ff114d` avait trois failures précises : surcompte de la source répétée, mauvais propriétaire et refus W5a-only tardif au submit.

`RawMaterialRequirementsV2` centralise tailles, packing, identité de valeur, additions/multiplications I64 contrôlées, conversions U32/Int et capacités réelles. Le stage natif consomme exactement ses octets et son identité canonique ; les agrégats dédupliquent donc comme le materializer existant, pas par draw ni par domaine de preuve géométrique. Ils vérifient d'abord non-uniformes + sources non-V2 uniques avec le diagnostic propriétaire, puis l'ajout V2 unique : le refus W5d est causal. Le composite W5a-only ferme son inventaire après interning : target/readback une fois, géométrie par lane, slab de stops une fois sans surcompter `GradientStopData`, uniformes uniques une fois. Son lowerer garde la même distinction. Les sources restent à offset zéro non dynamique ; aucun nouveau chemin de rendu ni owner n'est introduit. Les raccords supplémentaires Raw/stage/coordonnées V1 internes/lowerer ont été explicités dans le rapport de correction.

L'audit structure/valeurs confirme : famille, requested/effective tile, version du tile graph, présence de moyenne et séquence de tags coordonnées restent structurels; stops/ranges, tuples famille, opacity, bits matrices/subsets et moyenne restent dans les seals de valeurs. Raw authentifie de nouveau le plan et sa plage avant packing; un source-stage V2 incohérent refuse avec `schema.material.gradient.coordinate-plan` avant allocation native. La preuve de ces frontières internes reste une lecture de production; les tests observent exclusivement les API publiques.

Les limites physiques viennent de `device.limits`, transportées par `GPUCapabilities.toPlanCapabilitySnapshot`; aucune valeur ni capability AA4 de test n'est ajoutée. Le résultat réel AA4 est `w4d.general.texture-sample-support-unavailable: W4d.2 four-sample color support is unavailable`; le test vérifie ce code et la récupération avant son skip. La branche AA4 positive avec pixels exacts reste non exercée sur cet adapter.

L'ownership réutilise W5c sans nouveau handle, buffer ou cache W5d : `GPUW5aSourceOwnedHandlesV2` détient uniformes, layouts, pipelines et bind groups; `materializeGradientStopsV1` alloue/upload un seul stop buffer, emprunté par les groupes de la frame. Le payload transfère l'ensemble avec `PayloadOwnedCompletion`; l'exception de materialization conserve le draft dans le rollback existant. Après submit/completion, les fermetures réussies sont retirées et les fermetures incertaines restent en quarantine pour reprise. L'ABI du stop buffer reste deux vec4, 32 bytes par stop. Les renders répétés et la récupération après refus sont publics; aucune panne d'allocation/device loss ni lifetime de handle n'est simulée. L'ownership terminal ne reprend pas de continuation legacy.

La régression forcée utilise :

```sh
rtk ./gradlew :kanvas:test --tests '*W5dGradientAddressingSurfacePixelTest*' --tests '*W5cGradientSurfacePixelTest*' --tests '*W5bBlendSurfacePixelTest*' --tests '*W5aMaterialSurfacePixelTest*' --no-parallel --rerun-tasks
```

| Classe | Méthodes | Passées | Failures | Errors | Skips | Timestamp XML UTC, 12 septembre 2026 |
| --- | ---: | ---: | ---: | ---: | ---: | --- |
| W5aMaterialSurfacePixelTest | 48 | 47 | 0 | 0 | 1 | 14:31:02.075Z |
| W5bBlendSurfacePixelTest | 50 | 50 | 0 | 0 | 0 | 14:31:13.587Z |
| W5cGradientSurfacePixelTest | 27 | 27 | 0 | 0 | 0 | 14:31:38.403Z |
| W5dGradientAddressingSurfacePixelTest | 37 | 36 | 0 | 0 | 1 | 14:32:22.244Z |
| Total | 162 | 160 | 0 | 0 | 2 | — |

Le RED des workloads finaux est XML 14:28:02.215Z, trois failures contre la base ; le narrow GREEN est XML 14:29:09.075Z, trois passés sans skip/failure/error. La sélection conjointe ci-dessus termine en 3m50s avec `Gradle Test Executor 79` exit 133 et Gradle exit 1 (`BUILD FAILED`). Les essais intermédiaires en échec restent détaillés dans le rapport ; aucune suite isolée n'a été substituée à cette preuve conjointe. Aucun contournement native-access/Unsafe, reset du runtime ou masquage de crash n'est ajouté.

La compilation séparée `rtk ./gradlew :gpu-plan:compileKotlin :gpu-renderer:compileKotlin :kanvas:compileKotlin --no-parallel` termine `BUILD SUCCESSFUL`, exit 0. `rtk git diff --check` est propre. Cette auto-review Task 7 ne remplace pas une revue globale indépendante de la stack.

Réserves maintenues : collision d'IDs de cible equal-extent entre composite W5a et session W5b sur le même runtime; intervalle Conical B-cross-zero conservateur pouvant donner un faux `DomainUnbounded`; absence de preuve AA4 positive et de panne native injectée. Le finding adjacent Minor Task 4, `GradientTileOperationNodeV2.ClampF32` public jamais émis/rejeté, reste différé sans aggravation dans Task 7. W5e images, W5f non-SRGB/filters, W5g et W5h/H restent ouverts. Aucun GM/dashboard/régénération/Skia/font/codec/baseline/`jpg-color-cube` n'a été exécuté; seuls ce document et `refactor/README.md` portent le suivi durable.

## Historique W5c

W5c est close au niveau fonctionnel sur `codex/w5c-gradients`, empilée sur W5b : les quatre gradients CLAMP/SRGB sans local matrix sont promus sur Rect, RRect analytique, Path fill et Path stroke. La clôture Task 7 est complétée par le correctif Task 8 `d5b9307a0`, qui scelle le tuple F32 Linear et ajoute un pixel public biaxial discriminant. Sa vérification finale du 12 septembre 2026 comptait 125 méthodes, 124 passées, un skip AA4 et aucune failure/error XML, avec Gradle exit 1 et crash natif post-assertions exit 133. Les sections suivantes conservent cet historique antérieur à W5d.

## Frontière et preuves publiques W5c

| Source CLAMP/SRGB | Lanes promues et preuves |
| --- | --- |
| Linear | Rect intégrale/fractionnaire, RRect analytique, Path fill direct/stencil et stroke/hairline; coordonnées locales, endpoints implicites, hard stops, 1/2/16/17 stops, mutation après Picture; tuple F32 scellé et pixel biaxial sensible à la fusion/réassociation |
| Radial | Les quatre lanes; rayon nul/petit/négatif, singleton, interpolation et composition Opacity/blend |
| Sweep | Les quatre lanes; angles écran clockwise, spans partiels/étendus, limites et dégénérescences, hard stops et Opacity/blend |
| Conical | Les quatre lanes; plus grande racine valide, masque des fragments sans racine, singleton conservant le masque, branches linéaire/concentrique/dégénérée et Opacity/blend |

`mixedGradientFramePreservesOrderRangesOpacityAndBlend` intercale Linear Rect → Radial RRect analytique avec `Opacity(0.5)` et `DIFFERENCE` → Sweep Path fill → Conical Path stroke. Linear/Sweep réutilisent une séquence exacte; Radial/Conical partagent une seconde séquence distincte de 17 stops. Chacun des quatre draws intermédiaires possède un pixel non recouvert, vérifié dans les deux ordres. Trois recouvrements entre voisins ont chacun un contre-exemple d'ordre inversé, avec attentes indépendantes et couleurs différentes. Les draws initial/final ont aussi leurs témoins propres. Aucune assertion de range, buffer, compteur, scope ou détail privé ne remplace ces pixels.

`gradientFrameBudgetRefusesThenRuntimeRecovers` rend un gradient de 257 stops sous un budget public de 1 MiB, reçoit `resource.material.gradient.stop-budget` à `RenderConfig.frameLocalBudgetBytes = 4096`, puis rend immédiatement les mêmes pixels bleus exacts sur la Surface valide, sans interrompre le runtime/backend. La Surface refusée est distincte : son recording est append-only et sa configuration immuable, sans opération publique pour retirer la frame refusée ou changer le budget.

`authenticStorageCapabilityEitherRendersOrRefusesTyped` ne consulte l'adapter de production qu'à travers `Surface.render()`. Son budget logiciel explicite de 1 MiB suffit à sa petite frame de 17 stops. Le résultat authentique observé est `exact pixels rendered`, sans skip. La branche conditionnelle accepte seulement `unsupported.material.gradient.storage-capability` pour feature/limite/bindings indisponibles, ou `resource.material.gradient.stop-budget` pour une taille physique de buffer/binding insuffisante. Cette branche de refus n'a pas été exercée sur cet adapter; aucune capability n'a été injectée.

## Ressources et ownership W5c

L'audit de production confirme l'interning ordonné par première occurrence, la déduplication des séquences normalisées exactes, les plages réécrites et les autorités numériques rebasées dans la table de frame. Les clés structurelles des programmes excluent toujours données et nombre de stops. Les bindings/ranges, le contenu canonique du slab, les versions de programme/ABI et les coordonnées/provenances scellées restent portés par les autorités de frame existantes.

Le comptage brut précède les copies de recording/capture. Les bornes des séquences normalisées et des plages U32, les tailles hôte, les additions/multiplications de bytes, les tailles physique et de binding, les nombres de bindings et le budget combiné sont contrôlés avant readiness. Le buffer storage de frame est alloué/uploadé une fois par le source materializer, emprunté par les consommateurs et retenu jusqu'à leur completion. Les handles restent dans les journals existants de fermeture/rollback/quarantaine. Ces invariants sont une inspection de production, pas des tests d'infrastructure ni une simulation de panne native.

Après admission, les quatre familles et les quatre lanes consomment le material scellé; la soumission propriétaire n'a pas de continuation legacy. Aucun chemin promu supplémentaire à supprimer n'a été trouvé dans Task 7. Les classes de gradients historiques restent nécessaires avant admission pour les combinaisons W5d/W5f/W5h; les chemins image/font/codec n'ont pas été modifiés.

## Historique W5c Task 7 — avant le correctif Linear

Avant toute modification de production, la commande exacte Step 2 a finalement passé 3/3 méthodes sur la base `27427d799`. Les deux premières tentatives avaient 2 passées et une expectation `Unbounded` dans le fixture mixte : opacité fixed-function, puis soustraction destination-read près de zéro. Ces échecs de domaine d'oracle ne sont pas des RED de production. Le fixture retenu conserve l'opacité et un blend destination-read, sans changer l'oracle ni élargir sa borne singleton/deux codes adjacents. La production reste inchangée dans Task 7.

```sh
rtk ./gradlew :kanvas:test --tests '*W5cGradientSurfacePixelTest.mixedGradientFrame*' --tests '*W5cGradientSurfacePixelTest.gradientFrameBudget*' --tests '*W5cGradientSurfacePixelTest.authenticStorageCapability*' --no-parallel --rerun-tasks
```

## Correctif et reviews W5c Task 8

La review globale Sol initiale était `NOT READY` : Linear recalculait `dx`, `dy` et `length²` dans le shader alors que W5 §7.2 exige leur tuple F32 preflight scellé. Le commit `d5b9307a0` — `fix(gpu): consume sealed W5c linear tuple` — capture, authentifie et sérialise `linearDx`, `linearDy`, `linearX2`, `linearY2`, `linearLen2`, `linearLength` et `linearDegenerate`. WGSL consomme directement les valeurs nécessaires. L'identifiant structurel Linear porte `uniform-v2-numeric-v2`; le buffer uniforme existant contient les deux vecteurs supplémentaires, sans nouveau binding ni second buffer.

`linearBiaxialHardStopConsumesRoundedPreflightLength` a fait RED sur la production intacte : bleu attendu, rouge rendu à cause de la fusion/réassociation du calcul de `length²`. L'attente est dérivée d'opérations Kotlin Float explicites et d'une sélection locale des stops, sans factory Linear de production ni élargissement de tolérance. Le témoin passe après le correctif. La re-review du finding le marque `addressed`; la review distincte du lifecycle des ressources est `READY`. Ces conclusions restent distinctes du résultat de la commande Gradle.

## Vérification finale W5c Task 8 — 12 septembre 2026

Le controller a exécuté les commandes suivantes après le correctif :

```sh
rtk ./gradlew :render-ir:compileKotlin :gpu-plan:compileKotlin :gpu-renderer:compileKotlin :kanvas:compileKotlin --no-parallel
rtk ./gradlew :kanvas:test --tests '*W5cGradientSurfacePixelTest*' --tests '*W5bBlendSurfacePixelTest*' --tests '*W5aMaterialSurfacePixelTest*' --no-parallel --rerun-tasks
rtk git diff --check
```

Compilation : `BUILD SUCCESSFUL`, exit 0. La sélection publique forcée produit les XML frais suivants; tous les timestamps sont en UTC le 12 septembre 2026 :

| Classe | Méthodes | Passées | Failures | Errors | Skips | Timestamp XML |
| --- | ---: | ---: | ---: | ---: | ---: | --- |
| W5cGradientSurfacePixelTest | 27 | 27 | 0 | 0 | 0 | 00:51:35.732Z |
| W5bBlendSurfacePixelTest | 50 | 50 | 0 | 0 | 0 | 00:51:10.841Z |
| W5aMaterialSurfacePixelTest | 48 | 47 | 0 | 0 | 1 | 00:50:58.809Z |
| Total | 125 | 124 | 0 | 0 | 1 | — |

La capability W5c authentique rend `exact pixels rendered`.

Le seul skip AA4 est `public mixed AA4 frame keeps a hard Path binary cover materialized only at color output`, refus authentique `w4d.general.texture-sample-support-unavailable`. Les autres skips AA4 de l'historique W3/W4 ci-dessous ne font pas partie de cette sélection.

Après toutes les assertions, `Gradle Test Executor 263` sort avec **133** et Gradle avec **1** (`BUILD FAILED`). C'est la caveat native/AppKit déjà suivie; les résultats XML n'en font pas un succès de commande. Aucun contournement du harness, reset de runtime ou masquage du crash n'a été ajouté. Les warnings JVM native-access/Unsafe et CoreAnalytics historiques restent distincts des résultats des méthodes.

## Limites et suite après W5c

- Le gap d'intégration connu entre les IDs de cible du composite historique W5a et de la session W5b, avec deux Surfaces de même extent sur le même runtime/backend, reste différé. Il n'est pas nécessaire de le corriger pour exprimer honnêtement la gate mixte destination-read; aucune garantie générale sur cette transition de routes n'est ajoutée.
- L'oracle Conical reste conservateur lorsque l'intervalle de B traverse zéro : il peut produire un faux `DomainUnbounded`, jamais un faux `Bounded`. Aucune tolérance élargie ni prétention de conformance exhaustive.
- AA4 et les capabilities physiques absentes ne sont jamais simulés; allocation failure, device loss et récupération après panne native ne sont pas prouvés par injection.
- À cette étape historique, W5d était la prochaine stack; sa clôture REPEAT/MIRROR/DECAL, `WithLocalMatrix` et `CoordClamp` est désormais décrite en tête. W5f conserve LINEAR/OKLAB/HSL/OKLCH et les filters, W5h les gradients Point(s)/Text/Vertices/Mesh et autres cellules H. W5e images, W5g blend-children/noise et les autres runtime effects W5h restent planifiés.
- Aucune suite GM/dashboard/render-regeneration/Skia/font/codec/`jpg-color-cube` n'a été exécutée. Les modules peuvent compiler transitivement. Seuls ce status et `refactor/README.md` reçoivent la documentation durable; les rapports d'agents restent dans le workspace SDD ignoré.

## Historique W5b — référence antérieure à W5c

W5a Solid/Opacity est close sur son périmètre. W5b, son nettoyage Task 7 et sa boucle de reviews Task 8 ont été implémentés et vérifiés sur `codex/w5b-blends`, empilée sur `codex/w5a-solid-opacity`. Les deux reviews Sol indépendantes W5b sont `READY`; la PR empilée W5b est `#2396`. Les sections historiques suivantes décrivent cette clôture avant W5c.

## Périmètre public promu W5b

Le `BlendPlan` scellé est l'autorité finale après admission. `FinalBlendPlanner` classe dans `:gpu-plan`; `W5bBlendPlanLowerer` traduit ce plan sans relire le paint public. La source reste RGBA linéaire prémultipliée et le résultat reste `D + coverage × (blend(S,D) − D)`. `DST` est un `NoOp`; `PLUS` est fixed-function sur couverture full/scissor 1× avec clamp authentifié, et destination-read sur couverture scalaire. Les copies GPU portent target, device generation et `DestinationVersionI64`, sans réutilisation après une écriture intermédiaire, avec bounds conservateurs et row pitch/budget contrôlés. Aucune lecture CPU du target ne participe au blend.

| Famille promue | Preuve publique conservée |
| --- | --- |
| Rect intégrale, fractional Rect, RRect analytique | fixed-function, `DST`, destination-read avec alpha non trivial et couverture analytique originale |
| Path fill direct et stencil-cover | mêmes trois classes de blend, ordre et mutation du Path après capture `Picture`; producteurs stencil sans material/final blend effectif |
| Path stroke et hairline, transforms généraux hard | géométrie W4 inchangée, mêmes classes de blend et capture mutable; AA4 reste un refus natif explicite |
| W4e clips complexes, masques scalaires et inverse/D24S8 | consommateurs couleur promus; préfixes stencil/mask conservés, producteur 2×2 et quantification R8 originaux |
| Point/Points | fan/hairline et limite de 64 points conservés; les 45 cellules historiques DrawPoint sont fermées sur trois commandes successives |
| Text A8 déjà résolu | fixture existante `128/255`, fixed-function/`DST`/destination-read, mutation glyphs/positions; aucune génération de font ni promotion des glyphs couleur/LCD |
| Vertices avec/sans couleurs et Mesh sans programme | modulation de la source avant blend final, trois classes de blend, mutation positions/couleurs/indices; `MeshProgram` demeure hors promotion |
| Frame mixte Rect → Point → RRect → Path → A8 → Vertices | chaque famille observée, ordre contradictoire exclu, huit mutations publiques après capture, `DST` élidé; témoins de cible retenue et frames entièrement `DST` transparents |

## Fermeture des 45 cellules DrawPoint

La dette historique est fermée pour exactement `{PLUS, MULTIPLY, OVERLAY, DARKEN, LIGHTEN, COLOR_DODGE, COLOR_BURN, HARD_LIGHT, SOFT_LIGHT, DIFFERENCE, EXCLUSION, HUE, SATURATION, COLOR, LUMINOSITY}` × `{UNCLIPPED, SCISSOR, ALPHA_MASK}`. Chaque cellule garde ses trois draws et son oracle indépendant. Le gate dédié est filtrable sans charger de fixture font/image :

```sh
rtk proxy ./gradlew :kanvas:test --tests '*GPUAllApiBlendSurfaceTest.drawPointHistoricalW5bMatrix' --no-parallel --max-workers=1 --rerun-tasks -q
```

La caractérisation forcée Task 7 avant et après nettoyage, le 11 septembre 2026, a passé 146 méthodes publiques : W5b 45/45, W5a 47 sélectionnées dont 1 skip, W3/W4 `GPUPlanSurfacePixelTest` 53 sélectionnées dont 1 skip, et le gate DrawPoint (1 méthode, 45 cellules). Total : 144 passées, 2 skips, 0 failure/error. Le dernier run complet a terminé à 15:29:51 UTC, exit 0. Le filtre DrawPoint seul, lui aussi forcé, a terminé à 15:33:29 UTC : 1 méthode, 45 cellules, aucun failure/error/skip. Cette fermeture ciblée ne requalifie pas une baseline globale et ne prétend pas avoir rejoué les anciennes suites d'infrastructure.

## Ownership et compatibilité restante

Le routeur conserve uniquement les continuations candidate/capture-limit/`GapNotMigrated` antérieures à l'ownership. La soumission d'un token authentifié n'a plus accès à une continuation legacy. Dans W4e, les trois retours de construction `GapNotMigrated` deviennent `GapOnPromotedScope` après authentification d'un successor W5b ou d'une élision `NoOp`, en conservant exactement le diagnostic. Les color consumers W4c/W4d/General/W4e abaissent leur blend scellé; leur ancien choix booléen/nullable vers `SRC_OVER` est supprimé. Si un source-stage destination-read perd son seal W5b, le materializer refuse et utilise son rollback existant au lieu de produire la source seule.

| Sites de production audités | Ownership et échéance |
| --- | --- |
| `EffectiveMaterialPlanner`, `FinalBlendPlanner`, compilers W3/W4 et bridges Core/A8/Vertices | classification avant `Ready`; un candidat material local ne vaut pas admission géométrique. Les aliases explicites `LegacySrcOverV1` des graphs historiques conservent leurs witnesses |
| `GpuPlanTaskListLowerer`, lowerers W4a–W4e/W5b, witnesses et prepared task-list builder | plans W5b consommés et validés; aucune reclassification après ownership. Les champs `SRC_OVER` des producteurs couleur-disabled et du véritable clear initial ne sont pas des fallbacks de draw |
| `GPUBlendPlanning.GPUBlendPlanner` et projection analytique | compatibilité seulement avant admission ou pour familles non promues. Retrait à leur promotion W5c–W5h; toutes les cellules `H` au plus tard W5h |
| `GPUOpMapper`, `AnalysisContracts` et leurs wrappers `canonicalBlendPlan`/`canonicalPlan` | admission géométrique/recording legacy préalable au seal Core, ou dispatch non promu. Ils ne récupèrent jamais une frame W5b refusée; retrait du chemin promu à son admission, retrait legacy final W8 |
| `GPUPreparedTextLowerer`/`GPUTextA8RoutePlanner`, `GPUPreparedVerticesLowerer` | plan préparé prioritaire et obligatoire sur les produits promus. Branche legacy uniquement sans ownership; primitive vertex blend interne distinct du blend final. Autres matériaux : W5c–W5h/H |
| `GPUPreparedDrawImageLowerer`, Atlas/ImageGrid, image dispatch, glyphs couleur/LCD | hors W5b : images déjà décodées W5e et final-blend Image origin `H` avant W5h; glyphs hors A8 admis gardent leur refus/capability, font toujours exclue |
| `GPUPreparedMaterialProgram`, `BlendWgslBuilder`, runtime-child/filter helpers et formules CPU legacy | blends à l'intérieur de la source, pas le blend final W5b; migrations W5f–W5h. Les dispatchers WGSL legacy à défaut source ne sont pas sélectionnés par la formule W5b validée |
| `GPUIntermediatePlanner`, `compositeBlendPlan`, layer/composite capture, mask/image-filter dispatch | compatibilité layer/spatiale W6, hors promotion W5b. Le défaut composite-label historique n'est jamais une issue d'une frame W5b admise |
| Native pipeline/cache defaults, `GPUW5aSourceStageNativeV2`, preflight/executor catches | plan/ABI et ressources exacts après ownership; défauts historiques seulement hors W5b ou producteurs. Échec natif terminal avec journal de rollback, jamais `SRC_OVER`, transparent ou source-only substitué |

Cet audit est une inspection de production, pas une assertion de source shape. Le comportement étant déjà vert, Task 7 utilise l'exception de caractérisation avant/après autorisée pour les invariants non falsifiables via `Surface`; aucun RED artificiel ni test d'infrastructure n'est ajouté. Les rapports temporaires et le ledger exhaustif restent dans le workspace SDD ignoré. Aucun ancien document durable n'a été supprimé : l'historique W5a ci-dessous conserve ses preuves et ses limites.

## Vérification et limites W5b

Les compilations ciblées sont `:render-ir:compileKotlin`, `:gpu-plan:compileKotlin`, `:gpu-renderer:compileKotlin` et `:kanvas:compileKotlin`, forcées avec `--rerun-tasks --no-parallel --max-workers=1`. La régression publique reprend exactement la sélection Task 6 : toute W5b, les 47 méthodes Surface W5a (sans le test immutable-graph) et les 53 gates W3/W4 publics autorisés, plus GREEN45. Aucun test sur scopes, counters, packets, bindings ou détails internes ne sert de preuve. `:gpu-renderer:compileTestKotlin` a des erreurs historiques de sources de tests périmées et reste exclu des preuves.

Clôture fraîche Task 8 au commit `e470bcee8`, le 11 septembre 2026 : les quatre compilations principales forcées sont vertes de 17:52:43 à 17:53:42 UTC. La régression publique complète forcée est verte de 17:53:51 à 17:56:12 UTC avec 151 méthodes sélectionnées, 149 réussies, 2 skips AA4 authentiques et 0 failure/error. GREEN45 rejoué seul est vert de 17:56:31 à 17:58:19 UTC : une méthode, 45 cellules, aucun failure/error/skip.

Les reviews Task 8 ont fermé sept findings Important : copies destination bornées avec origine non nulle et version `DestinationVersionI64`; branches `COLOR_DODGE`/`COLOR_BURN` sans division singulière évaluée avidement; matérialisation ordonnée de plusieurs runs Vertices/Mesh; décision de clear après culling; indexation linéaire des ressources; cache de pipeline Vertices local à la frame, à ownership unique et clé typée indépendante des valeurs d'uniformes. Les deux re-reviews Sol sont `READY`, sans finding Critical/Important restant.

Deux skips AA4 authentiques dans cette sélection : `public mixed AA4 frame keeps a hard Path binary cover materialized only at color output` avec `w4d.general.texture-sample-support-unavailable`, et `W4e public Path AA4 uses only binary fixtures after its exact native capability boundary` avec `w4e.clip.sample-count-unavailable`. Le troisième skip de la vérification historique W5a ci-dessous n'est pas inclus dans la sélection W5b; aucune réussite ni capability AA4 n'est simulée.

`WgslFloatEnvelopeV1` accepte seulement un singleton ou deux codes RGBA8 adjacents, calculés analytiquement avec destination corrélée. Les fixtures W5a arbitraires 17/18 et 9/16 avec alpha Paint `253/255`, ainsi que les contre-exemples W5b dont les intervalles se chevauchent ou dépassent cette borne, restent `Unbounded` et ne sont pas des gates. Aucun seuil empirique ni garantie universelle sur tous les backends n'en découle.

La preuve de budget utilise un input W5b valide de deux Rects puis `resource-limit.w5b.destination-budget` à 1150 bytes et des pixels de récupération. Le display list public est append-only et `Surface.config` immuable : la récupération utilise des Surfaces distinctes sur le même runtime/backend ininterrompu, puis rejoue la Surface valide. La configuration prepared n'expose ni remplacement de capabilities ni budget agrégé injectables. Ces branches typées, les limites I64, la comptabilité physique pré-allocation et la libération/quarantaine native sont inspectées statiquement; ni device loss ni allocation failure ne sont prouvés par injection. Les ABIs admis utilisent uniforms, textures échantillonnées et samplers; aucun storage buffer inutilisé n'est exigé.

Le warning natif préexistant `Context leak detected, CoreAnalytics returned false` est toujours émis sans failure/error, avec les warnings JVM native-access/Unsafe. Les modules font peuvent se compiler transitivement; aucune suite font/codec/GM/dashboard/render/baseline/Skia/`jpg-color-cube` n'est exécutée. Le target `:kanvas` reste JVM, sans tâche JS/Node authentique. Le gap legacy Rect-gradient + RRect hard-edge `uniform slab` reste reporté. W5c vient ensuite; W5d matrices/tile, W5e images, W5f filters, W5g blend-children/noise et W5h runtime effects/H restent ouverts.

Minors explicitement différés : le seuil `1e-10` de `SetSat` reste partagé par l'implémentation et l'oracle et devra être réévalué avant l'expansion des sources; `GeneralPathDraw.withBlend` conserve un cast de l'autorité material legacy; le test public budget/recovery Task 6 vérifie aussi les pixels du primer avant le checkpoint refusal/recovery prévu par le brief. Aucun de ces points ne bloque les gates publics W5b actuels.

## Historique W5a — référence antérieure à W5b

Révision de production initiale : `7dbaf8cdf672e836f6ec6d77b1734cb68b6669db` (« admit W5a frame materials after geometry validation »), continuation du correctif global `39ff21985bd1407958d3ba1e909a74bbedf50010` sur `e0b1f39ce23a8badbd10074eb308908a26725280`. La vérification de cette vague couvre aussi les commits de recovery `64e6429c`, `582606d7`, `cbd8ab5e`, `33c54c09`, `9891e117` et `9aa924e5c`. Les cinq findings Important, les minors et les résidus d'admission/noms publics des scoped re-reviews sont traités dans cette même vague. Les deux re-reviews globales Task 8 sont désormais `READY`; W5a est close pour son périmètre, sans élargir les gates aux suites hors périmètre.

### Gates publiques W5a

W5a implémente `Transparent`, `Solid` et `Opacity` sous `SRC_OVER`. Chaque draw promu porte une `MaterialV1` vers une table immuable. La frontière W3 différée a été vérifiée en production : la capability historique exige table `null` et uniquement `LegacyColorV1`; `W5A_CAPABILITY_ID` exige une table présente et uniquement `MaterialV1`. Les formes hybrides sont refusées.

| Cellule publique | Preuve retenue |
| --- | --- |
| Rect hard-edge / fractional | fixtures nested-opacity et fractional Rect (Tasks 1–2) |
| RRect analytique | fixture fractional RRect et observation d'une couverture exacte 0.75 dans les frames mixtes |
| Path fill direct / stencil cover | fixtures Path (Task 3), plus chacune des deux variantes de frame mixte |
| Path stroke / hairline | fixtures publiques stroke et hairline (Task 3) |
| Point / Points | trois commandes, points multiples et hairline (Task 4) |
| Text pré-résolu | fixtures A8 et mutation d'une liste de glyphs déjà résolus (Task 5), sans génération de font |
| Vertices, avec/sans couleurs vertex | fixtures Picture et mutation des tableaux publics (Task 6) |
| Prepared frames mixtes | Rect → Point → Rect, Rect → A8 Text → Rect, Rect → Vertices → Rect et public Mesh sans programme : trois sources/opacités distinctes, ordre observé par l'oracle et mutation des collections publiques après capture |
| RRect + stroke/hairline | RRect → Path stroke ou hairline → RRect, géométrie W4d native, mutation du Path après Picture et pixels de composition |
| Bornes publiques | 512 runs natifs rendus avant/après le refus précoce de 513 runs sur une autre Surface du même runtime/backend; 683 matériaux point identiques dédupliqués, 683 chaînes distinctes de trois entrées refusées à la vraie borne 2048 |
| Admission avant interning | 683 Rect hors cible à chaînes distinctes sont élidées avant la table commune et seul le Point valide rend; Rect non finie + Point conserve `unsupported.core_primitive.geometry.non_finite_transform`; 683 candidats Vertices non finis gardent `unsupported.vertices.transform` |
| Frame Rect + RRect + Path | `Picture playback composes planned Rect RRect and Path bindings in recorded order` : trois bindings distincts, composition indépendante, observation AA, puis comparaison de tous les bytes après mutation du Path |
| Ordre intercalé et stencil | `native mixed stencil frame preserves interleaved Rect bindings and captured mutation` : Rect → RRect → Path stencil-cover → Rect, réutilisation d'un binding et comparaison de tous les bytes après mutation |
| Opacité identique, children distincts | `native mixed equal opacity preserves distinct Solid children and nested chains` : Rect rouge et RRect bleu à opacité 0.5, puis Path rouge et Rect bleu avec chaînes shader/Paint imbriquées et children réutilisés non adjacents; quatre observations pixel indépendantes |
| Refus puis récupération du runtime/backend | `public W5b gradient refusal leaves the runtime able to render a later W5a frame` : W5a valide → gradient refusé `unsupported.material.w5a.kind` → W5a valide, sur trois instances Surface partageant le même runtime/backend sans dispose intermédiaire, et pixels avant/après identiques |
| Absence d'ownership W5a | `hard edge gradient RRect outside W5a retains legacy pixels after caller stop mutation` : RRect hard-edge hors admission W4b, gradient rouge/bleu capturé en Picture, mutation des stops vers vert, pixels legacy rouge/bleu conservés |

### Composition native Task 7 W5a

La capability distincte `w5a-native-rect-rrect-path-composite-v1` est sélectionnée après les capabilities standalone existantes. Elle partitionne les commandes en runs natifs ordonnés, conserve leurs indices publics et confie Rect à W3, RRect analytique à W4b, Path fill à W4c et stroke/hairline à W4d. Le choix Path suit aussi les sémantiques de paint et l'admission native, pas la seule classe de géométrie. W4c n'accepte plus de conversion Rect/RRect en Path. La borne de 512 runs est vérifiée immédiatement après l'inventaire linéaire et avant toute `SceneSnapshot` par lane, y compris si une source material ultérieure serait refusée.

`W5aCompositePlanV1` possède les graphs de lanes, la table material internée et les réservations communes. Les refs locales sont remappées exactement dans la table de frame; la copie des draws conserve les faits de géométrie/raster sans reconstruction sémantique. Le graph composite utilise cette représentation hiérarchique typée, sans fabriquer une topologie standalone.

Correction d'interning : la clé d'une entrée inclut son child canonique et donc toute sa chaîne de bindings accessible, pas seulement sa structure et son alpha local. Puisque V1 désigne le child à `ref - 1`, toute nouvelle chaîne dont le child réutilisé n'est pas adjacent est copiée contiguë avant son parent. Les remaps canoniques restent déterministes, les bindings copiés défensivement et la borne de 2048 entrées contrôlée avant chaque ajout. La preuve RED Task 7 a produit du rouge dans le pixel bleu (`channel=0 observed=188 expected=[0]`); elle reste verte avec les chaînes imbriquées et le nouveau fragment.

`W5aPreparedFrameMaterialRegistry` remplace le bridge historiquement nommé CorePoint. Ses candidats Core locaux capturent uniquement le paint, sans acquisition de la table de frame ni admission géométrique implicite. Le mapper/lowerer existant, le recorder, la collecte des sémantiques et le preflight prepared vertices effectuent l'admission réelle : élision hors cible, transform, tessellation, limites et autres refus précèdent l'interning. Tous les `geometryRefusal` du lowerer sont propagés avant le recorder, sans filtre ad hoc; une Rect non finie conserve ainsi son diagnostic géométrique exact au lieu de devenir un refus de capture material ou de contrat de frame.

Seuls les payloads réellement admis Rect/RRect/Path/Points/A8 Text/Vertices et Mesh sans programme rejoignent ensuite la table commune. Text et Vertices sont abaissés une fois; leurs payloads sont remappés uniquement côté material, en authentifiant l'identité exacte du source-stage. Le witness Core vérifie également l'identité de la source locale et de sa ref globale. Aucune géométrie, tessellation, préparation d'atlas, inventaire ou artifact n'est rejoué. Les glyphs couleur/non-A8 et les géométries refusées n'acquièrent pas d'ownership W5a.

Correction de l'ownership des refus : les compilers natifs conservent séparément leurs refus de material et continuent les contrôles de géométrie, couverture, clip, état, provenance et limites sémantiques de toute la scène. Seule leur réussite complète permet d'émettre `GpuPlanSelection.MaterialOnlyRefusal`, lié à la scène, à la cible et à la capability. Les seams W4e et composite propagent ce résultat après leur propre admission; le backend vérifie l'identité scène/cible. Un `GapNotMigrated` ordinaire reste legacy : le routeur ne rescane plus les shaders et ne transforme plus des diagnostics accumulés en ownership. La nouvelle preuve RED échouait sur le faux terminal `unsupported.material.w5a.kind`; elle est GREEN sans modifier la géométrie ni substituer un material.

Le lowerer réutilise chaque lowerer et assembler natif avec un witness composite explicite. Il conserve les enveloppes d'origine pour le preflight exact, puis transporte seulement les ranges scellés vers les indices de la frame. Un witness de frame vérifie cible, readback, préparations, ordre, packets, états raster, dépendances et budget. La frame prépare une seule cible et un seul staging, efface au premier rendu puis charge l'attachement, et conserve les paires stencil atomiques.

Toutes les capacités V/I/U arrondies, y compris le scratch Rect W3 de cette composition, et les leases D24S8 sont comptées avant l'allocation. Un pool de session dédié aux lanes composites utilise la factory native existante avec la borne de 512 lanes; le pool standalone à trois slots reste inchangé. Le materializer réutilise les implémentations natives W3/W4b/W4c/W4d, partage un seul buffer readback, rassemble leurs leases sous un lifecycle commun et retient les journals de rollback si le nettoyage doit être retenté.

Les premières preuves RED ont révélé les anciennes exigences « toute la frame appartient à une seule lane », puis la limite des trois slots et la priorité de sélection devant W4b à 512 draws. Les corrections ajoutent une autorité composite et une gestion propre des ressources; elles ne relâchent pas les enveloppes standalone.

### Audit des compilations alternatives Solid/Opacity W5a

Le renderer génère maintenant le source-stage WGSL directement depuis chaque DAG numérique scellé, dans l'ordre des dépendances : sRGB→linear, prémultiplication et Opacity opèrent sur les bindings bruts en F32. `W5aMaterialPlanEvaluator` a été supprimé. Les anciens slots couleur de géométrie sont neutres, pas une seconde autorité. La queue du DAG est authentifiée à la couverture existante, au blend prémultiplié `SRC_OVER` et à l'attachement sRGB/clamp/UNORM8.

La partition native V2 conserve intégralement le group 0 de chaque lane et ajoute un group 1, binding 0, uniform non dynamique, dans les seuls fragments color-writing. Stencil/mask producers ne reçoivent ni source ni binding material; W4e inverse conserve son préfixe stencil atomique. Les pipelines composés conservent géométrie, constantes, entrypoints, raster, attachment et couverture. Text/Vertices utilisent ce même générateur dans leur composition material authentique, sans overlay core supplémentaire. Aucun compute prépass n'est introduit.

Le seal géométrique historique reste inchangé; une partition material V2 dérivée des packets immuables est ajoutée au budget agrégé avant toute allocation. Les raw buffers identiques sont dédupliqués et vivent jusqu'à completion; layouts/pipelines/bindings et buffers sont dans le journal de rollback commun. Coût exact des fixtures 512 : W4a `164100 + 16 = 164116` bytes (un Solid), W4b `164100 + 2×16 = 164132` bytes (deux Solids). Aucun slack arbitraire et aucun coût masqué; un caller ayant choisi exactement l'ancien budget doit compter ce nouveau matériel.

| Site de production | Décision W5a |
| --- | --- |
| compilers W3/W4a/W4b/W4c/W4d/W4e | `EffectiveMaterialPlanner` produit table/ref sur W5a; l'adaptateur historique conserve seulement `LegacyColorV1` |
| lowerers natifs + source V2 | transport de table/ref, génération du fragment depuis le DAG, bindings bruts; aucune évaluation CPU du résultat |
| composite Task 7 | interning structure + bindings + chaîne child canonique et remap exact, adjacency V1 conservée; aucune nouvelle évaluation du shader ni conversion de géométrie |
| bridges core/text/vertices | interning frame-wide après admissions, remaps d'émissions scellées et `compileW5a*`; les lanes non promues conservent leur entrée générique |
| `GPUMaterialMapper` | Opacity legacy reste un refus `OPACITY_CHILD`; aucun aplatissement Solid après sélection W5a |
| images, glyphs couleur, MeshProgram et dispatchs legacy restants | hors promotion W5a; `DrawMesh` sans programme utilise réellement la route publique vertices et dispose d'une preuve de mutation dédiée |

Cet audit porte sur le code de production. Les assertions W5a ajoutées dans les tests compiler/lowerer, SceneArchiveCodec et DisplayOpSceneAdapter ont été retirées du diff complet de branche; seules des adaptations mécaniques de tests historiques restent, sans servir de preuve W5a et sans exécuter de suite codec/infrastructure. Les preuves conservées sont exclusivement publiques. Si CPU et GPU sont tous deux dans l'enveloppe, aucun RED black-box ne distingue honnêtement l'architecture : la review de production prouve alors DAG→fragment, les tests prouvent pixels/enveloppe/mutation.

L'audit exhaustif des déclarations publiques ajoutées depuis la base empilée inclut les nombres dans les collections, maps, tableaux et types nullables. Les résidus `MaterializedSolidV2.premultipliedRgbaF32` et `issue(refsByCommandIdI32, sourcePlansByCommandIdI32)` sont corrigés; les KDoc décrivent le slot géométrique neutre et le source-stage fragment. Les signatures historiques inchangées et les overrides imposés par Kotlin ne sont pas présentés comme de nouvelles APIs W5a.

### Enveloppe numérique des preuves publiques W5a

Le cas public Rect puis Point à source ARGB `(197, 211, 79, 41)`, opacité shader `0.5` et paint `173/255` a isolé le RED `channel=2 observed=18 expected=[17]`. L'audit de la disposition brute, du binding et de l'expression DAG n'a pas révélé de divergence : l'oracle appliquait à tort la précision du `pow` WGSL à l'attachement fixed-function. La fixture 17/18 a été retirée de la suite car sa borne portable élargie est `Unbounded`; elle reste un exemple documenté, non un gate assoupli.

L'oracle indépendant distingue désormais les deux étapes. La source reste bornée par les opérations F32/WGSL du DAG. La conversion d'attachement utilise la référence sRGB réelle, calculée par arithmétique décimale dirigée et racines rationnelles, puis les bornes officielles : erreur totale d'encodage RGB strictement inférieure à un code et erreur de décodage, mesurée après ré-encodage exact, au plus un demi-code ([Metal, §8.7.7, 4 juin 2026](https://developer.apple.com/metal/Metal-Shading-Language-Specification.pdf)). Cette enveloppe contient l'encodage D3D limité à `0.6` code; l'alpha linéaire conserve aussi la borne FLOAT→UNORM de `0.6` code ([D3D 11.3, §3.2.3.6–8](https://microsoft.github.io/DirectX-Specs/d3d/archive/D3D11_3_FunctionalSpec.htm)). Il ne s'agit pas d'une tolérance ajoutée à un arrondi préalable. Le bleu de la régression admet analytiquement les seuls codes adjacents `{17, 18}`.

La couverture multiplie la source dans le fragment avant les facteurs fixed-function `One`/`InvSrcAlpha`. D3D11.3 §17.5 autorise une précision target-format mais ne fixe ni lattice ni schedule : l'oracle ferme donc le blend sur la grille RGBA8 minimale autorisée et F32, avec facteurs, produits et destination corrélée, plutôt qu'une enveloppe F32 seule. Les formes ordinaires/FMA, les erreurs F32 dirigées sur tout l'intervalle et FTZ restent incluses. Aucune mesure empirique ni seuil de similarité n'entre dans cette dérivation.

La première propagation des bornes officielles a rendu 13 anciennes fixtures multi-draw `Unbounded`. Une recherche déterministe par endpoints et grille fixed-point a retenu seulement des témoins à fond primaire opaque, couche blanche avec opacité/alpha Paint non triviaux, puis primaire opaque : ils préservent l'ordre, la capture et les mutations, tout en satisfaisant la règle stricte du singleton ou de deux codes adjacents. Un contre-exemple public en ordre inversé reste rejeté dans la preuve à trois Points. La combinaison fractionnaire 9/16 avec deux opacités et alpha Paint `253/255` reste explicitement `Unbounded`; le témoin 9/16 retenu garde les deux opacités mais un alpha Paint exact. Les conversions fixed-function peuvent élargir au-delà de deux codes l'enveloppe d'une scène arbitraire; ces fixtures sélectionnées ne prouvent pas une borne universelle. Un tel résultat reste `Unbounded`, jamais un succès assoupli. Aucun pipeline destination-read n'a été ajouté.

### Vérification historique W5a

Vérification JVM du correctif global Task 8 et de sa continuation, fraîche et sérielle, sur la série `7dbaf8c` → `9aa924e5c` :

```bash
rtk proxy ./gradlew :gpu-plan:compileKotlin :gpu-renderer:compileKotlin :kanvas:compileKotlin --no-parallel --max-workers=1
rtk proxy ./gradlew :kanvas:test --tests '*W5aMaterialSurfacePixelTest' --tests '*GPUPlanSurfacePixelTest' --no-parallel --max-workers=1
rtk git diff --check
```

Résultat : les deux commandes Gradle sont `BUILD SUCCESSFUL`; 119 tests publics, 116 passés, 0 failure/error, 3 skips AA4 authentiques. Répartition : W5a 48 tests (47 passés, 1 skip); GPUPlan 71 tests (69 passés, 2 skips), avec les deux fixtures 512, W4e inverse, toutes les frames mixtes et les régressions admission/numérique. Les compteurs ci-dessus sont la source de vérification; les timestamps XML ne sont pas une source documentaire. `rtk git diff --check` est propre.

Skips exacts : `public mixed AA4 frame keeps a hard Path binary cover materialized only at color output` (`w4d.general.texture-sample-support-unavailable`), `W4e public Path AA4 uses only binary fixtures after its exact native capability boundary` et `W4e public mixed hard and Path AA4 inverse consumers keep distinct D24S8 domains` (tous deux `w4e.clip.sample-count-unavailable`).

La commande planifiée `:kanvas:jsNodeTest` est absente : `:kanvas` applique `buildsrc.convention.kotlin-jvm` et l'inventaire Gradle ne publie aucune tâche JS/Node. Aucun substitut de test d'infrastructure n'a été exécuté.

### Limites de la clôture W5a

- Les trois skips AA4 restent attachés à l'indisponibilité native documentée; aucune capability ni réussite AA4 n'est simulée.
- SolidColor, Opacity et Paint sont immuables. La mutation publique observable porte sur Path, tableaux vertices et listes glyphs après capture.
- Les preuves numériques multi-draw concernent les entrées dont l'enveloppe indépendante officielle reste bornée à deux codes adjacents; les autres entrées restent explicitement `Unbounded`, sans affaiblissement de l'assertion ni prétention de conformance exhaustive de tous les backends.
- Les dépendances font se compilent transitivement, mais aucune suite font/codec/GM/dashboard/baseline/Skia/`jpg-color-cube` n'a été exécutée. Les fixtures text utilisent seulement des glyphs déjà résolus.
- Aucun test d'infrastructure n'a servi de preuve. Les deux re-reviews globales Sol indépendantes de Task 8 sont `READY`; la validation reste fondée sur la revue de production et les pixels publics autorisés.
- Le run public final émet aussi `Context leak detected, CoreAnalytics returned false`, sans failure/error ni correspondance dans les sources du repository; warning natif non attribué à un défaut du correctif, conservé explicitement dans le rapport plutôt que présenté comme absent.
- Une variante exploratoire non retenue, gradient Rect suivi de gradient RRect hard-edge, atteint legacy mais y rencontre `invalid.preflight.core_primitive_direct_geometry_resources` (uniform slab). Ce refus de ressources legacy distinct, suivi comme gap non bloquant, reste hors de ce correctif; la preuve retenue concerne la RRect seule demandée.
- Cette clôture W5a précédait W5b; le périmètre actuel W5b et la suite W5c sont décrits en tête de document.
