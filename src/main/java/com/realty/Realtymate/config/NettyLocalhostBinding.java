package com.realty.Realtymate.config;

import io.netty.channel.ChannelOption;
import org.springframework.boot.web.embedded.netty.NettyReactiveWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.stereotype.Component;

import java.net.InetSocketAddress;

@Component
public class NettyLocalhostBinding implements WebServerFactoryCustomizer<NettyReactiveWebServerFactory> {
    @Override
    public void customize(NettyReactiveWebServerFactory factory) {
        factory.addServerCustomizers(httpServer ->
                httpServer.bindAddress(() -> new InetSocketAddress("0.0.0.0", 8081))
                        .option(ChannelOption.SO_REUSEADDR, true)
        );
        System.out.println("🌐 Netty 서버: 0.0.0.0:8081로 바인딩 완료!");
    }
}