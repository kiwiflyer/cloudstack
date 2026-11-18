// Licensed to the Apache Software Foundation (ASF) under one
// or more contributor license agreements.  See the NOTICE file
// distributed with this work for additional information
// regarding copyright ownership.  The ASF licenses this file
// to you under the Apache License, Version 2.0 (the
// "License"); you may not use this file except in compliance
// with the License.  You may obtain a copy of the License at
//
//   http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing,
// software distributed under the License is distributed on an
// "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
// KIND, either express or implied.  See the License for the
// specific language governing permissions and limitations
// under the License.
package org.apache.cloudstack.network.tungsten.service;

import com.cloud.network.vpc.NetworkACLItem;
import com.cloud.utils.TungstenUtils;
import org.apache.cloudstack.network.tungsten.model.TungstenRule;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class TungstenAclTranslatorTest {

    private static final String TIER_CIDR = "10.1.1.0/24";

    private NetworkACLItem acl(NetworkACLItem.Action action, NetworkACLItem.TrafficType direction,
            String protocol, Integer portStart, Integer portEnd, int number, List<String> cidrs) {
        NetworkACLItem item = Mockito.mock(NetworkACLItem.class);
        Mockito.when(item.getAction()).thenReturn(action);
        Mockito.when(item.getTrafficType()).thenReturn(direction);
        Mockito.when(item.getProtocol()).thenReturn(protocol);
        Mockito.when(item.getSourcePortStart()).thenReturn(portStart);
        Mockito.when(item.getSourcePortEnd()).thenReturn(portEnd);
        Mockito.when(item.getNumber()).thenReturn(number);
        Mockito.when(item.getSourceCidrList()).thenReturn(cidrs);
        Mockito.when(item.getState()).thenReturn(NetworkACLItem.State.Active);
        return item;
    }

    private TungstenRule onlyRuleOf(List<TungstenRule> rules) {
        // The last rule is always the terminating deny.
        assertEquals("expected one translated rule plus the terminating deny", 2, rules.size());
        return rules.get(0);
    }

    // --- Direction semantics ----------------------------------------------
    // NetworkACLItem has only getSourceCidrList(). It means "the far end"
    // relative to the tier: where traffic comes FROM on ingress, and where it
    // goes TO on egress. Getting this backwards silently permits or blocks the
    // wrong traffic, so both directions are pinned.

    @Test
    public void testIngressPutsTheRemoteCidrOnTheSourceAndTheTierOnTheDestination() {
        List<TungstenRule> rules = TungstenAclTranslator.translate(
            Collections.singletonList(acl(NetworkACLItem.Action.Allow, NetworkACLItem.TrafficType.Ingress,
                "tcp", 80, 80, 1, Collections.singletonList("192.0.2.0/24"))),
            TIER_CIDR);

        TungstenRule rule = onlyRuleOf(rules);
        assertEquals("192.0.2.0", rule.getSrcIpPrefix());
        assertEquals(24, rule.getSrcIpPrefixLen());
        assertEquals("10.1.1.0", rule.getDstIpPrefix());
        assertEquals(24, rule.getDstIpPrefixLen());
        assertEquals("ports apply to the side being connected to", 80, rule.getDstStartPort());
        assertEquals(80, rule.getDstEndPort());
    }

    @Test
    public void testEgressPutsTheTierOnTheSourceAndTheRemoteCidrOnTheDestination() {
        List<TungstenRule> rules = TungstenAclTranslator.translate(
            Collections.singletonList(acl(NetworkACLItem.Action.Allow, NetworkACLItem.TrafficType.Egress,
                "tcp", 443, 443, 1, Collections.singletonList("198.51.100.0/24"))),
            TIER_CIDR);

        TungstenRule rule = onlyRuleOf(rules);
        assertEquals("10.1.1.0", rule.getSrcIpPrefix());
        assertEquals(24, rule.getSrcIpPrefixLen());
        assertEquals("198.51.100.0", rule.getDstIpPrefix());
        assertEquals(24, rule.getDstIpPrefixLen());
        assertEquals(443, rule.getDstStartPort());
    }

    // --- Action, protocol, ports ------------------------------------------

    @Test
    public void testDenyRulesTranslateToDenyNotPass() {
        // Firewall rules in this plugin are allow-only; ACLs are not, and a
        // deny that silently became a pass would be a security hole.
        List<TungstenRule> rules = TungstenAclTranslator.translate(
            Collections.singletonList(acl(NetworkACLItem.Action.Deny, NetworkACLItem.TrafficType.Ingress,
                "tcp", 22, 22, 1, Collections.singletonList("192.0.2.0/24"))),
            TIER_CIDR);

        assertEquals(TungstenUtils.DENY_ACTION, onlyRuleOf(rules).getAction());
    }

    @Test
    public void testAllowRulesTranslateToPass() {
        List<TungstenRule> rules = TungstenAclTranslator.translate(
            Collections.singletonList(acl(NetworkACLItem.Action.Allow, NetworkACLItem.TrafficType.Ingress,
                "tcp", 22, 22, 1, Collections.singletonList("192.0.2.0/24"))),
            TIER_CIDR);

        assertEquals(TungstenUtils.PASS_ACTION, onlyRuleOf(rules).getAction());
    }

    @Test
    public void testAllProtocolBecomesAny() {
        List<TungstenRule> rules = TungstenAclTranslator.translate(
            Collections.singletonList(acl(NetworkACLItem.Action.Allow, NetworkACLItem.TrafficType.Ingress,
                "all", null, null, 1, Collections.singletonList("192.0.2.0/24"))),
            TIER_CIDR);

        assertEquals(TungstenUtils.ANY, onlyRuleOf(rules).getProtocol());
    }

    @Test
    public void testAbsentPortsBecomeMinusOne() {
        List<TungstenRule> rules = TungstenAclTranslator.translate(
            Collections.singletonList(acl(NetworkACLItem.Action.Allow, NetworkACLItem.TrafficType.Ingress,
                "icmp", null, null, 1, Collections.singletonList("192.0.2.0/24"))),
            TIER_CIDR);

        assertEquals(-1, onlyRuleOf(rules).getDstStartPort());
        assertEquals(-1, onlyRuleOf(rules).getDstEndPort());
    }

    // --- Multiple CIDRs, ordering, state ----------------------------------

    @Test
    public void testEachSourceCidrBecomesItsOwnRule() {
        List<TungstenRule> rules = TungstenAclTranslator.translate(
            Collections.singletonList(acl(NetworkACLItem.Action.Allow, NetworkACLItem.TrafficType.Ingress,
                "tcp", 80, 80, 1, Arrays.asList("192.0.2.0/24", "198.51.100.0/24"))),
            TIER_CIDR);

        assertEquals("two CIDRs plus the terminating deny", 3, rules.size());
        assertEquals("192.0.2.0", rules.get(0).getSrcIpPrefix());
        assertEquals("198.51.100.0", rules.get(1).getSrcIpPrefix());
    }

    @Test
    public void testRulesAreOrderedByAclNumber() {
        // OpenSDN evaluates policy rules in list order, and CloudStack ACL
        // ordering is the rule number, so the list must follow it.
        List<TungstenRule> rules = TungstenAclTranslator.translate(
            Arrays.asList(
                acl(NetworkACLItem.Action.Deny, NetworkACLItem.TrafficType.Ingress, "tcp", 22, 22, 30,
                    Collections.singletonList("203.0.113.0/24")),
                acl(NetworkACLItem.Action.Allow, NetworkACLItem.TrafficType.Ingress, "tcp", 80, 80, 10,
                    Collections.singletonList("192.0.2.0/24"))),
            TIER_CIDR);

        assertEquals(3, rules.size());
        assertEquals("the lower-numbered rule must come first", "192.0.2.0", rules.get(0).getSrcIpPrefix());
        assertEquals("203.0.113.0", rules.get(1).getSrcIpPrefix());
    }

    @Test
    public void testRevokedRulesAreNotProgrammed() {
        NetworkACLItem revoked = acl(NetworkACLItem.Action.Allow, NetworkACLItem.TrafficType.Ingress,
            "tcp", 80, 80, 1, Collections.singletonList("192.0.2.0/24"));
        Mockito.when(revoked.getState()).thenReturn(NetworkACLItem.State.Revoke);

        List<TungstenRule> rules = TungstenAclTranslator.translate(Collections.singletonList(revoked), TIER_CIDR);

        assertEquals("only the terminating deny should remain", 1, rules.size());
    }

    // --- The terminating deny ---------------------------------------------

    @Test
    public void testAListAlwaysEndsWithAnExplicitDenyAll() {
        // CloudStack ACLs have an implicit final deny. OpenSDN policy semantics
        // differ, so it has to be stated.
        List<TungstenRule> rules = TungstenAclTranslator.translate(
            Collections.singletonList(acl(NetworkACLItem.Action.Allow, NetworkACLItem.TrafficType.Ingress,
                "tcp", 80, 80, 1, Collections.singletonList("192.0.2.0/24"))),
            TIER_CIDR);

        TungstenRule last = rules.get(rules.size() - 1);
        assertEquals(TungstenUtils.DENY_ACTION, last.getAction());
        assertEquals(TungstenUtils.ANY, last.getProtocol());
        assertEquals(TungstenUtils.ALL_IP4_PREFIX, last.getSrcIpPrefix());
        assertEquals(0, last.getSrcIpPrefixLen());
        assertEquals(TungstenUtils.ALL_IP4_PREFIX, last.getDstIpPrefix());
        assertEquals(0, last.getDstIpPrefixLen());
    }

    @Test
    public void testAnEmptyAclStillDeniesEverything() {
        // An empty ACL must not translate to an empty policy, which would
        // inherit whatever the fabric does by default.
        List<TungstenRule> rules = TungstenAclTranslator.translate(Collections.emptyList(), TIER_CIDR);

        assertEquals(1, rules.size());
        assertEquals(TungstenUtils.DENY_ACTION, rules.get(0).getAction());
    }

    @Test
    public void testNullAclListIsTreatedAsEmpty() {
        List<TungstenRule> rules = TungstenAclTranslator.translate(null, TIER_CIDR);

        assertEquals(1, rules.size());
        assertEquals(TungstenUtils.DENY_ACTION, rules.get(0).getAction());
    }

    @Test
    public void testEveryRuleIsOneWaySoDirectionIsNotImplicitlyWidened() {
        // A two-way rule would permit the reverse flow as well, which is not
        // what a directional ACL entry means.
        List<TungstenRule> rules = TungstenAclTranslator.translate(
            Arrays.asList(
                acl(NetworkACLItem.Action.Allow, NetworkACLItem.TrafficType.Ingress, "tcp", 80, 80, 1,
                    Collections.singletonList("192.0.2.0/24")),
                acl(NetworkACLItem.Action.Allow, NetworkACLItem.TrafficType.Egress, "tcp", 443, 443, 2,
                    Collections.singletonList("198.51.100.0/24"))),
            TIER_CIDR);

        for (TungstenRule rule : rules) {
            assertTrue("rule " + rule.getAction() + " should be one-way",
                TungstenUtils.ONE_WAY_DIRECTION.equals(rule.getDirection()));
        }
    }
}
