/*
 * ObfuscatingParser.java
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

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import org.snakeyaml.engine.v2.common.Anchor;
import org.snakeyaml.engine.v2.common.ScalarStyle;
import org.snakeyaml.engine.v2.events.Event;
import org.snakeyaml.engine.v2.events.Event.ID;
import org.snakeyaml.engine.v2.events.ScalarEvent;
import org.snakeyaml.engine.v2.parser.Parser;
import com.github.robtimus.obfuscation.Obfuscator;
import com.github.robtimus.obfuscation.yaml.YAMLObfuscator.PropertyConfigurer.ObfuscationMode;

final class ObfuscatingParser implements Parser {

    private final Parser delegate;
    private final Source source;
    private final Appendable destination;

    private final Map<String, PropertyConfig> properties;

    private final int textOffset;
    private final int textEnd;
    private int textIndex;

    private final Deque<ObfuscatedProperty> currentProperties = new ArrayDeque<>();
    // SnakeYAML reports field names as Scalar events. The only difference with actual values is the current state.
    // We need to keep track of this state, more specifically whether or not the current structure is a mapping or no, and the current field name.
    private final Deque<Event.ID> structureStack = new ArrayDeque<>();
    private String currentFieldName;

    ObfuscatingParser(Parser parser, Source source, int start, int end, Appendable destination, Map<String, PropertyConfig> properties) {
        this.delegate = parser;
        this.source = source;
        this.textOffset = start;
        this.textEnd = end;
        this.textIndex = start;
        this.destination = destination;
        this.properties = properties;
    }

    @Override
    public boolean checkEvent(ID choice) {
        return delegate.checkEvent(choice);
    }

    @Override
    public boolean hasNext() {
        return delegate.hasNext();
    }

    @Override
    public Event peekEvent() {
        return delegate.peekEvent();
    }

    @Override
    public Event next() {
        Event event = delegate.next();
        switch (event.getEventId()) {
            case StreamStart:
            case DocumentStart:
                startStructure(event.getEventId());
                break;
            case StreamEnd:
            case DocumentEnd:
                endStructure();
                break;
            case MappingStart:
                startMapping(event);
                break;
            case MappingEnd:
                endMapping(event);
                break;
            case SequenceStart:
                startSequence(event);
                break;
            case SequenceEnd:
                endSequence(event);
                break;
            case Alias:
                alias();
                break;
            case Scalar:
                scalar(event);
                break;
            default:
                break;
        }
        return event;
    }

    private void startStructure(Event.ID eventId) {
        structureStack.addLast(eventId);
        currentFieldName = null;
    }

    private void endStructure() {
        structureStack.removeLast();
        currentFieldName = null;
    }

    private void startMapping(Event event) {
        startStructure(event, Event.ID.MappingStart, p -> p.forMappings);
    }

    private void endMapping(Event event) {
        endStructure(event, Event.ID.MappingStart);
    }

    private void startSequence(Event event) {
        startStructure(event, Event.ID.SequenceStart, p -> p.forSequences);
    }

    private void endSequence(Event event) {
        endStructure(event, Event.ID.SequenceStart);
    }

    private void startStructure(Event event, Event.ID startEventId, Function<PropertyConfig, ObfuscationMode> getObfuscationMode) {
        startStructure(startEventId);
        ObfuscatedProperty currentProperty = currentProperties.peekLast();
        if (currentProperty != null) {
            if (currentProperty.depth == 0) {
                // The start of the structure that's being obfuscated
                ObfuscationMode obfuscationMode = getObfuscationMode.apply(currentProperty.config);
                if (obfuscationMode == ObfuscationMode.EXCLUDE) {
                    // There is an obfuscator for the structure property, but the obfuscation mode prohibits obfuscating it, so discard the property
                    currentProperties.removeLast();
                } else {
                    appendUntilEvent(event);

                    currentProperty.startEvent = event;
                    currentProperty.obfuscationMode = obfuscationMode;
                    currentProperty.depth++;
                }
            } else if (currentProperty.hasStartEventId(startEventId)) {
                // In a nested structure that's being obfuscated; do nothing
                currentProperty.depth++;
            }
            // else in a nested structure that's being obfuscated; do nothing
        }
        // else not obfuscating
    }

    private void endStructure(Event event, Event.ID startEventId) {
        endStructure();
        ObfuscatedProperty currentProperty = currentProperties.peekLast();
        if (currentProperty != null && currentProperty.hasStartEventId(startEventId)) {
            currentProperty.depth--;
            if (currentProperty.depth == 0) {
                if (currentProperty.obfuscateStructure()) {
                    obfuscateUntilEvent(currentProperty.startEvent, event, currentProperty.config.obfuscator);
                }
                // else the obfuscator is Obfuscator.none(), which means we don't need to obfuscate,
                // or the structure itself should not be obfuscated

                currentProperties.removeLast();
            }
            // else still in a nested structure that's being obfuscated
        }
        // else currently no structure is being obfuscated
    }

    private void alias() {
        currentFieldName = null;
    }

    private void scalar(Event event) {
        if (structureStack.peekLast() == Event.ID.MappingStart && currentFieldName == null) {
            // directly inside a sequence, and there is no current field name, so this must be it
            currentFieldName = ((ScalarEvent) event).getValue();
            fieldName(event);
        } else {
            currentFieldName = null;
            scalarValue(event);
        }
    }

    private void fieldName(Event event) {
        ObfuscatedProperty currentProperty = currentProperties.peekLast();
        if (currentProperty == null || currentProperty.allowsOverriding()) {
            PropertyConfig config = properties.get(currentFieldName);
            if (config != null) {
                currentProperty = new ObfuscatedProperty(config);
                currentProperties.addLast(currentProperty);
            }

            if (source.needsTruncating()) {
                appendUntilEvent(event);
                source.truncate();
            }
        } else if (!currentProperty.config.performObfuscation && source.needsTruncating()) {
            // in a nested object or array that's being obfuscated using Obfuscator.none(), which means we can just append data already
            appendUntilEvent(event);
            source.truncate();
        }
        // else in a nested mapping or sequence that's being obfuscated; do nothing
    }

    private void scalarValue(Event event) {
        ObfuscatedProperty currentProperty = currentProperties.peekLast();
        if (currentProperty != null && currentProperty.obfuscateScalar()) {
            appendUntilEvent(event);
            obfuscateEvent(event, currentProperty.config.obfuscator);

            if (currentProperty.depth == 0) {
                currentProperties.removeLast();
            }
        }
        // else not obfuscating, or in a nested mapping or or sequence that's being obfuscated; do nothing
    }

    private void appendUntilEvent(Event event) {
        int eventStartIndex = startIndex(event);
        try {
            source.appendTo(textIndex, eventStartIndex, destination);
            textIndex = eventStartIndex;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void obfuscateUntilEvent(Event startEvent, Event endEvent, Obfuscator obfuscator) {
        int startEventStartIndex = startIndex(startEvent);
        int eventEndIndex = endIndex(endEvent);
        // don't include any trailing white space
        eventEndIndex = source.skipTrailingWhitespace(startEventStartIndex, eventEndIndex);

        try {
            source.obfuscateText(startEventStartIndex, eventEndIndex, obfuscator, destination);
            textIndex = eventEndIndex;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void obfuscateEvent(Event event, Obfuscator obfuscator) {
        int eventStartIndex = startIndex(event);
        int eventEndIndex = endIndex(event);

        ScalarEvent scalarEvent = (ScalarEvent) event;

        try {
            // don't obfuscate any anchor
            eventStartIndex = appendAnchor(scalarEvent, eventStartIndex, eventEndIndex);

            // don't obfuscate any " or ' around the actual value
            ScalarStyle scalarStyle = scalarEvent.getScalarStyle();
            if (scalarStyle == ScalarStyle.DOUBLE_QUOTED || scalarStyle == ScalarStyle.SINGLE_QUOTED) {
                destination.append(source.charAt(eventStartIndex));
                source.obfuscateText(eventStartIndex + 1, eventEndIndex - 1, obfuscator, destination);
                destination.append(source.charAt(eventEndIndex - 1));
            } else {
                source.obfuscateText(eventStartIndex, eventEndIndex, obfuscator, destination);
            }
            textIndex = eventEndIndex;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private int appendAnchor(ScalarEvent event, int eventStartIndex, int eventEndIndex) throws IOException {
        Optional<Anchor> anchor = event.getAnchor();
        if (anchor.isPresent()) {
            // skip past the & and anchor name
            int newStartIndex = eventStartIndex;
            // In snakeyaml-engine 2.0, class Anchor has method getAnchor()
            // In snakeyaml-engine 2.1 that was replaced by method getValue()
            // To support both, use toString() that returns the anchor/value for both versions
            newStartIndex += 1 + anchor.get().toString().length();
            newStartIndex = source.skipLeadingWhitespace(newStartIndex, eventEndIndex);
            source.appendTo(eventStartIndex, newStartIndex, destination);
            return newStartIndex;
        }
        return eventStartIndex;
    }

    private int startIndex(Event event) {
        return textOffset + event.getStartMark()
                .orElseThrow(() -> new IllegalStateException(Messages.YAMLObfuscator.markNotAvailable()))
                .getIndex();
    }

    private int endIndex(Event event) {
        return textOffset + event.getEndMark()
                .orElseThrow(() -> new IllegalStateException(Messages.YAMLObfuscator.markNotAvailable()))
                .getIndex();
    }

    void appendRemainder() throws IOException {
        textIndex = source.appendRemainder(textIndex, textEnd, destination);
    }

    private static final class ObfuscatedProperty {

        private final PropertyConfig config;
        // In case of obfuscated mappings and sequences, we need to remember the start event
        private Event startEvent;
        private ObfuscationMode obfuscationMode;
        private int depth = 0;

        private ObfuscatedProperty(PropertyConfig config) {
            this.config = config;
        }

        private boolean hasStartEventId(Event.ID startEventId) {
            return startEvent != null && startEvent.getEventId() == startEventId;
        }

        private boolean allowsOverriding() {
            // OBFUSCATE and INHERITED do not allow overriding
            // No need to include EXCLUDE; if that occurs the ObfuscatedProperty is discarded
            return obfuscationMode == ObfuscationMode.INHERIT_OVERRIDABLE;
        }

        private boolean obfuscateStructure() {
            // Don't obfuscate the entire structure if Obfuscator.none() is used
            return config.performObfuscation && obfuscationMode == ObfuscationMode.OBFUSCATE;
        }

        private boolean obfuscateScalar() {
            // Don't obfuscate the scalar if Obfuscator.none() is used
            // Obfuscate if depth == 0 (the property is for the scalar itself),
            // or if the obfuscation mode is INHERITED or INHERITED_OVERRIDABLE (EXCLUDE is discarded)
            return config.performObfuscation
                    && (depth == 0 || obfuscationMode != ObfuscationMode.OBFUSCATE);
        }
    }
}
