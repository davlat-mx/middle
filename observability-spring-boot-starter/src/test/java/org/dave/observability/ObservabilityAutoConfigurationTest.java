package org.dave.observability;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

class ObservabilityAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ObservabilityAutoConfiguration.class));

    @Test
    void providesInMemoryGuardByDefault() {
        runner.run(context -> assertThat(context)
                .getBean(IdempotencyGuard.class)
                .isInstanceOf(InMemoryIdempotencyGuard.class));
    }

    @Test
    void guardDisabledByProperty() {
        runner.withPropertyValues("middle.observability.idempotency.enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(IdempotencyGuard.class));
    }

    @Test
    void userGuardOverridesAutoConfig() {
        runner.withUserConfiguration(CustomGuardConfig.class)
                .run(context -> assertThat(context)
                        .getBean(IdempotencyGuard.class)
                        .isSameAs(context.getBean(CustomGuardConfig.class).guard));
    }

    @Configuration(proxyBeanMethods = false)
    static class CustomGuardConfig {

        private final IdempotencyGuard guard = key -> true;

        @Bean
        IdempotencyGuard customGuard() {
            return guard;
        }
    }
}
