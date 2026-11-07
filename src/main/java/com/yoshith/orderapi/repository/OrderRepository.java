package com.yoshith.orderapi.repository;

import com.yoshith.orderapi.entity.Order;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {

    @EntityGraph(attributePaths = {"items", "items.product"})
    Optional<Order> findByOrderTrackingNumber(String trackingNumber);

    Optional<Order> findByIdempotencyKey(String idempotencyKey);

    List<Order> findByCustomerEmailOrderByCreatedAtDesc(String customerEmail);
}
