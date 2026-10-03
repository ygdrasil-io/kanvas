# W7 — qualification plein cadre du port RRectBlur

3 octobre 2026. Ce relevé qualifie la correction réversible des appels du GM,
pas la fidélité complète de Task2. Il conserve l’admission W6 déjà qualifiée
et les références fixes. La scène entière reste mesurée, y compris son panneau
central absent et ses écarts de blur.

## Résultat

Le vrai `RRectBlurGm` corrigé rend les quatre coins attendus; ses 25 ancres
historiques passent, le replay est identique et aucune opération n’est refusée.
Le score plein cadre progresse modestement, sans approcher la fidélité :

| `rrect_blurs`, 300×400, LINEAR | Avant | Après | Delta |
| --- | ---: | ---: | ---: |
| Pixels exacts | 51.58583333333333 % | 52.21916666666667 % | +0.63333333333334 point |
| Pixels à ±2 | 54.42 % | 55.0775 % | +0.6575 point |
| SSIM | 0.621383424754948 | 0.6624135636987509 | +0.041030138943802 |

L’erreur moyenne normalisée reste 0.07241883986928105 en exact et
0.07233138888888889 à ±2; le maximum RGB reste 187 et l’erreur alpha 0.
L’inspection des PNG montre les coins arrondis de la première ligne et les
coins bleus inférieurs corrigés sur les deux routes. Le panneau central et ses
labels manquent toujours; deux bordures et les régions floues divergent.
Tous les pixels restent dans la comparaison : aucun crop ne devient une
exclusion ni un score de remplacement.

Le contrôle inchangé `blurcircles2` conserve exactement ses résultats
précédents : pixels exacts 48.97108066971081 %, ±2 60.84485032978184 %,
SSIM 0.913971350294999, erreur moyenne normalisée 0.03557988181574 exacte et
0.03512099859730802 à ±2, maximum RGB 82 et alpha 0. Les 50 formes et les
quatre séparateurs sont présents; les régions floues restent plus claires que
la référence. Ce contrôle n’attribue aucune cause à la couleur, au kernel ou
à l’AA.

## Runs et preuve

Le contrôleur a exécuté chaque cas séparément, sous watchdog de 240 s, puis a
lu le terminal, le log complet, le code de sortie, tous les événements, tout
le XML, ainsi que stdout/stderr non tronqués avant le run suivant. Aucun run
n’a expiré, reçu de signal, été skippé ou fini en rouge.

| Cas | Processus et audit natif | PNG / ancres |
| --- | --- | --- |
| RRectBlur complet | enfant 81326, session 6606, exit 0, BUILD 12 s, 1 PASS / 0 skip / 0 erreur; 15 ops, 0 refus, 77 draw calls, 92 pipelines, 82 render passes, 1 submit, 1 readback, `Render+Readback`, `CompletionSucceeded`, alpha 255 | 25 ancres pass; RGBA réel et replay `8138738456382c12ac5f26cfa2b938d420f660d0a91148071ed85e7774b6eef2`; 22 PNG/crops conservés; vues entières actual/reference/diff±2 et triplets première/dernière rangée inspectés, incluant x200 |
| BlurCircles2 complet, contrôle inchangé | enfant 81652, session 61077, exit 0, BUILD 14 s, 1 PASS / 0 skip / 0 erreur; 55 ops, 0 refus, 413 draw calls, 468 pipelines, 414 render passes, 1 submit, 1 readback, `Render+Readback`, `CompletionSucceeded`, alpha 255 | 7 ancres pass; RGBA réel et replay `2c239fabc210472e7a6aa3942190800796ac9f9d6a81463184d4589cc3f10c92`; 30 PNG conservés; seules les vues entières actual/reference/diff±2 inspectées; 50 formes et quatre lignes présentes |

