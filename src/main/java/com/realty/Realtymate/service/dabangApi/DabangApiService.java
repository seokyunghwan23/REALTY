package com.realty.Realtymate.service.dabangApi;

import com.realty.Realtymate.model.AgentCustomer;
import com.realty.Realtymate.model.DagaguItemEntity;

import java.util.List;

public interface DabangApiService {
    void alertDagaguNewAd(List<DagaguItemEntity> previousItemList, AgentCustomer agentCustomer);
}
