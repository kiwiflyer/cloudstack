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
 * What an OpenSDN controller supports, resolved from its version.
 *
 * An unknown version resolves to the baseline: capabilities available on every
 * supported controller stay on, everything newer is off. A probe that fails
 * must never enable something the controller cannot honour.
 *
 * OpenSDN is the only supported controller. A Tungsten Fabric controller is not
 * blocked, since the API is compatible, but it reports no OpenSDN version and
 * so resolves to baseline with newer capabilities off.
 */
public enum SdnCapability {

    /**
     * IP fabric SNAT, giving a guest network a gatewayless path to the
     * underlay. {@code VirtualNetwork.setFabricSnat}, available on every
     * supported controller.
     */
    FABRIC_SNAT(null),

    /**
     * MAC-IP learning: the dynamic address learning / IPVLAN feature.
     * {@code VirtualNetwork.setMacIpLearningEnable}.
     */
    MAC_IP_LEARNING("24.1"),

    /**
     * ECMP hash field selection, used for flow stickiness.
     * {@code setEcmpHashingIncludeFields} on VirtualNetwork and
     * GlobalVrouterConfig.
     */
    ECMP_HASH_FIELDS("24.1"),

    /**
     * User-defined values on predefined tag types. {@code Tag.setPredefined},
     * {@code Tag.setValue}, {@code TagType.setId}.
     */
    USER_DEFINED_TAG_VALUES("24.1"),

    /**
     * Configurable virtual network MTU.
     *
     * Permanently unsupported, regardless of controller version. The
     * juniper-tungsten-api 2.0 bindings' VirtualNetworkType exposes only
     * allowTransit, forwardingMode, maxFlows, mirrorDestination, networkId, rpf
     * and vxlanNetworkIdentifier -- there is no mtu accessor to call. Enabling
     * this needs bindings regenerated from the OpenSDN schema. Declared here so
     * the gap is explicit rather than silently absent.
     */
    VN_MTU(null) {
        @Override
        public boolean isSupportedBy(String version) {
            return false;
        }

        @Override
        public boolean isBaseline() {
            return false;
        }
    };

    private final String minimumVersion;

    SdnCapability(String minimumVersion) {
        this.minimumVersion = minimumVersion;
    }

    /**
     * True when this capability is available on every supported controller and
     * therefore needs no version check.
     */
    public boolean isBaseline() {
        return minimumVersion == null;
    }

    /**
     * @param version the controller version, or null/blank when unknown.
     */
    public boolean isSupportedBy(String version) {
        if (isBaseline()) {
            return true;
        }
        if (version == null || version.trim().isEmpty()) {
            // Unknown version: baseline only. Never assume the newest.
            return false;
        }
        return compareVersions(version, minimumVersion) >= 0;
    }

    /**
     * Compares dotted versions segment by segment, numerically. Missing
     * segments count as zero, so "24.1" and "24.1.0" are equal, and a trailing
     * qualifier is ignored, so "24.1-rc1" equals "24.1".
     */
    static int compareVersions(String left, String right) {
        String[] leftSegments = left.trim().split("\\.");
        String[] rightSegments = right.trim().split("\\.");
        int length = Math.max(leftSegments.length, rightSegments.length);
        for (int i = 0; i < length; i++) {
            int l = i < leftSegments.length ? parseSegment(leftSegments[i]) : 0;
            int r = i < rightSegments.length ? parseSegment(rightSegments[i]) : 0;
            if (l != r) {
                return Integer.compare(l, r);
            }
        }
        return 0;
    }

    /** Leading digits of a segment; anything else counts as zero. */
    private static int parseSegment(String segment) {
        int end = 0;
        while (end < segment.length() && Character.isDigit(segment.charAt(end))) {
            end++;
        }
        return end == 0 ? 0 : Integer.parseInt(segment.substring(0, end));
    }
}
