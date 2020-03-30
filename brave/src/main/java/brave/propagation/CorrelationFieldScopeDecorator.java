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

import brave.internal.CorrelationContext;
import brave.internal.Nullable;
import brave.propagation.CurrentTraceContext.Scope;
import brave.propagation.CurrentTraceContext.ScopeDecorator;
import java.util.LinkedHashSet;
import java.util.Set;

import static java.util.Arrays.asList;

/**
 * Synchronizes fields such as {@link CorrelationFields#TRACE_ID} with a correlation context, such
 * as logging through decoration of a scope.
 *
 * <p>Setup example:
 * <pre>{@code
 * // Allow logging patterns like %X{traceId}/%X{spanId}
 * loggingContext = new Log4J2Context();
 *
 * // Add the field "region", so it can be used as a log expression %X{region}
 * cloudRegion = CorrelationFields.constant("region", System.getEnv("CLOUD_REGION"));
 * decorator = CorrelationFieldScopeDecorator.newBuilder(loggingContext)
 *   .addField(cloudRegion)
 *   .build();
 *
 * // Integrate the decorator
 * tracing = Tracing.newBuilder()
 *                  .currentTraceContext(ThreadLocalCurrentTraceContext.newBuilder()
 *                    .addScopeDecorator(decorator)
 *                    .build()
 *                  )
 *                  ...
 *                  .build();
 *
 * // Any scope operations (updates to the current span) apply the fields defined by the decorator.
 * ScopedSpan span = tracing.tracer().startScopedSpan("encode");
 * try {
 *   // %X{traceId} %X{parentId} %X{spanId} %X{sampled} %X{region} are in the logging context!
 *   return encoder.encode();
 * } catch (RuntimeException | Error e) {
 *   span.error(e); // Unless you handle exceptions, you might not know the operation failed!
 *   throw e;
 * } finally {
 *   span.finish();
 * }
 * }</pre>
 *
 * <h3>Extra Field integration</h3>
 * To configure extra fields marked {@link ExtraField.Builder#withCorrelation()}, add them to the
 * builder here like any other correlation field. This will ensure that their values are applied and
 * reverted upon scope decoration.
 *
 * @see CorrelationField
 * @see CorrelationContext
 * @see ExtraField.Builder#withCorrelation()
 * @since 5.11
 */
public abstract class CorrelationFieldScopeDecorator implements ScopeDecorator {
  // do not define newBuilder or create() here as it will mask subtypes
  public static abstract class Builder {
    final CorrelationContext context;
    final Set<CorrelationField> fields = new LinkedHashSet<>(asList(
      CorrelationFields.TRACE_ID,
      CorrelationFields.PARENT_ID,
      CorrelationFields.SPAN_ID,
      CorrelationFields.SAMPLED
    ));

    /** Internal constructor used by subtypes. */
    protected Builder(CorrelationContext context) {
      if (context == null) throw new NullPointerException("context == null");
      this.context = context;
    }

    /**
     * Invoke this to clear fields so that you can {@linkplain #addField(CorrelationField) add the
     * ones you need}.
     *
     * <p>Defaults may include a field you aren't using, such as "parentId". For best
     * performance, only include the fields you use in your correlation expressions (such as log
     * formats).
     *
     * @since 5.11
     */
    public Builder clearFields() {
      this.fields.clear();
      return this;
    }

    /** @since 5.11 */
    public Builder addField(CorrelationField field) {
      if (field == null) throw new NullPointerException("field == null");
      if (field.name() == null) throw new NullPointerException("field.name() == null");
      if (field.name().isEmpty()) throw new NullPointerException("field.name() isEmpty");
      fields.add(field);
      return this;
    }

    /** @throws IllegalArgumentException if no correlation fields were added. */
    public final CorrelationFieldScopeDecorator build() {
      int fieldCount = fields.size();
      if (fieldCount == 0) throw new IllegalArgumentException("no correlation fields");
      if (fieldCount == 1) {
        return new Single(context, fields.iterator().next());
      }
      return new Multiple(context, fields);
    }
  }

