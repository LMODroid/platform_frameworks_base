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

package com.android.systemui.media.remedia.domain.interactor

import android.content.Context
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.android.systemui.biometrics.Utils.toBitmap
import com.android.systemui.common.shared.model.Icon
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Application
import com.android.systemui.lifecycle.ExclusiveActivatable
import com.android.systemui.media.remedia.data.model.MediaDataModel
import com.android.systemui.media.remedia.data.repository.MediaRepository
import com.android.systemui.media.remedia.domain.model.MediaActionModel
import com.android.systemui.media.remedia.domain.model.MediaOutputDeviceModel
import com.android.systemui.media.remedia.domain.model.MediaSessionModel
import com.android.systemui.media.remedia.shared.model.MediaCardActionButtonLayout
import com.android.systemui.media.remedia.shared.model.MediaColorScheme
import com.android.systemui.media.remedia.shared.model.MediaSessionState
import javax.inject.Inject

/**
 * Defines interface for classes that can provide business logic in the domain of the media controls
 * element.
 */
interface MediaInteractor {

    /** The list of sessions. Needs to be backed by a compose snapshot state. */
    val sessions: List<MediaSessionModel>

    /** Seek to [to], in milliseconds on the media session with the given [sessionKey]. */
    fun seek(sessionKey: Any, to: Long)

    /** Hide the representation of the media session with the given [sessionKey]. */
    fun hide(sessionKey: Any)

    /** Open media settings. */
    fun openMediaSettings()
}

@SysUISingleton
class MediaInteractorImpl
@Inject
constructor(@Application val applicationContext: Context, val repository: MediaRepository) :
    MediaInteractor, ExclusiveActivatable() {

    override val sessions: List<MediaSessionModel>
        get() = repository.currentMedia.map { toMediaSessionModel(it) }

    override fun seek(sessionKey: Any, to: Long) {
        TODO("Not yet implemented")
    }

    override fun hide(sessionKey: Any) {
        TODO("Not yet implemented")
    }

    override fun openMediaSettings() {
        TODO("Not yet implemented")
    }

    private fun toMediaSessionModel(dataModel: MediaDataModel): MediaSessionModel {
        return object : MediaSessionModel {
            override val key
                get() = dataModel.instanceId

            override val appName
                get() = dataModel.appName

            override val appIcon: Icon
                get() = dataModel.appIcon

            override val background: ImageBitmap?
                get() =
                    dataModel.background?.let {
                        (it as Icon.Loaded).drawable.toBitmap()?.asImageBitmap()
                    }

            override val colorScheme: MediaColorScheme
                get() = TODO("Not yet implemented")

            override val title: String
                get() = dataModel.title

            override val subtitle: String
                get() = dataModel.subtitle

            override val onClick: () -> Unit
                get() = TODO("Not yet implemented")

            override val isActive: Boolean
                get() = dataModel.isActive

            override val canBeHidden: Boolean
                get() = dataModel.canBeDismissed

            override val canBeScrubbed: Boolean
                get() = TODO("Not yet implemented")

            override val state: MediaSessionState
                get() = TODO("Not yet implemented")

            override val positionMs: Long
                get() = TODO("Not yet implemented")

            override val durationMs: Long
                get() = TODO("Not yet implemented")

            override val outputDevice: MediaOutputDeviceModel
                get() = TODO("Not yet implemented")

            override val actionButtonLayout: MediaCardActionButtonLayout
                get() = TODO("Not yet implemented")

            override val playPauseAction: MediaActionModel
                get() = TODO("Not yet implemented")

            override val leftAction: MediaActionModel
                get() = TODO("Not yet implemented")

            override val rightAction: MediaActionModel
                get() = TODO("Not yet implemented")

            override val additionalActions: List<MediaActionModel.Action>
                get() = TODO("Not yet implemented")
        }
    }

    override suspend fun onActivated(): Nothing {
        repository.activate()
    }
}
