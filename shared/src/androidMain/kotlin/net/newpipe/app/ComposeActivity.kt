/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app

import android.graphics.Color as AndroidColor
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.graphics.luminance
import kotlinx.serialization.json.Json
import net.newpipe.Constants
import net.newpipe.app.navigation.Destination
import net.newpipe.app.theme.currentColorScheme

/**
 * Entry point for compose-related UI components on Android
 */
class ComposeActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            App(
                // TODO: Change when everything is in compose and this is the primary activity
                startDestination = Json.decodeFromString<Destination>(
                    intent.getStringExtra(Constants.INTENT_SCREEN_KEY)!!
                ),
                onCloseRequest = ::finish
            ) {
                // barre di sistema come il tema scelto nell'app (con il tema Nero restano scure),
                // non come quello del telefono
                val dark = currentColorScheme().background.luminance() < 0.5f
                DisposableEffect(dark) {
                    val style = if (dark) {
                        SystemBarStyle.dark(AndroidColor.TRANSPARENT)
                    } else {
                        SystemBarStyle.light(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT)
                    }
                    enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                    onDispose { }
                }
            }
        }
    }
}
