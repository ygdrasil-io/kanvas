# MIGRATION_PLAN_TEXT.md — Side plan : rendu de texte (`SkFont` / `SkTypeface` / `drawString`)

> **Statut** : draft, document only. Aucune ligne de code Kotlin écrite. À valider avant de démarrer la première slice.

## Contexte

À la clôture de Phase 6 (29/29 blend modes), beaucoup de GMs upstream restent inaccessibles parce qu'ils utilisent du texte — soit comme **contenu** (sujet du test : `bigtext`, `coloremoji`), soit comme **annotation** (titres / labels de cellules : `xfermodes`, `androidblendmodes`, `lcdblendmodes`, `aaxfermodes`, etc.).

Mesure rapide : **136 GMs upstream importent `SkFont.h` ou utilisent `drawString`/`drawTextBlob`/`drawSimpleText`/`SkTextUtils::DrawString`**. Beaucoup d'autres GMs portent des chaînes inline dans des labels qui n'affectent pas le contenu central — porter ces GMs sans texte rendrait des panneaux blancs à la place des labels, mais le **scoring** ne souffrirait pas si on ignorait les bandes de label dans la comparison.

Notre objectif : débloquer cette famille avec une approche **incrémentale et pragmatique**, pas chercher le bit-perfect upstream du jour 1.

## Pourquoi un side plan ?

Le rendu de texte est orthogonal à tout le reste du `MIGRATION_PLAN.md` :
- aucune dépendance bidirectionnelle avec rasterizer / shaders / blend modes ;
- introduit un module entièrement nouveau (`SkTypeface`, `SkFont`, glyph cache, font loader) ;
- les choix architecturaux (backend AWT vs FreeType vs HarfBuzz vs custom) ont des implications longue durée.

Garder le plan séparé permet de l'avancer en parallèle de slice 5g/5h-postmortem/portage GMs sans coupler les revues.

## API Skia visée (sous-ensemble minimal)

Pour exécuter les GMs en scope on a besoin de :

```cpp
// Création
SkFont font;                                     // default, size 12
SkFont font(typeface, size);                     // explicit
SkFont font(other);                              // copy ctor (used by aaxfermodes)
font.setTypeface(typeface);
font.setSize(size);
font.setEdging(SkFont::Edging::kAntiAlias);      // kAlias / kAntiAlias / kSubpixelAntiAlias
font.setSubpixel(true);

// Mesure
font.measureText(text, kUTF8_SkTextEncoding, &bounds);
font.getMetrics(&metrics);                       // ascent/descent/leading

// Rendu (canvas-side)
canvas->drawString(string, x, y, font, paint);
SkTextUtils::DrawString(canvas, str, x, y, font, paint, kCenter_Align);

// Typeface portable (utilities)
ToolUtils::DefaultPortableTypeface();            // sk_sp<SkTypeface>
ToolUtils::DefaultPortableFont();                // SkFont avec size par défaut
```

Hors scope (par phase) :
- `SkShaper`, `SkTextBlobBuilder` complet (texte mis en page complexe — paragraph, BiDi, etc.)
- Color emoji, COLR/CBDT, OpenType variations.
- LCD subpixel anti-aliasing.
- `SkSVGDOM` text, `Paragraph` (modules upstream).

## Backend de rasterisation : trois options

### A. AWT (`java.awt.Font` + `Font.createGlyphVector`)

**Pros** :
- Disponible direct sur la JVM, zéro nouvelle dépendance.
- Utilisable pour mesurer (`FontMetrics`, `getStringBounds`) ET pour rasteriser (via `Graphics2D.drawString` sur un `BufferedImage` mask, ou `GlyphVector.getGlyphOutline` → `Shape` → `SkPath` puis pipeline path existant).
- Polices système trouvables par `Font.createFont(TRUETYPE_FONT, file)` — on peut charger les TTF de Skia (`resources/fonts/Roboto-Regular.ttf` etc.) pour matcher upstream au mieux.

**Cons** :
- Le rasterizer AWT n'est **pas bit-exact** avec FreeType (utilisé par Skia DM). Les glyphes auront ~1-2 ulp de différence sur les bords AA.
- Subpixel positioning AWT diffère de Skia ; si on pousse à 100 % de match, il faudra un rasterizer custom.

**Verdict** : **option par défaut**. Fournit du rendu visuellement correct rapidement, suffisant pour > 90 % de similarité sur les labels. Bit-exact est un follow-up.

### B. FreeType + JNI

