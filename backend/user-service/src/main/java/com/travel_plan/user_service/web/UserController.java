package com.travel_plan.user_service.web;

import com.travel_plan.user_service.domain.Address;
import com.travel_plan.user_service.domain.User;
import com.travel_plan.user_service.exception.UserNotFoundException;
import com.travel_plan.user_service.repository.UserRepository;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserRepository userRepository;

    @GetMapping
    public List<UserResponse> findAll() {
        return userRepository.findAll().stream().map(UserResponse::from).toList();
    }

    @GetMapping("/{id}")
    public UserResponse findById(@PathVariable UUID id) {
        return UserResponse.from(getOrThrow(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse create(@Valid @RequestBody UserRequest request) {
        User user = User.builder()
                .firstName(request.firstName())
                .lastName(request.lastName())
                .email(request.email())
                .phone(request.phone())
                .role(request.role())
                .build();
        attachAddress(user, request.address());
        return UserResponse.from(userRepository.save(user));
    }

    @PutMapping("/{id}")
    public UserResponse update(@PathVariable UUID id, @Valid @RequestBody UserRequest request) {
        User user = getOrThrow(id);
        user.setFirstName(request.firstName());
        user.setLastName(request.lastName());
        user.setEmail(request.email());
        user.setPhone(request.phone());
        user.setRole(request.role());
        attachAddress(user, request.address());
        return UserResponse.from(userRepository.save(user));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        User user = getOrThrow(id);
        userRepository.delete(user);
    }

    private User getOrThrow(UUID id) {
        return userRepository.findById(id).orElseThrow(() -> new UserNotFoundException(id));
    }

    private void attachAddress(User user, AddressRequest request) {
        if (request == null) {
            user.setAddress(null);
            return;
        }

        Address address = user.getAddress();
        if (address == null) {
            address = new Address();
            address.setUser(user);
            user.setAddress(address);
        }
        address.setStreet(request.street());
        address.setCity(request.city());
        address.setPostalCode(request.postalCode());
        address.setCountry(request.country());
    }
}
