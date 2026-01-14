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

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;

import android.telephony.ServiceState;
import android.testing.AndroidTestingRunner;
import android.testing.TestableLooper;

import com.android.internal.telephony.Phone;
import com.android.internal.telephony.TelephonyTest;
import com.android.internal.telephony.flags.FeatureFlags;
import com.android.internal.telephony.metrics.SatelliteStats;
import com.android.internal.telephony.satellite.metrics.CarrierRoamingSatelliteSessionStats;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Collections;

@RunWith(AndroidTestingRunner.class)
@TestableLooper.RunWithLooper
public class CarrierRoamingSatelliteSessionStatsTest extends TelephonyTest {
    private static final String TAG = "CarrierRoamingSatelliteSessionStatsTest";
    private static final int SUB_ID = 0;
    private static final int CARRIER_ID = 1234;

    @Mock private SatelliteStats mMockSatelliteStats;
    @Mock private Phone mMockPhone;
    @Mock private ServiceState mMockServiceState;
    @Mock private FeatureFlags mMockFeatureFlags;

    private TestCarrierRoamingSatelliteSessionStats mCarrierRoamingSatelliteSessionStats;

    @Before
    public void setUp() throws Exception {
        super.setUp(getClass().getSimpleName());
        MockitoAnnotations.initMocks(this);
        logd(TAG + " Setup!");
        replaceInstance(SatelliteStats.class, "sInstance", null, mMockSatelliteStats);

        doReturn(mContext).when(mMockPhone).getContext();
        doReturn(SUB_ID).when(mMockPhone).getSubId();
        doReturn(CARRIER_ID).when(mMockPhone).getCarrierId();
        doReturn(mMockServiceState).when(mMockPhone).getServiceState();

        mCarrierRoamingSatelliteSessionStats = new TestCarrierRoamingSatelliteSessionStats(SUB_ID);
    }

    @After
    public void tearDown() throws Exception {
        CarrierRoamingSatelliteSessionStats.clearInstancesForTest();
        super.tearDown();
    }

    @Test
    public void testAccumulatedScreenOnTime() {
        doReturn(true).when(mMockFeatureFlags).satelliteMetricsEnhancement();
        doReturn(new int[]{SUB_ID}).when(mSubscriptionManagerService).getActiveSubIdList(
                anyBoolean());
        mCarrierRoamingSatelliteSessionStats.increaseElapsedTime(1_000L);
        mCarrierRoamingSatelliteSessionStats.onSessionStart(
                CARRIER_ID,
                mMockPhone,
                new int[] {},
                0,
                Collections.emptyList(),
                0,
                0,
                "12345",
                mMockFeatureFlags,
                true /* isScreenOn */);

        // Advance time by 5 seconds
        mCarrierRoamingSatelliteSessionStats.increaseElapsedTime(5_000L);
        // Screen turns off
        mCarrierRoamingSatelliteSessionStats.onScreenStateChanged(false);

        // Advance time by 10 seconds (screen off)
        mCarrierRoamingSatelliteSessionStats.increaseElapsedTime(10_000L);

        // Screen turns on
        mCarrierRoamingSatelliteSessionStats.onScreenStateChanged(true);
        // Advance time by 8 seconds
        mCarrierRoamingSatelliteSessionStats.increaseElapsedTime(8_000L);

        // End session
        mCarrierRoamingSatelliteSessionStats.onSessionEnd(SUB_ID, Collections.emptyList());

        ArgumentCaptor<SatelliteStats.CarrierRoamingSatelliteSessionParams> captor =
                ArgumentCaptor.forClass(SatelliteStats.CarrierRoamingSatelliteSessionParams.class);
        verify(mMockSatelliteStats).onCarrierRoamingSatelliteSessionMetrics(captor.capture());

        SatelliteStats.CarrierRoamingSatelliteSessionParams params = captor.getValue();
        // 5s + 8s = 13s
        assertEquals(13, params.getScreenOnTimeSec());
    }

    private static class TestCarrierRoamingSatelliteSessionStats
            extends CarrierRoamingSatelliteSessionStats {
        private long mElapsedTime = 0;

        TestCarrierRoamingSatelliteSessionStats(int subId) {
            super(subId);
        }

        @Override
        protected long getElapsedRealtime() {
            return mElapsedTime;
        }

        public void increaseElapsedTime(long time) {
            mElapsedTime += time;
        }

        @Override
        protected long getDataUsage() {
            return 0;
        }
    }
}
