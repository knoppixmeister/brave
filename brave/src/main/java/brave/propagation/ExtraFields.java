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
import brave.internal.PropagationFieldsFactory;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Copy-on-write keeps propagation changes in a child context from affecting its parent */
final class ExtraFields extends PropagationFields<ExtraField, String> {
  static final class Factory extends PropagationFieldsFactory<ExtraField, String, ExtraFields> {
    final ExtraField[] fields;

    Factory(ExtraField... fields) {
      this.fields = fields;
    }

    @Override public Class<ExtraFields> type() {
      return ExtraFields.class;
    }

    @Override public ExtraFields create() {
      return new ExtraFields(fields);
    }

    @Override public ExtraFields create(ExtraFields parent) {
      return new ExtraFields(parent, fields);
    }

    @Override protected TraceContext contextWithExtra(TraceContext context, List<Object> extra) {
      return context.withExtra(extra); // more efficient
    }
  }

  final ExtraField[] fields;
  volatile String[] values; // guarded by this, copy on write

  ExtraFields(ExtraField... fields) {
    this.fields = fields;
  }

  ExtraFields(ExtraFields parent, ExtraField... fields) {
    this(fields);
    checkSameFields(parent);
    this.values = parent.values;
  }

  @Override protected String get(ExtraField field) {
    int index = indexOf(field);
    return index != -1 ? get(index) : null;
  }

  String get(int index) {
    if (index >= fields.length) return null;

    String[] elements = values;
    return elements != null ? elements[index] : null;
  }

  @Override protected void forEach(FieldConsumer<ExtraField, String> fieldConsumer) {
    String[] elements = values;
    if (elements == null) return;

    for (int i = 0, length = fields.length; i < length; i++) {
      String value = elements[i];
      if (value == null) continue;
      fieldConsumer.accept(fields[i], value);
    }
  }

  @Override protected final void put(ExtraField field, String value) {
    int index = indexOf(field);
    if (index == -1) return;
    put(index, value);
  }

  @Override protected boolean isEmpty() {
    String[] elements = values;
    if (elements == null) return true;
    for (String value : elements) {
      if (value != null) return false;
    }
    return true;
  }

  protected final void put(int index, @Nullable String value) {
    if (index >= fields.length) return;

    synchronized (this) {
      doPut(index, value);
    }
  }

  void doPut(int index, @Nullable String value) {
    String[] elements = values;
    if (elements == null) {
      elements = new String[fields.length];
      elements[index] = value;
    } else if (equal(value, elements[index])) {
      return;
    } else { // this is the copy-on-write part
      elements = Arrays.copyOf(elements, elements.length);
      elements[index] = value;
    }
    values = elements;
  }

  @Override protected final void putAllIfAbsent(PropagationFields parent) {
    if (!(parent instanceof ExtraFields)) return;
    ExtraFields predefinedParent = (ExtraFields) parent;
    checkSameFields(predefinedParent);
    String[] parentValues = predefinedParent.values;
    if (parentValues == null) return;
    for (int i = 0; i < parentValues.length; i++) {
      if (parentValues[i] != null && get(i) == null) { // extracted wins vs parent
        doPut(i, parentValues[i]);
      }
    }
  }

  void checkSameFields(ExtraFields predefinedParent) {
    if (!Arrays.equals(fields, predefinedParent.fields)) {
      throw new IllegalStateException(
        String.format("Mixed name configuration unsupported: found %s, expected %s",
          Arrays.toString(fields), Arrays.toString(predefinedParent.fields))
      );
    }
  }

  @Override public final Map<String, String> toMap() {
    String[] elements = values;
    if (elements == null) return Collections.emptyMap();

    MapFieldConsumer result = new MapFieldConsumer();
    forEach(result);
    return result;
  }

  static final class MapFieldConsumer extends LinkedHashMap<String, String>
    implements FieldConsumer<ExtraField, String> {
    @Override public void accept(ExtraField field, String value) {
      put(field.name, value);
    }
  }

  int indexOf(ExtraField name) {
    for (int i = 0, length = fields.length; i < length; i++) {
      if (fields[i].equals(name)) return i;
    }
    return -1;
  }

  @Override public int hashCode() { // for unit tests
    String[] values = this.values;
    return values == null ? 0 : Arrays.hashCode(values);
  }

  @Override public boolean equals(Object o) { // for unit tests
    if (o == this) return true;
    if (!(o instanceof ExtraFields)) return false;
    ExtraFields that = (ExtraFields) o;
    String[] values = this.values, thatValues = that.values;
    return values == null ? thatValues == null : Arrays.equals(values, thatValues);
  }
}
