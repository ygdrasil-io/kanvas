package org.graphiks.kanvas.gpu.renderer.scenes.catalog

private fun requireHumanDocField(value: String, fieldName: String) {
    require(value.isNotBlank()) { "$fieldName must not be blank" }
}

data class LocalizedSceneText(
    val intention: String,
    val validates: String,
    val nonClaims: String,
    val evidence: String,
) {
    init {
        requireHumanDocField(intention, "LocalizedSceneText.intention")
        requireHumanDocField(validates, "LocalizedSceneText.validates")
        requireHumanDocField(nonClaims, "LocalizedSceneText.nonClaims")
        requireHumanDocField(evidence, "LocalizedSceneText.evidence")
    }
}

data class SceneHumanDocs(
    val sceneId: SceneId,
    val french: LocalizedSceneText,
)

enum class CandidateSceneStatus(val wireName: String) {
    Candidate("candidate"),
    FixtureReady("fixture-ready"),
    RunnerGap("runner-gap"),
    DependencyGated("dependency-gated"),
    ProductRefusalExpected("product-refusal-expected"),
}

data class CandidateSceneFrenchText(
    val intention: String,
    val validationTarget: String,
    val nonClaims: String,
    val rationale: String,
) {
    init {
        requireHumanDocField(intention, "CandidateSceneFrenchText.intention")
        requireHumanDocField(validationTarget, "CandidateSceneFrenchText.validationTarget")
        requireHumanDocField(nonClaims, "CandidateSceneFrenchText.nonClaims")
        requireHumanDocField(rationale, "CandidateSceneFrenchText.rationale")
    }
}

data class CandidateScene(
    val sceneId: SceneId,
    val title: String,
    val roadmapLinks: List<SceneRoadmapLink>,
    val tags: Set<SceneTag>,
    val status: CandidateSceneStatus,
    val french: CandidateSceneFrenchText,
) {
    init {
        require(title.isNotBlank()) { "candidate ${sceneId.value} title must not be blank" }
        require(roadmapLinks.isNotEmpty()) { "candidate ${sceneId.value} roadmapLinks must not be empty" }
        require(tags.isNotEmpty()) { "candidate ${sceneId.value} tags must not be empty" }
    }
}

internal fun validateSceneHumanDocumentation(
    scenes: List<GPURendererScene<*>>,
    docs: List<SceneHumanDocs>,
    candidateScenes: List<CandidateScene>,
): List<String> {
    val diagnostics = mutableListOf<String>()
    val executableIds = scenes.map { it.sceneId.value }.toSet()
    val docIds = docs.map { it.sceneId.value }
    val duplicateDocIds = docIds.groupingBy { it }.eachCount().filterValues { it > 1 }.keys
    duplicateDocIds.forEach { diagnostics += "duplicate human docs sceneId=$it" }
    docIds.filterNot { it in executableIds }.forEach {
        diagnostics += "human docs sceneId=$it does not match an executable scene"
    }
    executableIds.filterNot { it in docIds }.forEach {
        diagnostics += "missing human docs sceneId=$it"
    }

    val candidateIds = candidateScenes.map { it.sceneId.value }
    val duplicateCandidateIds = candidateIds.groupingBy { it }.eachCount().filterValues { it > 1 }.keys
    duplicateCandidateIds.forEach { diagnostics += "duplicate candidate sceneId=$it" }
    candidateIds.filter { it in executableIds }.forEach {
        diagnostics += "candidate sceneId=$it must not be executable"
    }
    return diagnostics
}

