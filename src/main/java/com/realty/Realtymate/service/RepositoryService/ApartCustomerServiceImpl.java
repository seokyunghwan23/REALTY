package com.realty.Realtymate.service.RepositoryService;

import com.realty.Realtymate.model.ApartCustomer;
import com.realty.Realtymate.repository.ApartCustomerRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ApartCustomerServiceImpl implements ApartCustomerService{

    private final ApartCustomerRepository apartCustomerRepository;

    public ApartCustomerServiceImpl(ApartCustomerRepository apartCustomerRepository) {
        this.apartCustomerRepository = apartCustomerRepository;
    }

    @Override
    public List<ApartCustomer> getAllCustomers() {
        return apartCustomerRepository.findAll();
    }

}
