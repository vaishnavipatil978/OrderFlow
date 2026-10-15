package com.orderflow.productservice.mapper;

import com.orderflow.productservice.dto.ProductRequest;
import com.orderflow.productservice.dto.ProductResponse;
import com.orderflow.productservice.entity.Product;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

@Mapper(componentModel = "spring", nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
public interface ProductMapper {

    ProductResponse toResponse(Product product);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "active", ignore = true)
    void updateEntityFromRequest(ProductRequest request, @MappingTarget Product product);

    default Product toEntity(ProductRequest request) {
        Product product = new Product();
        updateEntityFromRequest(request, product);
        return product;
    }
}
