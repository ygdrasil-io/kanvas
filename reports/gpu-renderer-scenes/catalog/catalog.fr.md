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

Intention: Rendre visible les blockers du stencil-cover path natif.
Valide: Valide les diagnostics de capacite depth-stencil, ordering et readback manquant.
Ne revendique pas: Ne revendique pas la route native stencil-cover ni activation produit.
Preuve: Preuve WebGPU offscreen avec statut de gate bloque.

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

## Candidates amont

### Gradient Tile Mode Boundary (`gradient-tile-mode-boundary`)
M2 - RRect, Gradient, Clip

Statut: `runner-gap`
Intention: Rendre visible les variations tile mode et transform autour des gradients.
Validation visee: Verifier que les tile modes non supportes refusent avec raison stable.
Ne revendique pas: Ne revendique pas repeat, mirror, decal ou matrices locales completes.
Raison: Complete les scenes M2 actuelles qui couvrent surtout le degrade lineaire simple.

### Path AA Stroke Join Board (`path-aa-stroke-join-board`)
M3 - Path, Stroke, Clip

Statut: `runner-gap`
Intention: Preparer une revue des joins, caps et AA path avant support natif.
Validation visee: Montrer les routes path/stroke attendues et les refus coverage manquants.
Ne revendique pas: Ne revendique pas couverture AA, joins/caps reels ou stencil-cover natif.
Raison: Couvre un trou M3 entre proxy rectangulaire et vraie couverture path.

### Layer Filter Chain Board (`layer-filter-chain-board`)
M5 - Layer, Filter

Statut: `runner-gap`
Intention: Preparer une scene de chainage layer/filter plus ambitieuse.
Validation visee: Distinguer bounded fixtures supportees et DAG image-filter non supporte.
Ne revendique pas: Ne revendique pas saveLayer general, intermediate textures ou DAG arbitraire.
Raison: Couvre les limites M5 autour de SaveLayer, FilterNode et destination-read.

### Simple Latin Glyph Atlas Strip (`simple-latin-glyph-atlas-strip`)
M6 - Text, Cache

Statut: `dependency-gated`
Intention: Preparer une scene texte simple-latin adossee a un atlas glyph reel.
Validation visee: Valider glyph masks, atlas entries et binding ressource quand la dependance existe.
Ne revendique pas: Ne revendique pas shaping complexe, font fallback ou emoji/color fonts.
Raison: Couvre M6 sans ajouter de substitut court-terme pour font/atlas.

### Cache Frame Budget Strip (`cache-frame-budget-strip`)
M9 - Rect, Cache

Statut: `product-refusal-expected`
Intention: Rendre visible un budget frame/cache depasse comme refus attendu.
Validation visee: Verifier qu'un budget depasse est expose comme refus produit explicite.
Ne revendique pas: Ne revendique pas mesure runtime WebGPU observee ni gate release-blocking.
Raison: Couvre M9 en alignant les refus attendus avec la politique produit.
