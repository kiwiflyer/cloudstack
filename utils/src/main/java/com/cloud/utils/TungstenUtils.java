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
package com.cloud.utils;

import com.cloud.utils.net.NetUtils;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class TungstenUtils {

    /**
     * Distinguishes the fabric objects of one CloudStack deployment from
     * another's when several share a controller (apache/cloudstack#7462).
     *
     * Empty by default, which produces exactly the names the plugin has always
     * produced. That default matters: an existing fabric holds objects under
     * the unprefixed names, and changing them would orphan every one of them.
     * Setting a prefix on an established deployment therefore needs the fabric
     * objects renamed to match; it is intended for new deployments, or for a
     * deliberate migration.
     *
     * Process-wide because the naming helpers are static. Set once at plugin
     * startup from the opensdn.deployment.prefix setting.
     */
    private static volatile String deploymentPrefix = "";

    public static void setDeploymentPrefix(final String prefix) {
        deploymentPrefix = prefix == null ? "" : prefix.trim();
    }

    public static String getDeploymentPrefix() {
        return deploymentPrefix;
    }

    /**
     * Qualifies a fabric object name with the deployment prefix.
     *
     * Only for names this plugin invents from CloudStack-local identifiers.
     * Names that must match what the fabric itself generates, and names of
     * things that are not fabric objects at all, must not pass through here.
     */
    private static String scoped(final String name) {
        return deploymentPrefix.isEmpty() ? name : deploymentPrefix + "-" + name;
    }
    private static final String GUEST_TYPE = "Guest";
    private static final String SECURITY_GROUP = "securityGroup";
    public static final String INGRESS_RULE = "ingress";
    public static final String EGRESS_RULE = "egress";
    public static final String LOCAL = "local";
    public static final String DEFAULT_VHOST_INTERFACE = "vhost0";
    public static final String DEFAULT_FORWARDING_MODE = "l3";
    public static final int MAX_CIDR = 32;
    public static final int MAX_IPV6_CIDR = 128;
    public static final int DNS_SERVICE_PORT = 53;
    public static final int NTP_SERVICE_PORT = 123;
    public static final int WEB_SERVICE_PORT = 8080;
    public static final String IPV4 = "IPv4";
    public static final String IPV6 = "IPv6";
    public static final String ALL_IP4_PREFIX = "0.0.0.0";
    public static final String ALL_IP6_PREFIX = "::";
    public static final String ANY = "any";
    public static final String DENY_ACTION = "deny";
    public static final String PASS_ACTION = "pass";
    public static final String ONE_WAY_DIRECTION = ">";
    public static final String TWO_WAY_DIRECTION = "<>";
    public static final String FABRIC_NETWORK_FQN = "default-domain:default-project:ip-fabric";
    public static final String ROUTINGLR_NAME = "routingLR";
    public static final String GUEST_NETWORK_NAME = "guestNetwork";
    public static final String PUBLIC_NETWORK_NAME = "publicNetwork";
    public static final String MANAGEMENT_NETWORK_NAME = "managementNetwork";
    public static final String SHARED_NETWORK_NAME = "sharedNetwork";
    public static final String SNAT_NETWORK_END_NAME = "right";

    private TungstenUtils() {
    }

    public static String getTapName(final String macAddress) {
        return "tap" + macAddress.replace(":", "");
    }

    public static String getVmiName(String trafficType, String vmType, String vmName, long nicId) {
        if (nicId != 0 && trafficType.equals(GUEST_TYPE))
            return scoped("vmi" + trafficType + vmType + nicId);
        else
            return scoped("vmi" + trafficType + vmType + vmName);
    }

    public static String getInstanceIpName(String trafficType, String vmType, String vmName, long nicId) {
        if (nicId != 0 && trafficType.equals(GUEST_TYPE))
            return scoped("instanceIp" + trafficType + vmType + nicId);
        else
            return scoped("instanceIp" + trafficType + vmType + vmName);
    }

    public static String getV6InstanceIpName(String trafficType, String vmType, String vmName, long nicId) {
        if (nicId != 0 && trafficType.equals(GUEST_TYPE))
            return scoped("instanceV6Ip" + trafficType + vmType + nicId);
        else
            return scoped("instanceV6Ip" + trafficType + vmType + vmName);
    }

    public static String getSecondaryInstanceIpName(long nicSecondaryIpId) {
        return scoped("instanceIpSecondaryIp" + nicSecondaryIpId);
    }

    /**
     * The logical router that backs a VPC. Distinct from
     * {@link #getLogicalRouterName(long)}, which is per isolated network --
     * a VPC's router is shared by all of its tiers.
     */
    public static String getVpcLogicalRouterName(long vpcId) {
        return scoped("vpcLogicalRouter" + vpcId);
    }

    /**
     * The UUID of the logical router backing a VPC, derived from the VPC's own
     * UUID (apache/cloudstack#7208).
     *
     * A VPC's tiers are implemented in separate calls, after the VPC itself, and
     * each needs to attach to the same router. Deriving the router's UUID from
     * the VPC makes it reachable from the VPC alone -- no lookup by name, and no
     * identifier to store and keep in step.
     *
     * Deliberately NOT affected by the deployment prefix: a prefix disambiguates
     * names, while a UUID is already globally unique. Making it prefix-dependent
     * would make an existing VPC's router unreachable the moment a prefix was
     * set.
     */
    public static String getVpcLogicalRouterUuid(final String vpcUuid) {
        return UUID.nameUUIDFromBytes(("opensdn-vpc-logical-router:" + vpcUuid)
            .getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString();
    }

    /** The network policy carrying a VPC tier's network ACL. */
    public static String getVpcTierAclPolicyName(long tierNetworkId) {
        return scoped("vpc-tier-acl-policy" + tierNetworkId);
    }

    public static String getLogicalRouterName(long networkId) {
        return scoped("logicalRouter" + networkId);
    }

    public static String getNetworkGatewayVmiName(long vnId) {
        return scoped("internalGatewayVmi" + vnId);
    }

    public static String getRoutingGatewayVmiName(String routerName, String networkName) {
        return scoped("routingGatewayVmi" + routerName + networkName);
    }

    public static String getNetworkGatewayIiName(long pvnId) {
        return scoped("internalGatewayIi" + pvnId);
    }

    public static String getRoutingGatewayIiName(String routerName, String networkName) {
        return scoped("routingGatewayIi" + routerName + networkName);
    }

    public static String getPublicNetworkName(long zoneId) {
        return scoped(PUBLIC_NETWORK_NAME + zoneId);
    }

    public static String getGuestNetworkName(String networkName, String networkUuid) {
        return GUEST_NETWORK_NAME + networkName + networkUuid;
    }

    public static String getSharedNetworkName(long networkId) {
        return scoped(SHARED_NETWORK_NAME + networkId);
    }

    public static String getManagementNetworkName(long mvnId) {
        return scoped(MANAGEMENT_NETWORK_NAME + mvnId);
    }


    public static String getVgwName(long zoneId) {
        return scoped("vgw" + zoneId);
    }

    public static String getSgVgwName(long networkId) {
        return scoped("sgvgw" + networkId);
    }

    public static String getVrfNetworkName(List<String> networkQualifiedName) {
        List<String> vrfList = new ArrayList<>(networkQualifiedName);
        vrfList.add(networkQualifiedName.get(networkQualifiedName.size() - 1));
        return StringUtils.join(vrfList, ":");
    }

    public static String getFloatingIpPoolName(long zoneId) {
        return scoped("floating-ip-pool" + zoneId);
    }

    public static String getFloatingIpName(long nicId) {
        return scoped("floating-ip" + nicId);
    }

    public static String getSnatNetworkStartName(List<String> projectFqn, String logicalRouterUuid) {
        return StringUtils.join(projectFqn, "__") + "__snat_" + logicalRouterUuid;
    }

    public static String getPublicNetworkPolicyName(long publicIpAddressId) {
        return scoped("public-network-policy" + publicIpAddressId);
    }

    public static String getManagementPolicyName(long zoneId) {
        return scoped("management-network-policy" + zoneId);
    }

    public static String getFabricPolicyName(long zoneId) {
        return scoped("fabric-network-policy" + zoneId);
    }

    public static String getVirtualNetworkPolicyName(long networkId) {
        return scoped("virtual-network-policy" + networkId);
    }

    public static String getDefaultPublicNetworkPolicyName(long zoneId) {
        return scoped("default-public-network-policy" + zoneId);
    }

    public static String getDefaultPublicSubnetPolicyName(long subnetId) {
        return scoped("default-public-subnet-policy" + subnetId);
    }

    public static String getRuleNetworkPolicyName(long ruleId) {
        return scoped("rule-network-policy" + ruleId);
    }

    public static String getSubnetName(long networkId) {
        return scoped("subnet-name" + networkId);
    }

    public static String getIPV4SubnetName(long networkId) {
        return scoped("subnet-name-IPV4" + networkId);
    }

    public static String getIPV6SubnetName(long networkId) {
        return scoped("subnet-name-IPV6" + networkId);
    }

    public static String getLoadBalancerVmiName(long publicIpId) {
        return scoped("loadbalancer-vmi" + publicIpId);
    }

    public static String getLoadBalancerIiName(long publicIpId) {
        return scoped("loadbalancer-ii" + publicIpId);
    }

    public static String getLoadBalancerName(long publicIpId) {
        return scoped("loadbalancer" + publicIpId);
    }

    public static String getLoadBalancerListenerName(long ruleId) {
        return scoped("loadbalancer-listener" + ruleId);
    }

    public static String getLoadBalancerHealthMonitorName(long publicIpId) {
        return scoped("loadbalancer-healthmonitor" + publicIpId);
    }

    public static String getLoadBalancerPoolName(long ruleId) {
        return scoped("loadbalancer-pool" + ruleId);
    }

    public static String getLoadBalancerMemberName(long ruleId, String memberIp) {
        return scoped("loadbalancer-member" + ruleId + memberIp);
    }

    public static String getLoadBalancerAlgorithm(String algorithm) {
        switch (algorithm) {
            case "leastconn":
                return "LEAST_CONNECTIONS";
            case "source":
                return "SOURCE_IP";
            default:
                return "ROUND_ROBIN";
        }
    }

    public static String getLoadBalancerSession(String stickiness) {
        switch (stickiness) {
            case "LbCookie":
                return "HTTP_COOKIE";
            case "AppCookie":
                return "APP_COOKIE";
            case "SourceBased":
                return "SOURCE_IP";
            default:
                return null;
        }
    }

    public static String getEthertTypeFromCidr(String cidr) {
        if (NetUtils.isValidIp4Cidr(cidr)) {
            return IPV4;
        } else {
            return IPV6;
        }
    }

    public static String getTungstenProtocol(String protocol, String cidr) {
        switch (protocol) {
            case NetUtils.TCP_PROTO:
                return NetUtils.TCP_PROTO;
            case NetUtils.UDP_PROTO:
                return NetUtils.UDP_PROTO;
            case NetUtils.ICMP_PROTO:
                if (NetUtils.isValidIp4Cidr(cidr))
                    return NetUtils.ICMP_PROTO;
                else
                    return NetUtils.ICMP6_PROTO;
            default:
                return NetUtils.ANY_PROTO;
        }
    }

    public static String getSecurityGroupName(String name, long accountId) {
        return scoped(SECURITY_GROUP + name + accountId);
    }

    public static String getSingleIpAddressCidr(String ipAddress) {
        if (NetUtils.isValidIp4(ipAddress)) {
            return ipAddress + "/" + MAX_CIDR;
        }

        if (NetUtils.isValidIp6(ipAddress)) {
            return ipAddress + "/" + MAX_IPV6_CIDR;
        }

        return null;
    }

    public static String getNameFromNetwork(String network) {
        if (network.startsWith(GUEST_NETWORK_NAME)) {
            String[] name = network.split("-");
            return name[0].replaceFirst(GUEST_NETWORK_NAME, "");
        } else {
            return network;
        }
    }
}
