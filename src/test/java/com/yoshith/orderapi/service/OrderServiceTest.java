package com.yoshith.orderapi.service;

import com.yoshith.orderapi.dto.CreateOrderRequest;
import com.yoshith.orderapi.dto.OrderItemRequest;
import com.yoshith.orderapi.dto.OrderResponse;
import com.yoshith.orderapi.entity.Order;
import com.yoshith.orderapi.entity.OrderStatus;
import com.yoshith.orderapi.entity.Product;
import com.yoshith.orderapi.exception.DuplicateOrderException;
import com.yoshith.orderapi.exception.InsufficientStockException;
import com.yoshith.orderapi.repository.OrderRepository;
import com.yoshith.orderapi.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private IdempotencyService idempotencyService;

    @InjectMocks
    private OrderServiceImpl orderService;

    private Product testProduct;

    @BeforeEach
    void setUp() {
        testProduct = Product.builder()
                .id(1L)
                .name("Wireless Noise-Cancelling Headphones")
                .sku("WNC-HP-001")
                .price(new BigDecimal("199.99"))
                .stockQuantity(10)
                .version(1L)
                .build();
    }

    @Test
    @DisplayName("Should successfully place order and decrement inventory")
    void testCreateOrder_Success() {
        CreateOrderRequest request = CreateOrderRequest.builder()
                .customerEmail("customer@example.com")
                .items(List.of(new OrderItemRequest(1L, 2)))
                .build();

        when(idempotencyService.acquireLock("key-123")).thenReturn(true);
        when(productRepository.findById(1L)).thenReturn(Optional.of(testProduct));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> {
            Order o = invocation.getArgument(0);
            o.setId(100L);
            return o;
        });

        OrderResponse response = orderService.createOrder(request, "key-123");

        assertNotNull(response);
        assertEquals(OrderStatus.CONFIRMED, response.getStatus());
        assertEquals("customer@example.com", response.getCustomerEmail());
        assertEquals(new BigDecimal("399.98"), response.getTotalAmount());
        assertEquals(8, testProduct.getStockQuantity()); // Stock decremented 10 -> 8
        verify(productRepository, times(1)).save(testProduct);
        verify(idempotencyService, times(1)).markSuccess(eq("key-123"), any());
    }

    @Test
    @DisplayName("Should throw InsufficientStockException when requested quantity exceeds available stock")
    void testCreateOrder_InsufficientStock() {
        CreateOrderRequest request = CreateOrderRequest.builder()
                .customerEmail("buyer@example.com")
                .items(List.of(new OrderItemRequest(1L, 50))) // Requests 50, but stock is 10
                .build();

        when(idempotencyService.acquireLock("key-stock-err")).thenReturn(true);
        when(productRepository.findById(1L)).thenReturn(Optional.of(testProduct));

        assertThrows(InsufficientStockException.class, () -> orderService.createOrder(request, "key-stock-err"));

        verify(orderRepository, never()).save(any());
        verify(idempotencyService, times(1)).releaseLock("key-stock-err");
    }

    @Test
    @DisplayName("Should reject concurrent duplicate order submission with same idempotency key")
    void testCreateOrder_DuplicateKeyInFlight() {
        CreateOrderRequest request = CreateOrderRequest.builder()
                .customerEmail("buyer@example.com")
                .items(List.of(new OrderItemRequest(1L, 1)))
                .build();

        // Lock acquisition returns false (key already being processed)
        when(idempotencyService.acquireLock("duplicate-key")).thenReturn(false);
        when(orderRepository.findByIdempotencyKey("duplicate-key")).thenReturn(Optional.empty());

        assertThrows(DuplicateOrderException.class, () -> orderService.createOrder(request, "duplicate-key"));
        verify(productRepository, never()).findById(any());
    }
}
