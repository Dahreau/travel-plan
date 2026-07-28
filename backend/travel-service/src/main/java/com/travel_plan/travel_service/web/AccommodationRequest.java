package com.travel_plan.travel_service.web;

import com.travel_plan.travel_service.domain.AccommodationType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

public record AccommodationRequest(
        @NotBlank String name,
        @NotNull AccommodationType type,
        @NotBlank String address,
        @NotNull LocalDate checkIn,
        @NotNull LocalDate checkOut) {
}
