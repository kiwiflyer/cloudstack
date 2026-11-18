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
import com.cloud.utils.Pair;
import com.cloud.utils.TungstenUtils;
import com.cloud.utils.net.NetUtils;
import org.apache.cloudstack.network.tungsten.model.TungstenRule;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Translates a VPC tier's network ACL into the rules of the OpenSDN network
 * policy attached to that tier.
 *
 * This is the one genuinely new mapping VPC needs; the rest of VPC support
 * re-scopes primitives the plugin already drives. Kept separate from
 * {@code TungstenElement} so it can be tested on its own -- the equivalent
 * firewall-rule conversion is a private method on a 1,100-line class and is
 * effectively untestable.
 *
 * Pure: no fabric calls, no CloudStack lookups.
 */
public final class TungstenAclTranslator {

    private TungstenAclTranslator() {
    }

    /**
     * @param aclItems the tier's ACL entries, in any order; may be null
     * @param tierCidr the tier's own CIDR, the local end of every rule
     * @return policy rules in evaluation order, always ending in a deny-all
     */
    public static List<TungstenRule> translate(final List<? extends NetworkACLItem> aclItems, final String tierCidr) {
        final List<TungstenRule> rules = new ArrayList<>();
        final Pair<String, Integer> tier = NetUtils.getCidr(tierCidr);

        for (NetworkACLItem item : ordered(aclItems)) {
            if (item.getState() == NetworkACLItem.State.Revoke) {
                // Revoked entries are on their way out and must not be programmed.
                continue;
            }
            rules.addAll(translateItem(item, tier));
        }

        // CloudStack ACLs carry an implicit final deny. OpenSDN policy semantics
        // differ, so it is stated explicitly -- including for an empty ACL, which
        // must not become an empty policy that inherits the fabric's default.
        rules.add(denyAll());
        return rules;
    }

    private static List<NetworkACLItem> ordered(final List<? extends NetworkACLItem> aclItems) {
        if (aclItems == null || aclItems.isEmpty()) {
            return Collections.emptyList();
        }
        final List<NetworkACLItem> sorted = new ArrayList<>(aclItems);
        // OpenSDN evaluates policy rules in list order; CloudStack orders ACL
        // entries by rule number, so the list has to follow it.
        sorted.sort(Comparator.comparingInt(NetworkACLItem::getNumber));
        return sorted;
    }

    /**
     * One rule per CIDR in the entry.
     *
     * {@link NetworkACLItem} exposes only {@code getSourceCidrList()}, and it
     * means the far end relative to the tier: where traffic comes from on
     * ingress, and where it goes to on egress.
     */
    private static List<TungstenRule> translateItem(final NetworkACLItem item, final Pair<String, Integer> tier) {
        final List<TungstenRule> rules = new ArrayList<>();
        final String action = item.getAction() == NetworkACLItem.Action.Deny
            ? TungstenUtils.DENY_ACTION : TungstenUtils.PASS_ACTION;
        final String protocol = protocolOf(item);
        final int portStart = item.getSourcePortStart() == null ? -1 : item.getSourcePortStart();
        final int portEnd = item.getSourcePortEnd() == null ? -1 : item.getSourcePortEnd();
        final boolean ingress = item.getTrafficType() == NetworkACLItem.TrafficType.Ingress;

        final List<String> remoteCidrs = item.getSourceCidrList() == null
            ? Collections.singletonList(NetUtils.ALL_IP4_CIDRS) : item.getSourceCidrList();

        for (String remoteCidr : remoteCidrs) {
            final Pair<String, Integer> remote = NetUtils.getCidr(remoteCidr);
            final Pair<String, Integer> source = ingress ? remote : tier;
            final Pair<String, Integer> destination = ingress ? tier : remote;

            // Ports describe the service being connected to, so they sit on the
            // destination, matching how firewall rules are already converted.
            // One-way: a directional ACL entry must not implicitly permit the
            // reverse flow.
            rules.add(new TungstenRule(item.getUuid(), action, TungstenUtils.ONE_WAY_DIRECTION, protocol,
                TungstenUtils.ANY, source.first(), source.second(), -1, -1,
                TungstenUtils.ANY, destination.first(), destination.second(), portStart, portEnd));
        }
        return rules;
    }

    private static String protocolOf(final NetworkACLItem item) {
        final String protocol = item.getProtocol();
        if (protocol == null || NetUtils.ALL_PROTO.equalsIgnoreCase(protocol)) {
            return TungstenUtils.ANY;
        }
        return protocol;
    }

    private static TungstenRule denyAll() {
        return new TungstenRule(TungstenUtils.DENY_ACTION, TungstenUtils.ONE_WAY_DIRECTION, TungstenUtils.ANY,
            TungstenUtils.ANY, TungstenUtils.ALL_IP4_PREFIX, 0, -1, -1,
            TungstenUtils.ANY, TungstenUtils.ALL_IP4_PREFIX, 0, -1, -1);
    }
}
