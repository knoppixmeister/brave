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
import brave.propagation.CorrelationFieldScopeDecorator;
import brave.propagation.CorrelationFields;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.After;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

public class CorrelationFieldScopeDecoratorFactoryBeanTest {
  XmlBeans context;

  public static final CorrelationContext
    CONTEXT = mock(CorrelationContext.class);

  @After public void close() {
    if (context != null) context.close();
  }

  @Test public void context() {
    context = new XmlBeans(""
      + "<bean id=\"correlationDecorator\" class=\"brave.spring.beans.CorrelationFieldScopeDecoratorFactoryBean\">\n"
      + "  <property name=\"context\">\n"
      + "      <util:constant static-field=\"" + getClass().getName() + ".CONTEXT\"/>\n"
      + "  </property>\n"
      + "</bean>"
    );

    assertThat(context.getBean("correlationDecorator", CorrelationFieldScopeDecorator.class))
      .extracting("context")
      .isEqualTo(CONTEXT);
  }

  @Test public void defaultFields() {
    context = new XmlBeans(""
      + "<bean id=\"correlationDecorator\" class=\"brave.spring.beans.CorrelationFieldScopeDecoratorFactoryBean\">\n"
      + "  <property name=\"context\">\n"
      + "      <util:constant static-field=\"" + getClass().getName() + ".CONTEXT\"/>\n"
      + "  </property>\n"
      + "</bean>"
    );

    assertThat(context.getBean("correlationDecorator", CorrelationFieldScopeDecorator.class))
      .extracting("fields").asInstanceOf(InstanceOfAssertFactories.ARRAY).containsExactly(
      CorrelationFields.TRACE_ID,
      CorrelationFields.PARENT_ID,
      CorrelationFields.SPAN_ID,
      CorrelationFields.SAMPLED
    );
  }

  @Test public void fields() {
    context = new XmlBeans(""
      + "<bean id=\"correlationDecorator\" class=\"brave.spring.beans.CorrelationFieldScopeDecoratorFactoryBean\">\n"
      + "  <property name=\"context\">\n"
      + "      <util:constant static-field=\"" + getClass().getName() + ".CONTEXT\"/>\n"
      + "  </property>\n"
      + "  <property name=\"fields\">\n"
      + "    <list>\n"
      + "      <util:constant static-field=\"brave.propagation.CorrelationFields.TRACE_ID\"/>\n"
      + "      <util:constant static-field=\"brave.propagation.CorrelationFields.SPAN_ID\"/>\n"
      + "    </list>\n"
      + "  </property>"
      + "</bean>"
    );

    assertThat(context.getBean("correlationDecorator", CorrelationFieldScopeDecorator.class))
      .extracting("fields").asInstanceOf(InstanceOfAssertFactories.ARRAY)
      .containsExactly(CorrelationFields.TRACE_ID, CorrelationFields.SPAN_ID);
  }
}
