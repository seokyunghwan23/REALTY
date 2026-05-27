package com.realty.Realtymate.service.peterpanzApi;

import com.realty.Realtymate.model.AgentCustomer;
import com.realty.Realtymate.model.DagaguItemEntity;

import java.util.List;

public interface PeterpanzApiService {
    void alertDagaguNewAd(List<DagaguItemEntity> previousItemList, AgentCustomer agentCustomer);
}
