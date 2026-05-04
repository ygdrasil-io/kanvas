# Plan #3 — Portage complet de la pipeline color space Skia

> Plan de complétion. Les divergences listées dans l'analyse `kanvas-skia ↔ skia-main` (cf. PR #6 review thread) sont consolidées en phases livrables. Chaque phase = un commit / une PR contre `from-skia`. Branche : `claude/colorspace-completion`. Worktree : `/Users/chaos/worktree/kanvas/colorspace-completion`.

## Contexte

[archives/MIGRATION_PLAN_COLORSPACE.md](archives/MIGRATION_PLAN_COLORSPACE.md) Phase 0-5 ✅ a livré :
- skcms minimal (TF eval/invert + matrix concat/invert) — sRGBish only.
- `SkColorSpace` minimal (singletons, MakeRGB, Equals, hash).
- `SkColorSpaceXformSteps` (apply scalaire) — sans OOTF.
- Wiring dans `SkBitmap` + `SkBitmapDevice` + `TestUtils`.
- 5 GMs Phase 1-3a passent à `tolerance=1`.

Reste pour la *vraie* parité avec Skia (cf. analyse de divergence) :

1. **HDR** — PQ, HLG, PQish, HLGish, HLGinvish dans skcms (classify, eval, invert) + branches OOTF dans `XformSteps`.
2. **ICC parsing** — `skcms_Parse` (header v2/v4, tags rXYZ/gXYZ/bXYZ/wtpt, paramètric et table TRCs, A2B/B2A LUTs, CICP), `SkColorSpace.Make(profile)`.
3. **CICP** — `SkColorSpacePrimaries`, `SkNamedPrimaries::*`, `SkNamedTransferFn::CicpId`, `MakeCICP`.
4. **Sérialisation** — `serialize` / `Deserialize` / `writeToMemory` (16 floats + version header).
5. **Modifiers** — `makeLinearGamma`, `makeSRGBGamma`, `makeColorSpin`.
6. **Hash bit-compat** — `SkChecksum::Hash32` (Mum-style).
7. **Snap dans `MakeRGB`** — `is_almost_srgb` / `is_almost_2dot2` / `is_almost_linear`, `xyz_almost_equal`.
8. **Optimisations `SkColorSpaceXformSteps`** — early-return src==dst, linearize/encode skip-if-linear, unpremul+premul cancel, `dstAT==Opaque → srcAT` hint.
9. **Constantes nommées manquantes** — kRec709, kRec601, kSMPTE_*, kPQ, kHLG, kIEC*, kProPhotoRGB, kA98RGB ; SkNamedPrimaries::kRec709/kRec2020/etc.
10. **Précision `kRec2020`** — décider : matcher Skia (6 décimales) ou garder précision PNG ICC (snap nécessaire pour rester equal au singleton).
11. **`apply(SkRasterPipeline*)`** — différé (hors scope colorspace, dépend du raster pipeline JIT).

## Trois décisions architecturales préalables

1. **Garder le port à la main.** Pas d'activation des fichiers `kanvas/src/generated/skcms/`. Trop de stubs (`Functions.kt` 154 KB), package `undefined.*` à résoudre. On porte chaque fonction depuis `modules/skcms/skcms.cc` upstream à la main, en gardant le commentaire C++ d'origine en KDoc comme spec.
2. **Hash bit-compat = optionnel.** `SkChecksum::Hash32` est utilisé par Skia pour comparer rapidement deux `SkColorSpace`. Notre FNV-1a est correct intra-impl mais incompatible si on cherche à matcher des hashs externes (cache disque, sérialisation, PRDeserialize qui suppose hash stable). Phase H (priorité basse) — uniquement si un test concret le réclame.
3. **HDR avant ou après ICC ?** ICC parsing est requis pour lire des PNGs avec profils non-Rec.2020 (à venir : `BitmapRectGM`, `EncodeGM`). HDR est requis pour les GMs PQ/HLG (peu nombreux, plus tard). **Ordre retenu** : ICC d'abord (Phase F), HDR après (Phase I).

## Trajectoire — 11 phases ordonnées

| Phase | Slice | Effort | Bloque quoi ? |
|-------|-------|--------|----------------|
| A | Optimisations XformSteps | XS | Drift float résiduel ; cycles gâchés |
| B | `is_almost_*` snap dans `MakeRGB` | XS | `Equals`/singleton-snap pour ICC parse |
| C | Modifiers (`makeLinearGamma` etc.) | XS | Symétrie API |
| D | Constantes nommées TF + Gamut | S | CICP table, ICC parse fallback |
| E | CICP infra (`SkColorSpacePrimaries`, `MakeCICP`) | M | ICC parse path |
| F | ICC parsing (`skcms_Parse`, `Make(profile)`) | **L** | Lecture PNG iCCP arbitraire |
| G | Sérialisation (`serialize`/`Deserialize`/`writeToMemory`) | S | Cache/persistance |
| H | Hash bit-compat (`SkChecksum::Hash32`) | XS | Interop hash externe (rarement utile) |
| I | HDR (PQ + HLG dans classify/eval/invert + OOTF dans XformSteps) | **L** | GMs HDR (kHDR_blue, etc.) |
| J | Précision/snap finalisation `kRec2020` | XS | Cohérence test/ratchet |
| K | `apply(SkRasterPipeline*)` | XL | **Différé** — dépend du raster pipeline JIT, hors scope colorspace |

XS = ~30 lignes. S = ~200 lignes. M = ~500 lignes. L = ~1500 lignes. XL = >5000.

---

## Phase A — Optimisations `SkColorSpaceXformSteps` (XS) — ✅

Modifié [kanvas-skia/src/main/kotlin/org/skia/core/SkColorSpaceXformSteps.kt](kanvas-skia/src/main/kotlin/org/skia/core/SkColorSpaceXformSteps.kt) :

- [x] **Opt 1 : hint `dstAT==Opaque → srcAT`** — Skia `SkColorSpaceXformSteps.cpp:45-47`.
- [x] **Opt 2 : early-return `src==dst`** — `:54-57`. Tous flags `false` par défaut quand hash et alpha identiques.
- [x] **Opt 3 : `linearize`/`encode` conditionnels TF** — `:99-104`, `:129-134`. `linearize = (srcTrfn != kLinear)`, `encode = (dstTrfn != kLinear)`. Économise 6 appels `pow` identité par pixel dans le cas Linear↔Linear.
- [x] **Opt 4a : linearize+encode même TF** — `:181-200`. Cancel quand seul l'alpha change (round-trip TF identité).
- [x] **Opt 4b : unpremul+premul cancel** — `:202-210`. Cancel quand `linearize` et `encode` sont off (élimine le drift float du round-trip α).

**Tests** [kanvas-skia/src/test/kotlin/org/skia/core/SkColorSpaceXformStepsOptTest.kt](kanvas-skia/src/test/kotlin/org/skia/core/SkColorSpaceXformStepsOptTest.kt) — 11 nouveaux tests :
- [x] Opt 1 : `Premul + Opaque-dst` → identity (via re-écriture en Premul→Premul → Opt 2).
- [x] Opt 2 : `same CS + same alpha` (3 cas Opaque/Premul/Unpremul) → identity.
- [x] Opt 2 : `same CS + alpha differ` → seul unpremul OU premul fire.
- [x] Opt 3 : `Linear → Linear different gamut` → linearize=encode=false, gamut=true.
- [x] Opt 3 : `sRGB → Linear different gamut` → linearize=true, encode=false.
- [x] Opt 3 : `Linear → sRGB different gamut` → linearize=false, encode=true.
- [x] Opt 4a : `same TF same gamut + alpha differ` (sRGB Premul → sRGB Unpremul) → linearize=encode=false, seul unpremul fire.
- [x] Opt 4b : `Linear → Linear different gamut + Premul→Premul` → unpremul=premul=false (gamut transform reste).
- [x] **Bit-stable α≠1** : `sRGB Premul → sRGB Premul` à α∈{0.5, 0.25, 0.75, 0.001, 1} = identité bit-à-bit.
- [x] α preservation `Linear Premul → Linear-different-gamut Premul`.
- [x] Régression : `(43, 13, 241)` toujours produit pour sRGB pur bleu → Rec.2020.

