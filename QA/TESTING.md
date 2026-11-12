# Plan de tests pour DataShare

## BACKEND API

### IT1 – Étape 1 : Tests unitaires

#### 1. UserAccountServiceTest

**Objectif :** Vérifier les comportements métier du service `UserAccountService`, notamment l'inscription et l'authentification des utilisateurs.

| ID | Cas de test | Comportement attendu |
| --- | --- | --- |
| AUTH-UNIT-01 | Inscription d'un nouvel utilisateur | Le compte est enregistré avec un mot de passe encodé. |
| AUTH-UNIT-02 | Inscription avec un email existant | Une exception `EmailAlreadyUsedException` est levée. |
| AUTH-UNIT-03 | Connexion avec des identifiants valides | Un JWT est généré et retourné. |
| AUTH-UNIT-04 | Connexion avec un email inconnu | Une exception `BadCredentialsException` est levée. |
| AUTH-UNIT-05 | Connexion avec un mot de passe incorrect | Une exception `BadCredentialsException` est levée. |

#### 2. JWTServiceTest

**Objectif :** Vérifier la génération, la validation et l'extraction des informations contenues dans les JWT d'authentification et de téléchargement.

| ID | Cas de test | Comportement attendu |
| --- | --- | --- |
| JWT-UNIT-01 | Génération d'un JWT d'authentification | Génère un token correctement structuré, signé avec la clé configurée et contenant le sujet attendu ainsi qu'une date d'expiration. |
| JWT-UNIT-02 | Validation d'un JWT valide et non expiré | Vérifie la signature, l'expiration et retourne le sujet du token. |
| JWT-UNIT-03 | Génération d'un JWT de téléchargement | Génère un token contenant l'identifiant du téléversement, l'action `DOWNLOAD`, l'identifiant utilisateur lorsqu'il est fourni et la date d'expiration. |
| JWT-UNIT-04 | Validation d'un JWT de téléchargement valide | Vérifie la signature, l'action `DOWNLOAD`, l'expiration et retourne l'identifiant du téléversement. |
| JWT-UNIT-05 | Extraction du sujet d'un JWT de téléchargement expiré | Vérifie la signature et l'action `DOWNLOAD`, mais permet de récupérer le sujet même lorsque le token est expiré. |

#### 3. FileServiceTest

Objectif : Vérifier les comportements métier du service FileService, notamment le téléversement, la consultation, la suppression et le téléchargement des fichiers.

| ID | Cas de test | Comportement attendu |
| --- | --- | --- |
| FILE-UNIT-01 | Téléversement d'un fichier avec un utilisateur authentifié | Les métadonnées sont enregistrées, le contenu est stocké dans MinIO et un token de téléchargement est retourné. |
| FILE-UNIT-02 | Consultation des fichiers d'un utilisateur | Retourne la liste des fichiers associés à l'utilisateur sous forme de DTO. |
| FILE-UNIT-03 | Suppression d'un fichier appartenant à l'utilisateur | Les métadonnées sont supprimées de PostgreSQL et le contenu associé est supprimé de MinIO. |
| FILE-UNIT-04 | Téléchargement d'un fichier sans protection par mot de passe | Retourne la ressource associée au token de téléchargement valide. |
| FILE-UNIT-05 | Téléchargement d'un fichier protégé avec un mot de passe valide | Vérifie le mot de passe et retourne la ressource associée au fichier. |
| FILE-UNIT-06 | Suppression automatique des fichiers expirés | Les métadonnées et les contenus des fichiers expirés sont supprimés de PostgreSQL et MinIO. |

#### 4 ExpiredFileCleanupCronServiceTest

Objectif :  Vérifier que le cron cleanup délègue la suppression des fichiers expirés au FileService.

| ID | Cas de test | Comportement attendu |
|---|---|---|
| CRON-UNIT-01 | Exécution du nettoyage automatique des fichiers expirés | La méthode `deleteExpiredFiles()` de `FileService` est appelée. |

### IT1 – Étape 2 : Tests d'intégration

#### 1. UserAccountController

Objectif : Vérifier le fonctionnement de l'inscription et de l'authentification des utilisateurs à travers l'API REST, en validant les échanges entre le contrôleur, les services et PostgreSQL, ainsi que la génération et la validation des JWT.

| ID | Cas de test | Comportement attendu |
|---|---|---|
| AUTH-INT-01 | Inscription d'un utilisateur | Le compte est enregistré dans PostgreSQL avec un mot de passe encodé. |
| AUTH-INT-02 | Connexion avec des identifiants valides | Retourne un JWT valide pour l'utilisateur. |
| AUTH-INT-03 | Connexion avec un mot de passe incorrect | Retourne un statut HTTP 401. |

#### 2. FileController — Tests d'intégration

