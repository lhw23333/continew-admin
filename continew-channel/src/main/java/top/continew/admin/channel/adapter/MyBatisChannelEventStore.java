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

package top.continew.admin.channel.adapter;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import top.continew.admin.channel.api.ChannelEventProcessingException;
import top.continew.admin.channel.api.ChannelEventStorePort;
import top.continew.admin.channel.dto.ChannelEventClaim;
import top.continew.admin.channel.dto.ChannelEventRecord;
import top.continew.admin.channel.dto.ChannelMappedStatus;

import java.time.LocalDateTime;
import java.util.Optional;

/** MyBatis event store using dialect-specific conflict-free atomic claims. */
@Repository
@RequiredArgsConstructor
public class MyBatisChannelEventStore implements ChannelEventStorePort {
    private final ChannelEventMapper mapper;

    @Override
    public Optional<ChannelEventClaim> find(Long tenantId, String channelCode, String eventKey) {
        return Optional.ofNullable(mapper.lambdaQuery()
            .select(ChannelEventDO::getId, ChannelEventDO::getPayloadHash)
            .eq(ChannelEventDO::getTenantId, tenantId)
            .eq(ChannelEventDO::getChannelCode, channelCode)
            .eq(ChannelEventDO::getEventKey, eventKey)
            .one()).map(existing -> new ChannelEventClaim(existing.getId(), false, existing.getPayloadHash()));
    }

    @Override
    public ChannelEventClaim claim(ChannelEventRecord event) {
        ChannelEventDO row = new ChannelEventDO();
        row.setTenantId(event.tenantId());
        row.setChannelCode(event.product().channelCode());
        row.setProductCode(event.product().productCode());
        row.setConfigVersion(event.configVersion());
        row.setChannelEventId(event.channelEventId());
        row.setEventKey(event.eventKey());
        row.setApplicationId(event.businessId());
        row.setMerchantId(event.merchantId());
        row.setBusinessType(event.businessType());
        row.setBusinessVersion(event.businessVersion());
        row.setBusinessSerial(event.businessSerial());
        row.setEventType(event.eventType());
        row.setChannelRequestId(event.channelRequestId());
        row.setRawStatus(event.rawStatusCode());
        row.setMappingVersion(event.mappingVersion());
        row.setPayloadHash(event.payloadHash());
        row.setSanitizedPayloadJson(event.sanitizedPayloadJson());
        row.setSignatureKeyVersion(event.signatureKeyVersion());
        row.setOccurredTime(event.occurredTime());
        row.setReceivedTime(event.receivedTime());
        row.setProcessingStatus("RECEIVED");
        row.setStateApplied(false);
        row.setRetryCount(0);
        row.setTraceId(event.traceId());
        row.setRowVersion(0L);
        row.setCreateTime(event.receivedTime());
        if (mapper.claim(row) == 1) {
            return new ChannelEventClaim(row.getId(), true, null);
        }
        return find(event.tenantId(), event.product().channelCode(), event.eventKey())
            .orElseThrow(() -> new ChannelEventProcessingException(ChannelEventProcessingException.Code.PERSISTENCE_FAILED));
    }

    @Override
    public void complete(Long tenantId,
                         Long eventRecordId,
                         ChannelMappedStatus mappedStatus,
                         boolean stateApplied,
                         String processingStatus,
                         LocalDateTime processedTime) {
        boolean updated = mapper.lambdaUpdate()
            .eq(ChannelEventDO::getTenantId, tenantId)
            .eq(ChannelEventDO::getId, eventRecordId)
            .eq(ChannelEventDO::getProcessingStatus, "RECEIVED")
            .eq(ChannelEventDO::getRowVersion, 0L)
            .set(ChannelEventDO::getNormalizedStateType, "ONBOARDING_SNAPSHOT")
            .set(ChannelEventDO::getNormalizedStatus, mappedStatus.operationStatus().name())
            .set(ChannelEventDO::getOperationStatus, mappedStatus.operationStatus())
            .set(ChannelEventDO::getReportingStatus, mappedStatus.onboardingState().reportingStatus())
            .set(ChannelEventDO::getSigningStatus, mappedStatus.onboardingState().signingStatus())
            .set(ChannelEventDO::getCardBindingStatus, mappedStatus.onboardingState().cardBindingStatus())
            .set(ChannelEventDO::getReserveAccountStatus, mappedStatus.onboardingState().reserveAccountStatus())
            .set(ChannelEventDO::getFinalStatus, mappedStatus.onboardingState().finalStatus())
            .set(ChannelEventDO::getProgressionRank, mappedStatus.progressionRank())
            .set(ChannelEventDO::getStateApplied, stateApplied)
            .set(ChannelEventDO::getProcessingStatus, processingStatus)
            .set(ChannelEventDO::getProcessedTime, processedTime)
            .set(ChannelEventDO::getRowVersion, 1L)
            .set(ChannelEventDO::getUpdateTime, processedTime)
            .update();
        if (!updated) {
            throw new ChannelEventProcessingException(ChannelEventProcessingException.Code.PERSISTENCE_FAILED);
        }
    }

    @Override
    public void fail(Long tenantId, Long eventRecordId, String failureCategory, LocalDateTime processedTime) {
        boolean updated = mapper.lambdaUpdate()
            .eq(ChannelEventDO::getTenantId, tenantId)
            .eq(ChannelEventDO::getId, eventRecordId)
            .eq(ChannelEventDO::getProcessingStatus, "RECEIVED")
            .eq(ChannelEventDO::getRowVersion, 0L)
            .set(ChannelEventDO::getProcessingStatus, "FAILED")
            .set(ChannelEventDO::getStateApplied, false)
            .set(ChannelEventDO::getLastErrorCategory, failureCategory)
            .set(ChannelEventDO::getProcessedTime, processedTime)
            .set(ChannelEventDO::getRowVersion, 1L)
            .set(ChannelEventDO::getUpdateTime, processedTime)
            .update();
        if (!updated) {
            throw new ChannelEventProcessingException(ChannelEventProcessingException.Code.PERSISTENCE_FAILED);
        }
    }
}
