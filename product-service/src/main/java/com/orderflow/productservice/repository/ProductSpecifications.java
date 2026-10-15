package com.orderflow.productservice.repository;

import com.orderflow.productservice.entity.Product;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

/**
 * Composable filters for the product search endpoint - a Specification per optional
 * criterion, combined with Specification.allOf/where in the service layer. Chosen over a
 * derived-query-method explosion (findByActiveTrueAndNameContainingAndCategory...) since the
 * combination of filters a caller supplies is genuinely optional and open to growing.
 */
public final class ProductSpecifications {

    private ProductSpecifications() {
    }

    public static Specification<Product> nameContains(String name) {
        if (!StringUtils.hasText(name)) {
            return null;
        }
        String pattern = "%" + name.toLowerCase() + "%";
        return (root, query, cb) -> cb.like(cb.lower(root.get("name")), pattern);
    }

    public static Specification<Product> hasCategory(String category) {
        if (!StringUtils.hasText(category)) {
            return null;
        }
        return (root, query, cb) -> cb.equal(cb.lower(root.get("category")), category.toLowerCase());
    }

    public static Specification<Product> isActive(boolean active) {
        return (root, query, cb) -> cb.equal(root.get("active"), active);
    }
}
