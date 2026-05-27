package com.realty.Realtymate.service.RepositoryService;

import com.realty.Realtymate.model.SanggaItemDto;
import com.realty.Realtymate.model.SanggaServeItemDto;
import com.realty.Realtymate.model.SanggaServeItemEntity;

import java.util.Optional;

public interface SanggaServieItemService {
    boolean saveOrUpdateSanggaServeItem(SanggaItemDto newItem);

    Optional<SanggaServeItemEntity> findExistingSanggaServeItem(SanggaItemDto newItem);
}
