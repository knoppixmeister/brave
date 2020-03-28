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
import org.apache.log4j.MDC;

/** Integrates Log4j's {@link MDC} */
public final class Log4jContext extends CorrelationContext {
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
