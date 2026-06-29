# Catalogue des scenes GPU Renderer

## Scenes executables

### Solid Card Stack (`solid-card-stack`)
M0,M1 - Rect - `ShouldRender`

Intention: Verifier une pile de cartes solides avec ordre de dessin stable.
Valide: Valide Clear, FillRect, alpha borne et paintOrder deterministe.
Ne revendique pas: Ne revendique pas les rrect, paths, gradients ou textures.
Preuve: Preuve WebGPU offscreen et Kadre windowed.

### Activation Candidate Boundary Board (`activation-candidate-boundary-board`)
M0,M1 - Rect, Cache, LegacyComparison - `ShouldRender`

Intention: Rendre visible les frontieres d'activation produit avant promotion.
Valide: Valide des lanes rectangulaires de diagnostic et de politique d'activation.
Ne revendique pas: Ne revendique pas une activation produit ni un mouvement de readiness.
Preuve: Preuve WebGPU offscreen; lecture PM des lanes attendue.

### First Route Rollback Panel (`first-route-rollback-panel`)
M1 - Rect, LegacyComparison - `ShouldRender`

Intention: Montrer le premier routage FillRect avec voie de rollback explicite.
Valide: Valide les lanes legacy, route candidate, rollback et refus de variante.
Ne revendique pas: Ne revendique pas une activation par defaut ni une migration globale.
Preuve: Preuve WebGPU offscreen et Kadre windowed.

### Product Route Smoke Lanes (`product-route-smoke-lanes`)
M0,M1 - Rect, LegacyComparison - `ShouldRender`

Intention: Verifier une lecture smoke des routes produit, legacy et rollback sans activation finale.
Valide: Valide des lanes rectangulaires lisibles pour transition M0/M1 et refus visibles.
Ne revendique pas: Ne revendique pas activation produit par defaut ni decision release.
Preuve: Preuve WebGPU offscreen et Kadre windowed.

### Rounded Panel Gradient (`rounded-panel-gradient`)
M2 - RRect, Gradient, Clip - `ShouldRender`

Intention: Verifier un panneau arrondi avec clip simple et degrade lineaire.
Valide: Valide FillRRect, Clip et LinearGradientRect dans une scene compacte.
Ne revendique pas: Ne revendique pas les rayons par coin, tile modes ou transforms complexes.
Preuve: Preuve WebGPU offscreen et Kadre windowed.

### RRect Gradient Route Board (`rrect-gradient-route-board`)
M2 - Rect, RRect, Gradient, Clip - `ShouldRender`

Intention: Documenter la planification rrect et degrade avec refus visibles.
Valide: Valide rrect natif, degrade lineaire et lanes de refus de route.
Ne revendique pas: Ne revendique pas tile modes, transforms de shader ou pipeline key complet.
Preuve: Preuve WebGPU offscreen avec diagnostics de refus visibles.

### Release Gate Progress Board (`release-gate-progress-board`)
M2 - Rect, RRect, Gradient, Clip - `ShouldRender`

Intention: Representer une progression de gate release dans un panneau clippe.
Valide: Valide rrect, scissor simple, degrade de progression et marqueur ordonne.
Ne revendique pas: Ne revendique pas une gate release bloquante ni une mesure de performance.
Preuve: Preuve WebGPU offscreen et Kadre windowed.

### Gradient Tile Mode Boundary (`gradient-tile-mode-boundary`)
M2 - Rect, Gradient, Clip - `ShouldRender`

Intention: Rendre visible les variations tile mode et transform autour des gradients.
Valide: Valide des lanes de refus repeat, mirror, decal et matrice locale.
Ne revendique pas: Ne revendique pas repeat, mirror, decal ou matrices locales completes.
Preuve: Preuve WebGPU offscreen avec refus gradient visibles.

### Path Badge And Stroke (`path-badge-and-stroke`)
M3 - RRect, Rect - `ShouldRender`

Intention: Garder une scene simple pour badge et proxy de stroke.
Valide: Valide un FillRRect et un FillRect utilise comme proxy visuel de stroke.
Ne revendique pas: Ne revendique pas la couverture path native ni les joins/caps.
Preuve: Preuve WebGPU offscreen et Kadre windowed.

### Path Coverage Review Board (`path-coverage-review-board`)
M3 - Rect, RRect, Clip, Path, Stroke - `ShouldRender`

Intention: Exposer les contrats path, stroke, clip et atlas encore bornes.
Valide: Valide des lanes de revue pour path fill, stroke simple, clip et refus atlas.
Ne revendique pas: Ne revendique pas stencil-cover natif ni couverture path GPU complete.
Preuve: Preuve WebGPU offscreen avec refus explicites.

### Path Stencil Cover Gate Board (`path-stencil-cover-gate-board`)
M3 - Rect, RRect, Clip, Path, Stroke - `ShouldRender`

