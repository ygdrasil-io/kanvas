# Qualification du corpus ordinary-AA retenu

## Décision

Le corpus est complet et comparable : les 631 identités, leurs références
physiques, seuils et scopes correspondent au baseline fixé. Trois cas auparavant
en échec de rendu deviennent comparables. Une baisse de SSIM sur un cas déjà
comparé et sept changements de premier diagnostic sur des cas encore refusés
restent ouverts. Cette qualification mesure l’admission retenue ; elle ne
démontre ni l’absence de régression ni la fidélité complète de la Task2
historique.

## État des revues après la mesure

La mesure reste celle du HEAD mesuré `7360c5f94e3fcaa2f68d4375d8cdd7ad12295460`
et conserve tous les résultats et limites détaillés ci-dessous. La gate de
mesure Task2 Sol est approuvée C0/I0/M1. La revue large Astra de la branche
`1872d31cb..befdceaa4e167ac52c67d162913f3031789caa9b` (HEAD full40
`befdceaa4e167ac52c67d162913f3031789caa9b`) juge la publication en draft prête,
C0/I0/M1 ; les bytes source sont inchangés. Cette disposition de revue est
postérieure à la mesure, elle ne change pas son HEAD ou ses résultats. La
publication reste en attente du contrôleur ; ceci ne vaut ni merge readiness,
ni global GREEN, ni clôture W7.

Le M1 conservé est une dette de nettoyage technique différée au prochain edit
qualifié : safe call superflu dans `W6aLayerPlanCompiler.kt:305`, fixture sans
opt-in explicite dans `W7W6OrdinaryAaPathSourceIntegrationTest.kt:118`, et
warnings natifs Java (`native-access`, `sun.misc.Unsafe` via LWJGL) et de
dépréciations Gradle consignés dans les logs. Ces dettes ne sont pas corrigées
dans cette qualification. Leur présence signifie que la sortie de test n’est
pas sans warnings ; elle n’invalide pas les données du corpus et ne rend pas
les suites globales vertes.

## Provenance et intégrité

La référence physique (baseline) est [`root-aa-rect-8e44f0c8a.json`](root-aa-rect-8e44f0c8a.json),
renderer commit `8e44f0c8ad1ac65d97275d010ed083e1e44bbfcd`. L’instantané public
(snapshot) est
[`ordinary-aa-retained-corpus.json`](ordinary-aa-retained-corpus.json), SHA-256
`767b1342a71e860d9718a66a863b457e52c28da15c7f144941c95af25e7b03fb`, produit
par l’agrégateur existant depuis les cinq journaux du corpus, sans les neuf
journaux indépendants Task1. Le HEAD complet réellement mesuré est
`7360c5f94e3fcaa2f68d4375d8cdd7ad12295460`.

Le registre conserve ses 631 entrées, ses 443 éligibles, ses 133 exclusions
fonts, 54 exclusions codecs et son cas `jpg-color-cube` quarantiné pour limite
de ressource. SHA-256 du registre :
`4ca8eea61451b1143fd3d15634d2c34e0c9ec31b74fd351ca30a69ee36f565d7`. Pour les
631 fiches, l’identité GM, le scope initial et courant, le domaine, le statut et
hash physique de référence, les dimensions déclarées, la tolérance, le seuil
minimum et `requiresZeroRefusals` sont inchangés. Le reçu contrôleur consigne
aussi les douze SHA-256 de sources/configuration avant et après, égaux, ainsi
que la vérification physique des 631 références sans divergence. Les
dimensions de référence mesurées dans une comparaison nouvelle sont des
résultats, non une modification des références figées.

Les cinq journaux bruts sont conservés sous
`/private/tmp/kanvas-w7-inverse-inventory.hbWqUb/retained-aa-corpus-7360c5f94/`.
Leur comparaison indépendante donne 631 lignes `gm`, 631 indices uniques,
chacun exactement dans son intervalle, et zéro différence entre une ligne
brute et la fiche correspondante du snapshot. Les tranches et sorties sont :

| Indices | Lignes JSONL | Gradle / wrapper | Résultat |
| --- | ---: | ---: | --- |
| `[0,200)` | 202 | 0 / 0 | footer complet |
| `[200,400)` | 202 | 0 / 0 | footer complet |
| `[400,607)` | 209 | 0 / 0 | footer complet |
| `[607,608)` | 2 | 1 / 1 | `vertices` timeout GM 30 s, JavaExec 124, sans footer |
| `[608,631)` | 25 | 0 / 0 | footer complet |

Chaque sortie externe indique `timed_out=false` et `child_signal=null`; la
tranche de l’indice 607 reste tout de même rouge à cause du timeout du GM. Les
quatre autres tranches terminent avec code 0. `measureSkiaParity` est une
exécution JavaExec : l’absence de JUnit XML et d’événements de tests est
attendue, pas un PASS de tests. Les logs complets, exits, inventaires et lignes
JSONL sont décrits dans
`.superpowers/sdd/ordinary-aa-retained-admission-plan/controller-evidence.md`.

