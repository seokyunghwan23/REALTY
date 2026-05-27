package com.realty.Realtymate.repository;

import com.realty.Realtymate.model.ApartCustomer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ApartCustomerRepository extends JpaRepository<ApartCustomer, Long> {
    List<ApartCustomer> findAll();

}