Pour RRectBlur, l’archive immuable est
`/private/tmp/kanvas-w7-inverse-inventory.hbWqUb/rrect-blur-port-full-gm-1/`;
elle contient `process.log`, `exit.json`, `events.jsonl`, le XML sous
`xml/TEST-org.graphiks.kanvas.skia.W7W6OrdinaryAaPathSourceIntegrationTest.xml`,
les PNG sous `images/rrect_blurs/`, et les reçus binaires. L’archive du contrôle
est `/private/tmp/kanvas-w7-inverse-inventory.hbWqUb/rrect-blur-port-blur-control-1/`
avec le même ensemble de reçus sous `images/blurcircles2/`. L’audit complet,
notamment la relecture séparée de l’XML de contrôle après un affichage combiné
tronqué, est détaillé dans
`.superpowers/sdd/rrect-blur-port-correction-plan/controller-evidence.md`.

La référence RRect reste le PNG SHA-256
`3327fa6254d219f5a23c5bdcdab30f0f363da1834353ff246f7e0b5ce66beaaa`;
la référence BlurCircles2 reste
`57680c49964fa6989acebf8526498cf799f9eaf07ad3d5c3cd8dfd7f87146964`.
Les deux dimensions et le domaine LINEAR sont inchangés. Le SHA RGBA du replay
RRect est identique au rendu; celui de BlurCircles2 est aussi identique au
précédent relevé. Sources W6, GM et fixtures sont restées inchangées après
ces runs.

## Portée et disposition

Le défaut causal était dans l’appelant : `RRectF32.of(rect, CornerRadii)`
renseigne seulement top-left. Les paramètres nommés par coin corrigés dans le
GM rétablissent la géométrie attendue; l’API conserve son comportement. Les
deux témoins du GM utilisent `Render+Readback`. Le contrôle API non filtré
emprunte la route prepared, qui expose honnêtement `scopes=[]`; ses compteurs
`drawCallCount` et `pipelineCount` proviennent de l’exécution GPU réelle, et
ses attentes sont les pixels littéraux. Aucun scope n’est fabriqué.

Le RED causal Task1 a été authentifié : dans RED2, le contrôle API passe et les
deux pixels du GM échouent (`(75,24)` RGB 106 au lieu de 68;
`(199,50)` RGB 68 au lieu de 192±1), sans skip ni erreur. Les deux GM sont
propres, `Render+Readback` et sans refus avant ces assertions. GREEN1 passe les
3 cas sur les mêmes oracles, sans skip, timeout ni refus. La preuve des
contrôles API utilise 2 ops, 0 refus, 3 draw calls et 6 pipelines par contrôle;
celle des deux scènes GM exige les scopes `Render+Readback` de leur route W6.

Disposition : retenir l’admission W6 qualifiée et le correctif réversible du
vrai GM. La Task2 originale de fidélité plein cadre reste falsifiée et
incomplète. Le lot ne change ni objets mathématiques, ni géométrie I/F32/64,
ni AA, domaine, référence, seuil, sampling ou budget. Aucun nouveau refus de
famille n’a été observé. La provenance primaire Skia `8d5cb2e` a été vérifiée;
la cible `8019` et la provenance exacte des PNG restent inconnues, donc aucune
équivalence upstream n’est revendiquée.

Les références restent le registre de 631 entrées / 443 éligibles; les
exclusions restent 133 fonts, 54 codecs et `jpg-color-cube` (1). Aucun gain
corpus n’a été mesuré. Une qualification distincte explicite pour l’admission
retenue — d’abord neuf opportunités, puis le corpus — peut être planifiée avec
empreintes, config, seuils, budgets et pertes visibles. Ce relevé ne lance ni
ces runs ni publication automatique. Aucun nouveau PR, push, merge, global
GREEN ou clôture W7 n’est revendiqué.

Le préexistant reste suivi : les 15 corps natifs des vrais masques W6 passent,
mais leur exécuteur termine en `133`, y compris sur la baseline antérieure;
la cause exacte n’est pas attribuée. Les suites globales restent RED ou
incomplètes, les avertissements mineurs différés, dont unsigned/safe-call et
native-access/LWJGLUnsafe/Gradle, ne sont pas réparés ici. Aucun correctif
d’infrastructure ni claim ISO n’est ajouté.
