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
package brave.spring.beans;

import brave.internal.CorrelationContext;
import brave.propagation.CorrelationField;
import brave.propagation.CorrelationFieldScopeDecorator;
import brave.propagation.CorrelationFieldScopeDecorator.Builder;
import java.util.List;
import org.springframework.beans.factory.FactoryBean;

/** Spring XML config does not support chained builders. This converts accordingly */
public class CorrelationFieldScopeDecoratorFactoryBean implements FactoryBean {
  CorrelationContext context;
  List<CorrelationField> fields;

  @Override public CorrelationFieldScopeDecorator getObject() {
    Builder builder = CorrelationFieldScopeDecorator.newBuilder(context);
    if (fields != null) {
      builder.clearFields();
      for (CorrelationField field : fields) {
        builder.addField(field);
      }
    }
    return builder.build();
  }

  @Override public Class<? extends CorrelationFieldScopeDecorator> getObjectType() {
    return CorrelationFieldScopeDecorator.class;
  }

  @Override public boolean isSingleton() {
    return true;
  }

  public void setContext(CorrelationContext context) {
    this.context = context;
  }

  public void setFields(List<CorrelationField> fields) {
    this.fields = fields;
  }
}
