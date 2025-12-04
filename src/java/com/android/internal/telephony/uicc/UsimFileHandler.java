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
 * This class should be used to access files in USIM ADF
 */
public final class UsimFileHandler extends IccFileHandler implements IccConstants {
// QTI_END: 2012-09-07: Telephony: Remove CdmaLteUicc objects
    static final String LOG_TAG = "UsimFH";
// QTI_BEGIN: 2012-09-07: Telephony: Remove CdmaLteUicc objects

    public UsimFileHandler(UiccCardApplication app, String aid, CommandsInterface ci) {
        super(app, aid, ci);
    }

    @Override
    protected String getEFPath(int efid) {
        switch(efid) {
        case EF_SMS:
// QTI_END: 2012-09-07: Telephony: Remove CdmaLteUicc objects
        case EF_EXT5:
// QTI_BEGIN: 2012-09-07: Telephony: Remove CdmaLteUicc objects
        case EF_EXT6:
        case EF_MWIS:
        case EF_MBI:
        case EF_SPN:
        case EF_AD:
        case EF_MBDN:
        case EF_PNN:
        case EF_OPL:
        case EF_SPDI:
        case EF_SST:
        case EF_CFIS:
        case EF_MAILBOX_CPHS:
        case EF_VOICE_MAIL_INDICATOR_CPHS:
        case EF_CFF_CPHS:
        case EF_SPN_CPHS:
        case EF_SPN_SHORT_CPHS:
        case EF_FDN:
// QTI_END: 2012-09-07: Telephony: Remove CdmaLteUicc objects
        case EF_SDN:
        case EF_EXT3:
// QTI_BEGIN: 2012-09-07: Telephony: Remove CdmaLteUicc objects
        case EF_MSISDN:
        case EF_EXT2:
        case EF_INFO_CPHS:
        case EF_CSP_CPHS:
// QTI_END: 2012-09-07: Telephony: Remove CdmaLteUicc objects
        case EF_GID1:
        case EF_GID2:
        case EF_LI:
        case EF_PLMN_W_ACT:
        case EF_OPLMN_W_ACT:
        case EF_HPLMN_W_ACT:
        case EF_EHPLMN:
        case EF_FPLMN:
        case EF_LRPLMNSI:
        case EF_HPPLMN:
        case EF_SMSS:
// QTI_BEGIN: 2012-09-07: Telephony: Remove CdmaLteUicc objects
            return MF_SIM + DF_ADF;

        case EF_PBR:
            // we only support global phonebook.
            return MF_SIM + DF_TELECOM + DF_PHONEBOOK;
        }
        String path = getCommonIccEFPath(efid);
        if (path == null) {
            // The EFids in USIM phone book entries are decided by the card manufacturer.
            // So if we don't match any of the cases above and if its a USIM return
            // the phone book path.
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
