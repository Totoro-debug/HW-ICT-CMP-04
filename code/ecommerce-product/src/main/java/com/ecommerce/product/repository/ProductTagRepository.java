package com.ecommerce.product.repository;

import com.ecommerce.product.entity.ProductTag;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface ProductTagRepository extends JpaRepository<ProductTag, Long> {

    @Query("select t.id from ProductTag t where lower(t.name) in :names")
    List<Long> findIdsByNames(@Param("names") Collection<String> names);
}
