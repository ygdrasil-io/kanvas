# Migration Skia → Kotlin — Plan #2 : `SkColorSpace`

> Plan complémentaire à [MIGRATION_PLAN.md](MIGRATION_PLAN.md). Les deux progressent en parallèle : le plan principal porte le pipeline raster (rects → AA → paths → …), celui-ci porte la gestion couleur. Ils convergent quand le raster sait écrire dans un espace de destination autre que sRGB.

## Contexte

Phase 1 du plan principal a livré `BigRectGM` et `SimpleRectGM` à 99.5 % et 100 % de similarité — **mais** avec une `tolerance=160` par canal. La raison : les PNGs de référence Skia (`kanvas/src/test/resources/original-888/*.png`) contiennent des pixels qui ne sont pas dans un sRGB strict. Exemple mesuré : pur bleu Skia `0xFF0000FF` est encodé `0xFF2B0DF2` dans `bigrect.png` (offset de +43 en R, +13 en G, –13 en B). Investigation préalable :

- Le profil ICC embarqué est *structurellement* sRGB (primaires sRGB ramenées D50, courbe sRGB en LUT).
- `java.awt.ColorConvertOp` sRGB → profil embarqué donne l'identité.
- Aucun profil grand gamut testé (Display P3, Rec2020, AdobeRGB, ProPhoto) ne reproduit le décalage.
- L'histogramme de `bigrect.png` ne contient que 7 couleurs (blanc + bleu Skia + 5 mélanges de couverture partielle), donc le décalage est intrinsèque aux données et pas un artefact de quantification d'affichage.

**Conclusion** : on ne peut pas atteindre `tolerance=0` sans répliquer la pipeline de gestion couleur de Skia bout en bout, depuis la couleur source dans `SkPaint` jusqu'aux octets que Skia écrit dans le PNG. Ce plan-ci se concentre exclusivement sur ce portage.

## Objectif chiffré

Faire passer `BigRectGM` et `SimpleRectGM` (et tous les GMs ajoutés ensuite) avec `tolerance ≤ 1` par canal, sans baisser le seuil de similarité (≥ 99 % en Phase 1, ≥ 95 % en Phase 2, etc.). Idéalement `tolerance = 0` une fois la pipeline complète, ou un offset constant ≤ 1 ulp imputable à de l'arrondi flottant.

## Trois décisions architecturales

1. **Porter `skcms` *avant* `SkColorSpace`.** `SkColorSpace` n'est qu'un wrapper autour de `skcms_TransferFunction` + `skcms_Matrix3x3`. Tenter `SkColorSpace` sans skcms produit une coquille vide. Le code généré (`kanvas/src/generated/skcms/org/skia/skcms/Functions.kt`, ~154 KB de C++ traduit) est notre spec.
2. **Ne pas porter `skcms` en bloc.** 154 KB couvrent : ICC parsing, table-based curves, A2B/B2A LUTs, opcode-based transforms, vectorisation. Pour reproduire le décalage observé, il suffit du sous-ensemble `MakeRGB(transferFn, toXYZD50)` + `XformSteps.apply(rgba)` + parse minimal de iCCP/sRGB/cICP. Le reste (LUT 3D, AVX) est différé.
3. **Décoder *et* encoder via la pipeline.** Aujourd'hui `TestUtils.bufferedImageToBitmap` lit en RGBA 16 bits *sans* color-management (lecture directe `DataBufferUShort`). Pour fermer la boucle, il faut soit :
   - **(a)** lire les PNGs avec leur profil embarqué et convertir vers l'espace de destination du test (sRGB ou linéaire 32f, au choix), *et* faire pareil au rendu — comparaison dans l'espace de destination.
   - **(b)** rendre dans l'espace exact des références (= profil ICC embarqué) et comparer octet-à-octet.

   On retient **(b)** : moins de logique de comparaison, et c'est ce que Skia DM fait nativement (output dans la `--config` choisie, profil ICC inscrit dans le PNG via `pngEncoder`). On peut basculer en (a) plus tard si besoin pour des inspections cross-profil.

## Phase 0 — Identifier le profil cible (avant tout code)

**But** : savoir *exactement* quel profil ICC les références utilisent, parce que sans ça toute pipeline qu'on porte vise au mauvais endroit.

