package com.travel_plan.travel_service.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.travel_plan.travel_service.domain.Destination;
import com.travel_plan.travel_service.domain.Travel;
import com.travel_plan.travel_service.domain.TravelStatus;
import com.travel_plan.travel_service.exception.ApiExceptionHandler;
import com.travel_plan.travel_service.exception.TravelNotFoundException;
import com.travel_plan.travel_service.service.TravelService;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class TravelControllerTest {

    private TravelService travelService;
    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @BeforeEach
    void setUp() {
        travelService = mock(TravelService.class);
        TravelController controller = new TravelController(travelService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @Test
    void findAllReturnsAllTravels() throws Exception {
        when(travelService.findAll()).thenReturn(List.of(newTravel("Iberian tour")));

        mockMvc.perform(get("/api/travels"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].title").value("Iberian tour"));
    }

    @Test
    void findByIdReturns404WhenMissing() throws Exception {
        UUID id = UUID.randomUUID();
        when(travelService.findById(id)).thenThrow(new TravelNotFoundException(id));

        mockMvc.perform(get("/api/travels/{id}", id)).andExpect(status().isNotFound());
    }

    @Test
    void createReturns201ForValidRequest() throws Exception {
        when(travelService.create(any(TravelRequest.class))).thenReturn(newTravel("Iberian tour"));

        mockMvc.perform(post("/api/travels")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest("Iberian tour"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("Iberian tour"));
    }

    @Test
    void createReturns400WhenNoDestination() throws Exception {
        TravelRequest request = new TravelRequest(
                "Iberian tour",
                UUID.randomUUID(),
                LocalDate.of(2026, 9, 1),
                LocalDate.of(2026, 9, 10),
                TravelStatus.PLANNED,
                List.of(),
                List.of());

        mockMvc.perform(post("/api/travels")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateReturns404WhenTravelMissing() throws Exception {
        UUID id = UUID.randomUUID();
        when(travelService.update(any(UUID.class), any(TravelRequest.class)))
                .thenThrow(new TravelNotFoundException(id));

        mockMvc.perform(put("/api/travels/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest("Iberian tour"))))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteRemovesExistingTravel() throws Exception {
        UUID id = UUID.randomUUID();

        mockMvc.perform(delete("/api/travels/{id}", id)).andExpect(status().isNoContent());
    }

    @Test
    void deleteReturns404WhenTravelMissing() throws Exception {
        UUID id = UUID.randomUUID();
        doThrow(new TravelNotFoundException(id)).when(travelService).delete(id);

        mockMvc.perform(delete("/api/travels/{id}", id)).andExpect(status().isNotFound());
    }

    private Travel newTravel(String title) {
        Travel travel = Travel.builder()
                .id(UUID.randomUUID())
                .title(title)
                .ownerId(UUID.randomUUID())
                .startDate(LocalDate.of(2026, 9, 1))
                .endDate(LocalDate.of(2026, 9, 10))
                .status(TravelStatus.PLANNED)
                .build();
        Destination destination = Destination.builder()
                .id(UUID.randomUUID())
                .travel(travel)
                .city("Lisbon")
                .country("Portugal")
                .arrivalDate(LocalDate.of(2026, 9, 1))
                .departureDate(LocalDate.of(2026, 9, 5))
                .orderIndex(0)
                .build();
        travel.getDestinations().add(destination);
        return travel;
    }

    private TravelRequest validRequest(String title) {
        DestinationRequest destination = new DestinationRequest(
                "Lisbon", "Portugal", LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 5), 0, List.of(), null);
        return new TravelRequest(
                title,
                UUID.randomUUID(),
                LocalDate.of(2026, 9, 1),
                LocalDate.of(2026, 9, 10),
                TravelStatus.PLANNED,
                List.of(destination),
                List.of());
    }
}
