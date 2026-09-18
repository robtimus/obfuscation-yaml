# Migrating from version 1.x to 2.0

## Builder

`JSONObfuscator.Builder` is no longer an interface but instead a final class. If you are creating mocks or implementing it directly you need to use actual instances created through `JSONObfucsator.builder()`.

### withProperty

`JSONObfuscator.Builder.withProperty` no longer returns a `PropertyConfigurer`. Instead it is overloaded to take a `Consumer<PropertyConfigurer>`. If you called any `PropertyConfigurer` methods you need to provide a lambda instead. For example:

```java
/* old:
JSONObfuscator.builder()
        .withProperty("foo", obfuscator)
                .forMappings(ObfuscationMode.INHERIT)
                .forSequences(ObfuscationMode.INHERIT)
 */
JSONObfuscator.builder()
        .withProperty("foo", obfuscator, property -> property
                .forMappings(ObfuscationMode.INHERIT)
                .forSequences(ObfuscationMode.INHERIT))
```

#### Case sensitivity

`JSONObfuscator.withProperty` no longer accepts a `CaseSensitivity` argument. You need to use new `PropertyConfigurer` methods `caseSensitive()` and `caseInsensitive()` instead. For example:

```java
/* old:
JSONObfuscator.builder()
        .withProperty("foo", obfuscator, CaseSensitivity.CASE_INSENSITIVE)
 */
JSONObfuscator.builder()
        .withProperty("foo", obfuscator, PropertyConfigurer::caseInsensitive)
```

### scalarsOnlyByDefault, excludeMappingsByDefault, excludeSequencesByDefault, all

`JSONObfuscator.scalarsOnlyByDefault`, `JSONObfuscator.excludeMappingsByDefault`, `JSONObfuscator.excludeSequencesByDefault` and `JSONObfuscator.allByDefault` have been removed. You need to use new method `withValueTypesByDefault` instead. For example:

```java
/*
JSONObfuscator.builder()
        .scalarsOnlyByDefault()
 */
JSONObfuscator.builder()
        .withValueTypesByDefault(ValueType.SCALAR)
```

```java
/*
JSONObfuscator.builder()
        .excludeMappingsByDefault()
 */
JSONObfuscator.builder()
        .withValueTypesByDefault(ValueType.SCALAR, ValueType.SEQUENCE)
```

```java
/*
JSONObfuscator.builder()
        .excludeSequencesByDefault()
 */
JSONObfuscator.builder()
        .withValueTypesByDefault(ValueType.SCALAR, ValueType.MAPPING)
```

```java
/*
JSONObfuscator.builder()
        .excludeMappingsByDefault()
        .excludeSequencesByDefault()
 */
JSONObfuscator.builder()
        .withValueTypesByDefault(ValueType.SCALAR)
```

```java
/*
JSONObfuscator.builder()
        .allByDefault()
 */
JSONObfuscator.builder()
        .withValueTypesByDefault(ValueType.ALL)
```

### includeMappingsByDefault, includeSequencesByDefault

`JSONObfuscator.includeMappingsByDefault` and `JSONObfuscator.includeSequencesByDefault` have been removed. You need to combine methods `forMappingsByDefault` and/or `forSequencesByDefault` with new method `withValueTypesByDefault` instead. For example:

```java
/*
JSONObfuscator.builder()
        .includeMappingsByDefault()
 */
JSONObfuscator.builder()
        .withValueTypesByDefault(ValueType.SCALAR, ValueType.MAPPING)
        // or .withValueTypesByDefault(ValueType.ALL) to also include sequences
        .forMappingsByDefault(ObfuscationMode.OBFUSCATE)
```

```java
/*
JSONObfuscator.builder()
        .includeSequencesByDefault()
 */
JSONObfuscator.builder()
        .withValueTypesByDefault(ValueType.SCALAR, ValueType.SEQUENCE)
        // or .withValueTypesByDefault(ValueType.ALL) to also include mappings
        .forSequencesByDefault(ObfuscationMode.OBFUSCATE)
```

```java
/*
JSONObfuscator.builder()
        .includeMappingsByDefault()
        .includeSequencesByDefault()
 */
JSONObfuscator.builder()
        .withValueTypesByDefault(ValueType.ALL)
        .forSequencesByDefault(ObfuscationMode.OBFUSCATE)
```

Note that the defaults already use `ValueType.ALL` and `ObfuscationMode.OBFUSCATE` for both mappings and sequences.

### limitTo

`JSONObfuscator.Builder.limitTo` no longer returns a `LimitConfigurer`. Instead it is overloaded to take a `Consumer<LimitConfigurer>`. If you called any `LimitConfigurer` methods you need to provide a lambda instead. For example:

```java
/* old:
JSONObfuscator.builder()
        .limitTo(1024)
                .withTruncatedIndicator("<truncated>")
 */
JSONObfuscator.builder()
        .limitTo(1024, limit -> limit
                .withTruncatedIndicator("<truncated>"))
```

## PropertyConfigurer

`JSONObfuscator.PropertyConfigurer` is no longer an interface but instead a final class. If you are creating mocks or implementing it directly you need to use actual instances passed to the `Consumer` argument of `JSONObfuscator.Builder.withProperty`.

### scalarsOnly, excludeMappings, excludeSequences, all

`JSONObfuscator.PropertyConfigurer.scalarsOnly`, `JSONObfuscator.PropertyConfigurer.excludeMappings`, `JSONObfuscator.PropertyConfigurer.excludeSequences` and `JSONObfuscator.all` have been removed. You need to use new method `withValueTypes` instead. For example:

