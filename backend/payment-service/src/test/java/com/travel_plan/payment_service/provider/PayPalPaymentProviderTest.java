package com.travel_plan.payment_service.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.travel_plan.payment_service.domain.PaymentStatus;
import com.travel_plan.payment_service.domain.ProviderType;
import java.math.BigDecimal;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

@SuppressWarnings({"unchecked", "rawtypes"})
class PayPalPaymentProviderTest {

    private static final String API_BASE = "https://api-m.sandbox.paypal.com";

    private final RestClient restClient = mock(RestClient.class);
    private final RestClient.RequestBodyUriSpec bodyUriSpec = mock(RestClient.RequestBodyUriSpec.class);
    private final PayPalCredentials credentials = new PayPalCredentials("client-id", "client-secret");
    private final PayPalPaymentProvider provider = new PayPalPaymentProvider(restClient, credentials, API_BASE);

    @BeforeEach
    void setUp() {
        when(restClient.post()).thenReturn(bodyUriSpec);
    }

    @Test
    void typeReturnsPayPal() {
        assertThat(provider.type()).isEqualTo(ProviderType.PAYPAL);
    }

    @Test
    void chargeReturnsSucceededWhenOrderCompleted() {
        stubOAuthToken("access-token-123");
        stubCreateOrder(Map.of("id", "order-1", "status", "COMPLETED"));

        ChargeResult result = provider.charge(new ChargeRequest(new BigDecimal("42.00"), "EUR", "vault-token-1"));

        assertThat(result.providerReference()).isEqualTo("order-1");
        assertThat(result.status()).isEqualTo(PaymentStatus.SUCCEEDED);
    }

    @Test
    void chargeReturnsFailedWhenOrderNotCompleted() {
        stubOAuthToken("access-token-123");
        stubCreateOrder(Map.of("id", "order-2", "status", "PENDING"));

        ChargeResult result = provider.charge(new ChargeRequest(new BigDecimal("42.00"), "EUR", "vault-token-1"));

        assertThat(result.status()).isEqualTo(PaymentStatus.FAILED);
    }

    @Test
    void chargeThrowsWhenAccessTokenMissing() {
        stubOAuthToken(null);

        assertThatThrownBy(() ->
                        provider.charge(new ChargeRequest(new BigDecimal("10.00"), "EUR", "vault-token-1")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("access token");
    }

    @Test
    void chargeThrowsWhenOrderIdMissing() {
        stubOAuthToken("access-token-123");
        stubCreateOrder(Map.of("status", "COMPLETED"));

        assertThatThrownBy(() ->
                        provider.charge(new ChargeRequest(new BigDecimal("10.00"), "EUR", "vault-token-1")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("order id");
    }

    private void stubOAuthToken(String accessToken) {
        RestClient.RequestBodySpec bodySpec = mock(RestClient.RequestBodySpec.class);
        RestClient.ResponseSpec responseSpec = mock(RestClient.ResponseSpec.class);

        when(bodyUriSpec.uri(API_BASE + "/v1/oauth2/token")).thenReturn(bodySpec);
        when(bodySpec.headers(any())).thenReturn(bodySpec);
        when(bodySpec.body(any(MultiValueMap.class))).thenReturn(bodySpec);
        when(bodySpec.retrieve()).thenReturn(responseSpec);
        Map<String, Object> response = accessToken == null ? Map.of() : Map.of("access_token", accessToken);
        when(responseSpec.body(Map.class)).thenReturn(response);
    }

    private void stubCreateOrder(Map<String, Object> response) {
        RestClient.RequestBodySpec bodySpec = mock(RestClient.RequestBodySpec.class);
        RestClient.ResponseSpec responseSpec = mock(RestClient.ResponseSpec.class);

        when(bodyUriSpec.uri(API_BASE + "/v2/checkout/orders")).thenReturn(bodySpec);
        when(bodySpec.headers(any())).thenReturn(bodySpec);
        when(bodySpec.body(any(Map.class))).thenReturn(bodySpec);
        when(bodySpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.body(Map.class)).thenReturn(response);
    }
}
