/**
 * Copyright (C) 2023 The LibreMobileOS Foundation
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

import android.content.pm.UserInfo;

import java.util.List;

/**
 * Internal class for system server to manage parallel space.
 *
 * @hide
 */
public interface ParallelSpaceManagerServiceInternal {

    /**
     * Return parallel owner id if userId is a parallel user.
     */
    int convertToParallelOwnerIfPossible(int userId);

    /**
     * Check whether target user is a parallel user.
     */
    boolean isCurrentParallelUser(int userId);

    /**
     * Get user id of currently foreground parallel space owner.
     */
    int getCurrentParallelOwnerId();

    /**
     * Check whether target user is the parallel owner.
     */
    boolean isCurrentParallelOwner(int userId);

    /**
     * Return a list of current parallel user ids.
     */
    List<Integer> getCurrentParallelUserIds();

    /**
     * Whether a userId in in range of {owner, profiles, parallelSpaces}.
     */
    boolean isInteractive(int userId);

    /**
     * Owner user, profiles and parallel spaces should be able to
     * interact with each other.
     */
    boolean canInteract(int userId1, int userId2);

    /**
     * Interactive users = owner + profiles + parallel spaces.
     */
    List<UserInfo> getInteractiveUsers();

}
