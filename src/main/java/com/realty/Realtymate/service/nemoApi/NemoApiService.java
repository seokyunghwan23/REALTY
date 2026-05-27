package com.realty.Realtymate.service.nemoApi;

import com.realty.Realtymate.model.AgentCustomer;
import com.realty.Realtymate.model.SanggaItemEntity;

import java.util.List;

public interface NemoApiService {
    void alertSanggaNewAd(List<SanggaItemEntity> previousItemList, AgentCustomer agentCustomer);
}
