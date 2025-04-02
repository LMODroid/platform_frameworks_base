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

package com.android.systemui.media.controls.data.repository

import com.android.internal.logging.InstanceId
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.media.controls.data.model.MediaSortKeyModel
import com.android.systemui.media.controls.shared.model.MediaCommonModel
import com.android.systemui.media.controls.shared.model.MediaData
import com.android.systemui.media.controls.shared.model.MediaDataLoadingModel
import com.android.systemui.media.remedia.data.repository.MediaPipelineRepository
import com.android.systemui.util.time.SystemClock
import java.util.TreeMap
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** A repository that holds the state of filtered media data on the device. */
@SysUISingleton
class MediaFilterRepository @Inject constructor(private val systemClock: SystemClock) :
    MediaPipelineRepository() {

    private val _currentMedia: MutableStateFlow<List<MediaCommonModel>> =
        MutableStateFlow(mutableListOf())
    val currentMedia = _currentMedia.asStateFlow()

    private var sortedMedia = TreeMap<MediaSortKeyModel, MediaCommonModel>(comparator)

    override fun addCurrentUserMediaEntry(data: MediaData): Boolean {
        return super.addCurrentUserMediaEntry(data).also {
            addMediaDataLoadingState(MediaDataLoadingModel.Loaded(data.instanceId), it)
        }
    }

    override fun removeCurrentUserMediaEntry(key: InstanceId): MediaData? {
        return super.removeCurrentUserMediaEntry(key)?.also {
            addMediaDataLoadingState(MediaDataLoadingModel.Removed(key))
        }
    }

    override fun removeCurrentUserMediaEntry(key: InstanceId, data: MediaData): Boolean {
        return super.removeCurrentUserMediaEntry(key, data).also {
            addMediaDataLoadingState(MediaDataLoadingModel.Removed(key))
        }
    }

    override fun clearCurrentUserMedia() {
        val userEntries = LinkedHashMap<InstanceId, MediaData>(mutableUserEntries.value)
        mutableUserEntries.value = LinkedHashMap()
        userEntries.forEach { addMediaDataLoadingState(MediaDataLoadingModel.Removed(it.key)) }
    }

    private fun addMediaDataLoadingState(
        mediaDataLoadingModel: MediaDataLoadingModel,
        isUpdate: Boolean = true,
    ) {
        val sortedMap = TreeMap<MediaSortKeyModel, MediaCommonModel>(comparator)
        sortedMap.putAll(
            sortedMedia.filter { (_, commonModel) ->
                commonModel.mediaLoadedModel.instanceId != mediaDataLoadingModel.instanceId
            }
        )

        mutableUserEntries.value[mediaDataLoadingModel.instanceId]?.let {
            val sortKey =
                MediaSortKeyModel(
                    it.isPlaying,
                    it.playbackLocation,
                    it.active,
                    it.resumption,
                    it.lastActive,
                    it.notificationKey,
                    systemClock.currentTimeMillis(),
                    it.instanceId,
                )

            if (mediaDataLoadingModel is MediaDataLoadingModel.Loaded) {
                val newCommonModel =
                    MediaCommonModel(
                        mediaDataLoadingModel,
                        canBeRemoved(it),
                        if (isUpdate) systemClock.currentTimeMillis() else 0,
                    )
                sortedMap[sortKey] = newCommonModel

                var isNewToCurrentMedia = true
                val currentList =
                    mutableListOf<MediaCommonModel>().apply { addAll(_currentMedia.value) }
                currentList.forEachIndexed { index, mediaCommonModel ->
                    if (
                        mediaCommonModel.mediaLoadedModel.instanceId ==
                            mediaDataLoadingModel.instanceId
                    ) {
                        // When loading an update for an existing media control.
                        isNewToCurrentMedia = false
                        if (mediaCommonModel != newCommonModel) {
                            // Update media model if changed.
                            currentList[index] = newCommonModel
                        }
                    }
                }
                if (isNewToCurrentMedia && it.active) {
                    _currentMedia.value = sortedMap.values.toList()
                } else {
                    _currentMedia.value = currentList
                }

                sortedMedia = sortedMap
            }
        }

        // On removal we want to keep the order being shown to user.
        if (mediaDataLoadingModel is MediaDataLoadingModel.Removed) {
            _currentMedia.value =
                _currentMedia.value.filter { commonModel ->
                    mediaDataLoadingModel.instanceId != commonModel.mediaLoadedModel.instanceId
                }
            sortedMedia = sortedMap
        }
    }

    fun setOrderedMedia() {
        _currentMedia.value = sortedMedia.values.toList()
    }

    fun hasActiveMedia(): Boolean {
        return mutableUserEntries.value.any { it.value.active }
    }

    fun hasAnyMedia(): Boolean {
        return mutableUserEntries.value.entries.isNotEmpty()
    }

    private fun canBeRemoved(data: MediaData): Boolean {
        return data.isPlaying?.let { !it } ?: data.isClearable && !data.active
    }
}
