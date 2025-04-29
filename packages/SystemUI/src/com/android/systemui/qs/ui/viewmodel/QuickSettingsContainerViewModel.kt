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

package com.android.systemui.qs.ui.viewmodel

import android.content.Context
import android.media.AudioManager
import androidx.compose.runtime.getValue
import com.android.settingslib.volume.shared.model.AudioStream
import com.android.systemui.Flags
import com.android.systemui.brightness.ui.viewmodel.BrightnessSliderViewModel
import com.android.systemui.lifecycle.ExclusiveActivatable
import com.android.systemui.lifecycle.Hydrator
import com.android.systemui.media.controls.domain.pipeline.interactor.MediaCarouselInteractor
import com.android.systemui.media.controls.ui.controller.MediaCarouselController
import com.android.systemui.media.controls.ui.view.MediaHost
import com.android.systemui.media.dagger.MediaModule
import com.android.systemui.qs.flags.QsDetailedView
import com.android.systemui.qs.panels.ui.viewmodel.DetailsViewModel
import com.android.systemui.qs.panels.ui.viewmodel.EditModeViewModel
import com.android.systemui.qs.panels.ui.viewmodel.TileGridViewModel
import com.android.systemui.qs.panels.ui.viewmodel.toolbar.ToolbarViewModel
import com.android.systemui.res.R
import com.android.systemui.shade.ShadeDisplayAware
import com.android.systemui.shade.ui.viewmodel.ShadeHeaderViewModel
import com.android.systemui.volume.panel.component.volume.domain.model.SliderType
import com.android.systemui.volume.panel.component.volume.slider.ui.viewmodel.AudioStreamSliderViewModel
import com.android.systemui.window.domain.interactor.WindowRootViewBlurInteractor
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import javax.inject.Named
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

class QuickSettingsContainerViewModel
@AssistedInject
constructor(
    @ShadeDisplayAware shadeContext: Context,
    brightnessSliderViewModelFactory: BrightnessSliderViewModel.Factory,
    private val audioStreamSliderViewModelFactory: AudioStreamSliderViewModel.Factory,
    shadeHeaderViewModelFactory: ShadeHeaderViewModel.Factory,
    tileGridViewModelFactory: TileGridViewModel.Factory,
    @Assisted private val supportsBrightnessMirroring: Boolean,
    @Assisted private val expansion: Float?,
    val editModeViewModel: EditModeViewModel,
    val detailsViewModel: DetailsViewModel,
    toolbarViewModelFactory: ToolbarViewModel.Factory,
    windowRootViewBlurInteractor: WindowRootViewBlurInteractor,
    mediaCarouselInteractor: MediaCarouselInteractor,
    val mediaCarouselController: MediaCarouselController,
    @Named(MediaModule.QS_PANEL) val mediaHost: MediaHost,
) : ExclusiveActivatable() {

    private val hydrator = Hydrator("QuickSettingsContainerViewModel.hydrator")

    /**
     * Whether the shade container transparency effect should be enabled (`true`), or whether to
     * render a fully-opaque shade container (`false`).
     */
    val isTransparencyEnabled: Boolean by
        hydrator.hydratedStateOf(
            traceName = "transparencyEnabled",
            initialValue =
                Flags.notificationShadeBlur() &&
                    windowRootViewBlurInteractor.isBlurCurrentlySupported.value,
            source =
                if (Flags.notificationShadeBlur()) {
                    windowRootViewBlurInteractor.isBlurCurrentlySupported
                } else {
                    flowOf(false)
                },
        )

    val brightnessSliderViewModel =
        brightnessSliderViewModelFactory.create(supportsBrightnessMirroring)

    private val showVolumeSlider =
        QsDetailedView.isEnabled &&
            shadeContext.resources.getBoolean(R.bool.config_enableDesktopAudioTileDetailsView)

    var volumeSliderViewModel: AudioStreamSliderViewModel? = null

    val toolbarViewModel = toolbarViewModelFactory.create()

    val shadeHeaderViewModel = shadeHeaderViewModelFactory.create()

    val tileGridViewModel = tileGridViewModelFactory.create()

    val showMedia: Boolean by
        hydrator.hydratedStateOf(
            traceName = "showMedia",
            source = mediaCarouselInteractor.hasAnyMedia,
        )

    override suspend fun onActivated(): Nothing {
        coroutineScope {
            if (showVolumeSlider) {
                val volumeSliderStream =
                    SliderType.Stream(AudioStream(AudioManager.STREAM_MUSIC)).stream
                launch {
                    volumeSliderViewModel =
                        audioStreamSliderViewModelFactory.create(
                            AudioStreamSliderViewModel.FactoryAudioStreamWrapper(
                                volumeSliderStream
                            ),
                            this,
                        )
                }
            }
            expansion?.let { mediaHost.expansion = it }
            launch { hydrator.activate() }
            launch { brightnessSliderViewModel.activate() }
            launch { toolbarViewModel.activate() }
            launch { shadeHeaderViewModel.activate() }
            launch { tileGridViewModel.activate() }
            awaitCancellation()
        }
    }

    @AssistedFactory
    interface Factory {
        fun create(
            supportsBrightnessMirroring: Boolean,
            expansion: Float? = null,
        ): QuickSettingsContainerViewModel
    }
}
