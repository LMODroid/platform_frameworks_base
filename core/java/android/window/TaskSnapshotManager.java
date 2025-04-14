/*
 * Copyright (C) 2025 The Android Open Source Project
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

package android.window;

import android.annotation.IntDef;
import android.annotation.NonNull;
import android.app.ActivityTaskManager;
import android.os.RemoteException;
import android.util.Singleton;
import android.util.Slog;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Retrieve or request app snapshots in system.
 *
 * @hide
 */
public class TaskSnapshotManager {

    private static final String TAG = "TaskSnapshotManager";

    /**
     * Set or retrieve the high resolution snapshot.
     */
    public static final int RESOLUTION_HIGH = 1;

    /**
     * Set or retrieve the low resolution snapshot.
     */
    public static final int RESOLUTION_LOW = 2;

    /**
     * Retrieve in any resolution.
     */
    public static final int RESOLUTION_ANY = 3;

    /**
     * Flags for which kind of resolution snapshot.
     *
     * @hide
     */
    @IntDef(prefix = { "RESOLUTION_" }, value = {
            RESOLUTION_HIGH,
            RESOLUTION_LOW,
            RESOLUTION_ANY
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface Resolution {}

    private static final TaskSnapshotManager sInstance = new TaskSnapshotManager();
    private TaskSnapshotManager() { }

    public static TaskSnapshotManager getInstance() {
        return sInstance;
    }

    /**
     * Fetches the snapshot for the task with the given id.
     *
     * @param taskId the id of the task to retrieve for
     * @param retrieveResolution the resolution we want to load.
     *
     * @return a graphic buffer representing a screenshot of a task, or {@code null} if no
     *         screenshot can be found.
     */
    public TaskSnapshot getTaskSnapshot(int taskId, @Resolution int retrieveResolution)
            throws RemoteException {
        final TaskSnapshot t;
        try {
            t = ISnapshotManagerSingleton.get().getTaskSnapshot(taskId, retrieveResolution);
        } catch (RemoteException r) {
            Slog.e(TAG, "getTaskSnapshot fail: " + r);
            throw r;
        }
        return t;
    }

    /**
     * Requests for a new snapshot to be taken for the task with the given id, storing it in the
     * task snapshot cache only if requested.
     *
     * @param taskId the id of the task to take a snapshot of
     * @param updateCache Whether to store the new snapshot in the system's task snapshot cache.
     *                    If it is true, the snapshot can be either real content or app-theme mode
     *                    depending on the attributes of app. Otherwise, the snapshot will be taken
     *                    with real content.
     * @return a graphic buffer representing a screenshot of a task,  or {@code null} if no
     *         corresponding task can be found.
     */
    public TaskSnapshot takeTaskSnapshot(int taskId, boolean updateCache) throws RemoteException {
        final TaskSnapshot t;
        try {
            t = ISnapshotManagerSingleton.get().takeTaskSnapshot(taskId, updateCache);
        } catch (RemoteException r) {
            Slog.e(TAG, "getTaskSnapshot fail: " + r);
            throw r;
        }
        return t;
    }

    /**
     * @return Whether the resolution of snapshot align with requested resolution.
     */
    public static boolean isResolutionMatch(@NonNull TaskSnapshot snapshot,
            @Resolution int retrieveResolution) {
        if (retrieveResolution == RESOLUTION_ANY) {
            return true;
        }
        final boolean isLowRes = snapshot.isLowResolution();
        if (isLowRes) {
            return retrieveResolution == TaskSnapshotManager.RESOLUTION_LOW;
        }
        return true;
    }

    /**
     * @return Util method, convert the isLowResolution either FLAG_LOW_RES or FLAG_HIGH_RES.
     */
    public static int convertRetrieveFlag(boolean isLowResolution) {
        return isLowResolution ? TaskSnapshotManager.RESOLUTION_LOW
                : TaskSnapshotManager.RESOLUTION_HIGH;
    }

    private static final Singleton<ITaskSnapshotManager> ISnapshotManagerSingleton =
            new Singleton<ITaskSnapshotManager>() {
                @Override
                protected ITaskSnapshotManager create() {
                    try {
                        return ActivityTaskManager.getService().getTaskSnapshotManager();
                    } catch (RemoteException e) {
                        return null;
                    }
                }
            };
}