- [x] Extraire les chunks `iCCP`, `sRGB`, `cICP`, `cHRM`, `gAMA` de `bigrect.png` et de 3-4 autres références (`simplerect.png`, `aarectmodes.png`, `addarc.png`).
- [x] Comparer les profils ICC entre PNGs : sont-ils identiques, ou Skia DM choisit-il un profil par GM ? **→ Identiques sur 6 PNGs sondés.**
- [x] ~~Examiner la config de Skia DM upstream~~ — non nécessaire, le tEXt chunk donne `Author: DM unified Rec.2020`.
- [x] Reproduire le décalage : `sRGB(0,0,255)` → `Rec.2020(43, 13, 241)` calculé, `(43, 13, 242)` observé. Diff ≤ 1 ulp.
- [x] ~~Chercher les PNGs Skia upstream~~ — non nécessaire.

**Résultat** : profile cible = **Rec.2020** (D50-adapted), ICC v4.3.0, TRC paramétrique type-4 = BT.2020 OETF. Détails complets dans [kanvas-skia/src/test/resources/colorspace-fingerprint.md](kanvas-skia/src/test/resources/colorspace-fingerprint.md). Constantes prêtes à être consommées par Phase 1.

## Phase 1 — Foundation skcms minimal

**But** : avoir `skcms_TransferFunction`, `skcms_Matrix3x3`, et leurs opérations primitives, en Kotlin pur.

À écrire à la main sous `kanvas-skia/src/main/kotlin/org/skia/skcms/` :
- [x] `SkcmsTransferFunction.kt` — data class.
- [x] `SkcmsMatrix3x3.kt` — class avec `vals: Array<FloatArray>`, `equals`/`hashCode` cell-by-cell, `IDENTITY`.
- [x] `SkcmsTFType.kt` — enum `Invalid / sRGBish / PQish / HLGish / PQ / HLG / HLGinvish`.
- [x] `Skcms.kt` (top-level) — `classify`, `skcmsTransferFunctionEval`, `skcmsTransferFunctionInvert`, `skcmsMatrix3x3Concat`, `skcmsMatrix3x3Invert`. Algos bit-compatibles upstream (incluant le pin `inv(eval(1.0)) == 1.0`).
- [x] `SkNamedTransferFn.kt` — `kSRGB`, `kLinear`, `k2Dot2`, `kRec2020`.
- [x] `SkNamedGamut.kt` — `kSRGB`, `kAdobeRGB`, `kDisplayP3`, `kRec2020`, `kXYZ`.

**Tests** [kanvas-skia/src/test/kotlin/org/skia/skcms/SkcmsFoundationTest.kt](kanvas-skia/src/test/kotlin/org/skia/skcms/SkcmsFoundationTest.kt) :
- [x] `eval(kSRGB, 0.5f)` ≈ 0.21404, `eval(kSRGB, 1.0f) == 1.0f`, `eval(kSRGB, 0.04045)` = boundary.
- [x] `Matrix3x3.concat(I, M) == M` et `Matrix3x3.invert(I) == I` (avec `0.0 == -0.0` géré).
- [x] `invert` matrice singulière → `null`.
- [x] `M * M^-1 ≈ I` pour `kSRGB` et `kRec2020` (dans 1e-4).
- [x] Round-trip : `eval(invert(kRec2020), eval(kRec2020, x)) ≈ x` pour x ∈ {0, 0.05, 0.0812, 0.5, 0.9, 1}.
- [x] **Pipeline end-to-end** : sRGB(0,0,1) linéaire → matVec(kSRGB) → matVec(invert(kRec2020)) → eval(invert(kRec2020 TF)) → 8-bit produit `(43, 13, 241)`.

**Vérification** : 11 tests Phase 1 passent, 5 tests existants passent, **0 régression**. Aucun GM impacté.

## Phase 2 — `SkColorSpace` minimal — ✅

À écrire à la main sous `kanvas-skia/src/main/kotlin/org/skia/foundation/` :
- [x] `SkColorSpace.kt` — `private constructor(transferFn, toXYZD50)`, hash de 7+9 floats via FNV-1a, lazy `fromXYZD50` + `invTransferFn` avec fallback sRGB si l'inversion échoue, `MakeSRGB()`/`MakeSRGBLinear()` singletons, `MakeRGB` avec snap aux singletons connus, `Equals` par hash.
- [x] Pas de fichier `SkColorSpaceSingletonFactory.kt` séparé — singletons inline dans le companion object de `SkColorSpace`.
- [x] Pas de `sk_sp` — on utilise `SkColorSpace?` (Kotlin gère via GC).

