/*
 * Copyright 2013-2020 The OpenZipkin Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package brave.propagation;

import brave.Tracing;
import brave.internal.Nullable;
import brave.propagation.TraceContext.Extractor;
import brave.propagation.TraceContext.Injector;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static brave.propagation.CorrelationFields.validateName;

/**
 * This implements {@linkplain ExtraField extra field} in-process and remote propagation.
 *
 * <p>For example, if you have a need to know the a specific request's country code, you can
 * propagate it through the trace as HTTP headers.
 * <pre>{@code
 * // Configure your extra field
 * countryCode = ExtraField.create("country-code");
 *
 * // When you initialize the builder, add the extra field you want to propagate
 * tracingBuilder.propagationFactory(
 *   ExtraFieldPropagation.newFactoryBuilder(B3Propagation.FACTORY)
 *                        .addField(countryCode)
 *                        .build()
 * );
 */
public class ExtraFieldPropagation<K> implements Propagation<K> {
  /** @deprecated Since 5.11 please always use {@link #newFactoryBuilder(Propagation.Factory)) */
  @Deprecated public static Factory newFactory(Propagation.Factory delegate, String... names) {
    if (delegate == null) throw new NullPointerException("delegate == null");
    if (names == null) throw new NullPointerException("field names == null");
    return newFactory(delegate, Arrays.asList(names));
  }

  /** @deprecated Since 5.11 please always use {@link #newFactoryBuilder(Propagation.Factory)) */
  @Deprecated
  public static Factory newFactory(Propagation.Factory delegate, Collection<String> names) {
    if (delegate == null) throw new NullPointerException("delegate == null");
    if (names == null) throw new NullPointerException("field names == null");
    if (names.isEmpty()) throw new IllegalArgumentException("no field names");
    FactoryBuilder builder = new FactoryBuilder(delegate);
    for (String name : names) builder.addField(ExtraField.create(name));
    return builder.build();
  }

  /** Wraps an underlying propagation implementation, pushing one or more fields. */
  public static FactoryBuilder newFactoryBuilder(Propagation.Factory delegate) {
    return new FactoryBuilder(delegate);
  }

  public static final class FactoryBuilder {
    final Propagation.Factory delegate;
    final Set<String> names = new LinkedHashSet<>();
    final Set<String> redactedNames = new LinkedHashSet<>();
    final Map<String, Set<String>> nameToPrefixes = new LinkedHashMap<>();

    final Set<ExtraField> fields = new LinkedHashSet<>();

    FactoryBuilder(Propagation.Factory delegate) {
      if (delegate == null) throw new NullPointerException("delegate == null");
      this.delegate = delegate;
    }

    /**
     * Adds an {@linkplain ExtraField extra field} for remote propagation.
     *
     * @since 5.11
     */
    public FactoryBuilder addField(ExtraField field) {
      if (field == null) throw new NullPointerException("field == null");
      fields.add(field);
      return this;
    }

    /** @deprecated Since 5.11 please always use {@link #addField(ExtraField)) */
    public FactoryBuilder addRedactedField(String name) {
      name = validateName(name).toLowerCase(Locale.ROOT);
      names.add(name);
      redactedNames.add(name);
      return this;
    }

    /** @deprecated Since 5.11 please always use {@link #addField(ExtraField)) */
    public FactoryBuilder addField(String name) {
      names.add(validateName(name).toLowerCase(Locale.ROOT));
      return this;
    }

    /** @deprecated Since 5.11 please always use {@link #addField(ExtraField)) */
    public FactoryBuilder addPrefixedFields(String prefix, Collection<String> names) {
      if (prefix == null) throw new NullPointerException("prefix == null");
      if (prefix.isEmpty()) throw new IllegalArgumentException("prefix is empty");
      if (names == null) throw new NullPointerException("names == null");
      for (String name : names) {
        name = validateName(name).toLowerCase(Locale.ROOT);
        Set<String> prefixes = nameToPrefixes.get(name);
        if (prefixes == null) nameToPrefixes.put(name, prefixes = new LinkedHashSet<>());
        prefixes.add(prefix);
      }
      return this;
    }

    Set<ExtraField> convertDeprecated() {
      Set<String> remainingNames = new LinkedHashSet<>(names);
      Set<ExtraField> result = new LinkedHashSet<>();
      for (Map.Entry<String, Set<String>> entry : nameToPrefixes.entrySet()) {
        String name = entry.getKey();
        ExtraField.Builder builder = ExtraField.newBuilder(name);
        if (redactedNames.contains(name)) builder.redacted();
        if (!remainingNames.remove(name)) builder.clearKeys();
        for (String prefix : entry.getValue()) {
          builder.addKey(prefix + name);
        }
        result.add(builder.build());
      }

      for (String name : remainingNames) {
        ExtraField.Builder builder = ExtraField.newBuilder(name);
        if (redactedNames.contains(name)) builder.redacted();
        result.add(builder.build());
      }
      return result;
    }

