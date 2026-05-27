package com.realty.Realtymate.repository;

import com.realty.Realtymate.model.ApartItemEntity;
import com.realty.Realtymate.model.SanggaItemEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public interface ApartItemRepository  extends JpaRepository<ApartItemEntity, Long> {
    @Modifying
    @Transactional
    @Query(value = "INSERT IGNORE INTO apart_items (" +
            "item_id, address, dong, floor, ho, area, deal_price, deposit, " +
            "monthly_fee, management_fee, trade_type, updated_date, apart_name, agent_name" +
            ") VALUES (" +
            ":itemId, :address, :dong, :floor, :ho, :area, :dealPrice, :deposit, " +
            ":monthlyFee, :managementFee, :tradeType, :updatedDate, :apartName, :agentName" +
            ")",
            nativeQuery = true)
    int insertIgnoreApartItem(
            @Param("itemId") String itemId,
            @Param("address") String address,
            @Param("dong") String dong,
            @Param("floor") String floor,
            @Param("ho") String ho,
            @Param("area") BigDecimal area,
            @Param("dealPrice") BigDecimal dealPrice,
            @Param("deposit") BigDecimal deposit,
            @Param("monthlyFee") BigDecimal monthlyFee,
            @Param("managementFee") BigDecimal managementFee,
            @Param("tradeType") String tradeType,
            @Param("updatedDate") LocalDateTime updatedDate,
            @Param("apartName") String apartName,
            @Param("agentName") String agentName
    );
}
