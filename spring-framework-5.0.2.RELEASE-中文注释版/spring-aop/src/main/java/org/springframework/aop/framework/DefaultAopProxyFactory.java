/*
 * Copyright 2002-2015 the original author or authors.
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

package org.springframework.aop.framework;

import java.io.Serializable;
import java.lang.reflect.Proxy;

import org.springframework.aop.SpringProxy;

/**
 * Default {@link AopProxyFactory} implementation, creating either a CGLIB proxy
 * or a JDK dynamic proxy.
 *
 * <p>Creates a CGLIB proxy if one the following is true for a given
 * {@link AdvisedSupport} instance:
 * <ul>
 * <li>the {@code optimize} flag is set
 * <li>the {@code proxyTargetClass} flag is set
 * <li>no proxy interfaces have been specified
 * </ul>
 *
 * <p>In general, specify {@code proxyTargetClass} to enforce a CGLIB proxy,
 * or specify one or more interfaces to use a JDK dynamic proxy.
 *
 * @author Rod Johnson
 * @author Juergen Hoeller
 * @since 12.03.2004
 * @see AdvisedSupport#setOptimize
 * @see AdvisedSupport#setProxyTargetClass
 * @see AdvisedSupport#setInterfaces
 */
@SuppressWarnings("serial")
public class DefaultAopProxyFactory implements AopProxyFactory, Serializable {

	/// 1.optimize（优化）：用来控制通过 CGLIB创建 的 代理 是否使用 激进的 优化策略。除非完全了解 AOP代理 如何处理优化，否则不推荐用户使用这个设置。
	///	目前这个属性仅 用于CGLIB代理，对于 JDK 动态代理（缺省代理）无效。
	/// 2.proxyTargetClass：这个属性为 true 时，目标 类本身被代理而不是目标类的接口。如果这个属性值为true，CGLIB代理 将被创建，
	/// 	设置方式：<aop:aspectj-autoproxy proxy-target-class="true"/>。
	/// 3. hasNoUserSuppliedProxyInterfaces：是否存在代理接口。

	/// 4.下面是对 JDK 与 Cglib 方式的总结。
	///		（1）如果目标对象实现了接口，默认情况下会采用 JDK 的动态代理实现 AOP。
	///		（2）如果目标对象实现了接口，可以强制使用 CGLIB 实现 AOP
	///		（3）如果目标对象没有实现接口，必须使用 CGLIB 库，Spring 会自动在 JDK 动态代理和 CGLIB 之间转换。

	/// 5.如何强制使用 CGLIB 实现 AOP
	///		（1）添加 CGLIB 库，Spring_HOME/cglib/*.jar
	///		（2）在 Spring 中配置文件中加入 <aop:aspectj-autoproxy proxy-target-class="true"/>。

	///	6.JDK 动态代理和 CGLIB 字节码生成的区别。
	///	（1）JDK 动态代理只能对 实现了接口 的类生成代理，而不能针对类。
	///	（2）CGLIB 是针对类实现代理，主要是对指定的类生成一个子类，覆盖其中的方法，因为是继承，所以该类或方法最好不要声明 final。
	@Override
	public AopProxy createAopProxy(AdvisedSupport config) throws AopConfigException {
		if (config.isOptimize() || config.isProxyTargetClass() || hasNoUserSuppliedProxyInterfaces(config)) {
			Class<?> targetClass = config.getTargetClass();
			if (targetClass == null) {
				throw new AopConfigException("TargetSource cannot determine target class: " +
						"Either an interface or a target is required for proxy creation.");
			}
			//是一个接口，或者是一个代理对象
			if (targetClass.isInterface() || Proxy.isProxyClass(targetClass)) {
				return new JdkDynamicAopProxy(config);
			}
				///		CGLIB 包的底层通过使用一个小而快的字节码处理框架	ASN，来转换字节码并生成新的类。除了	CGLIB 包，脚本语言
				/// 如 Groovy 和 BeanShell ，也是通过 ASM 来生成 Java 的字节码。当然不鼓励直接使用 ASM，因为他要求对 JVM 内部结构包括
				// class 文件的格式和指令集都很熟悉。
			return new ObjenesisCglibAopProxy(config);
		}
		else {
			return new JdkDynamicAopProxy(config);
		}
	}

	/**
	 * Determine whether the supplied {@link AdvisedSupport} has only the
	 * {@link org.springframework.aop.SpringProxy} interface specified
	 * (or no proxy interfaces specified at all).
	 */
	private boolean hasNoUserSuppliedProxyInterfaces(AdvisedSupport config) {
		Class<?>[] ifcs = config.getProxiedInterfaces();
		return (ifcs.length == 0 || (ifcs.length == 1 && SpringProxy.class.isAssignableFrom(ifcs[0])));
	}

}
