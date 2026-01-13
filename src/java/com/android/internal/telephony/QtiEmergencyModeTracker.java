/*
 * Copyright (c) Qualcomm Technologies, Inc. and/or its subsidiaries.
 * SPDX-License-Identifier: BSD-3-Clause-Clear
 */

package com.android.internal.telephony;

import android.content.Context;
import android.os.Handler;
import android.util.Log;

/**
 * Utility class to Track Emergency mode,
 * Handle exit SCBM/ECBM for both IMS and non-IMS path.
 */
public class QtiEmergencyModeTracker {

    private static final String LOG_TAG = "QtiEmergencyModeTracker";
    private boolean mPendingExitEcbmReq;
    private boolean mPendingExitScbmReq;

    /**
     * @return true if the phone is in Emergency Callback mode, otherwise false
     */
    public boolean isPhoneInEcbm() {
        return EcbmHandler.getInstance() != null && EcbmHandler.getInstance().isInEcm();
    }

    /**
     * @return true if the phone is in SMS callback mode and
     * exit SCBM supported, otherwise false
     */
    public boolean canExitScbm(Phone phone) {
        if (phone == null) {
            return false;
        }
        return phone.isInScbm() &&
                phone.isExitScbmFeatureSupported();
    }

    public boolean isPhoneInEmergencyMode(Phone phone) {
        return isPhoneInEcbm() || canExitScbm(phone);
    }

    public void exitEmergencyMode(Handler onComplete, Phone phone) throws Exception {
        if (phone == null) { return; }

        boolean isPhoneInEcbm = isPhoneInEcbm();
        boolean isPhoneInScbm = canExitScbm(phone);
        if (isPhoneInEcbm) {
            try {
                EcbmHandler.getInstance().exitEmergencyCallbackMode();
            } catch (Exception e) {
                throw e;
            }
            EcbmHandler.getInstance().setOnEcbModeExitResponse(onComplete,
                    CallTracker.EVENT_EXIT_ECM_RESPONSE_CDMA, null);
            mPendingExitEcbmReq = true;
        }
        if (isPhoneInScbm) {
            try {
                phone.exitScbm();
            } catch (Exception e) {
                throw e;
            }
            phone.setOnScbmExitResponse(onComplete,
                    CallTracker.EVENT_EXIT_SCBM_RESPONSE_CDMA, null);
            mPendingExitScbmReq = true;
        }
    }

    public void setPendingExitEcbmReq(boolean pendingExitEcbmReq) {
        mPendingExitEcbmReq = pendingExitEcbmReq;
    }

    public boolean getPendingExitEcbmReq() {
        return mPendingExitEcbmReq;
    }

    public void setPendingExitScbmReq(boolean pendingExitScbmReq) {
        mPendingExitScbmReq = pendingExitScbmReq;
    }

    public boolean getPendingExitScbmReq() {
        return mPendingExitScbmReq;
    }
}