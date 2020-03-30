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

import brave.internal.Nullable;
import brave.internal.PropagationFields;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import static brave.propagation.CorrelationFields.validateName;
import static brave.propagation.ExtraFieldPropagation.currentTraceContext;
import static java.util.Arrays.asList;

/**
 * Defines a request-scoped field, usually but not always analogous to an HTTP header. Fields will
 * be no-op unless {@link ExtraFieldPropagation} is configured.
 *
 * <p>For example, if you have a need to know a specific request's country code, you can
 * propagate it through the trace:
 * <pre>{@code
 * // Configure your extra field
 * countryCode = ExtraField.create("country-code");
 *
 * // If configured and set, you can retrieve it later. All of the below result in the same tag:
 * spanCustomizer.tag(countryCode.name(), countryCode.getValue());
 * spanCustomizer.tag(countryCode.name(), countryCode.getValue(context));
 * spanCustomizer.tag("country-code", ExtraField.getValue("country-code"));
 *
 * // You can also update the value similarly, so that the new value will propagate downstream.
 * countryCode.setValue("FO");
 * countryCode.setValue(context, "FO");
 * ExtraField.setValue("country-code", "FO");
 * ExtraField.setValue(context, "country-code", "FO");
 * }</pre>
 *
 * <h3>Correlation</h3>
 * If you want an extra field to also be available in correlation such as logging contexts, use
 * {@link Builder#withCorrelation()}.
 *
 * <pre>{@code
 * // configure the field, permitting it to be used in correlation contexts
 * amznTraceId = ExtraField.newBuilder("x-amzn-trace-id").withCorrelation().build();
 *
 * // Allow logging patterns like %X{traceId} %X{x-amzn-trace-id}
 * loggingContext = new Log4J2Context();
 *
 * decorator = CorrelationFieldScopeDecorator.newBuilder(loggingContext)
 *                                           .addField(amznTraceId).build();
 *
 * tracingBuilder.propagationFactory(ExtraFieldPropagation.newFactoryBuilder(B3Propagation.FACTORY)
 *                                                        .addField(amznTraceId)
 *                                                        .build())
 *               .currentTraceContext(ThreadLocalCurrentTraceContext.newBuilder()
 *                                                                  .addScopeDecorator(decorator)
 *                                                                  .build())
 * }</pre>
 *
 * <h3>Prefixed fields</h3>
 * <p>You can also prefix fields, if they follow a common pattern. For example, the following will
 * propagate the field "x-vcap-request-id" as-is, but send the fields "country-code" and "user-id"
 * on the wire as "baggage-country-code" and "baggage-user-id" respectively.
 *
 * <pre>{@code
 * requestId = ExtraField.create("x-vcap-request-id");
 * userId = ExtraField.newBuilder("user-id").prefix("baggage-").build();
 * countryCode = ExtraField.newBuilder("country-code").prefix("baggage-").build();
 *
 * // Later, you can call below to affect the country code of the current trace context
 * ExtraField.setValue("country-code", "FO");
 * String countryCode = ExtraField.getValue("country-code");
 * }</pre>
 *
 * <h3>Appropriate usage</h3>
 * It is generally not a good idea to use the tracing system for application logic or critical code
 * such as security context propagation.
 *
 * <p>Brave is an infrastructure library: you will create lock-in if you expose its apis into
 * business code. Prefer exposing your own types for utility functions that use this class as this
 * will insulate you from lock-in.
 *
 * <p>While it may seem convenient, do not use this for security context propagation as it was not
 * designed for this use case. For example, anything placed in here can be accessed by any code in
 * the same classloader!
 *
 * @see ExtraFieldPropagation
 * @see CorrelationFieldScopeDecorator
 */
public class ExtraField {
  /**
   * Creates a field that is referenced the same in-process as it is on the wire. For example, the
   * name "x-vcap-request-id" would be set as-is including the prefix.
   *
   * @param name will be currently lower-cased for remote propagation
   * @since 5.11
   */
  public static ExtraField create(String name) {
    return new Builder(name).build();
  }

