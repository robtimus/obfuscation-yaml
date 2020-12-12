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

final class PropertyConfig {

    final Obfuscator obfuscator;
    final boolean obfuscateMappings;
    final boolean obfuscateSequences;

    PropertyConfig(Obfuscator obfuscator, boolean obfuscateMappings, boolean obfuscateSequences) {
        this.obfuscator = Objects.requireNonNull(obfuscator);
        this.obfuscateMappings = obfuscateMappings;
        this.obfuscateSequences = obfuscateSequences;
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
                && obfuscateMappings == other.obfuscateMappings
                && obfuscateSequences == other.obfuscateSequences;
    }

    @Override
    public int hashCode() {
        return obfuscator.hashCode() ^ Boolean.hashCode(obfuscateMappings) ^ Boolean.hashCode(obfuscateSequences);
    }

    @Override
    @SuppressWarnings("nls")
    public String toString() {
        return "[obfuscator=" + obfuscator
                + ",obfuscateMappings=" + obfuscateMappings
                + ",obfuscateSequences=" + obfuscateSequences
                + "]";
    }
}
