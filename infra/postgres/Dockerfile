FROM postgres:18-alpine

# Certificat auto-signe genere au build (pas de bind-mount depuis l'host) : Postgres refuse
# de demarrer si la cle privee a des permissions trop ouvertes, ce que les bind-mounts
# Windows/WSL2 ne garantissent jamais correctement. Le generer directement dans l'image
# et chown/chmod ici evite ce piege completement.
RUN apk add --no-cache openssl \
    && mkdir -p /etc/postgresql/certs \
    && openssl req -x509 -newkey rsa:2048 -nodes \
       -keyout /etc/postgresql/certs/server.key \
       -out /etc/postgresql/certs/server.crt \
       -days 365 -subj /CN=postgres.local \
    && chown postgres:postgres /etc/postgresql/certs/server.key /etc/postgresql/certs/server.crt \
    && chmod 600 /etc/postgresql/certs/server.key \
    && chmod 644 /etc/postgresql/certs/server.crt
