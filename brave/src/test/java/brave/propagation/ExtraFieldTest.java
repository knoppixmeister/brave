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

import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ExtraFieldTest {
  @Test public void downcasesKey() {
    assertThat(ExtraField.create("X-FOO").keys())
      .containsExactly("x-foo");
  }

  @Test public void trimsName() {
    assertThat(ExtraField.create(" x-foo  ").name())
      .isEqualTo("x-foo");
  }

  @Test public void trimsKey() {
    assertThat(ExtraField.create(" x-foo  ").keys())
      .containsExactly("x-foo");
  }
}
