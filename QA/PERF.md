# Rapport de tests de performance – DataShare

## 1. Objectif

L'objectif de ces tests est d'évaluer les performances de la fonctionnalité de téléchargement de fichiers de DataShare.

Deux approches complémentaires ont été retenues :

1. **Test de performance backend** : mesurer directement les performances de l'endpoint de téléchargement de l'API REST.
2. **Test de performance navigateur** : évaluer le parcours utilisateur depuis l'interface Angular, à l'aide de Google Chrome.

Le fichier utilisé pour les essais est un fichier binaire de **10 Mio** (mébioctets).

Ces tests permettent d'observer le comportement de l'application avec différents niveaux de charge, de relever les temps de réponse et d'identifier d'éventuelles limites dans l'environnement testé.

---

## 2. Environnement de test

### 2.1. Environnement matériel et système

| Élément | Configuration |
|---|---|
| Système d'exploitation | Ubuntu 26.04.1 LTS |
| Mémoire vive (RAM) | 15 Gio |
| Mémoire d'échange (swap) | 4 Gio |
| Mémoire swap utilisée après les tests | 3 Gio |
| Mémoire disponible après les tests | 4,5 Gio |
| Navigateur | Google Chrome 156.0.8078.47 |

Les mesures de mémoire ont été relevées après les tests. Elles ne constituent donc pas une mesure de la consommation maximale pendant leur exécution.

### 2.2. Environnement logiciel

| Composant | Configuration |
|---|---|
| Frontend | Angular |
| URL frontend | `http://localhost:4200` |
| Backend | Spring Boot |
| URL backend | `http://localhost:8080` |
| Langage backend | Java 21 |
| Base de données | PostgreSQL |
| Stockage de fichiers | MinIO |
| Outil de test | Grafana k6 |
| Mode navigateur | k6 Browser / Chromium |
| Exécutable Chrome | `/opt/google/chrome/chrome` |

Les tests ont été exécutés localement. Le frontend, le backend, les services de stockage et l'outil de test partagent donc les ressources de la même machine.

### 2.3. Fichier de test (../perf/test-file.bin)

| Paramètre | Valeur |
|---|---|
| Type de fichier | Fichier binaire |
| Taille | 10 Mio |
| Génération | Données aléatoires |
| Fonctionnalité testée | Téléchargement |

Le téléchargement est effectué à partir d'un fichier préalablement téléversé dans DataShare. Le même jeton de téléchargement est réutilisé pour les différents scénarios.

Tous les fichiers relatif aux test se trouvent ici(../perf/)

---

## 3. Test de performance backend

### 3.1. Objectif et périmètre

Ce test porte directement sur l'endpoint :

`POST /api/files/download`

La requête contient le jeton de téléchargement au format JSON :

```json
{
  "token": "DOWNLOAD_TOKEN"
}
```

Le jeton est transmis par variable d'environnement afin de ne pas l'inscrire directement dans le script de test.

L'objectif est de mesurer le comportement de l'endpoint lorsque plusieurs utilisateurs virtuels demandent simultanément le téléchargement du fichier.

Ce test concerne le parcours backend et ses dépendances effectives : application Spring Boot, base de données PostgreSQL, stockage MinIO et réseau.

Il ne mesure pas le temps de rendu de l'interface Angular.

### 3.2. Scénarios exécutés

Trois scénarios ont été exécutés :

| Scénario | Utilisateurs virtuels (VU) | Nombre d'exécutions |
|---|---:|---:|
| Test initial | 1 | 1 |
| Test en charge | 5 | 1 minute |
| Test en charge | 10 | 1 minute |

Un utilisateur virtuel (VU, *Virtual User*) représente un utilisateur simulé par k6.

### 3.3. Résultats

| Indicateur | 1 VU | 5 VU | 10 VU |
|---|---:|---:|---:|
| Requêtes exécutées | 1 | 166 | 170 |
| Débit moyen (requêtes/s) | — | 2,72 | 2,72 |
| Temps moyen | 670,86 ms | 1,82 s | 3,64 s |
| Temps minimum | 670,86 ms | 1,28 s | 2,51 s |
| Médiane (p50) | 670,86 ms | 1,74 s | 3,55 s |
| 90e percentile (p90) | 670,86 ms | 2,27 s | 4,06 s |
| 95e percentile (p95) | 670,86 ms | 2,43 s | 4,68 s |
| Temps maximum | 670,86 ms | 2,86 s | 5,09 s |
| Requêtes en échec | 0 % | 0 % | 0 % |
| Données reçues | 11 MB | 1,7 GB | 1,8 GB |

