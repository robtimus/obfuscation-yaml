/*
 * YAMLObfuscator.java
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

import static com.github.robtimus.obfuscation.support.ObfuscatorUtils.checkStartAndEnd;
import static com.github.robtimus.obfuscation.support.ObfuscatorUtils.copyTo;
import static com.github.robtimus.obfuscation.support.ObfuscatorUtils.discardAll;
import static com.github.robtimus.obfuscation.support.ObfuscatorUtils.reader;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snakeyaml.engine.v2.api.LoadSettings;
import org.snakeyaml.engine.v2.exceptions.YamlEngineException;
import org.snakeyaml.engine.v2.parser.ParserImpl;
import org.snakeyaml.engine.v2.scanner.StreamReader;
import com.github.robtimus.obfuscation.Obfuscator;
import com.github.robtimus.obfuscation.support.CachingObfuscatingWriter;
import com.github.robtimus.obfuscation.support.CaseSensitivity;
import com.github.robtimus.obfuscation.support.MapBuilder;

/**
 * An obfuscator that obfuscates YAML properties in {@link CharSequence CharSequences} or the contents of {@link Reader Readers}.
 *
 * @author Rob Spoor
 */
public final class YAMLObfuscator extends Obfuscator {

    private static final Logger LOGGER = LoggerFactory.getLogger(YAMLObfuscator.class);

    private final Map<String, PropertyConfig> properties;

    private final LoadSettings settings;

    private final String malformedYAMLWarning;

    private YAMLObfuscator(ObfuscatorBuilder builder) {
        properties = builder.properties();

        settings = LoadSettings.builder()
                // be as lenient as possible
                .setAllowDuplicateKeys(true)
                .setAllowRecursiveKeys(true)
                // use marks, as they are needed for obfuscating text
                .setUseMarks(true)
                .build();

        malformedYAMLWarning = builder.malformedYAMLWarning;
    }

    @Override
    public CharSequence obfuscateText(CharSequence s, int start, int end) {
        checkStartAndEnd(s, start, end);
        StringBuilder sb = new StringBuilder(end - start);
        obfuscateText(s, start, end, sb);
        return sb.toString();
    }

    @Override
    public void obfuscateText(CharSequence s, int start, int end, Appendable destination) throws IOException {
        checkStartAndEnd(s, start, end);
        @SuppressWarnings("resource")
        Reader input = reader(s, start, end);
        obfuscateText(input, s, start, end, destination);
    }

    @Override
    public void obfuscateText(Reader input, Appendable destination) throws IOException {
        StringBuilder contents = new StringBuilder();
        @SuppressWarnings("resource")
        Reader reader = copyTo(input, contents);
        obfuscateText(reader, contents, 0, -1, destination);
    }

    private void obfuscateText(Reader input, CharSequence s, int start, int end, Appendable destination) throws IOException {
        ObfuscatingParser parser = new ObfuscatingParser(new ParserImpl(new StreamReader(input, settings), settings),
                s, start, end, destination, properties);

        try {
            while (parser.hasNext()) {
                parser.next();
            }
            // read the remainder so the final append will include all text
            discardAll(input);
            parser.appendRemainder();
        } catch (YamlEngineException e) {
            LOGGER.warn(Messages.YAMLObfuscator.malformedYAML.warning.get(), e);
            if (malformedYAMLWarning != null) {
                destination.append(malformedYAMLWarning);
            }
        }
    }

