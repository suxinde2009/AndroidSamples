/*
 * Copyright 2021 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.example.android.wearable.oauth.devicegrant

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.Text
import com.google.android.horologist.annotations.ExperimentalHorologistApi
import com.google.android.horologist.compose.layout.AppScaffold
import com.google.android.horologist.compose.layout.ScalingLazyColumn
import com.google.android.horologist.compose.layout.ScalingLazyColumnDefaults
import com.google.android.horologist.compose.layout.ScalingLazyColumnDefaults.ItemType
import com.google.android.horologist.compose.layout.ScreenScaffold
import com.google.android.horologist.compose.layout.rememberResponsiveColumnState

/**
 * Demonstrates the OAuth 2.0 flow on Wear OS using Device Authorization Grant, as described in
 * [RFC 8628](https://datatracker.ietf.org/doc/html/rfc8628).
 *
 * This sample currently directly polls the Google OAuth 2.0 API, which means the client id and
 * client secret are included in the app. In practice you would use an intermediary server that
 * holds these values for you.
 *
 * The sample uses the Google OAuth 2.0 APIs, but can be easily extended for any other
 * OAuth 2.0 provider.
 *
 * See [AuthDeviceGrantViewModel] for the implementation of the authorization flow.
 */
class AuthDeviceGrantActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { AuthenticateApp(deviceGrantViewModel = viewModel()) }
    }
}

@OptIn(ExperimentalHorologistApi::class)
@Composable
fun AuthenticateApp(deviceGrantViewModel: AuthDeviceGrantViewModel) {
    AppScaffold {
        val uiState = deviceGrantViewModel.uiState.collectAsState()
        val localContext = LocalContext.current
        val columnState = rememberResponsiveColumnState(
            contentPadding = ScalingLazyColumnDefaults.padding(
                first = ItemType.Unspecified,
                last = ItemType.Unspecified
            )
        )
        ScreenScaffold(scrollState = columnState) {
            ScalingLazyColumn(columnState = columnState) {
                item {
                    ListHeader {
                        Text(
                            stringResource(R.string.oauth_device_auth_grant),
                            textAlign = TextAlign.Center
                        )
                    }
                }
                item {
                    Button(
                        onClick = { deviceGrantViewModel.startAuthFlow(localContext) },
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Text(
                            text = stringResource(R.string.get_grant_from_phone),
                            modifier = Modifier.align(Alignment.CenterVertically)
                        )
                    }
                }
                item { Text(uiState.value.statusCode.toString()) }
                item { Text(uiState.value.resultMessage) }
            }
        }
    }
}