object GPURendererSceneHumanDocs {
    val docs: List<SceneHumanDocs> = listOf(
        doc("solid-card-stack", "Verifier une pile de cartes solides avec ordre de dessin stable.", "Valide Clear, FillRect, alpha borne et paintOrder deterministe.", "Ne revendique pas les rrect, paths, gradients ou textures.", "Preuve WebGPU offscreen et Kadre windowed."),
        doc("activation-candidate-boundary-board", "Rendre visible les frontieres d'activation produit avant promotion.", "Valide des lanes rectangulaires de diagnostic et de politique d'activation.", "Ne revendique pas une activation produit ni un mouvement de readiness.", "Preuve WebGPU offscreen; lecture PM des lanes attendue."),
        doc("first-route-rollback-panel", "Montrer le premier routage FillRect avec voie de rollback explicite.", "Valide les lanes legacy, route candidate, rollback et refus de variante.", "Ne revendique pas une activation par defaut ni une migration globale.", "Preuve WebGPU offscreen et Kadre windowed."),
        doc("product-route-smoke-lanes", "Verifier une lecture smoke des routes produit, legacy et rollback sans activation finale.", "Valide des lanes rectangulaires lisibles pour transition M0/M1 et refus visibles.", "Ne revendique pas activation produit par defaut ni decision release.", "Preuve WebGPU offscreen et Kadre windowed."),
        doc("rounded-panel-gradient", "Verifier un panneau arrondi avec clip simple et degrade lineaire.", "Valide FillRRect, Clip et LinearGradientRect dans une scene compacte.", "Ne revendique pas les rayons par coin, tile modes ou transforms complexes.", "Preuve WebGPU offscreen et Kadre windowed."),
        doc("rrect-gradient-route-board", "Documenter la planification rrect et degrade avec refus visibles.", "Valide rrect natif, degrade lineaire et lanes de refus de route.", "Ne revendique pas tile modes, transforms de shader ou pipeline key complet.", "Preuve WebGPU offscreen avec diagnostics de refus visibles."),
        doc("release-gate-progress-board", "Representer une progression de gate release dans un panneau clippe.", "Valide rrect, scissor simple, degrade de progression et marqueur ordonne.", "Ne revendique pas une gate release bloquante ni une mesure de performance.", "Preuve WebGPU offscreen et Kadre windowed."),
        doc("gradient-tile-mode-boundary", "Rendre visible les variations tile mode et transform autour des gradients.", "Valide des lanes de refus repeat, mirror, decal et matrice locale.", "Ne revendique pas repeat, mirror, decal ou matrices locales completes.", "Preuve WebGPU offscreen avec refus gradient visibles."),
        doc("path-badge-and-stroke", "Garder une scene simple pour badge et proxy de stroke.", "Valide un FillRRect et un FillRect utilise comme proxy visuel de stroke.", "Ne revendique pas la couverture path native ni les joins/caps.", "Preuve WebGPU offscreen et Kadre windowed."),
        doc("path-coverage-review-board", "Exposer les contrats path, stroke, clip et atlas encore bornes.", "Valide des lanes de revue pour path fill, stroke simple, clip et refus atlas.", "Ne revendique pas stencil-cover natif ni couverture path GPU complete.", "Preuve WebGPU offscreen avec refus explicites."),
        doc("path-stencil-cover-gate-board", "Rendre visible la gate stencil-cover path natif cloturee sans promotion.", "Valide le contrat candidate, les diagnostics de refus et les raisons skipped-lane.", "Ne revendique pas la route native stencil-cover ni activation produit.", "Preuve WebGPU offscreen avec statut de gate contractuelle."),
        doc("path-aa-stroke-join-board", "Preparer une revue des joins, caps et AA path avant support natif.", "Valide des lanes de refus AA pour joins, caps, coverage et stencil-cover.", "Ne revendique pas couverture AA, joins/caps reels ou stencil-cover natif.", "Preuve WebGPU offscreen avec refus path/stroke visibles."),
        doc("clipped-avatar-grid", "Verifier un contenu image fixture-backed dans une zone clippee.", "Valide Clip et BitmapRect avec sampling lineaire dans une grille d'avatars.", "Ne revendique pas un vrai decode image, texture arbitraire ou clip rrect complet.", "Preuve WebGPU offscreen et Kadre windowed."),
        doc("texture-swatch-board", "Comparer deux swatches bitmap deja decodees.", "Valide BitmapRect nearest et linear avec fixtures de couleur.", "Ne revendique pas codec, mipmap, tile mode ou color management.", "Preuve WebGPU offscreen et Kadre windowed."),
        doc("asset-intake-thumbnail-grid", "Verifier des thumbnails d'asset deja decodees dans un tray.", "Valide Clear, FillRRect, Clip et deux BitmapRect upload-ready.", "Ne revendique pas l'upload texture arbitraire ni la provenance codec complete.", "Preuve WebGPU offscreen et Kadre windowed."),
        doc("photo-contact-sheet", "Verifier un contact sheet de quatre photos deja decodees.", "Valide un tray rrect clippe et quatre BitmapRect fixture-backed.", "Ne revendique pas codec reel, upload texture arbitraire ou color management.", "Preuve WebGPU offscreen et Kadre windowed."),
        doc("codec-provenance-gate-board", "Rendre visible les exigences de provenance codec avant promotion.", "Valide sample bitmap fixture et lanes registry, dependency et provenance.", "Ne revendique pas decode codec reel ni promotion de route image complete.", "Preuve WebGPU offscreen avec refus dependency/provenance."),
        doc("sampler-boundary-gate-board", "Exposer les limites sampler autour des fixtures bitmap.", "Valide nearest, linear et lanes de refus tile, mipmap, cubic et anisotropic.", "Ne revendique pas sampler avance, perspective ou decode color-managed.", "Preuve WebGPU offscreen avec refus visibles."),
        doc("bitmap-sampler-matrix", "Comparer une matrice compacte de fixtures bitmap avec deux politiques de sampling.", "Valide un tray rrect clippe et plusieurs BitmapRect nearest ou linear bien bornes.", "Ne revendique pas mipmap, tile mode, anisotropic ni decode color-managed.", "Preuve WebGPU offscreen et Kadre windowed."),
        doc("savelayer-isolation-gate-board", "Documenter les limites saveLayer avant support general.", "Valide lanes d'isolation, bounds, destination-read et refus de layer.", "Ne revendique pas saveLayer general ni DAG image-filter.", "Preuve WebGPU offscreen avec refus produit/route explicites."),
        doc("destination-read-strategy-gate-board", "Rendre visible la strategie destination-read avant blending avance.", "Valide lanes de politique dst-read et blend sans lecture destination active.", "Ne revendique pas framebuffer fetch, dst texture ou blend compose general.", "Preuve WebGPU offscreen avec refus destination-read."),
        doc("layer-filter-chain-board", "Preparer une scene de chainage layer/filter plus ambitieuse.", "Valide des lanes de refus pour DAG, texture intermediaire et destination-read.", "Ne revendique pas saveLayer general, destination-read, intermediate textures ou DAG arbitraire.", "Preuve WebGPU offscreen avec refus layer/filter visibles."),
        doc("layered-shadow-card", "Verifier une carte shadow bornee materialisee par fixture.", "Valide SaveLayer fixture-backed et FilterNode drop-shadow borne.", "Ne revendique pas saveLayer general ni image-filter DAG arbitraire.", "Preuve WebGPU offscreen et Kadre windowed."),
        doc("notification-shadow-stack", "Verifier deux notifications shadow bornees empilees.", "Valide deux SaveLayer fixture-backed et deux FilterNode drop-shadow.", "Ne revendique pas saveLayer general, blur arbitraire ou filtre DAG complet.", "Preuve WebGPU offscreen et Kadre windowed."),
        doc("filtered-photo-chip", "Verifier un chip photo avec filtre luma-tint borne.", "Valide BitmapRect fixture-backed et FilterNode luma-tint.", "Ne revendique pas image-filter DAG ni color filter general.", "Preuve WebGPU offscreen et Kadre windowed."),
        doc("tinted-avatar-card", "Verifier une carte avatar clippee avec teinte luma.", "Valide Clear, FillRRect, Clip, BitmapRect et FilterNode luma-tint.", "Ne revendique pas DAG filtre general ni sampling image avance.", "Preuve WebGPU offscreen et Kadre windowed."),
        doc("filter-dag-refusal-board", "Lister les refus de DAG filtre avant promotion.", "Valide des lanes de refus stables pour familles de filtre.", "Ne revendique pas execution de DAG image-filter ni textures intermediaires.", "Preuve WebGPU offscreen avec refus attendus."),
        doc("receipt-text-run", "Representer un run texte de ticket avec inputs font reels.", "Valide le payload TextRun et les diagnostics de route texte indisponible.", "Ne revendique pas glyph atlas WebGPU ni shaping complet.", "Preuve attendue comme refus stable de route texte."),
        doc("text-handoff-boundary-board", "Exposer les frontieres de handoff texte et artefacts types.", "Valide lanes de font input, shaping, atlas et refus de handoff.", "Ne revendique pas rendu texte GPU ni fallback font complet.", "Preuve WebGPU offscreen avec refus texte visibles."),
        doc("text-resource-binding-gate-board", "Rendre visible les blockers de binding ressource texte.", "Valide lanes upload plan, binding layout, stale generation et budget.", "Ne revendique pas upload glyph atlas ni texture CPU-rendered supportee.", "Preuve WebGPU offscreen avec refus binding texte."),
        doc("a8-glyph-atlas-gate-board", "Documenter les gates d'atlas glyph A8.", "Valide lanes descriptor, page, entry, generation et instance buffer.", "Ne revendique pas route A8 glyph atlas ni ordering upload-before-sample.", "Preuve WebGPU offscreen avec refus atlas explicites."),
        doc("text-representation-gate-board", "Comparer les representations texte avant route GPU.", "Valide lanes de representation, font artefacts et refus de route texte.", "Ne revendique pas shaping multi-script ni rendu glyph GPU.", "Preuve WebGPU offscreen avec refus texte attendus."),
        doc("runtime-effect-color-tile", "Verifier une tuile RuntimeEffect SimpleRT enregistree.", "Valide RuntimeEffectTile registered simple_rt avec uniform gColor.", "Ne revendique pas SkSL dynamique ni runtime effects arbitraires.", "Preuve WebGPU offscreen et Kadre windowed."),
        doc("runtime-effect-descriptor-gate-board", "Rendre visible le contrat descriptor RuntimeEffect.", "Valide SimpleRT et lanes descriptor, WGSL, CPU implementation et uniforms.", "Ne revendique pas SpiralRT ni compilation dynamique de source SkSL.", "Preuve WebGPU offscreen avec diagnostics descriptor."),
        doc("runtime-effect-refusal-gate-board", "Documenter les refus runtime effect attendus par produit.", "Valide refus source arbitraire, child slot et placement non supporte.", "Ne revendique pas children RuntimeEffect ni route shader dynamique.", "Preuve WebGPU offscreen avec refus produit attendus."),
        doc("runtime-effect-uniform-ladder", "Verifier plusieurs tuiles RuntimeEffect SimpleRT avec une echelle de uniforms visible.", "Valide le descriptor SimpleRT, le layout gColor et la stabilite des tuiles runtime fixture-backed.", "Ne revendique pas SkSL dynamique, SpiralRT ou runtime effects enfants.", "Preuve WebGPU offscreen et Kadre windowed."),
        doc("blend-mode-strip", "Garder une scene minimale pour une lane blend simple.", "Valide un FillRect borne dans la famille blend/color actuelle.", "Ne revendique pas modes blend avances ni destination-read.", "Preuve WebGPU offscreen et Kadre windowed."),
        doc("translucent-card-overlap", "Verifier trois cartes alpha SrcOver bornees.", "Valide FillRect avec alpha partiel et ordre de peinture stable.", "Ne revendique pas blend modes arbitraires ni color space large.", "Preuve WebGPU offscreen et Kadre windowed."),
        doc("sdr-color-boundary-board", "Rendre visible les bornes couleur SDR.", "Valide lanes de faits SDR, clamp et refus de color management avance.", "Ne revendique pas wide-gamut, HDR ou transforms color-space complets.", "Preuve WebGPU offscreen avec refus couleur."),
        doc("mesh-ribbon", "Verifier un ruban mesh fixture-backed borne.", "Valide MeshRibbon bounded-ribbon-strip dans le runner actuel.", "Ne revendique pas DrawVertices general ni vertex/index buffer arbitraire.", "Preuve WebGPU offscreen et Kadre windowed."),
        doc("vertices-route-gate-board", "Lister les blockers de route vertices.", "Valide lanes descriptor, primitive blend, buffer upload, ABI et batching.", "Ne revendique pas vertices generaux ni pipeline mesh complet.", "Preuve WebGPU offscreen avec refus vertices."),
        doc("mesh-ribbon-depth-stack", "Verifier plusieurs rubans mesh bornes avec overlaps lisibles dans un cadre simple.", "Valide un stack de MeshRibbon fixture-backed avec ordre visuel stable et clipping borne.", "Ne revendique pas DrawVertices general ni upload libre de vertex/index buffers.", "Preuve WebGPU offscreen et Kadre windowed."),
        doc("cache-pressure-deck", "Representer une pression cache minimale et stable.", "Valide deux rectangles comme deck de pression cache fixture.", "Ne revendique pas telemetry cache runtime observee.", "Preuve WebGPU offscreen et Kadre windowed."),
        doc("cache-frame-budget-strip", "Rendre visible un budget frame/cache depasse comme refus attendu.", "Valide les lanes budget cible, warning, depassement et gate reporting-only.", "Ne revendique pas mesure runtime WebGPU observee ni gate release-blocking.", "Preuve WebGPU offscreen avec refus budget visible."),
        doc("cache-source-ledger-board", "Rendre visible la classification des sources cache.", "Valide lanes de source cache, stale, generated et policy.", "Ne revendique pas cache WebGPU observe ni eviction runtime.", "Preuve WebGPU offscreen avec ledger diagnostic."),
        doc("frame-gate-blocker-board", "Exposer les blockers de frame gate.", "Valide lanes de politique frame, telemetry et gating reporting-only.", "Ne revendique pas gate FPS release-blocking ni mesure native definitive.", "Preuve WebGPU offscreen avec blockers visibles."),
        doc("pm-readiness-freeze-board", "Montrer que la readiness PM ne bouge pas sans gate.", "Valide lanes de policy, release blocking false et readiness delta zero.", "Ne revendique pas activation produit ni mouvement de readiness.", "Preuve WebGPU offscreen avec diagnostics PM."),
        doc("legacy-route-comparison", "Garder une comparaison legacy minimale.", "Valide un proxy rectangulaire pour route legacy courante.", "Ne revendique pas comparaison pixel complete ni retrait legacy.", "Preuve WebGPU offscreen et Kadre windowed."),
        doc("legacy-inventory-hygiene-board", "Rendre visible l'hygiene inventaire legacy.", "Valide lanes inventory, archive, stale rows et cleanup gates.", "Ne revendique pas retrait de route legacy ni migration globale.", "Preuve WebGPU offscreen avec gates legacy."),
        doc("shadow-parity-migration-gate-board", "Documenter les gates de parite shadow avant migration.", "Valide lanes par famille pour parite, evidence et refus de migration.", "Ne revendique pas parite shadow complete ni remplacement accepte.", "Preuve WebGPU offscreen avec gates de parite."),
        doc("legacy-retirement-blocker-board", "Exposer les blockers de retrait legacy.", "Valide lanes replacement, activation decision, rollback et evidence PM.", "Ne revendique pas retirement legacy ni route produit activee.", "Preuve WebGPU offscreen avec blockers de retirement."),
        doc("legacy-parity-snapshot-board", "Verifier une vue de parite legacy lisible avant toute decision de retirement.", "Valide un board de comparaison rrect/rect borne avec lanes de parite, evidence et blockers.", "Ne revendique pas remplacement accepte ni retrait effectif de la route legacy.", "Preuve WebGPU offscreen et Kadre windowed."),
        doc("rounded-rect-solids", "Verifier trois rrects solides avec rayons varies.", "Valide FillRRect avec rayons petits, moyens et grands.", "Ne revendique pas rayons par coin, gradients ou transforms avances.", "Preuve WebGPU offscreen et Kadre windowed."),
        doc("linear-gradient-lanes", "Verifier trois degradees lineaires bornes avec clamp.", "Valide LinearGradientRect horizontal, vertical et diagonal.", "Ne revendique pas repeat, mirror, decal, local matrix ou plus de deux arrets.", "Preuve WebGPU offscreen et Kadre windowed."),
        doc("scissor-overlay", "Verifier un scissor simple avec rectangles bornes.", "Valide Clip device-rect et FillRect ordonnes.", "Ne revendique pas clip rrect, stencil-cover ni clip stack complexe.", "Preuve WebGPU offscreen et Kadre windowed."),
        doc("radial-swatch", "Verifier trois degradees radiales avec centres et rayons varies.", "Valide RadialGradientRect avec centres decales et rayons differs.", "Ne revendique pas repeat, mirror, decal, plus de deux arrets ou transforms.", "Preuve WebGPU offscreen et Kadre windowed."),
        doc("sweep-disk", "Verifier trois degradees angulaires avec angles de depart/fin varies.", "Valide SweepGradientRect avec sweeps 360, 180 et 90 degres.", "Ne revendique pas repeat, mirror, decal, plus de deux arrets ou transforms.", "Preuve WebGPU offscreen et Kadre windowed."),
    )

