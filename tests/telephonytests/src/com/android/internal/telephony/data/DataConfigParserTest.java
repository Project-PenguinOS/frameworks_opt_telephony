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

import static junit.framework.Assert.assertNotNull;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import android.testing.AndroidTestingRunner;

import com.android.internal.telephony.TelephonyConfigData;
import com.android.internal.telephony.TelephonyTest;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidTestingRunner.class)
public class DataConfigParserTest extends TelephonyTest {

    private byte[] mBytesProtoBuffer;

    @Before
    public void setUp() throws Exception {
        super.setUp(getClass().getSimpleName());
        logd(TAG + " Setup!");

        TelephonyConfigData.TelephonyConfigProto.Builder telephonyConfigBuilder =
                TelephonyConfigData.TelephonyConfigProto.newBuilder();
        TelephonyConfigData.DataConfigProto.Builder dataConfigBuilder =
                TelephonyConfigData.DataConfigProto.newBuilder();

        dataConfigBuilder.setVersion(1);

        TelephonyConfigData.ConnectionCapabilityConfig.Builder connCapConfigBuilder =
                TelephonyConfigData.ConnectionCapabilityConfig.newBuilder();

        TelephonyConfigData.ConnectionCapabilityMap.Builder defaultConnCapMapBuilder =
                TelephonyConfigData.ConnectionCapabilityMap.newBuilder();
        defaultConnCapMapBuilder.addRules("12:8:true");
        connCapConfigBuilder.setDefaultConnectionCapabilityConfig(defaultConnCapMapBuilder);

        TelephonyConfigData.ConnectionCapabilityMap.Builder carrierConnCapMapBuilder =
                TelephonyConfigData.ConnectionCapabilityMap.newBuilder();
        carrierConnCapMapBuilder.setCarrierId(1234);
        carrierConnCapMapBuilder.addRules("10:9:false");
        connCapConfigBuilder.addCarrierConnectionCapabilityConfigs(carrierConnCapMapBuilder);

        dataConfigBuilder.setConnectionCapabilityConfigs(connCapConfigBuilder);
        telephonyConfigBuilder.setData(dataConfigBuilder);

        TelephonyConfigData.TelephonyConfigProto telephonyConfigData =
                telephonyConfigBuilder.build();
        mBytesProtoBuffer = telephonyConfigData.toByteArray();
    }

    @After
    public void tearDown() throws Exception {
        logd(TAG + " tearDown");
        super.tearDown();
    }

    @Test
    public void testParseData() {
        DataConfigParser parser = new DataConfigParser(mBytesProtoBuffer);
        assertNotNull(parser.getConfig());
        assertEquals(1, parser.getVersion());

        DataConfig config = parser.getConfig();
        assertNotNull(config.getConfigData());
        assertEquals(1, config.getConfigData().getVersion());
        assertEquals(1, config.getConfigData().getConnectionCapabilityConfigs()
                .getCarrierConnectionCapabilityConfigsCount());
        assertEquals(1234, config.getConfigData().getConnectionCapabilityConfigs()
                .getCarrierConnectionCapabilityConfigs(0).getCarrierId());
    }

    @Test
    public void testParseNullData() {
        DataConfigParser parser = new DataConfigParser((byte[]) null);
        assertNull(parser.getConfig());
        assertEquals(-1, parser.getVersion()); // Default version
    }

    @Test
    public void testParseEmptyData() {
        DataConfigParser parser = new DataConfigParser(new byte[0]);
        assertNull(parser.getConfig());
    }
}
