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

package com.android.systemui.scene.domain.model

/** Enumerates the type of education still needed. */
enum class DualShadeEducationModel {
    /** Education isn't needed for either shade overlay. */
    None,
    /**
     * A hint is needed for the notifications shade overlay.
     *
     * A "hint" is something less intrusive than a tooltip; for example, a bounce animation.
     */
    HintForNotificationsShade,
    /**
     * A hint is needed for the quick settings shade overlay.
     *
     * A "hint" is something less intrusive than a tooltip; for example, a bounce animation.
     */
    HintForQuickSettingsShade,
    /** A tooltip is needed for the notifications shade overlay. */
    TooltipForNotificationsShade,
    /** A tooltip is needed for the quick settings shade overlay. */
    TooltipForQuickSettingsShade,
}
