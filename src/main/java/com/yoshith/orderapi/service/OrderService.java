package com.yoshith.orderapi.service;

import com.yoshith.orderapi.dto.CreateOrderRequest;
import com.yoshith.orderapi.dto.OrderResponse;

import java.util.List;

public interface OrderService {
    OrderResponse createOrder(CreateOrderRequest request, String idempotencyKey);
    OrderResponse getOrderByTrackingNumber(String trackingNumber);
    List<OrderResponse> getOrdersByCustomer(String customerEmail);
}
