package com.realty.Realtymate.service.RepositoryService;

import com.realty.Realtymate.model.*;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface ItemInfoService {
    List<ApartItemEntity> getApartItems();

    List<SanggaItemEntity> getItems();

    List<SanggaItemEntity> getNaverItems();

    @Transactional
    void saveSanggaItem(SanggaItemDto sanggaItemDto);

    @Transactional
    void saveApartItem(ApartItemDto apartItemDto);

}
