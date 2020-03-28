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
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.mockito.Mockito.mock;

public class CorrelationFieldScopeDecoratorTest {
  static final ExtraField.WithCorrelation EXTRA_FIELD =
    ExtraField.newBuilder("user-id").withCorrelation().build();

  static final Map<String, String> map = new LinkedHashMap<>();

  Propagation.Factory extraFactory = ExtraFieldPropagation.newFactoryBuilder(B3Propagation.FACTORY)
    .addField(EXTRA_FIELD).build();

  TraceContext context = extraFactory.decorate(TraceContext.newBuilder()
    .traceId(1L)
    .parentId(2L)
    .spanId(3L)
    .sampled(true)
    .build());

  ScopeDecorator decorator = CorrelationFieldScopeDecorator.newBuilder(new Context()).build();
  ScopeDecorator onlyTraceIdDecorator = CorrelationFieldScopeDecorator.newBuilder(new Context())
    .clearFields()
    .addField(CorrelationFields.TRACE_ID)
    .build();
  ScopeDecorator onlyExtraFieldDecorator = CorrelationFieldScopeDecorator.newBuilder(new Context())
    .clearFields()
    .addField(EXTRA_FIELD)
    .build();
  ScopeDecorator withExtraFieldDecorator = CorrelationFieldScopeDecorator.newBuilder(new Context())
    .addField(EXTRA_FIELD)
    .build();

  @Before public void before() {
    map.clear();
    EXTRA_FIELD.setValue(context, "romeo");
  }

  @Test public void doesntDecorateNoop() {
    assertThat(decorator.decorateScope(context, Scope.NOOP)).isSameAs(Scope.NOOP);
    assertThat(onlyExtraFieldDecorator.decorateScope(context, Scope.NOOP)).isSameAs(Scope.NOOP);
    assertThat(withExtraFieldDecorator.decorateScope(context, Scope.NOOP)).isSameAs(Scope.NOOP);
    assertThat(onlyExtraFieldDecorator.decorateScope(context, Scope.NOOP)).isSameAs(Scope.NOOP);
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
    Scope decorated = withExtraFieldDecorator.decorateScope(context, mock(Scope.class));
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

    Scope decorated = withExtraFieldDecorator.decorateScope(context, mock(Scope.class));
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
    Scope decorated = withExtraFieldDecorator.decorateScope(context, mock(Scope.class));
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

  final class Context extends CorrelationContext {
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