Intention: Rendre visible la gate stencil-cover path natif cloturee sans promotion.
Valide: Valide le contrat candidate, les diagnostics de refus et les raisons skipped-lane.
Ne revendique pas: Ne revendique pas la route native stencil-cover ni activation produit.
Preuve: Preuve WebGPU offscreen avec statut de gate contractuelle.

### Path AA Stroke Join Board (`path-aa-stroke-join-board`)
M3 - Rect, Clip, Path, Stroke - `ShouldRender`

Intention: Preparer une revue des joins, caps et AA path avant support natif.
Valide: Valide des lanes de refus AA pour joins, caps, coverage et stencil-cover.
Ne revendique pas: Ne revendique pas couverture AA, joins/caps reels ou stencil-cover natif.
Preuve: Preuve WebGPU offscreen avec refus path/stroke visibles.

### Clipped Avatar Grid (`clipped-avatar-grid`)
M3,M5 - Clip, Image - `ShouldRender`

Intention: Verifier un contenu image fixture-backed dans une zone clippee.
Valide: Valide Clip et BitmapRect avec sampling lineaire dans une grille d'avatars.
Ne revendique pas: Ne revendique pas un vrai decode image, texture arbitraire ou clip rrect complet.
Preuve: Preuve WebGPU offscreen et Kadre windowed.

### Texture Swatch Board (`texture-swatch-board`)
M4 - Image - `ShouldRender`

Intention: Comparer deux swatches bitmap deja decodees.
Valide: Valide BitmapRect nearest et linear avec fixtures de couleur.
Ne revendique pas: Ne revendique pas codec, mipmap, tile mode ou color management.
Preuve: Preuve WebGPU offscreen et Kadre windowed.

### Asset Intake Thumbnail Grid (`asset-intake-thumbnail-grid`)
M4 - Image, Clip, RRect - `ShouldRender`

Intention: Verifier des thumbnails d'asset deja decodees dans un tray.
Valide: Valide Clear, FillRRect, Clip et deux BitmapRect upload-ready.
Ne revendique pas: Ne revendique pas l'upload texture arbitraire ni la provenance codec complete.
Preuve: Preuve WebGPU offscreen et Kadre windowed.

### Photo Contact Sheet (`photo-contact-sheet`)
M4 - Image, Clip, RRect - `ShouldRender`

Intention: Verifier un contact sheet de quatre photos deja decodees.
Valide: Valide un tray rrect clippe et quatre BitmapRect fixture-backed.
Ne revendique pas: Ne revendique pas codec reel, upload texture arbitraire ou color management.
Preuve: Preuve WebGPU offscreen et Kadre windowed.

### Codec Provenance Gate Board (`codec-provenance-gate-board`)
M4 - Rect, RRect, Clip, Image - `ShouldRender`

Intention: Rendre visible les exigences de provenance codec avant promotion.
Valide: Valide sample bitmap fixture et lanes registry, dependency et provenance.
Ne revendique pas: Ne revendique pas decode codec reel ni promotion de route image complete.
Preuve: Preuve WebGPU offscreen avec refus dependency/provenance.

### Sampler Boundary Gate Board (`sampler-boundary-gate-board`)
M4 - Rect, RRect, Clip, Image - `ShouldRender`

Intention: Exposer les limites sampler autour des fixtures bitmap.
Valide: Valide nearest, linear et lanes de refus tile, mipmap, cubic et anisotropic.
Ne revendique pas: Ne revendique pas sampler avance, perspective ou decode color-managed.
Preuve: Preuve WebGPU offscreen avec refus visibles.

### Bitmap Sampler Matrix (`bitmap-sampler-matrix`)
M4 - Image, Clip, RRect - `ShouldRender`

Intention: Comparer une matrice compacte de fixtures bitmap avec deux politiques de sampling.
Valide: Valide un tray rrect clippe et plusieurs BitmapRect nearest ou linear bien bornes.
Ne revendique pas: Ne revendique pas mipmap, tile mode, anisotropic ni decode color-managed.
Preuve: Preuve WebGPU offscreen et Kadre windowed.

### SaveLayer Isolation Gate Board (`savelayer-isolation-gate-board`)
M5 - Rect, RRect, Clip, Layer - `ShouldRender`

Intention: Documenter les limites saveLayer avant support general.
Valide: Valide lanes d'isolation, bounds, destination-read et refus de layer.
Ne revendique pas: Ne revendique pas saveLayer general ni DAG image-filter.
Preuve: Preuve WebGPU offscreen avec refus produit/route explicites.

### Destination Read Strategy Gate Board (`destination-read-strategy-gate-board`)
M5 - Rect, RRect, Clip, Blend - `ShouldRender`

Intention: Rendre visible la strategie destination-read avant blending avance.
Valide: Valide lanes de politique dst-read et blend sans lecture destination active.
Ne revendique pas: Ne revendique pas framebuffer fetch, dst texture ou blend compose general.
Preuve: Preuve WebGPU offscreen avec refus destination-read.

### Layer Filter Chain Board (`layer-filter-chain-board`)
M5 - Rect, Clip, Layer, Filter - `ShouldRender`

