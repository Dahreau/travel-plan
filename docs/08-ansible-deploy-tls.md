# Ansible — déploiement automatisé, replicas, TLS via Nginx

[← Sommaire](00-getting-started.md)

## Lancer en local

Prérequis : Python 3 et Ansible installés sur la machine qui exécute les playbooks (`pip install ansible --break-system-packages` ou `apt install ansible`), et la collection Docker :

```bash
cd ansible
ansible-galaxy collection install -r requirements.yml
```

Copie `.env.example` en `.env` si ce n'est pas déjà fait (le playbook le fait aussi automatiquement au premier lancement), puis mets de vrais mots de passe dedans comme d'habitude.

Lance tout d'un coup :

```bash
cd ansible
ansible-playbook -i inventory.ini playbooks/site.yml --ask-become-pass
```

- `-i inventory.ini` : dit explicitement à Ansible où trouver l'inventaire, plutôt que de compter sur la lecture automatique d'`ansible.cfg` (qu'Ansible refuse par sécurité si le dossier semble "accessible en écriture à tout le monde" — ça arrive sur un disque Windows monté dans WSL, sans rapport avec le projet lui-même).
- `--ask-become-pass` : `install-docker.yml` a besoin des droits root pour installer des paquets — tape ton mot de passe sudo une fois quand demandé.

Ça fait, dans l'ordre : installer Docker si absent, démarrer toute la stack, récupérer les identifiants Vault de chaque service, puis redémarrer les microservices avec ces identifiants. Rejouable sans risque (voir "Pourquoi" plus bas).

Une fois terminé, l'application est accessible en HTTPS :

```bash
curl -k https://localhost/api/travels
```

(`-k` parce que le certificat est auto-signé — normal en local, pas un vrai certificat signé par une autorité publique.)

## Ce qui est construit

| Fichier / dossier                           | Rôle                                                                                |
| ------------------------------------------- | ----------------------------------------------------------------------------------- |
| `backend/*/Dockerfile`                      | build multi-stage (Maven → JRE), utilisateur non-root, un par microservice          |
| `docker-compose.yml`                        | étendu avec les 5 microservices (`deploy.replicas: 2` chacun) + `nginx`             |
| `infra/nginx/nginx.conf`                    | redirige HTTP→HTTPS, termine le TLS, relaie vers `api-gateway`                      |
| `ansible/playbooks/install-docker.yml`      | installe Docker + le plugin Compose si absent                                       |
| `ansible/playbooks/deploy.yml`              | crée `.env`, génère le certificat TLS, pré-tire les images tierces, lance `docker compose up` |
| `ansible/playbooks/fetch-vault-secrets.yml` | récupère/génère les identifiants AppRole de chaque service et les écrit dans `.env` |
| `ansible/playbooks/site.yml`                | enchaîne les trois dans le bon ordre                                                |
| `infra/postgres/init/init-databases.sh`     | crée + resynchronise (idempotent) chaque user/DB Postgres au démarrage du conteneur |
| `infra/vault/bootstrap.sh`                  | active AppRole, seede le secret JWT partagé + les identifiants Stripe/PayPal        |

## Nouveau par rapport à buy-02

Voir [`nouveautes-vs-buy02.md`](nouveautes-vs-buy02.md#ansible--tls-choreansible-deploy-tls) (déploiement automatisé, replicas, TLS).

## Pourquoi ces choix

### Pourquoi `deploy.yml` tourne deux fois dans `site.yml`

Chaque microservice a besoin d'un identifiant Vault (`VAULT_ROLE_ID`/`VAULT_SECRET_ID`) pour démarrer, mais Vault doit d'abord tourner et être "seedé" par `vault-init` pour que ces identifiants existent. Premier passage : la stack démarre, les microservices échouent à démarrer faute d'identifiants (ils redémarrent en boucle, sans casser le reste). `fetch-vault-secrets.yml` récupère alors les vrais identifiants et les écrit dans `.env`. Second passage de `deploy.yml` : Docker Compose ne recrée que les conteneurs dont la config a changé (les 5 microservices), qui démarrent cette fois avec de vrais identifiants. Pas de `sleep` ni de script fragile — juste une dépendance résolue en deux étapes explicites.

### Pourquoi les tâches Ansible utilisées sont idempotentes, sauf une nuance à connaître

`apt`, `copy` (avec `force: false`), `command` (avec `creates: ...`) et `community.docker.docker_compose_v2` sont tous conçus pour ne rien casser si on les relance : ils vérifient l'état actuel avant d'agir. Seule nuance, à savoir expliquer à l'audit : `fetch-vault-secrets.yml` génère un **nouveau** `secret_id` à chaque exécution (Vault n'en renvoie jamais le même deux fois) — donc la valeur change à chaque relance, mais le résultat final (un identifiant valide dans `.env`, le service peut démarrer) est toujours le même. C'est idempotent dans son effet, pas dans sa valeur exacte — comme changer un mot de passe.

### Pourquoi chaque microservice a `deploy.replicas: 2` sans changer une ligne de code Java

Docker Compose résout le nom d'un service (`http://auth-service:8081`) vers l'IP d'une instance différente à chaque requête quand plusieurs répliques tournent (DNS round-robin intégré à Docker). `api-gateway` pointe déjà vers ce nom logique via `AUTH_SERVICE_URI` (mécanisme déjà en place depuis `feat/api-gateway-routing`) — la répartition de charge et le failover entre répliques se font donc au niveau réseau Docker, sans toucher à `RouteConfig.java`.

### Pourquoi Nginx ne fait que le TLS, pas le routage entre microservices

Le routage (JWT, quel service pour quelle route) est déjà géré par `api-gateway`. Ajouter cette logique dans Nginx la dupliquerait à deux endroits différents. Nginx a un rôle unique et simple : être le seul point exposé sur Internet, chiffrer le trafic entrant, et relayer tout vers `api-gateway` qui reste responsable du routage métier.

### Pourquoi `deploy.yml` appelle `docker compose` directement plutôt que le module Ansible dédié

Le module `community.docker.docker_compose_v2` a un bug reproductible sur certaines installations Docker Desktop : son étape interne de vérification post-déploiement (`docker compose images`) plante sur une image multi-plateforme même quand l'image locale est correcte (testé avec tag simple, `--platform` explicite et digest exact — les trois échouent identiquement). `docker compose up -d --remove-orphans` en ligne de commande directe, lui, n'a jamais échoué : c'est exactement la même commande que lancerait un humain, juste sans l'étape de rapport buguée en plus. Le pré-pull explicite par digest (`hashicorp/vault:2.0@sha256:...`) juste avant sert à garantir que l'image correcte est déjà en cache avant que Compose n'y touche.

### Pourquoi le premier passage de `deploy.yml` ne fait pas échouer tout le playbook

`docker compose up` refuse de démarrer `api-gateway` tant qu'un service dont il dépend (`auth-service`, etc.) n'est pas `healthy` — normal au premier passage, ces services n'ont pas encore d'identifiants Vault valides. La variable `tolerate_unhealthy_dependencies` (mise à `true` uniquement pour le premier appel de `deploy.yml` dans `site.yml`) rend cet échec-là silencieux ; le second passage, lui, fait toujours échouer le playbook pour de vrai si quelque chose cloche, pour ne jamais masquer un vrai problème.

### Pourquoi un certificat auto-signé plutôt que Let's Encrypt

Let's Encrypt exige un nom de domaine public résolvable, ce que ce projet n'a pas en local/dev. Un certificat auto-signé chiffre le trafic exactement pareil (même algorithme TLS) — la seule différence est qu'aucune autorité tierce ne garantit l'identité du serveur, ce qui n'a pas d'importance en développement. Passer à Let's Encrypt le jour d'un vrai déploiement ne changerait que la génération du certificat, pas la configuration Nginx.
