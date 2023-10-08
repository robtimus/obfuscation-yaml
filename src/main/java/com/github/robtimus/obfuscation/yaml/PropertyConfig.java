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

final class PropertyConfig {

    final Obfuscator obfuscator;
    final ObfuscationMode forMappings;
    final ObfuscationMode forSequences;
    final boolean performObfuscation;

    PropertyConfig(Obfuscator obfuscator, ObfuscationMode forMappings, ObfuscationMode forSequences) {
        this.obfuscator = Objects.requireNonNull(obfuscator);
        this.forMappings = Objects.requireNonNull(forMappings);
        this.forSequences = Objects.requireNonNull(forSequences);
        this.performObfuscation = !obfuscator.equals(Obfuscator.none());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || o.getClass() != getClass()) {
            return false;
        }
        PropertyConfig other = (PropertyConfig) o;
        return obfuscator.equals(other.obfuscator)
                && forMappings == other.forMappings
                && forSequences == other.forSequences;
    }

    @Override
    public int hashCode() {
        return obfuscator.hashCode() ^ forMappings.hashCode() ^ forSequences.hashCode();
    }

    @Override
    @SuppressWarnings("nls")
    public String toString() {
        return "[obfuscator=" + obfuscator
                + ",forMappings=" + forMappings
                + ",forSequences=" + forSequences
                + "]";
    }
}
