/**
 * Copyright (c) 2015 the original author or authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.jmnarloch.cd.go.plugin.gradle.task;

import io.jmnarloch.cd.go.plugin.gradle.api.config.PropertyName;
import io.jmnarloch.cd.go.plugin.gradle.api.config.PropertyValue;

/**
 *
 */
public enum GradleTaskConfig {

    USE_WRAPPER("UseWrapper", "true"),

    GRADLE_HOME("GradleHome"),

    TASKS("Tasks"),

    DAEMON("Deamon"),

    OFFLINE("Offline"),

    DEBUG("Debug"),

    ADDITIONAL_OPTIONS("AdditionalOptions");

    @PropertyName
    private String name;

    @PropertyValue
    private String defaultValue;

    GradleTaskConfig(String name) {
        this(name, null);
    }

    GradleTaskConfig(String name, String defaultValue) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public String getDefaultValue() {
        return defaultValue;
    }
}
