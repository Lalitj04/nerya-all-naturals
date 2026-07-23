package com.nerya.neryaallnaturals.repository.spec;

import com.nerya.neryaallnaturals.entity.Product;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Composable {@link Specification}s for the public product search
 * ({@code GET /api/products?q=&categoryId=&minPrice=&maxPrice=&inStock=}). Every
 * specification is combined with AND, and results are always constrained to active
 * products so the storefront never surfaces soft-deleted rows.
 */
public final class ProductSpecifications {

    private ProductSpecifications() {
    }

    public static Specification<Product> activeMatching(String q, Long categoryId,
                                                        BigDecimal minPrice, BigDecimal maxPrice,
                                                        Boolean inStock) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            predicates.add(cb.isTrue(root.get("isActive")));

            if (q != null && !q.isBlank()) {
                String like = "%" + q.trim().toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("name")), like),
                        cb.like(cb.lower(root.get("shortDescription")), like),
                        cb.like(cb.lower(root.get("longDescription")), like)));
            }
            if (categoryId != null) {
                predicates.add(cb.equal(root.get("category").get("id"), categoryId));
            }
            if (minPrice != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("sellingPrice"), minPrice));
            }
            if (maxPrice != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("sellingPrice"), maxPrice));
            }
            if (inStock != null) {
                predicates.add(cb.equal(root.get("inStock"), inStock));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