**Pros** :
- Bit-exact avec Skia upstream (qui utilise FreeType en raster).
- Backend de référence.

**Cons** :
- Dépendance JNI : doit fournir un `.dylib` / `.so` / `.dll` par plateforme. Casse la portabilité Kotlin/JVM pure.
- Code natif à wrapper.
- À 100 % d'engineering supplémentaire pour gain marginal sur notre échelle.

**Verdict** : **rejeté pour l'instant**, on accepte un drift d'1-2 ulps sur les bords AA pour rester JVM-pur.

### C. Custom rasterizer + TTF parser pur Kotlin

**Pros** :
- Pas de dépendance native.
- Bit-exact possible si on s'aligne sur FreeType (très ambitieux).

**Cons** :
- Mois de travail pour parser TTF + faire un rasterizer compétitif. Out of scope pour un side plan.

**Verdict** : **rejeté**. Trop d'effort.

## Décision

**Backend : AWT** (option A). On accepte 1-2 ulps de drift sur les bords AA des glyphes, en échange d'un effort minimal et d'une portabilité totale.

L'architecture interne reste alignée sur Skia : `SkTypeface` est un abstrait, l'impl AWT (`AwtTypeface`) en est une réalisation parmi d'autres. Si on veut un FreeType backend plus tard, on ajoute `FreetypeTypeface` sans toucher l'API publique.

## Contrainte de design : façade Skia, implémentation AWT

**Règle d'or** : **toute API publique exposée doit reproduire fidèlement la surface Skia upstream** (noms de classes, noms de méthodes, signatures, énumérations, valeurs par défaut). On ne renomme pas, on ne simplifie pas, on ne « kotlinise » pas l'API. Si Skia expose `SkFont::setEdging(Edging)`, on expose `SkFont.setEdging(edging: Edging)` — pas `SkFont.edging = ...` même si c'est plus idiomatique en Kotlin.

**Justification** : permet de droper les fichiers générés (`kanvas/src/generated/tests/.../*.kt`) sans modification, même mécanique que pour `GM.getISize()` en Phase 0. Garde aussi la possibilité future de ports automatisés (`.cpp` → `.kt`) sans transformations.

**Implémentation** : pour le backend texte spécifiquement, **toute la logique de rendu/mesure passe par `java.awt.*`** (Font, GlyphVector, FontMetrics, Shape, PathIterator). C'est un choix pragmatique de portabilité JVM-pure ; **ce n'est pas l'implémentation Skia**.

### Convention de documentation (obligatoire dans chaque fichier impl)

Chaque fichier `.kt` qui *contient de la logique AWT* (par opposition à un simple data class qui mirroir une struct Skia) doit porter en tête de fichier le bloc suivant, en toutes lettres :

```kotlin
/**
 * **NOTE D'IMPLÉMENTATION** — Ce fichier expose la surface API Skia
 * (`SkFont` / `SkTypeface` / `SkFontMetrics` / …) mais l'implémentation
 * sous-jacente repose sur **`java.awt.Font` + `java.awt.font.GlyphVector`**,
 * pas sur le moteur de fontes natif Skia (FreeType + SkScalerContext).
 *
 * Conséquences :
 *  - Les métriques peuvent diverger de 1-2 ulps des valeurs upstream.
 *  - Le rasterizer AA des glyphes est celui d'AWT (relayé via SkPath →
 *    notre scanline-fill 4×4), pas le rasterizer FreeType de Skia.
 *  - `SkFont.Edging.kSubpixelAntiAlias` est dégradé silencieusement vers
 *    `kAntiAlias` (cf. MIGRATION_PLAN_TEXT.md §R3).
 *
 * Si un jour on remplace AWT par FreeType+JNI ou par un rasterizer custom,
 * **seul ce fichier (et ses pairs `Awt*.kt`) doit changer** — l'API publique
 * reste figée sur la signature Skia.
 */
```

Sur les fichiers qui sont de purs *value types* miroir d'une struct Skia (`SkFontMetrics`, `SkTextEncoding`, …) on se contente d'une référence Javadoc à la définition upstream sans le bloc d'avertissement, puisqu'ils ne contiennent pas d'implémentation AWT.

### Découpage de packages

| Package | Rôle | Backend ? |
|---------|------|-----------|
| `org.skia.foundation` | API publique : `SkFont`, `SkTypeface`, `SkFontMetrics`, `SkTextEncoding`, `SkFont.Edging` | indépendant |
| `org.skia.foundation.awt` (interne) | Impls AWT : `AwtTypeface`, `AwtGlyphRasterizer`, helpers | AWT-spécifique |
| `org.skia.tools` | `ToolUtils.DefaultPortableTypeface()` etc. | délègue à `awt.*` |

