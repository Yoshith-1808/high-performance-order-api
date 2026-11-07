package com.yoshith.orderapi.service;

import com.yoshith.orderapi.dto.*;
import com.yoshith.orderapi.entity.*;
import com.yoshith.orderapi.exception.*;
import com.yoshith.orderapi.repository.OrderRepository;
import com.yoshith.orderapi.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderServiceImpl implements OrderService {

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final IdempotencyService idempotencyService;

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    @CacheEvict(value = "products", allEntries = true)
    public OrderResponse createOrder(CreateOrderRequest request, String idempotencyKey) {
        log.info("Processing order creation for customer [{}] with Idempotency-Key [{}]", 
                request.getCustomerEmail(), idempotencyKey);

        // 1. Idempotency Check with Redis
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            boolean isNewRequest = idempotencyService.acquireLock(idempotencyKey);
            if (!isNewRequest) {
                // If order was already processed with this key, return the existing order
                return orderRepository.findByIdempotencyKey(idempotencyKey)
                        .map(this::mapToOrderResponse)
                        .orElseThrow(() -> new DuplicateOrderException(
                                "An order with idempotency key [" + idempotencyKey + "] is currently being processed."));
            }
        }

        try {
            // 2. Validate stock and perform optimistic updates
            Order order = Order.builder()
                    .orderTrackingNumber(UUID.randomUUID().toString())
                    .idempotencyKey(idempotencyKey)
                    .customerEmail(request.getCustomerEmail())
                    .status(OrderStatus.CONFIRMED)
                    .totalAmount(BigDecimal.ZERO)
                    .build();

            BigDecimal orderTotal = BigDecimal.ZERO;
            List<OrderItem> items = new ArrayList<>();

            for (OrderItemRequest itemReq : request.getItems()) {
                Product product = productRepository.findById(itemReq.getProductId())
                        .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + itemReq.getProductId()));

                if (product.getStockQuantity() < itemReq.getQuantity()) {
                    throw new InsufficientStockException(String.format(
                            "Insufficient stock for product [%s]. Available: %d, Requested: %d",
                            product.getName(), product.getStockQuantity(), itemReq.getQuantity()));
                }

                // Decrement stock (Optimistic Locking via @Version validates concurrency at commit)
                product.setStockQuantity(product.getStockQuantity() - itemReq.getQuantity());
                productRepository.save(product);

                BigDecimal subtotal = product.getPrice().multiply(BigDecimal.valueOf(itemReq.getQuantity()));
                orderTotal = orderTotal.add(subtotal);

                OrderItem orderItem = OrderItem.builder()
                        .product(product)
                        .quantity(itemReq.getQuantity())
                        .unitPrice(product.getPrice())
                        .subtotal(subtotal)
                        .build();

                order.addItem(orderItem);
            }

            order.setTotalAmount(orderTotal);
            Order savedOrder = orderRepository.save(order);

            // 3. Mark idempotency success in Redis
            if (idempotencyKey != null && !idempotencyKey.isBlank()) {
                idempotencyService.markSuccess(idempotencyKey, savedOrder.getOrderTrackingNumber());
            }

            log.info("Order successfully placed! Tracking: [{}] Total: ${}", 
                    savedOrder.getOrderTrackingNumber(), savedOrder.getTotalAmount());

            return mapToOrderResponse(savedOrder);

        } catch (Exception ex) {
            // If processing fails, release idempotency lock
            if (idempotencyKey != null && !idempotencyKey.isBlank()) {
                idempotencyService.releaseLock(idempotencyKey);
            }
            throw ex;
        }
    }

    @Override
    @Cacheable(value = "orders", key = "#trackingNumber")
    @Transactional(readOnly = true)
    public OrderResponse getOrderByTrackingNumber(String trackingNumber) {
        log.info("Fetching order [{}] from database (Cache Miss)", trackingNumber);
        return orderRepository.findByOrderTrackingNumber(trackingNumber)
                .map(this::mapToOrderResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found with tracking number: " + trackingNumber));
    }

    @Override
    @Transactional(readOnly = true)
    public List<OrderResponse> getOrdersByCustomer(String customerEmail) {
        return orderRepository.findByCustomerEmailOrderByCreatedAtDesc(customerEmail).stream()
                .map(this::mapToOrderResponse)
                .collect(Collectors.toList());
    }

    private OrderResponse mapToOrderResponse(Order order) {
        List<OrderItemResponse> itemResponses = order.getItems().stream()
                .map(item -> OrderItemResponse.builder()
                        .productId(item.getProduct().getId())
                        .productName(item.getProduct().getName())
                        .sku(item.getProduct().getSku())
                        .quantity(item.getQuantity())
                        .unitPrice(item.getUnitPrice())
                        .subtotal(item.getSubtotal())
                        .build())
                .collect(Collectors.toList());

        return OrderResponse.builder()
                .orderTrackingNumber(order.getOrderTrackingNumber())
                .customerEmail(order.getCustomerEmail())
                .status(order.getStatus())
                .totalAmount(order.getTotalAmount())
                .items(itemResponses)
                .createdAt(order.getCreatedAt())
                .build();
    }
}
