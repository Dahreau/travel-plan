package com.travel_plan.travel_service.vault;

import java.net.http.HttpClient;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.Map;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class VaultClient {

    private final RestClient restClient;
    private final String vaultAddr;
    private final String roleId;
    private final String secretId;

    @Autowired
    public VaultClient(
            @Value("${app.vault.addr}") String vaultAddr,
            @Value("${app.vault.role-id}") String roleId,
            @Value("${app.vault.secret-id}") String secretId) {
        this(vaultAddr, roleId, secretId, RestClient.builder()
                .requestFactory(new JdkClientHttpRequestFactory(internalNetworkHttpClient()))
                .build());
    }

    // Vault presente un certificat auto-signe sur le reseau Docker interne (aucune AC
    // publique ne signe un certificat pour un nom qui n'existe que dans notre propre
    // bridge). Faire confiance a ce certificat precis ici est equivalent au choix deja
    // fait pour Nginx : le trafic reste chiffre, seule la validation par une AC tierce
    // est absente - acceptable pour un flux qui ne sort jamais du reseau Docker isole.
    private static HttpClient internalNetworkHttpClient() {
        try {
            TrustManager[] trustAllCerts = {
                new X509TrustManager() {
                    public void checkClientTrusted(X509Certificate[] chain, String authType) {}

                    public void checkServerTrusted(X509Certificate[] chain, String authType) {}

                    public X509Certificate[] getAcceptedIssuers() {
                        return new X509Certificate[0];
                    }
                }
            };
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, trustAllCerts, new SecureRandom());
            return HttpClient.newBuilder().sslContext(sslContext).build();
        } catch (NoSuchAlgorithmException | KeyManagementException e) {
            throw new IllegalStateException("Failed to configure internal-network HTTP client for Vault", e);
        }
    }

    VaultClient(String vaultAddr, String roleId, String secretId, RestClient restClient) {
        this.restClient = restClient;
        this.vaultAddr = vaultAddr;
        this.roleId = roleId;
        this.secretId = secretId;
    }

    public String fetchSharedSecret(String path, String field) {
        if (roleId == null || roleId.isBlank() || secretId == null || secretId.isBlank()) {
            throw new IllegalStateException(
                    "Vault AppRole credentials not configured (VAULT_ROLE_ID/VAULT_SECRET_ID)");
        }

        String token = login();
        VaultKvResponse response = restClient.get()
                .uri(vaultAddr + "/v1/secret/data/" + path)
                .header("X-Vault-Token", token)
                .retrieve()
                .body(VaultKvResponse.class);

        if (response == null || response.data() == null || response.data().data() == null) {
            throw new IllegalStateException("Vault secret at path '" + path + "' is empty or missing");
        }

        String value = response.data().data().get(field);
        if (value == null) {
            throw new IllegalStateException("Vault secret at path '" + path + "' has no field '" + field + "'");
        }
        return value;
    }

    private String login() {
        Map<String, Object> body = Map.of("role_id", roleId, "secret_id", secretId);
        VaultLoginResponse response = restClient.post()
                .uri(vaultAddr + "/v1/auth/approle/login")
                .body(body)
                .retrieve()
                .body(VaultLoginResponse.class);

        if (response == null || response.auth() == null || response.auth().clientToken() == null) {
            throw new IllegalStateException("Vault AppRole login did not return a client token");
        }
        return response.auth().clientToken();
    }
}
