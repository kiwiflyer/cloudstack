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
 * The default version detector: it never probes, so the controller version is
 * whatever the operator declared when registering the provider.
 *
 * <h3>Why there is no probe yet</h3>
 *
 * Auto-detection was specified, and the resolution chain in
 * {@link OpenSdnVersionDetector#resolve} is built for it. It is deliberately
 * not implemented, because the response an OpenSDN controller gives has not
 * been verified against a real deployment.
 *
 * The asymmetry matters. A probe that fails and returns null is harmless: the
 * version stays unknown and {@link SdnCapability} falls back to baseline. A
 * probe that succeeds but misreads the version as *newer* than reality is not
 * harmless -- it switches on capabilities the controller cannot honour, and the
 * failure surfaces later as confusing errors at the API boundary. Guessing the
 * payload format risks exactly that, so until someone can capture a real
 * response, declaring the version explicitly is both safer and more honest.
 *
 * <h3>Adding one</h3>
 *
 * Implement {@link OpenSdnVersionDetector#probe} against the controller's
 * config API on the provider's configured port, returning null on every failure
 * path, and register that bean in place of this one. The resolution chain,
 * precedence rules and capability gating need no changes.
 */
public class DeclaredOnlyVersionDetector implements OpenSdnVersionDetector {

    @Override
    public String probe(String hostname, String port) {
        return null;
    }
}
