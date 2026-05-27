package com.realty.Realtymate.repository;

import com.realty.Realtymate.model.SanggaItemEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;


@Repository
public interface SanggaItemRepository extends JpaRepository<SanggaItemEntity, Long> {
    SanggaItemEntity findByItemId(String itemId);  // 특정 item_id로 조회

    List<SanggaItemEntity> findByRegionName(String regionName);

    List<SanggaItemEntity> findByPlatform(String platform);

    List<SanggaItemEntity> findAll();

    @Modifying
    @Transactional
    @Query(value = "INSERT IGNORE INTO sangga_items (item_id, address, floor, area, deposit, monthly_fee, " +
            "management_fee, longitude, latitude, updated_date, platform, region_name, agent_name) " +
            "VALUES (:itemId, :address, :floor, :area, :deposit, :monthlyFee, :managementFee, :longitude, :latitude, :updatedDate, :platform, :regionName, :agentName)",
            nativeQuery = true)
    int insertIgnoreSanggaItem(
            String itemId, String address, Integer floor, BigDecimal area, BigDecimal deposit,
            BigDecimal monthlyFee, BigDecimal managementFee, Double longitude, Double latitude,
            LocalDateTime updatedDate, String platform, String regionName, String agentName
    );

    //    @Query(value = "SELECT * FROM sangga_items WHERE item_id > :lastId", nativeQuery = true)
//    List<SanggaItemEntity> findNewListings(@Param("lastId") String lastId);

    @Query(value = """
                SELECT *
                FROM sangga_items
                WHERE CAST(item_id AS UNSIGNED) <= CAST(:lastId AS UNSIGNED)
                  AND platform = '네이버';
                
            """, nativeQuery = true)
    List<SanggaItemEntity> findNewListings(@Param("lastId") String lastId);


    @Query(value = """
                SELECT item_id 
                FROM sangga_items 
                ORDER BY CAST(SUBSTRING(item_id, 3) AS UNSIGNED) DESC 
                LIMIT 1
            """, nativeQuery = true)
    String findMaxItemId();


}