Intention: Preparer une scene de chainage layer/filter plus ambitieuse.
Valide: Valide des lanes de refus pour DAG, texture intermediaire et destination-read.
Ne revendique pas: Ne revendique pas saveLayer general, destination-read, intermediate textures ou DAG arbitraire.
Preuve: Preuve WebGPU offscreen avec refus layer/filter visibles.

### Layered Shadow Card (`layered-shadow-card`)
M5 - Layer, Filter - `ShouldRender`

Intention: Verifier une carte shadow bornee materialisee par fixture.
Valide: Valide SaveLayer fixture-backed et FilterNode drop-shadow borne.
Ne revendique pas: Ne revendique pas saveLayer general ni image-filter DAG arbitraire.
Preuve: Preuve WebGPU offscreen et Kadre windowed.

### Notification Shadow Stack (`notification-shadow-stack`)
M5 - Layer, Filter - `ShouldRender`

Intention: Verifier deux notifications shadow bornees empilees.
Valide: Valide deux SaveLayer fixture-backed et deux FilterNode drop-shadow.
Ne revendique pas: Ne revendique pas saveLayer general, blur arbitraire ou filtre DAG complet.
Preuve: Preuve WebGPU offscreen et Kadre windowed.

### Filtered Photo Chip (`filtered-photo-chip`)
M5 - Filter, Image - `ShouldRender`

Intention: Verifier un chip photo avec filtre luma-tint borne.
Valide: Valide BitmapRect fixture-backed et FilterNode luma-tint.
Ne revendique pas: Ne revendique pas image-filter DAG ni color filter general.
Preuve: Preuve WebGPU offscreen et Kadre windowed.

### Tinted Avatar Card (`tinted-avatar-card`)
M5 - Image, Clip, RRect, Filter - `ShouldRender`

Intention: Verifier une carte avatar clippee avec teinte luma.
Valide: Valide Clear, FillRRect, Clip, BitmapRect et FilterNode luma-tint.
Ne revendique pas: Ne revendique pas DAG filtre general ni sampling image avance.
Preuve: Preuve WebGPU offscreen et Kadre windowed.

### Filter DAG Refusal Board (`filter-dag-refusal-board`)
M5 - Rect, Filter - `ShouldRender`

Intention: Lister les refus de DAG filtre avant promotion.
Valide: Valide des lanes de refus stables pour familles de filtre.
Ne revendique pas: Ne revendique pas execution de DAG image-filter ni textures intermediaires.
Preuve: Preuve WebGPU offscreen avec refus attendus.

### Receipt Text Run (`receipt-text-run`)
M6 - Text - `ShouldRender`

Intention: Representer un run texte de ticket avec inputs font reels.
Valide: Valide le payload TextRun et les diagnostics de route texte indisponible.
Ne revendique pas: Ne revendique pas glyph atlas WebGPU ni shaping complet.
Preuve: Preuve attendue comme refus stable de route texte.

### Text Handoff Boundary Board (`text-handoff-boundary-board`)
M6 - Rect, RRect, Clip, Text - `ShouldRender`

Intention: Exposer les frontieres de handoff texte et artefacts types.
Valide: Valide lanes de font input, shaping, atlas et refus de handoff.
Ne revendique pas: Ne revendique pas rendu texte GPU ni fallback font complet.
Preuve: Preuve WebGPU offscreen avec refus texte visibles.

### Text Resource Binding Gate Board (`text-resource-binding-gate-board`)
M6 - Rect, RRect, Clip, Text - `ShouldRender`

Intention: Rendre visible les blockers de binding ressource texte.
Valide: Valide lanes upload plan, binding layout, stale generation et budget.
Ne revendique pas: Ne revendique pas upload glyph atlas ni texture CPU-rendered supportee.
Preuve: Preuve WebGPU offscreen avec refus binding texte.

### A8 Glyph Atlas Gate Board (`a8-glyph-atlas-gate-board`)
M6 - Rect, RRect, Clip, Text - `ShouldRender`

Intention: Documenter les gates d'atlas glyph A8.
Valide: Valide lanes descriptor, page, entry, generation et instance buffer.
Ne revendique pas: Ne revendique pas route A8 glyph atlas ni ordering upload-before-sample.
Preuve: Preuve WebGPU offscreen avec refus atlas explicites.

### Text Representation Gate Board (`text-representation-gate-board`)
M6 - Rect, RRect, Clip, Text - `ShouldRender`

Intention: Comparer les representations texte avant route GPU.
Valide: Valide lanes de representation, font artefacts et refus de route texte.
Ne revendique pas: Ne revendique pas shaping multi-script ni rendu glyph GPU.
Preuve: Preuve WebGPU offscreen avec refus texte attendus.

### Runtime Effect Color Tile (`runtime-effect-color-tile`)
M7 - RuntimeEffect - `ShouldRender`

