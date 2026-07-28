package com.travel_plan.api_gateway.vault;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

@SuppressWarnings({"unchecked", "rawtypes"})
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
        RestClient restClient = mock(RestClient.class);
        VaultClient client = new VaultClient(VAULT_ADDR, "role", "secret", restClient);

        stubLogin(restClient, new VaultLoginResponse(new VaultLoginResponse.VaultAuth("vault-token")));
        stubKvRead(restClient, "vault-token", new VaultKvResponse(new VaultKvResponse.VaultKvData(Map.of("secret", "c2VjcmV0"))));

        String value = client.fetchSharedSecret("shared/jwt", "secret");

        assertThat(value).isEqualTo("c2VjcmV0");
    }

    @Test
    void throwsWhenLoginDoesNotReturnToken() {
        RestClient restClient = mock(RestClient.class);
        VaultClient client = new VaultClient(VAULT_ADDR, "role", "secret", restClient);

        stubLogin(restClient, null);

        assertThatThrownBy(() -> client.fetchSharedSecret("shared/jwt", "secret"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("client token");
    }

    @Test
    void throwsWhenSecretDataMissing() {
        RestClient restClient = mock(RestClient.class);
        VaultClient client = new VaultClient(VAULT_ADDR, "role", "secret", restClient);

        stubLogin(restClient, new VaultLoginResponse(new VaultLoginResponse.VaultAuth("vault-token")));
        stubKvRead(restClient, "vault-token", null);

        assertThatThrownBy(() -> client.fetchSharedSecret("shared/jwt", "secret"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("empty or missing");
    }

    @Test
    void throwsWhenFieldMissingFromSecret() {
        RestClient restClient = mock(RestClient.class);
        VaultClient client = new VaultClient(VAULT_ADDR, "role", "secret", restClient);

        stubLogin(restClient, new VaultLoginResponse(new VaultLoginResponse.VaultAuth("vault-token")));
        stubKvRead(restClient, "vault-token", new VaultKvResponse(new VaultKvResponse.VaultKvData(Map.of("other", "value"))));

        assertThatThrownBy(() -> client.fetchSharedSecret("shared/jwt", "secret"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("no field");
    }

    private void stubLogin(RestClient restClient, VaultLoginResponse response) {
        RestClient.RequestBodyUriSpec bodyUriSpec = mock(RestClient.RequestBodyUriSpec.class);
        RestClient.RequestBodySpec bodySpec = mock(RestClient.RequestBodySpec.class);
        RestClient.ResponseSpec responseSpec = mock(RestClient.ResponseSpec.class);

        when(restClient.post()).thenReturn(bodyUriSpec);
        when(bodyUriSpec.uri(VAULT_ADDR + "/v1/auth/approle/login")).thenReturn(bodySpec);
        when(bodySpec.body(Map.of("role_id", "role", "secret_id", "secret"))).thenReturn(bodySpec);
        when(bodySpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.body(VaultLoginResponse.class)).thenReturn(response);
    }

    private void stubKvRead(RestClient restClient, String token, VaultKvResponse response) {
        RestClient.RequestHeadersUriSpec uriSpec = mock(RestClient.RequestHeadersUriSpec.class);
        RestClient.RequestHeadersSpec headersSpec = mock(RestClient.RequestHeadersSpec.class);
        RestClient.ResponseSpec responseSpec = mock(RestClient.ResponseSpec.class);

        when(restClient.get()).thenReturn(uriSpec);
        when(uriSpec.uri(VAULT_ADDR + "/v1/secret/data/shared/jwt")).thenReturn(headersSpec);
        when(headersSpec.header("X-Vault-Token", token)).thenReturn(headersSpec);
        when(headersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.body(VaultKvResponse.class)).thenReturn(response);
    }
}
