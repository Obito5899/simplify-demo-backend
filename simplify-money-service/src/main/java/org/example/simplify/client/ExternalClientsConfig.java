package org.example.simplify.client;

import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class ExternalClientsConfig {

    @Value("${clients.payment.base-url:http://payment-gateway-service:8081}")
    private String paymentBaseUrl;

    @Value("${clients.gold.base-url:http://gold-partner-service:8082}")
    private String goldBaseUrl;

    @Bean("paymentWebClient")
    public WebClient paymentWebClient(WebClient.Builder builder) {
        return builder.clone()
                .baseUrl(paymentBaseUrl)
                .filter(correlationIdFilter())
                .build();
    }

    @Bean("goldWebClient")
    public WebClient goldWebClient(WebClient.Builder builder) {
        return builder.clone()
                .baseUrl(goldBaseUrl)
                .filter(correlationIdFilter())
                .build();
    }

    private ExchangeFilterFunction correlationIdFilter() {
        return (request, next) -> {
            String cid = MDC.get("correlationId");
            if (cid != null && !cid.isBlank()) {
                ClientRequest filteredRequest = ClientRequest.from(request)
                        .header("X-Correlation-ID", cid)
                        .build();
                return next.exchange(filteredRequest);
            }
            return next.exchange(request);
        };
    }
}

