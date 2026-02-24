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

import static android.telephony.CarrierConfigManager.CARRIER_ROAMING_NTN_CONNECT_AUTOMATIC;
import static android.telephony.CarrierConfigManager.CARRIER_ROAMING_NTN_CONNECT_MANUAL;

import android.annotation.FlaggedApi;
import android.annotation.NonNull;
import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.telephony.flags.Flags;
import android.telephony.CarrierConfigManager;
import android.telephony.satellite.EnableRequestAttributes;
import android.telephony.satellite.SatelliteManager;

import java.util.concurrent.Executor;
import java.util.function.Consumer;

/**
 * Manages satellite enablement logic and resolves conflicts between different enablement requests.
 * This class acts as a central orchestrator, delegating to the appropriate strategy
 * (e.g., Manual or Automatic) based on the request.
 */
@FlaggedApi(Flags.FLAG_SATELLITE_UPSELL_26Q4)
public class SatelliteEnablementController {
    private final ManualEnablementController mManualSatelliteController;
    private final AutoEnablementController mAutoSatelliteController;

    /**
     * Creates a new instance of SatelliteEnablementController.
     */
    public SatelliteEnablementController() {
        this(new ManualEnablementController(), new AutoEnablementController());
    }

    @VisibleForTesting
    public SatelliteEnablementController(ManualEnablementController manualSatelliteController,
            AutoEnablementController autoSatelliteController) {
        mManualSatelliteController = manualSatelliteController;
        mAutoSatelliteController = autoSatelliteController;
    }

    /**
     * Handles requests to enable or disable satellite connectivity.
     *
     * @param attributes The attributes of the enable request.
     * @param executor The executor on which the callback will be called.
     * @param resultListener Listener for the result of the operation.
     */
    public void requestSatelliteEnabled(@NonNull EnableRequestAttributes attributes,
            @NonNull Executor executor, @NonNull Consumer<Integer> resultListener) {
        // TODO: Implement bitmask-based arbitration logic here.

        SatelliteEnablementStrategy enablementController =
                getEnablementController(attributes.getConnectType());
        if (enablementController != null) {
            if (attributes.isEnabled()) {
                enablementController.enableSatellite(attributes, executor, resultListener);
            } else {
                enablementController.disableSatellite(attributes, executor, resultListener);
            }
        } else {
            // Handle unsupported connect type
            resultListener.accept(SatelliteManager.SATELLITE_RESULT_INVALID_ARGUMENTS);
        }
    }

    private SatelliteEnablementStrategy getEnablementController(
            @CarrierConfigManager.CARRIER_ROAMING_NTN_CONNECT_TYPE int connectType) {
        switch (connectType) {
            case CARRIER_ROAMING_NTN_CONNECT_MANUAL:
                return mManualSatelliteController;
            case CARRIER_ROAMING_NTN_CONNECT_AUTOMATIC:
                return mAutoSatelliteController;
            default:
                return null;
        }
    }
}
