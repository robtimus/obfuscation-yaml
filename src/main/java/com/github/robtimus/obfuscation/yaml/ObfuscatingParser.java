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
import java.util.function.Predicate;
import org.snakeyaml.engine.v2.common.Anchor;
import org.snakeyaml.engine.v2.common.ScalarStyle;
import org.snakeyaml.engine.v2.events.Event;
import org.snakeyaml.engine.v2.events.Event.ID;
import org.snakeyaml.engine.v2.events.ScalarEvent;
import org.snakeyaml.engine.v2.parser.Parser;
import com.github.robtimus.obfuscation.Obfuscator;

final class ObfuscatingParser implements Parser {

    private final Parser delegate;
    private final Source source;
    private final Appendable destination;

    private final Map<String, PropertyConfig> properties;

    private final int textOffset;
    private final int textEnd;
    private int textIndex;

    private PropertyConfig currentProperty;
    // Snakeyaml reports field names as Scalar events. The only difference with actual values is the current state.
    // We need to keep track of this state, more specifically whether or not the current structure is a mapping or no, and the current field name.
    private final Deque<Event.ID> structureStack = new ArrayDeque<>();
    private String currentFieldName;

    // In case of obfuscated mappings and sequences, we need to remember the start event
    private Event startEvent;
    private int depth = 0;

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
        startStructure(event, Event.ID.MappingStart, p -> p.obfuscateMappings);
    }

    private void endMapping(Event event) {
        endStructure(event, Event.ID.MappingStart);
    }

    private void startSequence(Event event) {
        startStructure(event, Event.ID.SequenceStart, p -> p.obfuscateSequences);
    }

    private void endSequence(Event event) {
        endStructure(event, Event.ID.SequenceStart);
    }

    private void startStructure(Event event, Event.ID startEventId, Predicate<PropertyConfig> doValidate) {
        startStructure(startEventId);
        if (currentProperty != null) {
            if (depth == 0) {
                if (doValidate.test(currentProperty)) {
                    appendUntilEvent(event);

                    startEvent = event;
                    depth++;
                } else {
                    // There is an obfuscator for the structure property, but the obfuscation mode prohibits obfuscating it; reset the obfuscation
                    currentProperty = null;
                }
            } else if (startEvent.getEventId() == startEventId) {
                // In a nested structure that's being obfuscated; do nothing
                depth++;
            }
            // else in a nested structure that's being obfuscated; do nothing
        }
        // else not obfuscating
    }

    private void endStructure(Event event, Event.ID startEventId) {
        endStructure();
        if (startEvent != null && startEvent.getEventId() == startEventId) {
            depth--;
            if (depth == 0) {
                if (currentProperty.performObfuscation) {
                    obfuscateUntilEvent(startEvent, event, currentProperty.obfuscator);
                }
                // else the obfuscator is Obfuscator.none(), which means we don't need to obfuscate

                currentProperty = null;
                startEvent = null;
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
        if (currentProperty == null) {
            currentProperty = properties.get(currentFieldName);

            if (source.needsTruncating()) {
                appendUntilEvent(event);
                source.truncate();
            }
        } else if (!currentProperty.performObfuscation && source.needsTruncating()) {
            // in a nested object or array that's being obfuscated using Obfuscator.none(), which means we can just append data already
            appendUntilEvent(event);
            source.truncate();
        }
        // else in a nested mapping or sequence that's being obfuscated; do nothing
    }

    private void scalarValue(Event event) {
        if (currentProperty != null && depth == 0) {
            appendUntilEvent(event);
            // obfuscate even if the obfuscator is Obfuscator.none(), as that will already append the original value
            obfuscateEvent(event, currentProperty.obfuscator);
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
        eventEndIndex = skipTrailingWhitespace(startEventStartIndex, eventEndIndex);

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
            newStartIndex = skipLeadingWhitespace(newStartIndex, eventEndIndex);
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

    private int skipLeadingWhitespace(int fromIndex, int toIndex) {
        for (int i = fromIndex; i < toIndex; i++) {
            if (!Character.isWhitespace(source.charAt(i))) {
                return i;
            }
        }
        return toIndex;
    }

    private int skipTrailingWhitespace(int fromIndex, int toIndex) {
        for (int i = toIndex; i > fromIndex; i--) {
            if (!Character.isWhitespace(source.charAt(i - 1))) {
                return i;
            }
        }
        return fromIndex;
    }
}
