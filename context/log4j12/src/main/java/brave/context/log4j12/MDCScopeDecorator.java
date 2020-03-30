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
package brave.context.log4j12;

import brave.internal.CorrelationContext;
import brave.propagation.CorrelationFieldScopeDecorator;
import brave.propagation.CorrelationFields;
import brave.propagation.CurrentTraceContext;
import org.apache.log4j.MDC;

/**
 * Creates a {@link CorrelationFieldScopeDecorator} for Log4j 1.2 {@linkplain MDC Mapped Diagnostic
 * Context (MDC)}.
 *
 * <p>Ex.
 * <pre>{@code
 * tracing = Tracing.newBuilder()
 *                  .currentTraceContext(ThreadLocalCurrentTraceContext.newBuilder()
 *                    .addScopeDecorator(MDCScopeDecorator.create())
 *                    .build()
 *                  )
 *                  ...
 *                  .build();
 * }</pre>
 *
 * @see CorrelationFieldScopeDecorator
 */
public final class MDCScopeDecorator {
  /**
   * Initializes the builder with the standard fields: {@link CorrelationFields#TRACE_ID}, {@link
   * CorrelationFields#PARENT_ID}, {@link CorrelationFields#SPAN_ID} and {@link
   * CorrelationFields#SAMPLED}.
   *
   * @since 5.11
   */
  public static CorrelationFieldScopeDecorator.Builder newBuilder() {
    return new Builder();
  }

  /** @since 5.2 */
  public static CurrentTraceContext.ScopeDecorator create() {
    return new Builder().build();
  }

  static final class Builder extends CorrelationFieldScopeDecorator.Builder {
    Builder() {
      super(MDCContext.INSTANCE);
    }
  }

  enum MDCContext implements CorrelationContext {
    INSTANCE;

    @Override public String get(String name) {
      Object result = MDC.get(name);
      return result instanceof String ? (String) result : null;
    }

    @Override public void put(String name, String value) {
      MDC.put(name, value);
    }

    @Override public void remove(String name) {
      MDC.remove(name);
    }
  }
}
