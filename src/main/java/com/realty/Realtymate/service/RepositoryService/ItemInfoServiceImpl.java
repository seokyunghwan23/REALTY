package com.realty.Realtymate.service.RepositoryService;

import com.realty.Realtymate.model.*;
import com.realty.Realtymate.repository.ApartItemRepository;
import com.realty.Realtymate.repository.SanggaItemRepository;
import com.realty.Realtymate.repository.SanggaServeItemRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ItemInfoServiceImpl implements ItemInfoService{
    private final SanggaItemRepository sanggaItemRepository;
    private final SanggaServeItemRepository sanggaServeItemRepository;
    private final ApartItemRepository apartItemRepository;


    public ItemInfoServiceImpl(SanggaItemRepository sanggaItemRepository, SanggaServeItemRepository sanggaServeItemRepository, ApartItemRepository apartItemRepository) {
        this.sanggaItemRepository = sanggaItemRepository;
        this.sanggaServeItemRepository = sanggaServeItemRepository;
        this.apartItemRepository = apartItemRepository;
    }

    @Override
    public List<ApartItemEntity> getApartItems(){
        return apartItemRepository.findAll();
    }

    @Override
    public List<SanggaItemEntity> getItems(){
//        return sanggaItemRepository.findByRegionName(agentCustomer.getRegionName());
        return sanggaItemRepository.findAll();
    }

    @Override
    public List<SanggaItemEntity> getNaverItems() {
        return sanggaItemRepository.findByPlatform("네이버");
    }

    @Override
    @Transactional
    public void saveSanggaItem(SanggaItemDto sanggaItemDto) {
        sanggaItemRepository.insertIgnoreSanggaItem(
                sanggaItemDto.getItemId(),
                sanggaItemDto.getAddress(),
                sanggaItemDto.getFloor(),
                sanggaItemDto.getArea(),
                sanggaItemDto.getDeposit(),
                sanggaItemDto.getMonthlyFee(),
                sanggaItemDto.getManagementFee(),
                sanggaItemDto.getLongitude(),
                sanggaItemDto.getLatitude(),
                sanggaItemDto.getUpdatedDate(),
                sanggaItemDto.getPlatform(),
                sanggaItemDto.getRegionName(),
                sanggaItemDto.getAgentName()
        );
    }

    @Override
    @Transactional
    public void saveApartItem(ApartItemDto apartItemDto) {
        apartItemRepository.insertIgnoreApartItem(
                apartItemDto.getItemId(),
                apartItemDto.getAddress(),
                apartItemDto.getDong(),
                apartItemDto.getFloor(),
                apartItemDto.getHo(),
                apartItemDto.getArea(),
                apartItemDto.getDealPrice(),
                apartItemDto.getDeposit(),
                apartItemDto.getMonthlyFee(),
                apartItemDto.getManagementFee(),
                apartItemDto.getTradeType(),
                apartItemDto.getUpdatedDate(),
                apartItemDto.getApartName(),
                apartItemDto.getAgentName()
        );
    }



}
