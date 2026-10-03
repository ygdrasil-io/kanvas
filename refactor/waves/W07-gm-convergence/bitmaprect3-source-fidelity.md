# W7 — fidélité source de `DrawBitmapRect3`

3 octobre 2026. Qualification ciblée du GM `3x3bitmaprect`, basé sur la source Skia épinglée à `4f26f22daa4bf124e2999145f5caad4b10625580`. La révision exacte de la PNG de référence locale demeure inconnue.

Source au commit `f6c041d8d8088648e70850f60d00dc5c3e0c8192`, branche `codex/w7-bitmaprect3-source-fidelity`, base `f9bd8fae23cf2a15af235e61dc9c6096661ad740`, empilée sur la PR draft #2441. La tâche attend la revue Sol ; W7 reste ACTIVE.

Le GM déclare un fond noir et utilise `ColorARGB.Gray` pour la cellule grise. Le fond par défaut des autres GM reste blanc. Quatre rendus GPU bitmaprect concordent à 100 % avec l’oracle RGBA littéral indépendant ; 11/11 tests ciblés passent (trois nouveaux tests, ChildPort3, Historical1 et SceneCapture4), sans skip ni erreur.

La parité ciblée contre la référence historique progresse comme suit :

| Mesure | Avant | Après |
| --- | ---: | ---: |
| Pixels exacts | 4.069010416666666 % | 98.37239583333334 % |
| Pixels à ±2 | 5.696614583333333 % | 100.0 % |
| SSIM | 0.03333736469852865 | 0.9999996522494206 |

Le seuil contractuel est satisfait, mais les pixels exacts ne sont pas tous identiques à la référence. L’accord avec l’oracle source indépendant est une mesure distincte ; l’écart résiduel de référence n’est attribué ni à la couleur ni à l’encodage. `minSimilarity=0`, la tolérance 2 et le domaine `LINEAR` n’ont pas changé.

Les 813 PNG non-cibles restent scellées à `1dfc6fe2b56f175eadeadf66be1c5960a7d7e6675cd6509985ad4954ffa9f171` et les 558 autres scores à `34f4c04ce043a3194e963203cb0efb2874f0d52e65479948e2da02c864187533`. La référence reste inchangée. Seul le PNG cible et la valeur cible du fichier de scores ont évolué dans leurs validations respectives ; aucun nouvel agrégat des 631 GM n’a été lancé.

Les exclusions fonts, codecs externes et `jpg-color-cube`, ainsi que les échecs globaux et dettes W7 hérités, restent hors périmètre. Aucun GREEN global, merge ou clôture W7 n’est revendiqué. Les reçus natifs détaillés demeurent dans l’evidence privée du lot.
