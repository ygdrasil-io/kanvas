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

### Étape F1 — Types `skcms_ICCProfile` (et amis)

Cf. `skcms_public.h:160-323`.

- [ ] `SkcmsICCProfile` data class — `buffer`, `size`, `data_color_space`, `pcs`, `tag_count`, `trc[3]`, `toXYZD50`, `A2B`, `B2A`, `CICP`, `has_*` flags.
- [ ] `SkcmsCurve` — `parametric: SkcmsTransferFunction` OU `table_8` / `table_16`.
- [ ] `SkcmsCICP` — `color_primaries`, `transfer_characteristics`, `matrix_coefficients`, `video_full_range_flag`.
- [ ] `SkcmsA2B`, `SkcmsB2A` — LUT 3D et associated curves. Phase F4 (différé).
- [ ] `SkcmsSignature` enum (data color spaces : 'RGB ', 'GRAY', 'CMYK', 'XYZ ').

### Étape F2 — `skcms_Parse` (header v2/v4)

Cf. `skcms.cc:540-1100` (`Parse` + `parse_*`).

- [ ] Validation header ICC 128 octets : magic 'acsp', version, device class, color space, PCS, illuminant XYZ.
- [ ] Lecture tag table (`tag_count` × 12 octets : signature + offset + length).
- [ ] Pour chaque tag : signature switch.
  - [ ] `'rXYZ'` / `'gXYZ'` / `'bXYZ'` / `'wtpt'` : 20 octets, type='XYZ ', lit 3 floats s15Fixed16.
  - [ ] `'rTRC'` / `'gTRC'` / `'bTRC'` / `'kTRC'` : type='para' (paramétrique 0/1/2/3/4 → 1/3/4/5/7 floats) ou type='curv' (LUT, 0=linear / 1=gamma / N=table). Phase F2 supporte para uniquement, table déférée à F3.
  - [ ] `'cprt'` / `'desc'` : ignorer.
  - [ ] `'cicp'` : 4 octets (color_primaries, transfer_characteristics, matrix_coefficients, full_range_flag).
  - [ ] `'A2B0'` / `'A2B1'` / `'A2B2'` / `'B2A0'` etc. : F4.
- [ ] Calcule `has_trc`, `has_toXYZD50`, `has_A2B`, `has_B2A`, `has_CICP`.

### Étape F3 — Curve LUT support

Cf. `skcms.cc:471-499` (`evalCurve`).

- [ ] `SkcmsCurve.tableEntries` + `table_16`/`table_8`.
- [ ] `evalCurve(curve, x)` : interpole linéairement entre 2 entrées de table.
- [ ] `skcmsTRCsAreApproximateInverse(profile, invTf): Boolean` (fallback dans `Make`).
- [ ] `skcmsAreApproximateInverses(curve, invTf): Boolean` — `MaxRoundtripError < 1/512`.

### Étape F4 — A2B/B2A LUT (différé)

ICC v4 multi-dimensional LUT (3D 17×17×17 typique). Ce sont les profils CMYK et certains RGB exotiques. **Reporté** sauf si un PNG de référence l'exige (improbable).

### Étape F5 — `SkColorSpace.Make(profile)`

Cf. `SkColorSpace.cpp:331-407`.

