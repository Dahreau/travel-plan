package com.travel_plan.travel_service.web;

import com.travel_plan.travel_service.domain.TransportationType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;

public record TransportationRequest(
        @NotNull TransportationType type,
        @NotBlank String fromLocation,
        @NotBlank String toLocation,
        @NotNull Instant departureTime,
        @NotNull Instant arrivalTime,
        String provider) {
}
