# DataShare – Backend

## 1. Présentation

DataShare est une application de partage de fichiers.

Le backend repose sur Spring Boot et expose une API REST permettant notamment :
- La gestion des utilisateurs et de l'authentification.
- Le téléversement de fichiers.
- La génération de liens de téléchargement.
- Le téléchargement des fichiers partagés.
- La gestion des métadonnées et de la durée de conservation des fichiers.
- Le nettoyage automatique des fichiers expirés.

Les fichiers sont stockés dans MinIO, compatible avec l'API Amazon S3. Les données applicatives sont persistées dans PostgreSQL.

## 2. Technologies utilisées

| Technologie | Version |
|---|---|
| Java | 21 |
| Spring Boot | 4.1.2 |
| Spring Security | Gérée par Spring Boot |
| Spring Data JPA | Gérée par Spring Boot |
| Springdoc OpenAPI | 3.2.0 |
| PostgreSQL | 18 |
| MinIO | Image Chainguard |
| AWS SDK S3 | 2.25.15 |
| MapStruct | 1.6.3 |
| Testcontainers | Géré par Spring Boot |
| JaCoCo | 0.8.14 |

## 3. Prérequis

- JDK 21.
- Apache Maven 3.9.x.
- Docker Engine ou Docker Desktop.
- Docker Compose.
- OpenSSL pour générer la clé JWT.

Docker doit être installé et son moteur doit être opérationnel.

Le projet utilise l'intégration Docker Compose de Spring Boot pour démarrer automatiquement PostgreSQL et MinIO.

Des outils complémentaires sont nécessaires pour les contrôles de sécurité et les tests de performance :
- Trivy.
- k6.
- Google Chrome pour les tests navigateur avec k6 Browser.

## 4. Installation et configuration

### 4.1. Récupération du projet

Se positionner dans le répertoire backend :

```bash
cd data-share-backend
```

### 4.2. Configuration des variables d'environnement

Créer un fichier `.env` à la racine du backend, au même niveau que `compose.yaml`.

Exemple :

```dotenv
# PostgreSQL
DB_HOST=localhost
DB_PORT=5432
DB_USER=datashare
DB_PASSWORD=change_me
DB_NAME=data_share_db

# MinIO - administration
MINIO_ROOT_USER=datashare_admin
MINIO_ROOT_PASSWORD=change_me_minio

# MinIO - connexion backend
MINIO_ENDPOINT=http://localhost:9000
MINIO_BUCKET=my-shared-bucket
MINIO_ACCESS_KEY=datashare_admin
MINIO_SECRET_KEY=change_me_minio
MINIO_REGION=us-east-1

# JWT
JWT_SECRET_BASE64=REMPLACER_PAR_UNE_CLE_ALEATOIRE
JWT_EXPIRATION_MS=900000

# Stockage local éventuel
FILE_UPLOAD_DIR=./local-file-store
```

#### Génération de la clé JWT

La clé JWT doit être une clé aléatoire d'au moins 32 octets, encodée en Base64.

Générer une clé avec OpenSSL :

```bash
openssl rand -base64 32
```

Reporter le résultat dans `JWT_SECRET_BASE64`.

Ne jamais versionner le fichier `.env` ni partager les véritables identifiants.

Un fichier `.env.example` doit être fourni pour présenter les variables attendues sans exposer de secrets.

### 4.3. Fonctionnement de Docker Compose et initialisation du stockage

Le projet utilise la dépendance `spring-boot-docker-compose` pour automatiser le démarrage de son infrastructure.

Lors du lancement de Spring Boot, l'intégration détecte automatiquement le fichier `compose.yaml` et démarre les services nécessaires :

- PostgreSQL pour la persistance des données.
- MinIO pour le stockage des fichiers.

Il n'est donc pas nécessaire de démarrer manuellement les conteneurs Docker.

L'application assure également automatiquement l'initialisation du stockage MinIO, notamment la création du bucket configuré si celui-ci n'existe pas.

Les services sont accessibles aux adresses suivantes :

| Service | Adresse |
|---|---|
| PostgreSQL | localhost:5432 |
| MinIO API | http://localhost:9000 |
| MinIO Console | http://localhost:9001 |

