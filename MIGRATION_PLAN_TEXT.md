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

### Slice T4 — Polices portables Skia upstream (Liberation, option A)

**But** : remplacer la fallback platform-default sans-serif (T1-T3) par la **même famille de polices que Skia DM utilise** pour générer les images de référence, de sorte que la *forme* des glyphes converge vers l'upstream.

#### Investigation upstream — quelles polices DM utilise vraiment

> Note : le plan v1 disait « Roboto / DejaVu » — **incorrect**. L'investigation des sources `skia-main/tools/fonts/{FontToolUtils.cpp, TestFontMgr.cpp, TestTypeface.cpp, test_font_index.inc}` (faite après T3) donne le pipeline réel :
>
> ```
> ToolUtils::DefaultPortableTypeface()
>   → CreatePortableTypeface(nullptr, FontStyle())
>     → portableFontMgr->legacyMakeTypeface(nullptr, ...)
>       → TestFontMgr (tools/fonts/TestFontMgr.cpp)
>         → TestTypeface::Typefaces() (tools/fonts/TestTypeface.cpp)
>           → données dans test_font_*.inc
>             ↳ Liberation Sans / Liberation Mono / Liberation Serif
> ```
>
> Le default (par `gDefaultFontIndex = 4`) est **Liberation Sans Regular**.

3 familles disponibles upstream :
| Famille upstream | Source réelle |
|---|---|
| `monospace` / `Toy Liberation Mono` | Liberation Mono (Regular/Bold/Italic/BoldItalic) |
| `sans-serif` / `Toy Liberation Sans` | **Liberation Sans** ← `DefaultPortableTypeface` |
| `serif` / `Toy Liberation Serif` | Liberation Serif (Regular/Bold/Italic/BoldItalic) |

**Subtilité majeure** : upstream **ne charge pas** le `.ttf` à runtime. Les outlines sont **pré-extraites** dans des fichiers `.inc` C++ statiques (`test_font_sans_serif.inc` ≈ 408 KB de données points/verbs) générées une fois par `create_test_font.cpp`. Le runtime itère directement sur ces arrays via un `SkTestFont` custom — aucune dépendance FreeType pour ces typefaces.

#### Option A (T4 — adoptée pour la première itération)

Embed les **TTF Liberation officiels** (Red Hat, OFL) dans `kanvas-skia/src/main/resources/fonts/` et les charger via AWT.

**Travail** :
- Télécharger les 12 TTF Liberation : `LiberationSans-{Regular,Bold,Italic,BoldItalic}.ttf`, idem pour Mono et Serif. Source officielle : github.com/liberationfonts/liberation-fonts (releases).
- Les copier dans `kanvas-skia/src/main/resources/fonts/`.
- Implémenter `LiberationFontMgr` (interne, sous `org.skia.foundation.awt`) qui mappe `(family, style)` → ressource TTF, charge via `Font.createFont(TRUETYPE_FONT, classloader.getResourceAsStream(...))`.
- `ToolUtils.DefaultPortableTypeface()` route vers `LiberationFontMgr.getDefault()` (Liberation Sans Regular).
- Stratégie de cache : un `AwtTypeface` par `(family, style)`, créé lazy une seule fois.

**Bénéfices** :
- ✅ **Forme des glyphes ≈ upstream** : on rasterise les mêmes outlines vectoriels que `test_font_sans_serif.inc` exposait à FreeType (les `.inc` ont été générés depuis ces mêmes TTF).
- ✅ Effort minimal (~150 lignes Kotlin + 12 TTF, ~600 KB total).
- ✅ Pas de Kotlin custom typeface, AwtTypeface fait tout le boulot.

**Limites résiduelles (drift attendu)** :
- ⚠️ **Rasterizer AA différent** : AWT ≠ FreeType (utilisé par DM). 1-2 ulp de drift sur les bords AA.
- ⚠️ **Hinting AWT** appliqué (TT instructions interprétées différemment), peut décaler le placement subpixel d'un glyphe par rapport à l'upstream.
- ⚠️ **Scaler context** : Skia utilise son `SkScalerContext` qui choisit hinting/subpixel selon `font.edging`. AWT applique son propre policy via `RenderingHints` et `FontRenderContext`. Les deux divergent dans les cas limites.