Intention: Verifier une tuile RuntimeEffect SimpleRT enregistree.
Valide: Valide RuntimeEffectTile registered simple_rt avec uniform gColor.
Ne revendique pas: Ne revendique pas SkSL dynamique ni runtime effects arbitraires.
Preuve: Preuve WebGPU offscreen et Kadre windowed.

### Runtime Effect Descriptor Gate Board (`runtime-effect-descriptor-gate-board`)
M7 - Rect, RRect, Clip, RuntimeEffect - `ShouldRender`

Intention: Rendre visible le contrat descriptor RuntimeEffect.
Valide: Valide SimpleRT et lanes descriptor, WGSL, CPU implementation et uniforms.
Ne revendique pas: Ne revendique pas SpiralRT ni compilation dynamique de source SkSL.
Preuve: Preuve WebGPU offscreen avec diagnostics descriptor.

### Runtime Effect Refusal Gate Board (`runtime-effect-refusal-gate-board`)
M7 - Rect, RRect, Clip, RuntimeEffect - `ShouldRender`

Intention: Documenter les refus runtime effect attendus par produit.
Valide: Valide refus source arbitraire, child slot et placement non supporte.
Ne revendique pas: Ne revendique pas children RuntimeEffect ni route shader dynamique.
Preuve: Preuve WebGPU offscreen avec refus produit attendus.

### Runtime Effect Uniform Ladder (`runtime-effect-uniform-ladder`)
M7 - RuntimeEffect, RRect, Clip - `ShouldRender`

Intention: Verifier plusieurs tuiles RuntimeEffect SimpleRT avec une echelle de uniforms visible.
Valide: Valide le descriptor SimpleRT, le layout gColor et la stabilite des tuiles runtime fixture-backed.
Ne revendique pas: Ne revendique pas SkSL dynamique, SpiralRT ou runtime effects enfants.
Preuve: Preuve WebGPU offscreen et Kadre windowed.

### Custom Runtime Effect Valid Tile (`custom-runtime-effect-valid-tile`)
M32 - RuntimeEffect, Rect - `ShouldRender`

Intention: Enregistre un shader WGSL personnalise produisant une couleur unie, affiche un pave avec ce shader et verifie la sortie GPU.
Valide: Valide CustomRuntimeEffectTile avec wgslSource et uniformSchema personnalises.
Ne revendique pas: Ne revendique pas les runtime effects enregistres ni la compilation SkSL dynamique.
Preuve: Preuve WebGPU offscreen avec sortie GPU verifiee.

### Custom Runtime Effect Unregistered Refusal (`custom-runtime-effect-unregistered-refusal`)
M32 - RuntimeEffect, Rect - `ShouldRefuse:unsupported.runtime_effect.custom_wgsl_not_registered`

Intention: Tente d'executer un effet personnalise jamais enregistre. Doit refuser avec un diagnostic stable.
Valide: Valide le refus stable CustomRuntimeEffectTile avec customEffectId null.
Ne revendique pas: Ne revendique pas l'execution runtime effect non enregistree.
Preuve: Preuve WebGPU offscreen avec diagnostic de refus.

### Blend Mode Strip (`blend-mode-strip`)
M7 - Rect - `ShouldRender`

Intention: Garder une scene minimale pour une lane blend simple.
Valide: Valide un FillRect borne dans la famille blend/color actuelle.
Ne revendique pas: Ne revendique pas modes blend avances ni destination-read.
Preuve: Preuve WebGPU offscreen et Kadre windowed.

### Translucent Card Overlap (`translucent-card-overlap`)
M7 - Rect, Blend - `ShouldRender`

Intention: Verifier trois cartes alpha SrcOver bornees.
Valide: Valide FillRect avec alpha partiel et ordre de peinture stable.
Ne revendique pas: Ne revendique pas blend modes arbitraires ni color space large.
Preuve: Preuve WebGPU offscreen et Kadre windowed.

### SDR Color Boundary Board (`sdr-color-boundary-board`)
M7 - Rect, RRect, Clip - `ShouldRender`

Intention: Rendre visible les bornes couleur SDR.
Valide: Valide lanes de faits SDR, clamp et refus de color management avance.
Ne revendique pas: Ne revendique pas wide-gamut, HDR ou transforms color-space complets.
Preuve: Preuve WebGPU offscreen avec refus couleur.

### Mesh Ribbon (`mesh-ribbon`)
M8 - Vertices - `ShouldRender`

Intention: Verifier un ruban mesh fixture-backed borne.
Valide: Valide MeshRibbon bounded-ribbon-strip dans le runner actuel.
Ne revendique pas: Ne revendique pas DrawVertices general ni vertex/index buffer arbitraire.
Preuve: Preuve WebGPU offscreen et Kadre windowed.

### Vertices Route Gate Board (`vertices-route-gate-board`)
M8 - Rect, RRect, Clip, Vertices - `ShouldRender`

