package com.realty.Realtymate.repository;

import com.realty.Realtymate.model.DagaguItemEntity;
import com.realty.Realtymate.model.DongPnu;
import com.realty.Realtymate.model.DongPnuEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DongPnuRepository extends JpaRepository<DongPnuEntity, String> {

    @Query(value = "SELECT pnu FROM dong_pnu WHERE dong LIKE ?1%", nativeQuery = true)
    List<String> findAllPnuByDongPrefix(String dongPrefix);

    @Query(value = "SELECT dong, pnu FROM dong_pnu WHERE dong LIKE ?1%", nativeQuery = true)
    List<DongPnu> findAllDongAndPnuByDongPrefix(String dongPrefix);

    List<DongPnuEntity> findByDongStartingWith(String dong);

}
