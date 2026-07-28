#!/bin/sh
set -e

SERVICES="api-gateway auth-service user-service travel-service payment-service"

if ! vault auth list -format=json | grep -q '"approle/"'; then
	vault auth enable approle
fi

for svc in $SERVICES; do
	vault policy write "${svc}-policy" "/vault-init/policies/${svc}-policy.hcl"

	vault write "auth/approle/role/${svc}" \
		token_policies="${svc}-policy" \
		token_ttl=1h \
		token_max_ttl=4h

	echo "--- ${svc} ---"
	echo "role_id:"
	vault read -field=role_id "auth/approle/role/${svc}/role-id"
done

echo "Vault bootstrap done: AppRole enabled, one policy + one role per service."
