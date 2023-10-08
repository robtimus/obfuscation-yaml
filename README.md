# obfuscation-yaml
[![Maven Central](https://img.shields.io/maven-central/v/com.github.robtimus/obfuscation-yaml)](https://search.maven.org/artifact/com.github.robtimus/obfuscation-yaml)
[![Build Status](https://github.com/robtimus/obfuscation-yaml/actions/workflows/build.yml/badge.svg)](https://github.com/robtimus/obfuscation-yaml/actions/workflows/build.yml)
[![Quality Gate Status](https://sonarcloud.io/api/project_badges/measure?project=com.github.robtimus%3Aobfuscation-yaml&metric=alert_status)](https://sonarcloud.io/summary/overall?id=com.github.robtimus%3Aobfuscation-yaml)
[![Coverage](https://sonarcloud.io/api/project_badges/measure?project=com.github.robtimus%3Aobfuscation-yaml&metric=coverage)](https://sonarcloud.io/summary/overall?id=com.github.robtimus%3Aobfuscation-yaml)
[![Known Vulnerabilities](https://snyk.io/test/github/robtimus/obfuscation-yaml/badge.svg)](https://snyk.io/test/github/robtimus/obfuscation-yaml)

Provides functionality for obfuscating YAML documents. This can be useful for logging such documents, where sensitive content should not be logged as-is.

To create a YAML obfuscator, simply create a builder, add properties to it, and let it build the final obfuscator:

    Obfuscator obfuscator = YAMLObfuscator.builder()
            .withProperty("password", Obfuscator.fixedLength(3))
            .build();

## Obfuscation for mappings and/or sequences

By default, a YAML obfuscator will obfuscate all properties; for mapping and sequence properties, their contents in the document including opening and closing characters will be obfuscated. This can be turned on or off for all properties, or per property. For example:

    Obfuscator obfuscator = JSONObfuscator.builder()
            .scalarsOnlyByDefault()
            // .scalarsOnlyByDefault() is equivalent to:
            // .forMappingsByDefault(ObfuscationMode.EXCLUDE)
            // .forSequencesByDefault(ObfuscationMode.EXCLUDE)
            .withProperty("password", Obfuscator.fixedLength(3))
            .withProperty("complex", Obfuscator.fixedLength(3))
                    .forMappings(ObfuscationMode.OBFUSCATE) // override the default setting
            .withProperty("arrayOfComplex", Obfuscator.fixedLength(3))
                    .forSequences(ObfuscationMode.INHERIT_OVERRIDABLE) // override the default setting
            .build();

The four possible modes for both mappings and sequences are:
* `EXCLUDE`: don't obfuscate nested mappings or sequences, but instead traverse into them.
* `OBFUSCATE`: obfuscate nested mappings and sequences completely (default).
* `INHERIT`: don't obfuscate nested mappings or sequences, but use the obfuscator for all nested scalar properties.
* `INHERIT_OVERRIDABLE`: don't obfuscate nested mappings or sequences, but use the obfuscator for all nested scalar properties. If a nested property has its own obfuscator defined this will be used instead.

## Handling malformed YAML

If malformed YAML is encountered, obfuscation aborts. It will add a message to the result indicating that obfuscation was aborted. This message can be changed or turned off when creating YAML obfuscators:

    Obfuscator obfuscator = YAMLObfuscator.builder()
            .withProperty("password", Obfuscator.fixedLength(3))
            // use null to turn it off
            .withMalformedYAMLWarning("<invalid YAML>")
            .build();
