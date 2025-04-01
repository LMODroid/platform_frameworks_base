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

package com.android.systemui.media.remedia.data.repository

import com.android.internal.logging.InstanceId
import com.android.systemui.media.controls.shared.model.MediaData
import com.android.systemui.media.remedia.data.model.MediaDataModel
import kotlinx.coroutines.flow.StateFlow

interface MediaRepository {
    /** Current sorted media sessions. */
    val currentMedia: StateFlow<List<MediaDataModel>>

    fun addMediaEntry(key: String, data: MediaData)

    /**
     * Removes the media entry corresponding to the given [key].
     *
     * @return media data if an entry is actually removed, `null` otherwise.
     */
    fun removeMediaEntry(key: String): MediaData?

    /** @return whether the added media data already exists. */
    fun addCurrentUserMediaEntry(data: MediaData): Boolean

    /**
     * Removes current user media entry given the corresponding [key].
     *
     * @return media data if an entry is actually removed, `null` otherwise.
     */
    fun removeCurrentUserMediaEntry(key: InstanceId): MediaData?

    fun clearCurrentUserMedia()

    /** Seek to [to], in milliseconds on the media session with the given [sessionKey]. */
    fun seek(sessionKey: InstanceId, to: Long)
}
