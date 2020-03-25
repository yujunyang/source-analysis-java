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

package org.springframework.aop.config;

import org.springframework.aop.framework.AopContext;
import org.w3c.dom.Element;

import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.parsing.BeanComponentDefinition;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.xml.ParserContext;
import org.springframework.lang.Nullable;

/**
 * Utility class for handling registration of auto-proxy creators used internally
 * by the '{@code aop}' namespace tags.
 *
 * <p>Only a single auto-proxy creator can be registered and multiple tags may wish
 * to register different concrete implementations. As such this class delegates to
 * {@link AopConfigUtils} which wraps a simple escalation protocol. Therefore classes
 * may request a particular auto-proxy creator and know that class, <i>or a subclass
 * thereof</i>, will eventually be resident in the application context.
 *
 * @author Rob Harrop
 * @author Juergen Hoeller
 * @author Mark Fisher
 * @since 2.0
 * @see AopConfigUtils
 */
public abstract class AopNamespaceUtils {

	/**
	 * The {@code proxy-target-class} attribute as found on AOP-related XML tags.
	 */
	public static final String PROXY_TARGET_CLASS_ATTRIBUTE = "proxy-target-class";

	/**
	 * The {@code expose-proxy} attribute as found on AOP-related XML tags.
	 */
	private static final String EXPOSE_PROXY_ATTRIBUTE = "expose-proxy";


	public static void registerAutoProxyCreatorIfNecessary(
			ParserContext parserContext, Element sourceElement) {

		///	进入 registerAutoProxyCreatorIfNecessary
		BeanDefinition beanDefinition = AopConfigUtils.registerAutoProxyCreatorIfNecessary(
				parserContext.getRegistry(), parserContext.extractSource(sourceElement));

		useClassProxyingIfNecessary(parserContext.getRegistry(), sourceElement);
		registerComponentIfNecessary(beanDefinition, parserContext);
	}

	public static void registerAspectJAutoProxyCreatorIfNecessary(
			ParserContext parserContext, Element sourceElement) {

		BeanDefinition beanDefinition = AopConfigUtils.registerAspectJAutoProxyCreatorIfNecessary(
				parserContext.getRegistry(), parserContext.extractSource(sourceElement));
		useClassProxyingIfNecessary(parserContext.getRegistry(), sourceElement);
		registerComponentIfNecessary(beanDefinition, parserContext);
	}

	/// 注册AnnotationAutoProxyCreator
	/// 在registerAspectJAnnotationAutoProxyCreatorIfNecessary方法中主要完成了3件事，基本上每行代码就是一个完整的逻辑
	//	1.注册或升级AnnotationAwareAspectJAutoProxyCreator
	//		对于Aop的实现基本上都是靠AnnotationAwareAspectJAutoProxyCreator去完成，它可以根据@PointCut注解定义的切点来自动代理相匹配的bean。
	//	但是为了配置简单，Spring使用了自定义配置来帮助我们自动注册AnnotationAwareAspectJAutoProxyCreator，其注册过程就是在这里实现的。
	//	2.处理proxy-target-class以及expose-proxy属性。
	///	（1）proxy-target-class属性值决定是基于接口的还是基于类的代理被创建。
	// 		首先说明下proxy-target-class="true"和proxy-target-class="false"的区别，
	// 		为true则是基于类的代理将起作用（需要cglib库），
	// 		为false或者省略这个属性，则标准的JDK 基于接口的代理将起作用。
	///		proxy-target-class在spring事务、aop、缓存这几块都有设置，其作用都是一样的。
	///		<tx:annotation-driven transaction-manager="transactionManager" proxy-target-class="true"/>
	//		<aop:config proxy-target-class="true">
	//		<cache:annotation-driven proxy-target-class="true"/>
	//	  proxy-target-class: Spring Aop部分使用JDK动态代理或者CGLIB来为目标对象创建代理。（建议尽量使用JDK的动态代理），如果
	//	 被代理的目标对象实现了至少一个接口，则会使用JDK的动态代理。所有该目标类型实现的接口都将被代理。若该目标对象没有实现任何接口，
	//	 则创建一个CGLIB代理。如果你希望强制使用CGLIB代理，（例如希望代理目标对象的所有方法，而不只是实现自接口的方法）那也可以。但是要
	//	 考虑以下两个问题：
	//		a.无法通知（advise）Final方法，因爲它们不能被复写
	//		b.你需要将CGLIB二进制发行包放在classpath下面。
	//	 与之相较，JDK本身就提供了动态代理，强制使用CGLIB代理需要将<aop:config>的proxy-target-class属性设为true:
	//		<aop:config proxy-target-class="true">...</aop:config>
	//	 当需要使用CGLIB代理和@AspectJ自动代理支持，可以按照以下方式设置<aop:aspectj-autoproxy>的proxy-target-class属性：
	//		<aop:aspectj-autoproxy proxy-target-class="true"/>
	//	而实际使用的过程中才会发现细节问题的差别，
	//		c.JDK动态代理：其代理对象必须是摸个接口的实现，它是通过在运行期间创建一个接口的实现类来完成对目标对象的代理。
	//		d.CGLIB代理：实现原理类似于JDK动态代理，只是它在运行期间生成的代理对象时针对目标类扩展子类。CGLIB是高效的代码生成包，底层是依靠
	///		ASM(开源的java字节码编辑类库)操作字节码实现的，性能比JDK强。
	//	（2）expose-proxy属性：有时候目标对象内部的自我调用将无法实施切面中的增强，如下了所示：

