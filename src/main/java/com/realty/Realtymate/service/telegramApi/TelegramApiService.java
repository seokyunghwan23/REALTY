package com.realty.Realtymate.service.telegramApi;

import com.realty.Realtymate.model.AgentCustomer;

public interface TelegramApiService {
    public void sendMessage(AgentCustomer agentCustomer, String text);
    public void sendMessage(String chatId, String text);
}
