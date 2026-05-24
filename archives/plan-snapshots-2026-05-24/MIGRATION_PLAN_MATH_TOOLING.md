# Plan vivant — Outillage qualité du module `:math`

Ce plan suit les chantiers de **traçabilité, doc et qualité** sur le module `:math` (Kotlin, `org.graphiks.math`). Il a démarré avec la map de correspondance Kotlin ↔ Skia upstream, puis s'est étendu à la doc API et à l'audit d'alignement.

## État d'avancement

| Chantier | PR | Status |
|---|---|---|
| **Map-1** : Spec format TSV `.upstream/source/map/` + helpers shell | [#492](https://github.com/ygdrasil-io/kanvas/pull/492) | ✅ mergé |
| **Map-2** : Backfill TSVs `:math` (20 main + 11 tests, 774 lignes, 658 + 116 entrées) | [#493](https://github.com/ygdrasil-io/kanvas/pull/493) | ✅ mergé |
| **Math rename** : `org.skia.math` → `org.graphiks.math` (917 fichiers) | [#490](https://github.com/ygdrasil-io/kanvas/pull/490) | ✅ mergé |
| **Dokka init** : Dokka 2.2.0 GFM + MkDocs Material + workflow Pages | [#500](https://github.com/ygdrasil-io/kanvas/pull/500) | ✅ mergé |
| **Dokka post-process** : Cleanup breadcrumbs/jvm/sigs/lists | [#504](https://github.com/ygdrasil-io/kanvas/pull/504) | ✅ mergé |
| **Dokka nav grouping** : awesome-pages + 7 familles + titres class corrigés | [#509](https://github.com/ygdrasil-io/kanvas/pull/509) | ✅ mergé |
| **Audit Kotlin↔Skia** : 4 agents parallèles, 21 rapports, 23 divergences identifiées | [#511](https://github.com/ygdrasil-io/kanvas/pull/511) | 🔄 ouvert |
| **Fix #1+#7** : SkIRect arithmétique saturante + isEmpty overflow | [#520](https://github.com/ygdrasil-io/kanvas/pull/520) | 🔄 ouvert |
| **Fix #2** : SkColor4f byte order RGBA en mémoire | [#521](https://github.com/ygdrasil-io/kanvas/pull/521) | 🔄 ouvert |
| **Fix #3** : SkV3.operator*(SkV3) componentwise (BREAKING) | [#522](https://github.com/ygdrasil-io/kanvas/pull/522) | 🔄 ouvert |
| **Fix #4+#5** : SkMatrix.mapVectors + SkM44.mapRect perspective | [#523](https://github.com/ygdrasil-io/kanvas/pull/523) | 🔄 ouvert |
| **Fix #6** : SkScalarRound utilise floor(x+0.5) | [#519](https://github.com/ygdrasil-io/kanvas/pull/519) | 🔄 ouvert |
| **L5a** : audit `:math` zero-MISSING + CI lint (`_ignore.txt` whitelist, override Any auto-exclusion) | en cours | 🔄 ouvert |

**14 PRs ouvertes/mergées sur la branche outillage `:math`.** 7 correctness-blockers de l'audit corrigés, 5 fix PRs prêtes à merger (tous full `:skia-integration-tests` verts à 585 PASSED / 0 FAILED).

## Architecture des artefacts

```
.upstream/source/map/                            # TSV map Kotlin ↔ C++
├── README.md                                    # spec + 7 query patterns
├── _resolve_url.sh                              # path:line → URL GitHub
├── audit.sh                                     # python ; symboles publics sans TSV
└── math/
    ├── SkPoint.tsv … SkPathOpsTypes.tsv         # 20 main TSVs (671 lignes)
    └── SkPointTest.tsv … SkV4Test.tsv           # 11 tests TSVs (103 lignes)

docs/                                            # Site doc MkDocs
├── index.md                                     # landing
├── README.md                                    # guide local + roadmap
├── scripts/postprocess_dokka_gfm.py             # Dokka GFM → MkDocs-friendly
└── api/<module>/<package>/                      # GFM Dokka généré (gitignored)
mkdocs.yml                                       # MkDocs Material + awesome-pages

archives/audit/math/                             # Rapports d'audit (PR #511)
├── README.md                                    # consolidation + tables
└── Sk*.md                                       # 21 rapports par source Kotlin

.github/workflows/docs.yml                       # CI : Dokka → MkDocs → Pages
```

## Reste à faire

### Court terme (en attente de merge)

- Merger les 6 PRs ouvertes (#511 audit + 5 fix PRs)
- Setup repo : Settings → Pages → Source = "GitHub Actions" pour activer la première deploy

### Moyen terme — 4 divergences à impact moindre

Identifiées dans l'audit mais non encore fixées (impact faible, edge cases) :

1. `SkPreMultiplyARGB` — formule d'arrondi ±1 LSB
2. `SkHSVToColor` — modulo wrap vs clip pour h≥360
3. `MakeRectToRect` — NaN/Inf signe différent (edge case `src.isEmpty`)
4. `SkV3.normalize()` — zero vs NaN sur vecteur nul

Plus **13 cas "à vérifier"** dans les rapports d'audit pour review humaine.

### Long terme — extension multi-modules

Étendre la combo {TSV map + Dokka + audit} aux autres modules :

| Module | TSV map | Dokka | Audit |
|---|---|---|---|
| `:math` | ✅ Map-2 | ✅ Dokka | ✅ Audit |
| `:kanvas-skia` | 📋 Map-3 (foundation/, core/) | 📋 | 📋 |
| `:cpu-raster` | 📋 Map-4..6 (pathops, codec, effects, ...) | 📋 | 📋 |
| `:gpu-raster` | 📋 | 📋 (peut attendre G2-G3 GPU) | 📋 |
| `:skia-integration-tests` | 📋 Map-7 (~461 GMs scriptable) | n/a | n/a |

Estimation : 5-10 jours répartis sur 6-8 PRs pour porter le tooling à `:kanvas-skia` + `:cpu-raster`.

### Améliorations tooling

- `mkdocs-awesome-nav` (succession à awesome-pages) si la nav devient touffue
- `externalDocumentationLink` Dokka pour résoudre les KDocs croisés (ex. `[SkShader]` dans `SkMatrix.invert`)
- Migrer en Dokka V2 quand GFM/Jekyll natif débarque (V1 sera removed en 2.3+)
- ✅ **Lint CI** : `audit.sh math` exécuté dans `.github/workflows/test.yml`
  (job `raster`), fail si MISSING > 0. Whitelist Kotlin-original via
  `.upstream/source/map/<module>/_ignore.txt` + auto-exclusion des
  overrides `equals/hashCode/toString`.
- Re-running audit après chaque resync upstream Skia (détection de drift)

## Fichiers critiques

- [`CLAUDE.md`](CLAUDE.md) — pointer vers tous les artefacts (map, doc, audit)
- [`.upstream/source/map/README.md`](.upstream/source/map/README.md) — spec TSV
- [`docs/README.md`](docs/README.md) — guide MkDocs
- `archives/audit/math/README.md` (introduit par #511) — synthèse audit
- [`mkdocs.yml`](mkdocs.yml) — site config

## Métriques actuelles

- **`:math` couverture map** : 774 entrées TSV sur 31 sources Kotlin
- **`:math` alignement Skia** : 94 % (612/650 symboles), 23 divergences identifiées, 7 corrigées
- **Doc API** : 733 fichiers GFM générés, rendu MkDocs Material avec 7 familles thématiques
- **Tests** : 144 tests `:math` + 585 GMs `:skia-integration-tests` — 0 régression sur toutes les fix PRs