Le scénario initial à 1 VU ne comporte qu'une seule requête. Ses percentiles sont donc identiques à sa durée mesurée et ne sont pas représentatifs d'une distribution statistique.

### 3.4. Vérifications fonctionnelles

Chaque réponse a fait l'objet de trois vérifications :

- statut HTTP 200 ;
- type de contenu `application/octet-stream` ;
- présence de `attachment` dans l'en-tête `Content-Disposition`.

Les trois vérifications ont réussi sur l'ensemble des scénarios.

| Scénario | Vérifications réussies | Échecs |
|---|---:|---:|
| 1 VU | 3/3 | 0 |
| 5 VU | 498/498 | 0 |
| 10 VU | 510/510 | 0 |

### 3.5. Analyse des résultats backend

Le passage de 1 à 5 utilisateurs virtuels augmente le temps moyen de téléchargement de 670,86 ms à 1,82 seconde.

Entre 5 et 10 utilisateurs virtuels, le débit reste quasiment identique :

- 5 VU : 2,72 téléchargements par seconde ;
- 10 VU : 2,72 téléchargements par seconde.

En revanche, le temps moyen passe de 1,82 à 3,64 secondes. Le 95e percentile passe également de 2,43 à 4,68 secondes.

Ces observations indiquent que le débit atteint un plateau dans l'environnement de test. L'augmentation du nombre d'utilisateurs virtuels ne permet plus d'augmenter le nombre de téléchargements traités par seconde et entraîne une dégradation des temps de réponse.

Cela suggère l'existence d'une limite de capacité ou d'un goulot d'étranglement dans l'environnement testé.

**Bilan backend :** aucun échec HTTP ni aucune anomalie fonctionnelle n'ont été observés. Le débit mesuré reste stable entre 5 et 10 VU, mais les temps de réponse augmentent sensiblement.

---

## 4. Test de performance navigateur

### 4.1. Objectif et périmètre

Le test navigateur complète le test backend en évaluant le parcours réel depuis l'interface utilisateur.

Le scénario automatisé réalise les opérations suivantes :

1. Ouverture de la page de téléchargement à partir du jeton.
2. Attente de l'affichage de la carte de téléchargement.
3. Vérification de l'affichage du nom du fichier.
4. Clic sur le bouton de téléchargement.
5. Observation de la réponse de l'API.
6. Vérification du statut HTTP et des en-têtes de réponse.

Le test utilise k6 Browser avec Google Chrome.

Il mesure donc les performances du parcours dans un navigateur, et non uniquement celles de l'endpoint backend.

**Limite méthodologique :** le script vérifie la réponse HTTP déclenchée par le clic, mais ne vérifie pas l'intégrité du fichier effectivement enregistré sur le disque.

### 4.2. Scénarios exécutés

| Scénario | Utilisateurs virtuels (VU) | Durée |
|---|---:|---:|
| Test initial | 1 | 1 itération |
| Test en charge | 5 | 1 minute |
| Test en charge | 10 | 1 minute |

### 4.3. Résultats globaux

| Indicateur | 1 VU | 5 VU | 10 VU |
|---|---:|---:|---:|
| Itérations terminées | 1 | 36 | 37 |
| Durée moyenne d'une itération | 2,12 s | 6,51 s | 15,19 s |
| Requêtes HTTP navigateur | 32 | 1 152 | 1 182 |
| Temps moyen des requêtes HTTP | 138,76 ms | 486,67 ms | 840,72 ms |
| Médiane des requêtes HTTP | 141,34 ms | 377,07 ms | 675,29 ms |
| p90 des requêtes HTTP | 204,02 ms | 961,86 ms | 1,71 s |
| p95 des requêtes HTTP | 233,78 ms | 1,37 s | 2,11 s |
| Requêtes HTTP en échec | 0 % | 0 % | 0 % |
| Données reçues par le navigateur | 3,9 MB | 139 MB | 141 MB |

Le premier scénario est un test initial de fonctionnement avec une seule itération. Il ne doit pas être interprété comme un test de charge d'une minute.

### 4.4. Indicateurs Web Vitals

Les indicateurs Web Vitals permettent d'évaluer l'expérience utilisateur dans le navigateur.

| Indicateur | 1 VU | 5 VU | 10 VU |
|---|---:|---:|---:|
| FCP moyen | 760 ms | 3,03 s | 5,40 s |
| LCP moyen | 760 ms | 3,03 s | 5,40 s |
| INP moyen | 32 ms | 180,22 ms | 208 ms |
| TTFB moyen | 58,5 ms | 98,97 ms | 435,85 ms |
| CLS moyen | 0 | 0 | 0 |

