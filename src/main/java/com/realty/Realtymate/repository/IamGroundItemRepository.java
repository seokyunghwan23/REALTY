package com.realty.Realtymate.repository;

import com.realty.Realtymate.model.IamGroundItemsEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Repository
public interface IamGroundItemRepository extends JpaRepository<IamGroundItemsEntity, String> {

    @Query("SELECT i.itemId FROM IamGroundItemsEntity i")
    ArrayList<String> findAllItemIds();

    @Transactional
    default IamGroundItemsEntity saveItemId(String itemId) {
        IamGroundItemsEntity entity = new IamGroundItemsEntity();
        entity.setItemId(itemId);  // itemId만 설정 (address 없음)
        return save(entity);
    }

    List<IamGroundItemsEntity> findByItemId(String itemId);
}
