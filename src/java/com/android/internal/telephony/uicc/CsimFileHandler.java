// QTI_BEGIN: 2012-09-07: Telephony: Remove CdmaLteUicc objects
/*
 * Copyright (C) 2006, 2012 The Android Open Source Project
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

// QTI_END: 2012-09-07: Telephony: Remove CdmaLteUicc objects
package com.android.internal.telephony.uicc;
// QTI_BEGIN: 2012-09-07: Telephony: Remove CdmaLteUicc objects

import com.android.internal.telephony.CommandsInterface;
// QTI_END: 2012-09-07: Telephony: Remove CdmaLteUicc objects
import com.android.telephony.Rlog;
// QTI_BEGIN: 2012-09-07: Telephony: Remove CdmaLteUicc objects

/**
// QTI_END: 2012-09-07: Telephony: Remove CdmaLteUicc objects
 * @hide
// QTI_BEGIN: 2012-09-07: Telephony: Remove CdmaLteUicc objects
 * This class should be used to access files in CSIM ADF
 */
public final class CsimFileHandler extends IccFileHandler implements IccConstants {
// QTI_END: 2012-09-07: Telephony: Remove CdmaLteUicc objects
    static final String LOG_TAG = "CsimFH";
// QTI_BEGIN: 2012-09-07: Telephony: Remove CdmaLteUicc objects

    public CsimFileHandler(UiccCardApplication app, String aid, CommandsInterface ci) {
        super(app, aid, ci);
    }

    @Override
    protected String getEFPath(int efid) {
        switch(efid) {
        case EF_SMS:
        case EF_CST:
        case EF_FDN:
        case EF_MSISDN:
        case EF_RUIM_SPN:
        case EF_CSIM_LI:
        case EF_CSIM_MDN:
        case EF_CSIM_IMSIM:
        case EF_CSIM_CDMAHOME:
        case EF_CSIM_EPRL:
// QTI_END: 2012-09-07: Telephony: Remove CdmaLteUicc objects
// QTI_BEGIN: 2018-03-14: Telephony: Define EF MSPL/MLPL/PRL values and paths
        case EF_CSIM_PRL:
// QTI_END: 2018-03-14: Telephony: Define EF MSPL/MLPL/PRL values and paths
        case EF_CSIM_MIPUPP:
// QTI_BEGIN: 2018-03-14: Telephony: Define EF MSPL/MLPL/PRL values and paths
        case EF_RUIM_ID:
// QTI_END: 2018-03-14: Telephony: Define EF MSPL/MLPL/PRL values and paths
// QTI_BEGIN: 2012-09-07: Telephony: Remove CdmaLteUicc objects
            return MF_SIM + DF_ADF;
// QTI_END: 2012-09-07: Telephony: Remove CdmaLteUicc objects
// QTI_BEGIN: 2018-03-14: Telephony: Define EF MSPL/MLPL/PRL values and paths
        case EF_CSIM_MSPL:
        case EF_CSIM_MLPL:
            return MF_SIM + DF_TELECOM + DF_MMSS;
// QTI_END: 2018-03-14: Telephony: Define EF MSPL/MLPL/PRL values and paths
// QTI_BEGIN: 2012-09-07: Telephony: Remove CdmaLteUicc objects
        }
        String path = getCommonIccEFPath(efid);
        if (path == null) {
            // The EFids in UICC phone book entries are decided by the card manufacturer.
            // So if we don't match any of the cases above and if its a UICC return
            // the global 3g phone book path.
            return MF_SIM + DF_TELECOM + DF_PHONEBOOK;
        }
        return path;
    }

    @Override
    protected void logd(String msg) {
// QTI_END: 2012-09-07: Telephony: Remove CdmaLteUicc objects
        Rlog.d(LOG_TAG, msg);
// QTI_BEGIN: 2012-09-07: Telephony: Remove CdmaLteUicc objects
    }

    @Override
    protected void loge(String msg) {
// QTI_END: 2012-09-07: Telephony: Remove CdmaLteUicc objects
        Rlog.e(LOG_TAG, msg);
// QTI_BEGIN: 2012-09-07: Telephony: Remove CdmaLteUicc objects
    }
}
// QTI_END: 2012-09-07: Telephony: Remove CdmaLteUicc objects
