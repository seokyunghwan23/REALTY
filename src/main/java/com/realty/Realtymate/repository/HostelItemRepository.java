package com.realty.Realtymate.repository;

import com.realty.Realtymate.model.HostelItemsEntity;
import com.realty.Realtymate.model.IamGroundItemsEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Repository
public interface HostelItemRepository extends JpaRepository<HostelItemsEntity, String> {

    @Query("SELECT i.itemId FROM HostelItemsEntity i")
    ArrayList<String> findAllItemIds();

    @Transactional
    default HostelItemsEntity saveItemId(String itemId) {
        HostelItemsEntity entity = new HostelItemsEntity();
        entity.setItemId(itemId);  // itemId만 설정 (address 없음)
        return save(entity);
    }

    List<HostelItemsEntity> findByItemId(String itemId);
}
