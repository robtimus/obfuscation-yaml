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

import static com.github.robtimus.obfuscation.support.ObfuscatorUtils.skipLeadingWhitespace;
import static com.github.robtimus.obfuscation.support.ObfuscatorUtils.skipTrailingWhitespace;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.Optional;
import org.snakeyaml.engine.v2.common.Anchor;
import org.snakeyaml.engine.v2.common.ScalarStyle;
import org.snakeyaml.engine.v2.events.Event;
import org.snakeyaml.engine.v2.events.Event.ID;
import org.snakeyaml.engine.v2.events.ScalarEvent;
import org.snakeyaml.engine.v2.parser.Parser;
import com.github.robtimus.obfuscation.Obfuscator;

final class ObfuscatingParser implements Parser {

    private final Parser delegate;
    private final CharSequence text;
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

    ObfuscatingParser(Parser parser, CharSequence source, int start, int end, Appendable destination,
            Map<String, PropertyConfig> properties) {

        this.delegate = parser;
        this.text = source;
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
        startStructure(Event.ID.MappingStart);
        if (currentProperty != null) {
            if (depth == 0) {
                if (currentProperty.obfuscateMappings) {
                    appendUntilEvent(event);

                    startEvent = event;
                    depth++;
                } else {
                    // There is an obfuscator for the mapping property, but the obfuscation mode prohibits obfuscating mappings; reset the obfuscation
                    currentProperty = null;
                }
            } else if (startEvent.getEventId() == Event.ID.MappingStart) {
                // In a nested mapping that's being obfuscated; do nothing
                depth++;
            }
            // else in a nested sequence that's being obfuscated; do nothing
        }
        // else not obfuscating
    }

    private void endMapping(Event event) {
        endStructure();
        if (startEvent != null && startEvent.getEventId() == Event.ID.MappingStart) {
            depth--;
            if (depth == 0) {
                obfuscateUntilEvent(startEvent, event, currentProperty.obfuscator);

                currentProperty = null;
                startEvent = null;
            }
            // else still in a nested mapping that's being obfuscated
        }
        // else currently no mapping is being obfuscated
    }

    private void startSequence(Event event) {
        startStructure(Event.ID.SequenceStart);
        if (currentProperty != null) {
            if (depth == 0) {
                if (currentProperty.obfuscateSequences) {
                    appendUntilEvent(event);

                    startEvent = event;
                    depth++;
                } else {
                    // There is an obfuscator for the sequence property, but the obfuscation mode prohibits obfuscating sequences; reset the
                    // obfuscation
                    currentProperty = null;
                }
            } else if (startEvent.getEventId() == Event.ID.SequenceStart) {
                // In a nested sequence that's being obfuscated; do nothing
                depth++;
            }
            // else in a nested sequence that's being obfuscated; do nothing
        }
        // else not obfuscating
    }

    private void endSequence(Event event) {
        endStructure();
        if (startEvent != null && startEvent.getEventId() == Event.ID.SequenceStart) {
            depth--;
            if (depth == 0) {
                obfuscateUntilEvent(startEvent, event, currentProperty.obfuscator);

                currentProperty = null;
                startEvent = null;
            }
            // else still in a nested sequence that's being obfuscated
        }
        // else currently no sequence is being obfuscated
    }

    private void alias() {
        currentFieldName = null;
    }

    private void scalar(Event event) {
        if (structureStack.peekLast() == Event.ID.MappingStart && currentFieldName == null) {
            // directly inside a sequence, and there is no current field name, so this must be it
            currentFieldName = ((ScalarEvent) event).getValue();
            fieldName();
        } else {
            currentFieldName = null;
            scalarValue(event);
        }
    }

    private void fieldName() {
        if (currentProperty == null) {
            currentProperty = properties.get(currentFieldName);
        }
        // else in a nested mapping or sequence that's being obfuscated; do nothing
    }

    private void scalarValue(Event event) {
        if (currentProperty != null && depth == 0) {
            appendUntilEvent(event);
            obfuscateEvent(event, currentProperty.obfuscator);
        }
        // else not obfuscating, or in a nested mapping or or sequence that's being obfuscated; do nothing
    }

    private void appendUntilEvent(Event event) {
        int eventStartIndex = startIndex(event);
        try {
            destination.append(text, textIndex, eventStartIndex);
            textIndex = eventStartIndex;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void obfuscateUntilEvent(Event startEvent, Event endEvent, Obfuscator obfuscator) {
        int startEventStartIndex = startIndex(startEvent);
        int eventEndIndex = endIndex(endEvent);
        // don't include any trailing white space
        eventEndIndex = skipTrailingWhitespace(text, startEventStartIndex, eventEndIndex);

        try {
            obfuscator.obfuscateText(text, startEventStartIndex, eventEndIndex, destination);
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
                destination.append(text.charAt(eventStartIndex));
                obfuscator.obfuscateText(text, eventStartIndex + 1, eventEndIndex - 1, destination);
                destination.append(text.charAt(eventEndIndex - 1));
            } else {
                obfuscator.obfuscateText(text, eventStartIndex, eventEndIndex, destination);
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
            newStartIndex = skipLeadingWhitespace(text, newStartIndex, eventEndIndex);
            destination.append(text, eventStartIndex, newStartIndex);
            return newStartIndex;
        }
        return eventStartIndex;
    }

    private int startIndex(Event event) {
        return textOffset + event.getStartMark()
                .orElseThrow(() -> new IllegalStateException(Messages.YAMLObfuscator.markNotAvailable.get()))
                .getIndex();
    }

    private int endIndex(Event event) {
        return textOffset + event.getEndMark()
                .orElseThrow(() -> new IllegalStateException(Messages.YAMLObfuscator.markNotAvailable.get()))
                .getIndex();
    }

    void appendRemainder() throws IOException {
        int end = textEnd == -1 ? text.length() : textEnd;
        destination.append(text, textIndex, end);
        textIndex = end;
    }
}
