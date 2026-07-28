#!/bin/sh
set -e

SERVICES="api-gateway auth-service user-service travel-service payment-service"

if ! vault auth list -format=json | grep -q '"approle/"'; then
	vault auth enable approle
fi

vault policy write "shared-policy" "/vault-init/policies/shared-policy.hcl"

for svc in $SERVICES; do
	vault policy write "${svc}-policy" "/vault-init/policies/${svc}-policy.hcl"

	vault write "auth/approle/role/${svc}" \
		token_policies="${svc}-policy,shared-policy" \
		token_ttl=1h \
		token_max_ttl=4h

	echo "--- ${svc} ---"
	echo "role_id:"
	vault read -field=role_id "auth/approle/role/${svc}/role-id"
done

if ! vault kv get secret/shared/jwt >/dev/null 2>&1; then
	jwt_secret=$(vault write -field=random_bytes sys/tools/random/32 format=base64)
	vault kv put secret/shared/jwt secret="$jwt_secret"
	echo "Secret JWT partage cree dans secret/shared/jwt"
fi

echo "Vault bootstrap done: AppRole enabled, one policy + one role per service, shared JWT secret seeded."
