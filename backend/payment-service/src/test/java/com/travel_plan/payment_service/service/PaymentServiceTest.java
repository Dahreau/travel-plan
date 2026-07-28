package com.travel_plan.payment_service.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.travel_plan.payment_service.domain.MethodType;
import com.travel_plan.payment_service.domain.Payment;
import com.travel_plan.payment_service.domain.PaymentMethod;
import com.travel_plan.payment_service.domain.PaymentStatus;
import com.travel_plan.payment_service.domain.ProviderType;
import com.travel_plan.payment_service.exception.InvalidRefundException;
import com.travel_plan.payment_service.exception.PaymentMethodNotFoundException;
import com.travel_plan.payment_service.exception.PaymentNotFoundException;
import com.travel_plan.payment_service.provider.ChargeResult;
import com.travel_plan.payment_service.provider.PaymentProvider;
import com.travel_plan.payment_service.provider.PaymentProviderResolver;
import com.travel_plan.payment_service.repository.PaymentMethodRepository;
import com.travel_plan.payment_service.repository.PaymentRepository;
import com.travel_plan.payment_service.web.PaymentRequest;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PaymentServiceTest {

    private final PaymentRepository paymentRepository = mock(PaymentRepository.class);
    private final PaymentMethodRepository paymentMethodRepository = mock(PaymentMethodRepository.class);
    private final PaymentProviderResolver paymentProviderResolver = mock(PaymentProviderResolver.class);
    private final PaymentService paymentService =
            new PaymentService(paymentRepository, paymentMethodRepository, paymentProviderResolver);

    @Test
    void createChargesResolvedProviderAndSavesPayment() {
        UUID methodId = UUID.randomUUID();
        PaymentMethod method = PaymentMethod.builder()
                .id(methodId)
                .provider(ProviderType.STRIPE)
                .type(MethodType.CARD)
                .providerToken("pm_card_visa")
                .build();
        PaymentProvider stripeProvider = mock(PaymentProvider.class);
        when(paymentMethodRepository.findById(methodId)).thenReturn(Optional.of(method));
        when(paymentProviderResolver.resolve(ProviderType.STRIPE)).thenReturn(stripeProvider);
        when(stripeProvider.charge(any())).thenReturn(new ChargeResult("pi_123", PaymentStatus.SUCCEEDED));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PaymentRequest request = new PaymentRequest(
                UUID.randomUUID(), UUID.randomUUID(), methodId, new BigDecimal("99.90"), "eur");
        Payment created = paymentService.create(request);

        assertThat(created.getProvider()).isEqualTo(ProviderType.STRIPE);
        assertThat(created.getStatus()).isEqualTo(PaymentStatus.SUCCEEDED);
        assertThat(created.getProviderReference()).isEqualTo("pi_123");
        assertThat(created.getCurrency()).isEqualTo("EUR");
    }

    @Test
    void createThrowsWhenPaymentMethodMissing() {
        UUID methodId = UUID.randomUUID();
        when(paymentMethodRepository.findById(methodId)).thenReturn(Optional.empty());

        PaymentRequest request = new PaymentRequest(
                UUID.randomUUID(), UUID.randomUUID(), methodId, new BigDecimal("10.00"), "EUR");

        assertThatThrownBy(() -> paymentService.create(request))
                .isInstanceOf(PaymentMethodNotFoundException.class);
    }

    @Test
    void findByIdThrowsWhenMissing() {
        UUID id = UUID.randomUUID();
        when(paymentRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> paymentService.findById(id)).isInstanceOf(PaymentNotFoundException.class);
    }

    @Test
    void refundMarksSucceededPaymentAsRefunded() {
        UUID id = UUID.randomUUID();
        Payment payment = Payment.builder().id(id).status(PaymentStatus.SUCCEEDED).build();
        when(paymentRepository.findById(id)).thenReturn(Optional.of(payment));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Payment refunded = paymentService.refund(id);

        assertThat(refunded.getStatus()).isEqualTo(PaymentStatus.REFUNDED);
    }

    @Test
    void refundThrowsWhenPaymentNotSucceeded() {
        UUID id = UUID.randomUUID();
        Payment payment = Payment.builder().id(id).status(PaymentStatus.FAILED).build();
        when(paymentRepository.findById(id)).thenReturn(Optional.of(payment));

        assertThatThrownBy(() -> paymentService.refund(id)).isInstanceOf(InvalidRefundException.class);
    }

    @Test
    void findAllDelegatesToRepository() {
        when(paymentRepository.findAll()).thenReturn(List.of(Payment.builder().build()));

        assertThat(paymentService.findAll()).hasSize(1);
    }
}
