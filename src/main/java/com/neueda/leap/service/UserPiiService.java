package com.neueda.leap.service;
import org.springframework.stereotype.Service;
import com.neueda.leap.exception.ObjectNotProcessedException;
import com.neueda.leap.exception.ObjectNotFoundException;
import com.neueda.leap.mapper.UserPiiMapper;
import com.neueda.leap.model.UserPii;
import java.util.UUID;

@Service
public class UserPiiService {
    private UserPiiMapper userPiiMapper;

    public UserPiiService(UserPiiMapper userPiiMapper) {
        this.userPiiMapper = userPiiMapper;
    }

    public UserPii getUserPiiById(UUID id) {
        return userPiiMapper.getUserPiiById(id);
    }

    public String getUserFirstNameById(UUID id) {

        String firstName = userPiiMapper.getUserFirstNameById(id);
        if (firstName == null || firstName.isEmpty()) {
            throw new ObjectNotFoundException("First name not found for user with id: " + id);
        }
        return firstName;
    }

    public String getUserLastNameById(UUID id) {
        String lastName = userPiiMapper.getUserLastNameById(id);
        if (lastName == null || lastName.isEmpty()) {
            throw new ObjectNotFoundException("Last name not found for user with id: " + id);
        }
        return lastName;
    }

    public String getUserEmailById(UUID id) {
        String email = userPiiMapper.getUserEmailById(id);
        if (email == null || email.isEmpty()) {
            throw new ObjectNotFoundException("Email not found for user with id: " + id);
        }
        return email;
    }

    public UUID getUserIdByEmail(String email) {
        if (email == null || email.isEmpty()) {
            throw new IllegalArgumentException("Email cannot be null or empty");
        }
        UUID userId = userPiiMapper.getUserIdByEmail(email);
        if (userId == null) {
            throw new ObjectNotFoundException("User not found for email: " + email);
        }
        return userId;
    }

    public UUID findUserIdByEmail(String email) {
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("Email cannot be null or empty");
        }
        return userPiiMapper.getUserIdByEmail(email);
    }

    public String getUserPhoneNumberById(UUID id) {
        String phoneNumber = userPiiMapper.getUserPhoneNumberById(id);
        if (phoneNumber == null || phoneNumber.isEmpty()) {
            throw new ObjectNotFoundException("Phone number not found for user with id: " + id);
        }
        // TODO: regex check
        return phoneNumber;
    }

    public UserPii insertUserPii(UserPii userPii) {
        // TODO: validate userPii before insertion
        int insertFlag = userPiiMapper.insertUserPii(userPii);
        if (insertFlag < 1) {
            throw new ObjectNotProcessedException("Failed to insert UserPii");
        }
        return userPii;
    }

    public UserPii updateUserPii(UserPii userPii) {
        // TODO: validate userPii
        int updateFlag = userPiiMapper.updateUserPii(userPii);
        if (updateFlag < 1) {
            throw new ObjectNotProcessedException("Failed to update UserPii");
        }
        return userPii;
    }

    public void deleteUserPiiById(UUID id) {
        int deleteFlag = userPiiMapper.deleteUserPiiById(id);
        if ( deleteFlag < 1 ) {
            throw new ObjectNotProcessedException("Failed to delete UserPii with id: " + id);
        }
    }

}