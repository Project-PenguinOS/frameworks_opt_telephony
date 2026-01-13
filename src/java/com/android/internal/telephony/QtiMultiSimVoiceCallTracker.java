/*
 * Copyright (c) Qualcomm Technologies, Inc. and/or its subsidiaries.
 * SPDX-License-Identifier: BSD-3-Clause-Clear
 */

package com.android.internal.telephony;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.sysprop.TelephonyProperties;
import android.telephony.TelephonyManager;
import android.util.Log;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;

/**
 * Helper class to track MSIM Voice capalibilty
 */
public class QtiMultiSimVoiceCallTracker {

    private static final String LOG_TAG = "QtiMultiSimVoiceCallTracker";

    public static final String ACTION_MSIM_VOICE_CAPABILITY =
            "org.codeaurora.intent.action.MSIM_VOICE_CAPABILITY";
    public static final String PERMISSION_MSIM_VOICE_CAPABILITY =
            "com.qti.permission.RECEIVE_MSIM_VOICE_CAPABILITY";
    public static final String EXTRAS_MSIM_VOICE_CAPABILITY = "MsimVoiceCapability";
    public static final String EXTRAS_DSDS_TRANSITION_SUPPORTED =
            "DsdsTransitionSupported";
    private final Context mContext;
    private final Handler mHandler;
    private static final int EVENT_SIMULTANEOUS_CALLING_SUPPORT_CHANGED = 106;

    public QtiMultiSimVoiceCallTracker (Context context, Handler handler) {
        mContext = context;
        mHandler = handler;
        if (mContext != null) {
            mContext.registerReceiver(ConcurrentCallsReceiver,
                new IntentFilter(ACTION_MSIM_VOICE_CAPABILITY),
                PERMISSION_MSIM_VOICE_CAPABILITY,
                null, Context.RECEIVER_EXPORTED);
        }
    }

    private BroadcastReceiver ConcurrentCallsReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            int voiceCapability = intent.getIntExtra(EXTRAS_MSIM_VOICE_CAPABILITY,
                TelephonyManager.MultiSimVoiceCapability.UNSUPPORTED);
            boolean isDsdsTransitionSupported =
                intent.getBooleanExtra(EXTRAS_DSDS_TRANSITION_SUPPORTED,
                    false);
            Log.d(LOG_TAG,"ConcurrentCallsReceiver: voiceCapability : " + voiceCapability +
                " + isDsdsTransitionSupported : " + isDsdsTransitionSupported);
            TelephonyProperties.multi_sim_voice_capability(voiceCapability);
            TelephonyProperties.dsds_transition_supported(isDsdsTransitionSupported);
            // in cases where multi_sim_voice_capability is still used and simultaneous
            // calling API(s) are not supported, generate the simultaneous calling info
            // using the property values
            if (voiceCapability != TelephonyManager.MultiSimVoiceCapability.UNSUPPORTED &&
                mHandler != null) {
                mHandler.sendMessage(
                    mHandler.obtainMessage(
                        EVENT_SIMULTANEOUS_CALLING_SUPPORT_CHANGED));
            }
        }
    };

    // helper function used to generate the simultaneous calling support array
    // based on the multi_sim_voice_capability value. This is used for backwards
    // compatibility where the lower layers don't support the new simultaneous
    // calling API(s)
    public List<Integer> generateSimultaneousCallingSupport() {
        Log.d(LOG_TAG,"generateSimultaneousCallingSupport");
        List<Integer> simultaneousCallingSupported = new ArrayList<>();
        int simVoiceConfig = TelephonyProperties.multi_sim_voice_capability().orElse(
                TelephonyManager.MultiSimVoiceCapability.UNKNOWN);
        if (simVoiceConfig == TelephonyManager.MultiSimVoiceCapability.DSDA) {
            simultaneousCallingSupported.addAll(Arrays.asList(0,1));
        }
        return simultaneousCallingSupported;
    }

}