L'API publique (`org.skia.foundation`) est en théorie *backend-agnostic*. Tout fichier sous `org.skia.foundation.awt` porte le bloc d'avertissement ci-dessus en tête.

## Slices T1 → T5

### Slice T1 — API stub : `SkFont` / `SkTypeface` / `drawString` no-op

**But** : permettre la **compilation** et l'**exécution** sans crash des GMs textuels existants. Le texte ne s'affiche pas mais le test ne plante plus.

**API ajoutée (surface Skia fidèle, package `org.skia.foundation`)** :
- `SkTypeface` — classe ouverte (pas data class : Skia la traite comme polymorphique). Méthodes minimales : `getFontStyle(): SkFontStyle` (renvoie `Normal` par défaut), `MakeDefault()` companion qui retourne un singleton vide.
- `SkFontStyle` — data class miroir : `weight`, `width`, `slant` + companion `Normal`/`Bold`/`Italic`/`BoldItalic`.
- `SkFont` — classe (mutable, comme Skia) avec setters/getters typed : `setTypeface(t: SkTypeface)` / `getTypeface(): SkTypeface`, `setSize(s: SkScalar)` / `getSize(): SkScalar`, `setEdging(e: Edging)` / `getEdging(): Edging`, `setSubpixel(b: Boolean)` / `isSubpixel(): Boolean`. Constructeurs : `SkFont()`, `SkFont(typeface)`, `SkFont(typeface, size)`, `SkFont(other)` (copy).
- `SkFont.Edging` — enum imbriqué `kAlias` / `kAntiAlias` / `kSubpixelAntiAlias`.
- `SkTextEncoding` — enum `kUTF8` / `kUTF16` / `kUTF32` / `kGlyphID` (pour signature compat plus tard).
- `SkCanvas.drawString(text: String, x: SkScalar, y: SkScalar, font: SkFont, paint: SkPaint)` — **no-op** dans T1.
- `SkCanvas.drawSimpleText(text, byteLength, encoding, x, y, font, paint)` — **no-op**, signature qui mirroir l'upstream pour les futures slices.
- `org.skia.tools.ToolUtils.DefaultPortableTypeface(): SkTypeface` — retourne le singleton.
- `org.skia.tools.ToolUtils.DefaultPortableFont(size: SkScalar = 12f): SkFont` — convenience.

**Implémentation interne (T1 minimal)** :
- Aucun fichier `org.skia.foundation.awt.*` créé en T1 — pas encore de logique AWT.
- `SkFont` / `SkTypeface` ont des bodies vides ou triviaux, sans `import java.awt.*`.
- **Pas besoin du bloc d'avertissement T1** puisqu'aucun fichier ne contient d'AWT.

**Tests** : un test unitaire qui instancie `SkFont`/`SkTypeface`, exerce les ctors copy/Skia-like, et appelle `drawString` sans crash.

