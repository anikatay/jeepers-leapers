package com.neueda.leap.model;
import com.neueda.leap.exception.ObjectInvalidException;
import java.time.LocalDate;
import java.time.Period;
import java.util.UUID;

public class UserPii {

    private UUID userId;

    private String firstName;

    private String lastName;

    private LocalDate dateOfBirth;

    private String ssn;

    private String address;

    private String phoneNumber;

    private String email;

    public UserPii() {
    }

    public UserPii(
        UUID userId, 
        String firstName, 
        String lastName, 
        LocalDate dateOfBirth, 
        String ssn, 
        String address, 
        String phoneNumber, 
        String email
    ) {
        this.userId = userId;
        this.firstName = firstName;
        this.lastName = lastName;
        this.dateOfBirth = dateOfBirth;
        this.ssn = ssn;
        this.address = address;
        this.phoneNumber = phoneNumber;
        this.email = email;
    }

    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }

    public String getFirstName() { return firstName; }
    public void setFirstName(String firstName) { this.firstName = firstName; }

    public String getLastName() { return lastName; }
    public void setLastName(String lastName) { this.lastName = lastName; }

    public LocalDate getDateOfBirth() { return dateOfBirth; }
    public void setDateOfBirth(LocalDate dateOfBirth) { this.dateOfBirth = dateOfBirth; }

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
            // TODO: Change exception to proper response body
            throw new ObjectInvalidException("Date of birth is not set");
        }
        return Period.between(dateOfBirth, LocalDate.now()).getYears();
    }
}