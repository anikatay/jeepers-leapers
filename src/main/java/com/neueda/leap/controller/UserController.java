package com.neueda.leap.controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import com.neueda.leap.service.UserService;
import com.neueda.leap.service.UserPiiService;
import com.neueda.leap.service.AuthorizationService;
import com.neueda.leap.model.User;
import com.neueda.leap.dto.request.CreateUserRequest;
import com.neueda.leap.dto.response.UserDetailsResponse;
import com.neueda.leap.model.UserPii;
import com.neueda.leap.exception.ObjectNotFoundException;
import java.util.UUID;
import java.util.List;
@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;
    private final UserPiiService userPiiService;
    private final AuthorizationService authorizationService;

    public UserController(UserService userService, UserPiiService userPiiService,
            AuthorizationService authorizationService) {
        this.userService = userService;
        this.userPiiService = userPiiService;
        this.authorizationService = authorizationService;
    }

    @GetMapping("/{userId}")
        public UserDetailsResponse getUserById(@PathVariable(name = "userId") UUID userId,
            @AuthenticationPrincipal Jwt jwt) {
        authorizationService.requireAdminOrUser(userId, jwt);

        User user = userService.getUserById(userId);
        UserPii pii = userPiiService.getUserPiiById(userId);
        return UserDetailsResponse.from(user, pii);
    }

    @GetMapping
    public List<User> getAllUsers(@AuthenticationPrincipal Jwt jwt) {
        authorizationService.requireAdmin(jwt);
        return userService.getAllUsers();
    }

    @PostMapping
    public User createUser(@RequestBody CreateUserRequest createUserRequest, @AuthenticationPrincipal Jwt jwt) {
        authorizationService.requireAdmin(jwt);
        User user = new User(
            createUserRequest.userId(),
            createUserRequest.role(),
            createUserRequest.status()
        );
        return userService.createUser(user);
    }

    @PutMapping("/{userId}/status")
        public User updateUserStatus(@PathVariable(name = "userId") UUID userId,
            @RequestParam(name = "newStatus") String newStatus,
            @AuthenticationPrincipal Jwt jwt) {
        authorizationService.requireAdmin(jwt);

        User user = userService.getUserById(userId);
        return userService.updateUserStatus(user, newStatus);
    }

    @DeleteMapping("/{userId}")
        public void deleteUserById(@PathVariable(name = "userId") UUID userId,
            @AuthenticationPrincipal Jwt jwt) {
        authorizationService.requireAdmin(jwt);

        userService.deleteUserById(userId);
    }



}