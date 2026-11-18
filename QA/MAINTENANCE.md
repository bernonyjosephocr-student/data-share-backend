# Maintenance – DataShare

## 1. Objectif

Ce document décrit les procédures de maintenance de DataShare afin de garantir la sécurité, la stabilité et la disponibilité de l'application.

La maintenance concerne les deux parties de l'application :

- **Backend** : Java 21, Spring Boot, Maven, PostgreSQL et MinIO.
- **Frontend** : Angular 21, TypeScript, npm et Cypress.

Elle comprend notamment :

- la mise à jour des dépendances et des environnements d'exécution ;
- le contrôle des vulnérabilités ;
- la surveillance du fonctionnement de l'application ;
- la sauvegarde et la restauration des données ;
- la prévention et la résolution des incidents.

## 2. Procédures de mise à jour

### 2.1. Backend

Le backend repose sur Java 21 et Spring Boot 4.1.2.

Les mises à jour doivent être réalisées progressivement, en vérifiant la compatibilité des nouvelles versions avec les dépendances existantes.

**Procédure :**

1. Consulter les nouvelles versions disponibles des dépendances dans `pom.xml`.
2. Vérifier les éventuelles vulnérabilités connues.
3. Mettre à jour les dépendances concernées.
4. Compiler le projet.
5. Exécuter les tests automatisés.
6. Vérifier le bon fonctionnement de l'application avec PostgreSQL et MinIO.

Commandes de vérification :

```bash
mvn clean compile
mvn clean test
```

Le rapport de couverture des tests est disponible dans :

```text
target/site/jacoco/index.html
```

Une mise à jour majeure de Spring Boot ou de Java doit faire l'objet d'une vérification spécifique de compatibilité avant son intégration.

### 2.2. Frontend

Le frontend repose sur Angular 21 et TypeScript 5.9.3.

Les mises à jour doivent préserver la compatibilité entre Angular, Angular CLI, TypeScript et les outils de test.

**Procédure :**

1. Consulter les nouvelles versions des dépendances.
2. Examiner les éventuelles vulnérabilités.
3. Vérifier les contraintes de compatibilité.
4. Mettre à jour les dépendances.
5. Réinstaller les dépendances et exécuter les tests.
6. Vérifier la compilation et les parcours utilisateurs.

Commandes de vérification :

```bash
npm outdated
npm audit
npm audit --omit=dev
```

Après une mise à jour :

```bash
npm ci
npm run build
npx jest --coverage
npx cypress run
```

Les mises à jour majeures d'Angular doivent être réalisées en suivant le guide officiel de migration et en vérifiant les éventuelles modifications incompatibles.

### 2.3. Infrastructure Docker

L'infrastructure utilise Docker Compose pour démarrer automatiquement PostgreSQL et MinIO lors du lancement du backend.

Les images Docker doivent être mises à jour régulièrement, en vérifiant leur compatibilité avec les versions utilisées par l'application.

**Procédure :**

1. Consulter les nouvelles versions des images.
2. Vérifier les changements et les éventuelles vulnérabilités.
3. Tester les nouvelles images dans un environnement de développement.
4. Vérifier la persistance des données.
5. Contrôler le bon fonctionnement des services après mise à jour.

Il est recommandé de privilégier des versions d'images explicitement définies plutôt que des tags flottants tels que `latest`, afin de garantir la reproductibilité des déploiements.

## 3. Contrôles de sécurité

La sécurité des dépendances et de l'infrastructure doit être vérifiée régulièrement.

### 3.1. Dépendances frontend

Audit npm :

```bash
npm audit
npm audit --omit=dev
```

Le premier contrôle concerne l'ensemble des dépendances, tandis que le second cible les dépendances de production.

Les vulnérabilités doivent être analysées avant toute correction automatique, notamment lorsqu'une mise à jour majeure est nécessaire.

### 3.2. Dépendances backend

Analyse des vulnérabilités avec Trivy :

```bash
trivy fs --scanners vuln .
```

Cette analyse permet de détecter les vulnérabilités connues dans les dépendances du projet.

### 3.3. Recherche de secrets

Analyse des fichiers du projet :

```bash
trivy fs --scanners secret .
```

Si une configuration personnalisée est utilisée :

```bash
trivy fs --scanners secret --secret-config trivy-secret.yaml .
```

Les fichiers contenant des informations sensibles, notamment `.env`, ne doivent pas être versionnés.

En cas d'exposition d'un secret :

- identifier les systèmes concernés ;
- remplacer immédiatement le secret compromis ;
- vérifier les éventuelles traces de son utilisation ;
- supprimer les occurrences accidentelles dans les dépôts et les historiques lorsque cela est nécessaire.

## 4. Sauvegarde et restauration des données: procédures à définir et à tester avant une mise en production.

La sauvegarde est essentielle pour éviter toute perte de données en cas de panne ou de mauvaise manipulation.

DataShare utilise deux espaces de stockage persistants :

- PostgreSQL pour les métadonnées des fichiers et les informations utilisateurs ;
- MinIO pour les fichiers partagés.

### 4.1. Sauvegarde PostgreSQL

Une sauvegarde logique de la base de données peut être réalisée avec `pg_dump` :

```bash
docker exec file_sharing_db sh -c \
  'pg_dump -U "$POSTGRES_USER" "$POSTGRES_DB"' \
  > backup-datashare.sql
```

Le fichier généré doit être conservé dans un emplacement sécurisé, distinct du volume Docker utilisé par PostgreSQL.

### 4.2. Sauvegarde MinIO

Les fichiers sont stockés dans le répertoire `dossier-serveur`, monté dans le conteneur MinIO.

Ce répertoire doit être sauvegardé régulièrement vers un emplacement indépendant.

