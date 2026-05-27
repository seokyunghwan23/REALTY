package com.realty.Realtymate.service.RepositoryService;

import com.realty.Realtymate.model.AgentCustomer;
import com.realty.Realtymate.model.DagaguItemEntity;
import com.realty.Realtymate.repository.DagaguItemRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class DagaguItemServiceImpl implements DagaguItemService{

    private final DagaguItemRepository dagaguItemRepository;

    public DagaguItemServiceImpl(DagaguItemRepository dagaguItemRepository) {
        this.dagaguItemRepository = dagaguItemRepository;
    }
    @Override
    public List<DagaguItemEntity> getItems(AgentCustomer agentCustomer){
        return dagaguItemRepository.findByAgentAndKind(agentCustomer.getAgentName(), agentCustomer.getKind());
    }

    @Override
    public void addDagaguItem(String itemId, String agent, String platform, String kind) {
//        dagaguItemRepository.insertItemIfNotExists(itemId, agent, platform, kind);
        dagaguItemRepository.upsertItem(itemId, agent, platform, kind);
    }
}