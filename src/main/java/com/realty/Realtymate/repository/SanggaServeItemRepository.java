package com.realty.Realtymate.repository;

import com.realty.Realtymate.model.SanggaItemEntity;
import com.realty.Realtymate.model.SanggaServeItemEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Repository
public interface SanggaServeItemRepository extends JpaRepository<SanggaServeItemEntity, Long> {
    Optional<SanggaServeItemEntity> findByAddressAndFloorAndDepositAndMonthlyFeeAndManagementFee(
            String address, int floor, BigDecimal deposit, BigDecimal monthlyFee, BigDecimal managementFee
    );
        // 모든 매물 가져오기 (JPA 기본 제공 메서드)
        List<SanggaServeItemEntity> findAll();

}
