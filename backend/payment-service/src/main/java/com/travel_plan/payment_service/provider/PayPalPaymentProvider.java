package com.travel_plan.payment_service.provider;

import com.travel_plan.payment_service.domain.PaymentStatus;
import com.travel_plan.payment_service.domain.ProviderType;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

@Component
public class PayPalPaymentProvider implements PaymentProvider {

    private final RestClient restClient;
    private final PayPalCredentials credentials;
    private final String apiBase;

    public PayPalPaymentProvider(
            RestClient paymentRestClient, PayPalCredentials payPalCredentials,
            @Value("${app.paypal.api-base}") String apiBase) {
        this.restClient = paymentRestClient;
        this.credentials = payPalCredentials;
        this.apiBase = apiBase;
    }

    @Override
    public ProviderType type() {
        return ProviderType.PAYPAL;
    }

    @Override
    public ChargeResult charge(ChargeRequest request) {
        String accessToken = fetchAccessToken();

        Map<String, Object> amount =
                Map.of("currency_code", request.currency().toUpperCase(), "value", request.amount().toPlainString());
        Map<String, Object> purchaseUnit = Map.of("amount", amount);
        Map<String, Object> paypalSource = Map.of("vault_id", request.providerToken());
        Map<String, Object> orderBody = Map.of(
                "intent", "CAPTURE",
                "purchase_units", List.of(purchaseUnit),
                "payment_source", Map.of("paypal", paypalSource));

        Map<String, Object> response = restClient
                .post()
                .uri(apiBase + "/v2/checkout/orders")
                .headers(headers -> headers.setBearerAuth(accessToken))
                .body(orderBody)
                .retrieve()
                .body(Map.class);

        if (response == null || response.get("id") == null) {
            throw new IllegalStateException("PayPal did not return an order id");
        }

        String status = String.valueOf(response.get("status"));
        PaymentStatus mappedStatus = "COMPLETED".equals(status) ? PaymentStatus.SUCCEEDED : PaymentStatus.FAILED;
        return new ChargeResult(String.valueOf(response.get("id")), mappedStatus);
    }

    private String fetchAccessToken() {
        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("grant_type", "client_credentials");

        Map<String, Object> response = restClient
                .post()
                .uri(apiBase + "/v1/oauth2/token")
                .headers(headers -> {
                    headers.setBasicAuth(credentials.clientId(), credentials.clientSecret());
                    headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
                })
                .body(body)
                .retrieve()
                .body(Map.class);

        if (response == null || response.get("access_token") == null) {
            throw new IllegalStateException("PayPal did not return an access token");
        }
        return String.valueOf(response.get("access_token"));
    }
}
