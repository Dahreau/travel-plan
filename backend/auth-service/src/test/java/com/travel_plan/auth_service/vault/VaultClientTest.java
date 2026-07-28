package com.travel_plan.auth_service.vault;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

class VaultClientTest {

    private static final String VAULT_ADDR = "http://localhost:8200";

    @Test
    void throwsWhenRoleIdMissing() {
        VaultClient client = new VaultClient(VAULT_ADDR, null, "secret", mock(RestClient.class));

        assertThatThrownBy(() -> client.fetchSharedSecret("shared/jwt", "secret"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("VAULT_ROLE_ID");
    }

    @Test
    void throwsWhenSecretIdBlank() {
        VaultClient client = new VaultClient(VAULT_ADDR, "role", "  ", mock(RestClient.class));

        assertThatThrownBy(() -> client.fetchSharedSecret("shared/jwt", "secret"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("VAULT_ROLE_ID");
    }

    @Test
    void fetchesSharedSecretSuccessfully() {
        RestClient restClient = mock(RestClient.class, RETURNS_DEEP_STUBS);
        VaultClient client = new VaultClient(VAULT_ADDR, "role", "secret", restClient);

        VaultLoginResponse loginResponse = new VaultLoginResponse(new VaultLoginResponse.VaultAuth("vault-token"));
        when(restClient.post()
                        .uri(VAULT_ADDR + "/v1/auth/approle/login")
                        .body(eq(Map.of("role_id", "role", "secret_id", "secret")))
                        .retrieve()
                        .body(VaultLoginResponse.class))
                .thenReturn(loginResponse);

        VaultKvResponse kvResponse =
                new VaultKvResponse(new VaultKvResponse.VaultKvData(Map.of("secret", "c2VjcmV0")));
        when(restClient.get()
                        .uri(VAULT_ADDR + "/v1/secret/data/shared/jwt")
                        .header("X-Vault-Token", "vault-token")
                        .retrieve()
                        .body(VaultKvResponse.class))
                .thenReturn(kvResponse);

        String value = client.fetchSharedSecret("shared/jwt", "secret");

        assertThat(value).isEqualTo("c2VjcmV0");
    }

    @Test
    void throwsWhenLoginDoesNotReturnToken() {
        RestClient restClient = mock(RestClient.class, RETURNS_DEEP_STUBS);
        VaultClient client = new VaultClient(VAULT_ADDR, "role", "secret", restClient);

        when(restClient.post()
                        .uri(VAULT_ADDR + "/v1/auth/approle/login")
                        .body(eq(Map.of("role_id", "role", "secret_id", "secret")))
                        .retrieve()
                        .body(VaultLoginResponse.class))
                .thenReturn(null);

        assertThatThrownBy(() -> client.fetchSharedSecret("shared/jwt", "secret"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("client token");
    }

    @Test
    void throwsWhenSecretDataMissing() {
        RestClient restClient = mock(RestClient.class, RETURNS_DEEP_STUBS);
        VaultClient client = new VaultClient(VAULT_ADDR, "role", "secret", restClient);

        VaultLoginResponse loginResponse = new VaultLoginResponse(new VaultLoginResponse.VaultAuth("vault-token"));
        when(restClient.post()
                        .uri(VAULT_ADDR + "/v1/auth/approle/login")
                        .body(eq(Map.of("role_id", "role", "secret_id", "secret")))
                        .retrieve()
                        .body(VaultLoginResponse.class))
                .thenReturn(loginResponse);

        when(restClient.get()
                        .uri(VAULT_ADDR + "/v1/secret/data/shared/jwt")
                        .header("X-Vault-Token", "vault-token")
                        .retrieve()
                        .body(VaultKvResponse.class))
                .thenReturn(null);

        assertThatThrownBy(() -> client.fetchSharedSecret("shared/jwt", "secret"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("empty or missing");
    }

    @Test
    void throwsWhenFieldMissingFromSecret() {
        RestClient restClient = mock(RestClient.class, RETURNS_DEEP_STUBS);
        VaultClient client = new VaultClient(VAULT_ADDR, "role", "secret", restClient);

        VaultLoginResponse loginResponse = new VaultLoginResponse(new VaultLoginResponse.VaultAuth("vault-token"));
        when(restClient.post()
                        .uri(VAULT_ADDR + "/v1/auth/approle/login")
                        .body(eq(Map.of("role_id", "role", "secret_id", "secret")))
                        .retrieve()
                        .body(VaultLoginResponse.class))
                .thenReturn(loginResponse);

        VaultKvResponse kvResponse = new VaultKvResponse(new VaultKvResponse.VaultKvData(Map.of("other", "value")));
        when(restClient.get()
                        .uri(VAULT_ADDR + "/v1/secret/data/shared/jwt")
                        .header("X-Vault-Token", "vault-token")
                        .retrieve()
                        .body(VaultKvResponse.class))
                .thenReturn(kvResponse);

        assertThatThrownBy(() -> client.fetchSharedSecret("shared/jwt", "secret"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("no field");
    }
}
