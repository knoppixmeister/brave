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
package brave.context.log4j2;

import brave.propagation.CorrelationFieldScopeDecorator;
import brave.propagation.CurrentTraceContext.ScopeDecorator;

/**
 * This is a shortcut to using {@link CorrelationFieldScopeDecorator} with {@link Log4j2Context}.
 *
 * <p>Ex.
 * <pre>{@code
 * tracing = Tracing.newBuilder()
 *                  .currentTraceContext(ThreadLocalCurrentTraceContext.newBuilder()
 *                    .addScopeDecorator(ThreadContextScopeDecorator.create())
 *                    .build()
 *                  )
 *                  ...
 *                  .build();
 * }</pre>
 *
 * @see CorrelationFieldScopeDecorator
 */
public final class ThreadContextScopeDecorator {
  public static ScopeDecorator create() {
    return CorrelationFieldScopeDecorator.newBuilder(new Log4j2Context()).build();
  }
}
