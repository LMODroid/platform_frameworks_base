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

import android.app.WallpaperColors
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.media.session.MediaController
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import com.android.internal.logging.InstanceId
import com.android.systemui.common.shared.model.ContentDescription
import com.android.systemui.common.shared.model.Icon
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Application
import com.android.systemui.dagger.qualifiers.Background
import com.android.systemui.lifecycle.Activatable
import com.android.systemui.lifecycle.Hydrator
import com.android.systemui.media.controls.data.model.MediaSortKeyModel
import com.android.systemui.media.controls.shared.model.MediaData
import com.android.systemui.media.remedia.data.model.MediaDataModel
import com.android.systemui.media.remedia.shared.model.MediaColorScheme
import com.android.systemui.monet.ColorScheme
import com.android.systemui.monet.Style
import com.android.systemui.res.R
import com.android.systemui.util.time.SystemClock
import java.util.TreeMap
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** A repository that holds the state of current media on the device. */
interface MediaRepository : Activatable {
    /** Current sorted media sessions. */
    val currentMedia: List<MediaDataModel>

    /** Seek to [to], in milliseconds on the media session with the given [sessionKey]. */
    fun seek(sessionKey: InstanceId, to: Long)

    /** Reorders media list when media is not visible to user */
    fun reorderMedia()
}