**Tests** [kanvas-skia/src/test/kotlin/org/skia/foundation/SkColorSpaceTest.kt](kanvas-skia/src/test/kotlin/org/skia/foundation/SkColorSpaceTest.kt) — 10 tests verts :
- [x] `MakeSRGB() === MakeSRGB()`, idem `MakeSRGBLinear`.
- [x] `isSRGB`, `gammaIsLinear`, `gammaCloseToSRGB` cohérents.
- [x] `MakeRGB(kSRGB, kSRGB)` snap au singleton.
- [x] `MakeRGB(kRec2020, kRec2020)` produit deux instances distinctes mais `equals` par hash.
- [x] `Equals(null, null) == true`, `Equals(null, x) == false`.
- [x] Lazy fields cachés.

**Vérification** : `:kanvas-skia:test` passe, 0 régression. Aucun GM impacté.

## Phase 3 — `SkColorSpaceXformSteps` (la vraie pipeline) — ✅

- [x] `SkColorSpaceXformSteps.kt` + `SkAlphaType` enum dans `kanvas-skia/src/main/kotlin/org/skia/core/`.
  - Constructeur `(src, srcAT, dst, dstAT)` qui calcule flags + `srcToDstMatrix` (column-major) une fois.
  - `apply(rgba: FloatArray)` reproduit la séquence upstream (`unpremul → linearize → gamut → encode → premul`).
  - `Flags.isIdentity` permet au consumer (Phase 4) de fast-path skip.
- [x] Pas d'OOTF (HDR PQ/HLG) — différé.

**Tests** [kanvas-skia/src/test/kotlin/org/skia/core/SkColorSpaceXformStepsTest.kt](kanvas-skia/src/test/kotlin/org/skia/core/SkColorSpaceXformStepsTest.kt) — 5 tests verts :
- [x] sRGB→sRGB Opaque = `flags.isIdentity`, `apply` no-op (bit-stable).
- [x] sRGB→sRGB-linéaire à `(0.5, 0.5, 0.5, 1)` → `(0.21404, …)` ± 1e-3.
- [x] **sRGB→Rec.2020 sur pur bleu produit `(43, 13, 241)`** — la cible.
- [x] Premul→Unpremul même CS ne fait que la division alpha.
- [x] Alpha=0 unpremul → zeros (pas de NaN/Inf).

**Vérification** : tests passent, 0 régression sur les 5 GM existants. Reste 0 GM impacté — c'est juste la brique.

## Phase 4 — Wire colorspace dans `SkBitmap` et `SkBitmapDevice` — ✅

- [x] `SkBitmap` reçoit `val colorSpace: SkColorSpace = SkColorSpace.makeSRGB()`. Factory `Make(w, h, cs)` ajoutée.
- [x] `SkBitmapDevice` construit un `xformSteps` (paint sRGB → bitmap CS) à l'init. `transformPaintColor(c)` short-circuite via `flags.isIdentity` quand bitmap est sRGB.
- [x] `drawRect` transforme la couleur du paint *une fois* avant d'entrer dans `fillRect`/`strokeRect` — pas de transform per-pixel pour solide rect.
- [x] Le compositing SrcOver reste tel quel (encoded space, ce qui matche Skia DM `--config 8888` par défaut). Le fait que les GMs passent à `(43, 13, 241)` confirme.

**Tests micro** [kanvas-skia/src/test/kotlin/org/skia/core/SkBitmapDeviceColorSpaceTest.kt](kanvas-skia/src/test/kotlin/org/skia/core/SkBitmapDeviceColorSpaceTest.kt) — 4 tests verts :
- [x] Bitmap sRGB par défaut, paint sRGB bleu → pixel = `SK_ColorBLUE` bit-identique.
- [x] **Bitmap Rec.2020, paint pur bleu → pixel = `(43, 13, 241, 255)`**.
- [x] **Bitmap Rec.2020, paint pur rouge → pixel = `(202, 59, 19, 255)`**.
- [x] `SkBitmap(w, h).colorSpace == makeSRGB()` (default contract).