Intention: Lister les blockers de route vertices.
Valide: Valide lanes descriptor, primitive blend, buffer upload, ABI et batching.
Ne revendique pas: Ne revendique pas vertices generaux ni pipeline mesh complet.
Preuve: Preuve WebGPU offscreen avec refus vertices.

### Mesh Ribbon Depth Stack (`mesh-ribbon-depth-stack`)
M8 - Vertices, RRect, Clip - `ShouldRender`

Intention: Verifier plusieurs rubans mesh bornes avec overlaps lisibles dans un cadre simple.
Valide: Valide un stack de MeshRibbon fixture-backed avec ordre visuel stable et clipping borne.
Ne revendique pas: Ne revendique pas DrawVertices general ni upload libre de vertex/index buffers.
Preuve: Preuve WebGPU offscreen et Kadre windowed.

### Cache Pressure Deck (`cache-pressure-deck`)
M9 - Rect - `ShouldRender`

Intention: Representer une pression cache minimale et stable.
Valide: Valide deux rectangles comme deck de pression cache fixture.
Ne revendique pas: Ne revendique pas telemetry cache runtime observee.
Preuve: Preuve WebGPU offscreen et Kadre windowed.

### Cache Frame Budget Strip (`cache-frame-budget-strip`)
M9 - Rect, Cache - `ShouldRender`

Intention: Rendre visible un budget frame/cache depasse comme refus attendu.
Valide: Valide les lanes budget cible, warning, depassement et gate reporting-only.
Ne revendique pas: Ne revendique pas mesure runtime WebGPU observee ni gate release-blocking.
Preuve: Preuve WebGPU offscreen avec refus budget visible.

### Cache Source Ledger Board (`cache-source-ledger-board`)
M9 - Rect, Cache - `ShouldRender`

Intention: Rendre visible la classification des sources cache.
Valide: Valide lanes de source cache, stale, generated et policy.
Ne revendique pas: Ne revendique pas cache WebGPU observe ni eviction runtime.
Preuve: Preuve WebGPU offscreen avec ledger diagnostic.

### Frame Gate Blocker Board (`frame-gate-blocker-board`)
M9 - Rect - `ShouldRender`

Intention: Exposer les blockers de frame gate.
Valide: Valide lanes de politique frame, telemetry et gating reporting-only.
Ne revendique pas: Ne revendique pas gate FPS release-blocking ni mesure native definitive.
Preuve: Preuve WebGPU offscreen avec blockers visibles.

### PM Readiness Freeze Board (`pm-readiness-freeze-board`)
M9 - Rect, RRect, Clip, Cache - `ShouldRender`

Intention: Montrer que la readiness PM ne bouge pas sans gate.
Valide: Valide lanes de policy, release blocking false et readiness delta zero.
Ne revendique pas: Ne revendique pas activation produit ni mouvement de readiness.
Preuve: Preuve WebGPU offscreen avec diagnostics PM.

### Legacy Route Comparison (`legacy-route-comparison`)
M10 - Rect - `ShouldRender`

Intention: Garder une comparaison legacy minimale.
Valide: Valide un proxy rectangulaire pour route legacy courante.
Ne revendique pas: Ne revendique pas comparaison pixel complete ni retrait legacy.
Preuve: Preuve WebGPU offscreen et Kadre windowed.

### Legacy Inventory Hygiene Board (`legacy-inventory-hygiene-board`)
M10 - Rect, RRect, Clip, LegacyComparison - `ShouldRender`

Intention: Rendre visible l'hygiene inventaire legacy.
Valide: Valide lanes inventory, archive, stale rows et cleanup gates.
Ne revendique pas: Ne revendique pas retrait de route legacy ni migration globale.
Preuve: Preuve WebGPU offscreen avec gates legacy.

### Shadow Parity Migration Gate Board (`shadow-parity-migration-gate-board`)
M10 - Rect, RRect, Clip, LegacyComparison - `ShouldRender`

Intention: Documenter les gates de parite shadow avant migration.
Valide: Valide lanes par famille pour parite, evidence et refus de migration.
Ne revendique pas: Ne revendique pas parite shadow complete ni remplacement accepte.
Preuve: Preuve WebGPU offscreen avec gates de parite.

### Legacy Retirement Blocker Board (`legacy-retirement-blocker-board`)
M10 - Rect, RRect, Clip, LegacyComparison - `ShouldRender`

Intention: Exposer les blockers de retrait legacy.
Valide: Valide lanes replacement, activation decision, rollback et evidence PM.
Ne revendique pas: Ne revendique pas retirement legacy ni route produit activee.
Preuve: Preuve WebGPU offscreen avec blockers de retirement.

### Legacy Parity Snapshot Board (`legacy-parity-snapshot-board`)
M10 - LegacyComparison, Rect, RRect - `ShouldRender`

Intention: Verifier une vue de parite legacy lisible avant toute decision de retirement.
Valide: Valide un board de comparaison rrect/rect borne avec lanes de parite, evidence et blockers.
Ne revendique pas: Ne revendique pas remplacement accepte ni retrait effectif de la route legacy.
Preuve: Preuve WebGPU offscreen et Kadre windowed.

