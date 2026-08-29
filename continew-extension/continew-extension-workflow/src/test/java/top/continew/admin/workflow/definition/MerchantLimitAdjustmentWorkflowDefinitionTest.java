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

package top.continew.admin.workflow.definition;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import top.continew.admin.workflow.command.DeployWorkflowCommand;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MerchantLimitAdjustmentWorkflowDefinitionTest {

    @Test
    void approvedReviewRequiresChannelTaskBeforeAnyEffectiveTerminalOutcome() throws Exception {
        MerchantLimitAdjustmentWorkflowDefinition definition = new MerchantLimitAdjustmentWorkflowDefinition();
        DeployWorkflowCommand command = definition.deploymentCommand(1301L, 2301L);
        Document document = parse(command.resourceBytes());

        Element process = (Element)document.getElementsByTagNameNS("*", "process").item(0);
        assertEquals(MerchantLimitAdjustmentWorkflowDefinition.PROCESS_KEY, process.getAttribute("id"));
        assertEquals("channelSubmitTask", flow(document, "flow-review-approved").getAttribute("targetRef"));
        assertEquals("rejectedEnd", flow(document, "flow-review-rejected").getAttribute("targetRef"));
        assertEquals("effectiveEnd", flow(document, "flow-channel-effective").getAttribute("targetRef"));
        assertEquals("failedEnd", flow(document, "flow-channel-failed").getAttribute("targetRef"));
        assertEquals("channelQueryTask", flow(document, "flow-channel-query").getAttribute("targetRef"));
        assertEquals("channelResultGateway", flow(document, "flow-channel-query-result").getAttribute("targetRef"));
        assertTrue(flow(document, "flow-channel-effective").getTextContent().contains("channelStatus == 'EFFECTIVE'"));
        assertTrue(flow(document, "flow-channel-failed").getTextContent().contains("channelStatus == 'FAILED'"));
    }

    @Test
    void stableContractContainsAllLimitRoutingNodes() {
        Set<String> nodeIds = new MerchantLimitAdjustmentWorkflowDefinition().contract()
            .requiredNodes()
            .stream()
            .map(node -> node.nodeId())
            .collect(Collectors.toSet());

        assertEquals(Set
            .of("start", "limitReviewTask", "reviewDecisionGateway", "channelSubmitTask", "channelResultGateway", "channelQueryTask", "rejectedEnd", "effectiveEnd", "failedEnd"), nodeIds);
    }

    private Document parse(byte[] bytes) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        return factory.newDocumentBuilder().parse(new ByteArrayInputStream(bytes));
    }

    private Element flow(Document document, String id) {
        NodeList flows = document.getElementsByTagNameNS("*", "sequenceFlow");
        for (int index = 0; index < flows.getLength(); index++) {
            Element flow = (Element)flows.item(index);
            if (id.equals(flow.getAttribute("id"))) {
                return flow;
            }
        }
        throw new AssertionError("Missing BPMN sequence flow: " + id);
    }
}