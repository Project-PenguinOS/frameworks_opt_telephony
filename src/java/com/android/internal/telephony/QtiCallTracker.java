/*
 * Copyright (c) Qualcomm Technologies, Inc. and/or its subsidiaries.
 * SPDX-License-Identifier: BSD-3-Clause-Clear
 */

package com.android.internal.telephony;

import android.content.Context;
import android.os.Handler;
import android.telecom.PhoneAccount;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;
import android.telephony.TelephonyManager;
import android.util.Log;

/**
 * Utility class to handle Psuedo DSDA calls
 */
public class QtiCallTracker {

    private static final String LOG_TAG = "QtiCallTracker";

    /**
     * Determines whether incoming call is a pseudo-DSDA call.
     * @param phone the Phone receiving the incoming call
     * Returns false immediately for:
     *     1. SINGLE SIM configuration
     *     2. If the other SUB which receives the incoming call does not have any calls
     *     3. DSDA (part of the other phone account's calling restriction)
     * Returns true if
     *     1. If the SUB which receives the incoming call does not have any other calls
     *        and the other SUB has calls, otherwise returns false
     */

    public static boolean isPseudoDsdaCall(Phone phone) {
        if (phone == null) return false;
        Context context = phone.getContext();
        if (context == null) return false;
        TelephonyManager telephony = TelephonyManager.from(context);

        if (telephony.getActiveModemCount() <= PhoneConstants.MAX_PHONE_COUNT_SINGLE_SIM) {
            return false;
        }

        Phone otherOffhookPhone = getOtherOffHookPhone(phone);
        // If the other SUB is not offhook, then no need to check further
        // Ex cases which are handled (and vice versa):
        //  1) SUB1: INCOMING
        //     SUB2: IDLE
        //  2) SUB1: OFFHOOK + INCOMING
        //     SUB2: IDLE
        if (otherOffhookPhone == null) {
            return false;
        }

        if (containedInSimultaneousRestriction(phone.getDefaultPhone(), otherOffhookPhone)) {
            Log.d(LOG_TAG, "containsPhoneAccount in simultaneous restriction, so do not treat as"+
                            " pseudo dsda");
            return false;
        }
        // For cases where the UE is not configured for DSDA, identify pseudo-DSDA cases
        boolean hasCallOnSameSub = phone.getDefaultPhone().getState()
                == PhoneConstants.State.OFFHOOK;
        // ex: SUB1: ACTIVE
        //     SUB2: INCOMING --> treat as pseudo-DSDA

        if (!hasCallOnSameSub) {
            Log.d(LOG_TAG, "This is a pseudo dsda call");
            return true;
        }
        // ex: SUB1: OFFHOOK + INCOMING
        //     SUB2: OFFHOOK --> do not treat as pseudo-DSDA since there is already a call on SUB1
        return false;
    }

    // helper function to check and return the offhook phone if it is
    // not the phone where the incoming call resides
    private static Phone getOtherOffHookPhone(Phone ringingPhone) {
        if (ringingPhone == null) {
            return null;
        }
        for (Phone phone : PhoneFactory.getPhones()) {
            if (phone.getSubId() != ringingPhone.getSubId()
                    && phone.getState() == PhoneConstants.State.OFFHOOK) {
                return phone;
            }
        }
        return null;
    }

    // Checks if device is configured for DSDA, where the offhook phone account
    // is a part of the incoming phone account's simultaneous restriction
    private static boolean containedInSimultaneousRestriction(Phone ringing, Phone offhook) {
        if (offhook == null || ringing == null) {
            Log.d(LOG_TAG, "Either offhook or ringing call is null, which is not expected");
            return false;
        }
        Context context = ringing.getContext();

        if(context == null) {
            return false;
        }

        final TelecomManager telecomManager = context.getSystemService(TelecomManager.class);
        final TelephonyManager telephonyManager = TelephonyManager.from(context);

        PhoneAccountHandle ringingAccount = telephonyManager != null
                ? telephonyManager.getPhoneAccountHandleForSubscriptionId(ringing.getSubId())
                : null;
        PhoneAccountHandle offhookAccount = telephonyManager != null
                ? telephonyManager.getPhoneAccountHandleForSubscriptionId(offhook.getSubId())
                : null;

        if (ringingAccount == null || offhookAccount == null) {
            Log.d(LOG_TAG, "Null phone account handle for ringing phone");
            return false;
        }

        PhoneAccount pa = telecomManager != null
                ? telecomManager.getPhoneAccount(offhookAccount)
                : null;
        if (pa != null && pa.hasSimultaneousCallingRestriction()) {
            if (pa.getSimultaneousCallingRestriction().contains(ringingAccount)) {
                Log.d(LOG_TAG, "contains phone account in simultaneous calling restriction");
                return true;
            }
        }
        return false;
    }

}