Le contrôle a analysé par programme les cinq JSONL en entier, puis comparé
chaque objet case à sa ligne du snapshot ; aucun pixel n’a été créé ou inspecté
visuellement pour ce corpus `images=false`. Le baseline contient lui aussi 631
fiches. Un scan exhaustif du schéma baseline/snapshot trouve les seuls champs
différents suivants :

| Champ | Fiches affectées | Interprétation |
| --- | ---: | --- |
| `actualRgbaSha256` | 4 | Trois rendus devenus disponibles et le changement réel de l’index 77 |
| `outcome`, `rendered`, `declaredContractPass` | 3 chacun | Les trois transitions de statut détaillées ci-dessous |
| `diagnostic` | 10 | Trois gains et les sept changements de premier refus détaillés ci-dessous |
| `dispatched` | 4 | Trois nouveaux rendus plus le dispatch 2 → 4 de l’index 77 |
| `refused` | 3 | Refus à 0 pour les trois nouveaux rendus |
| Métriques pixel `exactPixelMatch`, `pixelMatchTolerance2`, `pixelMatchDeclaredTolerance`, `maxChannelDelta` | 3 chacune | Résultats des trois cas nouvellement comparés |
| `ssimLuminance`, `meanAbsoluteChannelErrorNormalized` | 4 chacune | Trois nouvelles comparaisons et la différence de l’index 77 |
| `referenceWidth`, `referenceHeight` | 3 chacune | Dimensions mesurées pour les trois nouvelles comparaisons ; octets des références inchangés |
| `operationCount` | 1 | `rrect_blurs`, 14 → 15 |
| `renderMs`, `elapsedMs` | 344, 419 | Durées volatiles, non interprétées comme gains ou benchmark |

Tous les autres champs par case sont identiques, y compris identité,
configuration, scope, hash de référence, setup, tentative, domaines et seuils.
Les dimensions de référence mesurées ne sont pas confondues avec les
dimensions déclarées et figées de l’entrée.

## Résultats comparés au baseline

| Mesure | Baseline | HEAD mesuré | Delta |
| --- | ---: | ---: | ---: |
| Rendus disponibles | 217 | 220 | +3 |
| Comparés | 194 | 197 | +3 |
| `render_failed` | 175 | 172 | −3 |
| `setup_failed` | 50 | 50 | 0 |
| `rendered_uncompared` | 15 | 15 | 0 |
| `reference_dimension_mismatch` | 8 | 8 | 0 |
| Timeouts | 1 | 1 | 0 |
| Cas ≥99 % pixels ±2/canal | 47 | 47 | 0 |
| Cas ≥95 % pixels ±2/canal | 63 | 63 | 0 |
| Médiane des comparés, pixels ±2/canal | 77,45815728081598 % | 76,24387741088867 % | −1,21427986992731 point |

La nouvelle médiane inclut les trois cas fraîchement comparés, dont les scores
±2 sont chacun sous l’ancienne médiane. Cette différence de population peut
réduire la médiane agrégée ; elle ne démontre pas une baisse des anciens pixels.
La baisse de SSIM de l’index 77 est une différence distincte sur un cas déjà
comparé et reste signalée séparément ci-dessous.

Les seuls changements de statut sont les trois gains ci-dessous ; aucun ancien
cas rendu n’a perdu son statut rendu ou comparé. Pour ces nouveaux rendus, le
baseline n’avait ni pixels ni métriques : les scores nouveaux ne sont donc pas
des deltas de pixels par rapport au baseline.

| Index / GM | Ancien → nouveau | Opérations ; dispatch / refus | Pixels ±2 ; exacts | SSIM ; erreur moyenne normalisée | SHA-256 RGBA |
| --- | --- | --- | --- | --- | --- |
| 64 `blurcircles2` | `render_failed` → `compared` | 109 ; 55 / 0 | 60,844850329781835 % ; 48,97108066971081 % | 0,913971350294999 ; 0,03557988181574 | `2c239fabc210472e7a6aa3942190800796ac9f9d6a81463184d4589cc3f10c92` |
| 210 `dropshadow_pseudopersp` | `render_failed` → `compared` | 6 ; 4 / 0 | 66,78876170655568 % ; 66,7429760665973 % | 0,9425840346735577 ; 0,032327232662055456 | `79a560812e56d90b4b674f1386e3ed2e63412db7319c0a5f300f28079233d436` |
| 472 `rrect_blurs` | `render_failed` → `compared` | 15 ; 15 / 0 | 55,0775 % ; 52,21916666666667 % | 0,6624135636987509 ; 0,07241883986928105 | `8138738456382c12ac5f26cfa2b938d420f660d0a91148071ed85e7774b6eef2` |

