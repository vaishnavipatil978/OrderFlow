package com.orderflow.productservice.service;

import com.orderflow.common.exception.ConflictException;
import com.orderflow.common.exception.ResourceNotFoundException;
import com.orderflow.productservice.dto.ProductRequest;
import com.orderflow.productservice.dto.ProductResponse;
import com.orderflow.productservice.entity.Product;
import com.orderflow.productservice.mapper.ProductMapper;
import com.orderflow.productservice.mapper.ProductMapperImpl;
import com.orderflow.productservice.repository.ProductRepository;
import com.orderflow.productservice.service.impl.ProductServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductServiceImplTest {

    @Mock
    private ProductRepository productRepository;

    private final ProductMapper productMapper = new ProductMapperImpl();

    private ProductServiceImpl productService;

    @BeforeEach
    void setUp() {
        productService = new ProductServiceImpl(productRepository, productMapper);
    }

    @Test
    void createSavesProductWhenSkuIsUnique() {
        ProductRequest request = new ProductRequest("Widget", "A widget", "WID-1", BigDecimal.valueOf(9.99), "Tools");
        when(productRepository.existsBySku("WID-1")).thenReturn(false);

        ProductResponse response = productService.create(request);

        verify(productRepository).save(any(Product.class));
        assertThat(response.name()).isEqualTo("Widget");
        assertThat(response.sku()).isEqualTo("WID-1");
        assertThat(response.active()).isTrue();
    }

    @Test
    void createRejectsDuplicateSku() {
        ProductRequest request = new ProductRequest("Widget", "A widget", "WID-1", BigDecimal.valueOf(9.99), "Tools");
        when(productRepository.existsBySku("WID-1")).thenReturn(true);

        assertThatThrownBy(() -> productService.create(request)).isInstanceOf(ConflictException.class);
        verify(productRepository, org.mockito.Mockito.never()).save(any());
    }

    @Test
    void updateModifiesExistingProduct() {
        UUID id = UUID.randomUUID();
        Product existing = productWithSku("WID-1");
        when(productRepository.findById(id)).thenReturn(Optional.of(existing));
        when(productRepository.existsBySkuAndIdNot("WID-1", id)).thenReturn(false);
        ProductRequest request = new ProductRequest("Widget v2", "Updated", "WID-1", BigDecimal.valueOf(12.50), "Tools");

        ProductResponse response = productService.update(id, request);

        assertThat(response.name()).isEqualTo("Widget v2");
        assertThat(response.price()).isEqualByComparingTo("12.50");
    }

    @Test
    void updateRejectsSkuTakenByAnotherProduct() {
        UUID id = UUID.randomUUID();
        when(productRepository.findById(id)).thenReturn(Optional.of(productWithSku("WID-1")));
        when(productRepository.existsBySkuAndIdNot("WID-2", id)).thenReturn(true);
        ProductRequest request = new ProductRequest("Widget", "desc", "WID-2", BigDecimal.TEN, "Tools");

        assertThatThrownBy(() -> productService.update(id, request)).isInstanceOf(ConflictException.class);
    }

    @Test
    void updateRejectsUnknownId() {
        UUID id = UUID.randomUUID();
        when(productRepository.findById(id)).thenReturn(Optional.empty());
        ProductRequest request = new ProductRequest("Widget", "desc", "WID-1", BigDecimal.TEN, "Tools");

        assertThatThrownBy(() -> productService.update(id, request)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void deleteDeactivatesRatherThanRemoving() {
        UUID id = UUID.randomUUID();
        Product existing = productWithSku("WID-1");
        when(productRepository.findById(id)).thenReturn(Optional.of(existing));

        productService.delete(id);

        assertThat(existing.isActive()).isFalse();
        verify(productRepository, org.mockito.Mockito.never()).delete(any(Product.class));
    }

    @Test
    void deleteRejectsUnknownId() {
        UUID id = UUID.randomUUID();
        when(productRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productService.delete(id)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getByIdReturnsMappedProduct() {
        UUID id = UUID.randomUUID();
        when(productRepository.findById(id)).thenReturn(Optional.of(productWithSku("WID-1")));

        assertThat(productService.getById(id).sku()).isEqualTo("WID-1");
    }

    @Test
    void getByIdRejectsUnknownId() {
        UUID id = UUID.randomUUID();
        when(productRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productService.getById(id)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void searchDelegatesToRepositoryWithSpecification() {
        Pageable pageable = Pageable.unpaged();
        Page<Product> page = new PageImpl<>(List.of(productWithSku("WID-1")));
        when(productRepository.findAll(org.mockito.ArgumentMatchers.<Specification<Product>>any(), any(Pageable.class)))
                .thenReturn(page);

        Page<ProductResponse> result = productService.search("widget", "Tools", true, pageable);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).sku()).isEqualTo("WID-1");
    }

    private Product productWithSku(String sku) {
        Product product = new Product();
        product.setName("Widget");
        product.setDescription("A widget");
        product.setSku(sku);
        product.setPrice(BigDecimal.valueOf(9.99));
        product.setCategory("Tools");
        return product;
    }
}
