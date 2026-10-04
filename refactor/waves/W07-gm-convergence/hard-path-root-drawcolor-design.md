# W7 — tranche whole-frame DrawColor + paths DARKEN

## Intention et autorité

Restaurer la composition GPU réelle de DrawColor et des hard path fills (remplissages de chemins sans antialiasing) DARKEN, sans changer références, tolérances ou politique de scores. Le user délègue les décisions et changements locaux W7 ; ce choix est une décision contrôleur, pas une approbation humaine fictive ni une permission Astra. Exécution Subagent-Driven Development, Sol pour les reviews, Astra utilisé ponctuellement pour le diagnostic architectural.

Le lot W3 à 91cff4f24c4474c3ed8b6cdfc41f39a4e88410c4 est qualifié : six méthodes natives, 26 buffers complets, B37184/B37183 et mixed512/513 ; filteredRect récupéré, GM104 rendable à 32,3025 %, GM111 exact100 inchangé. Root5/8 laisse DARKEN, ringAA et hairline/clip en échec. Renderer471=352PASS119FAIL reproduit exactement sans le patch W3, gpu-plan276=247PASS29FAIL inchangé ; ces modules ne sont pas GREEN. Le rapport Astra post-w3-next-lot-astra-report.md est un diagnostic source, non une mesure de la proposition.

Ce sous-lot appartient à Task2 destination-domain et remplace uniquement sa proposition de généralisation immédiate. Le compilateur root à SceneSnapshot projetés/Annotation/singleton, précédemment refusé, reste absent et interdit. Toute publication/push demeure hors de ce travail local.

## Choix

Choisir une tranche typée fermée derrière le select W4c existant, sur la scène originale. Extraire les producteurs DrawColor W3 et préparation W4c, puis publier leurs faits communs via le sealer W5b. Pas de nouveau compilateur root, changement de chain, reselection child ou préparation par intervalle.

Une extension locale par liste de couleurs déplacerait implicitement des hypothèses de matériaux/lanes/budgets dans plusieurs fichiers. Un compositeur général requerrait plusieurs autorités géométriques et ledgers absents. La tranche retenue rend explicite une séparation utile : deux partitions physiques au maximum, chemins et couleurs, mais UNE chronologie originale. Les couleurs intercalées ne créent pas de nouveau pool path ni de compteur de travail.

## Sous-domaine

Root SRGB + CompositionDomain.LINEAR ; au moins un DrawColor SrcOver retenu et un vrai path DARKEN retenu. Couleurs : domaine W3 actuel, CTM identité, clip vide ou DeviceRect integral admissible, aucune nouvelle politique. Paths : origin PATH, HARD_EDGE, WINDING/EVEN_ODD non-inverse, identity/scale-translate, scissor simple W4c, Paint solide authentifié sans shader/filtre/ressource/effet. Blends path limités à SrcOver, DARKEN et DST NoOp.

La borne du nouveau domaine est 512 commandes ORIGINALES totales (borne W3 existante), plus les limites W4c/math déjà effectives : 512 path draws, stencil WINDING255 edges, attempted edges par path65536/frame262144 et subdivision32. L'ancien W4c conserve sa borne de path draws et ses règles metadata, pas une nouvelle borne totale. Pure DrawColor513 garde sa continuation historique ; un NotCandidate du nouveau domaine n'impose pas un quota global Surface.

Préclassifier la scène originale entière avant préparation ; valider toutes les métadonnées authentiques. Les non-membres gardent leurs frontières antérieures. Les erreurs, MaterialRefused et ResourceLimit gardent leurs catégories ; après ownership, refus terminal, sans source concurrente ou publication partielle. Empty/fully-clipped W4c restent leurs Gap historiques. DST conserve le travail géométrique tenté avant élimination, sans occurrence matériau/packet factice. Pas de candidat mixte si aucun vrai path DARKEN reste retenu.

## Producteurs et preuve de frame

Créer W3DrawColorAdmissionV1.kt : issuer interne recognize(command: SceneCommand.DrawColor, commandIndexI32: Int, target: RectI32, compositionDomain: CompositionDomain): W3DrawColorAdmissionResultV1, résultat fermé Accepted(draw: SolidRectDraw), Gap(diagnostic: RenderDiagnostic), Invalid(diagnostic: RenderDiagnostic). Extraire exactement la reconnaissance/premultiplication/clip W3 actuelle ; W3 délègue au même issuer et adapte seulement le type de résultat. Aucun nouveau Paint/source/table pour une couleur, aucun changement de diagnostics ou identité. Les helpers de shape/math restent dans leurs modules existants.

Créer W4cFramePreparationV1.kt : W4cOriginalFrameAdmissionV1 inaccessible à un caller étranger ; W4cFramePreparationV1.prepareFrame(admission: W4cOriginalFrameAdmissionV1): W4cFramePreparationResultV1. Deux modes fermés, historique PathOnly et nouveau HardPathRoot. L'admission lie owner, scène originale, target et politique. Extraire la boucle/préparation/authentification W4c au lieu de la copier ; les anciens candidats l'utilisent également. Une seule progression frameAttemptedEdgesBeforeI32 traverse tous les paths, même de part et d'autre des couleurs, et conserve Accepted/NoOp/MaterialRefused. Pas de retessellation à publication/lowering.

