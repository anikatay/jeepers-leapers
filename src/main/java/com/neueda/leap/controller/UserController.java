package com.neueda.leap.controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import com.neueda.leap.service.UserService;
import com.neueda.leap.service.AuthorizationService;
import com.neueda.leap.model.User;
import com.neueda.leap.model.UserPii;
import com.neueda.leap.dto.request.CreateUserRequest;
import java.util.UUID;
import java.util.List;
@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;
    private final AuthorizationService authorizationService;

    public UserController(UserService userService, AuthorizationService authorizationService) {
        this.userService = userService;
        this.authorizationService = authorizationService;
    }

    @GetMapping("/{userId}")
    public User getUserById(@PathVariable UUID userId, @AuthenticationPrincipal Jwt jwt) {
        authorizationService.requireUser(userId, authorizationService.getAuthenticatedUserId(jwt));

        return userService.getUserById(userId);
    }

    @GetMapping
    public List<User> getAllUsers() {
        return userService.getAllUsers();
    }

    @PostMapping
    public User createUser(@RequestBody CreateUserRequest createUserRequest) {
        User user = new User(
            createUserRequest.userId(),
            createUserRequest.role(),
            createUserRequest.status()
        );
        UserPii userPii = new UserPii(
            createUserRequest.userId(),
            createUserRequest.firstName(),
            createUserRequest.lastName(),
            createUserRequest.dateOfBirth(),
            createUserRequest.ssn(),
            createUserRequest.address(),
            createUserRequest.phoneNumber(),
            createUserRequest.email()
        );
        return userService.createUser(user, userPii);
    }

    @PutMapping("/{userId}/status")
    public User updateUserStatus(@PathVariable UUID userId, @RequestParam String newStatus,
            @AuthenticationPrincipal Jwt jwt) {
        authorizationService.requireUser(userId, authorizationService.getAuthenticatedUserId(jwt));

        User user = userService.getUserById(userId);
        return userService.updateUserStatus(user, newStatus);
    }

    @DeleteMapping("/{userId}")
    public void deleteUserById(@PathVariable UUID userId, @AuthenticationPrincipal Jwt jwt) {
        authorizationService.requireUser(userId, authorizationService.getAuthenticatedUserId(jwt));

        userService.deleteUserById(userId);
    }


}