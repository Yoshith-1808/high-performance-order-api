package com.yoshith.orderapi.service;

import com.yoshith.orderapi.dto.ProductDto;
import com.yoshith.orderapi.entity.Product;

import java.util.List;

public interface ProductService {
    ProductDto getProductById(Long id);
    List<ProductDto> getAllProducts();
    ProductDto createProduct(ProductDto productDto);
    Product getProductEntity(Long id);
}
