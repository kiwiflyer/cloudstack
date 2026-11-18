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

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

public class TungstenUtilsTest {

    @Before
    @After
    public void resetDeploymentPrefix() {
        // The prefix is process-wide state; leaking it between tests would make
        // results depend on execution order.
        TungstenUtils.setDeploymentPrefix(null);
    }

    // --- Backward compatibility -------------------------------------------
    // With no prefix configured, every generated name must be byte-identical
    // to what the plugin produced before. Existing fabrics hold objects under
    // these exact names; a change here orphans them.

    @Test
    public void testNamesAreUnchangedWhenNoPrefixIsConfigured() {
        assertEquals("logicalRouter5", TungstenUtils.getLogicalRouterName(5L));
        assertEquals("publicNetwork1", TungstenUtils.getPublicNetworkName(1L));
        assertEquals("virtual-network-policy7", TungstenUtils.getVirtualNetworkPolicyName(7L));
        assertEquals("subnet-name3", TungstenUtils.getSubnetName(3L));
        assertEquals("floating-ip-pool2", TungstenUtils.getFloatingIpPoolName(2L));
        assertEquals("loadbalancer9", TungstenUtils.getLoadBalancerName(9L));
        assertEquals("vgw4", TungstenUtils.getVgwName(4L));
    }

    @Test
    public void testBlankPrefixIsTreatedAsNoPrefix() {
        TungstenUtils.setDeploymentPrefix("   ");
        assertEquals("logicalRouter5", TungstenUtils.getLogicalRouterName(5L));

        TungstenUtils.setDeploymentPrefix("");
        assertEquals("logicalRouter5", TungstenUtils.getLogicalRouterName(5L));
    }

    // --- The actual defect -------------------------------------------------

    @Test
    public void testPrefixMakesFabricObjectNamesUniquePerDeployment() {
        // apache/cloudstack#7462: two CloudStack deployments sharing one fabric
        // both produced "publicNetwork1" and "networkPolicy7".
        TungstenUtils.setDeploymentPrefix("acs-london");
        String londonPublic = TungstenUtils.getPublicNetworkName(1L);
        String londonPolicy = TungstenUtils.getVirtualNetworkPolicyName(7L);

        TungstenUtils.setDeploymentPrefix("acs-paris");
        String parisPublic = TungstenUtils.getPublicNetworkName(1L);
        String parisPolicy = TungstenUtils.getVirtualNetworkPolicyName(7L);

        assertEquals("acs-london-publicNetwork1", londonPublic);
        assertEquals("acs-paris-publicNetwork1", parisPublic);
        assertNotEquals("the same zone id in two deployments must not collide", londonPublic, parisPublic);
        assertNotEquals("the same network id in two deployments must not collide", londonPolicy, parisPolicy);
    }

    @Test
    public void testPrefixIsTrimmed() {
        TungstenUtils.setDeploymentPrefix("  acs-london  ");
        assertEquals("acs-london-publicNetwork1", TungstenUtils.getPublicNetworkName(1L));
    }

    @Test
    public void testPrefixAppliesAcrossTheCollidingNameFamilies() {
        TungstenUtils.setDeploymentPrefix("acs");

        assertEquals("acs-logicalRouter5", TungstenUtils.getLogicalRouterName(5L));
        assertEquals("acs-subnet-name3", TungstenUtils.getSubnetName(3L));
        assertEquals("acs-floating-ip-pool2", TungstenUtils.getFloatingIpPoolName(2L));
        assertEquals("acs-floating-ip8", TungstenUtils.getFloatingIpName(8L));
        assertEquals("acs-loadbalancer9", TungstenUtils.getLoadBalancerName(9L));
        assertEquals("acs-loadbalancer-pool6", TungstenUtils.getLoadBalancerPoolName(6L));
        assertEquals("acs-vgw4", TungstenUtils.getVgwName(4L));
        assertEquals("acs-internalGatewayVmi2", TungstenUtils.getNetworkGatewayVmiName(2L));
        assertEquals("acs-sharedNetwork3", TungstenUtils.getSharedNetworkName(3L));
    }

    // --- Names that must never be prefixed ---------------------------------

    @Test
    public void testHostTapDeviceNameIsNeverPrefixed() {
        // Not a fabric object: this is a Linux tap device on the KVM host, and
        // prefixing it would break host networking (and blow the 15-char IFNAMSIZ
        // limit).
        TungstenUtils.setDeploymentPrefix("acs-london");
        assertEquals("tap02fcf3d683c3", TungstenUtils.getTapName("02:fc:f3:d6:83:c3"));
    }

