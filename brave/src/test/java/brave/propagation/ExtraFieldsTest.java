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

import brave.internal.PropagationFields;
import brave.internal.PropagationFieldsFactoryTest;
import brave.propagation.ExtraFields.Factory;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ExtraFieldsTest
  extends PropagationFieldsFactoryTest<ExtraField, String, ExtraFields, Factory> {

  public ExtraFieldsTest() {
    super(ExtraField.create("one"), ExtraField.create("two"), "1", "2", "3");
  }

  @Override protected Factory newFactory() {
    return new Factory(keyOne, keyTwo);
  }

  @Test public void put_ignore_if_not_defined() {
    PropagationFields.put(context, ExtraField.create("balloon-color"), "red", factory.type());

    assertThat(((PropagationFields) context.extra().get(0)).toMap())
      .isEmpty();
  }

  @Test public void put_ignore_if_not_defined_index() {
    ExtraFields fields = factory.create();

    fields.put(4, "red");

    assertThat(fields)
      .isEqualToComparingFieldByField(factory.create());
  }

  @Test public void put_idempotent() {
    ExtraFields fields = factory.create();

    fields.put(keyOne, "red");
    String[] fieldsArray = fields.values;

    fields.put(keyOne, "red");
    assertThat(fields.values)
      .isSameAs(fieldsArray);

    fields.put(keyOne, "blue");
    assertThat(fields.values)
      .isNotSameAs(fieldsArray);
  }

  @Test public void get_ignore_if_not_defined_index() {
    ExtraFields fields = factory.create();

    assertThat(fields.get(4))
      .isNull();
  }

  @Test public void toMap_one_index() {
    ExtraFields fields = factory.create();
    fields.put(1, "a");

    assertThat(fields.toMap())
      .hasSize(1)
      .containsEntry(keyTwo.lcName, "a");
  }

  @Test public void toMap_two_index() {
    ExtraFields fields = factory.create();
    fields.put(0, "1");
    fields.put(1, "a");

    assertThat(fields.toMap())
      .hasSize(2)
      .containsEntry(keyOne.lcName, "1")
      .containsEntry(keyTwo.lcName, "a");
  }
}
