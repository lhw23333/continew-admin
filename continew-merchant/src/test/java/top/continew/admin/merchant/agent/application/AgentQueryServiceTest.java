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

package top.continew.admin.merchant.agent.application;

import org.junit.jupiter.api.Test;
import top.continew.admin.merchant.agent.domain.AgentAccessDeniedException;
import top.continew.admin.merchant.security.audit.application.SecurityAuditRepository;
import top.continew.admin.merchant.security.audit.application.SecurityAuditWriter;
import top.continew.admin.merchant.security.audit.domain.SecurityAuditRecord;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentQueryServiceTest {

    @Test
    void defaultTenantUserWithoutAgentGetsEmptyPageAndDeniedAuditInsteadOfServerFailure() {
        List<SecurityAuditRecord> audits = new ArrayList<>();
        AgentQueryService service = new AgentQueryService(emptyRepository(), new DenyingScopeService(), new SecurityAuditWriter(new CapturingAuditRepository(audits)));

        AgentPage result = service.page(0L, 1L, new AgentListQuery(null, null, null, 1, 20, "127.0.0.1"));

        assertTrue(result.list().isEmpty());
        assertEquals(0L, result.total());
        assertEquals(0L, audits.get(0).tenantId());
        assertEquals("AGENT_LIST_SCOPE_DENIED", audits.get(0).failureCode());
    }

    private AgentRepository emptyRepository() {
        return (AgentRepository)Proxy.newProxyInstance(AgentRepository.class.getClassLoader(), new Class<?>[] {
            AgentRepository.class}, (proxy, method, args) -> switch (method.getName()) {
                case "findById", "findByUserId", "findByPromotionCode" -> Optional.empty();
                case "existsById", "existsByAgentNo", "existsByUserId", "existsByPromotionCode", "bindDepartment",
                    "updateProfile", "updatePromotionCode", "updateLifecycle" -> false;
                case "page" -> AgentPage.empty(1, 20);
                case "insert" -> null;
                default -> throw new UnsupportedOperationException(method.getName());
            });
    }

    private static final class DenyingScopeService extends AgentScopeAuthorizationService {
        private DenyingScopeService() {
            super(null, null);
        }

        @Override
        public List<Long> listAuthorizedAgentIds(Long tenantId, Long actorUserId) {
            throw new AgentAccessDeniedException();
        }
    }

    private record CapturingAuditRepository(List<SecurityAuditRecord> records) implements SecurityAuditRepository {
        @Override
        public Long append(SecurityAuditRecord record) {
            records.add(record);
            return (long)records.size();
        }
    }
}