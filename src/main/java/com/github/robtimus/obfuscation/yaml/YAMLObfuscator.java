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

import static com.github.robtimus.obfuscation.CaseSensitivity.CASE_SENSITIVE;
import static com.github.robtimus.obfuscation.ObfuscatorUtils.checkStartAndEnd;
import static com.github.robtimus.obfuscation.ObfuscatorUtils.copyTo;
import static com.github.robtimus.obfuscation.ObfuscatorUtils.discardAll;
import static com.github.robtimus.obfuscation.ObfuscatorUtils.map;
import static com.github.robtimus.obfuscation.ObfuscatorUtils.reader;
import static com.github.robtimus.obfuscation.ObfuscatorUtils.skipLeadingWhitespace;
import static com.github.robtimus.obfuscation.ObfuscatorUtils.skipTrailingWhitespace;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snakeyaml.engine.v2.api.LoadSettings;
import org.snakeyaml.engine.v2.common.Anchor;
import org.snakeyaml.engine.v2.common.ScalarStyle;
import org.snakeyaml.engine.v2.events.Event;
import org.snakeyaml.engine.v2.events.ScalarEvent;
import org.snakeyaml.engine.v2.exceptions.YamlEngineException;
import org.snakeyaml.engine.v2.parser.Parser;
import org.snakeyaml.engine.v2.parser.ParserImpl;
import org.snakeyaml.engine.v2.scanner.StreamReader;
import com.github.robtimus.obfuscation.CachingObfuscatingWriter;
import com.github.robtimus.obfuscation.CaseSensitivity;
import com.github.robtimus.obfuscation.Obfuscator;
import com.github.robtimus.obfuscation.ObfuscatorUtils.MapBuilder;

/**
 * An obfuscator that obfuscates YAML properties in {@link CharSequence CharSequences} or the contents of {@link Reader Readers}.
 *
 * @author Rob Spoor
 */
public final class YAMLObfuscator extends Obfuscator {

    private static final Logger LOGGER = LoggerFactory.getLogger(YAMLObfuscator.class);

    /**
     * The possible obfuscation modes.
     *
     * @author Rob Spoor
     */
    public enum ObfuscationMode {
        /** Indicates only scalar properties (strings, numbers, booleans, nulls) will be obfuscated, not sequences or mappings. */
        SCALAR(false, false),

        /** Indicates all properties will be obfuscated, including sequences and mappings. */
        ALL(true, true),
        ;

        private final boolean obfuscateSequences;
        private final boolean obfuscateMappings;

        ObfuscationMode(boolean obfuscateSequences, boolean obfuscateMappings) {
            this.obfuscateSequences = obfuscateSequences;
            this.obfuscateMappings = obfuscateMappings;
        }
    }

    private final Map<String, Obfuscator> obfuscators;
    private final ObfuscationMode obfuscationMode;

    private final LoadSettings settings;

    private final String malformedYAMLWarning;

    private YAMLObfuscator(Builder builder) {
        obfuscators = builder.obfuscators();
        obfuscationMode = builder.obfuscationMode;

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
        Parser parser = new ParserImpl(new StreamReader(input, settings), settings);
        Context context = new Context(parser, s, start, end, destination);
        Event event = null;
        try {
            while (context.hasNextEvent()) {
                event = context.nextEvent();
                if (event.isEvent(Event.ID.Scalar) && context.hasCurrentFieldName()) {
                    String property = context.currentFieldName();
                    Obfuscator obfuscator = obfuscators.get(property);
                    if (obfuscator != null) {
                        obfuscateProperty(obfuscator, context);
                    }
                }
            }
            // read the remainder so the final append will include all text
            discardAll(input);
            context.appendRemainder();
        } catch (YamlEngineException e) {
            LOGGER.warn(Messages.YAMLObfuscator.malformedYAML.warning.get(), e);
            if (malformedYAMLWarning != null) {
                destination.append(malformedYAMLWarning);
            }
        }
    }

    private void obfuscateProperty(Obfuscator obfuscator, Context context) throws IOException {
        Event event = context.nextEvent();
        switch (event.getEventId()) {
        case SequenceStart:
            if (!obfuscationMode.obfuscateSequences) {
                // there is an obfuscator for the sequence property, but the obfuscation mode prohibits obfuscating sequences;
                // abort and continue with the next property
                return;
            }
            context.appendUntilEvent(event);
            obfuscateNested(obfuscator, context, event, Event.ID.SequenceStart, Event.ID.SequenceEnd);
            break;
        case MappingStart:
            if (!obfuscationMode.obfuscateMappings) {
                // there is an obfuscator for the mapping property, but the obfuscation mode prohibits obfuscating mappings;
                // abort and continue with the next property
                return;
            }
            context.appendUntilEvent(event);
            obfuscateNested(obfuscator, context, event, Event.ID.MappingStart, Event.ID.MappingEnd);
            break;
        case Scalar:
            context.appendUntilEvent(event);
            obfuscateScalar(obfuscator, context, event);
            break;
        default:
            // do nothing
            break;
        }
    }

