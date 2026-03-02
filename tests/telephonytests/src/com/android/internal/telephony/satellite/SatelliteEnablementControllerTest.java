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

package com.android.internal.telephony.satellite;

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import android.platform.test.annotations.RequiresFlagsEnabled;
import android.telephony.CarrierConfigManager;
import android.telephony.satellite.EnableRequestAttributes;
import android.telephony.satellite.SatelliteManager;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.concurrent.Executor;
import java.util.function.Consumer;

import com.android.internal.telephony.flags.Flags;

@RequiresFlagsEnabled(Flags.FLAG_SATELLITE_UPSELL_26Q4)
public class SatelliteEnablementControllerTest {

    @Mock private ManualEnablementController mManualEnablementController;
    @Mock private AutoEnablementController mAutoEnablementController;
    private SatelliteEnablementController mSatelliteEnablementController;

    @Mock private Consumer<Integer> mResultListener;
    private final Executor mExecutor = Runnable::run;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        mSatelliteEnablementController = new SatelliteEnablementController(
                mManualEnablementController, mAutoEnablementController);
    }

    @Test
    public void testRequestSatelliteEnabled_enableManual() {
        EnableRequestAttributes attributes = new EnableRequestAttributes.Builder(true)
                .setConnectType(CarrierConfigManager.CARRIER_ROAMING_NTN_CONNECT_MANUAL)
                .build();
        mSatelliteEnablementController.requestSatelliteEnabled(
                attributes, mExecutor, mResultListener);
        verify(mManualEnablementController).enableSatellite(attributes, mExecutor, mResultListener);
        verify(mAutoEnablementController, never()).enableSatellite(any(), any(), any());
    }

    @Test
    public void testRequestSatelliteEnabled_disableManual() {
        EnableRequestAttributes attributes = new EnableRequestAttributes.Builder(false)
                .setConnectType(CarrierConfigManager.CARRIER_ROAMING_NTN_CONNECT_MANUAL)
                .build();
        mSatelliteEnablementController.requestSatelliteEnabled(
                attributes, mExecutor, mResultListener);
        verify(mManualEnablementController).disableSatellite(
                attributes, mExecutor, mResultListener);
        verify(mAutoEnablementController, never()).disableSatellite(any(), any(), any());
    }

    @Test
    public void testRequestSatelliteEnabled_enableAuto() {
        EnableRequestAttributes attributes = new EnableRequestAttributes.Builder(true)
                .setConnectType(CarrierConfigManager.CARRIER_ROAMING_NTN_CONNECT_AUTOMATIC)
                .build();
        mSatelliteEnablementController.requestSatelliteEnabled(
                attributes, mExecutor, mResultListener);
        verify(mAutoEnablementController).enableSatellite(attributes, mExecutor, mResultListener);
        verify(mManualEnablementController, never()).enableSatellite(any(), any(), any());
    }

    @Test
    public void testRequestSatelliteEnabled_disableAuto() {
        EnableRequestAttributes attributes = new EnableRequestAttributes.Builder(false)
                .setConnectType(CarrierConfigManager.CARRIER_ROAMING_NTN_CONNECT_AUTOMATIC)
                .build();
        mSatelliteEnablementController.requestSatelliteEnabled(
                attributes, mExecutor, mResultListener);
        verify(mAutoEnablementController).disableSatellite(attributes, mExecutor, mResultListener);
        verify(mManualEnablementController, never()).disableSatellite(any(), any(), any());
    }

    @Test
    public void testRequestSatelliteEnabled_invalidConnectType() {
        EnableRequestAttributes attributes = new EnableRequestAttributes.Builder(true)
                .setConnectType(100) // Invalid connect type
                .build();
        mSatelliteEnablementController.requestSatelliteEnabled(
                attributes, mExecutor, mResultListener);
        verify(mResultListener).accept(SatelliteManager.SATELLITE_RESULT_INVALID_ARGUMENTS);
        verify(mManualEnablementController, never()).enableSatellite(any(), any(), any());
        verify(mAutoEnablementController, never()).enableSatellite(any(), any(), any());
    }
}
