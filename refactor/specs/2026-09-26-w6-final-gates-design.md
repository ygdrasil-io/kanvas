# W6 — correction des derniers gates de clip et de budget

## 1. Intention et état de départ

Cette correction est empilée sur `codex/w6e-effects-convergence` (Draft PR
#2408). Elle ne crée pas une vingt-troisième famille d'image filters et ne
relance ni les GMs ni la suite Skia globale. Son résultat attendu est une
qualification W6 plus honnête : un témoin RRect valide, et une réservation
gelée pour chaque classe d'objet natif opaque que la route W6 admise peut
matérialiser. Les allocations exactes du driver restent non observables.

Deux constats motivent la correction :

- Le test public `clipped rounded rect mask blur preserves its frozen analytic
  coverage` échoue également sur le prérequis W6e `6bd5962e5`. Son premier
  écart est le pixel `(2,4)`, hors du clip `[3,8) × [2,7)`. L'oracle floute
  une couverture déjà clippée et n'applique plus le clip final, tandis que la
  spec W6 §8 impose couverture → mask filter → source → image filter → clip
  terminal → blend. Ce pixel établit un défaut d'oracle, pas un défaut de
  production. Les pixels dans le clip ne sont pas encore qualifiés après
  correction de l'oracle.
- `W6aLayerPlanBudget` additionne les `PlanResource` et les leases logiques
  des seuls programmes W6d à `frozenSamplingProgram`. La route W6b/W6c
  matérialise aussi des modules, layouts, pipelines et bind groups depuis des
  `FilterPass` gelés ; les demandes `PlanCacheResourceRequest.Sampler` ont une
  taille de zéro dans le slot physique. Les B/B−1 déjà publiés sont exacts
  pour les ressources actuellement déclarées, mais ne prouvent pas que toute
  charge opaque de la route W6 a été réservée avant création native.

## 2. Approches et décision

1. **Retenir une réservation logique gelée par artefact natif opaque W6** :
   inventorier les sites de création de la route W6, associer chaque bundle
   programme et sampler à un owner, un descriptor, une génération et un
   intervalle de vie, puis inclure sa charge pessimiste dans le budget de
   frame. C'est la voie retenue ; elle étend le précédent W6d sans prétendre
   connaître les bytes privés du driver.
2. Déplacer tous les programmes et samplers vers un cache de session avec un
   budget indépendant. Ce serait plus invasif et ne dispenserait pas la frame
   consommatrice d'une réservation et d'une lease jusqu'à completion.
3. Corriger seulement l'oracle et documenter le reste. Ce serait plus rapide,
   mais laisserait le critère W6 §17.8 de comptabilité préalable insatisfait.

La correction reste un lot distinct : aucun des changements ci-dessous ne
réécrit rétroactivement les PR W6a–W6e déjà relues. Une Draft PR additionnelle
est empilée sur #2408 ; aucun merge automatique.

## 3. Clip et oracle RRect

L'oracle CPU construit d'abord la couverture RRect indépendante, y compris
hors du clip ; il applique ensuite le style `MaskFilter.Blur`, puis borne les
pixels de sortie par le clip dur public. L'anti-aliasing du RRect doit être
représenté par une couverture indépendante suffisamment précise pour que la
tolérance locale ne masque pas une erreur visible ; aucune tolérance globale
n'est élargie. Les octets attendus sont fixés avant `Surface`.

La correction commence par cet oracle, sans modifier la production. Le test
public est ensuite rejoué. Si une différence subsiste **dans** le clip, elle
devient un RED causal pour le propriétaire existant de l'auto-layer W6b ; une
correction de production ne peut suivre qu'après localisation de cette cause
et vérification des témoins W6b adjacents. Le test conserve les scopes publics
`Render` et `Readback`, et une erreur native 133/134 reste `UNKNOWN`.

## 4. Autorité de réservation et frontières

La route positive reste `Surface/Picture → render-ir → :math → :gpu-plan →
:gpu-renderer → submit → visibilité publique`. `:math` conserve seul les
objets et calculs géométriques, avec la nomenclature I/F32/64 existante.
Cette correction ne crée ni bounds dans le renderer, ni pass/resource/ID
après freeze, ni fallback legacy après admission.

Avant publication du graphe, `:gpu-plan` dresse un inventaire fermé des
catégories de ressources de W6 §11 provoquées par la route admise :
textures/buffers/staging déjà représentés par `PlanResource` (avec leurs
views), bundles programmes (shader module, pipeline layout, pipeline et
bindings nécessaires) et samplers. Un bundle qui peut être créé plusieurs
fois pour un même `PlanPass` possède un ordinal stable ;
une identité de pass seule ne suffit pas. Chaque entrée opaque a un owner
(`PlanPassId` et ordinal, ou `PlanResourceId` pour un sampler), un descriptor
canonique, une génération device, un slot, une charge logique checked-I64 et
un lifetime scellé. La matérialisation doit authentifier cet inventaire
complet avant de préparer les artefacts W6 de la frame ; aucun miss de cache ni
spécialisation renderer ne peut ajouter un type ou un slot non gelé.

La charge d'un bundle programme vaut au minimum `4096` octets logiques,
comme le lease W6d existant, ou la taille checked-I64 de son descriptor et
de sa recette canonique si elle est supérieure. Un sampler natif consommé
par une frame W6 reçoit aussi un lease logique de `4096` octets par slot
gelé, y compris dans la spécialisation W5a de destination-read. Le slot et
son `PlanResourceId` sont émis par le planner avant freeze, même si le
sampler natif n'est pas un `PlanResource` texture/buffer. Cette charge de
frame est distincte du `byteSizeI64 = 0` de
`PlanCacheResourceRequest.Sampler`, qui ne mesure pas l'objet natif conservé
dans le cache de session W5h. La cible finale de ce cache est `4096` octets
logiques par sampler résident, sans prétendre mesurer sa taille driver ; sa
clé canonique et son API ne changent pas. Dans ce lot, cette modification
W5h et le témoin runtime positif sont différés : aucun effet enregistré
public ne déclare actuellement de sampler, donc il n'existe ni entrée
résidente positive ni test public B/B−1 légitime. Le refus public existant
reste un témoin de préservation, pas une preuve positive.
Les leases W6d existants sont réutilisés ou intégrés sans double charge.

L'inventaire couvre toute la chaîne native W6, pas seulement le matérialiseur
géométrique W6 : les bundles W4e délégués puis les spécialisations W5a
`materializeW5aSourcePartitionV2` en font partie. Les recettes matériau sont
calculées depuis la `MaterialPlanTable` et les sources typées scellées, non
depuis un `MaterialPlanRef` nu ni depuis le WGSL produit tardivement. Une
table fermée relie chaque site natif à owner/ordinal/recette/charge. Le
dispatcher authentifie l'ensemble de cet inventaire avant la première
allocation ; une autorité de consommation commune passe par W6/W4e/W5a et
contrôle la dernière spécialisation. La projection budget du renderer
additionne chaque lease opaque exactement une fois comme le planner.

Le pic de frame additionne en I64 vérifié tous les slots physiques déclarés
et ces leases opaques. Par défaut, leurs lifetimes couvrent la frame jusqu'à
completion/quarantine ; aucun aliasing ou rabais de cache n'est admis sans
preuve de non-chevauchement dans le graphe gelé. Une frame à `B` est admise,
la même à `B−1` refuse **avant** la matérialisation de ses ressources W6 et
ne publie aucun sibling. Un cache hit peut économiser du travail, jamais réduire la charge
de cette admission. Le diagnostic conserve son owner W6 précis. Un
`discardRecordedOperations()` suivi d'un re-record sur la même `Surface`
reste possible après refus terminal.

Cette règle est une comptabilité logique pessimiste des objets opaques :
`4096` n'est pas une mesure ni une borne démontrée des bytes alloués par le
driver. Les textures/buffers et leurs alignements restent comptés par leurs
`PlanResource` exacts. La taille réelle des modules, pipelines et samplers
du driver reste une limite explicite ; elle ne peut servir à une claim de
budget physique byte-exact ou de convergence ISO.

## 5. Cache, leases et générations

La revue structurelle suit, sur hit, miss, eviction, retirement et changement
de génération, l'owner de chaque résultat spatial et de chaque ressource de
session consommée par W6. Une lease réutilisée reste vivante jusqu'à
completion/quarantine sûre ; la génération ancienne ne redevient jamais
valide. Le cache ne saute ni preflight, ni budget, ni authentification du
graphe gelé.

Les tests publics continuent de prouver pixels, refus B/B−1, sentinel et
recovery. Ils ne prétendent pas observer un cache hit, le nombre d'objets
natifs ou la durée d'une lease : aucune reflection, hook, compteur privé,
fake device ou test statique d'infrastructure n'est ajouté pour fabriquer
cette observation. La revue de code porte cette partie du contrat.

## 6. Preuves et livraison

Les témoins publics utilisent seulement `Surface`, `Picture`, les APIs de
paint/layer/filter, les diagnostics, `render()`, `readPixels` et
`discardRecordedOperations()`. Les attentes et les B sont calculés avant
`Surface`/`PictureRecorder`. Les positifs vérifient `Render` + `Readback`.

La matrice minimale est :

- le RRect clippé et les témoins W6b mask/shadow adjacents, après correction
  de l'oracle ;
- une frontière B/B−1 pour un programme W6b, une pour W6c, une pour W6d
  préservée et une pour un sampler natif W6 réellement consommé dans la
  spécialisation W5a ; le sampler runtime W5h reste un gap identifié ;
- un graphe public multi-familles borné dont le B est calculé depuis **ses
  propres** slots, charges et lifetimes, avec pixels, refus terminal, sentinel
  et récupération sur la même `Surface` ;
- les témoins de préservation W6a–W6e concernés, exécutés séquentiellement,
  et les compilations séparées des modules touchés.

Le graphe combiné n'est pas un monolithe exact des 22 familles ; les fixtures
W6e existantes ne sont pas additionnées pour inventer ce B. Les formules
historiques B/B−1 modifiées par les nouveaux leases sont recalculées à partir
de leurs propres ressources, jamais ajustées par essais empiriques. Un RED
pixel, diagnostic ou recovery est distingué d'un exit natif 133/134
`UNKNOWN`.

Terra implémente les tâches séquentielles ; Sol relit chaque tâche et la
branche entière. Astra n'est sollicité qu'en cas de blocage architectural ou
numérique démontré. La Draft PR finale cible `codex/w6e-effects-convergence`
et documente exactement les gaps restants, sans merge.

## 7. Exclusions et critère de fermeture

Fonts/glyphes, codecs et formats externes, GMs/dashboard/renders/scores et
rebaseline, suite Skia globale, `jpg-color-cube`, tests d'infrastructure,
frontend SkSL/WGSL arbitraire, F16/HDR positif, device-loss non observable et
retrait legacy W8 restent hors périmètre.

Ce lot partiel est terminé lorsque l'oracle RRect et ses témoins voisins
n'ont aucun échec JUnit, que toute ressource opaque effectivement créée par
la route W6 admise a un owner et une réservation logique authentifiée avant
matérialisation, que les nouvelles frontières B/B−1 accessibles et le graphe
multi-familles public sont qualifiés, et que les reviews Sol ne laissent
aucun finding Critical/Important. W6 global reste **ouvert** tant qu'une
voie publique positive à sampler runtime et la comptabilité résidente W5h
ne sont pas spécifiées puis prouvées ; ce lot ne ferme pas davantage la
taille réelle opaque du driver, native133/134 ou la convergence GM/ISO.