Objectif : Vérifier le fonctionnement des opérations de téléversement, de consultation, de téléchargement et de suppression des fichiers à travers l'API REST, en validant leur intégration avec les services métier, PostgreSQL et MinIO, ainsi que les mécanismes d'authentification et de validation des fichiers.

| ID | Cas de test | Comportement attendu |
|---|---|---|
| FILE-INT-01 | Téléversement authentifié d'un fichier | Les métadonnées sont enregistrées dans PostgreSQL et un token de téléchargement est retourné. |
| FILE-INT-02 | Téléversement anonyme d'un fichier | Le fichier est enregistré sans utilisateur associé et un token de téléchargement est retourné. |
| FILE-INT-03 | Consultation des fichiers d'un utilisateur authentifié | Retourne les fichiers associés à l'utilisateur. |
| FILE-INT-04 | Suppression d'un fichier par son propriétaire | Les métadonnées et le contenu associé sont supprimés. |
| FILE-INT-05 | Téléchargement d'un fichier avec un token valide | Retourne le contenu du fichier avec son nom dans l'en-tête `Content-Disposition`. |
| FILE-INT-06 | Téléversement d'un fichier avec une extension interdite | Retourne un statut HTTP 400. |

#### 3. DataShareBackendApplicationTests

Objectif : Vérifier que le JWT filtre les route et accepte un token utilisateur valide

| ID | Cas de test | Comportement attendu |
|---|---|---|
| FILTER-INT-04 | Accès à une route protégée sans token JWT | Retourne un statut HTTP 401 Unauthorized. |
| FILTER-INT-05 | Accès à une route protégée avec un token JWT valide | Le filtre JWT authentifie l'utilisateur et laisse passer la requête. |
| FILTER-INT-06 | Accès à une route protégée avec un token JWT invalide | Retourne un statut HTTP 401 Unauthorized. |

## FRONT-END

### Test cases

#### 1. User service

| ID | Cas de test | Comportement attendu |
|---|---|---|
| US-01 | Inscription d'un utilisateur | Une requête POST est envoyée vers `/api/auth/register` avec les informations de l'utilisateur. |
| US-02 | Connexion d'un utilisateur | Une requête POST est envoyée vers `/api/auth/login` avec les identifiants et le token retourné est récupéré. |
| US-03 | Enregistrement du token après connexion | Le token JWT est enregistré dans le `localStorage`. |

#### 2 File Service

| ID | Cas de test | Comportement attendu |
|---|---|---|
| FS-01 | Téléversement d'un fichier sans authentification | Une requête POST est envoyée vers `api/files` avec le `FormData`. |
| FS-02 | Téléversement d'un fichier avec authentification | Une requête POST est envoyée vers `api/auth/files` avec le `FormData`. |
| FS-03 | Téléchargement d'un fichier | Une requête POST est envoyée vers `api/files/download` et retourne un objet `Blob`. |

#### 3. HomeComponent

| ID | Cas de test | Comportement attendu |
|---|---|---|
| HC-01 | Sélection d'un fichier valide | Le fichier est enregistré dans `FileService.currentFileToUpload` et l'utilisateur est redirigé vers `/upload`. |
| HC-02 | Sélection d'un fichier trop volumineux | Un message d'erreur est affiché et aucune navigation n'est effectuée. |
| HC-03 | Sélection d'un fichier vide | Un message d'erreur est affiché et aucune navigation n'est effectuée. |
| HC-04 | Sélection d'un fichier avec une extension interdite | Un message d'erreur est affiché et aucune navigation n'est effectuée. |

#### 4 UploadComponent

| ID | Cas de test | Comportement attendu |
|---|---|---|
| UC-01 | Initialisation du composant | Le fichier sélectionné dans `FileService` est récupéré et ajouté à `selectedFiles`. |
| UC-02 | Initialisation du formulaire | Le formulaire est initialisé avec les valeurs par défaut et respecte les contraintes de validation. |
| UC-03 | Téléversement sans authentification | `FileService.uploadFile()` est appelé avec un token `null` et les données du fichier et des métadonnées. |
| UC-04 | Téléversement avec authentification | `FileService.uploadFile()` est appelé avec le token utilisateur et les données du fichier et des métadonnées. |
| UC-05 | Réception du résultat du téléversement | Le token de téléchargement est récupéré et `currentStep` passe à `UPLOAD_SUCCESS`. |
| UC-06 | Partage d'un autre fichier | L'utilidateur est redirigé vers la page d'acceuil. |
| UC-07 | Ajouter un tag | Le tag est ajouté dans la liste des tags. |

#### 5 FileDownloadComponent

