# W7 — assemblage AA inverse et hairline

## Objectif et mandat

Le mandat W7 autonome autorise les changements architecturaux nécessaires à la convergence Skia, pas une modification du contrat des GMs ou de leurs oracles. Ce sous-lot répare deux frontières mesurées autour du hairline (stroke de largeur nulle, un pixel en coordonnées device). Il ne promet pas encore le rendu filtré du GM337.

## Cause et choix

Six probes publics reproduisant le vrai GmCanvas ont exécuté 2 PASS / 4 FAIL sur le snapshot c4794751b41138df9f2e7df6687b62a952674b3c. Le cercle inverse et le hairline isolés rendent nativement. Leur assemblage simple refuse dans la construction W4d utilisée par W4e. Les cellules filtrées et le GM complet refusent d'abord le hairline RECT/ANTIALIASED non filtré, dans le propriétaire W6b.

Astra a confirmé deux défauts distincts de contrat source :
- W4e projette le Rect/STROKE en Path/STROKE. W4d capture inutilement le matériau uni comme une source composée pending, car sa normalisation AA résolue dépend d'un flag d'admission Rect. La construction composée reste limitée à 1×. Le refus exécuté est le garde anyAa de constructSources, pas le garde successor de construct.
- W6 lie sa source existante de Rect/STROKE AA à une scène mixte avec gradient. Le hairline non filtré d'une scène déjà possédée par W6b ne peut donc pas sélectionner cette source, bien que le constructeur root accepte cette occurrence non filtrée.

Choix A d'Astra : séparer la normalisation du matériau uni de l'admission géométrique Rect dans les seams W4e ; puis réutiliser étroitement la source root Rect/STROKE AA existante dans W6. Ne pas construire un nouveau tessellator, changer les samples ou généraliser les sources inverse/Picture pour masquer le problème.

## Contrat A1 — assemblage W4e simple

Un frame AA entièrement composé de solides SrcOver, sans shader, resource, operationBlendMode, blender, colorFilter, maskFilter, imageFilter, pathEffect ni EffectStack, peut employer la normalisation résolue déjà utilisée par W4d standalone. Cette permission est interne et indépendante de l'admission de Rect : les deux seams ordinaires W4e peuvent l'activer pour leur projection de construction authentique. La prévalidation géométrique, la provenance, la force-AA, les fingerprints, le clip/inverse graph et les guards des sources composées restent inchangés. Aucun flag Picture/source-only n'est ouvert.

Le vrai plain-cell GM337 doit rendre proprement avec native Render+Readback, dispatch positif, zéro refus, diagnostics vides et deuxième frame identique. Le hairline garde Paint/STROKE, width0 et AA ; la préparation math continue à construire sa largeur dans le device space.

Oracle visible indépendant : Surface16×10 LINEAR, fond opaque Blue HARD, inverse-AA Red d'un rectangle trou(1,1)-(14,9), hairline White avec Paint(style=STROKE,color=White) sans largeur ni AA modifiés. Variante identité : rect(2.5,2.5)-(7.5,7.5), pixels blancs aux bandes x2/x7 ou y2/y7 dans la boîte x2..7,y2..7. Variante transformée : translate(2,1) puis scale(3,1), rect local(1.5,1.5)-(3.5,3.5), bandes device x6/x12 ou y2/y4 dans x6..12,y2..4. Tous les autres pixels du trou sont Blue et hors trou Red. Les endpoints à demi-pixel rendent ces bandes opaques : aucune tolérance AA inventée. Le test vérifie chaque pixel et les deux frames.

Un contrôle de force-AA issue du clip, avec hairline HARD et clip path AA, qualifie aussi le seam forceAaFrame : ses pixels loin des bords restent ceux de la scène, sa native exécution ne refuse pas. Le matériau ne doit pas gagner de permission si le frame possède une source composée ou des effets : les anciens tests de refus et de blending restent les contrôles, sans fausse négative inventée.

## Addendum mesuré A1 — hard inverse sous clip AA

Le premier run source92862008 rend les deux tableaux hairline et le GmCanvas simple correctement, mais le contrôle force-AA HARDinverse produit Blue(2,4) au lieu de Red. La trace du payload/encodeur/shader confirme la branche HardEdgeBinaryColorCover : binding0 est le masque fini du chemin, binding1 le masque du clip, consumer.inverse signale l'inverse du chemin. BinaryConsumer calcule actuellement rawPath × (1 − rawClip), au lieu de (1 − rawPath) × rawClip. Au sample extérieur au trou/intérieur au clip, cela donne exactement0 au lieu de1. Le test reste positif et ses assertions ne sont pas supprimées.

