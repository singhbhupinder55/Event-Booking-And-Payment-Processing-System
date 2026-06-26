package com.portfolio.eventbooking.repository;

import com.portfolio.eventbooking.domain.IdempotencyKey;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface IdempotencyKeyRepository extends JpaRepository<IdempotencyKey, Long> {

    Optional<IdempotencyKey> findByIdempotencyKeyAndRequestPath(String idempotencyKey, String requestPath);
}
