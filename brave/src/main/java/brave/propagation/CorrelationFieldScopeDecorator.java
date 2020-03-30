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
import brave.propagation.CorrelationField.Updatable;
import brave.propagation.CurrentTraceContext.Scope;
import brave.propagation.CurrentTraceContext.ScopeDecorator;
import java.util.ArrayDeque;
import java.util.LinkedHashSet;
import java.util.Set;

import static java.util.Arrays.asList;

/**
 * Synchronizes fields such as {@link CorrelationFields#TRACE_ID} with a correlation context, such
 * as logging through decoration of a scope. A maximum of 32 fields are supported.
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
 *   // The below log message will have %X{region} in the context!
 *   logger.info("Encoding the span, hope it works");
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
      if (fieldCount == 1) return new Single(context, fields.iterator().next());
      if (fieldCount > 32) throw new IllegalArgumentException("over 32 correlation fields");
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
  static void update(
    CorrelationContext context, CorrelationField field, @Nullable String newValue) {
    if (newValue != null) {
      context.put(field.name(), newValue);
    } else {
      context.remove(field.name());
    }
  }

  static final class Single extends CorrelationFieldScopeDecorator {
    final CorrelationField field;
    final boolean fieldUpdatable, flushOnUpdate;

    Single(CorrelationContext context, CorrelationField field) {
      super(context);
      this.field = field;
      this.fieldUpdatable = field instanceof Updatable;
      this.flushOnUpdate = fieldUpdatable && ((Updatable) field).flushOnUpdate();
    }

    @Override public Scope decorateScope(@Nullable TraceContext traceContext, Scope scope) {
      String valueToRevert = context.get(field.name());
      String currentValue = traceContext != null ? field.getValue(traceContext) : null;

      boolean dirty = false;
      if (scope != Scope.NOOP || fieldUpdatable) {
        dirty = !equal(valueToRevert, currentValue);
        if (dirty) update(context, field, currentValue);
      }

      // If there was or could be a value update, we need to track the value to revert.
      if (dirty || flushOnUpdate) {
        return new SingleCorrelationFieldScope(scope, context, field, valueToRevert, dirty);
      }

      return scope;
    }
  }

  static abstract class CorrelationFieldScope implements Scope {
    final Scope delegate;
    final CorrelationContext context;

    CorrelationFieldScope(Scope delegate, CorrelationContext context) {
      this.delegate = delegate;
      this.context = context;
      pushCurrentFieldUpdater(this);
    }

    @Override public void close() {
      delegate.close();
      popCurrentFieldUpdater(this);
    }

    /** Called after {@link #handleUpdate ) for a flushed field. */
    abstract void handleUpdate(Updatable field, String value);
  }

  static final ThreadLocal<ArrayDeque<Object>> currentFieldUpdaterStack = new ThreadLocal<>();

  /**
   * Handles a flush by synchronizing the correlation context followed by signaling each stacked
   * scope about a potential field update.
   *
   * <p>Overhead here occurs on the calling thread. Ex. the one that calls {@link
   * ExtraField#updateValue(String)}.
   */
  static void flush(Updatable field, String value) {
    assert field.flushOnUpdate();

    Set<CorrelationContext> syncedContexts = new LinkedHashSet<>();
    for (Object o : currentFieldUpdaterStack()) {
      CorrelationFieldScope next = ((CorrelationFieldScope) o);

      // Since this is a static method, it could be called with different tracers on the stack.
      // This synchronizes the context if we haven't already.
      if (!syncedContexts.contains(next.context)) {
        if (!equal(next.context.get(field.name()), value)) {
          update(next.context, field, value);
        }
        syncedContexts.add(next.context);
      }

      // Now, signal the current scope in case it has a value change
      next.handleUpdate(field, value);
    }
  }

  static void popCurrentFieldUpdater(CorrelationFieldScope expected) {
    Object popped = currentFieldUpdaterStack().pop();
    assert equal(popped, expected) :
      "Misalignment: popped updater " + popped + " !=  expected " + expected;
  }

  static ArrayDeque<Object> currentFieldUpdaterStack() {
    ArrayDeque<Object> stack = currentFieldUpdaterStack.get();
    if (stack == null) {
      stack = new ArrayDeque<>();
      currentFieldUpdaterStack.set(stack);
    }
    return stack;
  }

  static void pushCurrentFieldUpdater(CorrelationFieldScope updater) {
    currentFieldUpdaterStack().push(updater);
  }

  static final class SingleCorrelationFieldScope extends CorrelationFieldScope {
    final CorrelationField field;
    final @Nullable String valueToRevert;
    boolean dirty;

    SingleCorrelationFieldScope(
      Scope delegate,
      CorrelationContext context,
      CorrelationField field,
      @Nullable String valueToRevert,
      boolean dirty
    ) {
      super(delegate, context);
      this.field = field;
      this.valueToRevert = valueToRevert;
      this.dirty = dirty;
    }

    @Override public void close() {
      super.close();
      if (dirty) CorrelationFieldScopeDecorator.update(context, field, valueToRevert);
    }

    @Override void handleUpdate(Updatable field, String value) {
      if (!this.field.equals(field)) return;
      if (!equal(value, valueToRevert)) dirty = true;
    }
  }

  static final class Multiple extends CorrelationFieldScopeDecorator {
    final CorrelationField[] fields;

    Multiple(CorrelationContext context, Set<CorrelationField> correlationFields) {
      super(context);
      fields = correlationFields.toArray(new CorrelationField[0]);
    }

    @Override public Scope decorateScope(@Nullable TraceContext traceContext, Scope scope) {
      int dirty = 0, flushOnUpdate = 0;
      String[] valuesToRevert = new String[fields.length];
      for (int i = 0; i < fields.length; i++) {
        CorrelationField field = fields[i];
        boolean fieldUpdatable = field instanceof Updatable;
        String valueToRevert = context.get(field.name());
        String currentValue = traceContext != null ? field.getValue(traceContext) : null;

        if (scope != Scope.NOOP || fieldUpdatable) {
          if (!equal(valueToRevert, currentValue)) {
            update(context, field, currentValue);
            dirty = set(dirty, i);
          }
        }

        if (fieldUpdatable && ((Updatable) field).flushOnUpdate()) {
          flushOnUpdate = set(flushOnUpdate, i);
        }

        valuesToRevert[i] = valueToRevert;
      }

      // If there was or could be a value update, we need to track the value to revert.
      if (dirty != 0 || flushOnUpdate != 0) {
        return new MultipleCorrelationFieldScope(scope, context, fields, valuesToRevert, dirty);
      }

      return scope;
    }
  }

  static final class MultipleCorrelationFieldScope extends CorrelationFieldScope {
    final CorrelationField[] fields;
    final String[] valuesToRevert;
    int dirty;

    MultipleCorrelationFieldScope(
      Scope delegate,
      CorrelationContext context,
      CorrelationField[] fields,
      String[] valuesToRevert,
      int dirty
    ) {
      super(delegate, context);
      this.fields = fields;
      this.valuesToRevert = valuesToRevert;
      this.dirty = dirty;
    }

    @Override public void close() {
      super.close();
      for (int i = 0; i < fields.length; i++) {
        if (isSet(dirty, i)) update(context, fields[i], valuesToRevert[i]);
      }
    }

    @Override void handleUpdate(Updatable field, String value) {
      for (int i = 0; i < fields.length; i++) {
        if (fields[i].equals(field)) {
          if (!equal(value, valuesToRevert[i])) dirty = set(dirty, i);
          return;
        }
      }
    }
  }

  static int set(int bitset, int i) {
    return bitset | (1 << i);
  }

  static boolean isSet(int bitset, int i) {
    return (bitset & (1 << i)) != 0;
  }

  static boolean equal(@Nullable Object a, @Nullable Object b) {
    return a == null ? b == null : a.equals(b); // Java 6 can't use Objects.equals()
  }
}