  final CorrelationContext context;

  CorrelationFieldScopeDecorator(CorrelationContext context) {
    this.context = context;
  }

  // Users generally expect data to be "cleaned up" when a scope completes, even if it was written
  // mid-scope. Ex. https://github.com/spring-cloud/spring-cloud-sleuth/issues/1416
  //
  // This means we cannot return a no-op scope based on if we detect no change when comparing
  // values up front. Hence, we save off the first value and revert when a scope closes. If a late
  // update changed the value mid-scope, it will reverted.
  void updateUnconditionally(CorrelationField field, @Nullable String newValue) {
    if (newValue != null) {
      context.put(field.name(), newValue);
    } else {
      context.remove(field.name());
    }
  }

  static final class Single extends CorrelationFieldScopeDecorator {
    final CorrelationField field;

    Single(CorrelationContext context, CorrelationField field) {
      super(context);
      this.field = field;
    }

    @Override public Scope decorateScope(TraceContext traceContext, Scope scope) {
      String valueToRevert = context.get(field.name());
      String currentValue = traceContext != null ? field.getValue(traceContext) : null;

      if (!equals(valueToRevert, currentValue)) updateUnconditionally(field, currentValue);

      // Only the value to revert is saved off in order to save overhead.
      return new SingleCorrelationFieldScope(scope, valueToRevert);
    }

    final class SingleCorrelationFieldScope implements CorrelationFieldUpdater, Scope {
      final Scope delegate;
      final @Nullable String valueToRevert;

      SingleCorrelationFieldScope(Scope delegate, @Nullable String valueToRevert) {
        this.delegate = delegate;
        this.valueToRevert = valueToRevert;
      }

      @Override public void close() {
        delegate.close();
        updateUnconditionally(field, valueToRevert);
      }

      @Override public void update(CorrelationField field, String value) {
        if (!field.equals(Single.this.field)) return;
        updateUnconditionally(field, value);
      }
    }
  }

  // TODO: put this in a thread local, and use in extra field propagation. Watch for overlaps
  // Ex look at how ThreadlocalSpan works.
  interface CorrelationFieldUpdater {
    void update(CorrelationField field, String value);
  }

  static final class Multiple extends CorrelationFieldScopeDecorator {
    final CorrelationField[] fields;

    Multiple(CorrelationContext context, Set<CorrelationField> correlationFields) {
      super(context);
      fields = correlationFields.toArray(new CorrelationField[0]);
    }

    @Override public Scope decorateScope(TraceContext traceContext, Scope scope) {
      // Only values to revert are saved off in order to save overhead.
      String[] valuesToRevert = new String[fields.length];
      for (int i = 0; i < fields.length; i++) {
        CorrelationField field = fields[i];
        String valueToRevert = context.get(field.name());
        String currentValue = traceContext != null ? field.getValue(traceContext) : null;

        if (!equals(valueToRevert, currentValue)) updateUnconditionally(field, currentValue);

        valuesToRevert[i] = valueToRevert;
      }

      return new MultipleCorrelationFieldScope(scope, valuesToRevert);
    }

    final class MultipleCorrelationFieldScope implements CorrelationFieldUpdater, Scope {
      final Scope delegate;
      final String[] valuesToRevert;

      MultipleCorrelationFieldScope(Scope delegate, String[] valuesToRevert) {
        this.delegate = delegate;
        this.valuesToRevert = valuesToRevert;
      }

      @Override public void close() {
        delegate.close();
        for (int i = 0; i < fields.length; i++) {
          updateUnconditionally(fields[i], valuesToRevert[i]);
        }
      }

      @Override public void update(CorrelationField field, String value) {
        for (int i = 0; i < fields.length; i++) {
          if (field.equals(fields[i])) {
            updateUnconditionally(field, value);
            return;
          }
        }
      }
    }
  }

  static boolean equals(@Nullable String a, @Nullable String b) {
    return a == null ? b == null : a.equals(b); // Java 6 can't use Objects.equals()
  }
}
