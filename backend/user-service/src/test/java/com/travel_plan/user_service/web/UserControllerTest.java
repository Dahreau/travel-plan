package com.travel_plan.user_service.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.travel_plan.user_service.domain.Role;
import com.travel_plan.user_service.domain.User;
import com.travel_plan.user_service.exception.ApiExceptionHandler;
import com.travel_plan.user_service.repository.UserRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class UserControllerTest {

    private UserRepository userRepository;
    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        UserController controller = new UserController(userRepository);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @Test
    void findAllReturnsAllUsers() throws Exception {
        when(userRepository.findAll()).thenReturn(List.of(newUser("ada@travel-plan.com")));

        mockMvc.perform(get("/api/users"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].email").value("ada@travel-plan.com"));
    }

    @Test
    void findByIdReturns404WhenMissing() throws Exception {
        UUID id = UUID.randomUUID();
        when(userRepository.findById(id)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/users/{id}", id)).andExpect(status().isNotFound());
    }

    @Test
    void createReturns201ForValidRequest() throws Exception {
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UserRequest request = new UserRequest("Ada", "Lovelace", "ada@travel-plan.com", null, Role.TRAVELER, null);

        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("ada@travel-plan.com"));
    }

    @Test
    void createReturns400ForBlankFirstName() throws Exception {
        UserRequest request = new UserRequest("", "Lovelace", "ada@travel-plan.com", null, Role.TRAVELER, null);

        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createReturns409WhenEmailAlreadyExists() throws Exception {
        when(userRepository.save(any(User.class))).thenThrow(new DataIntegrityViolationException("duplicate email"));

        UserRequest request = new UserRequest("Ada", "Lovelace", "ada@travel-plan.com", null, Role.TRAVELER, null);

        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict());
    }

    @Test
    void updateReplacesUserFields() throws Exception {
        UUID id = UUID.randomUUID();
        User existing = newUser("ada@travel-plan.com");
        when(userRepository.findById(id)).thenReturn(Optional.of(existing));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UserRequest request =
                new UserRequest("Ada", "Byron", "ada.byron@travel-plan.com", "0102030405", Role.TRAVELER, null);

        mockMvc.perform(put("/api/users/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lastName").value("Byron"));
    }

    @Test
    void updateReturns404WhenUserMissing() throws Exception {
        UUID id = UUID.randomUUID();
        when(userRepository.findById(id)).thenReturn(Optional.empty());

        UserRequest request = new UserRequest("Ada", "Lovelace", "ada@travel-plan.com", null, Role.TRAVELER, null);

        mockMvc.perform(put("/api/users/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteRemovesExistingUser() throws Exception {
        UUID id = UUID.randomUUID();
        when(userRepository.findById(id)).thenReturn(Optional.of(newUser("ada@travel-plan.com")));

        mockMvc.perform(delete("/api/users/{id}", id)).andExpect(status().isNoContent());
    }

    @Test
    void deleteReturns404WhenUserMissing() throws Exception {
        UUID id = UUID.randomUUID();
        when(userRepository.findById(id)).thenReturn(Optional.empty());

        mockMvc.perform(delete("/api/users/{id}", id)).andExpect(status().isNotFound());
    }

    private User newUser(String email) {
        return User.builder()
                .id(UUID.randomUUID())
                .firstName("Ada")
                .lastName("Lovelace")
                .email(email)
                .role(Role.TRAVELER)
                .build();
    }
}
