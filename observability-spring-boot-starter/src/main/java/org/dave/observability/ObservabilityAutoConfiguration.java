package org.dave.observability;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.filter.OncePerRequestFilter;

@AutoConfiguration
@EnableConfigurationProperties(ObservabilityProperties.class)
public class ObservabilityAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "middle.observability.idempotency", name = "enabled", matchIfMissing = true)
    public IdempotencyGuard idempotencyGuard() {
        return new InMemoryIdempotencyGuard();
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnWebApplication
    @ConditionalOnClass(OncePerRequestFilter.class)
    static class WebConfiguration {

        @Bean
        @ConditionalOnMissingBean
        public CorrelationIdFilter correlationIdFilter(ObservabilityProperties properties) {
            return new CorrelationIdFilter(properties.getHeaderName());
        }

        @Bean
        @ConditionalOnProperty(prefix = "middle.observability.logging", name = "enabled", matchIfMissing = true)
        public RequestLoggingFilter requestLoggingFilter(ObservabilityProperties properties) {
            return new RequestLoggingFilter(properties.getLogging().getIgnoredPaths());
        }
    }
}