### 4.5. Analyse des résultats navigateur

Les résultats montrent une dégradation progressive des performances lorsque le nombre d'utilisateurs virtuels augmente.

La durée moyenne d'une itération passe de 2,12 secondes avec 1 VU à 6,51 secondes avec 5 VU, puis à 15,19 secondes avec 10 VU.

Le temps moyen des requêtes HTTP du navigateur augmente également :

- 1 VU : 138,76 ms ;
- 5 VU : 486,67 ms ;
- 10 VU : 840,72 ms.

Le FCP et le LCP passent de 760 ms à 5,40 secondes entre le test initial et le scénario à 10 VU.

Le TTFB moyen augmente de 58,5 ms à 435,85 ms. Cette évolution indique que le délai avant réception de la première réponse augmente lui aussi sous charge.

L'INP moyen atteint 208 ms à 10 VU, ce qui dépasse légèrement le seuil de référence de 200 ms.

Le CLS reste nul dans les trois scénarios : aucun déplacement visuel significatif des éléments de la page n'a été mesuré.

### 4.6. Stabilité des processus navigateur

Des messages d'erreur ont été relevés pendant les tests à 5 et 10 VU :

- `context canceled` ;
- `signal: killed` ;
- `process unexpectedly ended`.

Ces messages indiquent que certains processus Chrome se sont terminés de manière inattendue pendant l'exécution.

Malgré ces événements :

- les itérations terminées ont toutes abouti ;
- les vérifications fonctionnelles ont réussi ;
- aucune requête HTTP en échec n'a été enregistrée.

Le nombre maximal de VU a bien été atteint, mais cela ne signifie pas que tous les navigateurs ont fonctionné sans interruption pendant toute la durée du test.

La vérification des journaux du noyau Linux n'a révélé aucun événement OOM (Out Of Memory) sur les deux heures précédant cette vérification.

Il n'est donc pas possible d'affirmer que les processus Chrome ont été arrêtés par l'OOM Killer.

Les résultats à 5 et 10 VU doivent être interprétés avec prudence : ils montrent une dégradation des performances, mais les arrêts inattendus de processus réduisent la fiabilité de ces mesures pour caractériser la capacité réelle de l'application.

**Bilan navigateur :** le parcours utilisateur fonctionne correctement dans les trois scénarios du point de vue des vérifications réalisées. Cependant, les performances se dégradent fortement sous charge et la stabilité de l'exécution navigateur constitue une limite expérimentale.

---

## 5. Budget de performance frontend

En complément des mesures réalisées, les seuils suivants sont retenus comme objectifs de référence pour l'expérience utilisateur.

Ils s'appuient sur les recommandations des Core Web Vitals.

| Indicateur | Objectif | Résultat à 1 VU | Résultat à 5 VU | Résultat à 10 VU |
|---|---:|---:|---:|---:|
| LCP | ≤ 2,5 s | 0,76 s | 3,03 s | 5,40 s |
| INP | ≤ 200 ms | 32 ms | 180,22 ms | 208 ms |
| CLS | ≤ 0,1 | 0 | 0 | 0 |

### Analyse du budget

- **LCP :** l'objectif est respecté à 1 VU, mais dépassé à 5 et 10 VU.
- **INP :** l'objectif est respecté à 1 et 5 VU. Il est légèrement dépassé à 10 VU.
- **CLS :** l'objectif est respecté dans les trois scénarios.

Ces seuils constituent des objectifs de qualité et non des garanties de performance sous charge.

Les résultats à 5 et 10 VU sont également influencés par l'exécution simultanée de plusieurs navigateurs sur la même machine.

---

## 6. Comparaison des deux approches

Les deux tests sont complémentaires, mais leurs résultats ne sont pas directement comparables.

| Critère | Test backend | Test navigateur |
|---|---|---|
| Périmètre | Endpoint de téléchargement | Parcours utilisateur complet |
| Outil | k6 HTTP | k6 Browser |
| Navigateur requis | Non | Oui |
| Rendu Angular | Non mesuré | Mesuré |
| Requête de téléchargement | Oui | Oui |
| Indicateurs Web Vitals | Non | Oui |
| Ressources navigateur | Non | Oui |
| Objectif principal | Mesurer la capacité de l'endpoint | Évaluer l'expérience utilisateur |

Le test backend permet d'isoler le comportement de l'API et de ses dépendances.

Le test navigateur prend en compte des éléments supplémentaires : chargement de la page, exécution JavaScript, rendu graphique, gestion des interactions et déclenchement du téléchargement.

Les temps de réponse mesurés dans les deux tests ne représentent donc pas la même chose.

