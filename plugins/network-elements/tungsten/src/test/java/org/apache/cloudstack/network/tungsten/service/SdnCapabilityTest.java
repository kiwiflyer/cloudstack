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

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SdnCapabilityTest {

    @Test
    public void testFabricSnatIsAlwaysAvailable() {
        assertTrue(SdnCapability.FABRIC_SNAT.isSupportedBy("24.1"));
        assertTrue("baseline capabilities survive an unknown version",
            SdnCapability.FABRIC_SNAT.isSupportedBy(null));
    }

    @Test
    public void testUnknownVersionGetsBaselineNotNewest() {
        // The whole point of the fallback: a failed probe, or a controller that
        // is not OpenSDN at all, must not enable anything it may not support.
        assertFalse(SdnCapability.MAC_IP_LEARNING.isSupportedBy(null));
        assertFalse(SdnCapability.ECMP_HASH_FIELDS.isSupportedBy(null));
        assertFalse(SdnCapability.USER_DEFINED_TAG_VALUES.isSupportedBy(null));
    }

    @Test
    public void testBlankVersionIsTreatedAsUnknown() {
        assertFalse(SdnCapability.MAC_IP_LEARNING.isSupportedBy(""));
        assertFalse(SdnCapability.MAC_IP_LEARNING.isSupportedBy("   "));
    }

    @Test
    public void testVersionedCapabilitiesRequireTheirMinimum() {
        assertTrue(SdnCapability.MAC_IP_LEARNING.isSupportedBy("24.1"));
        assertTrue(SdnCapability.MAC_IP_LEARNING.isSupportedBy("25.0"));
        assertFalse(SdnCapability.MAC_IP_LEARNING.isSupportedBy("21.12"));
    }

    @Test
    public void testVnMtuIsNeverSupportedUntilBindingsAreRefreshed() {
        // juniper-tungsten-api 2.0's VirtualNetworkType has no mtu accessor, so
        // this cannot be expressed regardless of what the controller supports.
        assertFalse(SdnCapability.VN_MTU.isSupportedBy("99.0"));
        assertFalse(SdnCapability.VN_MTU.isSupportedBy("24.1"));
        assertFalse(SdnCapability.VN_MTU.isSupportedBy(null));
    }

    @Test
    public void testVersionComparisonHandlesUnevenAndNonNumericSegments() {
        assertEquals(0, SdnCapability.compareVersions("24.1", "24.1.0"));
        assertTrue(SdnCapability.compareVersions("25.0", "24.1") > 0);
        assertTrue(SdnCapability.compareVersions("21.12", "24.1") < 0);
        assertEquals(0, SdnCapability.compareVersions("24.1-rc1", "24.1"));
    }

    @Test
    public void testVersionComparisonOrdersMinorSegmentsNumericallyNotLexically() {
        // "24.10" must be newer than "24.9". A string comparison gets this wrong.
        assertTrue(SdnCapability.compareVersions("24.10", "24.9") > 0);
    }

    @Test
    public void testEveryCapabilityIsSafeAtBaseline() {
        // Guards the invariant rather than each capability individually: any
        // capability added later with a minimum version must default to off
        // when the version is unknown.
        for (SdnCapability capability : SdnCapability.values()) {
            boolean supportedAtUnknown = capability.isSupportedBy(null);
            if (supportedAtUnknown) {
                assertTrue(capability + " is on at an unknown version, so it must be declared baseline",
                    capability.isBaseline());
            }
        }
    }
}
