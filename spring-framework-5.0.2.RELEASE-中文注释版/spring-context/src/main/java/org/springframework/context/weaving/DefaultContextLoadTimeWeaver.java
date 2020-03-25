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

package org.springframework.context.weaving;

import java.lang.instrument.ClassFileTransformer;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.beans.factory.BeanClassLoaderAware;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.instrument.InstrumentationSavingAgent;
import org.springframework.instrument.classloading.InstrumentationLoadTimeWeaver;
import org.springframework.instrument.classloading.LoadTimeWeaver;
import org.springframework.instrument.classloading.ReflectiveLoadTimeWeaver;
import org.springframework.instrument.classloading.glassfish.GlassFishLoadTimeWeaver;
import org.springframework.instrument.classloading.jboss.JBossLoadTimeWeaver;
import org.springframework.instrument.classloading.tomcat.TomcatLoadTimeWeaver;
import org.springframework.instrument.classloading.weblogic.WebLogicLoadTimeWeaver;
import org.springframework.instrument.classloading.websphere.WebSphereLoadTimeWeaver;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;

/**
 * Default {@link LoadTimeWeaver} bean for use in an application context,
 * decorating an automatically detected internal {@code LoadTimeWeaver}.
 *
 * <p>Typically registered for the default bean name
 * "{@code loadTimeWeaver}"; the most convenient way to achieve this is
 * Spring's {@code <context:load-time-weaver>} XML tag.
 *
 * <p>This class implements a runtime environment check for obtaining the
 * appropriate weaver implementation: As of Spring Framework 5.0, it detects
 * Oracle WebLogic 10+, GlassFish 4+, Tomcat 8+, WildFly 8+, IBM WebSphere 8.5+,
 * {@link InstrumentationSavingAgent Spring's VM agent}, and any {@link ClassLoader}
 * supported by Spring's {@link ReflectiveLoadTimeWeaver}.
 *
 * @author Juergen Hoeller
 * @author Ramnivas Laddad
 * @author Costin Leau
 * @since 2.5
 * @see org.springframework.context.ConfigurableApplicationContext#LOAD_TIME_WEAVER_BEAN_NAME
 */
public class DefaultContextLoadTimeWeaver implements LoadTimeWeaver, BeanClassLoaderAware, DisposableBean {

	protected final Log logger = LogFactory.getLog(getClass());

	@Nullable
	private LoadTimeWeaver loadTimeWeaver;


	public DefaultContextLoadTimeWeaver() {
	}

	public DefaultContextLoadTimeWeaver(ClassLoader beanClassLoader) {
		setBeanClassLoader(beanClassLoader);
	}


