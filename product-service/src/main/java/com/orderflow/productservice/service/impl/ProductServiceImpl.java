package com.orderflow.productservice.service.impl;

import com.orderflow.common.exception.ConflictException;
import com.orderflow.common.exception.ResourceNotFoundException;
import com.orderflow.productservice.dto.ProductRequest;
import com.orderflow.productservice.dto.ProductResponse;
import com.orderflow.productservice.entity.Product;
import com.orderflow.productservice.mapper.ProductMapper;
import com.orderflow.productservice.repository.ProductRepository;
import com.orderflow.productservice.repository.ProductSpecifications;
import com.orderflow.productservice.service.interfaces.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProductServiceImpl implements ProductService {

    static final String PRODUCT_CACHE = "products";

    private final ProductRepository productRepository;
    private final ProductMapper productMapper;

    @Override
    @Transactional
    public ProductResponse create(ProductRequest request) {
        if (productRepository.existsBySku(request.sku())) {
            throw new ConflictException("A product with SKU '%s' already exists".formatted(request.sku()));
        }
        Product product = productMapper.toEntity(request);
        productRepository.save(product);
        return productMapper.toResponse(product);
    }

    @Override
    @Transactional
    @CacheEvict(cacheNames = PRODUCT_CACHE, key = "#p0")
    public ProductResponse update(UUID id, ProductRequest request) {
        Product product = findOrThrow(id);
        if (productRepository.existsBySkuAndIdNot(request.sku(), id)) {
            throw new ConflictException("A product with SKU '%s' already exists".formatted(request.sku()));
        }
        productMapper.updateEntityFromRequest(request, product);
        return productMapper.toResponse(product);
    }

    @Override
    @Transactional
    @CacheEvict(cacheNames = PRODUCT_CACHE, key = "#p0")
    public void delete(UUID id) {
        Product product = findOrThrow(id);
        product.setActive(false);
    }

    @Override
    @Cacheable(cacheNames = PRODUCT_CACHE, key = "#p0")
    public ProductResponse getById(UUID id) {
        return productMapper.toResponse(findOrThrow(id));
    }

    @Override
    public Page<ProductResponse> search(String name, String category, boolean activeOnly, Pageable pageable) {
        Specification<Product> spec = Specification.where(ProductSpecifications.nameContains(name))
                .and(ProductSpecifications.hasCategory(category));
        if (activeOnly) {
            spec = spec.and(ProductSpecifications.isActive(true));
        }
        return productRepository.findAll(spec, pageable).map(productMapper::toResponse);
    }

    private Product findOrThrow(UUID id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product", id));
    }
}
