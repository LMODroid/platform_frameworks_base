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

package com.android.systemui.qs.panels.ui.compose.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

public val DualTargetArrow: ImageVector
    get() {
        if (_DualTargetArrow != null) {
            return _DualTargetArrow!!
        }
        _DualTargetArrow =
            ImageVector.Builder(
                    name = "DualTargetArrow",
                    defaultWidth = 24.dp,
                    defaultHeight = 24.dp,
                    viewportWidth = 960f,
                    viewportHeight = 960f,
                )
                .apply {
                    path(
                        fill = SolidColor(Color(0xFFFFFFFF)),
                        fillAlpha = 1.0f,
                        stroke = null,
                        strokeAlpha = 1.0f,
                        strokeLineWidth = 1.0f,
                        strokeLineCap = StrokeCap.Butt,
                        strokeLineJoin = StrokeJoin.Miter,
                        strokeLineMiter = 1.0f,
                        pathFillType = PathFillType.NonZero,
                    ) {
                        moveTo(480f, 599f)
                        quadTo(472f, 599f, 465f, 596.5f)
                        quadTo(458f, 594f, 452f, 588f)
                        lineTo(268f, 404f)
                        quadTo(257f, 393f, 257f, 376f)
                        quadTo(257f, 359f, 268f, 348f)
                        quadTo(279f, 337f, 296f, 337f)
                        quadTo(313f, 337f, 324f, 348f)
                        lineTo(480f, 504f)
                        lineTo(636f, 348f)
                        quadTo(647f, 337f, 664f, 337f)
                        quadTo(681f, 337f, 692f, 348f)
                        quadTo(703f, 359f, 703f, 376f)
                        quadTo(703f, 393f, 692f, 404f)
                        lineTo(508f, 588f)
                        quadTo(502f, 594f, 495f, 596.5f)
                        quadTo(488f, 599f, 480f, 599f)
                        close()
                    }
                }
                .build()
        return _DualTargetArrow!!
    }

private var _DualTargetArrow: ImageVector? = null
