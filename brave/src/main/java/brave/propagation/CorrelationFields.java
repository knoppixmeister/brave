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
import java.util.Locale;

/**
 * Standard fields defined for a correlation context such as MDC.
 *
 * @since 5.11
 */
public final class CorrelationFields {
  /**
   * This is the most common log correlation field.
   *
   * @see TraceContext#traceIdString()
   * @since 5.11
   */
  public static final CorrelationField TRACE_ID = new BaseCorrelationField("traceId") {
    @Override public String getValue(TraceContext context) {
      return context.traceIdString();
    }
  };
  /**
   * Typically only useful when spans are parsed from log records.
   *
   * @see TraceContext#parentIdString()
   * @since 5.11
   */
  public static final CorrelationField PARENT_ID = new BaseCorrelationField("parentId") {
    @Override public String getValue(TraceContext context) {
      return context.parentIdString();
    }
  };
  /**
   * Used with {@link #TRACE_ID} to correlate a log line with a span.
   *
   * @see TraceContext#spanIdString()
   * @since 5.11
   */
  public static final CorrelationField SPAN_ID = new BaseCorrelationField("spanId") {
    @Override public String getValue(TraceContext context) {
      return context.spanIdString();
    }
  };
  /**
   * This is only useful when {@link #TRACE_ID} is also a correlation field. It is a hint that a
   * trace may exist in Zipkin, when a user is viewing logs. For example, unsampled traces are not
   * typically reported to Zipkin.
   *
   * @see TraceContext#sampled()
   * @since 5.11
   */
  public static final CorrelationField SAMPLED = new BaseCorrelationField("sampled") {
    @Override public String getValue(TraceContext context) {
      Boolean sampled = context.sampled();
      return sampled != null ? sampled.toString() : null;
    }
  };

  /**
   * Creates a correlation field based on a possibly null value constant, such as an ENV variable.
   *
   * <p>Ex.
   * <pre>{@code
   * cloudRegion = CorrelationFields.constant("region", System.getEnv("CLOUD_REGION"));
   * }</pre>
   *
   * @since 5.11
   */
  public static CorrelationField constant(String name, @Nullable String value) {
    return new ConstantCorrelationField(name, value);
  }

  static final class ConstantCorrelationField extends BaseCorrelationField {
    final String value;

    ConstantCorrelationField(String name, String value) {
      super(name);
      this.value = value;
    }

    @Override public String getValue(TraceContext context) {
      return value;
    }
  }

  static abstract class BaseCorrelationField implements CorrelationField {
    final String name, lcName;

    BaseCorrelationField(String name) {
      this.name = validateName(name);
      this.lcName = name.toLowerCase(Locale.ROOT);
    }

    @Override public String name() {
      return name;
    }

    @Override public String toString() {
      return name;
    }

    @Override public boolean equals(Object o) {
      if (o == this) return true;
      if (!(o instanceof CorrelationField)) return false;
      return lcName.equals(((CorrelationField) o).name());
    }

    @Override public int hashCode() {
      return lcName.hashCode();
    }
  }

  protected static String validateName(String name) {
    if (name == null) throw new NullPointerException("name == null");
    name = name.trim();
    if (name.isEmpty()) throw new IllegalArgumentException("name is empty");
    return name;
  }
}