### Rounded Rect Solids (`rounded-rect-solids`)
M10 - RRect - `ShouldRender`

Intention: Verifier trois rrects solides avec rayons varies.
Valide: Valide FillRRect avec rayons petits, moyens et grands.
Ne revendique pas: Ne revendique pas rayons par coin, gradients ou transforms avances.
Preuve: Preuve WebGPU offscreen et Kadre windowed.

### Linear Gradient Lanes (`linear-gradient-lanes`)
M10 - Rect, Gradient - `ShouldRender`

Intention: Verifier trois degradees lineaires bornes avec clamp.
Valide: Valide LinearGradientRect horizontal, vertical et diagonal.
Ne revendique pas: Ne revendique pas repeat, mirror, decal, local matrix ou plus de deux arrets.
Preuve: Preuve WebGPU offscreen et Kadre windowed.

### Scissor Overlay (`scissor-overlay`)
M10 - Rect, Clip - `ShouldRender`

Intention: Verifier un scissor simple avec rectangles bornes.
Valide: Valide Clip device-rect et FillRect ordonnes.
Ne revendique pas: Ne revendique pas clip rrect, stencil-cover ni clip stack complexe.
Preuve: Preuve WebGPU offscreen et Kadre windowed.

### Radial Swatch (`radial-swatch`)
M14 - Rect, Gradient - `ShouldRender`

Intention: Verifier trois degradees radiales avec centres et rayons varies.
Valide: Valide RadialGradientRect avec centres decales et rayons differs.
Ne revendique pas: Ne revendique pas repeat, mirror, decal, plus de deux arrets ou transforms.
Preuve: Preuve WebGPU offscreen et Kadre windowed.

### Sweep Disk (`sweep-disk`)
M14 - Rect, Gradient - `ShouldRender`

Intention: Verifier trois degradees angulaires avec angles de depart/fin varies.
Valide: Valide SweepGradientRect avec sweeps 360, 180 et 90 degres.
Ne revendique pas: Ne revendique pas repeat, mirror, decal, plus de deux arrets ou transforms.
Preuve: Preuve WebGPU offscreen et Kadre windowed.

### Path Fill Stencil (`path-fill-stencil`)
M15 - Path - `ShouldRender`

Intention: Remplir une etoile non-convexe via stencil-cover deux passes.
Valide: Valide PathFillStencil avec chemin etoile et fond blanc.
Ne revendique pas: Ne revendique pas stencil-cover GPU natif ni activation produit.
Preuve: Preuve WebGPU offscreen avec sortie PNG pour la scene.

### Convex Fan Mesh (`convex-fan-mesh`)
M15 - Path - `ShouldRender`

Intention: Remplir un octogone convexe via convex fan monopasse.
Valide: Valide ConvexFanMesh avec octogone regulier et fond blanc.
Ne revendique pas: Ne revendique pas stencil-cover pour chemins convexes ni activation produit.
Preuve: Preuve WebGPU offscreen avec sortie PNG pour la scene.

### SaveLayer Isolated (`savelayer-isolated`)
M18 - Layer - `ShouldRender`

Intention: Rendre un groupe translucide via offscreen saveLayer.
Valide: Valide SaveLayer avec cible isolee, rendu enfant et composite srcOver.
Ne revendique pas: Ne revendique pas saveLayer general, filtres, destination-read ou activation produit.
Preuve: Preuve WebGPU offscreen avec execution saveLayer.

### SaveLayer Group Alpha (`savelayer-group-alpha`)
M28 - Layer, Blend - `ShouldRender`

Intention: Rendre un saveLayer a group alpha 0.5 avec deux enfants opaques qui se chevauchent.
Valide: Valide l isolation de couche reelle: la region de chevauchement se compose a 50% uniforme comme le reste.
Ne revendique pas: Ne revendique pas saveLayer general, filtres, destination-read ou activation produit.
Preuve: Preuve WebGPU offscreen avec parite GPU/CPU sur le chevauchement.

### Destination Read Strategy (`dst-read-strategy`)
M18 - Layer, Blend - `ShouldRender`

Intention: Verifier une strategie destination-read par copie ou intermediate.
Valide: Valide FillRect avec lecture de destination via copie et bind intermediate.
Ne revendique pas: Ne revendique pas framebuffer fetch, input attachment ou fallback CPU.
Preuve: Preuve WebGPU offscreen avec strategie dst-read.

### Blur Radius Ladder (`blur-radius-ladder`)
M19 - Filter - `ShouldRender`

Intention: Verifier un echelon de rayons de flou gaussien.
Valide: Valide quatre rectangles avec blur radii croissants via separable gaussian.
Ne revendique pas: Ne revendique pas blur texture output ni kernel CPU.
Preuve: Preuve WebGPU offscreen avec scene blur.

### Color Matrix Filter (`color-matrix-filter`)
M19 - Filter - `ShouldRender`

