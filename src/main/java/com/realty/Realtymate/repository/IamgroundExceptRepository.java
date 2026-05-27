package com.realty.Realtymate.repository;

import com.realty.Realtymate.model.IamgroundExceptEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;

@Repository
public interface IamgroundExceptRepository extends JpaRepository<IamgroundExceptEntity, String> {
    @Query("SELECT i.address FROM IamgroundExceptEntity i")
    ArrayList<String> findAllAddress();

}
