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

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.content.Context;
import android.content.Intent;
import android.os.FileUtils;
import android.util.Log;

import com.android.internal.annotations.GuardedBy;
import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.telephony.TelephonyConfigData;
import com.android.internal.telephony.data.DataConfig;
import com.android.internal.telephony.data.DataConfigParser;
import com.android.internal.telephony.data.DataUtils;
import com.android.internal.telephony.satellite.SatelliteConfig;
import com.android.internal.telephony.satellite.SatelliteConfigParser;
import com.android.internal.telephony.satellite.SatelliteConstants;
import com.android.internal.telephony.satellite.metrics.ConfigUpdaterMetricsStats;
import com.android.internal.telephony.util.TelephonyUtils;
import com.android.server.updates.ConfigUpdateInstallReceiver;

import libcore.io.IoUtils;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;

public class TelephonyConfigUpdateInstallReceiver extends ConfigUpdateInstallReceiver implements
        ConfigProviderAdaptor {

    private static final String TAG = "TelephonyConfigUpdateInstallReceiver";
    @VisibleForTesting(visibility = VisibleForTesting.Visibility.PRIVATE)
    protected static final String UPDATE_DIR = "/data/misc/telephonyconfig";
    @VisibleForTesting(visibility = VisibleForTesting.Visibility.PRIVATE)
    protected static final String NEW_CONFIG_CONTENT_PATH = "new_telephony_config.pb";
    protected static final String VALID_CONFIG_CONTENT_PATH = "valid_telephony_config.pb";
    private static final String BACKUP_CONTENT_PATH = "backup_telephony_config.pb";

    protected static final String UPDATE_METADATA_PATH = "metadata/";
    public static final String VERSION = "version";
    public static final String BACKUP_VERSION = "backup_version";

    private ConcurrentHashMap<Executor, Callback> mCallbackHashMap = new ConcurrentHashMap<>();
    @NonNull
    private final Object mConfigParserLock = new Object();
    @GuardedBy("mConfigParserLock")
    private final Map<String, ConfigParser> mConfigParsers = new ConcurrentHashMap<>();
    @NonNull
    private final ConfigUpdaterMetricsStats mConfigUpdaterMetricsStats;

    private int mOriginalVersion;

    public static TelephonyConfigUpdateInstallReceiver sReceiverAdaptorInstance =
            new TelephonyConfigUpdateInstallReceiver();

    /**
     * @return The singleton instance of TelephonyConfigUpdateInstallReceiver
     */
    @NonNull
    public static TelephonyConfigUpdateInstallReceiver getInstance() {
        return sReceiverAdaptorInstance;
    }

    public TelephonyConfigUpdateInstallReceiver() {
        super(UPDATE_DIR, NEW_CONFIG_CONTENT_PATH, UPDATE_METADATA_PATH, VERSION);
        mConfigUpdaterMetricsStats = ConfigUpdaterMetricsStats.getOrCreateInstance();
    }

    /**
     * @return byte array type of config data protobuffer file
     */
    @Nullable
    @VisibleForTesting(visibility = VisibleForTesting.Visibility.PRIVATE)
    public byte[] getContentFromContentPath(@NonNull File contentPath) {
        try {
            return IoUtils.readFileAsByteArray(contentPath.getCanonicalPath());
        } catch (IOException e) {
            Log.e(TAG, "Failed to read current content : " + contentPath);
            return null;
        }
    }

    /**
     * @param parser target of validation.
     * @return {@code true} if all the config data are valid {@code false} otherwise.
     */
    @VisibleForTesting(visibility = VisibleForTesting.Visibility.PRIVATE)
    public boolean isValidSatelliteCarrierConfigData(@NonNull ConfigParser parser) {
        SatelliteConfig satelliteConfig = (SatelliteConfig) parser.getConfig();
        if (satelliteConfig == null) {
            Log.e(TAG, "satelliteConfig is null");
            mConfigUpdaterMetricsStats.reportOemAndCarrierConfigError(
                    SatelliteConstants.CONFIG_UPDATE_RESULT_NO_SATELLITE_DATA);
            return false;
        }

        // If no carrier config exist then it is considered as a valid config
        Set<Integer> carrierIds = satelliteConfig.getAllSatelliteCarrierIds();
        for (int carrierId : carrierIds) {
            Map<String, Set<Integer>> plmnsServices =
                    satelliteConfig.getSupportedSatelliteServices(carrierId);
            Set<String> plmns = plmnsServices.keySet();
            for (String plmn : plmns) {
                if (!TelephonyUtils.isValidPlmn(plmn)) {
                    Log.e(TAG, "found invalid plmn : " + plmn);
                    mConfigUpdaterMetricsStats.reportCarrierConfigError(
                            SatelliteConstants.CONFIG_UPDATE_RESULT_INVALID_PLMN);
                    return false;
                }
                Set<Integer> serviceSet = plmnsServices.get(plmn);
                for (int service : serviceSet) {
                    if (!TelephonyUtils.isValidService(service)) {
                        Log.e(TAG, "found invalid service : " + service);
                        mConfigUpdaterMetricsStats.reportCarrierConfigError(SatelliteConstants
                                .CONFIG_UPDATE_RESULT_CARRIER_DATA_INVALID_SUPPORTED_SERVICES);
                        return false;
                    }
                }
            }
        }
        Log.d(TAG, "the config data is valid");
        return true;
    }

    /**
     * Validates if the max allowed datamode is valid
     *
     * @param parser target of validation.
     * @return {@code true} if max allowed datamode is valid, {@code false} otherwise.
     */
    public boolean isValidMaxAllowedDataMode(@NonNull ConfigParser parser) {
        SatelliteConfig satelliteConfig = (SatelliteConfig) parser.getConfig();
        if (satelliteConfig == null) {
            Log.e(TAG, "satelliteConfig is null");
            mConfigUpdaterMetricsStats.reportOemAndCarrierConfigError(
                    SatelliteConstants.CONFIG_UPDATE_RESULT_NO_SATELLITE_DATA);
            return false;
        }

        Integer maxAllowedDataMode = satelliteConfig.getSatelliteMaxAllowedDataMode();
        if (maxAllowedDataMode == null) {
            Log.d(TAG, "maxAllowedDataMode is not set");
            return true;
        }

        if (!DataUtils.isValidDataMode(maxAllowedDataMode)) {
            Log.e(TAG, "found invalid maxAllowedDataMode : " + maxAllowedDataMode);
            mConfigUpdaterMetricsStats.reportCarrierConfigError(
                    SatelliteConstants
                            .CONFIG_UPDATE_RESULT_CARRIER_DATA_INVALID_MAX_ALLOWED_DATA_MODE);
            return false;
        }
        Log.d(TAG, "maxAllowedDataMode is valid");
        return true;
    }

    /**
     * Validates if the satellite provider plmns are valid
     *
     * @param parser target of validation.
     * @return {@code true} if satellite provider plmn are valid, {@code false} otherwise.
     */
    public boolean isValidSatelliteProvider(@NonNull ConfigParser parser) {
        SatelliteConfig satelliteConfig = (SatelliteConfig) parser.getConfig();
        if (satelliteConfig == null) {
            Log.e(TAG, "isValidSatelliteProvider: satelliteConfig is null");
            mConfigUpdaterMetricsStats.reportOemAndCarrierConfigError(
                    SatelliteConstants.CONFIG_UPDATE_RESULT_NO_SATELLITE_DATA);
            return false;
        }

        List<String> satelliteProviderList = satelliteConfig.getDeviceSatelliteProviderList();
        if (satelliteProviderList == null) {
            Log.d(TAG, "isValidSatelliteProvider: satelliteProviderList is not set");
            return true;
        }

        for (String satellitePlmn : satelliteProviderList) {
            if (!TelephonyUtils.isValidPlmn(satellitePlmn)) {
                Log.e(TAG, "isValidSatelliteProvider: invalid plmn = " + satellitePlmn);
                mConfigUpdaterMetricsStats.reportOemConfigError(SatelliteConstants
                        .CONFIG_UPDATE_RESULT_INVALID_PLMN);
                return false;
            }
        }

        Log.d(TAG, "satelliteProviderList is valid");
        return true;
    }

    /**
     * @param parser target of validation.
     * @return {@code true} if all the config data are valid {@code false} otherwise.
     */
    @VisibleForTesting(visibility = VisibleForTesting.Visibility.PRIVATE)
    public boolean isValidDataConfig(@NonNull ConfigParser parser) {
        Log.d(TAG, "isValidDataConfig: parser class=" + parser.getClass().getName());
        if (!(parser instanceof DataConfigParser)) {
            Log.e(TAG, "isValidDataConfig: parser is not DataConfigParser!");
            return false;
        }
        DataConfig dataConfig = (DataConfig) parser.getConfig();
        if (dataConfig == null) {
            Log.e(TAG, "dataConfig is null");
            return false;
        }

        TelephonyConfigData.DataConfigProto configData = dataConfig.getConfigData();
        if (!configData.hasConnectionCapabilityConfigs()) {
            Log.d(TAG, "connection_capability_configs is null");
            return true;
        }

        TelephonyConfigData.ConnectionCapabilityConfig connectionCapabilityConfigs =
                configData.getConnectionCapabilityConfigs();

        if (connectionCapabilityConfigs.hasDefaultConnectionCapabilityConfig()) {
            if (!isConnectionCapabilityMapValid(
                    connectionCapabilityConfigs.getDefaultConnectionCapabilityConfig())) {
                return false;
            }
        }

        if (connectionCapabilityConfigs.getCarrierConnectionCapabilityConfigsCount() > 0) {
            for (TelephonyConfigData.ConnectionCapabilityMap map :
                    connectionCapabilityConfigs.getCarrierConnectionCapabilityConfigsList()) {
                if (!isConnectionCapabilityMapValid(map)) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * Validates the rules within a {@link TelephonyConfigData.ConnectionCapabilityMap}.
     * Each rule string must follow the format:
     * "NetworkCapability:ConnectionCapability:isApnRequired".
     * <ul>
     *     <li>NetworkCapability: An integer representing the network capability.</li>
     *     <li>ConnectionCapability: An integer representing the connection capability.</li>
     *     <li>isApnRequired: A boolean ("true" or "false") indicating if APN is required.</li>
     * </ul>
     *
     * @param map The {@link TelephonyConfigData.ConnectionCapabilityMap} to validate.
     * @return {@code true} if all rules in the map are valid, {@code false} otherwise.
     */
    private boolean isConnectionCapabilityMapValid(
            TelephonyConfigData.ConnectionCapabilityMap map) {
        if (map.getRulesCount() == 0) return true;
        for (String rule : map.getRulesList()) {
            String[] parts = rule.split(":");
            if (parts.length != 3) {
                Log.e(TAG, "Invalid rule format: " + rule);
                return false;
            }
            try {
                // try parsing
                Integer.parseInt(parts[0]); // NetworkCapability
                Integer.parseInt(parts[1]); // ConnectionCapability
                Boolean.parseBoolean(parts[2]);  //isApnRequired
            } catch (NumberFormatException e) {
                Log.e(TAG, "Invalid number format in rule: " + rule);
                return false;
            }
        }
        return true;
    }

    @Override
    @VisibleForTesting(visibility = VisibleForTesting.Visibility.PROTECTED)
    public void postInstall(Context context, Intent intent) {
        postInstall();
    }

    private void postInstall() {
        Log.d(TAG, "Telephony config is updated in file partition");

        byte[] content = getContentFromContentPath(updateContent);
        ConfigParser newSatelliteParser = getNewConfigParser(DOMAIN_SATELLITE, content);
        ConfigParser newDataParser = getNewConfigParser(DOMAIN_DATA, content);

        if (newSatelliteParser == null && newDataParser == null) {
            Log.e(TAG, "newConfigParser is null");
            return;
        }

        if (newSatelliteParser != null) {
            if (!isValidSatelliteCarrierConfigData(newSatelliteParser)) {
                Log.e(TAG, "received config data has invalid satellite carrier config data");
                return;
            }

            if (!isValidMaxAllowedDataMode(newSatelliteParser)) {
                Log.e(TAG, "received config data has invalid max allowed data mode");
                return;
            }

            if (!isValidSatelliteProvider(newSatelliteParser)) {
                Log.e(TAG, "received config data has invalid satellite plmn list");
                return;
            }
        }

        if (newDataParser != null) {
            if (!isValidDataConfig(newDataParser)) {
                Log.e(TAG, "received config data has invalid data config");
                return;
            }
        }

        synchronized (getInstance().mConfigParserLock) {
            if (newSatelliteParser != null) {
                ConfigParser oldParser = getInstance().mConfigParsers.get(DOMAIN_SATELLITE);
                if (oldParser != null) {
                    int updatedVersion = newSatelliteParser.mVersion;
                    int previousVersion = oldParser.mVersion;
                    Log.d(TAG, "previous proto version is " + previousVersion
                            + " | updated proto version is " + updatedVersion);

                    if (updatedVersion <= previousVersion) {
                        Log.e(TAG, "updated proto Version [" + updatedVersion
                                + "] is smaller than previous proto Version ["
                                + previousVersion + "]");
                        mConfigUpdaterMetricsStats.reportOemAndCarrierConfigError(
                                SatelliteConstants.CONFIG_UPDATE_RESULT_INVALID_VERSION);
                        return;
                    }
                }
                getInstance().mConfigParsers.put(DOMAIN_SATELLITE, newSatelliteParser);
                mConfigUpdaterMetricsStats.setConfigVersion(newSatelliteParser.getVersion());
            }

            if (newDataParser != null) {
                ConfigParser oldParser = getInstance().mConfigParsers.get(DOMAIN_DATA);
                if (oldParser != null) {
                    int updatedVersion = newDataParser.mVersion;
                    int previousVersion = oldParser.mVersion;
                    Log.d(TAG, "previous proto version is " + previousVersion
                            + " | updated proto version is " + updatedVersion);

                    if (updatedVersion <= previousVersion) {
                        Log.e(TAG, "updated data proto Version [" + updatedVersion
                                + "] is smaller than previous proto Version ["
                                + previousVersion + "]");
                        return;
                    }
                }
                getInstance().mConfigParsers.put(DOMAIN_DATA, newDataParser);
                // TODO (b/474507460) add Stats here
            }
        }

        if (!getInstance().mCallbackHashMap.keySet().isEmpty()) {
            Iterator<Executor> iterator = getInstance().mCallbackHashMap.keySet().iterator();
            while (iterator.hasNext()) {
                Executor executor = iterator.next();
                // Notify for DOMAIN_SATELLITE
                if (newSatelliteParser != null) {
                    getInstance().mCallbackHashMap.get(executor).onChanged(newSatelliteParser);
                }
                // Notify for DOMAIN_DATA
                if (newDataParser != null) {
                    getInstance().mCallbackHashMap.get(executor).onChanged(newDataParser);
                }
            }
        }

        if (!copySourceFileToTargetFile(NEW_CONFIG_CONTENT_PATH, VALID_CONFIG_CONTENT_PATH)) {
            Log.e(TAG, "fail to copy to the valid satellite carrier config data");
            mConfigUpdaterMetricsStats.reportOemAndCarrierConfigError(
                    SatelliteConstants.CONFIG_UPDATE_RESULT_IO_ERROR);
        }
    }

    @Nullable
    @Override
    public ConfigParser getConfigParser(String domain) {
        Log.d(TAG, "getConfigParser domain: " + domain);
        synchronized (getInstance().mConfigParserLock) {
            if (getInstance().mConfigParsers.get(domain) == null) {
                Log.d(TAG, "CreateNewConfigParser with domain " + domain);
                ConfigParser parser = getNewConfigParser(
                        domain, getContentFromContentPath(new File(updateDir,
                                VALID_CONFIG_CONTENT_PATH)));
                if (parser != null && parser.getConfig() != null) {
                    getInstance().mConfigParsers.put(domain, parser);
                }
            }
            return getInstance().mConfigParsers.get(domain);
        }
    }

    /**
     * Overrides the config parser. Should be used only in tests.
     *
     * @param configParser the config parser that we have to override
     */
    public void overrideConfigParser(ConfigParser configParser) {
        Log.d(TAG, "overrideConfigParser");
        synchronized (getInstance().mConfigParserLock) {
            if (configParser instanceof SatelliteConfigParser) {
                getInstance().mConfigParsers.put(DOMAIN_SATELLITE, configParser);
            } else if (configParser instanceof DataConfigParser) {
                getInstance().mConfigParsers.put(DOMAIN_DATA, configParser);
            }
        }
    }

    @Override
    public void registerCallback(@NonNull Executor executor, @NonNull Callback callback) {
        mCallbackHashMap.put(executor, callback);
    }

    @Override
    public void unregisterCallback(@NonNull Callback callback) {
        Iterator<Executor> iterator = mCallbackHashMap.keySet().iterator();
        while (iterator.hasNext()) {
            Executor executor = iterator.next();
            if (mCallbackHashMap.get(executor) == callback) {
                mCallbackHashMap.remove(executor);
                break;
            }
        }
    }

    @VisibleForTesting(visibility = VisibleForTesting.Visibility.PRIVATE)
    public File getUpdateDir() {
        return getInstance().updateDir;
    }

    @VisibleForTesting(visibility = VisibleForTesting.Visibility.PRIVATE)
    public File getUpdateContent() {
        return getInstance().updateContent;
    }

    @VisibleForTesting(visibility = VisibleForTesting.Visibility.PRIVATE)
    public ConcurrentHashMap<Executor, Callback> getCallbackMap() {
        return getInstance().mCallbackHashMap;
    }

    @VisibleForTesting(visibility = VisibleForTesting.Visibility.PRIVATE)
    public void setCallbackMap(ConcurrentHashMap<Executor, Callback> map) {
        getInstance().mCallbackHashMap = map;
    }

    /**
     * @param data byte array type of config data
     * @return when data is null, return null otherwise return ConfigParser
     */
    @Nullable
    @VisibleForTesting(visibility = VisibleForTesting.Visibility.PRIVATE)
    public ConfigParser getNewConfigParser(String domain, @Nullable byte[] data) {
        if (data == null) {
            Log.d(TAG, "content data is null");
            return null;
        }
        switch (domain) {
            case DOMAIN_SATELLITE:
                return new SatelliteConfigParser(data);
            case DOMAIN_DATA:
                return new DataConfigParser(data);
            default:
                Log.e(TAG, "DOMAIN should be specified");
                mConfigUpdaterMetricsStats.reportOemAndCarrierConfigError(
                        SatelliteConstants.CONFIG_UPDATE_RESULT_INVALID_DOMAIN);
                return null;
        }
    }

    /**
     * @param sourceFileName source file name
     * @param targetFileName target file name
     * @return {@code true} if successful, {@code false} otherwise
     */
    @VisibleForTesting(visibility = VisibleForTesting.Visibility.PRIVATE)
    public boolean copySourceFileToTargetFile(
            @NonNull String sourceFileName, @NonNull String targetFileName) {
        try {
            File sourceFile = new File(UPDATE_DIR, sourceFileName);
            File targetFile = new File(UPDATE_DIR, targetFileName);
            Log.d(TAG, "copy " + sourceFile.getName() + " >> " + targetFile.getName());

            if (sourceFile.exists()) {
                if (targetFile.exists()) {
                    targetFile.delete();
                }
                FileUtils.copy(sourceFile, targetFile);
                FileUtils.copyPermissions(sourceFile, targetFile);
                Log.d(TAG, "success to copy the file " + sourceFile.getName() + " to "
                        + targetFile.getName());
                return true;
            }
        } catch (Exception e) {
            Log.e(TAG, "copy error : " + e);
            return false;
        }
        Log.d(TAG, "source file is not exist, no file to copy");
        return false;
    }

    /**
     * This API should be used by only CTS/unit tests to reset the telephony configs set through
     * config updater
     */
    @VisibleForTesting(visibility = VisibleForTesting.Visibility.PRIVATE)
    public boolean cleanUpTelephonyConfigs() {
        Log.d(TAG, "cleanTelephonyConfigs: resetting the telephony configs");
        try {
            // metadata/version
            File updateMetadataDir = new File(updateDir, UPDATE_METADATA_PATH);
            writeUpdate(
                    updateMetadataDir,
                    updateVersion,
                    new ByteArrayInputStream(Integer.toString(-1).getBytes()));

            // new_telephony_config.pb
            writeUpdate(updateDir, updateContent, new ByteArrayInputStream(new byte[]{}));

            // valid_telephony_config.pb
            File validConfigContentPath = new File(updateDir, VALID_CONFIG_CONTENT_PATH);
            writeUpdate(updateDir, validConfigContentPath, new ByteArrayInputStream(new byte[]{}));
        } catch (IOException e) {
            Log.e(TAG, "Failed to clean telephony config files: " + e);
            return false;
        }

        Log.d(TAG, "cleanTelephonyConfigs: resetting the config parser");
        synchronized (getInstance().mConfigParserLock) {
            getInstance().mConfigParsers.clear();
        }
        return true;
    }


    /**
     * This API is used by CTS to override the version of the config data
     *
     * @param reset   Whether to restore the original version
     * @param version The overriding version
     * @return {@code true} if successful, {@code false} otherwise
     */
    public boolean overrideVersion(boolean reset, int version) {
        Log.d(TAG, "overrideVersion: reset=" + reset + ", version=" + version);
        if (reset) {
            version = mOriginalVersion;
            if (!restoreContentData()) {
                return false;
            }
        } else {
            mOriginalVersion = version;
            if (!backupContentData()) {
                return false;
            }
        }
        return overrideVersion(version);
    }

    private boolean overrideVersion(int version) {
        synchronized (getInstance().mConfigParserLock) {
            try {
                writeUpdate(updateDir, updateVersion,
                        new ByteArrayInputStream(Long.toString(version).getBytes()));
                for (ConfigParser parser : getInstance().mConfigParsers.values()) {
                    parser.overrideVersion(version);
                }
            } catch (IOException e) {
                Log.e(TAG, "overrideVersion: e=" + e);
                return false;
            }
            return true;
        }
    }

    private boolean isFileExists(@NonNull String fileName) {
        Log.d(TAG, "isFileExists");
        if (fileName == null) {
            Log.d(TAG, "fileName cannot be null");
            return false;
        }
        File sourceFile = new File(UPDATE_DIR, fileName);
        return sourceFile.exists() && sourceFile.isFile();
    }

    private boolean backupContentData() {
        if (!isFileExists(VALID_CONFIG_CONTENT_PATH)) {
            Log.d(TAG, VALID_CONFIG_CONTENT_PATH + " is not exit, no need to backup");
            return true;
        }
        if (!copySourceFileToTargetFile(VALID_CONFIG_CONTENT_PATH, BACKUP_CONTENT_PATH)) {
            Log.e(TAG, "backupContentData: fail to backup the config data");
            return false;
        }
        if (!copySourceFileToTargetFile(UPDATE_METADATA_PATH + VERSION,
                UPDATE_METADATA_PATH + BACKUP_VERSION)) {
            Log.e(TAG, "bakpuackupContentData: fail to backup the version");
            return false;
        }
        Log.d(TAG, "backupContentData: backup success");
        return true;
    }

    private boolean restoreContentData() {
        if (!isFileExists(BACKUP_CONTENT_PATH)) {
            Log.d(TAG, BACKUP_CONTENT_PATH + " is not exit, no need to restore");
            return true;
        }
        if (!copySourceFileToTargetFile(BACKUP_CONTENT_PATH, NEW_CONFIG_CONTENT_PATH)) {
            Log.e(TAG, "restoreContentData: fail to restore the config data");
            return false;
        }
        if (!copySourceFileToTargetFile(UPDATE_METADATA_PATH + BACKUP_VERSION,
                UPDATE_METADATA_PATH + VERSION)) {
            Log.e(TAG, "restoreContentData: fail to restore the version");
            return false;
        }
        Log.d(TAG, "restoreContentData: populate the data to SatelliteController");
        postInstall();
        Log.d(TAG, "restoreContentData: success");
        return true;
    }
}
