# W7 — tinybitmap : domaine encoded du port

Lot borné délégué par la carte blanche W7 ; pas d'approbation humaine fictive. Base075405b7a, parent draft #2445. La première boucle de ce lot est une mesure native actuelle, pas la réutilisation des scores historiques.

## Cause et choix

Au pin Skia `4f26f22daa4bf124e2999145f5caad4b10625580`, [tinybitmap](https://github.com/google/skia/blob/4f26f22daa4bf124e2999145f5caad4b10625580/gm/tinybitmap.cpp) contient un texel premul rouge128/alpha128, fond221, paint alpha0.5, repeat/mirror et drawPaint. Le [constructor SkPaint](https://github.com/google/skia/blob/4f26f22daa4bf124e2999145f5caad4b10625580/src/core/SkPaint.cpp) est non-AA. Le [preset bare8888 de DM](https://github.com/google/skia/blob/4f26f22daa4bf124e2999145f5caad4b10625580/dm/DM.cpp) choisit RasterSink N32 ; [colorInfo](https://github.com/google/skia/blob/4f26f22daa4bf124e2999145f5caad4b10625580/dm/DMSrcSink.h) est premul avec le color space optionnel. Sans via-tag, [CommonFlagsConfig](https://github.com/google/skia/blob/4f26f22daa4bf124e2999145f5caad4b10625580/tools/flags/CommonFlagsConfig.cpp) laisse ce space nul. Inférence pour ce contrat précis : composition sur valeurs encodées, pas transfert linéaire de la target Kanvas LINEAR.

Trois voies : conserver LINEAR (fidélité non alignée), changer le défaut de tous les GM/Surface (perte probable d'admissions non encore encoded), ou aligner explicitement ce port à SRGB_ENCODED + paint non-AA (retenu). Le défaut global reste inchangé ; les autres ports seront traités avec leurs preuves, pas sous une migration implicite. La capacité image nearest encoded existe déjà et possède ses témoins indépendants ; aucune nouvelle recette produit.

## Oracles avant GPU

Le modèle public conserve l'alpha byte128/255. Pour le texel128/255 puis paint128/255, la quantité source byte est128*128/255 ; composer au-dessus de221 puis arrondir le store UNORM8 donne le pixel encoded littéral [230,165,165,255]. Le contrôle public LINEAR existant reste [230,194,194,255]. Le modèle Skia alpha0.5 exact produirait un code vert/bleu166 : différence de quantification nommée, pas égalité bit-exact revendiquée.

Conserver100x100, fond221, source PREMUL, repeat/mirror, nearest, drawRect proxy intégral, alpha byte, minSimilarity0 et tolerance2. Les deux changements du GM sont son domaine explicite et antiAlias=false. Les buffers entiers, pas une PNG historique ou une assertion sur config, prouvent le domaine. Contrôle LINEAR explicitement fixé dans la fixture existante ; ses valeurs ne changent pas. Test enregistré attendu encoded en RED, nouveau replay encoded sur une Surface ; troisième test de compositions indépendantes deux-domaines conservé.

La référence physique contient un ICC Rec.2020 et Author `DM unified Rec.2020` ; ses pixels stockés ne sont pas les valeurs sRGB du comparateur. Cette métadonnée précise le stockage, pas la révision/configuration de production. Conversion existante ComparisonUtils inchangée ; voir la qualification pour les deux domaines et les hashes.

## Validation et limites

Baseline actuel cas592 seul ; RED de quatre méthodes avant modifier le GM ; GREEN avec17tests d'intégration sélectionnés (Tiny4, EncodedImage4, BitmapRect3, ChildPort3, ChildCausal1, BudgetWarmup2). Puis mesure du seul cas592, génération ciblée et runner standard sélectionné. Main audite intégralement et post-scelle séparément chaque runtime. AA des autres familles et scènes aux frontières restent ouverts. Une amélioration tinybitmap n'est ni un agrégat du corpus ni une provenance démontrée de la PNG.

## Global Constraints

- Fonts/external codecs/jpg-color-cube excluded; geometry stays in math with I/F32/64 nomenclature.
- No infrastructure/source-text/mock/fake/injected-callback tests, skips, CPU fallback, or weaker oracles/validators/thresholds/caps/budgets.
- Only TinyBitmapGm.kt and W7TinyBitmapSourceSurfacePixelTest.kt may change in worker phases; public API/runtime/planner/math and every other GM/reference stay byte-identical.
- Main-only serial native/Git, outer240, full process/exit/events/stacks/XMLstdoutstderr/JSONL/inventory audit then separate post-seal. No source/docs/Git writes while native active.
- Main alone may regenerate tinybitmap.png and its score value via existing selected tasks; 813 other PNG and 558 other score values stay identical. Preserve inverse96cd/private archives/workspaces.
- No global GREEN, merge or W7-complete; historical PNG producer remains unknown, alpha0.5 public quantization and drawPaint proxy remain explicit limits.
