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

/**
 * A field which are applied in a correlation context such as MDC via {@link
 * CorrelationFieldScopeDecorator}.
 *
 * <p>Field updates only apply during {@linkplain CorrelationFieldScopeDecorator scope
 * decoration}. This means values set do not flush immediately to the underlying correlation context
 * by default. Rather, they are scheduled for the next scope operation. This is a way to control
 * overhead. Use the type {@link Updatable} to allow immediate updates.
 *
 * <p>{@link #equals(Object)} and {@link #hashCode()} should be overridden to implement lower-case
 * {@link #name()} comparison.
 *
 * @see CorrelationFieldScopeDecorator
 * @since 5.11
 */
public interface CorrelationField {
  /** The name of this field in the correlation context. */
  String name();

  /**
   * Returns the most recent value of the field named {@link #name()} in the context or null if
   * unavailable.
   */
  @Nullable String getValue(TraceContext context);

  interface Updatable extends CorrelationField {
    /**
     * MCall this to immediately flush a value update to the correlation context as opposed waiting
     * for the next scope decoration. This has a significant performance impact as it requires even
     * {@link CurrentTraceContext#maybeScope(TraceContext)} to always track values.
     *
     * <p>This is useful for callbacks that have a void return. Ex.
     * <pre>{@code
     * @SendTo(SourceChannels.OUTPUT)
     * public void timerMessageSource() {
     *   // Assume BUSINESS_PROCESS is an updatable field
     *   BUSINESS_PROCESS.updateValue("accounting");
     *   // Assuming a Log4j context, the expression %{bp} will show "accounting" in businessCode()
     *   businessCode();
     * }
     * }</pre>
     *
     * <h3>Appropriate Usage</h3>
     * <p>Most fields do not change in the scope of a {@link TraceContext}. For example, standard
     * fields such as {@link CorrelationFields#SPAN_ID the span ID} and {@linkplain
     * CorrelationFields#constant(String, String) constants} such as env variables do not need to be
     * tracked. Even field value updates do not necessarily need to be flushed to the underlying
     * correlation context, as they will apply on the next scope operation.
     *
     * @see ExtraField.CorrelationBuilder#flushOnUpdate()
     */
    boolean flushOnUpdate();
  }
}