    val candidateScenes: List<CandidateScene> = listOf(
        candidate(
            id = "simple-latin-glyph-atlas-strip",
            title = "Simple Latin Glyph Atlas Strip",
            milestones = listOf("M6"),
            tags = setOf(SceneTag.Text, SceneTag.Cache),
            status = CandidateSceneStatus.DependencyGated,
            intention = "Preparer une scene texte simple-latin adossee a un atlas glyph reel.",
            validationTarget = "Valider glyph masks, atlas entries et binding ressource quand la dependance existe.",
            nonClaims = "Ne revendique pas shaping complexe, font fallback ou emoji/color fonts.",
            rationale = "Couvre M6 sans ajouter de substitut court-terme pour font/atlas.",
        ),
    )

    private fun doc(
        sceneId: String,
        intention: String,
        validates: String,
        nonClaims: String,
        evidence: String,
    ): SceneHumanDocs =
        SceneHumanDocs(
            sceneId = SceneId(sceneId),
            french = LocalizedSceneText(
                intention = intention,
                validates = validates,
                nonClaims = nonClaims,
                evidence = evidence,
            ),
        )

    private fun candidate(
        id: String,
        title: String,
        milestones: List<String>,
        tags: Set<SceneTag>,
        status: CandidateSceneStatus,
        intention: String,
        validationTarget: String,
        nonClaims: String,
        rationale: String,
    ): CandidateScene =
        CandidateScene(
            sceneId = SceneId(id),
            title = title,
            roadmapLinks = milestones.map { SceneRoadmapLink.milestone(it) },
            tags = tags,
            status = status,
            french = CandidateSceneFrenchText(
                intention = intention,
                validationTarget = validationTarget,
                nonClaims = nonClaims,
                rationale = rationale,
            ),
        )

    fun validateAgainst(scenes: List<GPURendererScene<*>>): List<String> =
        validateSceneHumanDocumentation(
            scenes = scenes,
            docs = docs,
            candidateScenes = candidateScenes,
        )
}