Intention: Verifier trois transformations couleur via matrice 4x5 WGSL.
Valide: Valide identity, saturation et hue-rotate via color matrix filter.
Ne revendique pas: Ne revendique pas color matrix filter produit ni pipeline GPU separe.
Preuve: Preuve WebGPU offscreen avec scene color matrix.

### Gaussian Blur Photo (`gaussian-blur-photo`)
M19 - Filter - `ShouldRender`

Intention: Verifier un flou gaussien applique a un rectangle colore.
Valide: Valide GaussianBlur via passes gaussiennes separables sur un FillRect.
Ne revendique pas: Ne revendique pas blur texture output ni kernel CPU.
Preuve: Preuve WebGPU offscreen avec scene blur.

### Color Matrix Tint (`color-matrix-tint`)
M19 - Filter - `ShouldRender`

Intention: Verifier une teinte couleur via matrice 4x5 WGSL.
Valide: Valide ColorMatrix filter avec transformation sur un FillRect vert.
Ne revendique pas: Ne revendique pas color matrix filter produit ni pipeline GPU separe.
Preuve: Preuve WebGPU offscreen avec scene color matrix.

### Stroke and Filter Card (`stroke-and-filter-card`)
M16,M19 - Stroke, Filter - `ShouldRender`

Intention: Verifier une carte combinant contour stroke et flou gaussien.
Valide: Valide Stroke et FillRect avec blur dans une meme scene ordonnee.
Ne revendique pas: Ne revendique pas path coverage ni DAG filtre general.
Preuve: Preuve WebGPU offscreen avec scene combinee.

### Glyph Atlas Strip (`glyph-atlas-strip`)
M20 - Text - `ShouldRender`

Intention: Verifier un echantillonnage atlas glyph A8.
Valide: Valide TextRun avec glyph route a8-atlas et diagnostic rect.
Ne revendique pas: Ne revendique pas atlas glyph A8 executable ni rendu glyph texture.
Preuve: Preuve WebGPU offscreen avec scene text.

### SDF Glyph Scale (`sdf-glyph-scale`)
M20 - Text - `ShouldRender`

Intention: Verifier trois tailles de glyphe SDF avec smoothstep.
Valide: Valide TextRun avec glyph route sdf-atlas pour 3 tailles de fonte.
Ne revendique pas: Ne revendique pas atlas SDF executable ni rendu glyph texture.
Preuve: Preuve WebGPU offscreen avec scene text.

### Runtime Effect Uniform (`runtime-effect-uniform`)
M21 - RuntimeEffect - `ShouldRender`

Intention: Verifier quatre tuiles SimpleRT avec uniforms gColor differents.
Valide: Valide RuntimeEffectTile SimpleRT avec variation d uniforms et route registered descriptor.
Ne revendique pas: Ne revendique pas compilation SkSL dynamique, SpiralRT ou runtime effects enfants.
Preuve: Preuve WebGPU offscreen et Kadre windowed.

### Runtime Effect Child (`runtime-effect-child`)
M21 - RuntimeEffect - `ShouldRender`

Intention: Verifier une tuile SimpleRT avec reference a un shader enfant.
Valide: Valide RuntimeEffectTile avec label child-effect-tile et descriptor registered simple_rt.
Ne revendique pas: Ne revendique pas enfant runtime effect actif ni pipeline child compose.
Preuve: Preuve WebGPU offscreen et Kadre windowed.

### Stroke Cap Join (`stroke-cap-join`)
M16 - Stroke - `ShouldRender`

Intention: Verifier quatre styles de cap et join d expansion stroke.
Valide: Valide StrokeExpander avec butt, square, miter et bevel via FillRect proxy.
Ne revendique pas: Ne revendique pas stroke expansion GPU native ni activation produit.
Preuve: Preuve WebGPU offscreen avec scene stroke.

### Dash Pattern Ladder (`dash-pattern-ladder`)
M16 - Stroke - `ShouldRender`

Intention: Verifier quatre motifs dash via decomposition d intervalles.
Valide: Valide DashPathEffect avec shorts, mediums, longs et alternes via FillRect proxy.
Ne revendique pas: Ne revendique pas dash path effect GPU natif ni activation produit.
Preuve: Preuve WebGPU offscreen avec scene dash.

### Stroke Rect Outline (`stroke-rect-outline`)
M16 - Stroke - `ShouldRender`

Intention: Verifier un contour de rectangle stroke bleu simple.
Valide: Valide Stroke avec strokeColor, strokeWidth et pathKind bounded-rect-path.
Ne revendique pas: Ne revendique pas path AA ni joins/caps complexes.
Preuve: Preuve WebGPU offscreen avec scene stroke.

### Tile Mode Strip (`tile-mode-strip`)
M17 - Image - `ShouldRender`

Intention: Verifier quatre bandes avec modes tile clamp, repeat, mirror et decal.
Valide: Valide BitmapRect avec intentions de tile mode et echantillonnage nearest/linear.
Ne revendique pas: Ne revendique pas tile modes GPU natifs ni echantillonnage texture executable.
Preuve: Preuve WebGPU offscreen avec scene tile-mode.

