package com.itx.similarproducts.infrastructure.client;

import io.netty.channel.ChannelOption;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;
import reactor.netty.resources.ConnectionProvider;

@Configuration
public class ExistingApisClientConfig {

    @Bean
    public WebClient existingApisWebClient(WebClient.Builder builder, ExistingApisProperties properties) {
        ConnectionProvider connectionProvider = ConnectionProvider.builder("existing-apis")
                .maxConnections(properties.maxConnections())
                .pendingAcquireTimeout(properties.pendingAcquireTimeout())
                .build();

        HttpClient httpClient = HttpClient.create(connectionProvider)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, (int) properties.connectTimeout().toMillis())
                .responseTimeout(properties.upstreamTimeout());

        return builder
                .baseUrl(properties.baseUrl())
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();
    }
}