| ID | Cas de test | Comportement attendu |
|---|---|---|
| FD-01 | Initialisation avec un token valide | Les informations du fichier sont récupérées et le formulaire est initialisé. |
| FD-02 | Initialisation avec un lien expiré | Le composant détecte l'expiration du lien. |
| FD-03 | Téléchargement sans mot de passe | Le service est appelé avec le token et un mot de passe à `null`. |
| FD-04 | Téléchargement avec mot de passe | Le service est appelé avec le token et le mot de passe renseigné. |
| FD-05 | Réception du fichier | Le téléchargement du Blob est déclenché avec le nom du fichier. |

#### 6. LoginComponent

| ID | Cas de test | Comportement attendu |
|---|---|---|
| LC-01 | Connexion avec des champs obligatoires vides | Un message d'erreur est affiché et le service n'est pas appelé. |
| LC-02 | Connexion réussie | Le token est enregistré et l'utilisateur est redirigé vers l'accueil. |

#### 7 Interceptor

| ID | Cas de test | Comportement attendu |
|---|---|---|
| AI-01 | Requête vers une API protégée avec token | Le header `Authorization: Bearer <token>` est ajouté. |
| AI-02 | Requête vers une API protégée sans token | La requête est transmise sans header Authorization. |
| AI-03 | Connexion ou inscription | Aucun token n'est ajouté aux requêtes publiques. |
| AI-04 | Requête vers une URL externe | Aucun token n'est ajouté. |

#### 8 .Layout component

| ID | Cas de test | Comportement attendu |
|---|---|---|
| LAC-01 | Initialisation du composant | Le composant est créé correctement. |
| LAC-02 | Affichage pour un utilisateur non connecté | Le bouton « Se connecter » est affiché. |
| LAC-03 | Affichage pour un utilisateur connecté | Le bouton « Mon espace » est affiché. |
| LAC-04 | Déconnexion | La session est effacée et l'utilisateur est redirigé vers l'accueil. |

#### 9 Guards

| ID | Cas de test | Comportement attendu |
|---|---|---|
| AG-01 | Utilisateur authentifié | L'accès est autorisé (`true`). |
| AG-02 | Utilisateur non authentifié | Redirection vers `/login`. |

#### 10 My-spaceComponent

| ID | Cas de test | Comportement attendu |
|---|---|---|
| MS-01 | Création du composant | Le composant est correctement créé. |
| MS-02 | Chargement des fichiers de l'utilisateur | Les fichiers sont récupérés depuis `FileService` et affichés selon le filtre actif. |
| MS-03 | Suppression d'un fichier | Après confirmation, le service est appelé et le fichier est retiré de la liste. |

#### rRegistreComponent

| ID | Cas de test | Comportement attendu |
|---|---|---|
| REG-01 | Création du composant | Le composant est correctement créé. |
| REG-02 | Affichage du formulaire d'inscription | Les champs et le lien de connexion sont affichés. |
| REG-03 | Validation des champs obligatoires | Un message d'erreur est affiché si un champ est manquant. |
| REG-04 | Vérification de la confirmation du mot de passe | Un message d'erreur est affiché si les mots de passe sont différents. |
| REG-05 | Inscription réussie | Le compte est créé et l'utilisateur est redirigé vers `/login`. |



### E2E Cypress tests

#### Parcours A — Téléversement anonyme et Téléchargement

| ID | Cas de test | Comportement attendu |
|---|---|---|
| E2E-01 | Accès à l'accueil | La page principale s'affiche. |
| E2E-02 | Upload anonyme | Le fichier est envoyé et le téléversement aboutit. |
| E2E-03 | Download anonyme | Le fichier est téléchargé, le contenu téléchargé est vérifié et retour sur la page d'accueil.|
| E2E-04 | Upload anonyme protégé | Le fichier est envoyé avec un mot de passe. |
| E2E-05 | Download anonyme protégé | Le mot de passe est demandé, le contenu téléchargé est vérifié et retour sur la page d'accueil. |


#### Parcours B — Téléversement avec authentification et Téléchargement

| ID | Parcours | Comportement attendu |
|---|---|---|
| E2E-01 | Accès à l'accueil, Connexion ou inscription | Si l'utisateur n'existe pas, pasge d'enregistrement(le compte est créé une seule fois) sinon page de connexion. Vérification de la présence du bouton "Mon espace"|
| E2E-02 | Se connecter et téléverser un fichier | Un vrai fichier est envoyé à `/api/auth/files` sans mot de passe. |
| E2E-03 | Téléchargment du fichier | Le contenu téléchargé est vérifié. |
| E2E-04 | Se connecter et téléverser un fichier protégé | Un vrai fichier est envoyé à `/api/auth/files` avec un mot de passe. |
| E2E-05 | Télécharger le fichier protégé | Le mot de passe est demandé et le contenu téléchargé est vérifié. |