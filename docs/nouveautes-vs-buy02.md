# Nouveautés par rapport à buy-02

[← Sommaire](00-getting-started.md)

Ce fichier réunit, branche après branche, tout ce qui est vraiment nouveau par rapport à buy-02 — pour réviser vite avant l'audit sans rouvrir chaque page de doc.

## CI/CD (`chore/setup-jenkins`)

buy-02 avait déjà Jenkins + SonarQube (l'énoncé buy-02 l'imposait) — donc pas nouveau en soi. Ce qui change ici :

| Notion | buy-02 | Ici |
|---|---|---|
| Nombre de jobs/pipelines | une seule app, un seul job Jenkins | **5 microservices indépendants**, chacun buildé/scanné séparément dans le même pipeline |
| Exécution des builds | séquentielle (une seule app à la fois) | **en parallèle** (`parallel {}`) — builder plusieurs services ne coûte pas plus de temps qu'un seul |
| Quality Gate | probablement un seul projet SonarQube à vérifier | **un Quality Gate par service** (`travel-plan-auth-service`, `travel-plan-user-service`, ...), chacun avec son propre statut pass/fail |
| Blocage sur le Quality Gate | probablement le step Jenkins `waitForQualityGate` (dépend d'un webhook SonarQube → Jenkins) | **le scanner Maven lui-même** (`-Dsonar.qualitygate.wait=true`) sonde l'API SonarQube en boucle — pas de dépendance à un webhook, et pas de risque de confondre le rapport de deux services scannés en parallèle |

**Pourquoi le blocage a changé de mécanisme** (le point le plus subtil) : le step Jenkins `waitForQualityGate` cherche un fichier `report-task.txt` dans tout le workspace — avec 5 services scannés en parallèle, il en trouve plusieurs et n'en garde arbitrairement qu'un seul, donc les 4 autres services ne sont jamais vraiment vérifiés malgré un statut Jenkins vert. Faire porter le blocage par le process Maven de chaque service (qui reçoit son propre ID de tâche directement dans sa réponse HTTP, sans jamais lire de fichier partagé) élimine ce risque de confusion entre services.

## Infra applicative (`chore/setup-app-infra`)

Secret manager et traçage distribué sont réellement nouveaux. L'isolation des bases, elle, ne l'est **pas** — buy-02 avait déjà des bases MongoDB isolées par service (corrigé après une première version de cette page qui supposait le contraire à tort) :

| Notion | buy-02 | Ici |
|---|---|---|
| Bases de données | MongoDB, isolées par service | **Postgres** (une instance, une base par service) + **Neo4j** (pour `travel-service`) — changement de techno (relationnel + graphe plutôt que documents), pas d'isolation en plus |
| Isolation entre services | déjà le cas (bases Mongo séparées) | même principe, transposé à Postgres : un user dédié par service, qui ne peut pas lire les bases des autres |
| Secrets (mots de passe, tokens) | en dur / variables d'env simples | **HashiCorp Vault** (mode dev), chaque service authentifié via sa propre identité AppRole |
| Traçage d'une requête | pas de notion équivalente | **Zipkin** — chaque service instrumenté envoie ses traces, consultables dans une UI dédiée |

**Vault en mode dev, pas en mode prod** (le point le plus nouveau) : un vrai déploiement Vault demande du stockage persistant et un déverrouillage (unseal) manuel — inutile tant qu'on développe en local. Le mode dev donne les mêmes mécanismes d'authentification/policies qu'un vrai Vault (AppRole, policies par service), seuls le stockage (mémoire, tout est perdu au redémarrage) et l'unseal (automatique) diffèrent. À retenir pour l'audit : ce n'est pas un raccourci de sécurité, c'est un choix explicite pour l'environnement de dev — le durcissement (stockage persistant, unseal manuel/Shamir) est prévu pour l'étape déploiement.

## auth-service (`feat/auth-service-jwt`)

| Notion | buy-02 | Ici |
|---|---|---|
| Secrets (mots de passe, clé de signature) | en dur / variables d'env simples | récupérés dynamiquement depuis **HashiCorp Vault** au démarrage, jamais commités |
| Authentification service-à-service | — (pas de notion équivalente) | **Vault AppRole** : chaque microservice a sa propre identité (`role_id` + `secret_id`), pas un secret partagé entre tous |
| Où vit l'authentification | probablement intégrée au service principal | service **dédié et indépendant** (`auth-service`) : son seul travail est d'émettre/valider des JWT pour tous les autres |
| Schéma de base | génération automatique (`ddl-auto`) probable | **Flyway** : chaque changement de schéma est un fichier SQL versionné (`V1__...sql`), rejouable, traçable |
| Vérification des tokens | un seul service qui fait tout | **stateless** : n'importe quel service peut vérifier un JWT tout seul avec le secret Vault, sans appeler `auth-service` à chaque requête |

**AppRole en deux mots** (concept le plus nouveau) : c'est la façon dont Vault authentifie un *service*, pas un humain. Deux valeurs : `role_id` (fixe, identifie "quel service" — pas secret) et `secret_id` (le vrai secret, à garder confidentiel). Le service envoie les deux à Vault, reçoit un token temporaire en retour, et l'utilise pour lire *uniquement* ses propres secrets — jamais ceux des autres services (imposé par la policy `auth-service-policy`). À retenir pour l'audit : l'équivalent d'un compte de service à accès limité, pas un admin qui a accès à tout.

## user-service (`feat/user-service-crud`)

| Notion | buy-02 | Ici |
|---|---|---|
| Suppression en cascade | probablement gérée à la main (plusieurs requêtes) ou pas du tout | **cascade réelle à deux niveaux** : `ON DELETE CASCADE` en base (migration Flyway) + `cascade = CascadeType.ALL, orphanRemoval = true` côté JPA sur `User.address` |
| Vérification des tokens | un seul service qui fait tout | `user-service` ne fait que **valider** les JWT émis par `auth-service` (`JwtService` sans `generateToken`) — chaque microservice vérifie lui-même, sans appeler `auth-service` |
| Contrôle d'accès | probablement un seul niveau "connecté" | `hasRole("ADMIN")` explicite sur toutes les routes, pas juste "authentifié" |

**Cascade en deux mots** (concept le plus nouveau) : supprimer un `User` supprime automatiquement son `Address` associée, sans code applicatif dédié ni requête séparée. Démontré par un test (`UserRepositoryTest.deletingUserCascadesToAddress`) qui sauvegarde un `User` + `Address`, supprime le `User`, puis vérifie que l'`Address` a disparu de la base. À retenir pour l'audit : la cascade est vérifiée aux deux niveaux (base ET JPA), pas seulement l'un ou l'autre.

## api-gateway (`feat/api-gateway-routing`)

| Notion | buy-02 | Ici |
|---|---|---|
| Point d'entrée unique | probablement chaque service exposé directement | **API Gateway** (`spring-cloud-starter-gateway-server-webmvc`) : un seul point d'entrée, aucun service backend exposé directement au client |
| Répartition de charge | — (pas de notion équivalente, un seul backend) | **load balancing par nom logique de service** (`lb("auth-service")`), résolu via une liste d'instances configurable — ajouter une replica ne change aucune ligne de code |
| Vérification des tokens | un seul endroit qui vérifie tout | **défense en profondeur** : le gateway rejette tôt (authentification, token valide ou pas), chaque service re-vérifie en plus (autorisation, quel rôle a le droit de faire quoi) |
| Traçage d'une requête à travers plusieurs services | probablement un seul service, pas de notion de trace distribuée | **Zipkin** : chaque service (`auth-service`, `user-service`, `api-gateway`) envoie ses spans, une requête qui traverse gateway → service est visible comme une seule trace |

**Load balancing "sans registry" en deux mots** (concept le plus nouveau) : d'habitude le load balancing suppose un service de découverte (Eureka, Consul) où chaque instance s'enregistre/se désenregistre dynamiquement. Ici, `SimpleDiscoveryClient` (Spring Cloud Commons) donne le même mécanisme de résolution par nom (`lb://auth-service` → une des instances déclarées) à partir d'une simple liste dans `application.properties` — sans registry à faire tourner. À retenir pour l'audit : la haute disponibilité (plusieurs instances, bascule automatique) fonctionne déjà, la seule chose qui manque pour un vrai environnement multi-instances est de lancer réellement plusieurs process par service.

## travel-service (`feat/travel-service-crud`)

| Notion | buy-02 | Ici |
|---|---|---|
| Bases de données | MongoDB seul | **Postgres pour la réservation** (voyage, destinations, activités, hébergement, transport, cascade) **+ Neo4j pour les recommandations** (graphe de destinations enchaînées) — deux systèmes, chacun pour ce qu'il fait le mieux |
| Cascade delete/update | intra-MongoDB uniquement | intra-Postgres (comme `user-service`) **et** orchestrée entre Postgres et Neo4j par le code applicatif — pas de contrainte native cross-DB, donc la cohérence est un choix explicite du service, pas un mécanisme de base |
| Traversée de graphe ("quelles destinations vont souvent ensemble") | pas de notion équivalente | requête Cypher à profondeur variable (`ROUTE_TO*1..2`) — ce qu'un JOIN SQL récursif ferait mal et lentement |

**Pourquoi deux bases pour un seul service** (le point le plus nouveau, et celui que l'audit demande explicitement) : il n'existe pas de transaction ACID unique entre Postgres et Neo4j sans 2PC/saga (hors scope ici). `TravelService` écrit d'abord dans Postgres (source de vérité du voyage), puis appelle `TravelGraphSyncService` pour mettre à jour le graphe Neo4j (nœuds `Place` partagés entre tous les voyages, reliés par `ROUTE_TO` avec un compteur `tripCount`). Supprimer un voyage décrémente ce compteur avant de supprimer les lignes Postgres (cascade native) ; si le compteur tombe à zéro, la relation Neo4j disparaît aussi. À retenir pour l'audit : c'est une cohérence *orchestrée par le code*, explicitement présentée comme telle — pas une prétention à une transaction distribuée qui n'existe pas.
