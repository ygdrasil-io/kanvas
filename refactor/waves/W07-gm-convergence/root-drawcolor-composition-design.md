# W7 — composition root ordonnée avec DrawColor (amendement Task2)

## Intention et preuve

But : conserver les vrais pixels GPU des familles root déjà rendables après la correction sémantique de GmCanvas.drawColor, sans adapter les références ou tolérances. Le user délègue les décisions locales W7 (« carte blanche ») et demande Subagent-Driven Development ; cet amendement est une décision contrôleur, pas une approbation humaine fictive d'un document nouveau.

La mesure sourcebc9c a perdu22 rendus face au corpus historiquebf38. Le falsifier92097dd, renderer fixe/adaptateur seul changé, récupère104/121/524 et exactement leurs hashes historiques. Les18 champs scene/reference/domain/opcount sont identiques. L'adaptateur corrigé est rétabli en160becfbd125b77606a4e0731cfbf17ae6c5e1c9. Le reçu controller-task2-adapter-falsifier-1-receipt.md et la stratégie Astra bb8afa709ac644b3d37438662684ea82bb43fe0fb404beab6ce8d5866a8a08f7 sont les preuves. Trois récupérations ne prouvent ni les19 autres ni la parité :104min0 passe mais32.3% reste loin de la référence ;524min80 échoue65.57%.

## Choix architectural

Introduire un propriétaire fermé de composition root LINEAR ordonnée, après les propriétaires whole-frame qui fonctionnent déjà. Il assemble les vraies lanes DrawColor authentifiées W3 et les spans géométriques maximaux entre couleurs, sélectionnés par les autorités existantes. Préférer réutiliser l'enveloppe ordonnée W6 et sa publication typée ; ne pas inventer un deuxième packer ou budget. L'ancienne provenance W3/W4e/W5a est une inférence statique forte, non une télémétrie observée ; les barrières DrawColor et le passage GapNotMigrated vers le prepared renderer sont établis. L'exception interne104 reste inconnue, et ses symptômes prepared ne seront pas réparés par supposition.

Alternatives écartées pour la solution générale : revenir à l'AA Rect (sémantique fausse) ; ajouter DrawColor séparément à chaque compiler (duplication et coupling). Le premier lot W3 décrit plus bas est une réparation bornée distincte, non cette généralisation. Une ouverture encoded→LINEAR du seul booléen W6 n'est pas suffisante : dispatch child général différent de standaloneRectPathFrames, réutilisation du clip et contrôles agrégés à préserver. Le compilateur root initial reste un objectif de design suspendu, pas une implémentation autorisée après le refus de sécurité.

## Contrat du propriétaire

Admettre seulement un root LINEAR sans layer/Picture/image/spatial-filter nouveaux, contenant au moins un DrawColor SrcOver et un Draw géométrique déjà admis. Metadata finite transform/clip/annotation restent capturées et validées. Les owners W6 layer/filter/deferred existants gardent leur priorité, ainsi que tous les owners ordinaires réussis. Les scènes sans ce mélange gardent leurs identités.

Chaque lane couleur conserve LegacyColorV1 sans dummy source ou table inventée. Chaque span conserve son compiler et ses preuves de géométrie, material, clip/stencil/destination snapshot ; un singleton n'est pas autorisé à contourner une autorité whole-frame. L'AA Rect stroke de la famille existante mixed-root utilise son autorité W6 fermée, pas un élargissement de W4d général. Ordre, canonical scene identity et indices originaux sont gelés, avec remapping authentifié des indices locaux.

La décision est complète avant publication : si aucun plan de span n'est admis, NotCandidate peut garder la frontière de compatibilité historique avant ownership ; les InvalidScene/MaterialOnlyRefusal/ResourceLimitExceeded restent terminales, aucun essai de source concurrent. Un root promu ou Ready ne retombe jamais dans un renderer. Aucun rendu partiel. Cette règle n'ajoute aucun fallback.

Validation whole-scene et bornes agrégées geometry/work/graph/caps/budget ne se réinitialisent pas par span. Partager un inventaire frame et le vrai packing permit. Conserver load/store continu, producteurs clip/stencil, destination copies, formats/samples, lifetime/epochs/quotas/cache ; une seule soumission finale et un seul readback. Les validateurs typés ne sont pas affaiblis pour faire passer un graphe arbitraire.

## Contrat DrawColor CTM distinct

En LINEAR, DrawColor remplit target∩clip déjà capturé indépendamment de toute CTM finie ultérieure. Supprimer seulement le veto finite-nonidentity de cette opération et le veto de provenance quand le frame ne contient que des couleurs ; conserver les refus nonfinite et les contraintes des vrais Draw géométriques. En encoded, `SetTransform` admet les translations intégrales mais `DrawColor` exige une CTM identité : le contrôle positif emploie une translation intégrale puis son inverse avant la couleur, sans ouvrir la politique de CompositionAdmissionV1 ou les filtres/geometry encoded. Une couleur sous CTM encoded nonidentité ne devient pas admise dans ce lot.

## Preuves comportementales

Qualification RED du 4 octobre : les trois témoins CTM LINEAR donnent déjà les pixels natifs attendus via la continuation prepared existante. Ils deviennent des contrôles inchangés, pas la preuve d'un défaut utilisateur W3. Ne pas modifier W3/provenance par anticipation ; si l'authentification d'une nouvelle composition root avec couleur transformée exige cette extension, établir d'abord un témoin public mixte géométrie/couleur RED et consigner la dépendance. Les quatre RED effectivement observés sont ring AA, Rect filtré, hairline clipped et paths DARKEN.