    @Test
    public void testSnatNetworkStartNameIsNeverPrefixed() {
        // This reconstructs a name the FABRIC generates for the logical router's
        // SNAT service instance. It must match what the fabric produced, so a
        // prefix would break the lookup. It already carries the router UUID and
        // is unique anyway.
        TungstenUtils.setDeploymentPrefix("acs-london");
        assertEquals("default-domain__myproject__snat_abc-123",
            TungstenUtils.getSnatNetworkStartName(Arrays.asList("default-domain", "myproject"), "abc-123"));
    }

    @Test
    public void testVrfNetworkNameIsNeverPrefixed() {
        // Derived from a fabric-supplied qualified name, not a name we invent.
        TungstenUtils.setDeploymentPrefix("acs-london");
        assertEquals("default-domain:myproject:net1:net1",
            TungstenUtils.getVrfNetworkName(Arrays.asList("default-domain", "myproject", "net1")));
    }

    @Test
    public void testGuestNetworkNameIsNotPrefixedBecauseItCarriesTheUuid() {
        // Already globally unique; prefixing would only lengthen it and would
        // change names on existing fabrics for no benefit.
        TungstenUtils.setDeploymentPrefix("acs-london");
        assertEquals("guestNetworkmynet" + "f47ac10b",
            TungstenUtils.getGuestNetworkName("mynet", "f47ac10b"));
    }

    @Test
    public void testVmiAndInstanceIpNamesArePrefixedOnBothBranches() {
        // Both branches build from CloudStack-local values: the nic id, or the
        // VM's internal name. Two deployments both have an "i-2-34-VM".
        // Note: the guest branch is selected by trafficType.equals("Guest"),
        // capitalised. A lowercase value falls through to the vmName branch.
        assertEquals("vmiGuestUser12", TungstenUtils.getVmiName("Guest", "User", "i-2-34-VM", 12L));
        assertEquals("vmiManagementUseri-2-34-VM",
            TungstenUtils.getVmiName("Management", "User", "i-2-34-VM", 0L));

        TungstenUtils.setDeploymentPrefix("acs");
        assertEquals("acs-vmiGuestUser12", TungstenUtils.getVmiName("Guest", "User", "i-2-34-VM", 12L));
        assertEquals("acs-vmiManagementUseri-2-34-VM",
            TungstenUtils.getVmiName("Management", "User", "i-2-34-VM", 0L));
        assertEquals("acs-instanceIpGuestUser12",
            TungstenUtils.getInstanceIpName("Guest", "User", "i-2-34-VM", 12L));
        assertEquals("acs-instanceV6IpGuestUser12",
            TungstenUtils.getV6InstanceIpName("Guest", "User", "i-2-34-VM", 12L));
    }

    // --- VPC logical router identity (apache/cloudstack#7208) -------------

    @Test
    public void testVpcLogicalRouterUuidIsDerivedFromTheVpcAndIsStable() {
        // Tiers implement after the VPC, in separate calls, and must reach the
        // same router without a lookup or a stored id. Deriving it from the
        // VPC's own UUID makes it reproducible from the VPC alone.
        String first = TungstenUtils.getVpcLogicalRouterUuid("f47ac10b-58cc-4372-a567-0e02b2c3d479");
        String second = TungstenUtils.getVpcLogicalRouterUuid("f47ac10b-58cc-4372-a567-0e02b2c3d479");

        assertEquals(first, second);
        assertEquals("must be a well-formed UUID", 36, first.length());
        java.util.UUID.fromString(first);
    }

    @Test
    public void testDifferentVpcsGetDifferentLogicalRouterUuids() {
        assertNotEquals(
            TungstenUtils.getVpcLogicalRouterUuid("f47ac10b-58cc-4372-a567-0e02b2c3d479"),
            TungstenUtils.getVpcLogicalRouterUuid("9f1a7c52-1d4e-4b2a-9c3f-7a5e2d8b6c10"));
    }

    @Test
    public void testVpcLogicalRouterUuidIsNotTheVpcUuidItself() {
        // They name different objects in different systems; reusing the VPC's
        // own UUID for a fabric object invites confusion.
        String vpcUuid = "f47ac10b-58cc-4372-a567-0e02b2c3d479";
        assertNotEquals(vpcUuid, TungstenUtils.getVpcLogicalRouterUuid(vpcUuid));
    }

    @Test
    public void testVpcLogicalRouterUuidIsIndependentOfTheDeploymentPrefix() {
        // The prefix disambiguates NAMES. A UUID is already globally unique, and
        // changing it with the prefix would make an existing VPC's router
        // unreachable if the prefix were ever set.
        String withoutPrefix = TungstenUtils.getVpcLogicalRouterUuid("f47ac10b-58cc-4372-a567-0e02b2c3d479");
        TungstenUtils.setDeploymentPrefix("acs-london");
        assertEquals(withoutPrefix, TungstenUtils.getVpcLogicalRouterUuid("f47ac10b-58cc-4372-a567-0e02b2c3d479"));
    }
}
