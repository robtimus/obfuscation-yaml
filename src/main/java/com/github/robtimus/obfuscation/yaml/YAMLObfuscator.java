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

import static com.github.robtimus.obfuscation.support.ObfuscatorUtils.appendAtMost;
import static com.github.robtimus.obfuscation.support.ObfuscatorUtils.checkStartAndEnd;
import static com.github.robtimus.obfuscation.support.ObfuscatorUtils.copyTo;
import static com.github.robtimus.obfuscation.support.ObfuscatorUtils.counting;
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
import com.github.robtimus.obfuscation.support.CountingReader;
import com.github.robtimus.obfuscation.support.LimitAppendable;
import com.github.robtimus.obfuscation.support.MapBuilder;
import com.github.robtimus.obfuscation.yaml.YAMLObfuscator.PropertyConfigurer.ObfuscationMode;

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

    private final long limit;
    private final String truncatedIndicator;

    private YAMLObfuscator(ObfuscatorBuilder builder) {
        properties = builder.properties();

        settings = LoadSettings.builder()
                // be as lenient as possible
                .setAllowDuplicateKeys(true)
                .setAllowRecursiveKeys(true)
                // use marks, as they are needed for obfuscating text
                .setUseMarks(true)
                // use the defined max size
                .setCodePointLimit(builder.maxDocumentSize)
                .build();

        malformedYAMLWarning = builder.malformedYAMLWarning;

        limit = builder.limit;
        truncatedIndicator = builder.truncatedIndicator;
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
        Reader reader = reader(s, start, end);
        LimitAppendable appendable = appendAtMost(destination, limit);
        obfuscateText(reader, new Source.OfCharSequence(s), start, end, appendable);
        if (appendable.limitExceeded() && truncatedIndicator != null) {
            destination.append(String.format(truncatedIndicator, end - start));
        }
    }

    @Override
    public void obfuscateText(Reader input, Appendable destination) throws IOException {
        @SuppressWarnings("resource")
        CountingReader countingReader = counting(input);
        Source.OfReader source = new Source.OfReader(countingReader, LOGGER);
        @SuppressWarnings("resource")
        Reader reader = copyTo(countingReader, source);
        LimitAppendable appendable = appendAtMost(destination, limit);
        obfuscateText(reader, source, 0, -1, appendable);
        if (appendable.limitExceeded() && truncatedIndicator != null) {
            destination.append(String.format(truncatedIndicator, countingReader.count()));
        }
    }

    private void obfuscateText(Reader input, Source source, int start, int end, LimitAppendable destination) throws IOException {
        ObfuscatingParser parser = createParser(input, source, start, end, destination);

        try {
            while (parser.hasNext() && !destination.limitExceeded()) {
                parser.next();
            }
            parser.appendRemainder();
        } catch (YamlEngineException e) {
            LOGGER.warn(Messages.YAMLObfuscator.malformedYAML.warning(), e);
            if (malformedYAMLWarning != null) {
                destination.append(malformedYAMLWarning);
            }
        }
    }

    private ObfuscatingParser createParser(Reader input, Source source, int start, int end, LimitAppendable destination) {
        return new ObfuscatingParser(new ParserImpl(settings, new StreamReader(settings, input)), source, start, end, destination, properties);
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
                && Objects.equals(malformedYAMLWarning, other.malformedYAMLWarning)
                && limit == other.limit
                && Objects.equals(truncatedIndicator, other.truncatedIndicator);
    }

    @Override
    public int hashCode() {
        return properties.hashCode() ^ Objects.hashCode(malformedYAMLWarning) ^ Long.hashCode(limit) ^ Objects.hashCode(truncatedIndicator);
    }

    @Override
    @SuppressWarnings("nls")
    public String toString() {
        return getClass().getName()
                + "[properties=" + properties
                + ",malformedYAMLWarning=" + malformedYAMLWarning
                + ",limit=" + limit
                + ",truncatedIndicator=" + truncatedIndicator
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
    public interface Builder {

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
        PropertyConfigurer withProperty(String property, Obfuscator obfuscator);

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
        PropertyConfigurer withProperty(String property, Obfuscator obfuscator, CaseSensitivity caseSensitivity);

        /**
         * Sets the default case sensitivity for new properties to {@link CaseSensitivity#CASE_SENSITIVE}. This is the default setting.
         * <p>
         * Note that this will not change the case sensitivity of any property that was already added.
         *
         * @return This object.
         */
        Builder caseSensitiveByDefault();

        /**
         * Sets the default case sensitivity for new properties to {@link CaseSensitivity#CASE_INSENSITIVE}.
         * <p>
         * Note that this will not change the case sensitivity of any property that was already added.
         *
         * @return This object.
         */
        Builder caseInsensitiveByDefault();

        /**
         * Indicates that by default properties will not be obfuscated if they are YAML mappings or sequences.
         * This method is shorthand for calling both {@link #excludeMappingsByDefault()} and {@link #excludeSequencesByDefault()}.
         * <p>
         * Note that this will not change what will be obfuscated for any property that was already added.
         *
         * @return This object.
         */
        default Builder scalarsOnlyByDefault() {
            return excludeMappingsByDefault()
                    .excludeSequencesByDefault();
        }

        /**
         * Indicates that by default properties will not be obfuscated if they are YAML mappings.
         * This method is an alias for {@link #forMappingsByDefault(ObfuscationMode)} in combination with {@link ObfuscationMode#EXCLUDE}.
         * <p>
         * Note that this will not change what will be obfuscated for any property that was already added.
         *
         * @return This object.
         */
        default Builder excludeMappingsByDefault() {
            return forMappingsByDefault(ObfuscationMode.EXCLUDE);
        }

        /**
         * Indicates that by default properties will not be obfuscated if they are YAML sequences.
         * This method is an alias for {@link #forSequencesByDefault(ObfuscationMode)} in combination with {@link ObfuscationMode#EXCLUDE}.
         * <p>
         * Note that this will not change what will be obfuscated for any property that was already added.
         *
         * @return This object.
         */
        default Builder excludeSequencesByDefault() {
            return forSequencesByDefault(ObfuscationMode.EXCLUDE);
        }

        /**
         * Indicates that by default properties will be obfuscated if they are YAML mappings or sequences (default).
         * This method is shorthand for calling both {@link #includeMappingsByDefault()} and {@link #includeSequencesByDefault()}.
         * <p>
         * Note that this will not change what will be obfuscated for any property that was already added.
         *
         * @return This object.
         */
        default Builder allByDefault() {
            return includeMappingsByDefault()
                    .includeSequencesByDefault();
        }

        /**
         * Indicates that by default properties will be obfuscated if they are YAML mappings (default).
         * This method is an alias for {@link #forMappingsByDefault(ObfuscationMode)} in combination with {@link ObfuscationMode#OBFUSCATE}.
         * <p>
         * Note that this will not change what will be obfuscated for any property that was already added.
         *
         * @return This object.
         */
        default Builder includeMappingsByDefault() {
            return forMappingsByDefault(ObfuscationMode.OBFUSCATE);
        }

        /**
         * Indicates that by default properties will be obfuscated if they are YAML sequences (default).
         * This method is an alias for {@link #forSequencesByDefault(ObfuscationMode)} in combination with {@link ObfuscationMode#OBFUSCATE}.
         * <p>
         * Note that this will not change what will be obfuscated for any property that was already added.
         *
         * @return This object.
         */
        default Builder includeSequencesByDefault() {
            return forSequencesByDefault(ObfuscationMode.OBFUSCATE);
        }

        /**
         * Indicates how to handle properties if they are YAML mappings. The default is {@link ObfuscationMode#OBFUSCATE}.
         * This can be overridden per property using {@link PropertyConfigurer#forMappings(ObfuscationMode)}
         * <p>
         * Note that this will not change what will be obfuscated for any property that was already added.
         *
         * @param obfuscationMode The obfuscation mode that determines how to handle properties.
         * @return This object.
         * @throws NullPointerException If the given obfuscation mode is {@code null}.
         * @since 1.3
         */
        Builder forMappingsByDefault(ObfuscationMode obfuscationMode);

        /**
         * Indicates how to handle properties if they are YAML sequences. The default is {@link ObfuscationMode#OBFUSCATE}.
         * This can be overridden per property using {@link PropertyConfigurer#forSequences(ObfuscationMode)}
         * <p>
         * Note that this will not change what will be obfuscated for any property that was already added.
         *
         * @param obfuscationMode The obfuscation mode that determines how to handle properties.
         * @return This object.
         * @throws NullPointerException If the given obfuscation mode is {@code null}.
         * @since 1.3
         */
        Builder forSequencesByDefault(ObfuscationMode obfuscationMode);

        /**
         * Sets the maximum size of YAML documents. If documents are larger they will be regarded as malformed YAML. The default is 3MB.
         *
         * @param maxSize The maximum allowed size of YAML documents.
         * @return This object.
         * @throws IllegalArgumentException If the given maximum size is negative.
         * @since 1.2
         */
        Builder withMaxDocumentSize(int maxSize);

        /**
         * Sets the warning to include if a {@link YamlEngineException} is thrown.
         * This can be used to override the default message. Use {@code null} to omit the warning.
         *
         * @param warning The warning to include.
         * @return This object.
         */
        Builder withMalformedYAMLWarning(String warning);

        /**
         * Sets the limit for the obfuscated result.
         *
         * @param limit The limit to use.
         * @return An object that can be used to configure the handling when the obfuscated result exceeds a pre-defined limit,
         *         or continue building {@link YAMLObfuscator YAMLObfuscators}.
         * @throws IllegalArgumentException If the given limit is negative.
         * @since 1.1
         */
        LimitConfigurer limitTo(long limit);

        /**
         * This method allows the application of a function to this builder.
         * <p>
         * Any exception thrown by the function will be propagated to the caller.
         *
         * @param <R> The type of the result of the function.
         * @param f The function to apply.
         * @return The result of applying the function to this builder.
         */
        default <R> R transform(Function<? super Builder, ? extends R> f) {
            return f.apply(this);
        }

        /**
         * Creates a new {@code YAMLObfuscator} with the properties and obfuscators added to this builder.
         *
         * @return The created {@code YAMLObfuscator}.
         */
        YAMLObfuscator build();
    }

    /**
     * An object that can be used to configure a property that should be obfuscated.
     *
     * @author Rob Spoor
     */
    public interface PropertyConfigurer extends Builder {

        /**
         * Indicates that properties with the current name will not be obfuscated if they are YAML mappings or sequences.
         * This method is shorthand for calling both {@link #excludeMappings()} and {@link #excludeSequences()}.
         *
         * @return This object.
         */
        default PropertyConfigurer scalarsOnly() {
            return excludeMappings()
                    .excludeSequences();
        }

        /**
         * Indicates that properties with the current name will not be obfuscated if they are YAML mappings.
         * This method is an alias for {@link #forMappings(ObfuscationMode)} in combination with {@link ObfuscationMode#EXCLUDE}.
         *
         * @return This object.
         */
        default PropertyConfigurer excludeMappings() {
            return forMappings(ObfuscationMode.EXCLUDE);
        }

        /**
         * Indicates that properties with the current name will not be obfuscated if they are YAML sequences.
         * This method is an alias for {@link #forSequences(ObfuscationMode)} in combination with {@link ObfuscationMode#EXCLUDE}.
         *
         * @return This object.
         */
        default PropertyConfigurer excludeSequences() {
            return forSequences(ObfuscationMode.EXCLUDE);
        }

        /**
         * Indicates that properties with the current name will be obfuscated if they are YAML mappings or sequences.
         * This method is shorthand for calling both {@link #includeMappings()} and {@link #includeSequences()}.
         *
         * @return This object.
         */
        default PropertyConfigurer all() {
            return includeMappings()
                    .includeSequences();
        }

        /**
         * Indicates that properties with the current name will be obfuscated if they are YAML mappings.
         * This method is an alias for {@link #forMappings(ObfuscationMode)} in combination with {@link ObfuscationMode#OBFUSCATE}.
         *
         * @return This object.
         */
        default PropertyConfigurer includeMappings() {
            return forMappings(ObfuscationMode.OBFUSCATE);
        }

        /**
         * Indicates that properties with the current name will be obfuscated if they are YAML sequences.
         * This method is an alias for {@link #forSequences(ObfuscationMode)} in combination with {@link ObfuscationMode#OBFUSCATE}.
         *
         * @return This object.
         */
        default PropertyConfigurer includeSequences() {
            return forSequences(ObfuscationMode.OBFUSCATE);
        }

        /**
         * Indicates how to handle properties if they are YAML mappings. The default is {@link ObfuscationMode#OBFUSCATE}.
         *
         * @param obfuscationMode The obfuscation mode that determines how to handle properties.
         * @return This object.
         * @throws NullPointerException If the given obfuscation mode is {@code null}.
         * @since 1.3
         */
        PropertyConfigurer forMappings(ObfuscationMode obfuscationMode);

        /**
         * Indicates how to handle properties if they are YAML sequences. The default is {@link ObfuscationMode#OBFUSCATE}.
         *
         * @param obfuscationMode The obfuscation mode that determines how to handle properties.
         * @return This object.
         * @throws NullPointerException If the given obfuscation mode is {@code null}.
         * @since 1.3
         */
        PropertyConfigurer forSequences(ObfuscationMode obfuscationMode);

        /**
         * The possible ways to deal with nested mappings and sequences.
         *
         * @author Rob Spoor
         * @since 1.3
         */
        enum ObfuscationMode {
            /** Don't obfuscate nested mappings or sequences, but instead traverse into them. **/
            EXCLUDE,

            /** Obfuscate nested mappings and sequences completely. **/
            OBFUSCATE,

            /** Don't obfuscate nested mappings or sequences, but use the obfuscator for all nested scalar properties. **/
            INHERIT,

            /**
             * Don't obfuscate nested mappings or sequences, but use the obfuscator for all nested scalar properties.
             * If a nested property has its own obfuscator defined this will be used instead.
             **/
            INHERIT_OVERRIDABLE,
        }
    }

    /**
     * An object that can be used to configure handling when the obfuscated result exceeds a pre-defined limit.
     *
     * @author Rob Spoor
     * @since 1.1
     */
    public interface LimitConfigurer extends Builder {

        /**
         * Sets the indicator to use when the obfuscated result is truncated due to the limit being exceeded.
         * There can be one place holder for the total number of characters. Defaults to {@code ... (total: %d)}.
         * Use {@code null} to omit the indicator.
         *
         * @param pattern The pattern to use as indicator.
         * @return This object.
         */
        LimitConfigurer withTruncatedIndicator(String pattern);
    }

    private static final class ObfuscatorBuilder implements PropertyConfigurer, LimitConfigurer {

        private final MapBuilder<PropertyConfig> properties;

        private int maxDocumentSize;
        private String malformedYAMLWarning;

        private long limit;
        private String truncatedIndicator;

        // default settings
        private ObfuscationMode forMappingsByDefault;
        private ObfuscationMode forSequencesByDefault;

        // per property settings
        private String property;
        private Obfuscator obfuscator;
        private CaseSensitivity caseSensitivity;
        private ObfuscationMode forMappings;
        private ObfuscationMode forSequences;

        private ObfuscatorBuilder() {
            properties = new MapBuilder<>();

            maxDocumentSize = 3 * 1024 * 1024;
            malformedYAMLWarning = Messages.YAMLObfuscator.malformedYAML.text();

            limit = Long.MAX_VALUE;
            truncatedIndicator = "... (total: %d)"; //$NON-NLS-1$

            forMappingsByDefault = ObfuscationMode.OBFUSCATE;
            forSequencesByDefault = ObfuscationMode.OBFUSCATE;
        }

        @Override
        public PropertyConfigurer withProperty(String property, Obfuscator obfuscator) {
            addLastProperty();

            properties.testEntry(property);

            this.property = property;
            this.obfuscator = obfuscator;
            this.caseSensitivity = null;
            this.forMappings = forMappingsByDefault;
            this.forSequences = forSequencesByDefault;

            return this;
        }

        @Override
        public PropertyConfigurer withProperty(String property, Obfuscator obfuscator, CaseSensitivity caseSensitivity) {
            addLastProperty();

            properties.testEntry(property, caseSensitivity);

            this.property = property;
            this.obfuscator = obfuscator;
            this.caseSensitivity = caseSensitivity;
            this.forMappings = forMappingsByDefault;
            this.forSequences = forSequencesByDefault;

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
        public Builder forMappingsByDefault(ObfuscationMode obfuscationMode) {
            forMappingsByDefault = Objects.requireNonNull(obfuscationMode);
            return this;
        }

        @Override
        public Builder forSequencesByDefault(ObfuscationMode obfuscationMode) {
            forSequencesByDefault = Objects.requireNonNull(obfuscationMode);
            return this;
        }

        @Override
        public PropertyConfigurer forMappings(ObfuscationMode obfuscationMode) {
            forMappings = Objects.requireNonNull(obfuscationMode);
            return this;
        }

        @Override
        public PropertyConfigurer forSequences(ObfuscationMode obfuscationMode) {
            forSequences = Objects.requireNonNull(obfuscationMode);
            return this;
        }

        @Override
        public Builder withMaxDocumentSize(int maxSize) {
            if (maxSize < 0) {
                throw new IllegalArgumentException(maxSize + " < 0"); //$NON-NLS-1$
            }
            this.maxDocumentSize = maxSize;
            return this;
        }

        @Override
        public Builder withMalformedYAMLWarning(String warning) {
            malformedYAMLWarning = warning;
            return this;
        }

        @Override
        public LimitConfigurer limitTo(long limit) {
            if (limit < 0) {
                throw new IllegalArgumentException(limit + " < 0"); //$NON-NLS-1$
            }
            this.limit = limit;
            return this;
        }

        @Override
        public LimitConfigurer withTruncatedIndicator(String pattern) {
            this.truncatedIndicator = pattern;
            return this;
        }

        private Map<String, PropertyConfig> properties() {
            return properties.build();
        }

        private void addLastProperty() {
            if (property != null) {
                PropertyConfig propertyConfig = new PropertyConfig(obfuscator, forMappings, forSequences);
                if (caseSensitivity != null) {
                    properties.withEntry(property, propertyConfig, caseSensitivity);
                } else {
                    properties.withEntry(property, propertyConfig);
                }
            }

            property = null;
            obfuscator = null;
            caseSensitivity = null;
            forMappings = forMappingsByDefault;
            forSequences = forSequencesByDefault;
        }

        @Override
        public YAMLObfuscator build() {
            addLastProperty();

            return new YAMLObfuscator(this);
        }
    }
}