**GMs débloqués** : aucun directement (le texte ne s'affiche pas, donc les zones de label restent BG). Mais ça permet de **porter** les GMs textuels sans erreur de compilation.

**Critères de réussite** :
- [ ] `./gradlew :kanvas-skia:compileKotlin` passe avec les nouvelles API.
- [ ] Un test unitaire vérifie que `SkCanvas.drawString` est silencieux.
- [ ] Les 65 GMs existants — 0 régression.

**Effort estimé** : 1 slice court (~150 lignes Kotlin + 1 test).

---

### Slice T2 — Mesure de texte (sans rendu glyphe)

**But** : implémenter `SkFont.measureText` et `SkFont.getMetrics` via AWT (`FontMetrics`, `Font.getStringBounds`). `drawString` reste no-op mais maintenant les GMs qui calculent un layout autour du texte (centrage, cellules redimensionnées en fonction du texte) auront les bonnes coordonnées.

**API ajoutée (surface Skia fidèle, `org.skia.foundation`)** :
- `SkFont.measureText(text: String, byteLength: Int = text.length, encoding: SkTextEncoding = SkTextEncoding.kUTF8, bounds: SkRect? = null): SkScalar` — signature mirroir de `SkFont::measureText` upstream.
- `SkFont.getMetrics(metrics: SkFontMetrics): SkScalar` — retourne `recommendedLineSpacing`, remplit `metrics`. Mirroir Skia.
- `SkFontMetrics` — data class (mutable via `var` pour matcher le pattern out-param Skia) : `top`, `ascent`, `descent`, `bottom`, `leading`, `avgCharWidth`, `maxCharWidth`, `xMin`, `xMax`, `xHeight`, `capHeight`, `underlineThickness`, `underlinePosition`, `strikeoutThickness`, `strikeoutPosition`. Pas de bloc d'avertissement (pure data).

**Backend AWT (nouveau package interne `org.skia.foundation.awt`)** :
- `AwtTypeface : SkTypeface` — sous-classe interne. Lazy-load un `java.awt.Font` (par défaut `Font.SANS_SERIF, Font.PLAIN, 1` puis `deriveFont(size)`).
- `AwtFontMetricsCalculator` — helper qui mappe `java.awt.font.LineMetrics` + `FontMetrics` vers `SkFontMetrics`.
- **`FontRenderContext` configuré une fois** (`anti-aliasing=ON`, `fractional-metrics=ON`) pour cohérence inter-OS.
- **Bloc d'avertissement obligatoire** en tête de chaque fichier sous `org.skia.foundation.awt.*`.

**GMs débloqués** : ceux qui calculent leur layout via `measureText`. Score-wise : pas de gain, juste de la cohérence de mise en page.

**Critères de réussite** :
- [ ] `measureText` et `getMetrics` retournent des valeurs cohérentes (smoke tests : `measureText("XX") ≈ 2 × measureText("X")` à ~5 % près).
- [ ] Tous les fichiers `org.skia.foundation.awt.*` portent le bloc d'avertissement.
- [ ] 65 + nouveaux GMs — 0 régression.

**Effort estimé** : 1 slice modéré (~250 lignes + tests).

---

### Slice T3 — Rendu glyphe via AWT

**But** : `drawString` rasterise effectivement le texte sur le canvas via AWT. Les labels apparaissent. Score sur les GMs textuels-mineurs (où le texte est juste annotation) doit grimper sensiblement.

**Approche** :
1. Pour chaque glyphe d'une chaîne : `Font.createGlyphVector(frc, char).getGlyphOutline(i)` → `java.awt.Shape`.
2. Convertir le `Shape` en `SkPath` via `PathIterator` (lines, quads, cubics).
3. Appliquer le `SkCanvas` CTM + position du baseline.
4. Router via le pipeline `drawPath` existant (fillPath avec `paint.color`, AA selon `font.edging`).

**Pros de cette approche** : 0 nouveau rasterizer (on réutilise notre scanline-fill AA + le pipeline Phase 6 blend modes). Le texte hérite automatiquement des supports complets `paint.shader`, `paint.blendMode`, etc.

**Limites** :
- Lent (path-fill par glyphe, pas de cache mask). Acceptable pour notre échelle de tests.
- Pas de hinting custom (on hérite du hinter AWT). Suffisant pour `tolerance = 8` ou plus.
- Pas de subpixel AA (kSubpixelAntiAlias dégradé en kAntiAlias).

**API ajoutée (surface Skia fidèle)** :
- `SkCanvas.drawString(text, x, y, font, paint)` — vrai rendu (signature inchangée depuis T1).
- `SkFont.getPath(glyphId: UShort, path: SkPath): Boolean` — mirroir `SkFont::getPath` upstream, retourne `true` si le glyph a un path.
- `SkFont.unicharsToGlyphs(uni: IntArray, count: Int, glyphs: ShortArray)` — mirroir `SkFont::unicharsToGlyphs`, optionnel selon les besoins T3.

**Implémentation interne (`org.skia.foundation.awt.*`)** :
- `AwtGlyphRasterizer` — orchestre `GlyphVector.getGlyphOutline` → `PathIterator` → `SkPath`.
- `AwtPathConverter` — helper pur qui convertit un `java.awt.Shape` en `SkPath` (`SEG_MOVETO`/`SEG_LINETO`/`SEG_QUADTO`/`SEG_CUBICTO`/`SEG_CLOSE`).
- **Bloc d'avertissement obligatoire** dans tous les nouveaux fichiers AWT.
- `SkCanvas.drawString` lui-même reste dans `org.skia.core` mais **délègue** à un helper interne `org.skia.foundation.awt.AwtGlyphRasterizer.drawString(canvas, text, x, y, font, paint)`. La logique AWT n'apparaît jamais dans `org.skia.core.SkCanvas` — elle reste cantonnée au sous-package `awt`.

**GMs débloqués (estimation)** :
- `XfermodesGM` (29 modes en grille, labels = noms des modes) — déjà 80% si labels divergent légèrement, avec texte ça devrait grimper à >90%.
- `AndroidBlendModesGM`, `AaXfermodesGM` — similaires.
- `BigRectGM`-style GMs avec text annotations — pas significatif (ils étaient déjà bien).
- ~40-60 nouveaux GMs portables (estimation grossière).

**Critères de réussite** :
- [ ] Un GM minimal de smoke test (`HelloWorldGM` interne) qui dessine un texte sur fond blanc et obtient ≥ 80 % de match contre une référence dessinée manuellement (pas upstream — référence interne).
- [ ] Port d'un GM upstream qui exerce drawString minimalement (par ex. `crbug_788500` ou similar) — score à au moins 70 %.
- [ ] Aucun `import java.awt.*` dans `org.skia.core.*` ou `org.skia.foundation.*` (hors sous-package `awt`).
- [ ] 0 régression sur les 65 GMs existants.

**Effort estimé** : 1 slice modéré-élevé (~400 lignes Kotlin + tests + 1-2 GM ports).

---

### Slice T4 — Polices portables Skia upstream

**But** : charger les mêmes TTF que Skia DM (`Roboto-Regular.ttf`, `DejaVuSans.ttf`, etc.) via `Font.createFont(TRUETYPE_FONT, file)` pour matcher la *forme* des glyphes upstream. Le rasterizer reste AWT donc le bit-exact n'est toujours pas garanti, mais on s'en rapproche significativement.

**Travail** :
- Copier `skia-main/resources/fonts/{Roboto-Regular,DejaVuSans.subset,Liberation*}.ttf` dans `kanvas-skia/src/main/resources/fonts/`.
- Implémenter un `PortableFontMgr` qui maps `family name` → `font file` selon le mapping Skia DM (`SkFontMgr_Custom`).
- `ToolUtils.DefaultPortableTypeface` charge `Roboto-Regular.ttf` (ou son équivalent) en cache.

**GMs débloqués (incrémental sur T3)** : tous les GMs qui utilisent `DefaultPortableTypeface` — la majorité. Les scores sur les GMs label-heavy devraient grimper de 5-15 % par rapport à T3 (mêmes formes de glyphes).

**Critères de réussite** :
- [ ] Les TTF embarqués chargent sans erreur en classpath.
- [ ] Score sur 1-2 GMs textual-heavy déjà portés en T3 grimpe de ≥ 5 %.

**Effort estimé** : 1 slice moyen (~150 lignes Kotlin + ressources).

---

### Slice T5 — Glyph cache + AA précision

**But** : optimisations + fidélité.

- **Glyph cache** : memoize `(typeface, size, glyphId, edging) → bitmap mask`. Évite la re-rasterisation. Important pour les GMs avec beaucoup de répétitions de mêmes caractères.
- **AA edges** : aligner le rendu AA des glyphes sur ce que Skia DM fait — le path-fill scanline 4×4 supersampling devrait suffire ; à valider par GM.
- **Subpixel positioning** : `font.isSubpixel = true` permet à Skia de ne pas snap les glyphes sur la grille pixel. AWT supporte (via `KEY_FRACTIONALMETRICS`).

**Effort estimé** : 1 slice moyen-élevé.

---

## GMs débloqués (estimation par slice)

| Slice | GMs accessibles (cumul) | Note |
|-------|--------------------------|------|
| T0 (actuel) | 0 | aucun GM textual-blocking compile |
| T1 | 0 | infra mais no-op rendu |
| T2 | 0–5 | layout-aware GMs ; encore vide visuellement |
| T3 | 40–60 | premier vrai rendu glyphe ; gros débloquage |
| T4 | 40–80 | polices portable ⇒ scores plus hauts sur les déjà-portés |
| T5 | 80–130 | optimisation + finesse AA, scores plats sur déjà-portés |

> Les nombres exacts dépendent du seuil de `tolerance` qu'on accepte pour les GMs où le texte n'est pas le sujet central. Avec `tolerance = 8`, beaucoup plus passent ; à `tolerance = 1`, c'est plus serré.

## Risques / Open questions

### R1 — AWT et FreeType ne convergent pas sur les bords AA

- Probabilité : élevée (1-2 ulp sur ~10 % des pixels d'un glyphe).
- Mitigation : accepter `tolerance ≥ 8` pour les GMs textual-content ; pour les GMs où le texte est annotation, le delta sur les bords est invisible relative au contenu central.
- Pas de blocker.

### R2 — Polices système pour `Font.SANS_SERIF` etc. varient par OS

- Probabilité : élevée (macOS = San Francisco, Linux = DejaVu, Windows = Arial).
- Mitigation : T4 résout en chargeant les TTF Skia. Avant T4, considérer T1-T3 comme "smoke-only" pour les scores et ne pas paniquer sur des deltas.

### R3 — Subpixel AA divergence

- Probabilité : moyenne. Skia subpixel AA produit du LCD 3x ; AWT subpixel est différent.
- Mitigation : downgrade à `kAntiAlias` partout, ne pas implémenter `kSubpixelAntiAlias` avant T5+.

### R4 — `SkShaper` / texte arabe / BiDi / kerning OpenType

- Probabilité : 0 dans notre scope (les GMs en cible utilisent du latin simple).
- Mitigation : explicit out-of-scope.

### R5 — Coût mémoire des polices embarquées

- Roboto-Regular.ttf ≈ 170 KB, DejaVuSans.subset.ttf ≈ 200 KB. Total < 1 MB pour les ~5 polices nécessaires. Négligeable.

## Trajectoire suggérée

1. **T1** ASAP — débloque la compilation, faible risque, faible effort.
2. **T2** dans la foulée si on veut porter un GM avec layout texte précoce.
3. **T3** = le slice à fort impact. À planifier avec ~1-2 jours de buffer.
4. **T4** quand on commence à porter les `XfermodesGM` family — apporte le pixel-fidelity vs upstream.
5. **T5** opportuniste, quand un GM spécifique nécessite glyph cache ou subpixel.

## Parallélisme avec le `MIGRATION_PLAN.md` principal

- **Phase 7+ (post-Phase 6)** : reprendre `SKIA_DM_TESTS_TO_IMPLEMENT.md` Level 2, en parallèle. Le text plan ne bloque pas et n'est pas bloqué.
- **Phase 5h re-attempt** : si on revient sur le linear-premul F16 storage, T1 est utile pour porter `TinyBitmapGM` (qui n'a pas de texte) — pas de couplage.
- **Image filters / blurs** : `BlurMaskFilter` et compagnie sont indépendants du texte. Un side plan séparé.

## Ressources upstream

- [`skia-main/include/core/SkFont.h`](file:///Users/chaos/workspace/kanvas-forge/skia-main/include/core/SkFont.h) — API publique, ~250 lignes, lisible.
- [`skia-main/include/core/SkTypeface.h`](file:///Users/chaos/workspace/kanvas-forge/skia-main/include/core/SkTypeface.h) — définition + idée de l'abstraction.
- [`skia-main/tools/fonts/FontToolUtils.cpp`](file:///Users/chaos/workspace/kanvas-forge/skia-main/tools/fonts/FontToolUtils.cpp) — le `MakePortableFontMgr`, les helpers DM-spécifiques. **Important** pour T4.
- [`skia-main/resources/fonts/`](file:///Users/chaos/workspace/kanvas-forge/skia-main/resources/fonts/) — les TTF à embarquer pour T4.
- Sample upstream à étudier : [`gm/aaxfermodes.cpp`](file:///Users/chaos/workspace/kanvas-forge/skia-main/gm/aaxfermodes.cpp) — utilise typique `SkFont.setTypeface` + `drawString`.

## Décisions finales (à valider avant de démarrer T1)

- [x] **Backend = AWT** pour T1-T5 (validé par utilisateur). FreeType reporté.
- [x] **Façade Skia obligatoire** : tout le code public utilise les noms et signatures Skia. Le code AWT vit isolé sous `org.skia.foundation.awt.*` avec bloc d'avertissement en tête. (validé)
- [ ] **Polices portables = T4**, pas avant. T1-T3 utilisent une font système quelconque.
- [ ] **`tolerance` par défaut sur GMs textuels = 8** (au lieu de 1) pour absorber les drifts AWT vs FreeType. À documenter par test.
- [ ] **`kSubpixelAntiAlias` = downgrade à `kAntiAlias`** pendant tout T1-T4. Subpixel correct = follow-up T5.
- [ ] **Slice T1 + T2 fusionnés ?** Petit ; à voir si on les combine en un seul PR pour simplifier (dépend si on veut juste compiler ou si on veut déjà mesurer).