  /**
   * Creates a builder for the specified {@linkplain #name()}.
   *
   * @param name will be currently lower-cased for remote propagation
   * @since 5.11
   */
  public static Builder newBuilder(String name) {
    return new Builder(name);
  }

  public Builder toBuilder() {
    return new Builder(this);
  }

  /**
   * Like {@link #getValue(TraceContext)} except looks up the field by {@linkplain #name()}.
   *
   * <p>Prefer using {@link ExtraField#getValue(TraceContext)} when you have a reference to the
   * underlying field.
   */
  @Nullable public static String getValue(TraceContext context, String name) {
    return PropagationFields.get(context, ExtraField.create(name), ExtraFields.class);
  }

  /**
   * Like {@link #getValue(TraceContext, String)} except against the current trace context.
   *
   * <p>Prefer using {@link #getValue()} when you have a reference to the underlying field.
   * <p>Prefer {@link #getValue(TraceContext, String)} if you have a reference to the trace
   * context.
   *
   * @see ExtraField#name()
   */
  @Nullable public static String getValue(String name) {
    TraceContext context = currentTraceContext();
    return context != null ? getValue(context, name) : null;
  }

  /**
   * Like {@link #setValue(TraceContext, String)} except looks up the field by {@linkplain
   * #name()}.
   *
   * <p>Prefer using {@link #setValue(TraceContext, String)} when you have a reference to the
   * underlying field.
   */
  public static void setValue(TraceContext context, String name, String value) {
    PropagationFields.put(context, ExtraField.create(name), value, ExtraFields.class);
  }

  /**
   * Like {@link #setValue(TraceContext, String, String)} except against the current trace context.
   *
   * <p>Prefer using {@link ExtraField#setValue(String)} when you have a reference to the
   * underlying field.
   * <p>Prefer {@link #setValue(TraceContext, String, String)} if you have a reference to the trace
   * context.
   *
   * @see ExtraField#name()
   */
  public static void setValue(String name, String value) {
    TraceContext context = currentTraceContext();
    if (context != null) setValue(context, name, value);
  }

  /** @since 5.11 */
  public static class Builder {
    final String name;
    final Set<String> keys = new LinkedHashSet<>();
    boolean redacted;

    Builder(String name) {
      this.name = validateName(name);
      keys.add(this.name.toLowerCase(Locale.ROOT));
    }

    Builder(Builder builder) {
      this.name = builder.name;
      this.keys.addAll(builder.keys);
      this.redacted = builder.redacted;
    }

    Builder(ExtraField extraField) {
      this.name = extraField.name;
      this.keys.addAll(asList(extraField.keys));
      this.redacted = extraField.redacted;
    }

    /**
     * Invoke this to clear propagated names of this field. You can add alternatives later with
     * {@link #addKey(String)}. <p>The default propagated name is the lowercase variant of the field
     * name.
     *
     * <p>One use case is prefixing. You may wish to not propagate the plain name of this field,
     * rather only a prefixed name in hyphen case. For example, the following would make the field
     * named "userId" propagated only as "baggage-user-id".
     *
     * <pre>{@code
     * userId = ExtraField.newBuilder("userId")
     *                    .clearKeys()
     *                    .addKey("baggage-user-id").build();
     * }</pre>
     *
     * @since 5.11
     */
    public Builder clearKeys() {
      keys.clear();
      return this;
    }

    /** @since 5.11 */
    public Builder addKey(String key) {
      keys.add(validateName(key));
      return this;
    }

    /**
     * Sets this field to be only visible in process, redacted from remote propagation.
     *
     * @since 5.11
     */
    public Builder redacted() {
      this.redacted = true;
      return this;
    }

