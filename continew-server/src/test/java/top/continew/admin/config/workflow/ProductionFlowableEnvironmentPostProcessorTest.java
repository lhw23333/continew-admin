/*
 * Copyright (c) 2022-present Charles7c Authors. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package top.continew.admin.config.workflow;

import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.mock.env.MockEnvironment;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProductionFlowableEnvironmentPostProcessorTest {

    private final ProductionFlowableEnvironmentPostProcessor validator = new ProductionFlowableEnvironmentPostProcessor();

    @Test
    void ignoresNonProductionProfiles() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("dev");
        environment.setProperty("flowable.database-schema-update", "true");
        assertDoesNotThrow(() -> validator.postProcessEnvironment(environment, application()));
    }

    @Test
    void rejectsAutomaticProductionSchemaMutationWithoutEchoingValue() {
        MockEnvironment environment = validProductionEnvironment();
        environment.setProperty("flowable.database-schema-update", "drop-create");

        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> validator
            .postProcessEnvironment(environment, application()));

        assertTrue(exception.getMessage().contains("flowable.database-schema-update"));
        assertFalse(exception.getMessage().contains("drop-create"));
    }

    @Test
    void rejectsOutOfScopeEnginesAndHistoryPolicy() {
        MockEnvironment environment = validProductionEnvironment();
        environment.setProperty("flowable.idm.enabled", "true");
        environment.setProperty("flowable.eventregistry.enabled", "true");
        environment.setProperty("flowable.history-level", "full");

        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> validator
            .postProcessEnvironment(environment, application()));

        assertTrue(exception.getMessage().contains("flowable.idm.enabled"));
        assertTrue(exception.getMessage().contains("flowable.eventregistry.enabled"));
        assertTrue(exception.getMessage().contains("flowable.history-level"));
    }

    @Test
    void acceptsReviewedProductionPolicy() {
        assertDoesNotThrow(() -> validator.postProcessEnvironment(validProductionEnvironment(), application()));
    }

    private MockEnvironment validProductionEnvironment() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("prod");
        environment.setProperty("flowable.database-schema-update", "false");
        environment.setProperty("flowable.history-level", "audit");
        environment.setProperty("flowable.async-executor-activate", "true");
        environment.setProperty("flowable.async-history-executor-activate", "false");
        environment.setProperty("flowable.idm.enabled", "false");
        environment.setProperty("flowable.eventregistry.enabled", "false");
        environment.setProperty("workflow.flowable.schema-strategy", "DEFAULT_ACT_FLW_PREFIXES");
        return environment;
    }

    private SpringApplication application() {
        return new SpringApplication(Object.class);
    }
}
