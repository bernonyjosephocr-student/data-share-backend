# Périmètre de l'audit

Les éléments suivants ont été examinés:

| Contrôle | Outil | Objectif |
|---|---|---|
| Dépendances Angular | `npm audit` | Détecter les dépendances vulnérables |
| Dépendances Java/Spring Boot | Trivy | Détecter les vulnérabilités connues |
| Secrets exposés | Trivy | Repérer les mots de passe, tokens et clés présents dans le projet |
| Configuration | Trivy | Détecter les mauvaises configurations |
| Docker | Trivy | Examiner les images et fichiers de configuration Docker |

## Bilan de sécurité et de migration – DataShare Frontend

### 1. Situation initiale : Angular 19

Au départ, le projet utilisait les versions suivantes :

| Dépendance | Version initiale |
|---|---:|
| Angular | 19.2.25 |
| Angular CLI | 19.2.27 |
| TypeScript | 5.7.3 |
| RxJS | 7.8.2 |
| Node.js | 22.23.3 |

L'analyse initiale avec `npm audit --omit=dev` avait révélé **7 vulnérabilités dans les dépendances de production** :

- 3 vulnérabilités modérées ;
- 4 vulnérabilités élevées.

Elles concernaient principalement Angular :

| Composant | Risques identifiés |
|---|---|
| `@angular/common` | Déni de service, consommation excessive de mémoire, problèmes de cache et de protection XSRF |
| `@angular/compiler` | Risques de XSS et contournement de protections |
| `@angular/core` | DOM Clobbering, empoisonnement du cache et XSS |
| `@angular/forms`, `platform` et `router` | Vulnérabilités de gravité modérée |

Par ailleurs, des vulnérabilités avaient également été identifiées dans des dépendances utilisées par l'environnement de compilation, notamment `piscina` et `tar`.

**Décision :** abandonner Angular 19, qui n'était plus maintenu, et procéder à une migration progressive vers Angular 20 puis Angular 21.

### 2. Première migration : Angular 19 → Angular 20

#### Objectifs

- Corriger les vulnérabilités affectant les dépendances Angular de production.
- Retrouver une version maintenue du framework.
- Mettre à niveau TypeScript vers 5.8.3.
- Migrer vers le nouveau builder Angular `@angular/build`.

La migration a également intégré le builder applicatif avec `use-application-builder`.

#### Résultat de l'audit de production

Après cette première migration :

```bash
npm audit --omit=dev
```

Résultat :

```text
found 0 vulnerabilities
```

Les 7 vulnérabilités initialement détectées dans les dépendances de production n'étaient donc plus signalées.

### 3. Mise à niveau de Jest

Dans le cadre de la modernisation de l'environnement de test, Jest a également été mis à niveau.

| Dépendance | Avant | Après |
|---|---:|---:|
| Jest | 29.7.0 | 30.5.2 |
| jest-preset-angular | — | 17.0.3 |
| jsdom | — | 26.1.0 |
| TypeScript | 5.8.3 | 5.8.3 |

Cette mise à niveau a permis de conserver un environnement de test compatible avec les versions récentes d'Angular.

Elle a toutefois fait apparaître des vulnérabilités transitives supplémentaires dans l'audit complet.

### 4. Deuxième migration : Angular 20 → Angular 21

Cette seconde migration répond à un objectif de pérennité : ne pas rester sur Angular 20 à l'approche de la fin de sa période de maintenance, prévue le 28 novembre 2026.

#### Versions obtenues

| Dépendance | Version finale |
|---|---:|
| Angular | 21.2.26 |
| Angular CLI | 21.2.30 |
| Angular Build | 21.2.30 |
| Angular Compiler CLI | 21.2.26 |
| TypeScript | 5.9.3 |
| RxJS | 7.8.2 |
| Node.js | 22.23.3 |

La migration a été réalisée sans passer à Angular 22 et sans appliquer la migration `router-current-navigation`, qui n'était pas nécessaire.

### 5. Vulnérabilités restantes après migration

Le dernier `npm audit` complet signale **34 vulnérabilités** :

- 1 faible ;
- 33 modérées.

Elles concernent principalement trois dépendances transitives :

