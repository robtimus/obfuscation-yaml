# Migrating from version 1.x to 2.0

## YAMLObfuscator.Builder

`YAMLObfuscator.Builder` is no longer an interface but instead a final class. If you are creating mocks or implementing it directly you need to use actual instances created through `YAMLObfucsator.builder()`.

### withProperty

`YAMLObfuscator.Builder.withProperty` no longer returns a `PropertyConfigurer`. Instead it is overloaded to take a `Consumer<PropertyConfigurer>`. If you called any `PropertyConfigurer` methods you need to provide a lambda instead. For example:

```java
/* old:
YAMLObfuscator.builder()
        .withProperty("foo", obfuscator)
                .forMappings(ObfuscationMode.INHERIT)
                .forSequences(ObfuscationMode.INHERIT)
 */
YAMLObfuscator.builder()
        .withProperty("foo", obfuscator, property -> property
                .forMappings(ObfuscationMode.INHERIT)
                .forSequences(ObfuscationMode.INHERIT))
```

#### Case sensitivity

`YAMLObfuscator.Builder.withProperty` no longer accepts a `CaseSensitivity` argument. You need to use new `PropertyConfigurer` methods `caseSensitive()` and `caseInsensitive()` instead. For example:

```java
/* old:
YAMLObfuscator.builder()
        .withProperty("foo", obfuscator, CaseSensitivity.CASE_INSENSITIVE)
 */
YAMLObfuscator.builder()
        .withProperty("foo", obfuscator, PropertyConfigurer::caseInsensitive)
```

### scalarsOnlyByDefault, excludeMappingsByDefault, excludeSequencesByDefault, all

`YAMLObfuscator.scalarsOnlyByDefault`, `YAMLObfuscator.excludeMappingsByDefault`, `YAMLObfuscator.excludeSequencesByDefault` and `YAMLObfuscator.allByDefault` have been removed. You need to use new method `withValueTypesByDefault` instead. For example:

```java
/*
YAMLObfuscator.builder()
        .scalarsOnlyByDefault()
 */
YAMLObfuscator.builder()
        .withValueTypesByDefault(ValueType.SCALAR)
```

```java
/*
YAMLObfuscator.builder()
        .excludeMappingsByDefault()
 */
YAMLObfuscator.builder()
        .withValueTypesByDefault(ValueType.SCALAR, ValueType.SEQUENCE)
```

```java
/*
YAMLObfuscator.builder()
        .excludeSequencesByDefault()
 */
YAMLObfuscator.builder()
        .withValueTypesByDefault(ValueType.SCALAR, ValueType.MAPPING)
```

```java
/*
YAMLObfuscator.builder()
        .excludeMappingsByDefault()
        .excludeSequencesByDefault()
 */
YAMLObfuscator.builder()
        .withValueTypesByDefault(ValueType.SCALAR)
```

```java
/*
YAMLObfuscator.builder()
        .allByDefault()
 */
YAMLObfuscator.builder()
        .withValueTypesByDefault(ValueType.ALL)
```

### includeMappingsByDefault, includeSequencesByDefault

`YAMLObfuscator.includeMappingsByDefault` and `YAMLObfuscator.includeSequencesByDefault` have been removed. You need to combine methods `forMappingsByDefault` and/or `forSequencesByDefault` with new method `withValueTypesByDefault` instead. For example:

```java
/*
YAMLObfuscator.builder()
        .includeMappingsByDefault()
 */
YAMLObfuscator.builder()
        .withValueTypesByDefault(ValueType.SCALAR, ValueType.MAPPING)
        // or .withValueTypesByDefault(ValueType.ALL) to also include sequences
        .forMappingsByDefault(ObfuscationMode.OBFUSCATE)
```

```java
/*
YAMLObfuscator.builder()
        .includeSequencesByDefault()
 */
YAMLObfuscator.builder()
        .withValueTypesByDefault(ValueType.SCALAR, ValueType.SEQUENCE)
        // or .withValueTypesByDefault(ValueType.ALL) to also include mappings
        .forSequencesByDefault(ObfuscationMode.OBFUSCATE)
```

```java
/*
YAMLObfuscator.builder()
        .includeMappingsByDefault()
        .includeSequencesByDefault()
 */
YAMLObfuscator.builder()
        .withValueTypesByDefault(ValueType.ALL)
        .forSequencesByDefault(ObfuscationMode.OBFUSCATE)
```

Note that the defaults already use `ValueType.ALL` and `ObfuscationMode.OBFUSCATE` for both mappings and sequences.

### limitTo

`YAMLObfuscator.Builder.limitTo` no longer returns a `LimitConfigurer`. Instead it is overloaded to take a `Consumer<LimitConfigurer>`. If you called any `LimitConfigurer` methods you need to provide a lambda instead. For example:

```java
/* old:
YAMLObfuscator.builder()
        .limitTo(1024)
                .withTruncatedIndicator("<truncated>")
 */
YAMLObfuscator.builder()
        .limitTo(1024, limit -> limit
                .withTruncatedIndicator("<truncated>"))
```

## YAMLObfuscator.PropertyConfigurer

`YAMLObfuscator.PropertyConfigurer` is no longer an interface but instead a final class. If you are creating mocks or implementing it directly you need to use actual instances passed to the `Consumer` argument of `YAMLObfuscator.Builder.withProperty`.

### scalarsOnly, excludeMappings, excludeSequences, all

`YAMLObfuscator.PropertyConfigurer.scalarsOnly`, `YAMLObfuscator.PropertyConfigurer.excludeMappings`, `YAMLObfuscator.PropertyConfigurer.excludeSequences` and `YAMLObfuscator.all` have been removed. You need to use new method `withValueTypes` instead. For example:

```java
/*
YAMLObfuscator.builder()
        .withProperty("foo", obfuscator)
                .scalarsOnlyByDefault()
 */
YAMLObfuscator.builder()
        .withProperty("foo", obfuscator, property -> property
                .withValueTypes(ValueType.SCALAR))
```

```java
/*
YAMLObfuscator.builder()
        .withProperty("foo", obfuscator)
                .excludeMappings()
 */
YAMLObfuscator.builder()
        .withProperty("foo", obfuscator, property -> property
                .withValueTypes(ValueType.SCALAR, ValueType.SEQUENCE))
```

```java
/*
YAMLObfuscator.builder()
        .withProperty("foo", obfuscator)
                .excludeSequences()
 */
YAMLObfuscator.builder()
        .withProperty("foo", obfuscator, property -> property
                .withValueTypes(ValueType.SCALAR, ValueType.MAPPING))
```

```java
/*
YAMLObfuscator.builder()
        .withProperty("foo", obfuscator)
                .excludeMappings()
                .excludeSequences()
 */
YAMLObfuscator.builder()
        .withProperty("foo", obfuscator, property -> property
                .withValueTypes(ValueType.SCALAR))
```

```java
/*
YAMLObfuscator.builder()
        .withProperty("foo", obfuscator)
               .all()
 */
YAMLObfuscator.builder()
        .withProperty("foo", obfuscator, property -> property
                .withValueTypes(ValueType.ALL))
```

### includeMappings, includeSequences

`YAMLObfuscator.PropertyConfigurer.includeMappings` and `YAMLObfuscator.PropertyConfigurer.includeSequences` have been removed. You need to combine methods `forMappings` and/or `forSequences` with new method `withValueTypes` instead. For example:

```java
/*
YAMLObfuscator.builder()
        .withProperty("foo", obfuscator)
                .includeMappings())
 */
YAMLObfuscator.builder()
        .withProperty("foo", obfuscator, property -> property
                .withValueTypes(ValueType.SCALAR, ValueType.MAPPING)
                // or .withValueTypes(ValueType.ALL) to also include sequences
                .forMappings(ObfuscationMode.OBFUSCATE))
```

```java
/*
YAMLObfuscator.builder()
        .withProperty("foo", obfuscator)
                .includeSequences()
 */
YAMLObfuscator.builder()
        .withProperty("foo", obfuscator, property -> property
                .withValueTypes(ValueType.SCALAR, ValueType.SEQUENCE)
                // or .withValueTypes(ValueType.ALL) to also include mappings
                .forSequences(ObfuscationMode.OBFUSCATE))
```

```java
/*
YAMLObfuscator.builder()
        .withProperty("foo", obfuscator)
                .includeMappings())
                .includeSequences()
 */
YAMLObfuscator.builder()
        .withProperty("foo", obfuscator, property -> property
                .withValueTypes(ValueType.ALL)
                .forSequences(ObfuscationMode.OBFUSCATE))
```

## YAMLObfuscator.LimitConfigurer

`YAMLObfuscator.LimitConfigurer` is no longer an interface but instead a final class. If you are creating mocks or implementing it directly you need to use actual instances passed to the `Consumer` argument of `YAMLObfuscator.Builder.limitTo`.

## YAMLObfuscator.ObfuscationMode

`ObfuscationMode` is no longer nested in `PropertyConfigurer` but directly in `YAMLObfuscator`. You need to replace any occurrence of `YAMLObfuscator.PropertyConfigurer.ObfuscationMode` to `YAMLObfuscator.ObfuscationMode` in import statements, method arguments, etc.

### EXCLUDE

Constant `ObfuscationMode.EXCLUDE` has been removed. You need to use new method `withValueTypesByDefault` and/or `withValueTypes` as documented above instead. For example:

```java
/* old
YAMLObfuscator.builder()
        .forMappingsByDefault(ObfuscationMode.EXCLUDE)
 */
YAMLObfuscator.builder()
        .withValueTypesByDefault(ValueType.SCALAR, ValueType.SEQUENCE)
```

```java
/* old
YAMLObfuscator.builder()
        .forSequencesByDefault(ObfuscationMode.EXCLUDE)
 */
YAMLObfuscator.builder()
        .withValueTypesByDefault(ValueType.SCALAR, ValueType.MAPPING)
```

```java
/* old
YAMLObfuscator.builder()
        .forMappingsByDefault(ObfuscationMode.EXCLUDE)
        .forSequencesByDefault(ObfuscationMode.EXCLUDE)
 */
YAMLObfuscator.builder()
        .withValueTypesByDefault(ValueType.SCALAR)
```

```java
/*
YAMLObfuscator.builder()
        .withProperty("foo", obfuscator)
                .forMappings(ObfuscationMode.EXCLUDE)
 */
YAMLObfuscator.builder()
        .withProperty("foo", obfuscator, property -> property
                .withValueTypes(ValueType.SCALAR, ValueType.SEQUENCE))
```

```java
/*
YAMLObfuscator.builder()
        .withProperty("foo", obfuscator)
                .forSequences(ObfuscationMode.EXCLUDE)
 */
YAMLObfuscator.builder()
        .withProperty("foo", obfuscator, property -> property
                .withValueTypes(ValueType.SCALAR, ValueType.MAPPING))
```

```java
/*
YAMLObfuscator.builder()
        .withProperty("foo", obfuscator)
                .forMappings(ObfuscationMode.EXCLUDE)
                .forSequences(ObfuscationMode.EXCLUDE)
 */
YAMLObfuscator.builder()
        .withProperty("foo", obfuscator, property -> property
                .withValueTypes(ValueType.SCALAR))
```
