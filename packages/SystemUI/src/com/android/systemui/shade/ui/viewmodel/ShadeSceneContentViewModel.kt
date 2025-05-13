/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.systemui.shade.ui.viewmodel

import androidx.compose.runtime.getValue
import androidx.lifecycle.LifecycleOwner
import com.android.app.tracing.coroutines.launchTraced as launch
import com.android.systemui.deviceentry.domain.interactor.DeviceEntryInteractor
import com.android.systemui.lifecycle.ExclusiveActivatable
import com.android.systemui.lifecycle.Hydrator
import com.android.systemui.media.controls.domain.pipeline.interactor.MediaCarouselInteractor
import com.android.systemui.qs.FooterActionsController
import com.android.systemui.qs.footer.ui.viewmodel.FooterActionsViewModel
import com.android.systemui.qs.ui.adapter.QSSceneAdapter
import com.android.systemui.scene.domain.interactor.SceneInteractor
import com.android.systemui.scene.shared.model.Scenes
import com.android.systemui.settings.brightness.ui.viewModel.BrightnessMirrorViewModel
import com.android.systemui.shade.domain.interactor.ShadeModeInteractor
import com.android.systemui.shade.shared.model.ShadeMode
import com.android.systemui.statusbar.disableflags.domain.interactor.DisableFlagsInteractor
import com.android.systemui.unfold.domain.interactor.UnfoldTransitionInteractor
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Models UI state used to render the content of the shade scene.
 *
 * Different from [ShadeUserActionsViewModel], which only models user actions that can be performed
 * to navigate to other scenes.
 */
class ShadeSceneContentViewModel
@AssistedInject
constructor(
    val qsSceneAdapter: QSSceneAdapter,
    val shadeHeaderViewModelFactory: ShadeHeaderViewModel.Factory,
    val brightnessMirrorViewModelFactory: BrightnessMirrorViewModel.Factory,
    val mediaCarouselInteractor: MediaCarouselInteractor,
    shadeModeInteractor: ShadeModeInteractor,
    disableFlagsInteractor: DisableFlagsInteractor,
    private val footerActionsViewModelFactory: FooterActionsViewModel.Factory,
    private val footerActionsController: FooterActionsController,
    private val unfoldTransitionInteractor: UnfoldTransitionInteractor,
    deviceEntryInteractor: DeviceEntryInteractor,
    private val sceneInteractor: SceneInteractor,
) : ExclusiveActivatable() {

    private val hydrator = Hydrator("ShadeSceneContentViewModel.hydrator")

    val shadeMode: ShadeMode by
        hydrator.hydratedStateOf(traceName = "shadeMode", source = shadeModeInteractor.shadeMode)

    /** Whether clicking on the empty area of the shade should do something. */
    val isEmptySpaceClickable: Boolean by
        hydrator.hydratedStateOf(
            traceName = "isEmptySpaceClickable",
            initialValue = !deviceEntryInteractor.isDeviceEntered.value,
            source = deviceEntryInteractor.isDeviceEntered.map { !it },
        )

    val isMediaVisible: Boolean by
        hydrator.hydratedStateOf(
            traceName = "isMediaVisible",
            source = mediaCarouselInteractor.hasActiveMedia,
        )

    val isQsEnabled: Boolean by
        hydrator.hydratedStateOf(
            traceName = "isQsEnabled",
            initialValue = disableFlagsInteractor.disableFlags.value.isQuickSettingsEnabled(),
            source = disableFlagsInteractor.disableFlags.map { it.isQuickSettingsEnabled() },
        )

    private val footerActionsControllerInitialized = AtomicBoolean(false)

    override suspend fun onActivated(): Nothing {
        coroutineScope {
            launch { hydrator.activate() }

            awaitCancellation()
        }
    }

    /**
     * Amount of X-axis translation to apply to various elements as the unfolded foldable is folded
     * slightly, in pixels.
     */
    fun unfoldTranslationX(isOnStartSide: Boolean): Flow<Float> {
        return unfoldTransitionInteractor.unfoldTranslationX(isOnStartSide)
    }

    fun getFooterActionsViewModel(lifecycleOwner: LifecycleOwner): FooterActionsViewModel {
        if (footerActionsControllerInitialized.compareAndSet(false, true)) {
            footerActionsController.init()
        }
        return footerActionsViewModelFactory.create(lifecycleOwner)
    }

    /** Notifies that the empty space in the shade has been clicked. */
    fun onEmptySpaceClicked() {
        if (!isEmptySpaceClickable) {
            return
        }

        sceneInteractor.changeScene(Scenes.Lockscreen, "Shade empty space clicked.")
    }

    @AssistedFactory
    interface Factory {
        fun create(): ShadeSceneContentViewModel
    }
}