```java
/*
JSONObfuscator.builder()
        .withProperty("foo", obfuscator)
                .scalarsOnlyByDefault()
 */
JSONObfuscator.builder()
        .withProperty("foo", obfuscator, property -> property
                .withValueTypes(ValueType.SCALAR))
```

```java
/*
JSONObfuscator.builder()
        .withProperty("foo", obfuscator)
                .excludeMappings()
 */
JSONObfuscator.builder()
        .withProperty("foo", obfuscator, property -> property
                .withValueTypes(ValueType.SCALAR, ValueType.SEQUENCE))
```

```java
/*
JSONObfuscator.builder()
        .withProperty("foo", obfuscator)
                .excludeSequences()
 */
JSONObfuscator.builder()
        .withProperty("foo", obfuscator, property -> property
                .withValueTypes(ValueType.SCALAR, ValueType.MAPPING))
```

```java
/*
JSONObfuscator.builder()
        .withProperty("foo", obfuscator)
                .excludeMappings()
                .excludeSequences()
 */
JSONObfuscator.builder()
        .withProperty("foo", obfuscator, property -> property
                .withValueTypes(ValueType.SCALAR))
```

```java
/*
JSONObfuscator.builder()
        .withProperty("foo", obfuscator)
               .all()
 */
JSONObfuscator.builder()
        .withProperty("foo", obfuscator, property -> property
                .withValueTypes(ValueType.ALL))
```

### includeMappings, includeSequences

`JSONObfuscator.PropertyConfigurer.includeMappings` and `JSONObfuscator.PropertyConfigurer.includeSequences` have been removed. You need to combine methods `forMappings` and/or `forSequences` with new method `withValueTypes` instead. For example:

```java
/*
JSONObfuscator.builder()
        .withProperty("foo", obfuscator)
                .includeMappings())
 */
JSONObfuscator.builder()
        .withProperty("foo", obfuscator, property -> property
                .withValueTypes(ValueType.SCALAR, ValueType.MAPPING)
                // or .withValueTypes(ValueType.ALL) to also include sequences
                .forMappings(ObfuscationMode.OBFUSCATE))
```

```java
/*
JSONObfuscator.builder()
        .withProperty("foo", obfuscator)
                .includeSequences()
 */
JSONObfuscator.builder()
        .withProperty("foo", obfuscator, property -> property
                .withValueTypes(ValueType.SCALAR, ValueType.SEQUENCE)
                // or .withValueTypes(ValueType.ALL) to also include mappings
                .forSequences(ObfuscationMode.OBFUSCATE))
```

```java
/*
JSONObfuscator.builder()
        .withProperty("foo", obfuscator)
                .includeMappings())
                .includeSequences()
 */
JSONObfuscator.builder()
        .withProperty("foo", obfuscator, property -> property
                .withValueTypes(ValueType.ALL)
                .forSequences(ObfuscationMode.OBFUSCATE))
```

## LimitConfigurer

`JSONObfuscator.LimitConfigurer` is no longer an interface but instead a final class. If you are creating mocks or implementing it directly you need to use actual instances passed to the `Consumer` argument of `JSONObfuscator.Builder.limitTo`.

## ObfuscationMode

Class `ObfuscationMode` is no longer nested in `PropertyConfigurer` but directly in `JSONObfuscator`. You need to replace any occurrence of `JSONObfuscator.PropertyConfigurer.ObfuscationMode` to `JSONObfuscator.ObfuscationMode` in import statements, method arguments, etc.

### EXCLUDE

Constant `ObfuscationMode.EXCLUDE` has been removed. You need to use new method `withValueTypesByDefault` and/or `withValueTypes` as documented above instead. For example:

```java
/* old
JSONObfuscator.builder()
        .forMappingsByDefault(ObfuscationMode.EXCLUDE)
 */
JSONObfuscator.builder()
        .withValueTypesByDefault(ValueType.SCALAR, ValueType.SEQUENCE)
```

```java
/* old
JSONObfuscator.builder()
        .forSequencesByDefault(ObfuscationMode.EXCLUDE)
 */
JSONObfuscator.builder()
        .withValueTypesByDefault(ValueType.SCALAR, ValueType.MAPPING)
```

```java
/* old
JSONObfuscator.builder()
        .forMappingsByDefault(ObfuscationMode.EXCLUDE)
        .forSequencesByDefault(ObfuscationMode.EXCLUDE)
 */
JSONObfuscator.builder()
        .withValueTypesByDefault(ValueType.SCALAR)
```

```java
/*
JSONObfuscator.builder()
        .withProperty("foo", obfuscator)
                .forMappings(ObfuscationMode.EXCLUDE)
 */
JSONObfuscator.builder()
        .withProperty("foo", obfuscator, property -> property
                .withValueTypes(ValueType.SCALAR, ValueType.SEQUENCE))
```

```java
/*
JSONObfuscator.builder()
        .withProperty("foo", obfuscator)
                .forSequences(ObfuscationMode.EXCLUDE)
 */
JSONObfuscator.builder()
        .withProperty("foo", obfuscator, property -> property
                .withValueTypes(ValueType.SCALAR, ValueType.MAPPING))
```

```java
/*
JSONObfuscator.builder()
        .withProperty("foo", obfuscator)
                .forMappings(ObfuscationMode.EXCLUDE)
                .forSequences(ObfuscationMode.EXCLUDE)
 */
JSONObfuscator.builder()
        .withProperty("foo", obfuscator, property -> property
                .withValueTypes(ValueType.SCALAR))
```
