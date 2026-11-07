package com.yoshith.orderapi.controller;

import com.yoshith.orderapi.dto.CreateOrderRequest;
import com.yoshith.orderapi.dto.OrderResponse;
import com.yoshith.orderapi.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
@Tag(name = "Orders", description = "High-performance idempotent order placement and retrieval APIs")
public class OrderController {

    private final OrderService orderService;

    @PostMapping
    @Operation(summary = "Place a new order with Redis-backed idempotency protection")
    public ResponseEntity<OrderResponse> placeOrder(
            @Parameter(description = "Client-supplied idempotency key (UUID) to guarantee strictly-once processing")
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody CreateOrderRequest request) {
        OrderResponse response = orderService.createOrder(request, idempotencyKey);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{trackingNumber}")
    @Operation(summary = "Retrieve order details by tracking number (cached via Redis)")
    public ResponseEntity<OrderResponse> getOrderByTrackingNumber(@PathVariable String trackingNumber) {
        return ResponseEntity.ok(orderService.getOrderByTrackingNumber(trackingNumber));
    }

    @GetMapping("/customer/{customerEmail}")
    @Operation(summary = "Retrieve all orders placed by customer email")
    public ResponseEntity<List<OrderResponse>> getOrdersByCustomer(@PathVariable String customerEmail) {
        return ResponseEntity.ok(orderService.getOrdersByCustomer(customerEmail));
    }
}
