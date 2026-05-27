package com.realty.Realtymate.service.RepositoryService;

import com.realty.Realtymate.model.SanggaItemDto;
import com.realty.Realtymate.model.SanggaServeItemEntity;
import com.realty.Realtymate.repository.SanggaServeItemRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
public class SanggaServiceItemServiceImpl implements  SanggaServieItemService{
    private final SanggaServeItemRepository sanggaServeItemRepository;

    public SanggaServiceItemServiceImpl(SanggaServeItemRepository sanggaServeItemRepository) {
        this.sanggaServeItemRepository = sanggaServeItemRepository;
    }

    @Override
    public boolean saveOrUpdateSanggaServeItem(SanggaItemDto newItem) {
        Optional<SanggaServeItemEntity> existingItem = sanggaServeItemRepository.findByAddressAndFloorAndDepositAndMonthlyFeeAndManagementFee(
                newItem.getAddress(),
                Optional.ofNullable(newItem.getFloor()).orElse(0),  // null이면 0으로 설정
                newItem.getDeposit(),
                newItem.getMonthlyFee(),
                newItem.getManagementFee()
        );

        if (existingItem.isPresent()) {
            SanggaServeItemEntity item = existingItem.get();
            item.setUpdatedDate(LocalDateTime.now());  // ✅ updated_date만 갱신
            sanggaServeItemRepository.save(item);
            return true;
        } else {
            sanggaServeItemRepository.save(newItem.toServeDto().toEntity());  // ✅ 새로운 데이터 저장
            return false;
        }
    }
    @Override
    public Optional<SanggaServeItemEntity> findExistingSanggaServeItem(SanggaItemDto newItem) {
        return sanggaServeItemRepository.findByAddressAndFloorAndDepositAndMonthlyFeeAndManagementFee(
                newItem.getAddress(),
                newItem.getFloor(),
                newItem.getDeposit(),
                newItem.getMonthlyFee(),
                newItem.getManagementFee()
        );
    }
}
