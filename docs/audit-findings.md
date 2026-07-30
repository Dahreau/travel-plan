# Audit findings — état du backend/infra au 2026-07-29

[← Sommaire](00-getting-started.md)

Revue complète du code contre la grille d'audit officielle (`travel-plan_audit.md`). Périmètre : backend, infra, CI/CD, sécurité, tests, git. **Le frontend (Admin Dashboard) est hors périmètre** — géré par un autre contributeur.

Légende : ✅ solide, prêt pour l'audit — ⚠️ vrai écart, à corriger ou à savoir justifier à l'oral.

## À corriger avant l'audit (par priorité)

### ⚠️ 1. Trafic interne non chiffré

L'audit demande explicitement "SSL/TLS pour **toutes** les données en transit". Aujourd'hui, seul le hop client→nginx est chiffré. Tout le reste tourne en clair à l'intérieur du réseau Docker :

- `nginx → api-gateway` : `http://` (`infra/nginx/nginx.conf:17`)
- `api-gateway → auth/user/travel/payment-service` : `http://` (`docker-compose.yml:222-225`)
- chaque service `→ Postgres` : JDBC sans `ssl=true`
- `travel-service → Neo4j` : `bolt://` (pas `bolt+s://`)
- chaque service `→ Vault` : `http://vault:8200`
- chaque service `→ Zipkin` : `http://zipkin:9411`

**Justification actuelle possible à l'oral** : isolation réseau Docker (bridge dédié, aucun de ces flux ne sort du host). **Mais** ça ne satisfait pas littéralement la formulation de l'énoncé — à trancher : soit l'assumer et le justifier clairement, soit chiffrer au moins Postgres/Neo4j (`sslmode=require` / `bolt+s://`) si le temps le permet.

### ⚠️ 2. Bases de données et infra exposées sur l'host

`docker-compose.yml` publie directement sur l'host : Postgres (`5432`), Neo4j (`7474`/`7687`), **Vault (`8200`, en mode dev donc déverrouillé avec un root token)**, Zipkin (`9411`). L'énoncé exige que "les bases et services soient accessibles uniquement depuis le réseau interne ou via un endpoint sécurisé et authentifié". Ce n'est pas le cas — n'importe quel process sur la machine hôte peut s'y connecter directement, en contournant complètement nginx/api-gateway.

**Fix simple** : retirer les `ports:` de `postgres`, `neo4j`, `vault`, `zipkin` dans `docker-compose.yml` (garder les mappings uniquement dans un fichier d'override séparé pour le debug local, jamais dans la version "prod"). C'est le correctif le plus rapide à faire de toute cette liste.

### ⚠️ 3. `auth-service` : une route en `.authenticated()` au lieu de `.hasRole("ADMIN")`

`auth-service/.../SecurityConfig.java:32` autorise tout utilisateur authentifié, alors que `user-service`, `travel-service`, `payment-service` exigent tous les trois `.hasRole("ADMIN")`. Fonctionnellement équivalent aujourd'hui (le `Role` enum d'auth-service n'a qu'une seule valeur, `ADMIN`), mais littéralement, `GET /api/auth/me` n'est pas explicitement admin-only — l'audit pose la question précise "est-ce que TOUTES les APIs sont admin-only ?". À aligner (`hasRole("ADMIN")` partout) pour ne pas avoir à improviser une explication le jour J.

### ⚠️ 4. Vault : `secret_id` jamais révoqué

`fetch-vault-secrets.yml` génère un nouveau `secret_id` à chaque exécution du playbook (comportement voulu, déjà documenté), mais ne révoque jamais l'ancien — les vieux identifiants restent valides jusqu'à expiration de leur TTL (4h max). Pas bloquant, mais un `vault write -f auth/approle/role/<svc>/secret-id-accessor/destroy` sur l'ancien avant d'en émettre un nouveau serait plus propre et facile à justifier comme "moindre privilège dans le temps".

### ⚠️ 5. Nœuds Neo4j jamais supprimés (seulement les relations)

Supprimer un `Travel` supprime bien la relation `ROUTE_TO` associée (`TravelGraphSyncService`, testé), mais jamais le `PlaceNode` (ville) lui-même — les villes s'accumulent indéfiniment dans le graphe. **C'est probablement le bon comportement** (une ville reste une référence partagée entre plusieurs voyages, la supprimer casserait les voyages des autres utilisateurs qui la référencent) — mais comme l'énoncé insiste spécifiquement sur "cascade delete entre PostgreSQL et Neo4j", prépare cette justification à l'oral plutôt que de laisser penser que c'est un oubli.

### ⚠️ 6. Trois trous de tests unitaires

- `api-gateway/.../security/JwtService.java` — aucun test dédié.
- `payment-service/.../repository/PaymentMethodRepository.java` — aucun test dédié.
- `travel-service/.../graph/PlaceRepository.java` — sa requête Cypher personnalisée (`suggestNextDestinations`) n'est jamais exécutée contre un vrai Neo4j, seulement mockée.

Rapide à combler, et l'audit demande explicitement un test par fonctionnalité.

### ⚠️ 7. `nginx` et `zipkin` sans `healthcheck`

Tous les autres services en ont un ; ces deux-là non. Facile à ajouter (`wget`/`curl` interne suffit).

## Ce qui est déjà solide (rien à changer)

- **Indépendance des microservices** : une DB Postgres + un user dédié par service, aucun accès croisé, aucune lib partagée.
- **API Gateway** : route vers les 4 services, valide le JWT au périmètre avant de relayer.
- **Tracing distribué** : les 5 services remontent vers Zipkin.
- **HA** : `replicas: 2` sur les 5 microservices, healthchecks sur l'essentiel, répartition via le DNS round-robin de Docker.
- **Vault** : policy scoped strictement par service (pas de wildcard `secret/*`), TTL courts (1h/4h).
- **Postgres** : chaque user n'a de droits que sur sa propre base, pas de superuser côté appli.
- **Aucun secret en dur** trouvé dans le code source (hors fixtures de test).
- **Cascade Postgres** : `User → Address` en `ON DELETE CASCADE`, `PaymentMethod → Payment` en `ON DELETE SET NULL` — les deux choix sont cohérents et justifiables.
- **Ansible** : quasi tout est idempotent nativement (`apt`, `copy force:false`, `command creates:`) ; seule nuance connue = le `secret_id` Vault (point 4 ci-dessus), déjà documentée.
- **Jenkins** : build + tests des 5 services en parallèle, Quality Gate SonarQube qui fait vraiment échouer le build (`sonar.qualitygate.wait=true`).
- **Docker** : build multi-stage, utilisateur non-root (`spring:spring`) sur les 5 services.
- **Git/PR** : convention `feat/<service>-<sujet>` / `chore/<sujet>` respectée, commits de type conventional commits, un service = une branche = une PR.
- **Bonus doc** : déjà complet et à jour (hors la note ci-dessous).
- **Bonus Kubernetes / E2E** : non commencés — attendu, à mentionner comme "non fait, priorisé après le cœur du sujet" si demandé.

## Petites incohérences de doc à corriger

- `docs/01-ci-cd.md` dit encore "3 services" dans `SERVICES` — le vrai `Jenkinsfile` en a 5.
- Vérifie que le job Multibranch Pipeline existe toujours dans Jenkins après le redémarrage du conteneur de ce soir (son volume `jenkins_home` a survécu, mais autant confirmer visuellement qu'il rescane bien).