| Dépendance | Référence | Risque |
|---|---|---|
| `deepmerge` | GHSA-gcqx-w48m-w2h4 | Prototype poisoning |
| `glob` | GHSA-3g3m-2fjr-6pw4 | Contournement de l'annulation par AbortSignal et parcours de fichiers après expiration |
| `minimatch` | GHSA-g3hv-75wg-m249 | Déni de service par amplification de motifs glob |

Ces dépendances sont présentes dans les chaînes de dépendances de Jest, Angular CLI et de certains outils npm.

#### Pourquoi ne pas appliquer les corrections proposées par npm ?

L'audit propose des corrections forcées qui impliqueraient notamment :

- Une rétrogradation de Jest 30 vers Jest 29.7.0 pour `deepmerge`.
- Un passage à Angular CLI 22.2.7 pour `glob` et `minimatch`.

Nous avons volontairement écarté `npm audit fix --force`, afin de ne pas dégrader l'environnement validé ni sortir du périmètre Angular 21 retenu pour le projet.

#### Résultat de l'audit de production

Commande :

```bash
npm audit --omit=dev
```

Résultat :

```text
found 0 vulnerabilities
```

Les 34 vulnérabilités restantes ne sont donc pas signalées dans l'arbre des dépendances de production.

### 6. Validation finale

| Contrôle | Résultat |
|---|---|
| Migration Angular 19 → 20 | Réussie |
| Migration Angular 20 → 21 | Réussie |
| Migration du builder Angular | Réussie |
| Build Angular 21 | Réussi |
| Tests Jest | 54/54 réussis |
| Suites Jest | 12/12 réussies |
| Tests Cypress E2E | Réussis |
| Audit npm production | 0 vulnérabilité |
| Audit npm complet | 34 vulnérabilités restantes |

### 7. Conclusion

La démarche a permis de répondre à deux objectifs successifs :

**1. Sécurité**

Éliminer les vulnérabilités initialement détectées dans les dépendances Angular de production, avec un audit désormais à zéro vulnérabilité en production.

**2. Maintenabilité**

Faire évoluer le projet d'Angular 19 vers Angular 21, moderniser le builder et mettre à niveau l'environnement de tests.

Les vulnérabilités transitives de développement restent identifiées et devront être réévaluées lors des prochaines mises à jour.

Le résultat est un frontend DataShare modernisé, dont la compilation, les tests unitaires et les tests E2E sont validés.


# Bilan de l'audit de sécurité Trivy — Backend DataShare

**Date de l'audit :** 12 novembre 2026  
**Outil :** Trivy v0.76  
**Périmètre :** Backend DataShare (Spring Boot / Java 21)

## 1. Synthèse des contrôles

| Contrôle | Résultat | Conclusion |
|---|---|---|
| Vulnérabilités des dépendances Java | 0 vulnérabilité détectée | ✅ Conforme au scan |
| Détection des secrets | 1 secret détecté dans `.env` | ⚠️ Alerte HIGH |
| Analyse des configurations | Aucun fichier de configuration reconnu | ⚪ Contrôle non concluant |
| Configuration Docker Compose | Fichier `compose.yaml` non reconnu par le scanner | ⚪ Contrôle non concluant |
| Images Docker | Analyse non réalisée | ⏳ À effectuer |

## 2. Analyse des vulnérabilités des dépendances Java

**Commande exécutée :**

```bash
trivy fs --scanners vuln .
```

**Résultat :**
- Le fichier `pom.xml` a été identifié comme cible d'analyse.
- Les dépendances Maven ont été résolues.
- Aucune vulnérabilité connue n'a été détectée.

**Conclusion :**

Aucune vulnérabilité connue n'a été identifiée dans les dépendances Java analysées par Trivy au moment du contrôle.

Ce résultat dépend de la couverture de la base de vulnérabilités utilisée par Trivy et ne constitue pas une garantie d'absence de vulnérabilités.

## 3. Détection des secrets

### 3.1. Premier constat

Les premiers scans ont montré que Trivy parcourait les fichiers du projet, mais ne fournissaient pas de résultat global suffisamment exploitable pour conclure sur l'absence de secrets.

L'ajout d'un fichier de configuration personnalisé `trivy-secret.yaml` a permis d'activer une règle de détection adaptée.

### 3.2. Commande exécutée