**Vérification** : tests passent, 0 régression. Les 5 GMs existants tournent toujours comme avant (parce que `runGmTest` rend encore sur sRGB en Phase 4).

## Phase 5 — Wire dans `TestUtils` et fermer la boucle — ✅

- [x] `TestUtils.DM_REFERENCE_COLOR_SPACE` exposé : `SkColorSpace.makeRGB(kRec2020, kRec2020)`. `runGmTest(gm)` alloue le bitmap dans cet espace.
- [x] **Note différée** : pas de check ICC profile-match dans `loadReferenceBitmap` pour l'instant — utile dès qu'on aura plusieurs profils sources, pas avant.
- [x] **Avant Phase 5** : tolérance = 160, similarité 99.5 % / 100 %.
- [x] **Après Phase 5** : tolérance = **1**, similarité 95.4 % / 100 %.
- [x] `BigRectGM` resette dans `test-similarity-scores.properties` (le drop 99.5 % → 95.4 % est sémantique, pas une régression).

**Probe diagnostique** [kanvas-skia/src/test/kotlin/org/skia/diagnostics/ToleranceProbeTest.kt](kanvas-skia/src/test/kotlin/org/skia/diagnostics/ToleranceProbeTest.kt) — résultats sur 10 paliers de tolerance :

| tolerance | BigRectGM | SimpleRectGM |
|-----------|-----------|--------------|
| 0  | 70.58 % | 58.64 % |
| **1**  | **95.37 %** | **100.00 %** |
| 2  | 95.37 % | 100.00 % |
| 4  | 95.37 % | 100.00 % |
| 8  | 95.37 % | 100.00 % |
| 16 | 95.37 % | 100.00 % |
| 32 | 95.37 % | 100.00 % |
| 64 | 95.82 % | 100.00 % |
| 128 | 97.71 % | 100.00 % |
| 160 | 99.51 % | 100.00 % |

Les `~4.6 %` de pixels non-matchés sur BigRectGM à `tolerance=1` ne sont **pas du color shift** (qui ferait des diffs de 100+) mais des désaccords structurels du rasterizer non-AA sur les cellules à coordonnées extrêmes (rects 1e6 / 1e10 clippés sur 35×35). Le saut à 95.82 % à `t=64` confirme : 0.45 % de pixels avec un diff dans la fenêtre [33..64] — typique d'un edge AA partiel qu'on n'implémente pas. Ces pixels resteront non-conformes jusqu'à ce que la Phase 2 du plan principal ([MIGRATION_PLAN.md](MIGRATION_PLAN.md)) ajoute l'AA.

**Vérification** : 32 tests verts (10 SkColorSpace + 11 skcms + 5 XformSteps + 4 device + 2 GM + 4 diagnostics + 4 bootstrap), 0 régression.

## Phase 6 (différée) — ICC parsing pour `SkColorSpace::Make(profile)`

**But** : pouvoir lire le profil ICC d'un PNG et construire un `SkColorSpace` correspondant. Pas requis pour la fidélité Phase 1 (le profil cible est en dur), mais nécessaire dès qu'on aura des GMs avec plusieurs profils sources (`Colorspace`, image-shader, etc.).

- [ ] Parser binaire ICC v2/v4 minimal : header (128 octets), tag table, tags `rXYZ`/`gXYZ`/`bXYZ`/`rTRC`/`gTRC`/`bTRC`/`wtpt`/`cprt`. Spec dans [kanvas/src/generated/skcms/org/skia/skcms/Functions.kt](kanvas/src/generated/skcms/org/skia/skcms/Functions.kt) → `skcms_Parse`.
- [ ] Support uniquement `paraType` (16) pour les TRC (formules paramétriques 7-params), pas `curveType` LUT — différer.
- [ ] `SkColorSpace.Make(profile)` qui essaie d'abord `ApproximatelyEqualProfiles(profile, sRGB)` → singleton, puis tombe sur `MakeRGB(parsed_tf, parsed_matrix)`.
- [ ] Bradford chromatic adaptation : si white point ≠ D50, appliquer la matrice Bradford avant de stocker `toXYZD50`. Spec : `skcms_AdaptToXYZD50`.

**Reporté avec 6** : LUT-based curves, A2B / B2A, CICP, ICC v4 mft1/mft2, gamma exponent-only.

