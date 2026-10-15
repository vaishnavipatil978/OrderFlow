package com.orderflow.productservice.service.interfaces;

import com.orderflow.productservice.dto.ProductRequest;
import com.orderflow.productservice.dto.ProductResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface ProductService {

    ProductResponse create(ProductRequest request);

    ProductResponse update(UUID id, ProductRequest request);

    void delete(UUID id);

    ProductResponse getById(UUID id);

    Page<ProductResponse> search(String name, String category, boolean activeOnly, Pageable pageable);
}
