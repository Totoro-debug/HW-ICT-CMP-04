package com.ecommerce.product.repository;

import com.ecommerce.product.entity.ProductSpuTag;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ProductSpuTagRepository extends JpaRepository<ProductSpuTag, Long> {
}
