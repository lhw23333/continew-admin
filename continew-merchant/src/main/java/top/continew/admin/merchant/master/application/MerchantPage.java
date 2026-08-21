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

package top.continew.admin.merchant.master.application;

import java.util.List;

/** Stable merchant page ordered by creation time and ID descending. */
public record MerchantPage(List<MerchantSummary> list, long total, int page, int size) {

    public MerchantPage {
        list = List.copyOf(list);
    }

    public static MerchantPage empty(int page, int size) {
        return new MerchantPage(List.of(), 0L, page, size);
    }
}
