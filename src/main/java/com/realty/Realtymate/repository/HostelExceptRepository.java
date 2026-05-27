package com.realty.Realtymate.repository;

import com.realty.Realtymate.model.HostelExceptEntity;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;

@Repository
public interface HostelExceptRepository extends JpaRepository<HostelExceptEntity, String> {
    @Query("SELECT i.address FROM HostelExceptEntity i")
    ArrayList<String> findAllAddress();



}
