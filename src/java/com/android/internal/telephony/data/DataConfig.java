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

import android.annotation.NonNull;

import com.android.internal.telephony.TelephonyConfigData;
import com.android.telephony.Rlog;

/**
 * DataConfig is utility class for data.
 * It is obtained through the getConfig() at the DataConfigParser.
 * TODO b/473478590 - impl this Data Config.
 */
public class DataConfig {
    private static final String TAG = "DataConfig";

    private TelephonyConfigData.DataConfigProto mConfigData;

    public DataConfig(@NonNull TelephonyConfigData.DataConfigProto configData) {
        logd("DataConfig: constructing with configData: " + configData);
        mConfigData = configData;
    }

    @NonNull
    public TelephonyConfigData.DataConfigProto getConfigData() {
        return mConfigData;
    }

    private static void logd(String log) {
        Rlog.d(TAG, log);
    }
}
