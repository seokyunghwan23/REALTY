package com.realty.Realtymate.service.RepositoryService;

import com.realty.Realtymate.model.AgentCustomer;
import com.realty.Realtymate.model.DagaguItemEntity;

import java.util.List;

public interface DagaguItemService {
    List<DagaguItemEntity> getItems(AgentCustomer agentCustomer);
    void addDagaguItem(String itemId, String agent, String platform, String kind);


}
