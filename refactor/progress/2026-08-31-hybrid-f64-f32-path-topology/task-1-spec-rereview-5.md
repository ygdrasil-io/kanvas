# Task 1 — Final Sol spec re-review, fix round 5

Fix base : `73fa30440`  
Head : `4e336bcfa`

## Verdicts

- **Joints collinéaires de subdivision** — **ADDRESSED** — les joints contigus du même segment sont traités avant le kernel et ne créent ni cut exact ni witness (`PathIntersectionsF64.kt:262`, `PathIntersectionsF64.kt:441`, `PathSourceTopologyF64.kt:224`).
- **Registre point n-way / overlap exact complet** — **NOT ADDRESSED** — les overlaps restent accumulés pairwise, leurs identités sont retrouvées par scan paramétrique, et les intervalles inclusifs ajoutent des spans seulement tangents aux bornes (`PathIntersectionsF64.kt:276`, `PathIntersectionsF64.kt:1247`, `PathSourceTopologyF64.kt:320`, `PathSourceTopologyF64.kt:382-420`).
- **Spans/sections autoritaires, sans side channel/chord** — **ADDRESSED** (`PathOpsF32.kt:189`, `PathSourceTopologyF64.kt:544`, `PathArrangementF64.kt:188`, `PathArrangementF64.kt:699`).
- **Compactor/chord/fallback supprimés ; décisions explicites** — **ADDRESSED** (`PathOpsF32.kt:93`, `PathOpsF32.kt:286-328`, `PathOpsF32.kt:778`).
- **Budget partagé et travail borné** — **NOT ADDRESSED** — arrangement, projection initiale, tri final et writer ne sont pas tous débités; le ledger rescane les claims et reste quadratique (`PathOpsF32.kt:129-151`, `PathOpsF32.kt:189`, `PathOpsF32.kt:279-327`, `PathIntersectionsF64.kt:563`).
- **IDs sémantiques indépendants et non tie-breakers** — **NOT ADDRESSED** — des spans égaux partagent un ID, des raw edge IDs subsistent dans l'ordre, et des IDs départagent des directions égales (`PathSourceTopologyF64.kt:260-279`, `PathArrangementF64.kt:735`, `PathArrangementF64.kt:824`).
- **Namespaces endpoints/joints internes disjoints** — **ADDRESSED** (`PathIntersectionsF64.kt:9`, `PathSourceTopologyF64.kt:565`, `PathSourceTopologyF64.kt:691`).
- **Witnesses autoritaires et tests sensibles à leur neutralisation** — **NOT ADDRESSED** — les tests décisifs reconstruisent topology/adapter/contour directement et contournent `PathArrangementF64.boundary()` (`PathOpsHybridTopologyF32Test.kt:175-245`).
- **Point/Overlap et claims atomiques** — **NOT ADDRESSED** — des overlaps distincts peuvent partager un witness ID; les claims ne portent pas l'identité exacte de leur endpoint commun (`PathOpsF32.kt:115-168`, `PathOpsF32.kt:499`, `PathSourceTopologyF64.kt:423-508`).
- **Cinq rejets tangents ciblés** — **ADDRESSED** — seuls les slots concernés changent et les autres opérations restent des succès (`PathOpsF32Test.kt:67-148`, `PathOpsF32Test.kt:1123`).
- **Tests uniquement comportementaux/géométriques/numériques** — **NOT ADDRESSED** — les nouveaux helpers inspectent/reconstruisent input edges, split edges, provenance et collections de topology (`PathOpsHybridTopologyF32Test.kt:175-229`, `PathOpsF32Test.kt:595`).
- **Aucun font/codec/GM** — **ADDRESSED**.

## Nouvelles ruptures

- **Critical — PointF64 non ancré.** Un witness attaché à un span est copié sur toutes ses sections; la preuve ne vérifie pas que le contact F32 est l'image projetée du witness. Un crossing F32 distant peut être accepté (`PathSourceTopologyF64.kt:573`, `PathOpsF32.kt:506`, `PathOpsF32.kt:565`).
- **Important — collisions de provenance.** Des spans distincts mais sémantiquement égaux peuvent partager l'ID utilisé comme clé/scope (`PathSourceTopologyF64.kt:269`, `PathSourceTopologyF64.kt:470`, `PathSourceTopologyF64.kt:694`).
- **Important — endpoint exact absent des claims.** Deux claims se touchant au même paramètre sont acceptées même si leurs identités exactes diffèrent (`PathOpsF32.kt:115`, `PathOpsF32.kt:157`).

## Verdict

- `Spec fix round 5: FAIL`

