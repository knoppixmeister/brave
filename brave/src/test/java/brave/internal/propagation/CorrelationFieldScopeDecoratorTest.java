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
package brave.internal.propagation;

import brave.internal.CorrelationContext;
import brave.propagation.B3Propagation;
import brave.propagation.CorrelationFieldScopeDecorator;
import brave.propagation.CorrelationFields;
import brave.propagation.CurrentTraceContext.Scope;
import brave.propagation.CurrentTraceContext.ScopeDecorator;
import brave.propagation.ExtraField;
import brave.propagation.ExtraFieldPropagation;
import brave.propagation.Propagation;
import brave.propagation.TraceContext;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.mockito.Mockito.mock;

public class CorrelationFieldScopeDecoratorTest {
  static final ExtraField.WithCorrelation EXTRA_FIELD =
    ExtraField.newBuilder("user-id").withCorrelation().build();
  static final ExtraField.WithCorrelation EXTRA_FIELD_2 =
    ExtraField.newBuilder("country-code").withCorrelation().build();

  static final Map<String, String> map = new LinkedHashMap<>();

  Propagation.Factory extraFactory = ExtraFieldPropagation.newFactoryBuilder(B3Propagation.FACTORY)
    .addField(EXTRA_FIELD)
    .addField(EXTRA_FIELD_2)
    .build();

  TraceContext context = extraFactory.decorate(TraceContext.newBuilder()
    .traceId(1L)
    .parentId(2L)
    .spanId(3L)
    .sampled(true)
    .build());

  ScopeDecorator decorator = new TestBuilder().build();
  ScopeDecorator onlyTraceIdDecorator = new TestBuilder()
    .clearFields()
    .addField(CorrelationFields.TRACE_ID)
    .build();
  ScopeDecorator onlyExtraFieldDecorator = new TestBuilder()
    .clearFields()
    .addField(EXTRA_FIELD)
    .build();
  ScopeDecorator withExtraFieldsDecorator = new TestBuilder()
    .addField(EXTRA_FIELD)
    .addField(EXTRA_FIELD_2)
    .build();

  @Before public void before() {
    map.clear();
    EXTRA_FIELD.updateValue(context, "romeo");
  }

  @Test public void doesntDecorateNoop() {
    assertThat(decorator.decorateScope(context, Scope.NOOP)).isSameAs(Scope.NOOP);
    assertThat(decorator.decorateScope(null, Scope.NOOP)).isSameAs(Scope.NOOP);
  }

  @Test public void doesntDecorateNoop_matchingNullExtraField() {
    EXTRA_FIELD.updateValue(context, null);
    EXTRA_FIELD_2.updateValue(context, null);
    map.put(EXTRA_FIELD.name(), null);
    map.put(EXTRA_FIELD_2.name(), null);

    assertThat(onlyTraceIdDecorator.decorateScope(context, Scope.NOOP)).isSameAs(Scope.NOOP);
    assertThat(withExtraFieldsDecorator.decorateScope(context, Scope.NOOP)).isSameAs(Scope.NOOP);
    assertThat(onlyExtraFieldDecorator.decorateScope(context, Scope.NOOP)).isSameAs(Scope.NOOP);
  }

  @Test public void doesntDecorateNoop_matchingExtraField() {
    EXTRA_FIELD.updateValue(context, "romeo");
    map.put(EXTRA_FIELD.name(), "romeo");
    EXTRA_FIELD_2.updateValue(context, "FO");
    map.put(EXTRA_FIELD_2.name(), "FO");

    assertThat(onlyTraceIdDecorator.decorateScope(context, Scope.NOOP)).isSameAs(Scope.NOOP);
    assertThat(withExtraFieldsDecorator.decorateScope(context, Scope.NOOP)).isSameAs(Scope.NOOP);
    assertThat(onlyExtraFieldDecorator.decorateScope(context, Scope.NOOP)).isSameAs(Scope.NOOP);
  }

  /** When a context is in an unexpected state, save off fields and revert. */
  @Test public void decoratesNoop_unconfiguredFields() {
    context = context.toBuilder().extra(Collections.emptyList()).build();

    for (ScopeDecorator decorator : asList(withExtraFieldsDecorator, onlyExtraFieldDecorator)) {
      map.put(EXTRA_FIELD.name(), "romeo");
      map.put(EXTRA_FIELD_2.name(), "FO");

      assertThat(decorator.decorateScope(context, Scope.NOOP)).isNotSameAs(Scope.NOOP);
    }
  }

  @Test public void decoratesNoop_nullMeansClearFields() {
    context = context.toBuilder().extra(Collections.emptyList()).build();

    for (ScopeDecorator decorator : asList(withExtraFieldsDecorator, onlyExtraFieldDecorator)) {
      map.put(EXTRA_FIELD.name(), "romeo");
      map.put(EXTRA_FIELD_2.name(), "FO");

      assertThat(decorator.decorateScope(null, Scope.NOOP)).isNotSameAs(Scope.NOOP);
    }
  }

  @Test public void addsAndRemoves() {
    Scope decorated = decorator.decorateScope(context, mock(Scope.class));
    assertThat(map).containsExactly(
      entry("traceId", "0000000000000001"),
      entry("parentId", "0000000000000002"),
      entry("spanId", "0000000000000003"),
      entry("sampled", "true")
    );
    decorated.close();
    assertThat(map.isEmpty());
  }

  @Test public void addsAndRemoves_onlyTraceId() {
    Scope decorated = onlyTraceIdDecorator.decorateScope(context, mock(Scope.class));
    assertThat(map).containsExactly(entry("traceId", "0000000000000001"));
    decorated.close();
    assertThat(map.isEmpty());
  }

