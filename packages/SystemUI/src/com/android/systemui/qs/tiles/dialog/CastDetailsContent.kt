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

package com.android.systemui.qs.tiles.dialog

import android.view.LayoutInflater
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.viewinterop.AndroidView
import com.android.compose.ui.graphics.painter.rememberDrawablePainter
import com.android.internal.R
import com.android.internal.app.MediaRouteControllerContentManager

@Composable
fun CastDetailsContent(castDetailsViewModel: CastDetailsViewModel) {
    if (castDetailsViewModel.shouldShowChooserDialog()) {
        // TODO(b/378514236): Show the chooser UI here.
        return
    }

    val contentManager: MediaRouteControllerContentManager = remember {
        castDetailsViewModel.createControllerContentManager()
    }

    Column(
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Image(
            painter = rememberDrawablePainter(castDetailsViewModel.deviceIcon),
            // TODO(b/388321032): Replace this string with a string in a translatable xml file.
            contentDescription = "device icon",
        )
        CastControllerView(contentManager)
        CastControllerDisconnectButton(contentManager)
    }
}

@Composable
fun CastControllerView(contentManager: MediaRouteControllerContentManager) {
    AndroidView(
        modifier = Modifier.fillMaxWidth().testTag("CastControllerView"),
        factory = { context ->
            // Inflate with the existing dialog xml layout
            val view =
                LayoutInflater.from(context).inflate(R.layout.media_route_controller_dialog, null)
            contentManager.bindViews(view)
            contentManager.onAttachedToWindow()

            view
        },
        onRelease = { contentManager.onDetachedFromWindow() },
    )
}

@Composable
fun CastControllerDisconnectButton(contentManager: MediaRouteControllerContentManager) {
    Button(
        onClick = { contentManager.onDisconnectButtonClick() },
        modifier = Modifier.fillMaxWidth(),
    ) {
        // TODO(b/388321032): Replace this string with a string in a translatable xml file.
        Text(text = "Disconnect")
    }
}
