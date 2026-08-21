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

package top.continew.admin.merchant.agent.domain;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Immutable merchant defaults copied by value into each new KYC draft. */
public record AgentMerchantDefaults(List<AgentMerchantDefaultProduct> products) {

    public AgentMerchantDefaults {
        if (products == null || products.isEmpty() || products.size() > 100) {
            throw new AgentDomainException("Agent merchant defaults must contain between 1 and 100 products");
        }
        products = List.copyOf(products);
        Set<String> dimensions = new HashSet<>();
        if (products.stream().anyMatch(product -> product == null || !dimensions.add(product.dimensionKey()))) {
            throw new AgentDomainException("Agent merchant defaults contain duplicate products");
        }
    }
}
