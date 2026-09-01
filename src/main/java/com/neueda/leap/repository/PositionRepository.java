package com.neueda.leap.repository;

import com.neueda.leap.model.Position;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface PositionRepository extends JpaRepository<Position, UUID> {

    /**
     * Find all positions for a given user (their portfolio holdings).
     * Spring Data JPA derives the query from the method name:
     * SELECT p FROM Position p WHERE p.user.userId = :userId
     */
    List<Position> findByUserUserId(UUID userId);
}

