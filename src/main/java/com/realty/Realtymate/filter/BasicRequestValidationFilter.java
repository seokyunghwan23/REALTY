package com.realty.Realtymate.filter;

import com.realty.Realtymate.security.IPBlacklistManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;

@Component
public class BasicRequestValidationFilter implements WebFilter {
    private static final Logger logger = LoggerFactory.getLogger(BasicRequestValidationFilter.class);

    @Autowired
    private IPBlacklistManager blacklistManager;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        // 블랙리스트 차단 비활성화 - 모든 요청 통과
        return chain.filter(exchange);
    }

    /**
     * 클라이언트 IP 주소 추출
     */
    private String getClientIP(ServerWebExchange exchange) {
        // X-Forwarded-For 헤더 확인 (프록시/로드밸런서 뒤에 있는 경우)
        String xForwardedFor = exchange.getRequest().getHeaders().getFirst("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }

        // X-Real-IP 헤더 확인
        String xRealIP = exchange.getRequest().getHeaders().getFirst("X-Real-IP");
        if (xRealIP != null && !xRealIP.isEmpty()) {
            return xRealIP;
        }

        // Remote Address에서 추출
        InetSocketAddress remoteAddress = exchange.getRequest().getRemoteAddress();
        if (remoteAddress != null) {
            return remoteAddress.getAddress().getHostAddress();
        }

        return "UNKNOWN";
    }
}