Les données PostgreSQL sont conservées dans le volume Docker `pgdata`.

Les fichiers MinIO sont conservés dans le répertoire `dossier-serveur`.

### 4.4. Chargement des variables d'environnement pour Spring Boot

Contrairement à Docker Compose, Spring Boot ne charge pas automatiquement le fichier `.env`.

Les variables doivent être exportées dans le terminal avant le démarrage de l'application.

Depuis la racine du backend :

```bash
set -a
source .env
set +a
```

Cette opération permet notamment de rendre accessibles à Spring Boot les variables `DB_*`, `MINIO_*` et `JWT_*`.

**Important :** cette opération doit être répétée dans chaque nouveau terminal utilisé pour démarrer le backend.

## 5. Compilation et exécution

### 5.1. Compilation

Compiler le projet :

```bash
mvn clean compile
```

### 5.2. Démarrage de l'application

Après avoir chargé les variables d'environnement, démarrer Spring Boot :

```bash
mvn spring-boot:run
```

Cette commande :
1. Démarre l'application Spring Boot.
2. Détecte automatiquement le fichier `compose.yaml`.
3. Démarre les conteneurs PostgreSQL et MinIO.
4. Initialise les composants nécessaires à l'application.

Le backend est accessible à l'adresse :

http://localhost:8080

## 6. Documentation de l'API

L'API est documentée avec OpenAPI et Springdoc.

Interface Swagger :

http://localhost:8080/swagger-ui/index.html

Cette interface permet de consulter les endpoints et de tester les opérations exposées par le backend.

## 7. Tests et couverture de code

Les tests backend utilisent les outils de test de Spring Boot et Testcontainers.

Les tests d'intégration s'appuient notamment sur PostgreSQL via Testcontainers.

Docker doit être opérationnel pour exécuter ces tests.

### Exécution des tests

```bash
mvn test
```

### Tests avec génération du rapport de couverture

```bash
mvn clean test
```

JaCoCo génère le rapport HTML dans :

```text
target/site/jacoco/index.html
```

Le rapport permet de consulter la couverture des classes, méthodes et instructions exécutées pendant les tests.

## 8. Analyse de sécurité

Les analyses de sécurité sont réalisées avec Trivy.

### 8.1. Analyse des vulnérabilités

```bash
trivy fs --scanners vuln .
```

Cette analyse recherche les vulnérabilités connues dans les dépendances du projet.

### 8.2. Détection des secrets

```bash
trivy fs --scanners secret .
```

### 8.3. Détection personnalisée des secrets

Le fichier `trivy-secret.yaml` définit des règles complémentaires de détection, notamment pour les mots de passe, tokens et clés présents dans les fichiers d'environnement.

Exécuter l'analyse avec les règles personnalisées :

```bash
trivy fs --scanners secret --secret-config trivy-secret.yaml .
```

### 8.4. Analyse des configurations

```bash
trivy config compose.yaml
```

Cette analyse recherche les mauvaises configurations susceptibles de présenter des risques de sécurité.

## 9. Tests de performance

Les tests de performance utilisent k6.

Les scénarios permettent d'évaluer les performances du téléchargement, notamment au niveau du backend et de ses dépendances : Spring Boot, PostgreSQL, MinIO et réseau.

### Tests navigateur

Le scénario `perf/download-browser.js` permet de mesurer le comportement du parcours de téléchargement depuis un navigateur.

Prérequis :
- k6 installé.
- Google Chrome installé.
- Backend et frontend démarrés.
- Environnement de test préparé.

Exemple d'exécution :

```bash
VUS=5 DURATION=1m \
K6_BROWSER_EXECUTABLE_PATH=/opt/google/chrome/chrome \
k6 run perf/download-browser.js
```

## 10. Arrêt de l'application

Arrêter le backend avec `Ctrl+C`.

L'intégration Docker Compose de Spring Boot gère également le cycle de vie des conteneurs démarrés par l'application.

Pour arrêter manuellement les services, si nécessaire :

```bash
docker compose down
```

Cette commande arrête les conteneurs sans supprimer les données persistées.

Pour supprimer également le volume PostgreSQL :

```bash
docker compose down -v
```

Attention : cette dernière commande entraîne la suppression des données PostgreSQL.