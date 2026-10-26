package com.neueda.leap.dto.response;

import java.time.LocalDate;
import java.util.UUID;

import com.neueda.leap.model.User;
import com.neueda.leap.model.UserPii;

public record UserDetailsResponse(
        UUID userId,
        String role,
        String status,
        String firstName,
        String lastName,
        LocalDate dateOfBirth,
        String ssn,
        String address,
        String phoneNumber,
        String email
) {
    public static UserDetailsResponse from(User user, UserPii userPii) {
        return new UserDetailsResponse(
                user.getUserId(),
                user.getRole(),
                user.getStatus(),
                userPii.getFirstName(),
                userPii.getLastName(),
                userPii.getDateOfBirth(),
                userPii.getSsn(),
                userPii.getAddress(),
                userPii.getPhoneNumber(),
                userPii.getEmail());
    }
}