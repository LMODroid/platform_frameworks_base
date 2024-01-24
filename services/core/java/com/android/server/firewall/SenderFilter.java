/*
 * Copyright (C) 2013 The Android Open Source Project
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

package com.android.server.firewall;

import android.app.AppGlobals;
import android.app.ActivityManager;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.IPackageManager;
import android.content.pm.PackageManager;
import android.os.Process;
import android.os.RemoteException;
import android.util.Slog;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;

abstract class SenderFilter implements Filter {
    private static final String ATTR_TYPE = "type";

    private static final String VAL_SIGNATURE = "signature";
    private static final String VAL_SYSTEM = "system";
    private static final String VAL_SYSTEM_OR_SIGNATURE = "system|signature";
    private static final String VAL_USER_ID = "userId";

    static boolean isPrivilegedApp(int callerUid, int callerPid) {
        if (callerUid == Process.SYSTEM_UID || callerUid == 0 ||
                callerPid == Process.myPid() || callerPid == 0) {
            return true;
        }

        IPackageManager pm = AppGlobals.getPackageManager();
        if (pm == null) {
            return false;
        }
        try {
            return (pm.getPrivateFlagsForUid(callerUid) & ApplicationInfo.PRIVATE_FLAG_PRIVILEGED)
                    != 0;
        } catch (RemoteException ex) {
            Slog.e(IntentFirewall.TAG, "Remote exception while retrieving uid flags",
                    ex);
        }

        return false;
    }

    public static final FilterFactory FACTORY = new FilterFactory("sender") {
        @Override
        public Filter newFilter(XmlPullParser parser) throws IOException, XmlPullParserException {
            String typeString = parser.getAttributeValue(null, ATTR_TYPE);
            if (typeString == null) {
                throw new XmlPullParserException("type attribute must be specified for <sender>",
                        parser, null);
            }
            if (typeString.equals(VAL_SYSTEM)) {
                return SYSTEM;
            } else if (typeString.equals(VAL_SIGNATURE)) {
                return SIGNATURE;
            } else if (typeString.equals(VAL_SYSTEM_OR_SIGNATURE)) {
                return SYSTEM_OR_SIGNATURE;
            } else if (typeString.equals(VAL_USER_ID)) {
                return USER_ID;
            }
            throw new XmlPullParserException(
                    "Invalid type attribute for <sender>: " + typeString, parser, null);
        }
    };

    @Override
    public boolean matches(IntentFirewall ifw, ComponentName resolvedComponent, Intent intent,
            int callerUid, int callerPid, String resolvedType, int receivingUid, int userId) {
        return matchesPackage(ifw, resolvedComponent.getPackageName(), callerUid, receivingUid,
                userId);
    }

    private static final Filter SIGNATURE = new SenderFilter() {
        @Override
        public boolean matchesPackage(IntentFirewall ifw, String resolvedPackage, int callerUid,
                int receivingUid, int userId) {
            return ifw.signaturesMatch(callerUid, receivingUid);
        }
    };

    private static final Filter SYSTEM = new SenderFilter() {
        @Override
        public boolean matches(IntentFirewall ifw, ComponentName resolvedComponent, Intent intent,
                int callerUid, int callerPid, String resolvedType, int receivingUid, int userId) {
            return isPrivilegedApp(callerUid, callerPid);
        }

        @Override
        public boolean matchesPackage(IntentFirewall ifw, String resolvedPackage, int callerUid,
                int receivingUid, int userId) {
            return isPrivilegedApp(callerUid, -1);
        }
    };

    private static final Filter SYSTEM_OR_SIGNATURE = new SenderFilter() {
        @Override
        public boolean matches(IntentFirewall ifw, ComponentName resolvedComponent, Intent intent,
                int callerUid, int callerPid, String resolvedType, int receivingUid, int userId) {
            return isPrivilegedApp(callerUid, callerPid) ||
                    ifw.signaturesMatch(callerUid, receivingUid);
        }

        @Override
        public boolean matchesPackage(IntentFirewall ifw, String resolvedPackage, int callerUid,
                int receivingUid, int userId) {
            return isPrivilegedApp(callerUid, -1) ||
                    ifw.signaturesMatch(callerUid, receivingUid);
        }
    };

    private static final Filter USER_ID = new SenderFilter() {
        @Override
        public boolean matches(IntentFirewall ifw, ComponentName resolvedComponent, Intent intent,
                int callerUid, int callerPid, String resolvedType, int receivingUid, int userId) {
            // This checks whether the caller is either the system process, or has the same user id
            // I.e. the same app, or an app that uses the same shared user id.
            // This is the same set of applications that would be able to access the component if
            // it wasn't exported.
            return ifw.checkComponentPermission(null, callerPid, callerUid, receivingUid, false);
        }

        @Override
        public boolean matchesPackage(IntentFirewall ifw, String resolvedPackage, int callerUid,
                int receivingUid, int userId) {
            return ActivityManager.checkComponentPermission(null, callerUid, receivingUid, false)
                    == PackageManager.PERMISSION_GRANTED;
        }
    };
}