```bash
trivy fs --scanners secret .
```

**Résultat :**

| Élément | Résultat |
|---|---|
| Fichier concerné | `.env` |
| Ligne | 1 |
| Règle déclenchée | `generic-env-password` |
| Sévérité | HIGH |
| Nombre de secrets détectés | 1 |

Trivy a identifié un mot de passe ou secret générique dans le fichier `.env`.

### 3.3. Mesures correctives

L'analyse de l'historique Git a révélé que `.env` avait été présent dans plusieurs commits, depuis le commit initial du projet.

Les mesures suivantes ont été prises :

- Retrait du fichier `.env` du suivi Git avec `git rm --cached .env`.
- Vérification de son exclusion par la règle `.gitignore`.
- Vérification que le fichier est toujours disponible localement.
- Identification de commits historiques contenant ce fichier.

**Remédiation complémentaire nécessaire :**

Le mot de passe potentiellement exposé dans l'historique Git doit être renouvelé. Si le dépôt a été publié sur un serveur distant, il faut considérer que l'ancien secret a pu être consulté.

Le retrait du fichier du suivi Git ne supprime pas son contenu des anciens commits.

### 3.4. Conclusion

Un secret de sévérité HIGH a été identifié dans `.env`. Cette détection a permis de mettre en évidence un risque d'exposition historique et de prendre les premières mesures correctives.

Le renouvellement du mot de passe reste nécessaire pour neutraliser le risque.

## 4. Analyse des mauvaises configurations

### 4.1. Analyse du projet

**Commande exécutée :**

```bash
trivy fs --scanners misconfig .
```

**Résultat :**

```text
Detected config files num=0
```

Aucun fichier de configuration pris en charge par le scanner n'a été identifié.

### 4.2. Analyse de Docker Compose

**Commande exécutée :**

```bash
trivy config compose.yaml
```

**Résultat :**

```text
Detected config files num=0
Supported files for scanner(s) not found
```

Le fichier `compose.yaml` n'a pas été reconnu comme fichier de configuration pris en charge dans ce contexte d'analyse.

### 4.3. Conclusion

Ces contrôles n'ont pas permis d'évaluer les configurations du projet.

L'absence de résultats ne signifie donc pas qu'aucune mauvaise configuration n'existe. Une analyse complémentaire avec un outil ou un format pris en charge sera nécessaire pour évaluer ces éléments.

## 5. Analyse des images Docker

L'analyse des images Docker n'a pas encore été réalisée.

Ce contrôle devra notamment porter sur :

- Les vulnérabilités des paquets présents dans les images.
- Les composants système et bibliothèques installés.
- Les versions des images de base.
- Les éventuelles vulnérabilités connues des images utilisées.

## 6. Synthèse des actions

| Action | État |
|---|---|
| Analyse des dépendances Java | ✅ Réalisée |
| Détection des secrets | ✅ Réalisée |
| Configuration personnalisée des règles de détection | ✅ Réalisée |
| Identification du secret dans `.env` | ✅ Réalisée |
| Retrait de `.env` du suivi Git | ✅ Réalisé |
| Vérification de l'exclusion dans `.gitignore` | ✅ Réalisée |
| Renouvellement du mot de passe exposé | ⏳ À effectuer |
| Analyse des configurations | ⚪ Non concluante |
| Analyse des images Docker | ⏳ À effectuer |

## 7. Conclusion générale

L'audit de sécurité du backend DataShare avec Trivy a permis d'identifier un secret de sévérité HIGH dans le fichier `.env`. L'analyse de l'historique Git a également révélé que ce fichier avait été présent dans plusieurs commits.

Le fichier a depuis été retiré du suivi Git et son exclusion est effective. Le renouvellement du mot de passe reste toutefois nécessaire afin de neutraliser le risque d'exposition historique.

Par ailleurs, aucune vulnérabilité connue n'a été détectée dans les dépendances Java analysées.

Les contrôles de configuration n'ont pas abouti, car Trivy n'a reconnu aucun fichier pris en charge dans le périmètre testé. Enfin, l'analyse des images Docker reste à réaliser.

**Bilan : audit partiellement concluant. Une exposition de secret a été identifiée, les premières mesures correctives ont été appliquées et plusieurs contrôles complémentaires restent nécessaires.**