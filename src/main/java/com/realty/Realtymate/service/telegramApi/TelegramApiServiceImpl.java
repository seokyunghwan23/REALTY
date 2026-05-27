package com.realty.Realtymate.service.telegramApi;

import com.realty.Realtymate.model.AgentCustomer;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.http.HttpHeaders;
import org.springframework.web.reactive.function.client.WebClientResponseException;

@Service
public class TelegramApiServiceImpl implements TelegramApiService {

    private final WebClient webClient;

    public TelegramApiServiceImpl(WebClient.Builder webClientBuilder) {
        this.webClient = webClientBuilder.baseUrl("https://api.telegram.org").build();
    }

    @Override
    public void sendMessage(AgentCustomer agentCustomer, String text) {
        String chatId = agentCustomer.getChatId(); // 기본값 설정
        String url = "";
        if(agentCustomer.getAgentName().equals("청운")) {
            url = "/bot5070846200:AAG0sfJE5Zt5-IwYLAsJIEkoQphZjWVrH4E/sendMessage?chat_id=" + chatId + "&text=" + text;
        } else if(agentCustomer.getAgentName().equals("베스트")){
            url = "/bot6676642356:AAG1os19AqpPU106dFMHf1egOhtu2GZ2CjM/sendMessage?chat_id=" + chatId + "&text=" + text;
        } else if(agentCustomer.getAgentName().equals("홍일")){
            url = "/bot5070846200:AAG0sfJE5Zt5-IwYLAsJIEkoQphZjWVrH4E/sendMessage?chat_id=" + chatId + "&text=" + text;
        }


        while (true) {
            try {
                // 요청 보내기
                webClient.get()
                        .uri(url)
                        .retrieve()
                        .bodyToMono(String.class)
                        .block(); // 동기 요청

                break;
            } catch (WebClientResponseException e) {
                if (e.getStatusCode().value() == 429) { // Too Many Requests
                    // retry_after 값 추출
                    String retryAfterHeader = e.getHeaders().getFirst(HttpHeaders.RETRY_AFTER);
                    int retryAfter = retryAfterHeader != null ? Integer.parseInt(retryAfterHeader) : 60; // 기본 60초
                    System.out.println("Too Many Requests. Retrying after " + retryAfter + " seconds.");
                    try {
                        Thread.sleep(retryAfter * 1000L); // 재시도 대기
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        System.out.println("Retry interrupted.");
                        break;
                    }
                } else {
                    // 다른 에러 처리
                    System.err.println("HTTP Error: " + e.getStatusCode() + " - " + e.getResponseBodyAsString());
                    break;
                }
            } catch (Exception ex) {
                System.err.println("Unexpected error: " + ex.getMessage());
                break;
            }
        }
    }

    @Override
    public void sendMessage(String chatId, String text) {
        String url = "/bot5070846200:AAG0sfJE5Zt5-IwYLAsJIEkoQphZjWVrH4E/sendMessage?chat_id=" + chatId + "&text=" + text;

        while (true) {
            try {
                // 요청 보내기
                webClient.get()
                        .uri(url)
                        .retrieve()
                        .bodyToMono(String.class)
                        .block(); // 동기 요청

                break;
            } catch (WebClientResponseException e) {
                if (e.getStatusCode().value() == 429) { // Too Many Requests
                    // retry_after 값 추출
                    String retryAfterHeader = e.getHeaders().getFirst(HttpHeaders.RETRY_AFTER);
                    int retryAfter = retryAfterHeader != null ? Integer.parseInt(retryAfterHeader) : 60; // 기본 60초
                    System.out.println("Too Many Requests. Retrying after " + retryAfter + " seconds.");
                    try {
                        Thread.sleep(retryAfter * 1000L); // 재시도 대기
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        System.out.println("Retry interrupted.");
                        break;
                    }
                } else {
                    // 다른 에러 처리
                    System.err.println("HTTP Error: " + e.getStatusCode() + " - " + e.getResponseBodyAsString());
                    break;
                }
            } catch (Exception ex) {
                System.err.println("Unexpected error: " + ex.getMessage());
                break;
            }
        }
    }
}
