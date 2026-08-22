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

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Profiles;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Fails production startup before Flowable can mutate schema or enable out-of-scope engines. */
public class ProductionFlowableEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    private static final List<RequiredValue> REQUIRED = List
        .of(new RequiredValue("flowable.database-schema-update", "false"), new RequiredValue("flowable.history-level", "audit"), new RequiredValue("flowable.async-executor-activate", "true"), new RequiredValue("flowable.async-history-executor-activate", "false"), new RequiredValue("flowable.idm.enabled", "false"), new RequiredValue("flowable.eventregistry.enabled", "false"), new RequiredValue("workflow.flowable.schema-strategy", "DEFAULT_ACT_FLW_PREFIXES"));

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        if (!environment.acceptsProfiles(Profiles.of("prod"))) {
            return;
        }
        List<String> invalid = new ArrayList<>();
        REQUIRED.forEach(required -> {
            String actual = environment.getProperty(required.property());
            if (actual == null || !required.value().equalsIgnoreCase(actual.trim())) {
                invalid.add(required.property());
            }
        });
        if (!invalid.isEmpty()) {
            invalid.sort(Comparator.naturalOrder());
            throw new IllegalStateException("Production Flowable policy validation failed for properties: " + String
                .join(", ", invalid));
        }
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }

    private record RequiredValue(String property, String value) {
    }
}