- [ ] Fast-path `skcms_ApproximatelyEqualProfiles(profile, sRGB) → makeSRGB()`.
- [ ] Fast-path CICP : si `kRec709 + kIEC61966_2_4` → makeSRGB.
- [ ] Sinon : tente CICP, puis `profile.toXYZD50`, puis `profile.trc[0..2].parametric` (s'ils matchent), sinon fallback `kSRGB` si TRCs ≈ inverse sRGB.
- [ ] Retourne `null` si rien ne se résout.

### Étape F6 — Helpers manquants

- [ ] `skcms_sRGB_profile()` — singleton du profil sRGB de référence (96 octets, parsable).
- [ ] `skcms_sRGB_Inverse_TransferFunction()` — inversed kSRGB.
- [ ] `skcms_ApproximatelyEqualProfiles(a, b)` — compare TFs et matrix avec tolérance.

### Étape F7 — Wiring `TestUtils.loadReferenceBitmap`

Aujourd'hui [TestUtils.kt:83-115](kanvas-skia/src/test/kotlin/org/skia/testing/TestUtils.kt) lit les PNGs en bypassant le profil ICC. Avec F1-F6 :

- [ ] `loadReferenceBitmap(name)` : lit le chunk iCCP, parse, construit `SkColorSpace.Make(profile)`, retourne un `SkBitmap` avec ce CS.
- [ ] `compareBitmaps(a, b)` peut maintenant garantir que les deux sont dans le même CS — ou xform si différents.
- [ ] **Effet de bord** : on n'a plus besoin de hardcoder `DM_REFERENCE_COLOR_SPACE` dans `runGmTest` — on peut lire le CS du PNG attendu et matcher dynamiquement.

**Tests** :
- [ ] `SkcmsParseTest` — parse un profil sRGB v4 connu, vérifie tags.
- [ ] `SkcmsParseRec2020Test` — parse le profil de `bigrect.png` (déjà extrait dans `colorspace-fingerprint.md`), vérifie matrix + TF.
- [ ] `SkColorSpaceMakeTest` — `Make(parsed_sRGB) === makeSRGB()`.
- [ ] `SkColorSpaceMakeTest.parsedRec2020EqualsKnown` — match avec `MakeRGB(kRec2020, kRec2020-gamut)`.
- [ ] **Test bout-en-bout** : `loadReferenceBitmap("bigrect")` retourne un bitmap dont `colorSpace == DM_REFERENCE_COLOR_SPACE`.

**Risque** : ICC v2 vs v4. Skia DM produit du v4. F1-F2 doivent gérer les deux versions (header byte 8). Plan B : v4 only, accepter de ne pas pouvoir lire un PNG v2.

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

## Phase H — Hash bit-compat `SkChecksum::Hash32` (XS)

**But** : `SkColorSpace.hash()` retourne une valeur identique à upstream Skia.

À porter de [`src/core/SkChecksum.h`](file:///Users/chaos/workspace/kanvas-forge/skia-main/src/core/SkChecksum.h) :

- [ ] `SkChecksum.hash32(data: ByteArray, len: Int, seed: Int = 0): Int` — Mum hash variant. Spec : `hash_fn` dans `src/opts/SkOpts_*.cpp`.
- [ ] Adapter les conversions float → byte pour matcher l'ordre mémoire C++ (little-endian sur ARM/x86).
- [ ] Remplacer `hashFloats` dans [SkColorSpace.kt:96-104](kanvas-skia/src/main/kotlin/org/skia/foundation/SkColorSpace.kt:96).

**Tests** :
- [ ] Cross-check avec Skia : `MakeSRGB().transferFnHash` == valeur connue Skia (à extraire).
- [ ] Stabilité : bit-rotation préservée, hash identique entre runs.

**Justification** : sans ça, `serialize`/`Deserialize` est OK (pas de hash dans le format), `Equals` est OK (intra-impl). Utile uniquement pour interop avec un cache Skia externe (peu probable). **Priorité basse**.

---

## Phase I — Support HDR (PQ + HLG) (L)

**But** : couvrir les TF sentinel-encoded (`g < 0`) dans skcms et activer les branches OOTF dans `XformSteps`. Débloque `SkColorSpace::MakeCICP(kRec2020, kPQ)` et les GMs HDR (encore inexistants dans nos refs mais existent upstream).

### Étape I1 — Support sentinelle dans `classify`

Cf. `skcms.cc:135-191`.

- [ ] Branche `tf.g < 0` dans `classify` : extrait `enum_g = -tf.g.toInt()`, switch sur `skcms_TFType_PQ/HLG/PQish/HLGish/HLGinvish`.
- [ ] Soundness : PQ exige `b=c=d=e=f=0`, HLG exige `d=e=f=0`.
- [ ] Sentinel marker : `TFKind_marker(kind: SkcmsTFType): Float = -kind.ordinal.toFloat()`.

### Étape I2 — `eval` pour PQ / PQish / HLG / HLGish / HLGinvish

Cf. `skcms.cc:259-295`.

- [ ] `case PQ` : Reinhard-style formula avec constantes c1, c2, c3, m1, m2 (BT.2100 PQ EOTF).
- [ ] `case PQish` : `pow((A + B*x^C) / (D + E*x^C), F)`. Lit `pq` struct via memcpy from `tf.a`.
- [ ] `case HLG` : split `x ≤ 0.5` linéaire vs power. Constantes BT.2100 HLG OETF.
- [ ] `case HLGish` : multipliée par `K = K_minus_1 + 1`.
- [ ] `case HLGinvish` : variante avec params inversés.
- [ ] Helper struct `TF_PQish(A, B, C, D, E, F: Float)` et `TF_HLGish(R, G, a, b, c, K_minus_1: Float)` avec layout mémoire identique aux 6 derniers floats du TF.

### Étape I3 — `invert` pour PQish / HLGish / HLGinvish

Cf. `skcms.cc:1992-2007`.

- [ ] `PQish → PQish` avec params transformés `{-A, D, 1/F, B, -E, 1/C}`.
- [ ] `HLGish ↔ HLGinvish` avec `{1/R, 1/G, 1/a, b, c, K_minus_1}`.
- [ ] PQ et HLG ne sont pas invertibles → `null`.

### Étape I4 — `SkNamedTransferFn` constructeurs HDR

Cf. `skcms.cc:212-248`.

- [ ] `skcmsTransferFunctionMakePQish(A, B, C, D, E, F)`.
- [ ] `skcmsTransferFunctionMakeScaledHLGish(K, R, G, a, b, c)`.
- [ ] `skcmsTransferFunctionMakeHLGish(R, G, a, b, c)` = `MakeScaledHLGish(1, ...)`.
- [ ] `skcmsTransferFunctionMakePQ(hdrRefWhite)`.
- [ ] `skcmsTransferFunctionMakeHLG(hdrRefWhite, peakLuminance, systemGamma)`.

### Étape I5 — OOTF dans `SkColorSpaceXformSteps`

Cf. `SkColorSpaceXformSteps.cpp:25-40`, `:74-105`, `:107-135`, `:166-178`.

- [ ] Champs `fSrcOotf: FloatArray(4)` et `fDstOotf: FloatArray(4)` dans `Flags` étendus.
- [ ] `setOotfY(cs, out)` : calcule Y luminance coefficients depuis le gamut transform vers Rec.2020.
- [ ] Branche PQ src : `scaleFactor *= 10000 / srcTrfn.a`, set `srcTF` à `kPQish` standard.
- [ ] Branche HLG src : `scaleFactor *= srcTrfn.b / srcTrfn.a`, set `srcTF` à `kHLGish` scalé par 1/12.
- [ ] Si HLG `srcTrfn.c != 1` : active `srcOotf`, calcule coefficients Y.
- [ ] Symétrie pour dst.
- [ ] `optimization 1` : si `srcOotf && dstOotf && !gamutTransform` et gammas réciproques → annuler les deux.
- [ ] Étendre `apply()` avec branches `srcOotf` / `dstOotf` (Y-luminance scaling avant et après gamut transform).

### Étape I6 — Constantes de référence

- [ ] `kPQ`, `kHLG` dans `SkNamedTransferFn` (déjà listées en Phase D, mais Phase D les insère sans support eval/invert ; Phase I active le support).

**Tests** :
- [ ] Round-trip PQ : `eval(kPQish, x)` puis `eval(invert(kPQish), y)` ≈ x dans `[0, 1]`.
- [ ] HLG idem.
- [ ] sRGB → PQ Rec.2020 sur (0,0,1) — comparer à une valeur connue Skia.
- [ ] OOTF cancel : sRGB+OOTF → sRGB+OOTF même CS = identité (via opt 1).

**Risque** : HDR dépend de `pow` haute précision. JVM `Math.pow` est fine, mais comportement sur extrêmes (`x` proche 1, `pow(x, 0.45)` etc.) peut diverger sub-ulp avec `powf_` Skia.

**Effort** : ~600 lignes. C'est le plus gros slice après ICC parsing. Pas requis tant qu'on n'a pas de GMs HDR à valider.

---

## Phase J — Précision / snap final `kRec2020` (XS)

**But** : décider de la fin du conflit `kRec2020 ours vs kRec2020 Skia`.

État actuel :
- **Skia** : `{2.22222, 0.909672, 0.0903276, 0.222222, 0.0812429, 0, 0}` (6 décimales).
- **Nous** : `{2.22221961, 0.909672439, 0.0903276134, 0.222222447, 0.0812428713, 0, 0}` (s15Fixed16-décodé du PNG).

Idem sur `SkNamedGamut.kRec2020`.

### Décision

Une fois Phase B (snap `is_almost_*`) en place :
- [ ] **Option 1** — Aligner sur Skia (6 décimales). Le snap dans `MakeRGB` couvre la divergence `0.001f`. Le PNG ICC parsé via Phase F sera snappé sur `kRec2020` exact, ce qui rend le `Equals` symétrique avec Skia.
- [ ] **Option 2** — Garder notre précision élevée. Justification : on a la valeur exacte du PNG cible. Mais on ne snap plus avec Skia upstream, donc deux instances `MakeRGB(perfect-rec2020-from-png, perfect-rec2020-gamut)` ne seraient `===` à `MakeRGB(SkNamedTransferFn.kRec2020, SkNamedGamut.kRec2020)` que si Phase B est activée.
- **Recommandation** : Option 1 + Phase B. Plus simple, plus aligné, et les tests Phase 1-3a passent toujours à `tolerance=1` (vérifié).

**Tests** :
- [ ] `MakeRGB(parsedFromPng).hash() == MakeRGB(SkNamedTransferFn.kRec2020, SkNamedGamut.kRec2020).hash()`.
- [ ] Re-mesure des 5 GMs : score doit rester ≥ leur valeur actuelle.

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
8. **PR Phase H** — hash bit-compat (optionnel, ~80 lignes).
9. **PR Phase I** — HDR complet (~600 lignes, en dernier — pas de bloquant aujourd'hui).

À chaque PR : commit unique par phase, tests verts, similarity scores maintenus.
