/*
 * PropertyConfig.java
 * Copyright 2020 Rob Spoor
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.github.robtimus.obfuscation.yaml;

import java.util.Objects;
import com.github.robtimus.obfuscation.Obfuscator;
import com.github.robtimus.obfuscation.yaml.YAMLObfuscator.PropertyConfigurer.ObfuscationMode;

record PropertyConfig(
        Obfuscator obfuscator,
        ObfuscationMode forMappings,
        ObfuscationMode forSequences,
        boolean performObfuscation
) {

    PropertyConfig {
        Objects.requireNonNull(obfuscator);
        Objects.requireNonNull(forMappings);
        Objects.requireNonNull(forSequences);
    }

    PropertyConfig(Obfuscator obfuscator, ObfuscationMode forMappings, ObfuscationMode forSequences) {
        this(obfuscator, forMappings, forSequences, !obfuscator.equals(Obfuscator.none()));
    }
}