	//   	public interface AService{
	//   		public void a();
	//			public void b();
	//   	}

	//	@Service()
	//	public class AserviceImpl1 implements AService{
	//		@Transactional(propagation = Propagation.REQUIRED)
	//		public void a(){
	//			this.b();
	//		}
	//		@Transactional(propagation = Propagation.REQUIRES_NEW)
	//		public void b(){
	//
	//		}
	//	}
	//		此处的this指向目标对象，因此调用this.b()将不会执行b事务切面，即不会执行事务增强，因此b方法的事务定义
	//	"@Transactional((propagation = Propagation.REQUIRES_NEW)"将不会实施，为了解决这个问题，我们可以这样做
	//	 <aop:aspectj-autoproxy expose-proxy="true"/>
	// 		然后将以上代码中的"this.b();"修改为"((Aservice)AopContext.currentProxy()).b();"即可。
	//	通过以上的修改便可以完成a和b方法的同时增强。
	//	跳过aop生成代理的过程，直接看代理的调用的地方，以JdkDynamicAopProxy为例，
	//	cglib方式的地方与jdk一致。对JDK方式的动态代理，都是通过invoke方法执行目标对象的方法，
	//	那么来看一下spring中JdkDynamicAopProxy的invoke方法源码。

	//		if (this.advised.exposeProxy) {
	//			// Make invocation available if necessary.
	//				oldProxy = AopContext.setCurrentProxy(proxy);
	//				setProxyContext = true;
	//		}

	//		if (setProxyContext) {
	//			// Restore old proxy.
	//			AopContext.setCurrentProxy(oldProxy);
	//			}

	// AopContext源碼
	//	@Nullable
	//	static Object setCurrentProxy(@Nullable Object proxy) {
	//		Object old = currentProxy.get();
	//		if (proxy != null) {
	//			currentProxy.set(proxy);
	//		}
	//		else {
	//			currentProxy.remove();
	//		}
	//		return old;
	//	}


	//	public static Object currentProxy() throws IllegalStateException {
	//		Object proxy = currentProxy.get();
	//		if (proxy == null) {
	//			throw new IllegalStateException(
	//					"Cannot find current proxy: Set 'exposeProxy' property on Advised to 'true' to make it available.");
	//		}
	//		return proxy;
	//	}

	//	那么之前在问题中，a中调用b的时候通过从ThreadLocal中获取到当前代理再执行b方法就可以达到b中也有事务的要求了。
	//	简单来说，处理的方法就是设置expose-proxy属性为true暴露代理。
	public static void registerAspectJAnnotationAutoProxyCreatorIfNecessary(
			ParserContext parserContext, Element sourceElement) {
			///注册或升级AutoProxyCreator定义beanName为org.springframework.aop.config.internalAutoProxyCreator的BeanDefinition
		BeanDefinition beanDefinition = AopConfigUtils.registerAspectJAnnotationAutoProxyCreatorIfNecessary(
				parserContext.getRegistry(), parserContext.extractSource(sourceElement));
			///对于proxy-target-class以及expose-proxy属性的处理
		useClassProxyingIfNecessary(parserContext.getRegistry(), sourceElement);
			///注册组件并通知，便于监听器做进一步处理
			///其中BeanDefinition的className为AnnotationAwareAspectJAutoProxyCreator。
		registerComponentIfNecessary(beanDefinition, parserContext);
	}

	private static void useClassProxyingIfNecessary(BeanDefinitionRegistry registry, @Nullable Element sourceElement) {
		if (sourceElement != null) {
			//对于proxy-target-class属性的处理
			boolean proxyTargetClass = Boolean.valueOf(sourceElement.getAttribute(PROXY_TARGET_CLASS_ATTRIBUTE));
			if (proxyTargetClass) {
				AopConfigUtils.forceAutoProxyCreatorToUseClassProxying(registry);
			}
			//对于expose-proxy属性的处理
			boolean exposeProxy = Boolean.valueOf(sourceElement.getAttribute(EXPOSE_PROXY_ATTRIBUTE));
			if (exposeProxy) {
				AopConfigUtils.forceAutoProxyCreatorToExposeProxy(registry);
			}
		}
	}

	private static void registerComponentIfNecessary(@Nullable BeanDefinition beanDefinition, ParserContext parserContext) {
		if (beanDefinition != null) {
			BeanComponentDefinition componentDefinition =
					new BeanComponentDefinition(beanDefinition, AopConfigUtils.AUTO_PROXY_CREATOR_BEAN_NAME);
			parserContext.registerComponent(componentDefinition);
		}
	}

}
