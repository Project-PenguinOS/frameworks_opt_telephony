/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.internal.telephony.configupdate;

import static android.telephony.NetworkRegistrationInfo.FIRST_SERVICE_TYPE;
import static android.telephony.NetworkRegistrationInfo.LAST_SERVICE_TYPE;

import static com.android.internal.telephony.configupdate.TelephonyConfigUpdateInstallReceiver.NEW_CONFIG_CONTENT_PATH;
import static com.android.internal.telephony.configupdate.TelephonyConfigUpdateInstallReceiver.UPDATE_DIR;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.Intent;
import android.util.ArraySet;

import androidx.annotation.Nullable;

import com.android.internal.telephony.TelephonyConfigData;
import com.android.internal.telephony.TelephonyTest;
import com.android.internal.telephony.data.DataConfig;
import com.android.internal.telephony.data.DataConfigParser;
import com.android.internal.telephony.satellite.SatelliteConfig;
import com.android.internal.telephony.satellite.SatelliteConfigParser;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class TelephonyConfigUpdateInstallReceiverTest extends TelephonyTest {

    public static final String DOMAIN_SATELLITE = "satellite";
    public static final String DOMAIN_DATA = "data";
    private static final int[] ACTIVE_SUB_LIST = {1};
    @Mock
    private Executor mExecutor;
    @Mock
    private ConfigProviderAdaptor.Callback mCallback;

    @Before
    public void setUp() throws Exception {
        super.setUp(getClass().getSimpleName());
        MockitoAnnotations.initMocks(this);
        when(mSubscriptionManagerService.getActiveSubIdList(anyBoolean())).thenReturn(
                ACTIVE_SUB_LIST);
        logd(TAG + " Setup!");
    }

    @After
    public void tearDown() throws Exception {
        logd(TAG + " tearDown");
        super.tearDown();
    }

    @Test
    public void testTelephonyConfigUpdateInstallReceiver() {
        TelephonyConfigUpdateInstallReceiver testReceiver =
                new TelephonyConfigUpdateInstallReceiver();
        assertEquals(UPDATE_DIR, testReceiver.getUpdateDir().toString());
        assertEquals(new File(new File(UPDATE_DIR), NEW_CONFIG_CONTENT_PATH).toString(),
                testReceiver.getUpdateContent().toString());
    }

    @Test
    public void testGetInstance() {
        TelephonyConfigUpdateInstallReceiver testReceiver1 =
                TelephonyConfigUpdateInstallReceiver.getInstance();
        TelephonyConfigUpdateInstallReceiver testReceiver2 =
                TelephonyConfigUpdateInstallReceiver.getInstance();
        assertSame(testReceiver1, testReceiver2);
    }

    @Test
    public void testPostInstall() throws Exception {
        // create spyTelephonyConfigUpdateInstallReceiver
        TelephonyConfigUpdateInstallReceiver spyTelephonyConfigUpdateInstallReceiver =
                spy(new TelephonyConfigUpdateInstallReceiver());
        doReturn(true).when(spyTelephonyConfigUpdateInstallReceiver)
                .copySourceFileToTargetFile(any(), any());
        replaceInstance(TelephonyConfigUpdateInstallReceiver.class, "sReceiverAdaptorInstance",
                null, spyTelephonyConfigUpdateInstallReceiver);

        assertSame(spyTelephonyConfigUpdateInstallReceiver,
                TelephonyConfigUpdateInstallReceiver.getInstance());

        ConcurrentHashMap<Executor, ConfigProviderAdaptor.Callback> spyCallbackHashMap = spy(
                new ConcurrentHashMap<>());
        spyCallbackHashMap.put(mExecutor, mCallback);
        spyTelephonyConfigUpdateInstallReceiver.setCallbackMap(spyCallbackHashMap);

        // Mocks for Satellite
        SatelliteConfigParser spySatelliteParser = mock(SatelliteConfigParser.class);
        doReturn(new SatelliteConfig()).when(spySatelliteParser).getConfig();
        doReturn(DOMAIN_SATELLITE).when(spySatelliteParser).getDomain();
        doReturn(true).when(spyTelephonyConfigUpdateInstallReceiver)
                .isValidSatelliteCarrierConfigData(any());
        doReturn(true).when(spyTelephonyConfigUpdateInstallReceiver)
                .isValidMaxAllowedDataMode(any());
        doReturn(true).when(spyTelephonyConfigUpdateInstallReceiver)
                .isValidSatelliteProvider(any());
        doReturn(10).when(spySatelliteParser).getVersion();

        // Mocks for Data
        DataConfigParser spyDataParser = mock(DataConfigParser.class);
        DataConfig mockDataConfig = mock(DataConfig.class);
        TelephonyConfigData.DataConfigProto validProto =
                TelephonyConfigData.DataConfigProto.newBuilder().build();
        doReturn(validProto).when(mockDataConfig).getConfigData();
        doReturn(mockDataConfig).when(spyDataParser).getConfig();
        doReturn(DOMAIN_DATA).when(spyDataParser).getDomain();
        doReturn(true).when(spyTelephonyConfigUpdateInstallReceiver)
                .isValidDataConfig(any());
        doReturn(10).when(spyDataParser).getVersion();

        // 1. Success case for both
        doReturn(spySatelliteParser).when(spyTelephonyConfigUpdateInstallReceiver)
                .getNewConfigParser(eq(DOMAIN_SATELLITE), any());
        doReturn(spyDataParser).when(spyTelephonyConfigUpdateInstallReceiver)
                .getNewConfigParser(eq(DOMAIN_DATA), any());

        spyTelephonyConfigUpdateInstallReceiver.postInstall(mContext, new Intent());

        verify(spyCallbackHashMap, times(2)).entrySet();
        verify(spyTelephonyConfigUpdateInstallReceiver, times(1))
                .copySourceFileToTargetFile(any(), any());
        Mockito.clearInvocations(spyCallbackHashMap);
        Mockito.clearInvocations(spyTelephonyConfigUpdateInstallReceiver);

        // 2. Smaller version case
        // Setup existing parsers with version 10
        spyTelephonyConfigUpdateInstallReceiver.overrideConfigParser(spySatelliteParser);
        spyTelephonyConfigUpdateInstallReceiver.overrideConfigParser(spyDataParser);

        // New parsers with version 5
        SatelliteConfigParser newSatelliteParser = mock(SatelliteConfigParser.class);
        doReturn(new SatelliteConfig()).when(newSatelliteParser).getConfig();
        doReturn(DOMAIN_SATELLITE).when(newSatelliteParser).getDomain();
        doReturn(true).when(spyTelephonyConfigUpdateInstallReceiver) // Need validation to pass
                .isValidSatelliteCarrierConfigData(newSatelliteParser);
        doReturn(true).when(spyTelephonyConfigUpdateInstallReceiver)
                .isValidMaxAllowedDataMode(newSatelliteParser);
        doReturn(true).when(spyTelephonyConfigUpdateInstallReceiver)
                .isValidSatelliteProvider(newSatelliteParser);
        doReturn(5).when(newSatelliteParser).getVersion();

        DataConfigParser newDataParser = mock(DataConfigParser.class);
        DataConfig newDataConfig = mock(DataConfig.class);
        doReturn(newDataConfig).when(newDataParser).getConfig();
        doReturn(validProto).when(newDataConfig).getConfigData();
        doReturn(DOMAIN_DATA).when(newDataParser).getDomain();
        doReturn(true).when(spyTelephonyConfigUpdateInstallReceiver)
                .isValidDataConfig(newDataParser);
        doReturn(5).when(newDataParser).getVersion();

        doReturn(newSatelliteParser).when(spyTelephonyConfigUpdateInstallReceiver)
                .getNewConfigParser(eq(DOMAIN_SATELLITE), any());
        doReturn(newDataParser).when(spyTelephonyConfigUpdateInstallReceiver)
                .getNewConfigParser(eq(DOMAIN_DATA), any());

        spyTelephonyConfigUpdateInstallReceiver.postInstall(mContext, new Intent());

        verify(spyCallbackHashMap, times(0)).keySet();
        verify(spyTelephonyConfigUpdateInstallReceiver, times(0))
                .copySourceFileToTargetFile(any(), any());
        Mockito.clearInvocations(spyCallbackHashMap);
        Mockito.clearInvocations(spyTelephonyConfigUpdateInstallReceiver);

        // 3. Invalid config case
        // Reset override
        spyTelephonyConfigUpdateInstallReceiver.cleanUpTelephonyConfigs();

        // Let's rely on doReturn(false).
        doReturn(false).when(spyTelephonyConfigUpdateInstallReceiver)
                .isValidSatelliteCarrierConfigData(any());
        doReturn(false).when(spyTelephonyConfigUpdateInstallReceiver)
                .isValidDataConfig(any());

        spyTelephonyConfigUpdateInstallReceiver.postInstall(mContext, new Intent());

        verify(spyCallbackHashMap, times(0)).keySet();
        verify(spyTelephonyConfigUpdateInstallReceiver, times(0))
                .copySourceFileToTargetFile(any(), any());
    }


    @Test
    public void testGetConfig() throws Exception {
        TelephonyConfigUpdateInstallReceiver spyTelephonyConfigUpdateInstallReceiver =
                spy(new TelephonyConfigUpdateInstallReceiver());

        replaceInstance(TelephonyConfigUpdateInstallReceiver.class, "sReceiverAdaptorInstance",
                null, spyTelephonyConfigUpdateInstallReceiver);

        // 1. Test null case
        doReturn(null).when(
                spyTelephonyConfigUpdateInstallReceiver).getNewConfigParser(any(), any());

        assertNull(TelephonyConfigUpdateInstallReceiver.getInstance().getConfigParser(
                DOMAIN_SATELLITE));
        assertNull(TelephonyConfigUpdateInstallReceiver.getInstance().getConfigParser(
                DOMAIN_DATA));

        // 2. Test success case
        SatelliteConfigParser mockSatParser = mock(SatelliteConfigParser.class);
        doReturn(new SatelliteConfig()).when(mockSatParser).getConfig();
        doReturn(mockSatParser).when(spyTelephonyConfigUpdateInstallReceiver)
                .getNewConfigParser(eq(DOMAIN_SATELLITE), any());

        DataConfigParser mockDataParser = mock(DataConfigParser.class);
        DataConfig mockDataConfig = mock(DataConfig.class);
        doReturn(mockDataConfig).when(mockDataParser).getConfig();
        doReturn(mockDataParser).when(spyTelephonyConfigUpdateInstallReceiver)
                .getNewConfigParser(eq(DOMAIN_DATA), any());

        assertNotNull(TelephonyConfigUpdateInstallReceiver.getInstance().getConfigParser(
                DOMAIN_SATELLITE));
        assertNotNull(TelephonyConfigUpdateInstallReceiver.getInstance().getConfigParser(
                DOMAIN_DATA));
    }

    @Test
    public void testRegisterUnRegisterCallback() {
        TelephonyConfigUpdateInstallReceiver testReceiver =
                TelephonyConfigUpdateInstallReceiver.getInstance();

        ConfigProviderAdaptor.Callback testCallback = new ConfigProviderAdaptor.Callback() {
            @Override
            public void onChanged(@Nullable ConfigParser config) {
                super.onChanged(config);
            }
        };
        Executor executor = Executors.newSingleThreadExecutor();

        testReceiver.registerCallback(executor, testCallback);
        assertSame(testCallback, testReceiver.getCallbackMap().get(executor));

        testReceiver.unregisterCallback(testCallback);
        assertEquals(0, testReceiver.getCallbackMap().size());
    }

    @Test
    public void testIsValidSatelliteCarrierConfigData() {
        TelephonyConfigUpdateInstallReceiver spyTelephonyConfigUpdateInstallReceiver =
                spy(new TelephonyConfigUpdateInstallReceiver());
        SatelliteConfigParser mockParser = mock(SatelliteConfigParser.class);
        SatelliteConfig mockConfig = mock(SatelliteConfig.class);
        doReturn(new ArraySet<>()).when(mockConfig).getAllSatelliteCarrierIds();
        doReturn(mockConfig).when(mockParser).getConfig();

        assertTrue(spyTelephonyConfigUpdateInstallReceiver
                .isValidSatelliteCarrierConfigData(mockParser));

        doReturn(Set.of(1)).when(mockConfig).getAllSatelliteCarrierIds();
        Map<String, Set<Integer>> validPlmnsServices = new HashMap<>();
        validPlmnsServices.put("123456", Set.of(FIRST_SERVICE_TYPE, 3, LAST_SERVICE_TYPE));
        validPlmnsServices.put("12345", Set.of(FIRST_SERVICE_TYPE, 4, LAST_SERVICE_TYPE));
        doReturn(validPlmnsServices).when(mockConfig).getSupportedSatelliteServices(anyInt());
        doReturn(mockConfig).when(mockParser).getConfig();

        assertTrue(spyTelephonyConfigUpdateInstallReceiver
                .isValidSatelliteCarrierConfigData(mockParser));

        doReturn(Set.of(1)).when(mockConfig).getAllSatelliteCarrierIds();
        Map<String, Set<Integer>> invalidPlmnsServices1 = new HashMap<>();
        invalidPlmnsServices1.put("123456", Set.of(FIRST_SERVICE_TYPE - 1, 3, LAST_SERVICE_TYPE));
        doReturn(invalidPlmnsServices1).when(mockConfig).getSupportedSatelliteServices(anyInt());
        doReturn(mockConfig).when(mockParser).getConfig();
        assertFalse(spyTelephonyConfigUpdateInstallReceiver
                .isValidSatelliteCarrierConfigData(mockParser));

        doReturn(Set.of(1)).when(mockConfig).getAllSatelliteCarrierIds();
        Map<String, Set<Integer>> invalidPlmnsServices2 = new HashMap<>();
        invalidPlmnsServices2.put("123456", Set.of(FIRST_SERVICE_TYPE, 3, LAST_SERVICE_TYPE + 1));
        doReturn(invalidPlmnsServices2).when(mockConfig).getSupportedSatelliteServices(anyInt());
        doReturn(mockConfig).when(mockParser).getConfig();
        assertFalse(spyTelephonyConfigUpdateInstallReceiver
                .isValidSatelliteCarrierConfigData(mockParser));

        doReturn(Set.of(1)).when(mockConfig).getAllSatelliteCarrierIds();
        Map<String, Set<Integer>> invalidPlmnsServices3 = new HashMap<>();
        invalidPlmnsServices3.put("1234", Set.of(FIRST_SERVICE_TYPE, 3, LAST_SERVICE_TYPE));
        doReturn(invalidPlmnsServices3).when(mockConfig).getSupportedSatelliteServices(anyInt());
        doReturn(mockConfig).when(mockParser).getConfig();
        assertFalse(spyTelephonyConfigUpdateInstallReceiver
                .isValidSatelliteCarrierConfigData(mockParser));

        doReturn(Set.of(1)).when(mockConfig).getAllSatelliteCarrierIds();
        Map<String, Set<Integer>> invalidPlmnsServices4 = new HashMap<>();
        invalidPlmnsServices4.put("1234567", Set.of(FIRST_SERVICE_TYPE, 3, LAST_SERVICE_TYPE));
        doReturn(invalidPlmnsServices4).when(mockConfig).getSupportedSatelliteServices(anyInt());
        doReturn(mockConfig).when(mockParser).getConfig();
        assertFalse(spyTelephonyConfigUpdateInstallReceiver
                .isValidSatelliteCarrierConfigData(mockParser));
    }

    @Test
    public void testIsValidMaxAllowedDataMode() {
        TelephonyConfigUpdateInstallReceiver spyTelephonyConfigUpdateInstallReceiver =
                spy(new TelephonyConfigUpdateInstallReceiver());
        SatelliteConfigParser mockParser = mock(SatelliteConfigParser.class);
        SatelliteConfig mockConfig = mock(SatelliteConfig.class);
        doReturn(mockConfig).when(mockParser).getConfig();

        assertTrue(spyTelephonyConfigUpdateInstallReceiver.isValidMaxAllowedDataMode(mockParser));

        doReturn(null).when(mockConfig).getSatelliteMaxAllowedDataMode();
        assertTrue(spyTelephonyConfigUpdateInstallReceiver.isValidMaxAllowedDataMode(mockParser));

        doReturn(0).when(mockConfig).getSatelliteMaxAllowedDataMode();
        assertTrue(spyTelephonyConfigUpdateInstallReceiver.isValidMaxAllowedDataMode(mockParser));

        doReturn(1).when(mockConfig).getSatelliteMaxAllowedDataMode();
        assertTrue(spyTelephonyConfigUpdateInstallReceiver.isValidMaxAllowedDataMode(mockParser));

        doReturn(2).when(mockConfig).getSatelliteMaxAllowedDataMode();
        assertTrue(spyTelephonyConfigUpdateInstallReceiver.isValidMaxAllowedDataMode(mockParser));

        doReturn(-1).when(mockConfig).getSatelliteMaxAllowedDataMode();
        assertFalse(spyTelephonyConfigUpdateInstallReceiver.isValidMaxAllowedDataMode(mockParser));

        doReturn(3).when(mockConfig).getSatelliteMaxAllowedDataMode();
        assertFalse(spyTelephonyConfigUpdateInstallReceiver.isValidMaxAllowedDataMode(mockParser));
    }

    @Test
    public void testIsValidDeviceSatellitePlmns() {
        TelephonyConfigUpdateInstallReceiver spyTelephonyConfigUpdateInstallReceiver =
                spy(new TelephonyConfigUpdateInstallReceiver());
        SatelliteConfigParser mockParser = mock(SatelliteConfigParser.class);
        SatelliteConfig mockConfig = mock(SatelliteConfig.class);
        doReturn(mockConfig).when(mockParser).getConfig();

        assertTrue(spyTelephonyConfigUpdateInstallReceiver
                .isValidSatelliteProvider(mockParser));

        doReturn(null).when(mockConfig).getDeviceSatelliteProviderList();
        assertTrue(spyTelephonyConfigUpdateInstallReceiver
                .isValidSatelliteProvider(mockParser));

        doReturn(List.of("310211", "310212")).when(mockConfig).getDeviceSatelliteProviderList();
        assertTrue(spyTelephonyConfigUpdateInstallReceiver
                .isValidSatelliteProvider(mockParser));

        doReturn(List.of("310211", "123")).when(mockConfig).getDeviceSatelliteProviderList();
        assertFalse(spyTelephonyConfigUpdateInstallReceiver
                .isValidSatelliteProvider(mockParser));
    }

    @Test
    public void testIsValidDataConfig() {
        TelephonyConfigUpdateInstallReceiver spyReceiver =
                spy(new TelephonyConfigUpdateInstallReceiver());
        DataConfigParser mockParser = mock(DataConfigParser.class);
        DataConfig mockConfig = mock(DataConfig.class);

        doReturn(mockConfig).when(mockParser).getConfig();

        // Case 1: parser not instance of DataConfigParser
        assertFalse(spyReceiver.isValidDataConfig(mock(ConfigParser.class)));

        // Case 2: dataConfig null
        doReturn(null).when(mockParser).getConfig();
        assertFalse(spyReceiver.isValidDataConfig(mockParser));
        doReturn(mockConfig).when(mockParser).getConfig();

        // Case 3: no connection capability configs (valid)
        TelephonyConfigData.DataConfigProto proto =
                TelephonyConfigData.DataConfigProto.newBuilder().build();
        doReturn(proto).when(mockConfig).getConfigData();
        assertTrue(spyReceiver.isValidDataConfig(mockParser));

        // Case 4: valid connection capability configs
        TelephonyConfigData.ConnectionCapabilityMap defaultMap =
                TelephonyConfigData.ConnectionCapabilityMap.newBuilder()
                        .addRules("123:456:true")
                        .build();
        TelephonyConfigData.ConnectionCapabilityMap carrierMap =
                TelephonyConfigData.ConnectionCapabilityMap.newBuilder()
                        .setCarrierId(1)
                        .addRules("111:222:false")
                        .build();

        TelephonyConfigData.ConnectionCapabilityConfig caps =
                TelephonyConfigData.ConnectionCapabilityConfig.newBuilder()
                        .setDefaultConnectionCapabilityConfig(defaultMap)
                        .addCarrierConnectionCapabilityConfigs(carrierMap)
                        .build();

        proto = TelephonyConfigData.DataConfigProto.newBuilder()
                .setConnectionCapabilityConfigs(caps)
                .build();
        doReturn(proto).when(mockConfig).getConfigData();

        assertTrue(spyReceiver.isValidDataConfig(mockParser));

        // Case 5: Invalid rule format in default map
        defaultMap = TelephonyConfigData.ConnectionCapabilityMap.newBuilder()
                .addRules("invalid_rule")
                .build();
        caps = TelephonyConfigData.ConnectionCapabilityConfig.newBuilder()
                .setDefaultConnectionCapabilityConfig(defaultMap)
                .build();
        proto = TelephonyConfigData.DataConfigProto.newBuilder()
                .setConnectionCapabilityConfigs(caps)
                .build();
        doReturn(proto).when(mockConfig).getConfigData();

        assertFalse(spyReceiver.isValidDataConfig(mockParser));

        // Case 6: Invalid number format in carrier map
        carrierMap = TelephonyConfigData.ConnectionCapabilityMap.newBuilder()
                .setCarrierId(1)
                .addRules("abc:def:true")
                .build();
        caps = TelephonyConfigData.ConnectionCapabilityConfig.newBuilder()
                .addCarrierConnectionCapabilityConfigs(carrierMap)
                .build();
        proto = TelephonyConfigData.DataConfigProto.newBuilder()
                .setConnectionCapabilityConfigs(caps)
                .build();
        doReturn(proto).when(mockConfig).getConfigData();

        assertFalse(spyReceiver.isValidDataConfig(mockParser));
    }
}