Il est préférable d'utiliser une procédure de sauvegarde cohérente avec le fonctionnement de MinIO et de vérifier l'intégrité des fichiers sauvegardés.

### 4.3. Restauration

En cas de perte ou de corruption de la base de données, une sauvegarde PostgreSQL peut être restaurée après préparation de la base cible :

```bash
cat backup-datashare.sql | docker exec -i file_sharing_db sh -c \
  'psql -U "$POSTGRES_USER" "$POSTGRES_DB"'
```

Pour MinIO, les fichiers sauvegardés doivent être restaurés dans le stockage correspondant, en respectant les précautions propres à MinIO.

**Important :** une sauvegarde n'est considérée comme fiable qu'après vérification de son intégrité et réalisation périodique d'un test de restauration.

La sauvegarde de PostgreSQL et celle de MinIO doivent être coordonnées afin de conserver la cohérence entre les métadonnées et les fichiers stockés.

## 5. Surveillance et maintenance préventive

Plusieurs éléments doivent être contrôlés afin de détecter les anomalies avant qu'elles ne deviennent des incidents.

### 5.1. Backend

Contrôler :

- les journaux d'exécution Spring Boot ;
- les erreurs de connexion à PostgreSQL ;
- la disponibilité du stockage MinIO ;
- les erreurs lors des opérations d'envoi et de téléchargement ;
- l'exécution du nettoyage automatique des fichiers expirés.

Le nettoyage automatique est configuré pour s'exécuter toutes les cinq minutes mais ceci est configurable(ref: .env.example)

La durée maximale de conservation des fichiers est actuellement fixée à sept jours.

### 5.2. Frontend

Contrôler :

- les erreurs JavaScript dans la console du navigateur ;
- les erreurs de communication avec l'API ;
- le fonctionnement de l'authentification ;
- les parcours de partage et de téléchargement ;
- la compatibilité avec les versions récentes des navigateurs.

### 5.3. Infrastructure

Contrôler :

- l'état des conteneurs Docker ;
- l'espace disque disponible ;
- l'état des volumes persistants ;
- les ressources CPU et mémoire ;
- la disponibilité des ports utilisés par PostgreSQL et MinIO.

## 6. Fréquence recommandée des opérations

La périodicité est à définir selon les conditions d'exploitation.

Les fréquences proposées sont des recommandations de maintenance et non des tâches automatisées par défaut.

| Opération | Fréquence | Objectif |
|---|---|---|
| Vérification des journaux | À définir | Détecter les erreurs et les anomalies de fonctionnement |
| Audit npm | À définir (commande disponible) | Identifier les vulnérabilités des dépendances frontend |
| Analyse Trivy | À définir (outil utilisé) | Détecter les vulnérabilités, les secrets exposés et les erreurs de configuration |
| Vérification des mises à jour | À définir | Identifier les nouvelles versions et les correctifs disponibles |
| Mise à jour des dépendances | Selon les besoins et les correctifs disponibles | Corriger les vulnérabilités et maintenir la compatibilité des composants |
| Sauvegarde PostgreSQL | Procédure à définir | Protéger les métadonnées des utilisateurs et des fichiers partagés |
| Sauvegarde MinIO | Procédure à définir | Préserver les fichiers stockés |
| Vérification des sauvegardes | Procédure à définir | S'assurer de l'intégrité des sauvegardes |
| Test de restauration | Procédure à définir | Vérifier la capacité à récupérer les données après un incident |
| Surveillance des performances | À définir | Détecter les dégradations et les régressions de performances |

## 7. Gestion des incidents

En cas d'incident, appliquer une procédure progressive.

### 7.1. Identification

- Identifier le service concerné : frontend, backend, PostgreSQL ou MinIO.
- Consulter les journaux/logs.
- Reproduire le problème lorsque cela est possible.
- Évaluer l'impact sur les utilisateurs et les données.

### 7.2. Correction

- Corriger la configuration ou le composant concerné.
- Appliquer les correctifs nécessaires.
- Enrichir les tests si besoin.
- Si nécessaire, restaurer les données à partir d'une sauvegarde.
- Vérifier le fonctionnement des services après intervention.

### 7.3. Validation

Avant de considérer l'incident comme résolu :

- exécuter les tests automatisés ;
- vérifier les opérations principales de l'application ;
- contrôler les journaux ;
- vérifier l'intégrité des données.

## 8. Gestion des changements et retour arrière

Toute modification importante doit être réalisée dans une branche Git dédiée et validée avant son intégration dans la branche principale.

Les modifications doivent être accompagnées, lorsque cela est nécessaire, de tests et d'une mise à jour de la documentation.

En cas de régression après une mise à jour :

1. Identifier le changement responsable.
2. Revenir à une version stable du code si nécessaire.
3. Restaurer la configuration précédente.
4. Restaurer les données uniquement si leur intégrité a été compromise.
5. Vérifier le fonctionnement complet de l'application.

Les sauvegardes doivent être conservées indépendamment des versions du code afin de permettre une restauration même en cas de problème sur le dépôt Git.

---

## 9. Bonnes pratiques

Pour assurer la pérennité de DataShare :

- Ne jamais versionner les fichiers contenant des secrets ou des mots de passe.
- Utiliser des secrets robustes et renouveler ceux qui sont compromis.
- Maintenir les dépendances à jour.
- Éviter les mises à jour majeures sans tests préalables.
- Conserver des sauvegardes indépendantes des volumes Docker.
- Tester régulièrement les procédures de restauration.
- Vérifier les résultats des audits de sécurité.
- Documenter les modifications importantes de configuration et d'infrastructure.
- Contrôler les effets des changements sur la conservation et la suppression automatique des fichiers.