    private void obfuscateNested(Obfuscator obfuscator, Context context, Event startEvent, Event.ID beginEventId, Event.ID endEventId)
            throws IOException {

        int depth = 1;
        Event endEvent = null;
        while (depth > 0 && context.hasNextEvent()) {
            endEvent = context.nextEvent();
            if (endEvent.isEvent(beginEventId)) {
                depth++;
            } else if (endEvent.isEvent(endEventId)) {
                depth--;
            }
        }
        context.obfuscateUntilEvent(startEvent, endEvent, obfuscator);
    }

    private void obfuscateScalar(Obfuscator obfuscator, Context context, Event event) throws IOException {
        context.obfuscateEvent(event, obfuscator);
    }

    private static final class Context {
        private final Parser parser;
        private final CharSequence text;
        private final Appendable destination;

        private final int textOffset;
        private final int textEnd;
        private int textIndex;

        // Snakeyaml reports field names as Scalar events. The only difference with actual values is the current state.
        // We need to keep track of this state, more specifically whether or not the current structure is a mapping or no, and the current field name.
        private final Deque<Event.ID> structureStack = new ArrayDeque<>();
        private String currentFieldName;

        private Context(Parser parser, CharSequence source, int start, int end, Appendable destination) {
            this.parser = parser;
            this.text = source;
            this.textOffset = start;
            this.textEnd = end;
            this.textIndex = start;
            this.destination = destination;
        }

        private boolean hasNextEvent() {
            return parser.hasNext();
        }

        private Event nextEvent() {
            Event event = parser.next();
            Event.ID eventId = event.getEventId();
            switch (eventId) {
            case StreamStart:
            case DocumentStart:
            case SequenceStart:
            case MappingStart:
                structureStack.addLast(eventId);
                currentFieldName = null;
                break;
            case StreamEnd:
            case DocumentEnd:
            case SequenceEnd:
            case MappingEnd:
                structureStack.removeLast();
                currentFieldName = null;
                break;
            case Alias:
                currentFieldName = null;
                break;
            case Scalar:
                if (structureStack.peekLast() == Event.ID.MappingStart && currentFieldName == null) {
                    // directly inside an array, and there is no current field name, so this must be it
                    currentFieldName = ((ScalarEvent) event).getValue();
                } else {
                    currentFieldName = null;
                }
                break;
            default:
                break;
            }
            return event;
        }

        private boolean hasCurrentFieldName() {
            return currentFieldName != null;
        }

        private String currentFieldName() {
            return currentFieldName;
        }

        private void appendUntilEvent(Event event) throws IOException {
            int eventStartIndex = startIndex(event);
            destination.append(text, textIndex, eventStartIndex);
            textIndex = eventStartIndex;
        }

        private void obfuscateUntilEvent(Event startEvent, Event endEvent, Obfuscator obfuscator) throws IOException {
            int startEventStartIndex = startIndex(startEvent);
            int eventEndIndex = endIndex(endEvent);
            // don't include any trailing white space
            eventEndIndex = skipTrailingWhitespace(text, startEventStartIndex, eventEndIndex);

            obfuscator.obfuscateText(text, startEventStartIndex, eventEndIndex, destination);
            textIndex = eventEndIndex;
        }

        private void obfuscateEvent(Event event, Obfuscator obfuscator) throws IOException {
            int eventStartIndex = startIndex(event);
            int eventEndIndex = endIndex(event);

            ScalarEvent scalarEvent = (ScalarEvent) event;

            // don't obfuscate any anchor
            eventStartIndex = appendAnchor(scalarEvent, eventStartIndex, eventEndIndex);

            // don't obfuscate any " or ' around the actual value
            ScalarStyle scalarStyle = scalarEvent.getScalarStyle();
            if (scalarStyle == ScalarStyle.DOUBLE_QUOTED || scalarStyle == ScalarStyle.SINGLE_QUOTED) {
                destination.append(text.charAt(eventStartIndex));
                obfuscator.obfuscateText(text, eventStartIndex + 1, eventEndIndex - 1, destination);
                destination.append(text.charAt(eventEndIndex - 1));
            } else {
                obfuscator.obfuscateText(text, eventStartIndex, eventEndIndex, destination);
            }
            textIndex = eventEndIndex;
        }