**Test pré-existant à corriger** : [SkColorSpaceXformStepsTest.kt:39](kanvas-skia/src/test/kotlin/org/skia/core/SkColorSpaceXformStepsTest.kt:39) asserait `encode=true` pour sRGB → sRGB-linear. Avec Opt 3, `encode=false` (dst est linear). Test mis à jour.

**Résultat** : 168 tests verts (157 existants + 11 nouveaux), 0 régression. Scores GMs identiques (BigRect 95.53%, SimpleRect/ClipStrokeRect/DrawBitmapRect3 100%, ConcavePaths 98.86%, ThinRects 92.10%).

---

## Phase B — Snap quasi-sRGB dans `MakeRGB` (XS) — ✅

Créé [kanvas-skia/src/main/kotlin/org/skia/foundation/SkColorSpacePriv.kt](kanvas-skia/src/main/kotlin/org/skia/foundation/SkColorSpacePriv.kt) :

- [x] `colorSpaceAlmostEqual` — tolérance `0.01f` pour matrices.
- [x] `transferFnAlmostEqual` — tolérance `0.001f` pour TFs.
- [x] `xyzAlmostEqual(mA, mB)` — cell-by-cell.
- [x] `isAlmostSRGB(tf)` — 7 comparaisons.
- [x] `isAlmost2Dot2(tf)` — `a=1, b=0, e=0, g≈2.2, d≤0`.
- [x] `isAlmostLinear(tf)` — deux formes (exponentielle `g≈1, d≤0` OU linéaire `c≈1, d≥1`).
- [x] **`SkColorSpace.makeRGB` rewired** : snap par `isAlmost*` + `xyzAlmostEqual` au lieu d'égalité exacte. Mirror `SkColorSpace.cpp:136-159`.

**Tests** [kanvas-skia/src/test/kotlin/org/skia/foundation/SkColorSpacePrivTest.kt](kanvas-skia/src/test/kotlin/org/skia/foundation/SkColorSpacePrivTest.kt) — 12 nouveaux tests :
- [x] `colorSpaceAlmostEqual` accepte 0.005, refuse 0.02.
- [x] `isAlmostSRGB` accepte exact, accepte perturbé `0.0005`, refuse `kLinear`.
- [x] `isAlmost2Dot2` accepte exact, refuse `2.4`-power.
- [x] `isAlmostLinear` accepte les 2 formes, refuse `kSRGB`/`k2Dot2`.
- [x] `makeRGB(kSRGB-perturbé, kSRGB-gamut-perturbé) === makeSRGB()` (singleton snap).
- [x] `makeRGB(kLinear-perturbé, kSRGB-gamut) === makeSRGBLinear()`.
- [x] `makeRGB(kSRGB-perturbé, kRec2020-gamut)` produit instance fraîche dont `gammaCloseToSRGB() == true`.
- [x] `makeRGB(kSRGB+0.005, kSRGB-gamut)` (TF tolérance 0.001 dépassée) ne snap pas.
- [x] **Régression** : 5 GMs gardent leurs scores (BigRect 95.53%, etc.).

**Résultat** : 180 tests verts (168 + 12), 0 régression. Le snap fonctionne — un TF/gamut bruité par s15Fixed16 (ce que produira le parser ICC en Phase F) est désormais snappé sur le singleton.

---

## Phase C — Modifiers `makeLinearGamma` / `makeSRGBGamma` / `makeColorSpin` (XS) — ✅

Ajouté à [kanvas-skia/src/main/kotlin/org/skia/foundation/SkColorSpace.kt](kanvas-skia/src/main/kotlin/org/skia/foundation/SkColorSpace.kt) :

- [x] `makeLinearGamma()`: short-circuit si déjà linéaire, sinon `makeRGB(kLinear, toXYZD50)` (qui re-snap au singleton si gamut == kSRGB grâce à Phase B).
- [x] `makeSRGBGamma()`: symétrique sur `kSRGB`.
- [x] `makeColorSpin()`: `concat(toXYZD50, spin)` avec `spin = {{0,0,1},{1,0,0},{0,1,0}}`.

**Tests** [kanvas-skia/src/test/kotlin/org/skia/foundation/SkColorSpaceModifiersTest.kt](kanvas-skia/src/test/kotlin/org/skia/foundation/SkColorSpaceModifiersTest.kt) — 9 nouveaux tests :
- [x] `makeSRGB().makeLinearGamma() === makeSRGBLinear()` (snap singleton via Phase B).
- [x] `makeSRGBLinear().makeLinearGamma() === makeSRGBLinear()` (idempotent).
- [x] `Rec2020.makeLinearGamma()` garde le gamut, TF → linéaire.
- [x] Symétrique pour `makeSRGBGamma`.
- [x] `makeColorSpin()` 3× retourne au matrix-équivalent original (spin³ = identité).
- [x] `makeColorSpin()` 1× ≠ original (`Equals` retourne false).
- [x] `makeColorSpin().toXYZD50` permute les colonnes : col0=col1_orig, col1=col2_orig, col2=col0_orig.

**Résultat** : 189 tests verts (180 + 9), 0 régression.

---

## Phase D — Constantes nommées complètes (S) — ✅