    /**
     * When set, the field will be added to the correlation context on {@link
     * CorrelationFieldScopeDecorator#decorateScope(TraceContext, CurrentTraceContext.Scope)}.
     *
     * <h3>Immediate flushing</h3>
     * <p>This will also immediately flush a value update to the correlation context on {@link
     * #setValue(String)} as opposed waiting for the next scope decoration.
     *
     * <p>This is useful for callbacks that have a void return. Ex.
     * <pre>{@code
     * static final ExtraField BUSINESS_PROCESS = ExtraField.newBuilder("bp").withCorrelation().build();
     *
     * @SendTo(SourceChannels.OUTPUT)
     * public void timerMessageSource() {
     *   BUSINESS_PROCESS.setValue("accounting");
     *   // Assuming a Log4j context, the expression %{bp} will show "accounting" in businessCode()
     *   businessCode();
     * }
     * }</pre>
     *
     * @since 5.11
     */
    public CorrelationBuilder withCorrelation() {
      return new CorrelationBuilder(this);
    }

    /** @since 5.11 */
    public ExtraField build() {
      return new ExtraField(this);
    }
  }

  /** Used to make an extra field for {@link CorrelationFieldScopeDecorator} */
  public static class CorrelationBuilder extends Builder {
    CorrelationBuilder(Builder builder) {
      super(builder);
    }

    /** @see Builder#redacted() */
    public CorrelationBuilder clearKeys() {
      super.clearKeys();
      return this;
    }

    /** @see Builder#redacted() */
    public CorrelationBuilder addKey(String key) {
      super.addKey(key);
      return this;
    }

    /** @see Builder#redacted() */
    public CorrelationBuilder redacted() {
      super.redacted();
      return this;
    }

    /** @see Builder#build() */
    public final WithCorrelation build() {
      return new WithCorrelation(this);
    }
  }

  public static final class WithCorrelation extends ExtraField implements CorrelationField {
    WithCorrelation(Builder builder) {
      super(builder);
    }

    @Override public String name() {
      return name;
    }

    @Override public CorrelationBuilder toBuilder() {
      return new Builder(this).withCorrelation();
    }
  }

  final String name, lcName;
  final String[] keys; // for faster iteration
  final List<String> keysList;
  final boolean redacted;

  ExtraField(Builder builder) {
    name = builder.name;
    lcName = name.toLowerCase(Locale.ROOT);
    if (builder.keys.isEmpty()) throw new IllegalArgumentException("keys are empty");
    keys = builder.keys.toArray(new String[0]);
    keysList = asList(keys);
    redacted = builder.redacted;
  }

  /** Extracts the most recent value for this field in the context or null if unavailable. */
  @Nullable public String getValue(TraceContext context) {
    return PropagationFields.get(context, this, ExtraFields.class);
  }

  /** Sets the value of the this field, or ignores if not configured. */
  public void setValue(TraceContext context, String value) {
    PropagationFields.put(context, this, value, ExtraFields.class);
  }

  /**
   * Like {@link #getValue(TraceContext)} except against the current trace context.
   *
   * <p>Prefer {@link #getValue(TraceContext)} if you have a reference to the trace context.
   */
  @Nullable public String getValue() {
    TraceContext context = currentTraceContext();
    return context != null ? getValue(context) : null;
  }

  /**
   * Like {@link #setValue(TraceContext, String)} except against the current trace context.
   *
   * <p>Prefer {@link #setValue(TraceContext, String)} if you have a reference to the trace
   * context.
   */
  public void setValue(String value) {
    TraceContext context = currentTraceContext();
    if (context != null) setValue(context, value);
  }

  /** The non-empty name of the field. Ex "userId" */
  public String name() {
    return name;
  }

  /**
   * The non-empty list of names for use in remote propagation. By default it includes only the
   * lowercase variant of the {@link #name()}.
   */
  public List<String> keys() {
    return keysList;
  }

  @Override public String toString() {
    return name;
  }

  @Override public boolean equals(Object o) {
    if (o == this) return true;
    if (!(o instanceof ExtraField)) return false;
    return lcName.equals(((ExtraField) o).lcName);
  }

  @Override public int hashCode() {
    return lcName.hashCode();
  }
}