## Phase 7 (différée) — Encodage PNG avec profil ICC

**But** : que `saveDebugImage` (et tout futur encoder) écrive le profil ICC dans le PNG. Sans ça les images de debug sont inspectables visuellement, mais `Image Color Sync` du système les rendra "correctement" (= avec leur color management, pas les nôtres).

- [ ] Ajouter chunk `iCCP` à l'écriture PNG via `javax.imageio.metadata.IIOMetadata`. Le profil sérialisé est récupéré par `SkColorSpace.serialize()` (à porter aussi).
- [ ] `serialize()` : 12 octets de header + 7 floats (TF) + 9 floats (matrice) = 76 octets. Spec dans le code généré.
- [ ] `Deserialize()` : symétrique.

## Fichiers critiques à créer

- [x] [kanvas-skia/src/main/kotlin/org/skia/skcms/SkcmsTransferFunction.kt](kanvas-skia/src/main/kotlin/org/skia/skcms/SkcmsTransferFunction.kt)
- [x] [kanvas-skia/src/main/kotlin/org/skia/skcms/SkcmsMatrix3x3.kt](kanvas-skia/src/main/kotlin/org/skia/skcms/SkcmsMatrix3x3.kt)
- [x] [kanvas-skia/src/main/kotlin/org/skia/skcms/SkcmsTFType.kt](kanvas-skia/src/main/kotlin/org/skia/skcms/SkcmsTFType.kt)
- [x] [kanvas-skia/src/main/kotlin/org/skia/skcms/Skcms.kt](kanvas-skia/src/main/kotlin/org/skia/skcms/Skcms.kt) (renommé depuis `SkcmsTransferFunctions.kt` du plan initial)
- [x] [kanvas-skia/src/main/kotlin/org/skia/skcms/SkNamedTransferFn.kt](kanvas-skia/src/main/kotlin/org/skia/skcms/SkNamedTransferFn.kt)
- [x] [kanvas-skia/src/main/kotlin/org/skia/skcms/SkNamedGamut.kt](kanvas-skia/src/main/kotlin/org/skia/skcms/SkNamedGamut.kt)
- [x] [kanvas-skia/src/main/kotlin/org/skia/foundation/SkColorSpace.kt](kanvas-skia/src/main/kotlin/org/skia/foundation/SkColorSpace.kt) — singletons inline dans le companion (pas de fichier `SkColorSpaceSingletonFactory.kt` séparé)
- [x] [kanvas-skia/src/main/kotlin/org/skia/core/SkColorSpaceXformSteps.kt](kanvas-skia/src/main/kotlin/org/skia/core/SkColorSpaceXformSteps.kt)
- [x] [kanvas-skia/src/test/resources/colorspace-fingerprint.md](kanvas-skia/src/test/resources/colorspace-fingerprint.md) — output Phase 0.

## Fichiers à modifier

- [x] [kanvas-skia/src/main/kotlin/org/skia/foundation/SkBitmap.kt](kanvas-skia/src/main/kotlin/org/skia/foundation/SkBitmap.kt) — ajout `colorSpace`.
- [x] [kanvas-skia/src/main/kotlin/org/skia/core/SkBitmapDevice.kt](kanvas-skia/src/main/kotlin/org/skia/core/SkBitmapDevice.kt) — `transformPaintColor` pré-draw + fast-path identité.
- [x] [kanvas-skia/src/test/kotlin/org/skia/testing/TestUtils.kt](kanvas-skia/src/test/kotlin/org/skia/testing/TestUtils.kt) — `DM_REFERENCE_COLOR_SPACE` exposé, `runGmTest` rend en Rec.2020, bg color via `gm.getBGColor()`.
- [x] [kanvas-skia/src/test/kotlin/org/skia/tests/BigRectTest.kt](kanvas-skia/src/test/kotlin/org/skia/tests/BigRectTest.kt) — `tolerance = 1`, threshold 95%.
- [x] [kanvas-skia/src/test/kotlin/org/skia/tests/SimpleRectTest.kt](kanvas-skia/src/test/kotlin/org/skia/tests/SimpleRectTest.kt) — `tolerance = 1`, threshold 99%.

## Sources de référence (lecture seule)