    /** Returns a wrapper of the delegate if there are no fields to propagate. */
    public Factory build() {
      Set<ExtraField> fields = convertDeprecated();
      fields.addAll(this.fields); // clobbering deprecated config is ok

      if (fields.isEmpty()) return new Factory(delegate);
      return new RealFactory(delegate, fields.toArray(new ExtraField[0]));
    }
  }

  /** @deprecated Since 5.11 use {@link ExtraField#getValue(String)} */
  @Deprecated @Nullable public static String current(String name) {
    return ExtraField.getValue(name);
  }

  /** @deprecated Since 5.11 use {@link ExtraField#getValue(TraceContext, String)} */
  @Deprecated @Nullable public static String get(TraceContext context, String name) {
    return ExtraField.getValue(context, name);
  }

  /** @deprecated Since 5.11 use {@link ExtraField#getValue(String)} */
  @Deprecated @Nullable public static String get(String name) {
    return ExtraField.getValue(name);
  }

  /** @deprecated Since 5.11 use {@link ExtraField#setValue(TraceContext, String, String)} */
  @Deprecated public static void set(TraceContext context, String name, String value) {
    ExtraField.setValue(context, name, value);
  }

  /** @deprecated Since 5.11 use {@link ExtraField#setValue(String, String)} */
  @Deprecated public static void set(String name, String value) {
    ExtraField.setValue(name, value);
  }

  /**
   * Returns a mapping of fields in the current trace context, or empty if there are none.
   *
   * <p>Prefer {@link #set(TraceContext, String, String)} if you have a reference to the trace
   * context.
   *
   * @see ExtraField#name()
   */
  public static Map<String, String> getAll() {
    TraceContext context = currentTraceContext();
    if (context == null) return Collections.emptyMap();
    return getAll(context);
  }

  /** Like {@link #getAll()} except extracts the input instead of the current trace context. */
  public static Map<String, String> getAll(TraceContextOrSamplingFlags extracted) {
    if (extracted == null) throw new NullPointerException("extracted == null");
    TraceContext extractedContext = extracted.context();
    if (extractedContext != null) return getAll(extractedContext);
    ExtraFields fields = TraceContext.findExtra(ExtraFields.class, extracted.extra());
    return fields != null ? fields.toMap() : Collections.emptyMap();
  }

  /** Like {@link #getAll()} except extracts the input instead of the current trace context. */
  public static Map<String, String> getAll(TraceContext context) {
    if (context == null) throw new NullPointerException("context == null");
    ExtraFields fields = context.findExtra(ExtraFields.class);
    return fields != null ? fields.toMap() : Collections.emptyMap();
  }

  @Nullable static TraceContext currentTraceContext() {
    Tracing tracing = Tracing.current();
    return tracing != null ? tracing.currentTraceContext().get() : null;
  }

  public static class Factory extends Propagation.Factory {
    final Propagation.Factory delegate;

    Factory(Propagation.Factory delegate) {
      this.delegate = delegate;
    }

    @Override public <K> ExtraFieldPropagation<K> create(Propagation.KeyFactory<K> keyFactory) {
      return new ExtraFieldPropagation<>(delegate, keyFactory);
    }

    @Override public boolean supportsJoin() {
      return delegate.supportsJoin();
    }

    @Override public boolean requires128BitTraceId() {
      return delegate.requires128BitTraceId();
    }

    @Override public TraceContext decorate(TraceContext context) {
      return delegate.decorate(context);
    }
  }

  static class ExtraFieldWithKeys<K> {
    final ExtraField field;
    /** Corresponds to {@link ExtraField#keys()} */
    final K[] keys;

    ExtraFieldWithKeys(ExtraField field, K[] keys) {
      this.field = field;
      this.keys = keys;
    }
  }

  static final class RealFactory extends Factory {
    final ExtraFields.Factory extraFactory;

    RealFactory(Propagation.Factory delegate, ExtraField[] fields) {
      super(delegate);
      this.extraFactory = new ExtraFields.Factory(fields);
    }

    @Override
    public final <K> ExtraFieldPropagation<K> create(Propagation.KeyFactory<K> keyFactory) {
      int i = 0;
      List<K> allKeys = new ArrayList<>();
      ExtraFieldWithKeys<K>[] fieldsWithKeys = new ExtraFieldWithKeys[extraFactory.fields.length];
      for (ExtraField field : extraFactory.fields) {
        K[] keysForField = (K[]) new Object[field.keys.length];
        for (int j = 0, length = field.keys.length; j < length; j++) {
          keysForField[j] = keyFactory.create(field.keys[j]);
          allKeys.add(keysForField[j]);
        }
        fieldsWithKeys[i++] = new ExtraFieldWithKeys<>(field, keysForField);
      }
      return new RealExtraFieldPropagation<>(this, keyFactory, fieldsWithKeys, allKeys);
    }