	///		而 DefaultContextLoadTimeWeaver 类又同时实现了LoadTimeWeaver、BeanClassLoaderAware 以及 DisposableBean。
	/// 其中 DisposableBean 接口保证在 bean 销毁时会调用 destory 方法进行 bean 的清理，而 BeanClassLoaderAware 接口则保证在 bean 的初始化调用
	/// AbstractAutowireCapableBeanFactory 的 invokeAwareMethods 时调用 setBeanClassLoader 方法。

//
//		上面的函数中有一句很容易被忽略但是很关键的代码：
//				this.loadTimeWeaver = new InstrumentationLoadTimeWeaver(classLoader);
//
//		这句代码不仅仅是实例化了一个 InstrumentationLoadTimeWeaver 类型的实例，而且在实例化过程中还做了一些额外的操作。
//	 在实例化过程中判断了当前是否存在 Instrumentation 实例，最终会取 InstrumentationSavingAgent 类中的 instrumentation 的静态属性，
//	 判断这个属性是否是 null，InstrumentationSavingAgent 这个类是 spring-instrument-3.2.9.RELEASE.jar 的代理入口类，当应用程序启动时启动了
//	 spring-instrument-3.2.9.RELEASE.jar 代理时，即在虚拟机参数中设置了 -javaagent 参数，虚拟机会创建 Instrumentation 实例并传递给 premain 方法，
//	InstrumentationSavingAgent 会把这个类保存在 instrumentation 静态属性中。
//		所以在程序启动时启动了代理时 InstrumentationLoadTimeWeaver.isInstrumentationAvailable()
//	这个方法是返回 true 的，所以 loadTimeWeaver 属性会设置成 InstrumentationLoadTimeWeaver 对象。对于注册转换器，如 addTransformer 函数等，便可以直接使用此属性(instrumentation)进行操作了。
	@Override
	public void setBeanClassLoader(ClassLoader classLoader) {
		LoadTimeWeaver serverSpecificLoadTimeWeaver = createServerSpecificLoadTimeWeaver(classLoader);
		if (serverSpecificLoadTimeWeaver != null) {
			if (logger.isInfoEnabled()) {
				logger.info("Determined server-specific load-time weaver: " +
						serverSpecificLoadTimeWeaver.getClass().getName());
			}
			this.loadTimeWeaver = serverSpecificLoadTimeWeaver;
		}
		else if (InstrumentationLoadTimeWeaver.isInstrumentationAvailable()) {
			/// 	检查当前虚拟中的 Instrumentation 实例是否可用
			logger.info("Found Spring's JVM agent for instrumentation");
			///	这就代码不仅仅是是例化了一个 InstrumenttaionLoadTimeWeaver 类型的实例，而且在实例化过程中还做了一些额外的操作。
			this.loadTimeWeaver = new InstrumentationLoadTimeWeaver(classLoader);
		}
		else {
			try {
				this.loadTimeWeaver = new ReflectiveLoadTimeWeaver(classLoader);
				if (logger.isInfoEnabled()) {
					logger.info("Using a reflective load-time weaver for class loader: " +
							this.loadTimeWeaver.getInstrumentableClassLoader().getClass().getName());
				}
			}
			catch (IllegalStateException ex) {
				throw new IllegalStateException(ex.getMessage() + " Specify a custom LoadTimeWeaver or start your " +
						"Java virtual machine with Spring's agent: -javaagent:org.springframework.instrument.jar");
			}
		}
	}

	/*
	 * This method never fails, allowing to try other possible ways to use an
	 * server-agnostic weaver. This non-failure logic is required since
	 * determining a load-time weaver based on the ClassLoader name alone may
	 * legitimately fail due to other mismatches. Specific case in point: the
	 * use of WebLogicLoadTimeWeaver works for WLS 10 but fails due to the lack
	 * of a specific method (addInstanceClassPreProcessor) for any earlier
	 * versions even though the ClassLoader name is the same.
	 */
	@Nullable
	protected LoadTimeWeaver createServerSpecificLoadTimeWeaver(ClassLoader classLoader) {
		String name = classLoader.getClass().getName();
		try {
			if (name.startsWith("weblogic")) {
				return new WebLogicLoadTimeWeaver(classLoader);
			}
			else if (name.startsWith("org.glassfish")) {
				return new GlassFishLoadTimeWeaver(classLoader);
			}
			else if (name.startsWith("org.apache.catalina")) {
				return new TomcatLoadTimeWeaver(classLoader);
			}
			else if (name.startsWith("org.jboss")) {
				return new JBossLoadTimeWeaver(classLoader);
			}
			else if (name.startsWith("com.ibm")) {
				return new WebSphereLoadTimeWeaver(classLoader);
			}
		}
		catch (IllegalStateException ex) {
			if (logger.isInfoEnabled()) {
				logger.info("Could not obtain server-specific LoadTimeWeaver: " + ex.getMessage());
			}
		}
		return null;
	}

	@Override
	public void destroy() {
		if (this.loadTimeWeaver instanceof InstrumentationLoadTimeWeaver) {
			if (logger.isInfoEnabled()) {
				logger.info("Removing all registered transformers for class loader: " +
						this.loadTimeWeaver.getInstrumentableClassLoader().getClass().getName());
			}
			((InstrumentationLoadTimeWeaver) this.loadTimeWeaver).removeTransformers();
		}
	}


	@Override
	public void addTransformer(ClassFileTransformer transformer) {
		Assert.state(this.loadTimeWeaver != null, "Not initialized");
		this.loadTimeWeaver.addTransformer(transformer);
	}

	@Override
	public ClassLoader getInstrumentableClassLoader() {
		Assert.state(this.loadTimeWeaver != null, "Not initialized");
		return this.loadTimeWeaver.getInstrumentableClassLoader();
	}

	@Override
	public ClassLoader getThrowawayClassLoader() {
		Assert.state(this.loadTimeWeaver != null, "Not initialized");
		return this.loadTimeWeaver.getThrowawayClassLoader();
	}

}
