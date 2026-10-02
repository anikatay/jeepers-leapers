package com.neueda.leap.model;

import java.time.OffsetDateTime;
import java.time.Period;
import java.util.UUID;

public class UserPii {

    private UUID userId;

    private User user;

    private String firstName;

    private String lastName;

    private OffsetDateTime dateOfBirth;

    private String ssn;

    private String address;

    private String phoneNumber;

    private String email;

    public UserPii() {
    }

    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }

    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }

    public String getFirstName() { return firstName; }
    public void setFirstName(String firstName) { this.firstName = firstName; }

    public String getLastName() { return lastName; }
    public void setLastName(String lastName) { this.lastName = lastName; }

    public OffsetDateTime getDateOfBirth() { return dateOfBirth; }
    public void setDateOfBirth(OffsetDateTime dateOfBirth) { this.dateOfBirth = dateOfBirth; }

    public String getSsn() { return ssn; }
    public void setSsn(String ssn) { this.ssn = ssn; }

    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }

    public String getPhoneNumber() { return phoneNumber; }
    public void setPhoneNumber(String phoneNumber) { this.phoneNumber = phoneNumber; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public int getAge() {
        if (dateOfBirth == null) {
            return 0;
        }
        return Period.between(dateOfBirth.toLocalDate(), OffsetDateTime.now().toLocalDate()).getYears();
    }
}