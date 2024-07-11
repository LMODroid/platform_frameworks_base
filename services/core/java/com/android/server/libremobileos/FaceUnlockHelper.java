/**
 * Copyright (C) 2024 The LibreMobileOS Foundation
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
package com.android.server.libremobileos;

import static android.hardware.biometrics.BiometricAuthenticator.TYPE_FACE;
import static android.hardware.biometrics.BiometricManager.Authenticators;

import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.biometrics.BiometricManager;
import android.hardware.biometrics.IBiometricService;
import android.hardware.face.IFaceService;
import android.hardware.face.FaceSensorProperties;
import android.hardware.face.FaceSensorPropertiesInternal;
import android.hardware.fingerprint.FingerprintManager;
import android.hardware.fingerprint.FingerprintSensorPropertiesInternal;
import android.hardware.fingerprint.IFingerprintAuthenticatorsRegisteredCallback;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.util.Slog;

import com.android.internal.R;
import com.android.server.biometrics.Utils;

import java.util.ArrayList;
import java.util.List;

public class FaceUnlockHelper {

    private static final String TAG = "FaceUnlockHelper";

    private final Context mContext;

    public FaceUnlockHelper(Context context) {
        mContext = context;
    }

    public void registerLMOFaceAuthenticator() {
        final PackageManager pm = mContext.getPackageManager();
        boolean supportsFace = pm.hasSystemFeature(PackageManager.FEATURE_FACE);
        // Ignore if device don't support it
        if (!supportsFace) return;

        // We have to wait until fingerprint authenticators are registered
        // so we can get the fingerprint sensor IDs to avoid duplication
        // with our randomly generated face sensor ID.
        FingerprintManager fingerprintManager =
                mContext.getSystemService(FingerprintManager.class);
        if (fingerprintManager != null) {
            fingerprintManager.addAuthenticatorsRegisteredCallback(
                    new IFingerprintAuthenticatorsRegisteredCallback.Stub() {
                @Override
                public void onAllAuthenticatorsRegistered(
                        List<FingerprintSensorPropertiesInternal> fingerprintSensors) {
                    initializeIds();
                }
            });
        } else {
            initializeIds();
        }
    }

    private void initializeIds() {
        try {
            IBiometricService biometricService = IBiometricService.Stub.asInterface(
                    ServiceManager.getService(Context.BIOMETRIC_SERVICE));
            if (biometricService == null) {
                Slog.e(TAG, "Face feature exists, but IBiometricService is null.");
                return;
            }
            IFaceService faceService = IFaceService.Stub.asInterface(
                    ServiceManager.getService(Context.FACE_SERVICE));
            if (faceService == null) {
                Slog.e(TAG, "Face feature exists, but FaceService is null.");
                return;
            }
            List<FaceSensorPropertiesInternal> faceSensors =
                    faceService.getSensorPropertiesInternal(mContext.getOpPackageName());
            if (faceSensors.size() == 0) {
                Slog.i(TAG, "Using software face sensor.");
                int newId = 0;
                // IDs may come from HALs and be non-linear, ensure we really have unique ID,
                // because if ID is duplicated, we crash system server!
                boolean foundDuplicate = false;
                do {
                    if (foundDuplicate) {
                        newId++;
                    }
                    foundDuplicate = biometricService.getCurrentStrength(newId)
                            != Authenticators.EMPTY_SET;
                } while (foundDuplicate);
                faceService.registerAuthenticators(getHidlFaceSensorProps(newId,
                        Authenticators.BIOMETRIC_WEAK));
            } else {
                Slog.i(TAG, "Using hardware face sensor.");
            }
        } catch (RemoteException e) {
            Slog.e(TAG, "RemoteException when loading face configuration", e);
        }
    }

    private List<FaceSensorPropertiesInternal> getHidlFaceSensorProps(int sensorId,
            @BiometricManager.Authenticators.Types int strength) {
        // see AuthService.java getHidlFaceSensorProps()
        final boolean supportsSelfIllumination = mContext.getResources().getBoolean(
                R.bool.config_faceAuthSupportsSelfIllumination);
        final int maxTemplatesAllowed = mContext.getResources().getInteger(
                R.integer.config_faceMaxTemplatesPerUser);
        return List.of(new FaceSensorPropertiesInternal(sensorId,
                Utils.authenticatorStrengthToPropertyStrength(strength), maxTemplatesAllowed,
                new ArrayList<>(), FaceSensorProperties.TYPE_UNKNOWN, false,
                supportsSelfIllumination, true));
    }
}
