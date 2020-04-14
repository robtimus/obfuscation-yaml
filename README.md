# obfuscation-yaml

Provides functionality for obfuscating YAML documents. This can be useful for logging such documents, where sensitive content should not be logged as-is.

To create a YAML obfuscator, simply create a builder, add properties to it, and let it build the final obfuscator:

    Obfuscator obfuscator = YAMLObfuscator.builder()
            .withProperty("password", Obfuscator.fixedLength(3))
            .build();

## Disabling obfuscation for mappings and/or sequences

By default, a JSON obfuscator will obfuscate all properties; for mapping and sequence properties, their contents in the document including opening and closing characters will be obfuscated. This can be turned and or off for all properties, or per property. For example:

    Obfuscator obfuscator = JSONObfuscator.builder()
            .scalarsOnlyByDefault()
            .withProperty("password", Obfuscator.fixedLength(3))
            .withProperty("complex", Obfuscator.fixedLength(3))
                    .includeMappings() // override the default setting
            .build();

## Handling malformed YAML

If malformed YAML is encountered, obfuscation aborts. It will add a message to the result indicating that obfuscation was aborted. This message can be changed or turned off when creating YAML obfuscators:

    Obfuscator obfuscator = YAMLObfuscator.builder()
            .withProperty("password", Obfuscator.fixedLength(3))
            // use null to turn it off
            .withMalformedYAMLWarning("<invalid YAML>")
            .build();