### `SkNamedTransferFn` — ajouts
- [x] `kRec709`, `kRec470SystemM`, `kRec470SystemBG`, `kRec601` (= kRec709), `kSMPTE_ST_240`.
- [x] `kIEC61966_2_4` (= kRec709), `kIEC61966_2_1` (= kSRGB), `kRec2020_10bit` / `kRec2020_12bit` (= kRec709), `kSMPTE_ST_428_1`.
- [x] `kPQ`, `kHLG` (sentinelles HDR — classify=Invalid jusqu'à Phase I).
- [x] `kProPhotoRGB`, `kA98RGB` (= k2Dot2).
- [x] `CicpId` enum (ITU-T H.273 Table 3) avec `kSRGB` alias et `kCicpIdApplicationDefined = 2`.

### `SkColorSpacePrimaries` + `SkNamedPrimaries` — nouveau
- [x] `SkColorSpacePrimaries` data class avec 4 paires xy (R, G, B, white point) dans [kanvas-skia/src/main/kotlin/org/skia/foundation/SkColorSpacePrimaries.kt](kanvas-skia/src/main/kotlin/org/skia/foundation/SkColorSpacePrimaries.kt). `toXYZD50()` → `NotImplementedError` jusqu'à Phase E.
- [x] [kanvas-skia/src/main/kotlin/org/skia/foundation/SkNamedPrimaries.kt](kanvas-skia/src/main/kotlin/org/skia/foundation/SkNamedPrimaries.kt) avec 11 constantes + `CicpId` enum (ITU-T H.273 Table 2) + `kCicpIdApplicationDefined = 2`.

### `SkNamedGamut` — déjà complet
Le port précédent a déjà `kSRGB`, `kAdobeRGB`, `kDisplayP3`, `kRec2020`, `kXYZ`. Précision `kRec2020` à finaliser en Phase J.

**Tests** — 14 nouveaux :
- [x] [SkNamedTransferFnConstantsTest](kanvas-skia/src/test/kotlin/org/skia/skcms/SkNamedTransferFnConstantsTest.kt) — 8 tests : classify sRGBish pour 16 TFs, classify Invalid pour PQ/HLG, alias identités, valeurs CicpId, kSRGB=kIEC61966_2_1, eval kRec709(0.5)≈0.1895^2.4, round-trip kSMPTE_ST_240.
- [x] [SkNamedPrimariesTest](kanvas-skia/src/test/kotlin/org/skia/foundation/SkNamedPrimariesTest.kt) — 6 tests : valeurs xy canoniques pour kRec709/kRec2020, alias `kSMPTE_ST_240 === kRec601`, valeurs CicpId, `kCicpIdApplicationDefined = 2`, `toXYZD50()` throws (Phase E).

**Résultat** : 203 tests verts (189 + 14), 0 régression.

---

## Phase E — Infrastructure CICP + `MakeCICP` (M) — ✅

### Opérations skcms ([Skcms.kt](kanvas-skia/src/main/kotlin/org/skia/skcms/Skcms.kt))
- [x] `skcmsMv3Mul(m, v)` — 3x3 · 3-vector.
- [x] `skcmsAdaptToXYZD50(wx, wy)` — Bradford adaptation, ~30 lignes incluant matrices Bradford. Mirror `skcms.cc:1826-1865`.
- [x] `skcmsPrimariesToXYZD50(rx, ry, gx, gy, bx, by, wx, wy)` — primaires + WP → toXYZD50 matrix. Mirror `skcms.cc:1867-1909`.

### Lookup tables CICP
- [x] [SkNamedPrimaries.kt](kanvas-skia/src/main/kotlin/org/skia/foundation/SkNamedPrimaries.kt) ajoute `getCicp(primaries: CicpId)` et `getCicpFromMatrix(m)` — table de 11 entrées avec fast-path pour `kRec709 → kSRGB-gamut`, `kRec2020 → kRec2020-gamut`, `kSMPTE_EG_432_1 → kDisplayP3-gamut` (renvoie l'instance singleton, pas un re-calcul). Mirror `SkColorSpace.cpp:30-82`.
- [x] [SkNamedTransferFn.kt](kanvas-skia/src/main/kotlin/org/skia/skcms/SkNamedTransferFn.kt) ajoute `getCicp(id: CicpId)` — table de 13 entrées. Mirror `SkColorSpace.cpp:112-120`.

### `SkColorSpace.makeCICP` ([SkColorSpace.kt](kanvas-skia/src/main/kotlin/org/skia/foundation/SkColorSpace.kt))
- [x] Combine `SkNamedTransferFn.getCicp` + `SkNamedPrimaries.getCicp` + `makeRGB`. Mirror `SkColorSpace.cpp:161-174`. Retourne `null` si une des lookups échoue (typique : PQ/HLG TF, qui sont Invalid jusqu'à Phase I).

### `SkColorSpacePrimaries.toXYZD50` activé
Le stub Phase D (`NotImplementedError`) est remplacé par `skcmsPrimariesToXYZD50` réel.

**Tests** — 22 nouveaux :
- [x] [SkcmsPrimariesTest](kanvas-skia/src/test/kotlin/org/skia/skcms/SkcmsPrimariesTest.kt) — 8 tests : `mv3Mul` identity et reject vector-non-3, `adaptToXYZD50` D50→identity et D65→non-identity, primariesToXYZD50 sRGB→~kSRGB-gamut et Rec.2020→~kRec2020-gamut (tolérance 0.01), reject inputs out-of-range.
- [x] [SkColorSpaceCicpTest](kanvas-skia/src/test/kotlin/org/skia/foundation/SkColorSpaceCicpTest.kt) — 13 tests : `SkColorSpacePrimaries.toXYZD50()` actif, `getCicp` fast-paths (kRec709→kSRGB, kRec2020→kRec2020, kSMPTE_EG_432_1→kDisplayP3), `getCicp` non-fast-path, `getCicpFromMatrix` recover, `getCicpFromMatrix` returns null pour matrice random, `getCicp` TF (kSRGB, kLinear, kPQ), **`makeCICP(kRec709, kIEC61966_2_1)` snap au sRGB singleton** (preuve que la chaîne CICP→TF/gamut→makeRGB→singleton snap fonctionne), `makeCICP(kRec2020, kRec2020_10bit)`, `makeCICP(kRec2020, kPQ)` retourne null (PQ Invalid jusqu'à Phase I).
- [x] [SkNamedPrimariesTest](kanvas-skia/src/test/kotlin/org/skia/foundation/SkNamedPrimariesTest.kt) — `toXYZD50` test mis à jour : retourne maintenant un matrix valide (au lieu de `NotImplementedError`).

**Résultat** : 225 tests verts (203 + 22), 0 régression sur GMs.

---

## Phase F — ICC parsing (L)

**But** : `SkColorSpace.Make(skcms_ICCProfile)` peut consommer un profil ICC parsé.

C'est la grosse pièce. ~1500 lignes C++ dans `skcms.cc:600-1500` à porter.

### Étape F1 — Types `skcms_ICCProfile` (et amis) — ✅

Créés sous [kanvas-skia/src/main/kotlin/org/skia/skcms/](kanvas-skia/src/main/kotlin/org/skia/skcms/) :

- [x] [SkcmsSignature.kt](kanvas-skia/src/main/kotlin/org/skia/skcms/SkcmsSignature.kt) — enum 25 entrées (RGB/CMYK/Gray/XYZ/Lab/CIELUV/HSV/2CLR..15CLR) + `fromValue(Int)` lookup.
- [x] [SkcmsCICP.kt](kanvas-skia/src/main/kotlin/org/skia/skcms/SkcmsCICP.kt) — data class 4 fields (colorPrimaries, transferCharacteristics, matrixCoefficients, videoFullRangeFlag).
- [x] [SkcmsCurve.kt](kanvas-skia/src/main/kotlin/org/skia/skcms/SkcmsCurve.kt) — sealed class avec `Parametric(SkcmsTransferFunction)` et `Table(tableEntries, table8, table16)`. Validations stricts (XOR exclusif sur table8/table16, tableEntries > 0). `equals`/`hashCode` content-based.
- [x] [SkcmsMatrix3x4.kt](kanvas-skia/src/main/kotlin/org/skia/skcms/SkcmsMatrix3x4.kt) — pour les ICC v4 A2B/B2A "M" matrices.
- [x] [SkcmsA2B.kt](kanvas-skia/src/main/kotlin/org/skia/skcms/SkcmsA2B.kt) + [SkcmsB2A.kt](kanvas-skia/src/main/kotlin/org/skia/skcms/SkcmsB2A.kt) — data shape complete, `EMPTY` sentinel. Equals/hash throw (Phase F4).
- [x] [SkcmsICCProfile.kt](kanvas-skia/src/main/kotlin/org/skia/skcms/SkcmsICCProfile.kt) — toutes les fields upstream avec defaults pour `has*` flags.

**Tests** [SkcmsTypesTest](kanvas-skia/src/test/kotlin/org/skia/skcms/SkcmsTypesTest.kt) — 8 nouveaux : valeurs ASCII bigendian de SkcmsSignature, fromValue lookup, SkcmsCICP construction, SkcmsCurve.Parametric/Table validations, SkcmsCurve.Table content-equality, SkcmsMatrix3x4 equals + dimension check, SkcmsICCProfile defaults all-flags-false.

**Résultat** : 245 tests verts (237 + 8), 0 régression.

### Étape F2 — `skcms_Parse` (header v2/v4) — ✅

[kanvas-skia/src/main/kotlin/org/skia/skcms/SkcmsParse.kt](kanvas-skia/src/main/kotlin/org/skia/skcms/SkcmsParse.kt) (~280 lignes) :

- [x] Helpers `readBigU16` / `readBigU32` / `readBigFixed` (s15.16 fixed-point).
- [x] Validation header 132 octets : `'acsp'` signature, taille ≤ buffer, version ≤ 4, illuminant ~D50.
- [x] Walk tag table (`tag_count × 12` octets) avec validation offset+size.
- [x] [SkcmsICCTag.kt](kanvas-skia/src/main/kotlin/org/skia/skcms/SkcmsICCTag.kt) data class.
- [x] `'rXYZ'` / `'gXYZ'` / `'bXYZ'` (XYZ type) → matrice `toXYZD50` row-major.
- [x] `'kTRC'` (Gray) / `'rTRC'` / `'gTRC'` / `'bTRC'` :
  - [x] type=`'para'` : 5 variantes function_type (kG/kGAB/kGABC/kGABCD/kGABCDEF), valide via `classify == sRGBish`.
  - [x] type=`'curv'` : 0/1/N entries → linear / gamma / `SkcmsCurve.Table` (slice du buffer).
- [x] `'cicp'` : 4 octets (colorPrimaries, transferCharacteristics, matrixCoefficients, videoFullRangeFlag).
- [x] `'A2B0/1/2'` / `'B2A0/1/2'` : différés Phase F4.
- [x] [SkcmsTagSignature](kanvas-skia/src/main/kotlin/org/skia/skcms/SkcmsSignature.kt) constants : `acsp`, `curv`, `para`, `rTRC/gTRC/bTRC/kTRC`, `rXYZ/gXYZ/bXYZ`, `WTPT`, `CHAD`, `CICP`, `A2B0..2`, `B2A0..2`.

**Tests** [SkcmsParseTest](kanvas-skia/src/test/kotlin/org/skia/skcms/SkcmsParseTest.kt) — 10 nouveaux :
- [x] readBig{U16, U32, Fixed} byte-swap.
- [x] **Parse réel** : extraction iCCP de `bigrect.png`, puis `skcmsParse(profileBytes)` → profile non-null avec 9 tags, `dataColorSpace=RGB`, `pcs=XYZ`, `hasTrc + hasToXYZD50`.
- [x] TRC parsée matche `kRec2020` au transferFnAlmostEqual (tol 0.001).
- [x] `toXYZD50` matche `kRec2020-gamut` au xyzAlmostEqual (tol 0.01).
- [x] Erreurs : truncated buffer, wrong magic, illuminant non-D50, version > 4 → null.

### Étape F3 — Curve LUT support — ✅

Activée pour compléter la surface skcms. Les PNGs DM utilisent uniquement des courbes paramétriques, donc cette phase n'a pas de GM consommateur immédiat ; mais sans elle, tout profil ICC avec une table TRC reste inexploitable côté `SkColorSpace.make(profile)`.

- [x] [evalCurve(curve, x)](kanvas-skia/src/main/kotlin/org/skia/skcms/Skcms.kt) — dispatch sealed-class. `Parametric` → `skcmsTransferFunctionEval`. `Table` → clamp x à `[0,1]`, lerp entre les entrées `lo`/`hi` ; lecture big-endian uint16 par byte-swap manuel pour `table16`. Mirror `eval_curve` (skcms.cc:302-326).
- [x] [minus1Ulp](kanvas-skia/src/main/kotlin/org/skia/skcms/Skcms.kt) — décrément bit-à-bit via `Float.fromBits(x.toRawBits() - 1)` (mirror skcms.cc:113-119). Garantit que `(int)minus_1_ulp(ix + 1) == int(ix)` quand `ix` est exactement un entier — point critique pour ne pas déborder l'index aux frontières.
- [x] [skcmsMaxRoundtripError(curve, invTf)](kanvas-skia/src/main/kotlin/org/skia/skcms/Skcms.kt) — grid `max(table_entries, 256)`, mirror skcms.cc:328-338.
- [x] [skcmsAreApproximateInverses(curve, invTf)](kanvas-skia/src/main/kotlin/org/skia/skcms/Skcms.kt) — seuil `1/512`, mirror skcms.cc:340-342.
- [x] [skcmsTRCsAreApproximateInverse(profile, invTf)](kanvas-skia/src/main/kotlin/org/skia/skcms/Skcms.kt) — vérifie les 3 TRCs R/G/B ; retourne false si le profil n'a pas de TRC. Mirror skcms.cc:1799-1808.

**Tests** [SkcmsCurveLutTest](kanvas-skia/src/test/kotlin/org/skia/skcms/SkcmsCurveLutTest.kt) — 15 :
- [x] **`minus1Ulp`** : `1.0 → 0.99999994`, frontière `ix=3.0 → lo=hi=3`.
- [x] **Parametric eval** : délégation à `skcmsTransferFunctionEval` (équivalence sur `kSRGB(0.5)`), identité sur `kLinear` pour 5 valeurs.
- [x] **Table8 ground truth** : 7 valeurs (`0/0.10/0.25/0.40/0.50/0.75/1.0`) sur `[0, 64, 128, 255]` matchent le driver C++ (`tools/curve_lut_test.cpp`) à 1e-6.
- [x] **Table8 clamping** : `eval(-0.5) = 0` et `eval(1.5) = 1`.
- [x] **Table16 big-endian** : 6 valeurs sur `[0x0000, 0x4000, 0x8000, 0xFFFF]` (`0.25 → 0.1875`, `1/3 → 0.25`, etc.) matchent le driver à 1e-6.
- [x] **Byte-swap discrimination** : `Table16([0x00, 0x01, 0xFF, 0xFF])` retourne `1/65535 ≈ 1.5e-5` (lecture LE donnerait `256/65535 ≈ 0.004` — diffère de 256x).
- [x] **AreApproximateInverses** : sRGB curve vs `invert(kSRGB)` ✓ ; Linear self-inverse ✓ ; sRGB curve vs `invert(k2Dot2)` ✗.
- [x] **256-entry table8 round-trip** : encoder sRGB sampled puis décodage par `kSRGB` reste sous `1/64` (quantization 8-bit), strictement > 0.
- [x] **TRCs check** : false sans TRC, true sur 3× sRGB parametric, false si une TRC diverge (k2Dot2 mélangée).

**Vérification globale** : `./gradlew :kanvas-skia:test` → 565 tests verts, 0 régression sur les 54 GMs.

### Étape F4 — A2B/B2A LUT — différé

Idem F3 — pas requis par les profils SDR RGB cibles.

### Étape F5 — `SkColorSpace.Make(profile)` — ✅

Ajouté à [SkColorSpace.kt](kanvas-skia/src/main/kotlin/org/skia/foundation/SkColorSpace.kt) companion :

- [x] `make(profile: SkcmsICCProfile): SkColorSpace?` — algorithme de résolution CICP-priorité-puis-fallback :
  - useCicp si `hasCICP && matrixCoefficients=0 && videoFullRangeFlag=1`.
  - toXYZD50 : `SkNamedPrimaries.getCicp(cicpPrimaries)` puis fallback `profile.toXYZD50`.
  - TF : `SkNamedTransferFn.getCicp(cicpTrfn)` puis fallback `profile.trc[0..2].parametric` (les 3 doivent être bit-egales).
  - `makeRGB(tf, toXYZD50)` (re-snap au singleton via Phase B).
  - Retourne null si CICP id inconnu et pas application-defined, ou si pas de TF/matrix usable.
- [x] LUT-only TRCs (Phase F3) → `null` (curve.parametric cast échoue).
- [x] **Pas porté** : `skcms_ApproximatelyEqualProfiles` fast-path pour sRGB (Phase F6 — non requis car la chaîne CICP/TRC + Phase B snap couvre le cas).
- [x] **Pas porté** : `skcms_TRCs_AreApproximateInverse` fallback (Phase F6 — non requis pour les profils paramétriques propres).

**Tests** [SkColorSpaceMakeProfileTest](kanvas-skia/src/test/kotlin/org/skia/foundation/SkColorSpaceMakeProfileTest.kt) — 6 nouveaux :
- [x] **Profile Rec.2020 réel (`bigrect.png`)** → SkColorSpace structurellement équivalent à canonical : TF dans 0.001, matrix dans 0.01, **et** xform sRGB(0,0,1) reproduit `(43, 13, 241)` 8-bit.
- [x] Profile sRGB synthétique → `makeSRGB()` singleton (assertSame).
- [x] Profile sRGB-linéaire synthétique → `makeSRGBLinear()` singleton.
- [x] No-TRC → null.
- [x] TRCs disagree → null.
- [x] CICP fast-path : `(kRec709, kIEC61966_2_1)` snap au sRGB singleton (preuve que CICP override fonctionne).

### Étape F6 — Helpers manquants — ✅

[SkcmsKnownProfiles.kt](kanvas-skia/src/main/kotlin/org/skia/skcms/SkcmsKnownProfiles.kt) ajoute :

- [x] `skcmsSrgbProfile` — singleton ICC profile sRGB, 3 TRCs paramétriques `kSRGB` + matrice `kSRGB-gamut`. Mirror skcms.cc:1511-1607.
- [x] `skcmsXyzd50Profile` — singleton identité, 3 TRCs `kLinear` + matrice IDENTITY. Mirror skcms.cc:1609-1705.
- [x] `skcmsSrgbTransferFunction()` — accesseur trivial retournant `SkNamedTransferFn.kSRGB`. Mirror skcms.cc:1707-1709.
- [x] `skcmsApproximatelyEqualProfiles(a, b)` — variante structurelle (au lieu du `skcms_Transform` byte-comparison upstream, qui requiert le pipeline complet — territoire Phase K). Couvre : identité (`a === b`), TRCs paramétriques égales `transferFnAlmostEqual` (tol 0.001), Tables byte-égales, gamut `xyzAlmostEqual` (tol 0.01), CMYK ≠ RGB, faux pour cross-type Parametric vs Table.

`skcmsTRCsAreApproximateInverse` était déjà livré en F3 (placement réajusté).

**Tests** [SkcmsKnownProfilesTest](kanvas-skia/src/test/kotlin/org/skia/skcms/SkcmsKnownProfilesTest.kt) — 14 :
- [x] Sanity sur la shape de `skcmsSrgbProfile` (3 TRCs identiques `kSRGB`, matrice `kSRGB-gamut`, flags) et `skcmsXyzd50Profile` (linéaire + identity).
- [x] **Round-trip via `SkColorSpace.make`** : `make(skcmsSrgbProfile)` snap au singleton (`assertSame(makeSRGB(), ...)` — preuve que F2/F5/F6 + Phase B s'enchaînent correctement).
- [x] `skcmsSrgbTransferFunction() === kSRGB`.
- [x] **Approximate-equal positifs** : profile === lui-même, profile rebuilt avec mêmes TF/gamut, profile avec bruit sub-tolérance (TF perturbé de 0.0005).
- [x] **Approximate-equal négatifs** : gamuts différents (sRGB vs Rec.2020), TFs différents (sRGB vs Rec.2020), Parametric vs Table cross-type, CMYK vs RGB, profile sans TRC, profile sans toXYZD50.

**Vérification globale** : `./gradlew :kanvas-skia:test` → 579 tests verts, 0 régression sur les 54 GMs.

### Étape F7 — Wiring `TestUtils.loadReferenceBitmap` — ✅

Modifié [TestUtils.kt](kanvas-skia/src/test/kotlin/org/skia/testing/TestUtils.kt) :

- [x] Helper `extractIccProfile(pngBytes)` — walk PNG chunks, inflate iCCP via `java.util.zip.Inflater`.
- [x] `loadReferenceColorSpace(name): SkColorSpace?` — extract + parse + make. Best-effort, retourne null si pas d'iCCP ou parse échoue.
- [x] `loadReferenceBitmap(name)` tag le bitmap retourné avec le CS parsé (ou sRGB par défaut).
- [x] `bufferedImageToBitmap(img, colorSpace)` accepte le CS (default sRGB).

**Tests** [LoadReferenceColorSpaceTest](kanvas-skia/src/test/kotlin/org/skia/testing/LoadReferenceColorSpaceTest.kt) — 3 nouveaux :
- [x] **Sanity check** : 5 PNGs sondés (bigrect, simplerect, thinrects, concavepaths, clip_strokerect) → tous retournent un CS dont la matrice match `DM_REFERENCE_COLOR_SPACE.toXYZD50` à `xyzAlmostEqual` près. Confirme que tous nos GMs partagent le même profil (assertion documentaire).
- [x] `loadReferenceBitmap("bigrect").colorSpace.toXYZD50 ≈ DM_REFERENCE_COLOR_SPACE.toXYZD50`.
- [x] `loadReferenceColorSpace("does-not-exist") == null`.

**Tolérance non-modifiée** : les 5 GMs gardent leurs `tolerance = 1` thresholds. Le payoff "tolerance 1 → 0" théorique nécessite d'aligner notre rendu sur les valeurs s15Fixed16 du PNG (et non kRec2020 canonique 6 décimales). C'est une inversion de stratégie qu'on garderait pour un slice ultérieur dédié — gain marginal vs complexité.

**Note résiduelle** : `compareBitmaps` ne fait pas encore de xform si les deux bitmaps sont dans des CS différents. Aujourd'hui les bitmaps rendu et référence sont structurellement dans le même CS (même `xyzAlmostEqual`), donc OK. À ajouter quand un GM utilisera un profil source ≠ DM Rec.2020.

---

## Phase G — Sérialisation `SkColorSpace` (S) — ✅

Ajouté à [SkColorSpace.kt](kanvas-skia/src/main/kotlin/org/skia/foundation/SkColorSpace.kt) :

- [x] `SERIALIZED_SIZE = 68` (4-byte header + 7 TF + 9 matrix floats), `SERIALIZED_VERSION = 1`.
- [x] `writeToMemory(memory: ByteArray?): Int` — `null` retourne la taille requise. Mirror `SkColorSpace.cpp:427-439`.
- [x] `serialize(): ByteArray` — alloue + writeToMemory. Mirror `:441-445`.
- [x] `deserialize(data, length)` — valide header v1 + version byte, lit 16 floats little-endian, appelle `makeRGB` (qui re-snap au singleton via Phase B). Mirror `:447-470`.
- [x] Helpers privés `writeFloatLE` / `readFloatLE` — IEEE 754 raw-bits, byte order LE comme upstream sur ARM/x86.

**Tests** [SkColorSpaceSerializeTest](kanvas-skia/src/test/kotlin/org/skia/foundation/SkColorSpaceSerializeTest.kt) — 12 nouveaux :
- [x] `SERIALIZED_SIZE == 68`, header version=1, reserved bytes=0.
- [x] `writeToMemory(null)` retourne 68.
- [x] `writeToMemory(ByteArray(64))` rejette (`IllegalArgumentException`).
- [x] **Round-trip sRGB → singleton (`assertSame`)** — preuve que `makeRGB` snap fonctionne après deserialize.
- [x] Round-trip sRGB-linear → singleton.
- [x] Round-trip Rec.2020 → fresh instance avec hash égal.
- [x] Round-trip `makeColorSpin()` → fresh instance avec hash égal.
- [x] `deserialize(ByteArray(67))` → null.
- [x] `deserialize` avec version=99 → null.
- [x] `deserialize` accepte un buffer plus grand que 68 (excess ignoré).
- [x] **Wire-format check** : float `g` à offset 4 == `2.4f.toRawBits()`.

**Résultat** : 237 tests verts (225 + 12), 0 régression.

**Note** : ce format 68-octets n'est PAS un profil ICC valide. Pour écrire un chunk iCCP standard dans un PNG il faut Phase F (ICC v4 emit), encore différé.

---

## Phase H — Hash bit-compat `SkChecksum::Hash32` (XS) — ✅

**But** : `SkColorSpace.transferFnHash` / `toXYZD50Hash` retournent des valeurs identiques à upstream Skia (`SkColorSpace.cpp:132-133`).

L'implémentation upstream est **wyhash** (https://github.com/wangyi-fudan/wyhash) — pas Mum hash : la spec dans le plan original était périmée. Source : [`src/core/SkChecksum.cpp`](file:///Users/chaos/workspace/kanvas-forge/skia-main/src/core/SkChecksum.cpp).

- [x] [SkChecksum.kt](kanvas-skia/src/main/kotlin/org/skia/foundation/SkChecksum.kt) — port complet de wyhash. Utilise `Math.unsignedMultiplyHigh` (Java 18+, on est sur JVM 25) pour le `_wymum` 128-bit. Secrets `WYP0..3` identiques à upstream. `hash32` zero-extend le seed uint32 → uint64 puis tronque le résultat 64 → 32 bits comme Skia.
- [x] `SkColorSpace.transferFnHash` / `toXYZD50Hash` réécrits : floats → 28 / 36 bytes little-endian via un helper local `floatsToBytes`, puis `SkChecksum.hash32(bytes)`. Mirror de `Hash32(&fTransferFn, 7*sizeof(float))` byte-pour-byte.
- [x] L'ancien `hashFloats` (FNV-1a) supprimé.

**Tests** [SkChecksumTest](kanvas-skia/src/test/kotlin/org/skia/foundation/SkChecksumTest.kt) — 11 :
- [x] **Ground truth** : 7 cross-checks contre la sortie d'un binaire C++ standalone qui ré-instancie wyhash avec les mêmes secrets — `empty`, len-1/2/3 (branche `_wyr3`), len-4 (`_wyr4`), `"hello world"` (len-11), 64 bytes (loop `i > 48`).
- [x] **Seed honoré** : `hash32(data, seed=0) != hash32(data, seed=1)`.
- [x] **Length honorée** : `hash32(data, length=4) == hash32(data[..4])`.
- [x] **Stabilité** : 3 appels successifs → même valeur.
- [x] **Hash64 cohérent** : `hash32` == low-32-bits de `hash64`.

**Tests** [SkColorSpaceHashTest](kanvas-skia/src/test/kotlin/org/skia/foundation/SkColorSpaceHashTest.kt) — 6 :
- [x] **Cross-check Skia** : sRGB transferFnHash = `0x105632bd`, sRGB toXYZD50Hash = `0x7910144c`, Linear TF = `0x70e19594`, Rec.2020 TF = `0xef6bae87`, Rec.2020 matrix = `0x9ebacc71`. Toutes les 5 valeurs viennent du driver C++ standalone qui mirror exactement `SkChecksum.cpp`.
- [x] **Equals stable** : sRGB == sRGB, sRGBLinear == sRGBLinear, sRGB ≠ sRGBLinear.
- [x] **Rec.2020 ≠ sRGB** sur les deux hashes (sanity).

**Vérification globale** : `./gradlew :kanvas-skia:test` → 415 tests, 0 échec, 0 régression sur les 44 GMs.

---

## Phase I — Support HDR (PQ + HLG) (L) — ✅

**But** : couvrir les TF sentinel-encoded (`g < 0`) dans skcms et activer les branches OOTF dans `XformSteps`. Débloque `SkColorSpace::MakeCICP(kRec2020, kPQ)` et les GMs HDR.

### Étape I1 — Support sentinelle dans `classify` ✅

- [x] Branche `tf.g < 0` dans [classify](kanvas-skia/src/main/kotlin/org/skia/skcms/Skcms.kt) : extrait `enum_g = -tf.g.toInt()`, vérifie `(-enum_g).toFloat() == tf.g` (rejet des fractions), rejette `tf.g < -128`. Switch sur les ordinaux `SkcmsTFType.PQish/HLGish/HLGinvish/PQ/HLG`.
- [x] Soundness : PQ exige `b=c=d=e=f=0`, HLG exige `d=e=f=0` (skcms.cc:165-173).
- [x] Helper `tfKindMarker(kind: SkcmsTFType): Float = -kind.ordinal.toFloat()` exposé publiquement.
- [x] **Important** : ordre du `enum class SkcmsTFType` corrigé pour matcher upstream `skcms_public.h` : `Invalid, sRGBish, PQish, HLGish, HLGinvish, PQ, HLG`. Les constantes existantes `kPQ(g=-5)` et `kHLG(g=-6)` étaient déjà cohérentes avec cet ordre, mais l'enum interne avait `PQ=4, HLG=5, HLGinvish=6` (ordre Phase D périmé).

### Étape I2 — `eval` pour PQ / PQish / HLG / HLGish / HLGinvish ✅

- [x] `case PQ` : EOTF BT.2100 avec constantes `c1=107/128, c2=2413/128, c3=2392/128, m1=1305/8192, m2=2523/32`. Pas de `sign *` (mirror upstream skcms.cc:284-292).
- [x] `case PQish` : `(A + B·x^C) / (D + E·x^C)` puis `pow(_, F)`, `*sign`. Lit A..F directement depuis `tf.a..tf.f` (pas de memcpy explicite ; layout déjà compatible).
- [x] `case HLG` : `x ≤ 0.5 → x²/3`, sinon `(exp((x-c)/a) + b)/12` avec `a=0.17883277, b=0.28466892, c=0.55991073`.
- [x] `case HLGish` : `K · sign · (xR ≤ 1 ? (xR)^G : exp((x-c)·a) + b)`. R/G/a/b/c lus depuis `tf.a..tf.e`, `K = tf.f + 1`.
- [x] `case HLGinvish` : variante avec `R/G/a` réciproques produits par `invert` ; `R · (x/K)^G` ou `a·ln((x/K)-b) + c`.

### Étape I3 — `invert` pour PQish / HLGish / HLGinvish ✅

- [x] `PQish → PQish` avec params `{-A, D, 1/F, B, -E, 1/C}` (skcms.cc:1992-1995).
- [x] `HLGish ↔ HLGinvish` avec `{1/R, 1/G, 1/a, b, c, K_minus_1}` (skcms.cc:1997-2007).
- [x] PQ et HLG retournent `null` (pas inversibles, skcms.cc:1988-1989).
- [x] La branche sRGBish reste intacte (regression test inclus).

### Étape I4 — `SkNamedTransferFn` constructeurs HDR ✅

- [x] [skcmsTransferFunctionMakePQish(A,B,C,D,E,F)](kanvas-skia/src/main/kotlin/org/skia/skcms/Skcms.kt).
- [x] [skcmsTransferFunctionMakeScaledHLGish(K,R,G,a,b,c)](kanvas-skia/src/main/kotlin/org/skia/skcms/Skcms.kt) — packe `K-1` dans le slot `f`.
- [x] [skcmsTransferFunctionMakeHLGish(R,G,a,b,c)](kanvas-skia/src/main/kotlin/org/skia/skcms/Skcms.kt) = `MakeScaledHLGish(1, ...)`.
- [x] [skcmsTransferFunctionMakePQ(hdrRefWhite)](kanvas-skia/src/main/kotlin/org/skia/skcms/Skcms.kt).
- [x] [skcmsTransferFunctionMakeHLG(hdrRefWhite, peakLuminance, systemGamma)](kanvas-skia/src/main/kotlin/org/skia/skcms/Skcms.kt).

### Étape I5 — OOTF dans `SkColorSpaceXformSteps` ✅

[SkColorSpaceXformSteps.kt](kanvas-skia/src/main/kotlin/org/skia/core/SkColorSpaceXformSteps.kt) intégralement réécrit pour le constructeur (apply() élargi de 2 branches).

- [x] Nouveaux champs publics : `fSrcOotf: FloatArray(4)` et `fDstOotf: FloatArray(4)`.
- [x] Nouveaux flags : `srcOotf`, `dstOotf` dans la `data class Flags`.
- [x] [setOotfY(cs, out)](kanvas-skia/src/main/kotlin/org/skia/core/SkColorSpaceXformSteps.kt) : calcule `Y[i] = ∑_j m[j][i] · Y_rec2020[j]` où `m = rec2020Linear.fromXYZD50 · cs.toXYZD50`. Mirror `set_ootf_Y` (SkColorSpaceXformSteps.cpp:27-39).
- [x] Branche PQ src : `scaleFactor *= 10000 / srcTrfn.a`, `srcTF = K_PQISH_STANDARD`, linearize=true.
- [x] Branche HLG src : `scaleFactor *= b/a`, `srcTF = K_HLGISH_STANDARD.copy(f = 1/12 - 1)` (K=1/12), `linearize=true` ; si `srcTrfn.c != 1`, active `srcOotf` avec `gamma_minus_1 = c - 1`.
- [x] Symétrie pour dst : `scaleFactor /= 10000/a` ou `/= b/a`, `dstTFInv = invert(K_PQISH/HLGISH_STANDARD)`, `dstOotf` avec `gamma_minus_1 = 1/c - 1`.
- [x] `gamut_transform = (toXYZD50Hash diffèrent) || (scaleFactor != 1)` — la matrice est multipliée par `scaleFactor` cell par cell.
- [x] **Optimisation cancel-OOTF** : si `srcOotf && !gamutTransform && dstOotf` et `(srcOotf[3]+1)·(dstOotf[3]+1) == 1` → annule les deux. Mirror upstream (SkColorSpaceXformSteps.cpp:154-163), bit-pour-bit (même check multiplicatif).
- [x] **Optimisation linearize-encode skip** désactivée pour PQ/HLG : `srcTF/dstTFInv` sont alors les paramétriques standard, pas les TFs originales — les hashes ne reflèteraient pas l'identité du round-trip.
- [x] `apply()` étendu avec les deux branches OOTF (après linearize, avant gamut ; après gamut, avant encode).

### Étape I6 — Constantes de référence ✅

- [x] `kPQ`, `kHLG` dans [SkNamedTransferFn](kanvas-skia/src/main/kotlin/org/skia/skcms/SkNamedTransferFn.kt) déjà ajoutés en Phase D ; Phase I les active sans modification — `classify(kPQ) = PQ`, `classify(kHLG) = HLG`.

### Mises à jour collatérales

- [x] [SkColorSpace.makeRGB](kanvas-skia/src/main/kotlin/org/skia/foundation/SkColorSpace.kt) : `if (classify == sRGBish)` → `if (classify == Invalid)` rejet. Aligné sur `SkColorSpace::MakeRGB` upstream (rejette uniquement `skcms_TFType_Invalid`). Permet `makeRGB(kPQ, kRec2020)` de retourner un colorspace HDR utilisable.
- [x] Tests Phase D `SkNamedTransferFnConstantsTest` et Phase E `SkColorSpaceCicpTest` mis à jour : la branche "until Phase I" devient "Phase I activated".

**Tests** [SkcmsHdrTest](kanvas-skia/src/test/kotlin/org/skia/skcms/SkcmsHdrTest.kt) — 19 :
- [x] **Ground truth eval** : 5 valeurs PQ (0.0/0.25/0.50/0.75/1.0), 5 valeurs HLG, et PQish parametric reproduit PQ. Toutes match un driver C++ standalone (`tools/hdr_test.cpp`) à 5e-6 (drift float-vs-double inhérent au stack pow).
- [x] **Sanity classify** : ordinaux PQish/HLGish/HLGinvish/PQ/HLG, rejet fractional `g`, rejet `g < -128`, rejet PQ avec `b!=0`, rejet HLG avec `d!=0`.
- [x] **Invert** : `invert(PQ) == null`, `invert(HLG) == null`, `invert(PQish)` retourne PQish avec params mangés `{-A, D, 1/F, B, -E, 1/C}`, `invert(HLGish) → HLGinvish` avec `R/G/a` réciproques, `invert(HLGinvish) → HLGish`.
- [x] **Round-trip** : HLGish → HLGinvish → HLGish exact à 1e-4 sur 9 points (régions linéaire et non-linéaire), PQish self-inverse à 1e-4 sur 5 points.
- [x] **Régression sRGBish** : `invert(kSRGB)` ; round-trip sur 0 et 1 reste exact.

**Tests** [SkColorSpaceXformStepsHdrTest](kanvas-skia/src/test/kotlin/org/skia/core/SkColorSpaceXformStepsHdrTest.kt) — 12 :
- [x] **Ground truth sRGB → PQ Rec.2020 (ref=203)** : pure blue (0,0,1) → (0.290, 0.197, 0.569) ; pure red (1,0,0) → (0.533, 0.327, 0.220) ; gris (0.5) → (0.427, 0.427, 0.427). Match à 1e-3 vs `tools/ootf_test.cpp`.
- [x] **PQ src** active linearize + gamut_transform (scaleFactor=1/49.26 ≠ 1) sans srcOotf (PQ n'a pas de gamma système).
- [x] **HLG src** avec gamma 1.2 active srcOotf et précalcule Y_Rec2020 = (0.2627, 0.678, 0.0593) ; gamma 1.0 désactive srcOotf.
- [x] **OOTF cancel** : src.c == dst.c et même gamut → les deux ootfs droppés (mirror exact `(c_s)·(1/c_d) == 1` upstream).
- [x] **OOTF non-cancel** : différents gamuts → ootfs conservés.
- [x] **PQ→PQ scale** : refWhite 203 → 100 produit une matrice `dstA/srcA = 100/203 ≈ 0.493 · I`.
- [x] **Régression critique** : sRGB(0,0,1) → kRec2020 reproduit toujours (43, 13, 241) byte-pour-byte (invariant Phase F5 / J).
- [x] **Régression** : sRGB → sRGB opaque reste identité (early-return).

**Vérification globale** : `./gradlew :kanvas-skia:test` → **543 tests verts, 0 régression** sur les 54 GMs.

---

## Phase J — Précision / snap final `kRec2020` (XS) — ✅

**Décision retenue** : Option 1 — alignement sur Skia upstream (6 décimales).

- [x] [SkNamedTransferFn.kRec2020](kanvas-skia/src/main/kotlin/org/skia/skcms/SkNamedTransferFn.kt) : `{2.22222f, 0.909672f, 0.0903276f, 0.222222f, 0.0812429f, 0, 0}` (correspondance exacte avec `SkColorSpace.h:130-131`).
- [x] [SkNamedGamut.kRec2020](kanvas-skia/src/main/kotlin/org/skia/skcms/SkNamedGamut.kt) : `{0.673459, 0.165661, 0.125100, 0.279033, 0.675338, 0.0456288, -0.00193139, 0.0299794, 0.797162}` (correspondance exacte avec `SkColorSpace.h:251-255`).

Le snap Phase B (`transferFnAlmostEqual` tolérance `0.001f`, `xyzAlmostEqual` tolérance `0.01f`) absorbe la divergence ~1e-5 / ~5e-7 entre ces valeurs et celles s15Fixed16-décodées du profil ICC dans le PNG de référence. Donc :

- `makeRGB(parsed-from-PNG-icc, parsed-from-PNG-gamut)` snap maintenant sur le **même** instance que `makeRGB(SkNamedTransferFn.kRec2020, SkNamedGamut.kRec2020)`.
- `Equals` est désormais symétrique avec Skia upstream pour les profils Rec.2020 d'origine variée.

**Vérification** : 237 tests verts, GMs scores inchangés (BigRect 95.53, SimpleRect/ClipStrokeRect/DrawBitmapRect3 100, ConcavePaths 98.86, ThinRects 92.10).

---

## Phase K — `apply(SkRasterPipeline*)` (XL — différé)

**But** : émettre la pipeline color comme une chaîne d'opcodes `SkRasterPipelineOp` au lieu d'appeler `apply(rgba)` scalaire par pixel.

Cf. `SkColorSpaceXformSteps.cpp:267-275`.

- Dépend de **`SkRasterPipeline`** qui est un sous-système séparé (3000+ lignes), JIT scalaire avec un large opcode table.
- **Hors scope** du plan colorspace. À aborder dans un plan dédié `MIGRATION_PLAN_RASTER_PIPELINE.md` quand le rendu nécessite la perf JIT.

---

## Critères de complétude

À la fin du plan (Phases A-J, hors I et K) :

- [ ] `SkColorSpace` API surface = 95% Skia (manque : `apply(SkRasterPipeline*)`, vrai write-PNG-iCCP).
- [ ] `MakeRGB` snap aussi serré que Skia.
- [ ] ICC parser opérationnel sur tout PNG v4 standard.
- [ ] `Make(profile)` fonctionne pour profils sRGB / Rec.2020 / Adobe RGB / Display P3 / ProPhoto / et toute table-CICP standard.
- [ ] Fidélité numérique sur les 5 GMs existants : ≥ état actuel à `tolerance=1`.
- [ ] Optionnel **Phase I** (HDR) : 100% surface skcms, `MakeCICP(kRec2020, kPQ)` fonctionne.

## Fichiers critiques à créer

```
kanvas-skia/src/main/kotlin/org/skia/skcms/
  SkcmsCurve.kt                          # F1
  SkcmsCICP.kt                           # F1
  SkcmsICCProfile.kt                     # F1
  SkcmsSignature.kt                      # F1
  SkcmsA2B.kt + SkcmsB2A.kt              # F1 (stubs, F4 active)
  SkcmsParse.kt                          # F2
  SkcmsAdaptToXYZD50.kt                  # E
  SkcmsPrimariesToXYZD50.kt              # E
  TFPQish.kt + TFHLGish.kt               # I (struct mémoire)

kanvas-skia/src/main/kotlin/org/skia/foundation/
  SkColorSpacePrimaries.kt               # D
  SkNamedPrimaries.kt                    # D
  SkColorSpacePriv.kt                    # B (is_almost_*)

kanvas-skia/src/main/kotlin/org/skia/util/
  SkChecksum.kt                          # H

kanvas-skia/src/test/kotlin/org/skia/skcms/
  SkcmsParseTest.kt                      # F
  SkcmsHDRTest.kt                        # I
  SkcmsCicpTest.kt                       # E

kanvas-skia/src/test/kotlin/org/skia/foundation/
  SkColorSpaceMakeTest.kt                # F
  SkColorSpaceModifiersTest.kt           # C
  SkColorSpaceSerializeTest.kt           # G
  SkColorSpaceCicpTest.kt                # E

kanvas-skia/src/test/kotlin/org/skia/core/
  SkColorSpaceXformStepsOptTest.kt       # A
  SkColorSpaceXformStepsHDRTest.kt       # I
```

## Sources de référence

- [`skia-main/include/core/SkColorSpace.h`](file:///Users/chaos/workspace/kanvas-forge/skia-main/include/core/SkColorSpace.h) — surface publique complète.
- [`skia-main/src/core/SkColorSpace.cpp`](file:///Users/chaos/workspace/kanvas-forge/skia-main/src/core/SkColorSpace.cpp) — Make, MakeCICP, Make(profile), modifiers, serialize.
- [`skia-main/src/core/SkColorSpacePriv.h`](file:///Users/chaos/workspace/kanvas-forge/skia-main/src/core/SkColorSpacePriv.h) — helpers `is_almost_*`.
- [`skia-main/src/core/SkColorSpaceXformSteps.{h,cpp}`](file:///Users/chaos/workspace/kanvas-forge/skia-main/src/core/SkColorSpaceXformSteps.cpp) — XformSteps complet avec OOTF.
- [`skia-main/modules/skcms/skcms.cc`](file:///Users/chaos/workspace/kanvas-forge/skia-main/modules/skcms/skcms.cc) — 3141 lignes, contient ICC parse, classify, eval, invert, matrix ops, primaries, AdaptToXYZD50.
- [`skia-main/modules/skcms/src/skcms_public.h`](file:///Users/chaos/workspace/kanvas-forge/skia-main/modules/skcms/src/skcms_public.h) — types publics.
- [`skia-main/src/core/SkChecksum.h`](file:///Users/chaos/workspace/kanvas-forge/skia-main/src/core/SkChecksum.h) — hash bit-compat.

## Vérification end-to-end (par phase)

Identique aux plans précédents :
1. `./gradlew :kanvas-skia:compileKotlin` compile sans erreur.
2. `./gradlew :kanvas-skia:test` réussit tous les tests existants + nouveaux.
3. Scores dans `kanvas-skia/test-similarity-scores.properties` ≥ valeur précédente (jamais chuter > 1%).
4. Une fois Phase F mergée : `loadReferenceBitmap` lit le profil ICC du PNG et tightens le tolerance à 0 (l'objectif final).

## Ordre de PRs recommandé

1. **PR Phase A + B + C** — petits patches, peuvent passer ensemble (~150 lignes total).
2. **PR Phase D + E** — constantes + CICP (~400 lignes).
3. **PR Phase F1-F2** — types ICC + parser header/tags paramétric (~600 lignes).
4. **PR Phase F3+F5** — curve LUT + Make(profile) (~400 lignes).
5. **PR Phase F7** — wiring `loadReferenceBitmap` sur ICC parser (~50 lignes, bénéfice énorme : tolerance 1→0).
6. **PR Phase G** — sérialisation (~150 lignes).
7. **PR Phase J** — clean-up précisions kRec2020 (~30 lignes).
8. ✅ **PR Phase H** — hash bit-compat (~150 lignes, wyhash port + 17 tests).
9. ✅ **PR Phase I** — HDR complet (~600 lignes, 31 nouveaux tests, ground truth via 2 drivers C++).

À chaque PR : commit unique par phase, tests verts, similarity scores maintenus.
