/*
 * Copyright 2002-2017 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.transaction.annotation;

import java.io.Serializable;
import java.lang.reflect.AnnotatedElement;
import java.util.ArrayList;

import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.annotation.AnnotationAttributes;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.lang.Nullable;
import org.springframework.transaction.interceptor.NoRollbackRuleAttribute;
import org.springframework.transaction.interceptor.RollbackRuleAttribute;
import org.springframework.transaction.interceptor.RuleBasedTransactionAttribute;
import org.springframework.transaction.interceptor.TransactionAttribute;

/**
 * Strategy implementation for parsing Spring's {@link Transactional} annotation.
 *
 * @author Juergen Hoeller
 * @since 2.5
 */
@SuppressWarnings("serial")
public class SpringTransactionAnnotationParser implements TransactionAnnotationParser, Serializable {

	@Override
	@Nullable
	public TransactionAttribute parseTransactionAnnotation(AnnotatedElement ae) {
		//// 获取 AnnotationAttributes
		AnnotationAttributes attributes = AnnotatedElementUtils.findMergedAnnotationAttributes(
				ae, Transactional.class, false, false);
		if (attributes != null) {
			return parseTransactionAnnotation(attributes);
		}
		else {
			return null;
		}
	}

	public TransactionAttribute parseTransactionAnnotation(Transactional ann) {
		return parseTransactionAnnotation(AnnotationUtils.getAnnotationAttributes(ann, false, false));
	}
//
//		至此，我们终于看到了想看到的 获取注解标记 的代码。首先会判断 当前的类是否含有 Transactional 注解，这是事务属性的基础，
//	当然如果有的话会继续调用 parseTransadionAnnotation 施细的属性。
//----------------------------------------------------------------------------------------------------------------
//		上面方法中实现了对对应类或者方法的事务属性解析，你会在这个类中看到任何你常用或者不常用的属性提取。
//		至此，我们终于完成了事务标签的解析。我们是不是分析的太远了，似乎已经忘了从哪里开始了。再回顾一下，
//	我们的现在的任务是找出某个増强器是否适合于对应的类，而是否匹配的关键则在于是否从指定的类或类中的方法中找到对应的事务属性，现
//	在，我们以 UserServicelmpl 为例，已经在它的接口 UserService 中找到了事务属性，所以，它是与事务増强器匹配的，也就是它会被事务功能修饰。
//		至此，事务功能的初始化工作便结束了，当判断某个 bean 适用于事务増强时，也就是适用于増强器 BeanFactoryTransactionAttributeSourceAdvisor,
//	没错，还是这个类，所以说，在自定义标签解析时，注入的类成为了整个事务功能的基础。
//		BeanFactoryTransactionAttributeSourceAdvisor 作为 Advisor 的实现类，自然要遵从 Advisor 的处理方式，当代理被调用时会调用这个类的増强方法，
//	也就是此bean的Advise,又因为在解析事务定义标签时我们把 Transactioninterceptor 类型的 bean 注入到了 BeanFactoryTransactionAttributeSourceAdvisor 中，
//	所以，在调用事务増强器増强的代理类时会首先执行 Transactioninterceptor 进行増强，同时，也就是在 Transactioninterceptor 类中的invoke方法中完成了整个事务的逻辑。
	protected TransactionAttribute parseTransactionAnnotation(AnnotationAttributes attributes) {
		RuleBasedTransactionAttribute rbta = new RuleBasedTransactionAttribute();
		/// 解析 propagation
		Propagation propagation = attributes.getEnum("propagation");
		rbta.setPropagationBehavior(propagation.value());
		/// 解析 isolation
		Isolation isolation = attributes.getEnum("isolation");
		rbta.setIsolationLevel(isolation.value());
		///	解析timeout
		rbta.setTimeout(attributes.getNumber("timeout").intValue());
		/// 解析 readOnly
		rbta.setReadOnly(attributes.getBoolean("readOnly"));
		/// 解析 value
		rbta.setQualifier(attributes.getString("value"));
		ArrayList<RollbackRuleAttribute> rollBackRules = new ArrayList<>();
		/// 解析 rollbackFor
		Class<?>[] rbf = attributes.getClassArray("rollbackFor");
		for (Class<?> rbRule : rbf) {
			RollbackRuleAttribute rule = new RollbackRuleAttribute(rbRule);
			rollBackRules.add(rule);
		}
		///	解析 rollbackForClassName
		String[] rbfc = attributes.getStringArray("rollbackForClassName");
		for (String rbRule : rbfc) {
			RollbackRuleAttribute rule = new RollbackRuleAttribute(rbRule);
			rollBackRules.add(rule);
		}
		/// 解析noRollbackFor
		Class<?>[] nrbf = attributes.getClassArray("noRollbackFor");
		for (Class<?> rbRule : nrbf) {
			NoRollbackRuleAttribute rule = new NoRollbackRuleAttribute(rbRule);
			rollBackRules.add(rule);
		}
		/// 解析 noRollbackForClassName
		String[] nrbfc = attributes.getStringArray("noRollbackForClassName");
		for (String rbRule : nrbfc) {
			NoRollbackRuleAttribute rule = new NoRollbackRuleAttribute(rbRule);
			rollBackRules.add(rule);
		}
		rbta.getRollbackRules().addAll(rollBackRules);
		return rbta;
	}

	@Override
	public boolean equals(Object other) {
		return (this == other || other instanceof SpringTransactionAnnotationParser);
	}

	@Override
	public int hashCode() {
		return SpringTransactionAnnotationParser.class.hashCode();
	}

}
