# Nouveautés par rapport à buy-02

[← Sommaire](00-getting-started.md)

Ce fichier réunit, branche après branche, tout ce qui est vraiment nouveau par rapport à buy-02 — pour réviser vite avant l'audit sans rouvrir chaque page de doc.

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
