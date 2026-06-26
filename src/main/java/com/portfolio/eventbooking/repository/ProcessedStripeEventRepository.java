package com.portfolio.eventbooking.repository;

import com.portfolio.eventbooking.domain.ProcessedStripeEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ProcessedStripeEventRepository extends JpaRepository<ProcessedStripeEvent, Long> {

    Optional<ProcessedStripeEvent> findByStripeEventId(String stripeEventId);

    boolean existsByStripeEventId(String stripeEventId);
}
