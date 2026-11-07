package com.yoshith.orderapi.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yoshith.orderapi.dto.CreateOrderRequest;
import com.yoshith.orderapi.dto.OrderItemRequest;
import com.yoshith.orderapi.dto.OrderResponse;
import com.yoshith.orderapi.entity.OrderStatus;
import com.yoshith.orderapi.service.OrderService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(OrderController.class)
class OrderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private OrderService orderService;

    @Test
    @DisplayName("POST /api/v1/orders - Should return 201 Created with tracking number")
    void testPlaceOrder_Success() throws Exception {
        CreateOrderRequest request = CreateOrderRequest.builder()
                .customerEmail("alex@example.com")
                .items(List.of(new OrderItemRequest(1L, 1)))
                .build();

        OrderResponse response = OrderResponse.builder()
                .orderTrackingNumber("ord-uuid-999")
                .customerEmail("alex@example.com")
                .status(OrderStatus.CONFIRMED)
                .totalAmount(new BigDecimal("149.99"))
                .createdAt(LocalDateTime.now())
                .items(List.of())
                .build();

        when(orderService.createOrder(any(CreateOrderRequest.class), eq("idemp-999"))).thenReturn(response);

        mockMvc.perform(post("/api/v1/orders")
                .header("Idempotency-Key", "idemp-999")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.orderTrackingNumber").value("ord-uuid-999"))
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.totalAmount").value(149.99));
    }

    @Test
    @DisplayName("POST /api/v1/orders - Should return 400 Bad Request when validation fails")
    void testPlaceOrder_ValidationFailure() throws Exception {
        CreateOrderRequest invalidRequest = CreateOrderRequest.builder()
                .customerEmail("not-an-email") // Invalid email
                .items(List.of()) // Empty items
                .build();

        mockMvc.perform(post("/api/v1/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Validation Failed"));
    }
}