Une nouvelle fixture publique Surface32×32, oracles complets calculés avant Surface, RGB±2/alpha exact, rendus répétés byte-identiques, diagnostics clean/zéro refus/Render+Readback/submit1/readback1/QueueSubmitted avant CompletionSucceeded. Réutiliser les patterns d'observation existants vers w7.ordinaryAaEvidenceDir et retenir les vrais buffers ; pas de nouveau harnais ni test de structure/forwarding. Les compteurs retention0 restent une limite de snapshot, pas un faux settled receipt.

Cas : clip-fill CTM et alpha ; gradient bleu constant, DrawColor vert et ring rouge [2.5,2.5,5.5,5.5] width1 dans les trois ordres ; Rect integer ColorFilter.Matrix identité ; hairline hard rect [8.5,8.5,23.5,23.5] width0 et clip path hard intersect/difference ; deux hard paths DARKEN de rectangles [4,4,20,20] et [12,12,28,28] sur fond blanc. Oracles de géométrie indépendante, aucune attente obtenue du renderer ou graphe.

Négatif : AA Rect stroke Shader.SolidColor de la famille mixed-root déjà refusée, sentinel readPixels inchangée puis recovery propre. Conserver le prefix historique unsupported.stroke.rect_anti_alias. Ne pas prétendre disposer de compteurs de soumission d'un résultat qui n'est pas retourné : prouver la frontière terminale disponible et noter explicitement toute absence de télémétrie native négative.

## Premier lot borné après le refus de sécurité — W3 mixte

Le patch général root avec scènes Annotation projetées et bindings singleton a été refusé ; aucun correctif produit n'est conservé. Le diagnostic Astra d2166a43a81da7ab82ae7ef613ec11d3935dfb9bbfe26b507790563c9d6cbad0 établit une alternative distincte : reconnaître et construire le mélange sur la scène originale, dans W3, avec un nouveau contrat mixte fermé producteur/consommateur. Ce choix de séquençage relève de la délégation locale W7, pas d'une autorisation Astra ni d'un contournement du patch refusé.

Sous-domaine uniquement LINEAR : au moins un DrawColor réellement admis par W3 et un véritable Rect matériel admis par W3 ; toutes les opérations visuelles SrcOver, bounds/CTM/clip/metadata authentiques conformes à W3, aucune destination copy. Conserver les 512 commandes totales W3 et les contrôles caps/budget/empreintes whole-frame. Hors de ce sous-domaine (encoded, matériau refusé, autre geometry/blend/clip), conserver les frontières précédentes. Les scènes pure LegacyColor et pure matérielles réussies gardent leurs identités et leurs branches de validation.

La nouvelle capacité explicite `W3SolidRectPlanCompiler.W7_MIXED_COLOR_MATERIAL_CAPABILITY_ID` vaut `w7.w3.root-drawcolor-material.v1`. Son candidat est issu du seul parcours de la scène originale, et sa publication utilise une table réelle non nulle avec au moins une autorité LegacyColorV1 et une autorité matérielle W3. Les couleurs restent sans ref/source artificielle. L'association des occurrences matérielles à leurs vrais indices est dense avant interning, indépendante des ordinals des couleurs ; no-op et sources pending/résolues ne doivent pas la décaler. Source construction, fingerprints, remapping, graphe et packing permit existants restent authentifiés. Un discriminant explicite n'est pas une permission de publier un graphe arbitraire : le lowerer vérifie la forme W3 exacte et le mélange réel.

Le consommateur garde INCHANGÉES les branches historiques CAPABILITY_ID et W5A_CAPABILITY_ID ; ajoute uniquement la branche du nouveau contrat, exigeant SolidRectDraw/FullOrScissor/SingleSample/SrcOver, les deux autorités réellement présentes, la table, toutes les refs/coordonnées/packed proofs et les mêmes ressources/peak/budget/scratch. Aucune validation native générique, shader, executor ou packer relâché. Aucun nouveau public constructor/debug API.

Périmètre produit : W3SolidRectPlanCompiler.kt et GpuPlanTaskListLowerer.kt seulement. Nouvelle fixture publique W7W3DrawColorMaterialSurfacePixelTest.kt ; les fixtures gelées restent intactes. Les consommateurs FrameSourceLayoutV4/SourceDeferredRenderConstructionV4/RenderGraphConstruction/RenderGraph sont à vérifier, non à modifier sans dépendance concrète escaladée. Geometry math I/F32/64 inchangé.

Gain visé uniquement : integerFilteredRect et son mélange W3. Pas de garantie préalable de GM104 complet. Les trois RED hairline/DARKEN/ring et le design général restent ouverts ; aucun nouveau compilateur root/projection/W6/singleton n'est autorisé dans ce lot. Corpus443 et disposition22pertes restent nécessaires avant finalisation Task2.

## Qualification et invariants

RED natif fixture avant product patch ; GREEN fixture puis controls inchangés W7MixedRootAaRectSurfacePixelTest (B29408/B−1), W7CommonAaPathSourceSurfacePixelTest (width1/2), W7ColorFilterDestinationDomainSurfacePixelTest (9methods), GmCanvasSurfacePixelTest, srgbLINEAR control. Réexécuter104/121/524 et111, puis corpus existant631/443 complet avant migration/finalization, en comparant bc9c et parentbf38 distinct. Rendre compte de chaque22 perte, gain66 et hashes/refusals modifiés ; ne pas cacher les échecs29gpu-plan+39renderer déjà consignés.

Binding : global-constraints.md et design destination-domain restent applicables sauf extension production explicite ci-dessus. DéfautLINEAR, coeffs/refPNG/scores/tol/exclusions/timeout30/outer240 inchangés. Pas de font/externalcodec/jpg, CPU/mock/fake/GPU skip/budgetrelaxation, shaderfix spéculatif ou export public. Geometry dans math, I/F32/64. Archives/SDD/protected4/Task1fixture conservés.
