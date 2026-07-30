FROM neo4j:5.26

# Meme raisonnement que infra/postgres/Dockerfile : la cle privee doit appartenir
# a l'utilisateur neo4j avec des permissions restrictives (600), ce qu'un bind-mount
# depuis un host Windows/WSL2 ne garantit jamais. On genere donc le certificat
# auto-signe directement dans l'image, avec le bon chown/chmod.
RUN apt-get update \
    && apt-get install -y --no-install-recommends openssl \
    && rm -rf /var/lib/apt/lists/* \
    && mkdir -p /var/lib/neo4j/certificates/bolt/trusted /var/lib/neo4j/certificates/bolt/revoked \
    && openssl req -x509 -newkey rsa:2048 -nodes \
       -keyout /var/lib/neo4j/certificates/bolt/private.key \
       -out /var/lib/neo4j/certificates/bolt/public.crt \
       -days 365 -subj /CN=neo4j.local \
    && chown -R neo4j:neo4j /var/lib/neo4j/certificates \
    && chmod 600 /var/lib/neo4j/certificates/bolt/private.key \
    && chmod 644 /var/lib/neo4j/certificates/bolt/public.crt
