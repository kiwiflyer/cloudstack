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

/**
 * Works out which OpenSDN version a provider talks to.
 *
 * Resolution order: an operator declaration always wins, then a controller
 * probe, then unknown. Unknown resolves to the baseline capability set, so a
 * controller that cannot be probed - unreachable, firewalled, or not OpenSDN
 * at all - never gets capabilities it may not support.
 *
 * The probe sits behind this interface so it can be replaced or disabled
 * without touching callers.
 */
@FunctionalInterface
public interface OpenSdnVersionDetector {

    /** A resolved version, and whether it came from the controller or the operator. */
    class VersionResult {
        private final String version;
        private final boolean detected;

        public VersionResult(String version, boolean detected) {
            this.version = version;
            this.detected = detected;
        }

        /** The controller version, or null when unknown. Null resolves to baseline. */
        public String getVersion() {
            return version;
        }

        /** True when probed from the controller, false when operator-declared. */
        public boolean isDetected() {
            return detected;
        }
    }

    /**
     * Probe the controller for its version.
     *
     * @return the version, or null when it cannot be determined. Implementations
     *         should not throw, but {@link #resolve} tolerates one that does.
     */
    String probe(String hostname, String port);

    /**
     * The full resolution chain.
     *
     * @param declaredVersion what the operator supplied, may be null or blank
     */
    default VersionResult resolve(String declaredVersion, String hostname, String port) {
        if (declaredVersion != null && !declaredVersion.trim().isEmpty()) {
            return new VersionResult(declaredVersion.trim(), false);
        }

        String probed = null;
        try {
            probed = probe(hostname, port);
        } catch (RuntimeException ignored) {
            // A probe failure must never block provider registration.
        }
        if (probed != null && !probed.trim().isEmpty()) {
            return new VersionResult(probed.trim(), true);
        }

        // Unknown. Never invent a version from a failed probe.
        return new VersionResult(null, false);
    }
}