@SysUISingleton
class MediaRepositoryImpl
@Inject
constructor(
    @Application private val applicationContext: Context,
    @Application private val applicationScope: CoroutineScope,
    @Background val backgroundDispatcher: CoroutineDispatcher,
    private val systemClock: SystemClock,
) : MediaRepository, MediaPipelineRepository() {

    private val hydrator = Hydrator(traceName = "MediaRepository.hydrator")
    private val mutableCurrentMedia: MutableStateFlow<List<MediaDataModel>> =
        MutableStateFlow(mutableListOf())
    override val currentMedia by
        hydrator.hydratedStateOf(traceName = "currentMedia", source = mutableCurrentMedia)

    private var sortedMedia = TreeMap<MediaSortKeyModel, MediaDataModel>(comparator)

    override fun addCurrentUserMediaEntry(data: MediaData): Boolean {
        return super.addCurrentUserMediaEntry(data).also { addToSortedMedia(data) }
    }

    override fun removeCurrentUserMediaEntry(key: InstanceId): MediaData? {
        return super.removeCurrentUserMediaEntry(key)?.also { removeFromSortedMedia(it) }
    }

    override fun removeCurrentUserMediaEntry(key: InstanceId, data: MediaData): Boolean {
        return super.removeCurrentUserMediaEntry(key, data).also {
            if (it) {
                removeFromSortedMedia(data)
            }
        }
    }

    override fun clearCurrentUserMedia() {
        val userEntries = LinkedHashMap<InstanceId, MediaData>(mutableUserEntries.value)
        mutableUserEntries.value = LinkedHashMap()
        userEntries.forEach { removeFromSortedMedia(it.value) }
    }

    override fun seek(sessionKey: InstanceId, to: Long) {
        mutableCurrentMedia.value
            .first { sessionKey == it.instanceId }
            .controller
            .transportControls
            .seekTo(to)
    }

    override fun reorderMedia() {
        mutableCurrentMedia.value = sortedMedia.values.toList()
    }

    override suspend fun activate(): Nothing {
        hydrator.activate()
    }

    private fun addToSortedMedia(data: MediaData) {
        val sortedMap = TreeMap<MediaSortKeyModel, MediaDataModel>(comparator)
        val currentModel = sortedMedia.values.find { it.instanceId == data.instanceId }

        sortedMap.putAll(
            sortedMedia.filter { (keyModel, _) -> keyModel.instanceId != data.instanceId }
        )

        mutableUserEntries.value[data.instanceId]?.let { mediaData ->
            with(mediaData) {
                val sortKey =
                    MediaSortKeyModel(
                        isPlaying,
                        playbackLocation,
                        active,
                        resumption,
                        lastActive,
                        notificationKey,
                        systemClock.currentTimeMillis(),
                        instanceId,
                    )

                applicationScope.launch {
                    val mediaModel = toDataModel(currentModel)
                    sortedMap[sortKey] = mediaModel

                    var isNewToCurrentMedia = true
                    val currentList =
                        mutableListOf<MediaDataModel>().apply { addAll(mutableCurrentMedia.value) }
                    currentList.forEachIndexed { index, mediaDataModel ->
                        if (mediaDataModel.instanceId == data.instanceId) {
                            // When loading an update for an existing media control.
                            isNewToCurrentMedia = false
                            if (mediaDataModel != mediaModel) {
                                // Update media model if changed.
                                currentList[index] = mediaModel
                            }
                        }
                    }
                    if (isNewToCurrentMedia && active) {
                        mutableCurrentMedia.value = sortedMap.values.toList()
                    } else {
                        mutableCurrentMedia.value = currentList
                    }

                    sortedMedia = sortedMap
                }
            }
        }
    }

    private fun removeFromSortedMedia(data: MediaData) {
        mutableCurrentMedia.value =
            mutableCurrentMedia.value.filter { model -> data.instanceId != model.instanceId }
        sortedMedia =
            TreeMap(sortedMedia.filter { (keyModel, _) -> keyModel.instanceId != data.instanceId })
    }

    private suspend fun MediaData.toDataModel(currentModel: MediaDataModel?): MediaDataModel {
        return withContext(backgroundDispatcher) {
            val controller =
                if (currentModel != null && currentModel.controller.sessionToken == token) {
                    currentModel.controller
                } else {
                    MediaController(applicationContext, token!!)
                }
            val icon = appIcon?.loadDrawable(applicationContext)
            val background = artwork?.loadDrawable(applicationContext)
            MediaDataModel(
                instanceId = instanceId,
                appUid = appUid,
                packageName = packageName,
                appName = app.toString(),
                appIcon =
                    icon?.let { Icon.Loaded(it, ContentDescription.Loaded(app)) }
                        ?: getAltIcon(packageName),
                background = background?.let { Icon.Loaded(background, null) },
                title = song.toString(),
                subtitle = artist.toString(),
                colorScheme = getScheme(artwork, packageName),
                notificationActions = actions,
                playbackStateActions = semanticActions,
                outputDevice = device,
                clickIntent = clickIntent,
                controller = controller,
                canBeDismissed = isClearable,
                isActive = active,
                isResume = resumption,
                resumeAction = resumeAction,
                isExplicit = isExplicit,
            )
        }
    }

    private suspend fun getScheme(
        artwork: android.graphics.drawable.Icon?,
        packageName: String,
    ): MediaColorScheme? {
        val wallpaperColors = getWallpaperColor(applicationContext, backgroundDispatcher, artwork)
        val colorScheme =
            wallpaperColors?.let { ColorScheme(it, false, Style.CONTENT) }
                ?: let {
                    val launcherIcon = getAltIcon(packageName)
                    if (launcherIcon is Icon.Loaded) {
                        getColorScheme(launcherIcon.drawable)
                    } else {
                        null
                    }
                }
        return colorScheme?.run {
            MediaColorScheme(
                Color(colorScheme.materialScheme.getPrimaryFixed()),
                Color(colorScheme.materialScheme.getOnPrimaryFixed()),
            )
        }
    }

    private suspend fun getAltIcon(packageName: String): Icon {
        return withContext(backgroundDispatcher) {
            try {
                val icon = applicationContext.packageManager.getApplicationIcon(packageName)
                Icon.Loaded(icon, null)
            } catch (exception: PackageManager.NameNotFoundException) {
                Icon.Resource(R.drawable.ic_music_note, null)
            }
        }
    }

    /**
     * This method should be called from a background thread. WallpaperColors.fromBitmap is a
     * blocking call.
     */
    private suspend fun getWallpaperColor(
        applicationContext: Context,
        backgroundDispatcher: CoroutineDispatcher,
        artworkIcon: android.graphics.drawable.Icon?,
    ): WallpaperColors? {
        return withContext(backgroundDispatcher) {
            artworkIcon?.let {
                if (
                    it.type == android.graphics.drawable.Icon.TYPE_BITMAP ||
                        it.type == android.graphics.drawable.Icon.TYPE_ADAPTIVE_BITMAP
                ) {
                    // Avoids extra processing if this is already a valid bitmap
                    it.bitmap.let { artworkBitmap ->
                        if (artworkBitmap.isRecycled) {
                            Log.d(TAG, "Cannot load wallpaper color from a recycled bitmap")
                            null
                        } else {
                            WallpaperColors.fromBitmap(artworkBitmap)
                        }
                    }
                } else {
                    it.loadDrawable(applicationContext)?.let { artworkDrawable ->
                        WallpaperColors.fromDrawable(artworkDrawable)
                    }
                }
            }
        }
    }

    /** Returns [ColorScheme] of media app given its [icon]. */
    private fun getColorScheme(icon: Drawable): ColorScheme? {
        return try {
            ColorScheme(WallpaperColors.fromDrawable(icon), false, Style.CONTENT)
        } catch (e: PackageManager.NameNotFoundException) {
            Log.w(TAG, "Fail to get media app info", e)
            null
        }
    }

    companion object {
        private const val TAG = "MediaRepository"
    }
}