    @Override
    public Writer streamTo(Appendable destination) {
        return new CachingObfuscatingWriter(this, destination);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || o.getClass() != getClass()) {
            return false;
        }
        YAMLObfuscator other = (YAMLObfuscator) o;
        return properties.equals(other.properties)
                && Objects.equals(malformedYAMLWarning, other.malformedYAMLWarning);
    }

    @Override
    public int hashCode() {
        return properties.hashCode() ^ Objects.hashCode(malformedYAMLWarning);
    }

    @Override
    @SuppressWarnings("nls")
    public String toString() {
        return getClass().getName()
                + "[properties=" + properties
                + ",malformedYAMLWarning=" + malformedYAMLWarning
                + "]";
    }

    /**
     * Returns a builder that will create {@code YAMLObfuscators}.
     *
     * @return A builder that will create {@code YAMLObfuscators}.
     */
    public static Builder builder() {
        return new ObfuscatorBuilder();
    }

    /**
     * A builder for {@link YAMLObfuscator YAMLObfuscators}.
     *
     * @author Rob Spoor
     */
    public abstract static class Builder {

        private Builder() {
            super();
        }

        /**
         * Adds a property to obfuscate.
         * This method is an alias for {@link #withProperty(String, Obfuscator, CaseSensitivity)} with the last specified default case sensitivity
         * using {@link #caseSensitiveByDefault()} or {@link #caseInsensitiveByDefault()}. The default is {@link CaseSensitivity#CASE_SENSITIVE}.
         *
         * @param property The name of the property.
         * @param obfuscator The obfuscator to use for obfuscating the property.
         * @return An object that can be used to configure the property, or continue building {@link YAMLObfuscator YAMLObfuscators}.
         * @throws NullPointerException If the given property name or obfuscator is {@code null}.
         * @throws IllegalArgumentException If a property with the same name and the same case sensitivity was already added.
         */
        public abstract PropertyConfigurer withProperty(String property, Obfuscator obfuscator);

        /**
         * Adds a property to obfuscate.
         *
         * @param property The name of the property.
         * @param obfuscator The obfuscator to use for obfuscating the property.
         * @param caseSensitivity The case sensitivity for the property.
         * @return An object that can be used to configure the property, or continue building {@link YAMLObfuscator YAMLObfuscators}.
         * @throws NullPointerException If the given property name, obfuscator or case sensitivity is {@code null}.
         * @throws IllegalArgumentException If a property with the same name and the same case sensitivity was already added.
         */
        public abstract PropertyConfigurer withProperty(String property, Obfuscator obfuscator, CaseSensitivity caseSensitivity);

        /**
         * Sets the default case sensitivity for new properties to {@link CaseSensitivity#CASE_SENSITIVE}. This is the default setting.
         * <p>
         * Note that this will not change the case sensitivity of any property that was already added.
         *
         * @return This object.
         */
        public abstract Builder caseSensitiveByDefault();

        /**
         * Sets the default case sensitivity for new properties to {@link CaseSensitivity#CASE_INSENSITIVE}.
         * <p>
         * Note that this will not change the case sensitivity of any property that was already added.
         *
         * @return This object.
         */
        public abstract Builder caseInsensitiveByDefault();

        /**
         * Indicates that by default properties will not be obfuscated if they are YAML mappings or sequences.
         * This method is shorthand for calling both {@link #excludeMappingsByDefault()} and {@link #excludeSequencesByDefault()}.
         * <p>
         * Note that this will not change what will be obfuscated for any property that was already added.
         *
         * @return This object.
         */
        public Builder scalarsOnlyByDefault() {
            return excludeMappingsByDefault()
                    .excludeSequencesByDefault();
        }

        /**
         * Indicates that by default properties will not be obfuscated if they are YAML mappings.
         * This can be overridden per property using {@link PropertyConfigurer#excludeMappings()}
         * <p>
         * Note that this will not change what will be obfuscated for any property that was already added.
         *
         * @return This object.
         */
        public abstract Builder excludeMappingsByDefault();

        /**
         * Indicates that by default properties will not be obfuscated if they are YAML sequences.
         * This can be overridden per property using {@link PropertyConfigurer#excludeSequences()}
         * <p>
         * Note that this will not change what will be obfuscated for any property that was already added.
         *
         * @return This object.
         */
        public abstract Builder excludeSequencesByDefault();

        /**
         * Indicates that by default properties will be obfuscated if they are YAML mappings or sequences (default).
         * This method is shorthand for calling both {@link #includeMappingsByDefault()} and {@link #includeSequencesByDefault()}.
         * <p>
         * Note that this will not change what will be obfuscated for any property that was already added.
         *
         * @return This object.
         */
        public Builder allByDefault() {
            return includeMappingsByDefault()
                    .includeSequencesByDefault();
        }

        /**
         * Indicates that by default properties will be obfuscated if they are YAML mappings (default).
         * This can be overridden per property using {@link PropertyConfigurer#excludeMappings()}
         * <p>
         * Note that this will not change what will be obfuscated for any property that was already added.
         *
         * @return This object.
         */
        public abstract Builder includeMappingsByDefault();

        /**
         * Indicates that by default properties will be obfuscated if they are YAML sequences (default).
         * This can be overridden per property using {@link PropertyConfigurer#excludeSequences()}
         * <p>
         * Note that this will not change what will be obfuscated for any property that was already added.
         *
         * @return This object.
         */
        public abstract Builder includeSequencesByDefault();

        /**
         * Sets the warning to include if a {@link YamlEngineException} is thrown.
         * This can be used to override the default message. Use {@code null} to omit the warning.
         *
         * @param warning The warning to include.
         * @return This object.
         */
        public abstract Builder withMalformedYAMLWarning(String warning);

        /**
         * This method allows the application of a function to this builder.
         * <p>
         * Any exception thrown by the function will be propagated to the caller.
         *
         * @param <R> The type of the result of the function.
         * @param f The function to apply.
         * @return The result of applying the function to this builder.
         */
        public <R> R transform(Function<? super Builder, ? extends R> f) {
            return f.apply(this);
        }

        /**
         * Creates a new {@code YAMLObfuscator} with the properties and obfuscators added to this builder.
         *
         * @return The created {@code YAMLObfuscator}.
         */
        public abstract YAMLObfuscator build();
    }

    /**
     * An object that can be used to configure a property that should be obfuscated.
     *
     * @author Rob Spoor
     */
    public abstract static class PropertyConfigurer extends Builder {

        private PropertyConfigurer() {
            super();
        }

        /**
         * Indicates that properties with the current name will not be obfuscated if they are YAML mappings or sequences.
         * This method is shorthand for calling both {@link #excludeMappings()} and {@link #excludeSequences()}.
         *
         * @return An object that can be used to configure the property, or continue building {@link YAMLObfuscator YAMLObfuscators}.
         */
        public PropertyConfigurer scalarsOnly() {
            return excludeMappings()
                    .excludeSequences();
        }

        /**
         * Indicates that properties with the current name will not be obfuscated if they are YAML mappings.
         *
         * @return An object that can be used to configure the property, or continue building {@link YAMLObfuscator YAMLObfuscators}.
         */
        public abstract PropertyConfigurer excludeMappings();

        /**
         * Indicates that properties with the current name will not be obfuscated if they are YAML sequences.
         *
         * @return An object that can be used to configure the property, or continue building {@link YAMLObfuscator YAMLObfuscators}.
         */
        public abstract PropertyConfigurer excludeSequences();

        /**
         * Indicates that properties with the current name will be obfuscated if they are YAML mappings or sequences.
         * This method is shorthand for calling both {@link #includeMappings()} and {@link #includeSequences()}.
         *
         * @return An object that can be used to configure the property, or continue building {@link YAMLObfuscator YAMLObfuscators}.
         */
        public PropertyConfigurer all() {
            return includeMappings()
                    .includeSequences();
        }

        /**
         * Indicates that properties with the current name will be obfuscated if they are YAML mappings.
         *
         * @return An object that can be used to configure the property, or continue building {@link YAMLObfuscator YAMLObfuscators}.
         */
        public abstract PropertyConfigurer includeMappings();

        /**
         * Indicates that properties with the current name will be obfuscated if they are YAML sequences.
         *
         * @return An object that can be used to configure the property, or continue building {@link YAMLObfuscator YAMLObfuscators}.
         */
        public abstract PropertyConfigurer includeSequences();
    }

    private static final class ObfuscatorBuilder extends PropertyConfigurer {

        private final MapBuilder<PropertyConfig> properties;

        private String malformedYAMLWarning;

        // default settings
        private boolean obfuscateMappingsByDefault;
        private boolean obfuscateSequencesByDefault;

        // per property settings
        private String property;
        private Obfuscator obfuscator;
        private CaseSensitivity caseSensitivity;
        private boolean obfuscateMappings;
        private boolean obfuscateSequences;

        private ObfuscatorBuilder() {
            properties = new MapBuilder<>();
            malformedYAMLWarning = Messages.YAMLObfuscator.malformedYAML.text.get();

            obfuscateMappingsByDefault = true;
            obfuscateSequencesByDefault = true;
        }

        @Override
        public PropertyConfigurer withProperty(String property, Obfuscator obfuscator) {
            addLastProperty();

            properties.testEntry(property);

            this.property = property;
            this.obfuscator = obfuscator;
            this.caseSensitivity = null;
            this.obfuscateMappings = obfuscateMappingsByDefault;
            this.obfuscateSequences = obfuscateSequencesByDefault;

            return this;
        }

        @Override
        public PropertyConfigurer withProperty(String property, Obfuscator obfuscator, CaseSensitivity caseSensitivity) {
            addLastProperty();

            properties.testEntry(property, caseSensitivity);

            this.property = property;
            this.obfuscator = obfuscator;
            this.caseSensitivity = caseSensitivity;
            this.obfuscateMappings = obfuscateMappingsByDefault;
            this.obfuscateSequences = obfuscateSequencesByDefault;

            return this;
        }

        @Override
        public PropertyConfigurer caseSensitiveByDefault() {
            properties.caseSensitiveByDefault();
            return this;
        }

        @Override
        public PropertyConfigurer caseInsensitiveByDefault() {
            properties.caseInsensitiveByDefault();
            return this;
        }

        @Override
        public Builder excludeMappingsByDefault() {
            obfuscateMappingsByDefault = false;
            return this;
        }

        @Override
        public Builder excludeSequencesByDefault() {
            obfuscateSequencesByDefault = false;
            return this;
        }

        @Override
        public Builder includeMappingsByDefault() {
            obfuscateMappingsByDefault = true;
            return this;
        }

        @Override
        public Builder includeSequencesByDefault() {
            obfuscateSequencesByDefault = true;
            return this;
        }

        @Override
        public PropertyConfigurer excludeMappings() {
            obfuscateMappings = false;
            return this;
        }

        @Override
        public PropertyConfigurer excludeSequences() {
            obfuscateSequences = false;
            return this;
        }

        @Override
        public PropertyConfigurer includeMappings() {
            obfuscateMappings = true;
            return this;
        }

        @Override
        public PropertyConfigurer includeSequences() {
            obfuscateSequences = true;
            return this;
        }

        @Override
        public Builder withMalformedYAMLWarning(String warning) {
            malformedYAMLWarning = warning;
            return this;
        }

        private Map<String, PropertyConfig> properties() {
            return properties.build();
        }

        private void addLastProperty() {
            if (property != null) {
                PropertyConfig propertyConfig = new PropertyConfig(obfuscator, obfuscateMappings, obfuscateSequences);
                if (caseSensitivity != null) {
                    properties.withEntry(property, propertyConfig, caseSensitivity);
                } else {
                    properties.withEntry(property, propertyConfig);
                }
            }

            property = null;
            obfuscator = null;
            caseSensitivity = null;
            obfuscateMappings = obfuscateMappingsByDefault;
            obfuscateSequences = obfuscateSequencesByDefault;
        }

        @Override
        public YAMLObfuscator build() {
            addLastProperty();

            return new YAMLObfuscator(this);
        }
    }
}