### Vertices Color Mesh (`vertices-color-mesh`)
M22 - Vertices - `ShouldRender`

Intention: Rendre un maillage colore via per-vertex colors.
Valide: Valide VerticesExecutor avec triangle list, vertex colors et MeshRibbon scene.
Ne revendique pas: Ne revendique pas DrawVertices GPU natif ni activation produit.
Preuve: Preuve WebGPU offscreen avec scene vertices.

### Mesh Ribbon Depth (`mesh-ribbon-depth`)
M22 - Vertices - `ShouldRender`

Intention: Rendre trois rubans qui se chevauchent avec ordre de profondeur.
Valide: Valide MeshRibbon avec trois couches superposees et ordre paintOrder.
Ne revendique pas: Ne revendique pas mesh batching GPU natif ni activation produit.
Preuve: Preuve WebGPU offscreen avec scene mesh.

### Performance Budget Review (`performance-budget-review`)
M23 - Rect, Cache - `ShouldRender`

Intention: Verifier les budgets performance par famille de draw.
Valide: Valide PerformanceBudgetEvaluator avec seuils pass, warning et fail.
Ne revendique pas: Ne revendique pas release-blocking ni activation produit.
Preuve: Preuve WebGPU offscreen avec scene budget.

### Pipeline Cache Telemetry Review (`pipeline-cache-telemetry-review`)
M23 - Rect, Cache - `ShouldRender`

Intention: Verifier la telemetrie cache pipeline par scene.
Valide: Valide GPUPipelineCacheTelemetry avec hit rate, eviction et module count.
Ne revendique pas: Ne revendique pas cache readiness movement ni activation produit.
Preuve: Preuve WebGPU offscreen avec scene telemetry.

### Frame Gate M23 Baseline (`frame-gate-m23-baseline`)
M23 - Rect, Cache - `ShouldRender`

Intention: Verifier la politique frame gate M23 60fps/30fps.
Valide: Valide M23 baseline avec cibles 60fps et avertissement 30fps.
Ne revendique pas: Ne revendique pas release-blocking gate ni activation produit.
Preuve: Preuve WebGPU offscreen avec scene frame-gate.

### PM Evidence M23 Bundle (`pm-evidence-m23-bundle`)
M23 - Rect, Cache - `ShouldRender`

Intention: Verifier le bundle evidence PM final M23.
Valide: Valide M23PMEvidenceBundle avec familles activees et gates verts.
Ne revendique pas: Ne revendique pas readiness movement ni activation produit.
Preuve: Preuve WebGPU offscreen avec scene PM bundle.

### Performance Gates Product Flag (`performance-gates-product-flag`)
M23 - Rect - `ShouldRender`

Intention: Verifier le flag produit performanceGatesEnabled.
Valide: Valide performanceGatesEnabled flag et sa propriete systeme disable.
Ne revendique pas: Ne revendique pas release-blocking ni activation produit.
Preuve: Preuve WebGPU offscreen avec scene product flag.

### Path Star Gradient (`path-star-gradient`)
M26 - Path, Gradient - `ShouldRender`

Intention: Remplir une etoile non-convexe via stencil-cover avec degrade lineaire.
Valide: Valide PathFillGradient avec chemin etoile et degrade ambre vers vert.
Ne revendique pas: Ne revendique pas stencil-cover GPU natif ni activation produit.
Preuve: Preuve WebGPU offscreen avec sortie PNG pour la scene.

### Text A8 Hello (`text-a8-hello`)
M26 - Text - `ShouldRender`

Intention: Verifier un texte Hello Kanvas via atlas A8 active.
Valide: Valide TextRun avec route a8-atlas et fallback vide (route disponible).
Ne revendique pas: Ne revendique pas shaping complexe, emoji ou SDF atlas.
Preuve: Preuve WebGPU offscreen avec sortie PNG pour la scene.

### Gradient Path And Text (`gradient-path-and-text`)
M26 - Path, Gradient, Text - `ShouldRender`

Intention: Verifier une etoile degradee avec texte superpose via atlas A8.
Valide: Valide PathFillGradient et TextRun actif en composition.
Ne revendique pas: Ne revendique pas stencil-cover GPU natif ni activation produit.
Preuve: Preuve WebGPU offscreen avec sortie PNG pour la scene.

## Candidates amont

### Simple Latin Glyph Atlas Strip (`simple-latin-glyph-atlas-strip`)
M6 - Text, Cache

Statut: `dependency-gated`
Intention: Preparer une scene texte simple-latin adossee a un atlas glyph reel.
Validation visee: Valider glyph masks, atlas entries et binding ressource quand la dependance existe.
Ne revendique pas: Ne revendique pas shaping complexe, font fallback ou emoji/color fonts.
Raison: Couvre M6 sans ajouter de substitut court-terme pour font/atlas.
