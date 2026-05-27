package com.realty.Realtymate.repository;

import com.realty.Realtymate.model.DagaguItemEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Repository
public interface DagaguItemRepository extends JpaRepository<DagaguItemEntity, Long> {

    // agent, platform, kind로 조회하는 메서드
    List<DagaguItemEntity> findByAgentAndKind(String agent, String kind);

    @Transactional
    default DagaguItemEntity insertItem(String itemId, String agent, String platform, String kind) {
        DagaguItemEntity item = new DagaguItemEntity(null, itemId, agent, platform, kind);
        return save(item);
    }
    @Transactional
    @Modifying
    @Query(value = "INSERT IGNORE INTO dagagu_items (item_id, agent, platform, kind) " +
            "VALUES (:itemId, :agent, :platform, :kind)", nativeQuery = true)
    void insertItemIfNotExists(@Param("itemId") String itemId,
                               @Param("agent") String agent,
                               @Param("platform") String platform,
                               @Param("kind") String kind);

    @Transactional
    @Modifying
    @Query(value = "INSERT INTO dagagu_items (item_id, agent, platform, kind) " +
            "VALUES (:itemId, :agent, :platform, :kind) " +
            "ON DUPLICATE KEY UPDATE " +
            "agent = VALUES(agent), " +
            "platform = VALUES(platform), " +
            "kind = VALUES(kind)", nativeQuery = true)
    void upsertItem(@Param("itemId") String itemId,
                    @Param("agent") String agent,
                    @Param("platform") String platform,
                    @Param("kind") String kind);


}