Créer W7HardPathRootFrameV1.kt : type de preuve immuable à constructor non public, émis seulement après l'admission/préparation complète. Contient original scene/target fingerprints, slots commandIndexI32/command fingerprint ordonnés, paths préparés, couleurs LegacyColor authentiques, résultats NoOp et attempted-work/politique, mapping dense des seules occurrences path avant interning. Pas de factory à partir de List<RenderGraph>, SceneSnapshot de substitution ou candidat étranger.

W4cPathFillPlanCompiler.select(scene,target) reste l'entrée enregistrée ; nouvelle variante privée de candidat pour cette preuve, capability W7_HARD_PATH_ROOT_CAPABILITY_ID = "w7.w4c.root-drawcolor-path.v1". IDs/candidats/planIdentity historiques conservés. Aucun issuer public supplémentaire ; l'accès inter-module est read-only.

## Publication et budget V1 réel

W5bDestinationGraphSealer.constructHardPathRoot(frame: W7HardPathRootFrameV1, capabilities: PlanCapabilitySnapshot, budget: PlanBudget): RenderGraphConstruction est l'entrée interne distincte. Réutiliser sizeLayout/finish destination, sans ouvrir construct historique ni appliquer materialPlanRef() à LegacyColor.

Calculer UNE fois PathFillPlanBudget pour tous les paths retenus ; réserver UNE partition W3-compatible pour toutes les couleurs, en factorisant la recette existante de nativeCompositeGeometryLayoutV4 (32bytes vertex,24bytes index,stride uniform aligné32 par couleur + floors effectifs). Deux jeux V/I/U, target/readback comptés une fois, depth une fois, snapshot d'extents selon W5b ; chaque draw pointe vers sa vraie partition.

Les sources sont V1 solides réelles ; RawMaterialRequirementsV2.requireFrameBudget couvre toutes les sources distinctes + inventaire physique complet. Ne pas renommer une source V1 en V4, ajouter un filtre identité ou ouvrir PackedFrameSourcesV4.issue pour prétendre posséder son permit. Le permit/check V1 réel est attesté par une Publication immuable liée au frame, caps/budget, table/mapping et inventaire exacts, créée après le check.

Publication, nested dans W7HardPathRootFrameV1, possède constructor non public et un accès read-only depuis RenderGraph.hardPathRootPublicationV1OrNull(): W7HardPathRootFrameV1.Publication?. Elle authentifie graph id/target/domain/caps/budget/ressources/passes/dependencies/chronologie et les deux partitions. Elle ne peut pas être composée depuis deux frames différents. RenderGraphConstruction conserve cette autorité jusqu'au graphe final ; remapping de refs ne transforme jamais LegacyColor.

W5bGeometryLanePlanV3 distingue Path et DrawColor pour ce seul token. Chaque partition pointe vers la même vraie construction root, pas un sourceGraph portant un ancien ID mensonger. command indices path peuvent être non contigus. Les validations historiques RenderGraph/validateW5bGeometryPasses restent fermées ; le nouveau bras réutilise leurs checks exacts et n'admet LegacyColor qu'aux slots prouvés du token, jamais via un booléen permissif ou le seul capability string.

Destination : chaque couleur retenue incrémente la version ; chaque DARKEN copy précède SON stencil producer/cover. Producer/cover restent adjacents, atomiques, mêmes geometry/scissor/depth et load/store. Aucun snapshot périmé après une couleur, fusion de copies incompatible ou suppression de dépendance.

## Consommation native

Dispatch exact dans GpuPlanTaskListLowerer vers W5bNativeGeometryGraphLowerer.lower(request: GpuPlanLoweringRequest). Nouveau bras fermé au token/capability, bijection slots/partitions : réutiliser W4cPathFillGraphLowerer.w5bPacket/sealScratch sur tous les paths et GpuPlanTaskListLowerer.packet/sealW3Scratch sur les vraies valeurs LegacyColor. Réserver/valider les capacities natives exactes des deux partitions ; même target/readback, frame/capability seal et generation. Ne pas appeler materialPlanRef() pour une couleur.

W5bPreparedFrameWitnessV3.validates conserve l'égalité par concaténation historique. Pour le nouveau contrat, vérifier exactement l'ordre des packets dérivé des passes authentifiées, les rôles Shading/producer/cover, command/packet IDs, ressources et uniform bytes de chaque partition. Exhaustivité et unicité des packets : un slot couleur donne un Shading packet, un slot stencil donne producer PUIS cover adjacents. Ni tri permissif ni simple égalité d'ensembles. Chaque packet appartient à une unique partition liée au même frame.