Correction bornée à GPUW4eMaterialGeometryRecipeV1.BinaryConsumer : appliquer select(rawPath,1−rawPath,inverse) au masque du chemin, multiplier ensuite le masque de clip clampé sans le compléter. Aucun binding, uniform, ressource, format, blend, phase, sample, scissor, producer/consumer authority ni autre recette shader n'est changé. Le bit pour inverse intérieur Zero reste gouverné par le payload existant, inchangé. Rerun obligatoire des contrôles clip/inverse/Picture lors de qualification. Le défaut est traité dans A1 avant son gate Sol, pas masqué par un oracle plus faible.

## Contrat A2 — source root W6 non filtrée

Dans une scène déjà admise/possédée par W6, un root occurrence Rect/STROKE AA peut sélectionner le constructeur w6RootAaRectStrokeSource existant à partir des faits originaux du draw. Il doit satisfaire le prédicat fermé existant : solide SrcOver, MITER, transformée finie axis-aligned non nulle, effets/clip ordinaires, et aucun filtre direct. Un autre sibling filtré n'invalide pas ce contrat. Ne pas élargir ownsMixedRootAaRectFrame, enlever CompositionAdmission, ou autoriser la même source à partir des faits après stripW6bPayload.

Test indépendant : Surface128×96 LINEAR, fond opaque Blue HARD, path triangle AA Black de sommets(16,16),(80,16),(16,80) avec NORMAL mask blur sigma1.5 (fixture existante), puis un hairline visible unfiltered root. Le hairline est translaté de(96,0) avant chacune des deux variantes A1 : sa boîte locale16×10 est donc spatialement séparée du halo filtré. Vérifier tous les pixels x96..111,y0..9 contre les mêmes bandes littérales White/Blue, le plateau Black(24,24) et le contrôle Blue(90,90), plus le repeat natif. Une véritable occurrence Rect/STROKE avec ImageFilter.Blur(1,1) reste refusée selon son contrat existant, avec sentinel0x5a inchangé, discard puis récupération native opaque Blue du même Surface. Aucune admission de scopes layer/Picture par cette extension. Pour cette qualification, l'extension de sélection est bornée au propriétaire ownsW6b en plus de l'ancien ownsMixedRootAaRect ; les autres propriétaires W6 sans filtre ne gagnent pas un contrat non testé.

## Frontière restante et sortie du lot

Après A2, les six probes exacts sont rerun sans changer leurs assertions. Un nouveau premier refus est un progrès de routage, pas un GM rendu. Le garde W4e constructSources AA hors contrat inverseAaCoverageSourceMode est maintenant mesuré sur les3probes filtrés/GM337 au candidat6c6d ; aucune suppression ni réutilisation du contrat Picture n'est autorisée ici. Le corpus exact2e5419afd mesure aussi GM338 : premier refus d'un PATH/WINDING AA ordinaire root sousW6b, sans filtre direct. Le prochain design ciblé doit distinguer cette source AA originale, la source inverse et la demande de halo versus clip terminal ; une source inverse seule ne promet pas338. La publication de ce sous-lot peut être utile avec GM337 encore refusé, mais doit l'indiquer explicitement, ne pas committer de tests positifs toujours RED et ne pas annoncer de gain corpus non mesuré. Le corpus frais mesure5admissions indépendantes et aucun ancien rendu changé, sans résolution du gap337/338.

## Contraintes globales

Fonts, codecs externes et jpg-color-cube hors périmètre. Aucun test d'infrastructure, mock, source-text, forwarding, skip GPU, CPU fallback, fake Picture ou GM-name routing. Ne pas changer epsilon, CompositionEnvelope, oracle tolerance, budgets, caps, registry631/eligible443, scopes/domains, références ou exclusions. Géométrie/numeric dans math et nomenclature I/F32/64. Tous les suivis durables dans refactor. Controller seul pour runtime serial borné240s, custody/evidence/docs/Git/publication ; workers produit/tests seulement et aucun helper/subagent/commit/runtime. Pas de merge ni suppression de custody.

## Revue

Diagnostic et choix relus par Astra dans astra-hairline-strategy-report.md ; deux tâches séparées avec gates Sol spec+quality, puis qualification native/corpus et broad review Astra du sous-lot. Mandat carte blanche : pas de nouvelle boucle d'approbation humaine ordinaire.
