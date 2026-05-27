package com.realty.Realtymate.service;

import com.realty.Realtymate.model.AgentCustomer;
import com.realty.Realtymate.security.IPBlacklistManager;
import com.realty.Realtymate.service.RealtySerevice.MatchingService;
import com.realty.Realtymate.service.RealtySerevice.RealtyService;
import com.realty.Realtymate.service.RepositoryService.AgentCustomerService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Slf4j
public class ScheduledTaskService {

    @Autowired
    RealtyService realtyService;
    @Autowired
    AgentCustomerService agentCustomerService;
    @Autowired
    MatchingService matchingService;
    @Autowired
    IPBlacklistManager blacklistManager;

/*    @Scheduled(fixedDelay = 1000 * 60 * 10)
    public void alertApartProcess(){
        System.out.println("아파트 시작");
        realtyService.alertApartProcess();
        System.out.println("아파트 끝");
    }*/


    @Scheduled(fixedDelay = 1000 * 60 * 10)
    public void alertDagaguProcess(){
        System.out.println("다가구 시작");
        realtyService.alertDagaguProcess();
        System.out.println("다가구 끝");
    }

    @Scheduled(fixedDelay = 1000 * 60 * 10)
    public void alertSanggaProccess(){
        System.out.println("상가 시작");
        realtyService.alertSanggaProcess();
        System.out.println("상가 끝");
    }

    @Scheduled(fixedDelay = 1000 * 60 * 10)
    public void alertOwnerProcess(){
        System.out.println("소유자 시작");
        realtyService.alertOwnerProcess();
        System.out.println("소유자 끝");
    }

    /*
    @Scheduled(fixedDelay = 1000 * 60 * 30)
    public void checkCustomerCondition(){
        System.out.println("매칭 시작");
        matchingService.matchCustomersWithListings();
        System.out.println("매칭 끝");
    }

    @Scheduled(fixedDelay = 1000 * 60 * 30)
    public void iAmGroundProcess(){
        System.out.println("아이엠그라운드 시작");
        realtyService.alertIamgroundProcess();
        System.out.println("아이엠그라운드 끝");
    }

    @Scheduled(fixedDelay = 1000 * 60 * 30)
    public void hostelProcess(){
        System.out.println("호스텔 시작");
        realtyService.alertHostelProcess();
        System.out.println("호스텔 끝");
    }*/

    /**
     * IP 블랙리스트 통계 (1시간마다) - 로그 없이 실행
     */
    @Scheduled(cron = "0 0 * * * ?")
    public void logBlacklistStats() {
        // 로그 없이 조용히 실행
    }

    /**
     * 시도 횟수 카운터 초기화 (매일 자정) - 로그 없이 실행
     */
    @Scheduled(cron = "0 0 0 * * ?")
    public void resetAttemptCounter() {
        try {
            blacklistManager.resetAttemptCounter();
        } catch (Exception ignored) {
        }
    }

}
