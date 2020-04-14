/*
 * YAMLObfuscatorTest.java
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

import static com.github.robtimus.obfuscation.Obfuscator.fixedLength;
import static com.github.robtimus.obfuscation.Obfuscator.none;
import static com.github.robtimus.obfuscation.support.CaseSensitivity.CASE_SENSITIVE;
import static com.github.robtimus.obfuscation.yaml.YAMLObfuscator.builder;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.function.Supplier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import com.github.robtimus.obfuscation.Obfuscator;
import com.github.robtimus.obfuscation.yaml.YAMLObfuscator.Builder;

@SuppressWarnings({ "javadoc", "nls" })
@TestInstance(Lifecycle.PER_CLASS)
public class YAMLObfuscatorTest {

    @ParameterizedTest(name = "{1}")
    @MethodSource
    @DisplayName("equals(Object)")
    public void testEquals(Obfuscator obfuscator, Object object, boolean expected) {
        assertEquals(expected, obfuscator.equals(object));
    }

    Arguments[] testEquals() {
        Obfuscator obfuscator = createObfuscator(builder().withProperty("test", none()));
        return new Arguments[] {
                arguments(obfuscator, obfuscator, true),
                arguments(obfuscator, null, false),
                arguments(obfuscator, createObfuscator(builder().withProperty("test", none())), true),
                arguments(obfuscator, createObfuscator(builder().withProperty("test", none(), CASE_SENSITIVE)), true),
                arguments(obfuscator, createObfuscator(builder().withProperty("test", fixedLength(3))), false),
                arguments(obfuscator, createObfuscator(builder().withProperty("test", none()).excludeMappings()), false),
                arguments(obfuscator, createObfuscator(builder().withProperty("test", none()).excludeSequences()), false),
                arguments(obfuscator, builder().build(), false),
                arguments(obfuscator, createObfuscator(builder().withProperty("test", none()).withMalformedYAMLWarning(null)), false),
                arguments(obfuscator, "foo", false),
        };
    }

    @Test
    @DisplayName("hashCode()")
    public void testHashCode() {
        Obfuscator obfuscator = createObfuscator();
        assertEquals(obfuscator.hashCode(), obfuscator.hashCode());
        assertEquals(obfuscator.hashCode(), createObfuscator().hashCode());
    }

    @Nested
    @DisplayName("valid YAML")
    @TestInstance(Lifecycle.PER_CLASS)
    public class ValidYAML {

        @Nested
        @DisplayName("caseSensitiveByDefault()")
        @TestInstance(Lifecycle.PER_CLASS)
        public class ObfuscatingCaseSensitively extends ObfuscatorTest {

            public ObfuscatingCaseSensitively() {
                super("YAMLObfuscator.input.valid.yaml", "YAMLObfuscator.expected.valid.all",
                        () -> createObfuscator(builder().caseSensitiveByDefault()));
            }
        }

        @Nested
        @DisplayName("caseInsensitiveByDefault()")
        @TestInstance(Lifecycle.PER_CLASS)
        public class ObfuscatingCaseInsensitively extends ObfuscatorTest {

            public ObfuscatingCaseInsensitively() {
                super("YAMLObfuscator.input.valid.yaml", "YAMLObfuscator.expected.valid.all",
                        () -> createObfuscatorCaseInsensitive(builder().caseInsensitiveByDefault()));
            }
        }

        @Nested
        @DisplayName("obfuscating all (default)")
        @TestInstance(Lifecycle.PER_CLASS)
        public class ObfuscatingAll extends ObfuscatorTest {

            public ObfuscatingAll() {
                super("YAMLObfuscator.input.valid.yaml", "YAMLObfuscator.expected.valid.all", () -> createObfuscator());
            }
        }

        @Nested
        @DisplayName("obfuscating all, overriding scalars only by default")
        @TestInstance(Lifecycle.PER_CLASS)
        public class ObfuscatingAllOverridden extends ObfuscatorTest {

            public ObfuscatingAllOverridden() {
                super("YAMLObfuscator.input.valid.yaml", "YAMLObfuscator.expected.valid.all",
                        () -> createObfuscatorObfuscatingAll(builder().scalarsOnlyByDefault()));
            }
        }

        @Nested
        @DisplayName("obfuscating scalars only by default")
        @TestInstance(Lifecycle.PER_CLASS)
        public class ObfuscatingScalars extends ObfuscatorTest {

            public ObfuscatingScalars() {
                super("YAMLObfuscator.input.valid.yaml", "YAMLObfuscator.expected.valid.scalar",
                        () -> createObfuscator(builder().scalarsOnlyByDefault()));
            }
        }

        @Nested
        @DisplayName("obfuscating scalars only, overriding all by default")
        @TestInstance(Lifecycle.PER_CLASS)
        public class ObfuscatingScalarsOverridden extends ObfuscatorTest {

            public ObfuscatingScalarsOverridden() {
                super("YAMLObfuscator.input.valid.yaml", "YAMLObfuscator.expected.valid.scalar",
                        () -> createObfuscatorObfuscatingScalarsOnly(builder().allByDefault()));
            }
        }
    }

    @Nested
    @DisplayName("invalid YAML")
    @TestInstance(Lifecycle.PER_CLASS)
    public class InvalidYAML extends ObfuscatorTest {

        public InvalidYAML() {
            super("YAMLObfuscator.input.invalid", "YAMLObfuscator.expected.invalid", () -> createObfuscator());
        }
    }

    @Nested
    @DisplayName("truncated YAML")
    @TestInstance(Lifecycle.PER_CLASS)
    public class TruncatedYAML {

        @Nested
        @DisplayName("with warning")
        public class WithWarning extends TruncatedYAMLTest {

            public WithWarning() {
                super("YAMLObfuscator.expected.truncated", true);
            }
        }

        @Nested
        @DisplayName("without warning")
        public class WithoutWarning extends TruncatedYAMLTest {

            public WithoutWarning() {
                super("YAMLObfuscator.expected.truncated.no-warning", false);
            }
        }

        private class TruncatedYAMLTest extends ObfuscatorTest {

            protected TruncatedYAMLTest(String expectedResource, boolean includeWarning) {
                super("YAMLObfuscator.input.truncated", expectedResource, () -> createObfuscator(includeWarning));
            }
        }
    }

    private static class ObfuscatorTest {

        private final String input;
        private final String expected;
        private final Supplier<Obfuscator> obfuscatorSupplier;

        protected ObfuscatorTest(String inputResource, String expectedResource, Supplier<Obfuscator> obfuscatorSupplier) {
            this.input = readResource(inputResource);
            this.expected = readResource(expectedResource);
            this.obfuscatorSupplier = obfuscatorSupplier;
        }

        @Test
        @DisplayName("obfuscateText(CharSequence, int, int)")
        public void testObfuscateTextCharSequence() {
            Obfuscator obfuscator = obfuscatorSupplier.get();

            assertEquals(expected, obfuscator.obfuscateText("x" + input + "x", 1, 1 + input.length()).toString());
        }

        @Test
        @DisplayName("obfuscateText(CharSequence, int, int, Appendable)")
        public void testObfuscateTextCharSequenceToAppendable() throws IOException {
            Obfuscator obfuscator = obfuscatorSupplier.get();

            StringBuilder destination = new StringBuilder();
            obfuscator.obfuscateText("x" + input + "x", 1, 1 + input.length(), (Appendable) destination);
            assertEquals(expected, destination.toString());
        }

        @Test
        @DisplayName("obfuscateText(Reader, Appendable)")
        public void testObfuscateTextReaderToAppendable() throws IOException {
            Obfuscator obfuscator = obfuscatorSupplier.get();

            StringBuilder destination = new StringBuilder();
            obfuscator.obfuscateText(new StringReader(input), destination);
            assertEquals(expected, destination.toString());

            destination.delete(0, destination.length());
            obfuscator.obfuscateText(new BufferedReader(new StringReader(input)), destination);
            assertEquals(expected, destination.toString());
        }

        @Test
        @DisplayName("streamTo(Appendable")
        public void testStreamTo() throws IOException {
            Obfuscator obfuscator = obfuscatorSupplier.get();

            Writer writer = new StringWriter();
            try (Writer w = obfuscator.streamTo(writer)) {
                int index = 0;
                while (index < input.length()) {
                    int to = Math.min(index + 5, input.length());
                    w.write(input, index, to - index);
                    index = to;
                }
            }
            assertEquals(expected, writer.toString());
        }
    }

    private static Obfuscator createObfuscator() {
        return builder()
                .transform(YAMLObfuscatorTest::createObfuscator);
    }

    private static Obfuscator createObfuscator(boolean includeWarning) {
        Builder builder = builder();
        if (!includeWarning) {
            builder = builder.withMalformedYAMLWarning(null);
        }
        return builder.transform(YAMLObfuscatorTest::createObfuscator);
    }

    private static Obfuscator createObfuscator(Builder builder) {
        Obfuscator obfuscator = fixedLength(3);
        return builder
                .withProperty("string", obfuscator)
                .withProperty("int", obfuscator)
                .withProperty("float", obfuscator)
                .withProperty("boolean", obfuscator)
                .withProperty("mapping", obfuscator)
                .withProperty("flowMapping", obfuscator)
                .withProperty("sequence", obfuscator)
                .withProperty("flowSequence", obfuscator)
                .withProperty("null", obfuscator)
                .withProperty("anchor", obfuscator)
                .withProperty("alias", obfuscator)
                .withProperty("notObfuscated", none())
                .build();
    }

    private static Obfuscator createObfuscatorCaseInsensitive(Builder builder) {
        Obfuscator obfuscator = fixedLength(3);
        return builder
                .withProperty("STRING", obfuscator)
                .withProperty("INT", obfuscator)
                .withProperty("FLOAT", obfuscator)
                .withProperty("BOOLEAN", obfuscator)
                .withProperty("MAPPING", obfuscator)
                .withProperty("FLOWMAPPING", obfuscator)
                .withProperty("SEQUENCE", obfuscator)
                .withProperty("FLOWSEQUENCE", obfuscator)
                .withProperty("NULL", obfuscator)
                .withProperty("ANCHOR", obfuscator)
                .withProperty("ALIAS", obfuscator)
                .withProperty("NOTOBFUSCATED", none())
                .build();
    }

    private static Obfuscator createObfuscatorObfuscatingAll(Builder builder) {
        Obfuscator obfuscator = fixedLength(3);
        return builder
                .withProperty("string", obfuscator).all()
                .withProperty("int", obfuscator).all()
                .withProperty("float", obfuscator).all()
                .withProperty("boolean", obfuscator).all()
                .withProperty("mapping", obfuscator).all()
                .withProperty("flowMapping", obfuscator).all()
                .withProperty("sequence", obfuscator).all()
                .withProperty("flowSequence", obfuscator).all()
                .withProperty("null", obfuscator).all()
                .withProperty("anchor", obfuscator).all()
                .withProperty("alias", obfuscator).all()
                .withProperty("notObfuscated", none()).all()
                .build();
    }

    private static Obfuscator createObfuscatorObfuscatingScalarsOnly(Builder builder) {
        Obfuscator obfuscator = fixedLength(3);
        return builder
                .withProperty("string", obfuscator).scalarsOnly()
                .withProperty("int", obfuscator).scalarsOnly()
                .withProperty("float", obfuscator).scalarsOnly()
                .withProperty("boolean", obfuscator).scalarsOnly()
                .withProperty("mapping", obfuscator).scalarsOnly()
                .withProperty("flowMapping", obfuscator).scalarsOnly()
                .withProperty("sequence", obfuscator).scalarsOnly()
                .withProperty("flowSequence", obfuscator).scalarsOnly()
                .withProperty("null", obfuscator).scalarsOnly()
                .withProperty("anchor", obfuscator).scalarsOnly()
                .withProperty("alias", obfuscator).scalarsOnly()
                .withProperty("notObfuscated", none()).scalarsOnly()
                .build();
    }

    private static String readResource(String name) {
        StringBuilder sb = new StringBuilder();
        try (Reader input = new InputStreamReader(YAMLObfuscatorTest.class.getResourceAsStream(name), StandardCharsets.UTF_8)) {
            char[] buffer = new char[4096];
            int len;
            while ((len = input.read(buffer)) != -1) {
                sb.append(buffer, 0, len);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return sb.toString();
    }
}