    @Override public TraceContext decorate(TraceContext context) {
      TraceContext result = delegate.decorate(context);
      return extraFactory.decorate(result);
    }
  }

  final Propagation<K> delegate;

  ExtraFieldPropagation(Propagation.Factory factory, Propagation.KeyFactory<K> keyFactory) {
    this.delegate = factory.create(keyFactory);
  }

  /**
   * Returns the extra keys this component can extract. This result is lowercase and does not
   * include any {@link #keys() trace context keys}.
   */
  // This is here to support extraction from carriers missing a get field by name function. The only
  // known example is OpenTracing TextMap https://github.com/opentracing/opentracing-java/issues/305
  public List<K> extraKeys() {
    return Collections.emptyList();
  }

  /**
   * Only returns trace context keys. Extra field names are not returned to ensure tools don't
   * delete them. This is to support users accessing extra fields without Brave apis (ex via
   * headers).
   */
  @Override public List<K> keys() {
    return delegate.keys();
  }

  @Override public <C> Injector<C> injector(Setter<C, K> setter) {
    return delegate.injector(setter);
  }

  @Override public <C> Extractor<C> extractor(Getter<C, K> getter) {
    return delegate.extractor(getter);
  }

  static final class RealExtraFieldPropagation<K> extends ExtraFieldPropagation<K> {
    final RealFactory factory;
    final ExtraFieldWithKeys<K>[] fieldsWithKeys;
    final List<K> allKeys;

    RealExtraFieldPropagation(
      RealFactory factory, Propagation.KeyFactory<K> keyFactory,
      ExtraFieldWithKeys<K>[] fieldsWithKeys, List<K> allKeys) {
      super(factory.delegate, keyFactory);
      this.factory = factory;
      this.fieldsWithKeys = fieldsWithKeys;
      this.allKeys = Collections.unmodifiableList(allKeys);
    }

    @Override public List<K> extraKeys() {
      return allKeys;
    }

    @Override public <C> Injector<C> injector(Setter<C, K> setter) {
      return new ExtraFieldInjector<>(this, setter);
    }

    @Override public <C> Extractor<C> extractor(Getter<C, K> getter) {
      return new ExtraFieldExtractor<>(this, getter);
    }
  }

  static final class ExtraFieldInjector<C, K> implements Injector<C> {
    final RealExtraFieldPropagation<K> propagation;
    final Injector<C> delegate;
    final Propagation.Setter<C, K> setter;

    ExtraFieldInjector(RealExtraFieldPropagation<K> propagation, Setter<C, K> setter) {
      this.propagation = propagation;
      this.delegate = propagation.delegate.injector(setter);
      this.setter = setter;
    }

    @Override public void inject(TraceContext traceContext, C carrier) {
      delegate.inject(traceContext, carrier);
      ExtraFields extra = traceContext.findExtra(ExtraFields.class);
      if (extra == null) return;
      inject(extra, carrier);
    }

    void inject(ExtraFields fields, C carrier) {
      for (ExtraFieldWithKeys<K> fieldWithKeys : propagation.fieldsWithKeys) {
        if (fieldWithKeys.field.redacted) continue; // don't propagate downstream
        String maybeValue = fields.get(fieldWithKeys.field);
        if (maybeValue == null) continue;
        for (K key : fieldWithKeys.keys) setter.put(carrier, key, maybeValue);
      }
    }
  }

  static final class ExtraFieldExtractor<C, K> implements Extractor<C> {
    final RealExtraFieldPropagation<K> propagation;
    final Extractor<C> delegate;
    final Propagation.Getter<C, K> getter;

    ExtraFieldExtractor(RealExtraFieldPropagation<K> propagation, Getter<C, K> getter) {
      this.propagation = propagation;
      this.delegate = propagation.delegate.extractor(getter);
      this.getter = getter;
    }

    @Override public TraceContextOrSamplingFlags extract(C carrier) {
      TraceContextOrSamplingFlags result = delegate.extract(carrier);

      // always allocate in case values are added late
      ExtraFields fields = propagation.factory.extraFactory.create();
      for (ExtraFieldWithKeys<K> fieldWithKeys : propagation.fieldsWithKeys) {
        for (K key : fieldWithKeys.keys) { // possibly multiple keys when prefixes are in use
          String maybeValue = getter.get(carrier, key);
          if (maybeValue != null) { // accept the first match
            fields.put(fieldWithKeys.field, maybeValue);
            break;
          }
        }
      }

      return result.toBuilder().addExtra(fields).build();
    }
  }
}
