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
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class OpenSdnVersionDetectorTest {

    private OpenSdnVersionDetector detectorReturning(final String probed) {
        return (hostname, port) -> probed;
    }

    @Test
    public void testOperatorDeclarationWinsOverProbe() {
        OpenSdnVersionDetector detector = detectorReturning("24.1");

        OpenSdnVersionDetector.VersionResult result = detector.resolve("25.0", "host", "8082");

        assertEquals("25.0", result.getVersion());
        assertFalse("a declared version is not a detected one", result.isDetected());
    }

    @Test
    public void testDeclaredVersionIsTrimmed() {
        OpenSdnVersionDetector detector = detectorReturning(null);

        OpenSdnVersionDetector.VersionResult result = detector.resolve("  24.1  ", "host", "8082");

        assertEquals("24.1", result.getVersion());
    }

    @Test
    public void testProbeIsUsedWhenNothingWasDeclared() {
        OpenSdnVersionDetector detector = detectorReturning("24.1");

        OpenSdnVersionDetector.VersionResult result = detector.resolve(null, "host", "8082");

        assertEquals("24.1", result.getVersion());
        assertTrue(result.isDetected());
    }

    @Test
    public void testBlankDeclarationFallsThroughToTheProbe() {
        OpenSdnVersionDetector detector = detectorReturning("24.1");

        OpenSdnVersionDetector.VersionResult result = detector.resolve("   ", "host", "8082");

        assertEquals("24.1", result.getVersion());
        assertTrue(result.isDetected());
    }

    @Test
    public void testFailedProbeYieldsUnknownWhichResolvesToBaseline() {
        OpenSdnVersionDetector detector = detectorReturning(null);

        OpenSdnVersionDetector.VersionResult result = detector.resolve(null, "host", "8082");

        assertNull("a failed probe must not invent a version", result.getVersion());
        assertFalse(result.isDetected());
        assertFalse("and must not enable newer capabilities",
            SdnCapability.MAC_IP_LEARNING.isSupportedBy(result.getVersion()));
        assertTrue("while baseline capabilities stay available",
            SdnCapability.FABRIC_SNAT.isSupportedBy(result.getVersion()));
    }

    @Test
    public void testBlankProbeResultIsTreatedAsFailure() {
        OpenSdnVersionDetector detector = detectorReturning("  ");

        OpenSdnVersionDetector.VersionResult result = detector.resolve(null, "host", "8082");

        assertNull(result.getVersion());
        assertFalse(result.isDetected());
    }

    @Test
    public void testAThrowingProbeDoesNotBlockRegistration() {
        // A controller that is unreachable, firewalled, or not OpenSDN at all
        // must not prevent a provider being registered.
        OpenSdnVersionDetector detector = (hostname, port) -> {
            throw new RuntimeException("controller unreachable");
        };

        OpenSdnVersionDetector.VersionResult result = detector.resolve(null, "host", "8082");

        assertNull(result.getVersion());
        assertFalse(result.isDetected());
    }
}
