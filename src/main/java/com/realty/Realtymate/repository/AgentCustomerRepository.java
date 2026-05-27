package com.realty.Realtymate.repository;

import com.realty.Realtymate.model.AgentCustomer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AgentCustomerRepository extends JpaRepository<AgentCustomer, Long> {
    List<AgentCustomer> findAll();

    List<AgentCustomer> findByKindIn(List<String> kinds);
}
