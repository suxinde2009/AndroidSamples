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


plugins {
	id("declarative-plugin-module-conventions")
	alias(libs.plugins.kotlin.jvm)
	//alias(libs.plugins.plugin.publish)
}

gradlePlugin {
	plugins {
		create("comAndroidDeclarativeSettings") {
			id = "com.android.experiments.declarative.settings"
			version = Constants.PLUGIN_VERSION
			implementationClass = "com.android.declarative.settings.api.SettingsDeclarativePlugin"
		}
	}
}

dependencies {
	implementation(project(":settings-plugin"))
}