Les maxima de delta par canal RGBA pour ces trois nouveaux rendus sont
respectivement `[82,82,82,0]`, `[248,248,248,0]` et `[187,187,187,0]`.
Les cibles déclarées ±2 correspondent aux scores pixels ±2 indiqués.

## Baisse et autres changements à conserver

Parmi tous les cas rendus et comparés avant le lot, le seul changement de hash
RGBA ou métrique de pixels est l’index 77, `child_sampling_rt`. Le statut reste
`compared`, `tolerance=2` reste inchangée, les pixels exacts et ceux à ±2
restent à 0 %, et le score `pixelMatchDeclaredTolerance` reste à 0 %. Le
maximum de delta reste `[25,230,230,0]`. Son hash passe de
`6ed9398ed73864345d644d5f4c90c1ec41ba54035f06fc966aa623c3e468a119` à
`994233fcfa219c1b1cd9cfc73429be4e5d4c3bef57b04d316f7cbd346bae11d8` ; la SSIM
baisse de `0.8264076669756701` à `0.8069318241191481` (−`0.01947584285652204`)
et l’erreur moyenne normalisée change de `0.08306848862591912` à
`0.08291554170496324` (−`0.00015294692095588203`). Le dispatch passe de 2 à 4,
sans refus ; le compte d’opérations reste 4. Le changement de `drawLine` public
est une hypothèse possible au vu de la scène, mais la cause de cette différence
n’est pas établie : elle reste un lot causal ouvert. Aucun input GM globalement
inchangé n’est revendiqué.

Sept autres fiches conservent `render_failed`, mais leur premier diagnostic
change. Aucune n’est comptée comme gain de rendu :

| Index / GM | Premier diagnostic baseline → nouveau | Observation |
| --- | --- | --- |
| 213 `emboss` | `unsupported_child`, draw 1 → draw 5 | Toujours refusé, filtre désormais présent dans le premier refus |
| 233 `filterfastbounds` | `unsupported_child` → `w6b.filter.frame_budget_exceeded` | 1 141 545 688 octets requis, plafond inchangé à 1 073 741 824 |
| 236 `flippity` | `w4c.command.not_migrated` → `w4d.command-not-migrated` | Toujours refusé |
| 285 `gradients_many` | `unsupported.core_primitive.geometry.invalid` → `unsupported.stroke.width_invalid` | Toujours refusé |
| 317 `imagefiltersbase` | `unsupported_child`, draw 32 → draw 36 | Toujours refusé, filtre présent dans le premier refus |
| 338 `inverse_windingmode_filters` | `unsupported_child` → `w4e.clip.capability-unavailable` | Toujours refusé |
| 470 `rotate_imagefilter` | `unsupported_child`, draw 3 → draw 7 | Toujours refusé, filtre présent dans le premier refus |

Ces décalages du premier refus ne démontrent ni un rendu, ni une causalité
complète. La seule différence de `operationCount` entre les 631 lignes est
l’index 472, 14 → 15, cohérente avec la scène RRect corrigée du port. Ce compte
n’est pas une identité figée ; les changements draw/API et le port de scène sont
intentionnels. La différence du GM 77 reste distincte et sans cause prouvée.

La somme `sumCaseElapsedMs` passe de 161 281 à 160 486 ms. Ce cumul n’est pas un
benchmark et ne soutient aucune conclusion de performance.

## Portée de la décision

Pour ce lot de mesure, aucun seuil, référence, budget ou scope n’a été modifié.
À l’échelle de la branche comparée au baseline, toutefois, la scène RRect du
port et l’API publique `drawLine` ont été intentionnellement modifiées
auparavant ; il serait inexact d’affirmer que toute la branche ou toutes les
scènes sont inchangées.

Le corpus satisfait le contrôle d’identité, de références et de complétude,
mais demeure RED : 172 échecs de rendu et un timeout natif. La baisse de SSIM
sur l’index 77 empêche toute déclaration « sans régression » ; elle doit être
traitée comme un lot causal distinct avant une telle conclusion. Les dettes de
fidélité originales restent ouvertes, notamment `rrect_blurs` à 55,0775 % et
`blurcircles2` à 60,844850329781835 % à ±2. Aucun seuil, référence, budget ou
scope n’a été altéré dans ce lot pour faire monter les comptes. La scène RRect
du port et l’API `drawLine` sont des changements intentionnels antérieurs dans
la branche et ne sont pas revendiqués comme inchangés.

Le gate Task2 frais, la revue large de branche et toute éventuelle PR draft
restent à faire. Ce relevé ne revendique ni tests globaux verts, ni proximité
ISO générale, ni fusion, ni clôture W7.
