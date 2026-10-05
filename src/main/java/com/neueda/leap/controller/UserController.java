package com.neueda.leap.controller;
import org.springframework.web.bind.annotation.*;
import com.neueda.leap.service.UserService;
import com.neueda.leap.model.User;
import com.neueda.leap.model.UserPii;
import com.neueda.leap.dto.request.CreateUserRequest;
import java.util.UUID;
import java.util.List;
@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/{userId}")
    public User getUserById(@PathVariable UUID userId) {
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
            createUserRequest.status(),
            createUserRequest.firstName(),
            createUserRequest.lastName()
        );
        UserPii userPii = new UserPii(
            createUserRequest.userId(),
            createUserRequest.dateOfBirth(),
            createUserRequest.ssn(),
            createUserRequest.address(),
            createUserRequest.phoneNumber(),
            createUserRequest.email()
        );
        return userService.createUser(user, userPii);
    }

    @PutMapping("/{userId}/status")
    public User updateUserStatus(@PathVariable UUID userId, @RequestParam String newStatus) {
        User user = userService.getUserById(userId);
        return userService.updateUserStatus(user, newStatus);
    }

    @DeleteMapping("/{userId}")
    public void deleteUserById(@PathVariable UUID userId) {
        userService.deleteUserById(userId);
    }


}