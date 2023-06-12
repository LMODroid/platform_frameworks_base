/*
 * Copyright (C) 2023 LibreMobileOS Foundation
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

import static android.os.Process.THREAD_PRIORITY_DEFAULT;
import static android.hardware.biometrics.BiometricAuthenticator.TYPE_FACE;
import static android.hardware.biometrics.BiometricManager.Authenticators;

import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.biometrics.BiometricManager;
import android.hardware.biometrics.IBiometricService;
import android.hardware.face.IFaceService;
import android.hardware.face.FaceSensorProperties;
import android.hardware.face.FaceSensorPropertiesInternal;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.util.Slog;

import com.android.internal.R;
import com.android.server.ServiceThread;
import com.android.server.SystemService;
import com.android.server.biometrics.Utils;

import java.util.ArrayList;
import java.util.List;

import com.libremobileos.faceunlock.server.FaceUnlockServer;

public class FaceUnlockService extends SystemService {
	private static final String TAG = "FaceUnlockService";

	private static boolean sSupportsFace = false;
	private static int sCompleted = 0;
	private static boolean sCompletedSoftFace = false;
	private static boolean sSupportsSelfIllumination = false;
	private static int sMaxTemplatesAllowed = 0;

	private final Context mContext;
	private FaceUnlockServer mServer;

	public FaceUnlockService(Context context) {
		super(context);

		mContext = context;
	}

	@Override
	public void onStart() {
		final PackageManager pm = mContext.getPackageManager();
		sSupportsSelfIllumination = getContext().getResources().getBoolean(
				R.bool.config_faceAuthSupportsSelfIllumination);
		sMaxTemplatesAllowed = getContext().getResources().getInteger(
				R.integer.config_faceMaxTemplatesPerUser);
		sSupportsFace = pm.hasSystemFeature(PackageManager.FEATURE_FACE);
		if (!pm.hasSystemFeature(PackageManager.FEATURE_FINGERPRINT)) sCompleted++;
		if (!pm.hasSystemFeature(PackageManager.FEATURE_IRIS)) sCompleted++;
		if (sSupportsFace) {
			ServiceThread st = new ServiceThread(TAG, THREAD_PRIORITY_DEFAULT, false);
			st.start();
			mServer = new FaceUnlockServer(mContext, st.getLooper(), this::publishBinderService);
			onInitComplete();
		} else {
			mServer = null;
			sCompletedSoftFace = true;
		}
	}

	// called from iris, face, fp and ourselves
	public static void onInitComplete() {
        if (++sCompleted == 4) {
			if (sSupportsFace && !sCompletedSoftFace) {
				try {
					IBiometricService biometricService = IBiometricService.Stub.asInterface(
						ServiceManager.getService(Context.BIOMETRIC_SERVICE));
					IFaceService faceService = IFaceService.Stub.asInterface(
						ServiceManager.getService(Context.FACE_SERVICE));
					if ((biometricService.getSupportedModalities(Authenticators.BIOMETRIC_WEAK) & TYPE_FACE) == 0) {
						int newId = 0;
						// IDs may come from HALs and be non-linear, ensure we really have unique ID
						// if ID is duplicated, we crash system server
						boolean foundDuplicate = false;
						do {
							if (foundDuplicate) {
								newId++;
							}
							foundDuplicate = biometricService.getCurrentStrength(newId)
									!= Authenticators.EMPTY_SET;
						} while (foundDuplicate);
						if (faceService != null) {
							faceService.registerAuthenticators(List.of(getHidlFaceSensorProps(newId,
									Authenticators.BIOMETRIC_STRONG)));
							Slog.i(TAG, "Using software face sensor.");
						} else {
							Slog.e(TAG, "Software face configuration exists, but FaceService is null.");
						}
					} else {
						Slog.i(TAG, "Using hardware face sensor.");
					}
				} catch (RemoteException e) {
					Slog.e(TAG, "RemoteException when loading face configuration", e);
				}
			} else {
				Slog.i(TAG, "Not using any face sensor.");
			}
			sCompletedSoftFace = true;
		} else {
			Slog.i(TAG, "Reporting event count: " + sCompleted);
		}
	}

	private static FaceSensorPropertiesInternal getHidlFaceSensorProps(int sensorId,
			@BiometricManager.Authenticators.Types int strength) {
			// see AuthService.java getHidlFaceSensorProps()
		return new FaceSensorPropertiesInternal(sensorId,
				Utils.authenticatorStrengthToPropertyStrength(strength), sMaxTemplatesAllowed,
				new ArrayList<>(), FaceSensorProperties.TYPE_UNKNOWN, false, sSupportsSelfIllumination, true);
	}
}