        private int appendAnchor(ScalarEvent event, int eventStartIndex, int eventEndIndex) throws IOException {
            Optional<Anchor> anchor = event.getAnchor();
            if (anchor.isPresent()) {
                // skip past the & and anchor name
                int newStartIndex = eventStartIndex;
                newStartIndex += 1 + anchor.get().getAnchor().length();
                newStartIndex = skipLeadingWhitespace(text, newStartIndex, eventEndIndex);
                destination.append(text, eventStartIndex, newStartIndex);
                return newStartIndex;
            }
            return eventStartIndex;
        }

        private void appendRemainder() throws IOException {
            int end = textEnd == -1 ? text.length() : textEnd;
            destination.append(text, textIndex, end);
            textIndex = end;
        }

        private int startIndex(Event event) {
            return textOffset + event.getStartMark().get().getIndex();
        }

        private int endIndex(Event event) {
            return textOffset + event.getEndMark().get().getIndex();
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
        return obfuscators.equals(other.obfuscators)
                && obfuscationMode == other.obfuscationMode
                && Objects.equals(malformedYAMLWarning, other.malformedYAMLWarning);
    }

    @Override
    public int hashCode() {
        return obfuscators.hashCode() ^ obfuscationMode.hashCode() ^ Objects.hashCode(malformedYAMLWarning);
    }

    @Override
    @SuppressWarnings("nls")
    public String toString() {
        return getClass().getName()
                + "[obfuscators=" + obfuscators
                + ",obfuscationMode=" + obfuscationMode
                + ",malformedYAMLWarning=" + malformedYAMLWarning
                + "]";
    }

    /**
     * Returns a builder that will create {@code YAMLObfuscators}.
     *
     * @return A builder that will create {@code YAMLObfuscators}.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * A builder for {@link YAMLObfuscator YAMLObfuscators}.
     *
     * @author Rob Spoor
     */
    public static final class Builder {

        private final MapBuilder<Obfuscator> obfuscators;

        private ObfuscationMode obfuscationMode;

        private String malformedYAMLWarning;

        private Builder() {
            obfuscators = map();
            obfuscationMode = ObfuscationMode.ALL;
            malformedYAMLWarning = Messages.YAMLObfuscator.malformedYAML.text.get();
        }

        /**
         * Adds a property to obfuscate.
         * This method is an alias for {@link #withProperty(String, Obfuscator, CaseSensitivity) withProperty(property, obfuscator, CASE_SENSITIVE)}.
         *
         * @param property The name of the property. It will be treated case sensitively.
         * @param obfuscator The obfuscator to use for obfuscating the property.
         * @return This object.
         * @throws NullPointerException If the given property name or obfuscator is {@code null}.
         * @throws IllegalArgumentException If a property with the same name and the same case sensitivity was already added.
         */
        public Builder withProperty(String property, Obfuscator obfuscator) {
            return withProperty(property, obfuscator, CASE_SENSITIVE);
        }

        /**
         * Adds a property to obfuscate.
         *
         * @param property The name of the property.
         * @param obfuscator The obfuscator to use for obfuscating the property.
         * @param caseSensitivity The case sensitivity for the key.
         * @return This object.
         * @throws NullPointerException If the given property name, obfuscator or case sensitivity is {@code null}.
         * @throws IllegalArgumentException If a property with the same name and the same case sensitivity was already added.
         */
        public Builder withProperty(String property, Obfuscator obfuscator, CaseSensitivity caseSensitivity) {
            obfuscators.withEntry(property, obfuscator, caseSensitivity);
            return this;
        }

        /**
         * Sets the obfuscation mode. The default is {@link ObfuscationMode#ALL}.
         *
         * @param obfuscationMode The obfuscation mode.
         * @return This object.
         * @throws NullPointerException If the given obfuscation mode is {@code null}.
         */
        public Builder withObfuscationMode(ObfuscationMode obfuscationMode) {
            this.obfuscationMode = Objects.requireNonNull(obfuscationMode);
            return this;
        }

        /**
         * Sets the warning to include if a {@link YamlEngineException} is thrown.
         * This can be used to override the default message. Use {@code null} to omit the warning.
         *
         * @param warning The warning to include.
         * @return This object.
         */
        public Builder withMalformedYAMLWarning(String warning) {
            malformedYAMLWarning = warning;
            return this;
        }

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

        private Map<String, Obfuscator> obfuscators() {
            return obfuscators.build();
        }

        /**
         * Creates a new {@code YAMLObfuscator} with the properties and obfuscators added to this builder.
         *
         * @return The created {@code YAMLObfuscator}.
         */
        public YAMLObfuscator build() {
            return new YAMLObfuscator(this);
        }
    }
}