---

## 7. Limites des tests

Plusieurs limites doivent être prises en compte dans l'interprétation des résultats.

### 7.1. Environnement local

Les tests ont été réalisés sur une seule machine.

Le frontend, le backend, la base de données, le stockage et les navigateurs Chrome partagent les ressources matérielles disponibles.

Les résultats ne peuvent donc pas être considérés comme une mesure de capacité dans un environnement de production distribué.

### 7.2. Ressources consommées par les navigateurs

Les scénarios navigateur nécessitent le lancement de plusieurs processus Chrome.

Cette consommation de ressources s'ajoute à celle de l'application et de l'outil k6.

Les arrêts inattendus de certains processus Chrome à 5 et 10 VU constituent une limite importante de ces essais.

### 7.3. Nombre limité de répétitions

Les tests ne constituent pas une campagne exhaustive de performance.

Le scénario backend à 1 VU ne comporte qu'une seule requête et le scénario navigateur initial ne comporte qu'une seule itération.

Les résultats à 5 et 10 VU ont été exécutés sur une durée d'une minute.

Il serait nécessaire de répéter les essais dans un environnement isolé et stable pour obtenir des mesures statistiques plus représentatives.

### 7.4. Périmètre des mesures

Les mesures recueillies portent principalement sur les requêtes HTTP et le navigateur.

## 8. Conclusion générale

Les tests réalisés permettent de valider le fonctionnement de la fonctionnalité de téléchargement de DataShare et d'observer son comportement sous différentes charges.

### Backend

Les trois scénarios ont réussi leurs vérifications fonctionnelles, sans erreur HTTP.

Le débit atteint environ 2,72 téléchargements par seconde à 5 VU et ne progresse pas à 10 VU. En parallèle, les temps de réponse augmentent sensiblement.

Ces résultats suggèrent une limite de capacité dans l'environnement testé, sans permettre d'en identifier la cause exacte.

### Frontend et navigateur

Le parcours utilisateur fonctionne correctement dans les trois scénarios du point de vue des vérifications réalisées.

Les indicateurs Web Vitals montrent toutefois une dégradation progressive sous charge, notamment pour le LCP et le TTFB.

Les arrêts inattendus de certains processus Chrome à 5 et 10 VU limitent la portée des résultats.

### Bilan

La campagne confirme le bon fonctionnement fonctionnel du téléchargement et fournit une première évaluation de ses performances côté backend et côté navigateur.

Elle met également en évidence deux points à surveiller en fonction de l'environnement matériel du test:

- le plateau du débit backend lorsque la charge augmente ;
- la dégradation de l'expérience utilisateur et l'instabilité des processus navigateur sous charge.

Les résultats doivent être considérés comme des mesures exploratoires réalisées dans un environnement local, et non comme une caractérisation définitive des capacités de DataShare en production.

---

## 9. Glossaire

| Terme | Définition |
|---|---|
| API | Application Programming Interface : interface de programmation permettant aux applications de communiquer. |
| VU | Virtual User : utilisateur virtuel simulé par l'outil de test. |
| HTTP | Hypertext Transfer Protocol : protocole de communication utilisé sur le Web. |
| req/s | Requêtes par seconde : nombre de requêtes traitées chaque seconde. |
| avg | Average : valeur moyenne. |
| min | Valeur minimale mesurée. |
| max | Valeur maximale mesurée. |
| p50 | 50e percentile : médiane, 50 % des mesures sont inférieures ou égales à cette valeur. |
| p90 | 90e percentile : 90 % des mesures sont inférieures ou égales à cette valeur. |
| p95 | 95e percentile : 95 % des mesures sont inférieures ou égales à cette valeur. |
| FCP | First Contentful Paint : délai avant l'affichage du premier contenu de la page. |
| LCP | Largest Contentful Paint : délai avant l'affichage du plus grand élément de contenu visible. |
| INP | Interaction to Next Paint : mesure la réactivité de la page aux interactions utilisateur. |
| TTFB | Time to First Byte : délai avant réception du premier octet de la réponse. |
| CLS | Cumulative Layout Shift : mesure les déplacements inattendus des éléments visuels de la page. |
| RAM | Random Access Memory : mémoire vive de la machine. |
| OOM | Out Of Memory : situation dans laquelle le système manque de mémoire disponible. |
| Mio | Mébioctet : unité correspondant à 1 048 576 octets. |
| GB | Gigaoctet : unité décimale correspondant à 1 000 000 000 octets. |
| Core Web Vitals | Indicateurs de Google permettant d'évaluer l'expérience utilisateur sur le Web. |