package com.realty.Realtymate.repository;

import com.realty.Realtymate.model.CustomerInfoEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Repository
public interface CustomerInfoRepository extends JpaRepository<CustomerInfoEntity, Long> {
    List<CustomerInfoEntity> findByAlertTrue(); // alert가 true인 손님만 가져오기

    @Modifying
    @Transactional
    @Query("UPDATE CustomerInfoEntity c SET c.lastId = :lastId WHERE c.id = :customerId")
    void updateLastId(@Param("customerId") Long customerId, @Param("lastId") String lastId);
}