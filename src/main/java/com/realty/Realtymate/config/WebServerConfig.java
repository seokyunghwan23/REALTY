package com.realty.Realtymate.config;

import com.realty.Realtymate.security.IPBlacklistManager;
import io.netty.channel.ChannelOption;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.web.embedded.netty.NettyReactiveWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import reactor.netty.http.server.HttpServer;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;

@Configuration
public class WebServerConfig {
    private static final Logger logger = LoggerFactory.getLogger(WebServerConfig.class);

    @Autowired
    private IPBlacklistManager blacklistManager;

    @Bean
    @Order(0)
    public WebServerFactoryCustomizer<NettyReactiveWebServerFactory> nettyCustomizer() {
        return factory -> {
            try {
                InetAddress address = InetAddress.getByName("0.0.0.0");
                factory.setAddress(address);
                factory.setPort(8081);

                // Netty HttpServer 커스터마이징
                factory.addServerCustomizers(httpServer ->
                        httpServer
                                // 연결 타임아웃 설정
                                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 10000)
                                // 디코딩 실패 처리
                                .doOnConnection(conn -> {
                                    conn.addHandlerLast("loggingHandler", new LoggingHandler(LogLevel.DEBUG));
                                })
                                // 에러 처리 및 IP 차단
                                .doOnChannelInit((observer, channel, remoteAddress) -> {
                                    // IP 차단 체크
                                    String ip = "UNKNOWN";
                                    if (remoteAddress instanceof InetSocketAddress) {
                                        ip = ((InetSocketAddress) remoteAddress).getAddress().getHostAddress();
                                    }

                                    if (blacklistManager.isBlocked(ip)) {
                                        channel.close();
                                        return;
                                    }

                                    channel.pipeline().addFirst("invalid-request-detector",
                                            new io.netty.channel.ChannelInboundHandlerAdapter() {
                                                @Override
                                                public void exceptionCaught(io.netty.channel.ChannelHandlerContext ctx,
                                                                            Throwable cause) {
                                                    String ip = extractIP(ctx);
                                                    String reason = cause.getMessage() != null ?
                                                            cause.getMessage() : cause.getClass().getSimpleName();

                                                    // Connection reset 등 특정 에러는 즉시 차단 (로그 없이 처리)
                                                    if (reason.contains("Connection reset") ||
                                                        reason.contains("Broken pipe") ||
                                                        reason.contains("DecoderException")) {
                                                        blacklistManager.recordInvalidRequest(ip, reason);
                                                    }

                                                    ctx.close();
                                                }

                                                private String extractIP(io.netty.channel.ChannelHandlerContext ctx) {
                                                    try {
                                                        InetSocketAddress remote =
                                                                (InetSocketAddress) ctx.channel().remoteAddress();
                                                        if (remote != null) {
                                                            return remote.getAddress().getHostAddress();
                                                        }
                                                    } catch (Exception e) {
                                                        logger.debug("IP 추출 실패", e);
                                                    }
                                                    return "UNKNOWN";
                                                }
                                            });
                                })
                );

                System.out.println("=================================================");
                System.out.println("✅ 서버 설정: 0.0.0.0:8081 (외부 접속 가능)");
                System.out.println("✅ Address: " + address.getHostAddress());
                System.out.println("✅ IP 차단 시스템 활성화");
                System.out.println("=================================================");
            } catch (UnknownHostException e) {
                System.err.println("❌ 서버 주소 설정 실패: " + e.getMessage());
                e.printStackTrace();
            }
        };
    }
}
