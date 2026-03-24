/*
 * Copyright (C) 2026 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.internal.telephony.data;

import static com.google.common.truth.Truth.assertThat;

import android.net.NetworkCapabilities;

import com.android.internal.telephony.TelephonyConfigData;
import com.android.internal.telephony.TelephonyTest;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.Map;
import java.util.Set;

public class DataConfigTest extends TelephonyTest {

    // Mock Carrier IDs
    private static final int CARRIER_ID_TMOBILE = 1;
    private static final int CARRIER_ID_UNKNOWN = 9999;

    @Before
    public void setUp() throws Exception {
        super.setUp(getClass().getSimpleName());
    }

    @After
    public void tearDown() throws Exception {
        super.tearDown();
    }

    @Test
    public void testConnectionCapabilityRules_CarrierSpecific() {
        // Rule: NET_CAP_MMS (0) -> CONN_CAP_MMS (1) : ApnRequired=true
        String carrierRule = "0:1:true";

        TelephonyConfigData.DataConfigProto proto = TelephonyConfigData.DataConfigProto.newBuilder()
                .setVersion(1)
                .setConnectionCapabilityConfigs(
                        TelephonyConfigData.ConnectionCapabilityConfig.newBuilder()
                                .addCarrierConnectionCapabilityConfigs(
                                        TelephonyConfigData.ConnectionCapabilityMap.newBuilder()
                                                .setCarrierId(CARRIER_ID_TMOBILE)
                                                .addRules(carrierRule)
                                                .build())
                                .build())
                .build();

        DataConfig dataConfig = new DataConfig(proto);

        // Verify Capabilities Map
        Map<Integer, Integer> caps = dataConfig.getConnectionCapabilities(CARRIER_ID_TMOBILE);
        assertThat(caps).hasSize(1);
        assertThat(caps.get(0)).isEqualTo(1);

        // Verify APN Requirements Map
        Map<Integer, Boolean> apnReqs = dataConfig.getApnRequired(CARRIER_ID_TMOBILE);
        assertThat(apnReqs).hasSize(1);
        assertThat(apnReqs.get(0)).isTrue();
    }

    @Test
    public void testConnectionCapabilityRules_DefaultFallback() {
        // Default Rule: NET_CAP_INTERNET (12) -> CONN_CAP_INTERNET (2) : ApnRequired=false
        String defaultRule = "12:2:false";

        TelephonyConfigData.DataConfigProto proto = TelephonyConfigData.DataConfigProto.newBuilder()
                .setVersion(1)
                .setConnectionCapabilityConfigs(
                        TelephonyConfigData.ConnectionCapabilityConfig.newBuilder()
                                .setDefaultConnectionCapabilityConfig(
                                        TelephonyConfigData.ConnectionCapabilityMap.newBuilder()
                                                .addRules(defaultRule)
                                                .build())
                                .build())
                .build();

        DataConfig dataConfig = new DataConfig(proto);

        // Query for an unknown carrier, should return default rules
        Map<Integer, Integer> caps = dataConfig.getConnectionCapabilities(CARRIER_ID_UNKNOWN);
        assertThat(caps).hasSize(1);
        assertThat(caps.get(12)).isEqualTo(2);

        Map<Integer, Boolean> apnReqs = dataConfig.getApnRequired(CARRIER_ID_UNKNOWN);
        assertThat(apnReqs.get(12)).isFalse();
    }

    @Test
    public void testConnectionCapabilityRules_NoConfig() {
        // connection_capability_configs has no data
        // (Proto does not have the field set at all)
        TelephonyConfigData.DataConfigProto proto = TelephonyConfigData.DataConfigProto.newBuilder()
                .setVersion(1)
                .build();

        DataConfig dataConfig = new DataConfig(proto);

        // Should return empty map (triggering fallback)
        Map<Integer, Integer> caps = dataConfig.getConnectionCapabilities(CARRIER_ID_UNKNOWN);
        assertThat(caps).isNull();
    }

    @Test
    public void testConnectionCapabilityRules_NoDefaultConfig() {
        // connection_capability_configs has data, but default has "no data"
        // (Proto has carrier specific rules, but NO DefaultConnectionCapabilityConfig)
        String carrierRule = "0:1:true";
        TelephonyConfigData.DataConfigProto proto = TelephonyConfigData.DataConfigProto.newBuilder()
                .setVersion(1)
                .setConnectionCapabilityConfigs(
                        TelephonyConfigData.ConnectionCapabilityConfig.newBuilder()
                                .addCarrierConnectionCapabilityConfigs(
                                        TelephonyConfigData.ConnectionCapabilityMap.newBuilder()
                                                .setCarrierId(CARRIER_ID_TMOBILE)
                                                .addRules(carrierRule)
                                                .build())
                                // No setDefaultConnectionCapabilityConfig called
                                .build())
                .build();

        DataConfig dataConfig = new DataConfig(proto);

        // Verify Carrier Specific still works
        assertThat(dataConfig.getConnectionCapabilities(CARRIER_ID_TMOBILE)).hasSize(1);

        Map<Integer, Integer> caps = dataConfig.getConnectionCapabilities(CARRIER_ID_UNKNOWN);
        assertThat(caps).isNull();
    }

    @Test
    public void testConnectionCapabilityRules_EmptyDefaultConfig() {
        // connection_capability_configs has data (Carrier 1), but default is "empty".
        // This validates that we respect the empty default list instead of falling back to
        // something else.
        String carrierRule = "0:1:true";

        TelephonyConfigData.DataConfigProto proto = TelephonyConfigData.DataConfigProto.newBuilder()
                .setVersion(1)
                .setConnectionCapabilityConfigs(
                        TelephonyConfigData.ConnectionCapabilityConfig.newBuilder()
                                // 1. Add Carrier Specific Data (So config "has data")
                                .addCarrierConnectionCapabilityConfigs(
                                        TelephonyConfigData.ConnectionCapabilityMap.newBuilder()
                                                .setCarrierId(CARRIER_ID_TMOBILE)
                                                .addRules(carrierRule)
                                                .build())
                                // 2. Add Explicit Empty Default
                                .setDefaultConnectionCapabilityConfig(
                                        TelephonyConfigData.ConnectionCapabilityMap.newBuilder()
                                                // No .addRules() called -> Empty List
                                                .build())
                                .build())
                .build();

        DataConfig dataConfig = new DataConfig(proto);

        // Verify Carrier Specific still works (Configuration is valid)
        assertThat(dataConfig.getConnectionCapabilities(CARRIER_ID_TMOBILE)).hasSize(1);

        // Verify Unknown Carrier returns EMPTY map (Matches the explicit empty default)
        Map<Integer, Integer> caps = dataConfig.getConnectionCapabilities(CARRIER_ID_UNKNOWN);
        assertThat(caps).isNotNull();
        assertThat(caps).isEmpty();
    }

    @Test
    public void testConnectionCapabilityRules_InvalidFormat() {
        // Invalid rules should be ignored
        String invalidRule1 = "12:2"; // Missing APN req
        String invalidRule2 = "invalid";
        String validRule = "12:2:true";

        TelephonyConfigData.DataConfigProto proto = TelephonyConfigData.DataConfigProto.newBuilder()
                .setConnectionCapabilityConfigs(
                        TelephonyConfigData.ConnectionCapabilityConfig.newBuilder()
                                .setDefaultConnectionCapabilityConfig(
                                        TelephonyConfigData.ConnectionCapabilityMap.newBuilder()
                                                .addRules(invalidRule1)
                                                .addRules(invalidRule2)
                                                .addRules(validRule)
                                                .build())
                                .build())
                .build();

        DataConfig dataConfig = new DataConfig(proto);
        Map<Integer, Integer> caps = dataConfig.getConnectionCapabilities(CARRIER_ID_UNKNOWN);

        // Should only contain the one valid rule
        assertThat(caps).hasSize(1);
        assertThat(caps.get(12)).isEqualTo(2);
    }

    @Test
    public void testMeteredCapabilities() {
        // Setup:
        // Home Default: [MMS]
        // Home Carrier: [MMS, INTERNET]
        // Roaming Default: [INTERNET]
        // Roaming Carrier: [SUPL]

        TelephonyConfigData.DataConfigProto proto = TelephonyConfigData.DataConfigProto.newBuilder()
                .setHomeMeteredCapabilityConfigs(
                        TelephonyConfigData.MeteredCapabilityConfig.newBuilder()
                                .setDefaultMeteredCapabilityConfig(
                                        TelephonyConfigData.MeteredCapabilities.newBuilder()
                                                .addCapabilityIds(
                                                        NetworkCapabilities.NET_CAPABILITY_MMS)
                                                .build())
                                .addCarrierMeteredCapabilityConfigs(
                                        TelephonyConfigData.MeteredCapabilities.newBuilder()
                                                .setCarrierId(CARRIER_ID_TMOBILE)
                                                .addCapabilityIds(
                                                        NetworkCapabilities.NET_CAPABILITY_MMS)
                                                .addCapabilityIds(
                                                        NetworkCapabilities.NET_CAPABILITY_INTERNET)
                                                .build())
                                .build())
                .setRoamMeteredCapabilityConfigs(
                        TelephonyConfigData.MeteredCapabilityConfig.newBuilder()
                                .setDefaultMeteredCapabilityConfig(
                                        TelephonyConfigData.MeteredCapabilities.newBuilder()
                                                .addCapabilityIds(
                                                        NetworkCapabilities.NET_CAPABILITY_INTERNET)
                                                .build())
                                .addCarrierMeteredCapabilityConfigs(
                                        TelephonyConfigData.MeteredCapabilities.newBuilder()
                                                .setCarrierId(CARRIER_ID_TMOBILE)
                                                .addCapabilityIds(
                                                        NetworkCapabilities.NET_CAPABILITY_SUPL)
                                                .build())
                                .build())
                .build();

        DataConfig dataConfig = new DataConfig(proto);

        // 1. Home / Unknown Carrier -> Default Home ([MMS])
        Set<Integer> homeDefault = dataConfig.getMeteredNetworkCapabilities(
                CARRIER_ID_UNKNOWN, false /* isRoaming */);
        assertThat(homeDefault).containsExactly(NetworkCapabilities.NET_CAPABILITY_MMS);

        // 2. Home / T-Mobile -> Carrier Home ([MMS, INTERNET])
        Set<Integer> homeTmo = dataConfig.getMeteredNetworkCapabilities(
                CARRIER_ID_TMOBILE, false /* isRoaming */);
        assertThat(homeTmo).containsExactly(
                NetworkCapabilities.NET_CAPABILITY_MMS,
                NetworkCapabilities.NET_CAPABILITY_INTERNET);

        // 3. Roaming / Unknown Carrier -> Default Roam ([INTERNET])
        Set<Integer> roamDefault = dataConfig.getMeteredNetworkCapabilities(
                CARRIER_ID_UNKNOWN, true /* isRoaming */);
        assertThat(roamDefault).containsExactly(NetworkCapabilities.NET_CAPABILITY_INTERNET);

        // 4. Roaming / T-Mobile -> Carrier Roam ([SUPL])
        Set<Integer> roamTmo = dataConfig.getMeteredNetworkCapabilities(
                CARRIER_ID_TMOBILE, true /* isRoaming */);
        assertThat(roamTmo).containsExactly(NetworkCapabilities.NET_CAPABILITY_SUPL);
    }

    @Test
    public void testMeteredCapabilities_ReturnsNullWhenMissing() {
        // Scenario: Proto has NO metered config sections at all.
        // Expectation: getMeteredNetworkCapabilities returns NULL (signaling DataConfigManager to
        // use hardcoded defaults).
        TelephonyConfigData.DataConfigProto proto = TelephonyConfigData.DataConfigProto.newBuilder()
                .setVersion(1)
                // .setHomeMeteredCapabilityConfigs is NOT called
                .build();

        DataConfig dataConfig = new DataConfig(proto);

        // Verify Home returns null (Missing)
        assertThat(
                dataConfig.getMeteredNetworkCapabilities(CARRIER_ID_TMOBILE, false /* isRoaming */))
                .isNull();

        // Verify Roaming returns null (Missing)
        assertThat(
                dataConfig.getMeteredNetworkCapabilities(CARRIER_ID_TMOBILE, true /* isRoaming */))
                .isNull();
    }

    @Test
    public void testMeteredCapabilities_ReturnsEmptyWhenExplicitlyEmpty_Carrier() {
        // Scenario: Carrier explicitly sets an empty list for Home metering.
        // Expectation: Returns an EMPTY SET (Not null). This tells DataConfigManager "The
        // carrier wants NOTHING metered".
        TelephonyConfigData.DataConfigProto proto = TelephonyConfigData.DataConfigProto.newBuilder()
                .setHomeMeteredCapabilityConfigs(
                        TelephonyConfigData.MeteredCapabilityConfig.newBuilder()
                                .addCarrierMeteredCapabilityConfigs(
                                        TelephonyConfigData.MeteredCapabilities.newBuilder()
                                                .setCarrierId(CARRIER_ID_TMOBILE)
                                                // No addCapabilityIds() -> Explicit Empty List
                                                .build())
                                .build())
                .build();

        DataConfig dataConfig = new DataConfig(proto);

        Set<Integer> result = dataConfig.getMeteredNetworkCapabilities(CARRIER_ID_TMOBILE, false);

        // Must NOT be null (which would trigger default fallback)
        assertThat(result).isNotNull();
        // Must be empty (No metering)
        assertThat(result).isEmpty();
    }

    @Test
    public void testMeteredCapabilities_ReturnsEmptyWhenExplicitlyEmpty_Default() {
        // Scenario: Carrier specific config is missing, but Default config is explicitly empty.
        // Expectation: Returns an EMPTY SET.
        TelephonyConfigData.DataConfigProto proto = TelephonyConfigData.DataConfigProto.newBuilder()
                .setHomeMeteredCapabilityConfigs(
                        TelephonyConfigData.MeteredCapabilityConfig.newBuilder()
                                .setDefaultMeteredCapabilityConfig(
                                        TelephonyConfigData.MeteredCapabilities.newBuilder()
                                                // No addCapabilityIds() -> Explicit Empty List
                                                .build())
                                .build())
                .build();

        DataConfig dataConfig = new DataConfig(proto);

        // Check for an unknown carrier (falls back to default)
        Set<Integer> result = dataConfig.getMeteredNetworkCapabilities(CARRIER_ID_UNKNOWN, false);

        // Must NOT be null
        assertThat(result).isNotNull();
        // Must be empty
        assertThat(result).isEmpty();
    }

    @Test
    public void testVersion() {
        TelephonyConfigData.DataConfigProto proto = TelephonyConfigData.DataConfigProto.newBuilder()
                .setVersion(123)
                .build();
        DataConfig dataConfig = new DataConfig(proto);
        assertThat(dataConfig.getVersion()).isEqualTo(123);
    }

    @Test
    public void testGetAllNetworkCapabilities() {
        // Setup:
        // Default Rule: 10->20, 11->21
        // Carrier 1 Rule: 12->22
        // Carrier 2 Rule: 13->23
        int carrierId1 = 1;
        int carrierId2 = 2;

        TelephonyConfigData.DataConfigProto proto = TelephonyConfigData.DataConfigProto.newBuilder()
                .setConnectionCapabilityConfigs(
                        TelephonyConfigData.ConnectionCapabilityConfig.newBuilder()
                                .setDefaultConnectionCapabilityConfig(
                                        TelephonyConfigData.ConnectionCapabilityMap.newBuilder()
                                                .addRules("10:20:false")
                                                .addRules("11:21:false")
                                                .build())
                                .addCarrierConnectionCapabilityConfigs(
                                        TelephonyConfigData.ConnectionCapabilityMap.newBuilder()
                                                .setCarrierId(carrierId1)
                                                .addRules("12:22:false")
                                                .build())
                                .addCarrierConnectionCapabilityConfigs(
                                        TelephonyConfigData.ConnectionCapabilityMap.newBuilder()
                                                .setCarrierId(carrierId2)
                                                .addRules("13:23:false")
                                                .build())
                                .build())
                .build();

        DataConfig dataConfig = new DataConfig(proto);

        Set<Integer> allCaps = dataConfig.getAllNetworkCapabilities();

        assertThat(allCaps).containsExactly(10, 11, 12, 13);

        // Verify idempotency / consistency
        assertThat(dataConfig.getAllNetworkCapabilities()).isEqualTo(allCaps);
    }

    @Test
    public void testEqualsAndHashCode_IdenticalConfigs() {
        // Even if the versions are different, if the functional data rules are the same,
        // they should be considered equal to avoid unnecessary network teardowns.
        TelephonyConfigData.DataConfigProto proto1 =
                TelephonyConfigData.DataConfigProto.newBuilder()
                        .setVersion(1)
                        .setConnectionCapabilityConfigs(
                                TelephonyConfigData.ConnectionCapabilityConfig.newBuilder()
                                        .addCarrierConnectionCapabilityConfigs(
                                                TelephonyConfigData.ConnectionCapabilityMap
                                                        .newBuilder()
                                                        .setCarrierId(CARRIER_ID_TMOBILE)
                                                        .addRules("0:1:true")
                                                        .build())
                                        .build())
                        .build();

        TelephonyConfigData.DataConfigProto proto2 =
                TelephonyConfigData.DataConfigProto.newBuilder()
                        .setVersion(2) // Version bumped (e.g., due to a satellite config update)
                        .setConnectionCapabilityConfigs(
                                TelephonyConfigData.ConnectionCapabilityConfig.newBuilder()
                                        .addCarrierConnectionCapabilityConfigs(
                                                TelephonyConfigData.ConnectionCapabilityMap
                                                        .newBuilder()
                                                        .setCarrierId(CARRIER_ID_TMOBILE)
                                                        .addRules(
                                                                "0:1:true") // Rules remain
                                                        // identical
                                                        .build())
                                        .build())
                        .build();

        DataConfig config1 = new DataConfig(proto1);
        DataConfig config2 = new DataConfig(proto2);

        assertThat(config1).isEqualTo(config2);
        assertThat(config1.hashCode()).isEqualTo(config2.hashCode());
    }

    @Test
    public void testEquals_DifferentConnectionCapabilities() {
        TelephonyConfigData.DataConfigProto proto1 =
                TelephonyConfigData.DataConfigProto.newBuilder()
                        .setConnectionCapabilityConfigs(
                                TelephonyConfigData.ConnectionCapabilityConfig.newBuilder()
                                        .addCarrierConnectionCapabilityConfigs(
                                                TelephonyConfigData.ConnectionCapabilityMap
                                                        .newBuilder()
                                                        .setCarrierId(CARRIER_ID_TMOBILE)
                                                        .addRules("0:1:true")
                                                        .build())
                                        .build())
                        .build();

        // Mapped to a different connection capability (2 instead of 1)
        TelephonyConfigData.DataConfigProto proto2 =
                TelephonyConfigData.DataConfigProto.newBuilder()
                        .setConnectionCapabilityConfigs(
                                TelephonyConfigData.ConnectionCapabilityConfig.newBuilder()
                                        .addCarrierConnectionCapabilityConfigs(
                                                TelephonyConfigData.ConnectionCapabilityMap
                                                        .newBuilder()
                                                        .setCarrierId(CARRIER_ID_TMOBILE)
                                                        .addRules("0:2:true")
                                                        .build())
                                        .build())
                        .build();

        DataConfig config1 = new DataConfig(proto1);
        DataConfig config2 = new DataConfig(proto2);

        assertThat(config1).isNotEqualTo(config2);
    }

    @Test
    public void testEquals_DifferentMeteredCapabilities() {
        TelephonyConfigData.DataConfigProto proto1 =
                TelephonyConfigData.DataConfigProto.newBuilder()
                        .setHomeMeteredCapabilityConfigs(
                                TelephonyConfigData.MeteredCapabilityConfig.newBuilder()
                                        .addCarrierMeteredCapabilityConfigs(
                                                TelephonyConfigData.MeteredCapabilities
                                                        .newBuilder()
                                                        .setCarrierId(CARRIER_ID_TMOBILE)
                                                        .addCapabilityIds(
                                                                NetworkCapabilities
                                                                        .NET_CAPABILITY_MMS)
                                                        .build())
                                        .build())
                        .build();

        TelephonyConfigData.DataConfigProto proto2 =
                TelephonyConfigData.DataConfigProto.newBuilder()
                        .setHomeMeteredCapabilityConfigs(
                                TelephonyConfigData.MeteredCapabilityConfig.newBuilder()
                                        .addCarrierMeteredCapabilityConfigs(
                                                TelephonyConfigData.MeteredCapabilities.newBuilder()
                                                        .setCarrierId(CARRIER_ID_TMOBILE)
                                                        .addCapabilityIds(
                                                                NetworkCapabilities
                                                                        .NET_CAPABILITY_INTERNET)
                                                        .build())
                                        .build())
                        .build();

        DataConfig config1 = new DataConfig(proto1);
        DataConfig config2 = new DataConfig(proto2);

        assertThat(config1).isNotEqualTo(config2);
    }

    @Test
    public void testEquals_NullAndDifferentClass() {
        TelephonyConfigData.DataConfigProto proto =
                TelephonyConfigData.DataConfigProto.newBuilder().build();
        DataConfig config = new DataConfig(proto);

        assertThat(config.equals(null)).isFalse();
        assertThat(config.equals(new Object())).isFalse();
    }

}