→ Cumul → **forme correcte, pixels pas bit-exact**. Acceptable pour les GMs où le texte est annotation (tolerance ≥ 8 absorbe). Insuffisant pour `bigtext` / `coloremoji` where le glyphe est le sujet du test.

**Critères de réussite (T4 option A)** :
- [ ] Les 12 TTF Liberation chargent sans erreur en classpath.
- [ ] `LiberationFontMgr.matchFamilyStyle("sans-serif", Normal)` retourne `LiberationSans-Regular`.
- [ ] `ToolUtils.DefaultPortableTypeface()` est `LiberationSans-Regular`.
- [ ] Score d'un GM textual-heavy (porté en T3) grimpe de ≥ 5 % par rapport à la fallback platform-default.
- [ ] Aucun import `java.awt.*` hors `org.skia.foundation.awt.*` (contrainte design inchangée).

**Effort estimé** : 1 slice moyen (~150 lignes Kotlin + 12 TTF embarqués + tests).

#### Option B (à terme — reportée)

Porter les données vectorielles `test_font_*.inc` (1.2 MB de points/verbs C++) en Kotlin et construire un `SkTestTypeface` custom qui les expose **sans passer par AWT pour le rendu**. La rasterisation reste celle d'AWT (notre scanline-fill 4×4 via `drawPath`), mais les **outlines sont bit-exact upstream**.

**Pourquoi B est la cible long-terme** :
- ✅ **Bit-exact glyph outlines** vs upstream — élimine le drift de hinting et de scaler context. Le drift résiduel ne provient plus que du rasterizer scanline-fill (mêmes outlines, donc même points de coverage).
- ✅ Bypass d'AWT pour la résolution outline → indépendance du moteur de fontes JVM.
- ✅ Permet de viser `tolerance ≤ 4` voire `≤ 1` sur les GMs `bigtext` family.
- ✅ Dette technique réduite : seule la couche rasterizer reste à aligner pour viser le bit-exact total.

**Pourquoi pas tout de suite** :
- 📦 **1.2 MB de données** à porter (~3 × 408 KB de points/verbs/widths/charcodes/metrics par famille).
- 🔨 Custom typeface = nouveau type de `SkTypeface` (`SkTestTypeface`) qui ne dérive pas d'AwtTypeface — duplique les hooks `makeTextPath` / `measureTextInternal` / `getMetricsInternal`.
- ⏳ ROI déclenché seulement quand on s'attaque aux `bigtext`-family GMs (post-T5).

**Travail estimé pour B** :
- Script Python qui parse `test_font_*.inc` → généré Kotlin `data class TestFontData(points: FloatArray, verbs: IntArray, charCodes: IntArray, widths: FloatArray, metrics: SkFontMetrics)`.
- Embed les 3 fichiers générés (~1.2 MB de Kotlin compilé, sera lazy-loaded au premier accès).
- `SkTestTypeface : SkTypeface` qui surroute `makeTextPath` en itérant points/verbs directement (skip `Font.createGlyphVector` / `Shape` / `PathIterator`).
- `LiberationFontMgr` switch entre AwtTypeface (option A) et SkTestTypeface (option B) selon un flag (par défaut B en CI, A en dev local pour rapidité de boot).

**Effort estimé pour B** : 1 slice élevé (~600 lignes Kotlin générées + ~200 lignes infra + tests). À planifier quand un GM textual-content concrete réclame une fidélité que l'option A ne peut pas atteindre.

#### Trajectoire option A → option B

1. **T4** = option A. Liberation TTF embedded, AWT rasterise. Couvre 80-90 % du cas d'usage GMs (text en annotation).
2. **Tx future (post-T5)** = option B. Génération du Kotlin depuis les `.inc`, switch interne sur `LiberationFontMgr`. Active automatiquement pour les GMs `bigtext` family, opt-in ailleurs. **Aucune API publique ne change** — c'est une swap d'implémentation derrière `SkTypeface`. Les utilisateurs (`SkCanvas.drawString`) ne voient rien.

