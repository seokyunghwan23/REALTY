package com.realty.Realtymate.service.RealtySerevice;

import com.realty.Realtymate.model.*;
import com.realty.Realtymate.repository.DongPnuRepository;
import com.realty.Realtymate.repository.HostelItemRepository;
import com.realty.Realtymate.repository.IamGroundItemRepository;
import com.realty.Realtymate.service.RepositoryService.AgentCustomerService;
import com.realty.Realtymate.service.RepositoryService.ApartCustomerService;
import com.realty.Realtymate.service.RepositoryService.DagaguItemService;
import com.realty.Realtymate.service.RepositoryService.ItemInfoService;
import com.realty.Realtymate.service.dabangApi.DabangApiService;
import com.realty.Realtymate.service.naverApi.NaverApiService;
import com.realty.Realtymate.service.nemoApi.NemoApiService;
import com.realty.Realtymate.service.peterpanzApi.PeterpanzApiService;
import com.realty.Realtymate.service.zigbangApi.ZigbangApiService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class RealtyServiceImpl implements RealtyService {

    @Autowired
    ZigbangApiService zigbangApiService;
    @Autowired
    DabangApiService dabangApiService;
    @Autowired
    PeterpanzApiService peterpanzApiService;
    @Autowired
    NemoApiService nemoApiService;
    @Autowired
    NaverApiService naverApiService;

    @Autowired
    AgentCustomerService agentCustomerService;

    @Autowired
    ApartCustomerService apartCustomerService;

    @Autowired
    DagaguItemService dagaguItemService;

    @Autowired
    ItemInfoService itemInfoService;

    private final DongPnuRepository dongPnuRepository;
    private final IamGroundItemRepository iamGroundItemRepository;
    private final HostelItemRepository hostelItemRepository;


    public RealtyServiceImpl(DongPnuRepository dongPnuRepository, IamGroundItemRepository iamGroundItemRepository, HostelItemRepository hostelItemRepository) {
        this.dongPnuRepository = dongPnuRepository;
        this.iamGroundItemRepository = iamGroundItemRepository;
        this.hostelItemRepository = hostelItemRepository;
    }

    @Override
    public void alertDagaguProcess() {
        List<AgentCustomer> allCustomers = agentCustomerService.getAllCustomers(Arrays.asList("원룸", "투룸"));
        allCustomers.stream()
                .filter(agentCustomer -> agentCustomer.isAlert())
                .forEach(agentCustomer -> alertDagaguNewAd(agentCustomer));
    }

    @Override
    public void alertApartProcess() {
        List<ApartCustomer> allCustomers = apartCustomerService.getAllCustomers();
        allCustomers.stream()
                .filter(apartCustomer -> apartCustomer.isAlert())
                .forEach(apartCustomer -> alertApartNewAd(apartCustomer));
    }

    public void alertApartNewAd(ApartCustomer apartCustomer) {
        List<ApartItemEntity> previousItemList = itemInfoService.getApartItems();
        naverApiService.alertApartNewAd(previousItemList, apartCustomer);
    }


    public void alertDagaguNewAd(AgentCustomer agentCustomer) {
        List<DagaguItemEntity> previousItemList = dagaguItemService.getItems(agentCustomer);
        zigbangApiService.alertDagaguNewAd(previousItemList, agentCustomer);
        dabangApiService.alertDagaguNewAd(previousItemList, agentCustomer);
        peterpanzApiService.alertDagaguNewAd(previousItemList, agentCustomer);
    }

    @Override
    public void alertOwnerProcess() {
        List<AgentCustomer> allCustomers = agentCustomerService.getAllCustomers(Arrays.asList("소유자"));
        allCustomers.stream()
                .filter(agentCustomer -> agentCustomer.isAlert())
                .forEach(agentCustomer -> {
                    System.out.println(agentCustomer.getRegionName());
                    alertOwnerNewAd(agentCustomer);
                });
    }

    private void alertOwnerNewAd(AgentCustomer agentCustomer) {
        List<SanggaItemEntity> previousItemList = itemInfoService.getNaverItems();
        naverApiService.alertOwnerAd(previousItemList, agentCustomer);

    }

    @Override
    public void alertSanggaProcess() {
        List<AgentCustomer> allCustomers = agentCustomerService.getAllCustomers(Arrays.asList("상가", "공장창고"));
        allCustomers.stream()
                .filter(agentCustomer -> agentCustomer.isAlert())
                .forEach(agentCustomer -> {
                    System.out.println(agentCustomer.getRegionName());
                    alertSanggaNewAd(agentCustomer);
                });
    }

    public void alertSanggaNewAd(AgentCustomer agentCustomer) {
        List<SanggaItemEntity> previousItemList = itemInfoService.getItems();
        naverApiService.alertSanggaNewAd(previousItemList, agentCustomer);
        if (agentCustomer.getKind().equals("상가")) {
            nemoApiService.alertSanggaNewAd(previousItemList, agentCustomer);
        }
    }

    @Override
    public void alertIamgroundProcess() {
        ArrayList<String> previousItemList = iamGroundItemRepository.findAllItemIds();
        List<String> dongPnuList = dongPnuRepository.findAllPnuByDongPrefix("서울시");
        naverApiService.alertIamGroundNewAd(previousItemList, dongPnuList);
    }

    @Override
    public void alertHostelProcess() {
        ArrayList<String> previousItemList = hostelItemRepository.findAllItemIds();
        List<String> excludeGu = List.of("노원구", "도봉구", "강북구");
        List<DongPnu> dongPnuList = dongPnuRepository.findAllDongAndPnuByDongPrefix("서울시");

// 제외할 구가 포함된 dong은 걸러냄
        List<String> filteredPnuList = dongPnuList.stream()
                .filter(dp -> excludeGu.stream().noneMatch(dp.getDong()::contains))
                .map(DongPnu::getPnu)
                .collect(Collectors.toList());

        naverApiService.alertHostelNewAd(previousItemList, filteredPnuList);
    }


}