GPUCorePrimitivePreparedFrameTaskListBuilder.buildW5bPreplanned et GPUFramePreflighter.validateW5bPathGeometry restent inchangés. Ne pas relâcher le generic prepared path-stencil one-pass check qui refuse aujourd'hui le témoin. Toute autre dépendance native doit être rapportée avec signature/contradiction avant une décision contrôleur explicite ; ne pas élargir silencieusement le périmètre.

## Preuves publiques

Conserver W7RootDrawColorCompositionSurfacePixelTest et toutes les fixtures gelées. Son hardDarkenPathsComposeWithDrawColor doit devenir GREEN ; ring/hairline restent ouverts, root attendu au mieux6/8.

Une nouvelle W7HardPathRootDrawColorSurfacePixelTest, Surface32x32, full4096 bytes, oracles calculés AVANT Surface, RGB±2/alpha exact, replay byte-identique, buffers retenus via propriété existante et vrai Render+Readback/clean/zero refusal/submit1/readback1/ordre de completion. Pas de nouveau harnais, debug accessor, test source-text, forwarding ou manipulation de witness privé.

Cas :
- Trois ordres : blanc initial + RED DARKEN path[4,4,20,20] + GREEN DARKEN path[12,12,28,28] => blanc/red/green/black par zone ; BLUE entre les paths => bleu hors second/noir dedans ; BLUE après => bleu complet.
- Alpha : même scène, BLUE128 entre paths. Oracle LINEAR par stage UNORM : horspaths[187,187,255,255], firstonly[187,0,188,255], secondonly[0,187,0,255], intersection[0,0,0,255], sous±2RGB/alphaexact. Calcul indépendant EOTF/SrcOver/DARKEN/OETF/quantization, sans valeur obtenue du plan ou renderer.
- Path partition non contiguë : WHITE, triangle RED SrcOver vertices(4,4),(19,4),(4,20), BLUE128, GREEN DARKEN rectangle[8,8,24,24]. Oracle de centres : x/y>=4 et 16*(x+.5-4)+15*(y+.5-4)<240 pour triangle ; coordonnées choisies sans centres sur l'hypoténuse. Appliquer indépendamment les mêmes stages, distinguer les quatre zones.
- DST path intercalé ne modifie pas les pixels ni ne décale les sources ; mêmes couleurs/deuxpaths. Contrôles homogènes : couleurs seules et deuxpaths seuls, horspaths transparentblack pour le second.
- Public compiler select :510 paths EVEN_ODD de515 edges (unDARKEN et509DST), divisés255/255 par une couleur, plusune couleurinitiale, soit512commandes. Chacun et chaque moitié sous262144, total262650 au-dessus : ResourceLimitExceeded "w4c.path.resource_limit" et pas un reset après couleur. Test métier via SceneSnapshot réel/select public, pas introspection de token.
- B/B-1 : dériver B par les recettes exactes target/readback/depth/snapshot + deux partitions V/I/U + sourcesV1distinctes et alignments/caps effectifs. Inscrire le literal dérivé et sa décomposition avant product patch ; pas B37184/B29408, seuil recherché ou pool capacity imaginée. B positif et B-1 refus terminal, sentinel0x5a puis recoveryBLUE sur mêmeSurface.
- Frontière mixed512/513 : autant de vraies couleurs que nécessaire + deuxpathsDARKEN ; qualifier512. Mesurer513 sans imposer une fausse limite globale à Surface ; sa continuation peut réussir. Le select du domaine fermé doit rester NotCandidate à513, contrôlé via API publique. Un résultat natif réussi à513 reste un succès hors nouveau domaine.
- Foreign families : strokeAA et shader path restent sur refus antérieur mesuré, sentinel inchangée puis recovery ; ne choisir aucun prefix négatif par intuition.

Contrôles W3 six/budgets, W4c public compiler/caps/buffers/foreign candidates, W7MixedRootAaRect B29408/B-1, commonAA, destination9, GmCanvas/Skia/encoded et root entier. Modules comparés aux maps d'échecs retenues, sans prétendre effacer leur dette. Après le lot, mesurer104/121/524/111 puis full631/443 inchangé avant finalisation Task2 ; bénéfice GM non prédit.

## Limites et réalisation

Trois reviewer-sized tasks : témoins + extraction conservatrice sans admission nouvelle ; preuve/publication plan ; consumer natif + qualification. Fixture budget dérivée avant activation, staging homogène autorisé pour obtenir caps/inventaire sans deviner. Cette extraction seule n'est pas une livraison W7 autonome.

Fonts/codecs/jpg exclus, geometry math I/F32/64 ; pas de changement de shader/executor/default/domain/reference/score/tolerance/exclusion/budget/cap/epoch/quotas/lifetimes/cache. Controller seul runtime/Git, wrapper240/offline/no-daemon/init existant/FULLaudit/PGIDvide/postseal séparé, workers gelés. Aucun push/PR update dans ce lot. L'ancienne généralisation root reste suspendue.

B chiffré et résultats natifs du nouveau frame seront issus de la qualification, pas de ce diagnostic. Cause/remédiation des29/119 module failures, Picture mismatch antérieur, ring/hairline,21autres pertes et convergence443 restent ouverts. Choisir cette famille ne les clôt pas.
