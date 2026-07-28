# Getting started

Guide pour comprendre d'où part le projet et comment le faire tourner au fur
et à mesure que les briques se construisent. Cette page n'invente rien : elle
ne documente que ce qui existe réellement dans le repo au moment où tu la lis.

## Prérequis

- JDK 21
- Maven (fourni via le wrapper `mvnw` dans chaque microservice)
- Docker Desktop (+ Docker Compose)
- Node.js (version exacte à préciser une fois le projet Angular généré)

## Ce qui tourne réellement aujourd'hui

```bash
./scripts/start-ci.sh    # Jenkins + SonarQube — détail : 01-ci-cd.md
./scripts/start-app.sh   # Postgres, Neo4j, Vault, Zipkin — détail : 02-app-infra.md
./scripts/start-all.sh   # les deux d'un coup
```

Chaque script crée son `.env` depuis `.env.example` s'il n'existe pas encore
(et s'arrête là, le temps que tu mettes de vrais mots de passe), sinon lance
directement `docker compose up -d --build`. Rejouable sans risque.

`api-gateway`, `auth-service`, `user-service` et `travel-service` sont
maintenant branchés sur ces briques (Postgres/Neo4j, Vault, Zipkin) — voir
`02-app-infra.md`. `payment-service` reste à construire.

## Sommaire de la doc (au fur et à mesure des branches)

- [`00-getting-started.md`](00-getting-started.md) — cette page.
- [`01-ci-cd.md`](01-ci-cd.md) — Jenkins, SonarQube, pipeline (branche `chore/setup-jenkins`).
- [`02-app-infra.md`](02-app-infra.md) — Postgres, Neo4j, Vault, Zipkin (branche `chore/setup-app-infra`).
- [`03-auth-service.md`](03-auth-service.md) — JWT, RBAC, secret Vault partagé (branche `feat/auth-service-jwt`).
- [`04-user-service.md`](04-user-service.md) — CRUD utilisateurs, cascade `User`/`Address` (branche `feat/user-service-crud`).
- [`05-api-gateway.md`](05-api-gateway.md) — routage, JWT au périmètre, load balancing (branche `feat/api-gateway-routing`).
- [`06-travel-service.md`](06-travel-service.md) — voyages/destinations, cascade Postgres + Neo4j (branche `feat/travel-service-crud`).
- [`nouveautes-vs-buy02.md`](nouveautes-vs-buy02.md) — tout ce qui est vraiment nouveau par rapport à buy-02, mis à jour à chaque branche.

Chaque nouvelle page prend le numéro suivant au moment où sa branche est
construite — pas de trous ni de numéros réservés à l'avance, pour éviter le
"pourquoi ça saute à 4" alors que 1-2-3 n'existent pas encore.

## Comment les 5 microservices ont été créés (Spring Initializr)

Les 5 coquilles (`api-gateway`, `auth-service`, `user-service`, `travel-service`,
`payment-service`) ont été générées via [start.spring.io](https://start.spring.io),
pas écrites à la main.

**Paramètres communs** : Maven, Java 21, packaging Jar, Group `com.travel-plan`
(le tiret est gardé tel quel dans le `groupId` Maven ; Spring Initializr le
convertit en underscore uniquement dans les packages Java générés, d'où
`com.travel_plan.api_gateway` — conversion normale, pas une erreur), Spring
Boot **4.1.0** (dernière stable au moment de la génération).

| Service | Rôle | Dépendances Spring Initializr |
|---|---|---|
| `api-gateway` | Route le trafic, ne contient aucune logique métier | Gateway, Lombok |
| `auth-service` | Génère et vérifie les JWT, gère les rôles admin | Spring Web, Spring Security, Spring Data JPA, PostgreSQL Driver, Lombok |
| `user-service` | CRUD des comptes (admins et voyageurs) | Spring Web, Spring Data JPA, PostgreSQL Driver, Lombok |
| `travel-service` | Destinations, dates, activités | Spring Web, Spring Data Neo4j, Lombok |
| `payment-service` | Intégration Stripe / PayPal | Spring Web, Spring Data JPA, PostgreSQL Driver, Lombok |

**Écart déjà validé par rapport à la recette d'origine** : `travel-service`
recevra en plus Spring Data JPA + PostgreSQL Driver (persistance polyglotte
Postgres + Neo4j), pas juste Neo4j seul — l'audit demande de gérer les
suppressions/mises à jour en cascade *entre* PostgreSQL et Neo4j, ce qui
suppose que `travel-service` écrive dans les deux (cœur du voyage en Postgres,
destinations/relations en graphe Neo4j). Détaillé dans sa propre page le
moment venu.

**HashiCorp Vault** n'a volontairement pas été ajouté dès la génération : le
service refuserait de démarrer tant qu'un serveur Vault ne tourne pas — il
sera branché une fois Vault lui-même construit.
