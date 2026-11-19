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

package com.android.systemui.keyguard.ui.viewmodel

import android.util.MathUtils
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.keyguard.domain.interactor.FromGoneTransitionInteractor.Companion.TO_LOCKSCREEN_DURATION
import com.android.systemui.keyguard.shared.model.Edge
import com.android.systemui.keyguard.shared.model.KeyguardState.GONE
import com.android.systemui.keyguard.shared.model.KeyguardState.LOCKSCREEN
import com.android.systemui.keyguard.ui.KeyguardTransitionAnimationFlow
import com.android.systemui.keyguard.ui.transitions.DeviceEntryIconTransition
import com.android.systemui.scene.shared.model.Scenes
import com.android.systemui.shade.data.repository.ShadeRepository
import javax.inject.Inject
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged

@SysUISingleton
class GoneToLockscreenTransitionViewModel
@Inject
constructor(
    animationFlow: KeyguardTransitionAnimationFlow,
    private val shadeRepository: ShadeRepository,
) : DeviceEntryIconTransition {

    private val transitionAnimation =
        animationFlow
            .setup(
                duration = TO_LOCKSCREEN_DURATION,
                edge = Edge.create(from = Scenes.Gone, to = LOCKSCREEN),
            )
            .setupWithoutSceneContainer(edge = Edge.create(from = GONE, to = LOCKSCREEN))

    val shortcutsAlpha: Flow<Float> =
        transitionAnimation.sharedFlow(duration = 250.milliseconds, onStep = { it })

    val lockscreenAlpha: Flow<Float> = run {
        var useExpansionAlpha = false
        transitionAnimation
            .sharedFlow(
                duration = TO_LOCKSCREEN_DURATION,
                onStart = {
                    val expansion = shadeRepository.legacyShadeExpansion.value
                    useExpansionAlpha =
                        shadeRepository.legacyShadeTracking.value ||
                            expansion > 0f && expansion < 1f
                },
                onStep = { transitionProgress ->
                    if (useExpansionAlpha) {
                        dismissAlpha()
                    } else {
                        MathUtils.constrainedMap(0f, 1f, 0f, 0.5f, transitionProgress)
                    }
                },
                onCancel = { if (useExpansionAlpha) dismissAlpha() else 1f },
            )
            .distinctUntilChanged()
    }

    private fun dismissAlpha(): Float =
        MathUtils.constrainedMap(
            0f,
            1f,
            0.85f,
            1f,
            shadeRepository.legacyShadeExpansion.value,
        )

    val deviceEntryBackgroundViewAlpha: Flow<Float> =
        transitionAnimation.immediatelyTransitionTo(1f)

    override val deviceEntryParentViewAlpha: Flow<Float> =
        transitionAnimation.immediatelyTransitionTo(1f)
}
