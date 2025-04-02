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
import com.android.systemui.media.controls.data.model.MediaSortKeyModel
import com.android.systemui.media.controls.shared.model.MediaData
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** An abstract repository class that holds fields and functions called by media pipeline. */
abstract class MediaPipelineRepository {

    protected val mutableUserEntries: MutableStateFlow<Map<InstanceId, MediaData>> =
        MutableStateFlow(LinkedHashMap())
    val currentUserEntries: StateFlow<Map<InstanceId, MediaData>> = mutableUserEntries.asStateFlow()

    private val mutableAllEntries: MutableStateFlow<Map<String, MediaData>> =
        MutableStateFlow(LinkedHashMap())
    val allMediaEntries: StateFlow<Map<String, MediaData>> = mutableAllEntries.asStateFlow()

    protected val comparator =
        compareByDescending<MediaSortKeyModel> {
                it.isPlaying == true && it.playbackLocation == MediaData.PLAYBACK_LOCAL
            }
            .thenByDescending {
                it.isPlaying == true && it.playbackLocation == MediaData.PLAYBACK_CAST_LOCAL
            }
            .thenByDescending { it.active }
            .thenByDescending { !it.isResume }
            .thenByDescending { it.playbackLocation != MediaData.PLAYBACK_CAST_REMOTE }
            .thenByDescending { it.lastActive }
            .thenByDescending { it.updateTime }
            .thenByDescending { it.notificationKey }

    fun addMediaEntry(key: String, data: MediaData) {
        val entries = LinkedHashMap<String, MediaData>(mutableAllEntries.value)
        entries[key] = data
        mutableAllEntries.value = entries
    }

    /**
     * Removes the media entry corresponding to the given [key].
     *
     * @return media data if an entry is actually removed, `null` otherwise.
     */
    fun removeMediaEntry(key: String): MediaData? {
        val entries = LinkedHashMap<String, MediaData>(mutableAllEntries.value)
        val mediaData = entries.remove(key)
        mutableAllEntries.value = entries
        return mediaData
    }

    /** @return whether the added media data already exists. */
    open fun addCurrentUserMediaEntry(data: MediaData): Boolean {
        val entries = LinkedHashMap<InstanceId, MediaData>(mutableUserEntries.value)
        val update = mutableUserEntries.value.containsKey(data.instanceId)
        entries[data.instanceId] = data
        mutableUserEntries.value = entries
        return update
    }

    /**
     * Removes current user media entry given the corresponding key.
     *
     * @return media data if an entry is actually removed, `null` otherwise.
     */
    open fun removeCurrentUserMediaEntry(key: InstanceId): MediaData? {
        val entries = LinkedHashMap<InstanceId, MediaData>(mutableUserEntries.value)
        val mediaData = entries.remove(key)
        mutableUserEntries.value = entries
        return mediaData
    }

    /**
     * Removes current user media entry given a key and media data.
     *
     * @return true if media data is removed, false otherwise.
     */
    open fun removeCurrentUserMediaEntry(key: InstanceId, data: MediaData): Boolean {
        val entries = LinkedHashMap<InstanceId, MediaData>(mutableUserEntries.value)
        val succeed = entries.remove(key, data)
        if (!succeed) {
            return false
        }
        mutableUserEntries.value = entries
        return true
    }

    abstract fun clearCurrentUserMedia()
}