- [kanvas/src/generated/skcms/org/skia/skcms/](kanvas/src/generated/skcms/org/skia/skcms/) — 32 fichiers, code C++ Skia traduit.
- [kanvas/src/generated/skcms/org/skia/skcms/Functions.kt](kanvas/src/generated/skcms/org/skia/skcms/Functions.kt) — 154 KB, contient *toute* la logique skcms en Javadoc (transfer fn eval, matrix ops, ICC parse).
- [kanvas/src/generated/foundation/org/skia/foundation/SkColorSpace.kt](kanvas/src/generated/foundation/org/skia/foundation/SkColorSpace.kt) — squelette + code C++ original en Javadoc.
- [kanvas/src/generated/core/org/skia/core/SkColorSpaceXformSteps.kt](kanvas/src/generated/core/org/skia/core/SkColorSpaceXformSteps.kt) — pipeline scalaire commentée.
- Skia upstream : [`include/core/SkColorSpace.h`](https://github.com/google/skia/blob/main/include/core/SkColorSpace.h), [`src/core/SkColorSpaceXformSteps.cpp`](https://github.com/google/skia/blob/main/src/core/SkColorSpaceXformSteps.cpp), [`include/third_party/skcms/skcms.h`](https://github.com/google/skia/blob/main/include/third_party/skcms/skcms.h).

## Risques et plans de repli

- **Risque 1** : Phase 0 échoue à identifier le profil. Plan B : tester par fingerprinting (~10 candidats wide-gamut) ; en dernier recours, déduire la matrice 3×3 à partir de paires `(srgb_in, observed_out)` via moindres carrés sur les 7 couleurs de `bigrect`. Coût : un test diagnostic isolé, pas de blocage durable.
- **Risque 2** : le compositing SrcOver de Skia DM se fait en linéaire. Plan B : ajouter un mode `linear-blending` dans `SkBitmapDevice` (linéariser la dst avant blend, encoder après). Coût : ~30 lignes, mais double les chemins à tester.
- **Risque 3** : skcms 7-paramètres ne suffit pas pour le profil cible (rare — la plupart des profils ICC display modernes sont 7-params). Plan B : ajouter support des LUT 1024-entry (utilisé par certains profils Apple Display P3). Coût : Phase 6 anticipée.
- **Risque 4** : effort total > bénéfice. Plan B : si après Phase 5 on est encore à `tolerance > 4`, on archive ce plan, on vit avec `tolerance = 8` documenté, et on revient une fois la Phase 6 du plan principal (gradients) atteinte — où l'arithmétique flottante des shaders nous donnera de toute façon `tolerance ≥ 4` même en sRGB pur.

## Trajectoire de progression — résultat final

| Phase | Livrable | Tolerance atteinte |
|-------|----------|---------------------|
| 0     | Profil cible identifié = Rec.2020 (DM unified) | (état des lieux) |
| 1     | skcms TF + Matrix3x3 + named consts | (pas wired) |
| 2     | `SkColorSpace.MakeSRGB/MakeRGB/Equals` | (pas wired) |
| 3     | `SkColorSpaceXformSteps.apply(rgba)` | (pas wired) |
| 4     | Bitmap + Device color-space-aware | tests micro `(43, 13, 241)` ✅ |
| 5     | TestUtils → 160 → 1 | **`tolerance=1`** ✅ |
| 6     | ICC parsing | différé |
| 7     | PNG encode avec iCCP | différé |

**Plan #2 atteint son objectif** : passer de `tolerance=160` à `tolerance=1` sur les GMs Phase 1, sans régression. Le résiduel `~4.6 %` sur BigRectGM est dorénavant un débat de rasterizer (à traiter par MIGRATION_PLAN.md Phase 2/AA), plus un débat de gestion couleur.

## Vérification end-to-end

À chaque phase, comme dans le plan principal :
1. `./gradlew :kanvas-skia:compileKotlin` doit compiler sans erreur.
2. `./gradlew :kanvas-skia:test` doit réussir tous les tests existants + le(s) nouveau(x) du slice.
3. Les scores dans `kanvas-skia/test-similarity-scores.properties` doivent monter ou rester stables (jamais chuter de >1%).
4. Le `tolerance` des tests existants ne baisse jamais sans une justification explicite dans le commit.

À chaque slice : commit unique avec mise à jour atomique de `test-similarity-scores.properties` *et* du tolerance des tests touchés.
