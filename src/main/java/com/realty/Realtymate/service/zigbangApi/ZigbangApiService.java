package com.realty.Realtymate.service.zigbangApi;

import com.realty.Realtymate.model.AgentCustomer;
import com.realty.Realtymate.model.DagaguItemEntity;

import java.util.List;

public interface ZigbangApiService {
    void alertDagaguNewAd(List<DagaguItemEntity> previousItemList, AgentCustomer agentCustomer);
}