  @Test public void addsAndRemoves_onlyExtraField() {
    Scope decorated = onlyExtraFieldDecorator.decorateScope(context, mock(Scope.class));
    assertThat(map).containsExactly(entry(EXTRA_FIELD.name(), "romeo"));
    decorated.close();
    assertThat(map.isEmpty());
  }

  @Test public void addsAndRemoves_withExtraField() {
    Scope decorated = withExtraFieldsDecorator.decorateScope(context, mock(Scope.class));
    assertThat(map).containsExactly(
      entry("traceId", "0000000000000001"),
      entry("parentId", "0000000000000002"),
      entry("spanId", "0000000000000003"),
      entry("sampled", "true"),
      entry(EXTRA_FIELD.name(), "romeo")
    );
    decorated.close();
    assertThat(map.isEmpty());
  }

  @Test public void revertsChanges() {
    map.put("traceId", "000000000000000a");
    map.put("parentId", "000000000000000b");
    map.put("spanId", "000000000000000c");
    map.put("sampled", "false");

    Scope decorated = decorator.decorateScope(context, mock(Scope.class));
    assertThat(map).containsExactly(
      entry("traceId", "0000000000000001"),
      entry("parentId", "0000000000000002"),
      entry("spanId", "0000000000000003"),
      entry("sampled", "true")
    );
    decorated.close();

    assertThat(map).containsExactly(
      entry("traceId", "000000000000000a"),
      entry("parentId", "000000000000000b"),
      entry("spanId", "000000000000000c"),
      entry("sampled", "false")
    );
  }

  @Test public void revertsChanges_onlyTraceId() {
    map.put("traceId", "000000000000000a");

    Scope decorated = onlyTraceIdDecorator.decorateScope(context, mock(Scope.class));
    assertThat(map).containsExactly(entry("traceId", "0000000000000001"));
    decorated.close();

    assertThat(map).containsExactly(entry("traceId", "000000000000000a"));
  }

  @Test public void revertsChanges_onlyExtraField() {
    map.put(EXTRA_FIELD.name(), "bob");

    Scope decorated = onlyExtraFieldDecorator.decorateScope(context, mock(Scope.class));
    assertThat(map).containsExactly(entry(EXTRA_FIELD.name(), "romeo"));
    decorated.close();

    assertThat(map).containsExactly(entry(EXTRA_FIELD.name(), "bob"));
  }

  @Test public void revertsChanges_withExtraField() {
    map.put("traceId", "000000000000000a");
    map.put("parentId", "000000000000000b");
    map.put("spanId", "000000000000000c");
    map.put("sampled", "false");
    map.put(EXTRA_FIELD.name(), "bob");

    Scope decorated = withExtraFieldsDecorator.decorateScope(context, mock(Scope.class));
    assertThat(map).containsExactly(
      entry("traceId", "0000000000000001"),
      entry("parentId", "0000000000000002"),
      entry("spanId", "0000000000000003"),
      entry("sampled", "true"),
      entry(EXTRA_FIELD.name(), "romeo")
    );
    decorated.close();

    assertThat(map).containsExactly(
      entry("traceId", "000000000000000a"),
      entry("parentId", "000000000000000b"),
      entry("spanId", "000000000000000c"),
      entry("sampled", "false"),
      entry(EXTRA_FIELD.name(), "bob")
    );
  }

  @Test public void revertsLateChanges() {
    Scope decorated = decorator.decorateScope(context, mock(Scope.class));
    assertThat(map).containsExactly(
      entry("traceId", "0000000000000001"),
      entry("parentId", "0000000000000002"),
      entry("spanId", "0000000000000003"),
      entry("sampled", "true")
    );

    // late changes
    map.put("traceId", "000000000000000a");
    map.put("parentId", "000000000000000b");
    map.put("spanId", "000000000000000c");
    map.put("sampled", "false");

    decorated.close();

    assertThat(map).isEmpty();
  }

  @Test public void revertsLateChanges_onlyTraceId() {
    Scope decorated = onlyTraceIdDecorator.decorateScope(context, mock(Scope.class));
    assertThat(map).containsExactly(entry("traceId", "0000000000000001"));

    // late changes
    map.put("traceId", "000000000000000a");

    decorated.close();

    assertThat(map).isEmpty();
  }

  @Test public void revertsLateChanges_onlyExtraField() {
    Scope decorated = onlyExtraFieldDecorator.decorateScope(context, mock(Scope.class));
    assertThat(map).containsExactly(entry(EXTRA_FIELD.name(), "romeo"));

    // late changes
    map.put(EXTRA_FIELD.name(), "bob");

    decorated.close();

    assertThat(map).isEmpty();
  }

  @Test public void revertsLateChanges_withExtraField() {
    Scope decorated = withExtraFieldsDecorator.decorateScope(context, mock(Scope.class));
    assertThat(map).containsExactly(
      entry("traceId", "0000000000000001"),
      entry("parentId", "0000000000000002"),
      entry("spanId", "0000000000000003"),
      entry("sampled", "true"),
      entry(EXTRA_FIELD.name(), "romeo")
    );

    // late changes
    map.put("traceId", "000000000000000a");
    map.put("parentId", "000000000000000b");
    map.put("spanId", "000000000000000c");
    map.put("sampled", "false");
    map.put(EXTRA_FIELD.name(), "bob");

    decorated.close();

    assertThat(map).isEmpty();
  }

  static final class TestBuilder extends CorrelationFieldScopeDecorator.Builder {
    TestBuilder() {
      super(MapContext.INSTANCE);
    }
  }

  enum MapContext implements CorrelationContext {
    INSTANCE;

    @Override public String get(String name) {
      return map.get(name);
    }

    @Override public void put(String name, String value) {
      map.put(name, value);
    }

    @Override public void remove(String name) {
      map.remove(name);
    }
  }
}
