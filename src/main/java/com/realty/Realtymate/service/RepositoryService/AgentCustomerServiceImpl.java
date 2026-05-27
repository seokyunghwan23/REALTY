package com.realty.Realtymate.service.RepositoryService;

import com.realty.Realtymate.model.AgentCustomer;
import com.realty.Realtymate.repository.AgentCustomerRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AgentCustomerServiceImpl implements AgentCustomerService {

    private final AgentCustomerRepository agentCustomerRepository;

    public AgentCustomerServiceImpl(AgentCustomerRepository agentCustomerRepository) {
        this.agentCustomerRepository = agentCustomerRepository;
    }
    @Override
    public List<AgentCustomer> getAllCustomers(List<String> list) {
        return agentCustomerRepository.findByKindIn(list);
    }
}