Le bloc d'avertissement KDoc en tête des fichiers AWT mentionne déjà cette possibilité ("Si on remplace AWT par FreeType+JNI ou par un rasterizer custom, **seul ce fichier doit changer**"). Option B = le « rasterizer custom » de cette promesse, juste limité à la couche outline lookup (le rasterizer scanline reste partagé).

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
| T4 (option A — Liberation TTF) | 40–90 | forme de glyphes ≈ upstream, drift résiduel sur bords AA / hinting |
| Tx future (option B — `.inc` ports) | 90–130 | bit-exact outlines ; déblocage des `bigtext`-family GMs |
| T5 | 80–130 | optimisation + finesse AA, scores plats sur déjà-portés |

> Les nombres exacts dépendent du seuil de `tolerance` qu'on accepte pour les GMs où le texte n'est pas le sujet central. Avec `tolerance = 8`, beaucoup plus passent ; à `tolerance = 1`, c'est plus serré.

## Risques / Open questions

### R1 — AWT et FreeType ne convergent pas sur les bords AA

- Probabilité : élevée (1-2 ulp sur ~10 % des pixels d'un glyphe).
- Mitigation : accepter `tolerance ≥ 8` pour les GMs textual-content ; pour les GMs où le texte est annotation, le delta sur les bords est invisible relative au contenu central.
- Pas de blocker.

### R2 — Polices système pour `Font.SANS_SERIF` etc. varient par OS

- Probabilité : élevée (macOS = San Francisco, Linux = DejaVu Sans, Windows = Arial).
- Mitigation : **T4 option A** (Liberation TTF embedded) résout — `Font.createFont(TRUETYPE_FONT, …)` instancie une police indépendante de l'OS, identique d'une machine à l'autre. Avant T4, considérer T1-T3 comme "smoke-only" pour les scores et ne pas paniquer sur des deltas inter-OS.

### R3 — Subpixel AA divergence

- Probabilité : moyenne. Skia subpixel AA produit du LCD 3x ; AWT subpixel est différent.
- Mitigation : downgrade à `kAntiAlias` partout, ne pas implémenter `kSubpixelAntiAlias` avant T5+.

### R4 — `SkShaper` / texte arabe / BiDi / kerning OpenType

- Probabilité : 0 dans notre scope (les GMs en cible utilisent du latin simple).
- Mitigation : explicit out-of-scope.

### R5 — Coût mémoire des polices embarquées

- Liberation TTF (option A — T4) : `LiberationSans-Regular.ttf` ≈ 130 KB, multipliée par 12 (4 styles × 3 familles) ≈ **600-700 KB** classpath. Lazy-loaded → coût RAM résiduel ≪ 100 KB une fois en mémoire. Négligeable.
- Données `.inc` portées (option B — futur) : ≈ 1.2 MB de Kotlin généré (points/verbs/charcodes/widths/metrics × 3 familles). Lazy-loaded au premier accès via `SkTestTypeface.Make`. Acceptable pour un module de test/référence.

## Trajectoire suggérée

1. **T1** ✅ livré (PR #48) — débloque la compilation.
2. **T2** ✅ livré (PR #48, fusionné avec T1) — `measureText` / `getMetrics` AWT-backed.
3. **T3** ✅ livré (PR #49) — vrai rendu glyphe via `GlyphVector` → `SkPath` → `drawPath`.
4. **T4 (option A)** = next. Liberation TTF embedded, bypass de la fallback platform-default sans-serif. Effort modéré, gros gain de stabilité inter-OS.
5. **T5** opportuniste, quand un GM spécifique nécessite glyph cache ou subpixel.
6. **Tx future (option B)** = porter les `test_font_*.inc` → bit-exact outlines. À planifier quand un GM textual-content concret le réclame (probablement après les premiers ports `bigtext`-family).

## Parallélisme avec le `MIGRATION_PLAN.md` principal

- **Phase 7+ (post-Phase 6)** : reprendre `SKIA_DM_TESTS_TO_IMPLEMENT.md` Level 2, en parallèle. Le text plan ne bloque pas et n'est pas bloqué.
- **Phase 5h re-attempt** : si on revient sur le linear-premul F16 storage, T1 est utile pour porter `TinyBitmapGM` (qui n'a pas de texte) — pas de couplage.
- **Image filters / blurs** : `BlurMaskFilter` et compagnie sont indépendants du texte. Un side plan séparé.

## Ressources upstream

- [`skia-main/include/core/SkFont.h`](file:///Users/chaos/workspace/kanvas-forge/skia-main/include/core/SkFont.h) — API publique, ~250 lignes, lisible.
- [`skia-main/include/core/SkTypeface.h`](file:///Users/chaos/workspace/kanvas-forge/skia-main/include/core/SkTypeface.h) — définition + idée de l'abstraction.
- [`skia-main/tools/fonts/FontToolUtils.cpp`](file:///Users/chaos/workspace/kanvas-forge/skia-main/tools/fonts/FontToolUtils.cpp) — `DefaultPortableTypeface()` → `MakePortableFontMgr` → `legacyMakeTypeface(nullptr)`. **Pivot** de T4.
- [`skia-main/tools/fonts/TestFontMgr.cpp`](file:///Users/chaos/workspace/kanvas-forge/skia-main/tools/fonts/TestFontMgr.cpp) — `MakePortableFontMgr` retourne un `FontMgr` qui sert les typefaces de `TestTypeface::Typefaces()` (PAS de chargement TTF runtime).
- [`skia-main/tools/fonts/test_font_index.inc`](file:///Users/chaos/workspace/kanvas-forge/skia-main/tools/fonts/test_font_index.inc) — la liste des 12 sub-fonts (Liberation Mono / Sans / Serif × 4 styles), avec `gDefaultFontIndex = 4` → **Liberation Sans Regular** est le `DefaultPortableTypeface`.
- [`skia-main/tools/fonts/test_font_sans_serif.inc`](file:///Users/chaos/workspace/kanvas-forge/skia-main/tools/fonts/test_font_sans_serif.inc) (408 KB) / `test_font_monospace.inc` / `test_font_serif.inc` — données vectorielles pré-extraites (points/verbs/charcodes/widths/metrics). **Cible du portage option B**.
- [`skia-main/tools/fonts/create_test_font.cpp`](file:///Users/chaos/workspace/kanvas-forge/skia-main/tools/fonts/create_test_font.cpp) — outil qui a généré les `.inc` à partir des TTF Liberation. Utile pour comprendre le format des arrays portés.
- **TTF Liberation officiels** (à télécharger pour T4 option A) : [github.com/liberationfonts/liberation-fonts/releases](https://github.com/liberationfonts/liberation-fonts/releases) — licence OFL, redistribution OK.
- Sample upstream à étudier : [`gm/aaxfermodes.cpp`](file:///Users/chaos/workspace/kanvas-forge/skia-main/gm/aaxfermodes.cpp) — utilise typique `SkFont.setTypeface` + `drawString`.

## Décisions finales

- [x] **Backend = AWT** pour T1-T5 (validé par utilisateur). FreeType reporté.
- [x] **Façade Skia obligatoire** : tout le code public utilise les noms et signatures Skia. Le code AWT vit isolé sous `org.skia.foundation.awt.*` avec bloc d'avertissement en tête. (validé)
- [x] **Slice T1 + T2 fusionnés** dans PR #48 (validé par utilisateur, livré).
- [x] **Polices portables = T4 option A** (Liberation TTF embedded). Skia DM utilise Liberation Sans/Mono/Serif (pas Roboto/DejaVu — investigation upstream après T3 a corrigé l'hypothèse initiale). Validé.
- [x] **Option B reportée** : porter les `test_font_*.inc` pour bit-exact outlines, post-T5, opportuniste. Validé.
- [ ] **`tolerance` par défaut sur GMs textuels = 8** (au lieu de 1) pour absorber les drifts AWT vs FreeType. À documenter par test, à confirmer au premier port de GM textual-content.
- [ ] **`kSubpixelAntiAlias` = downgrade à `kAntiAlias`** pendant tout T1-T4. Subpixel correct = follow-up T5.
