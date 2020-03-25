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

package org.springframework.instrument.classloading;

import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.lang.instrument.Instrumentation;
import java.security.ProtectionDomain;
import java.util.ArrayList;
import java.util.List;

import org.springframework.instrument.InstrumentationSavingAgent;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;

/**
 * {@link LoadTimeWeaver} relying on VM {@link Instrumentation}.
 *
 * <p>Start the JVM specifying the Java agent to be used, like as follows:
 *
 * <p><code class="code">-javaagent:path/to/org.springframework.instrument.jar</code>
 *
 * <p>where {@code org.springframework.instrument.jar} is a JAR file containing
 * the {@link InstrumentationSavingAgent} class, as shipped with Spring.
 *
 * <p>In Eclipse, for example, set the "Run configuration"'s JVM args to be of the form:
 *
 * <p><code class="code">-javaagent:${project_loc}/lib/org.springframework.instrument.jar</code>
 *
 * @author Rod Johnson
 * @author Juergen Hoeller
 * @since 2.0
 * @see InstrumentationSavingAgent
 */
public class InstrumentationLoadTimeWeaver implements LoadTimeWeaver {

	private static final boolean AGENT_CLASS_PRESENT = ClassUtils.isPresent(
			"org.springframework.instrument.InstrumentationSavingAgent",
			InstrumentationLoadTimeWeaver.class.getClassLoader());


	@Nullable
	private final ClassLoader classLoader;


	/// https://www.cnblogs.com/wade-luffy/p/6078301.html
	@Nullable
	private final Instrumentation instrumentation;

	private final List<ClassFileTransformer> transformers = new ArrayList<>(4);


	/**
	 * Create a new InstrumentationLoadTimeWeaver for the default ClassLoader.
	 */
	public InstrumentationLoadTimeWeaver() {
		this(ClassUtils.getDefaultClassLoader());
	}

	/**
	 * Create a new InstrumentationLoadTimeWeaver for the given ClassLoader.
	 * @param classLoader the ClassLoader that registered transformers are supposed to apply to
	 */
	///		在实例化的过程中会对当前的 this.instrumentation属性进行初始化，而初始化的代码如下:this.instrumentation = getInstrumentation()，
	///也就是说在InstrumentationLoadTimeWeaver实例化后，其属性Instrumentation已经被初始化为代表着当前虚拟机的实例了。综合我们讲过的例子，对于
	///注册转换器，如addTransformer函数等，在Spring中的bean之前的关系如下：
	/// 	a.AspectJWeavingEnable类型的bean中的loadTimeWeaver属性被初始化为DefaultContextLoadTimeWeaver类型的bean。
	///		b.DefualtContextLoadTimeWeaver类型的bean中的LoadTimeWeaver属性被初始化为InstrumentationLoadTimeWeaver。
	///		因为AspectJWeavingEnable类同样实现BeanFactoryPostProcessor，所以当所有bean解析结束后会调用其postProcessBeanFactory方法
	public InstrumentationLoadTimeWeaver(@Nullable ClassLoader classLoader) {
		this.classLoader = classLoader;
		this.instrumentation = getInstrumentation();
	}


	/// 这个方法中，把类转换器 actualTransformer 通过 instrumentation 实例注册给了虚拟机。这里采用了修饰器模式，actualTransformer 对 transformer 进行修改封装
	@Override
	public void addTransformer(ClassFileTransformer transformer) {
		Assert.notNull(transformer, "Transformer must not be null");
		FilteringClassFileTransformer actualTransformer =
				new FilteringClassFileTransformer(transformer, this.classLoader);
		synchronized (this.transformers) {
			Assert.state(this.instrumentation != null,
					"Must start with Java agent to use InstrumentationLoadTimeWeaver. See Spring documentation.");
			//加入到 jdk的 instrumentation 中加载 class 时自动调用
			this.instrumentation.addTransformer(actualTransformer);
			this.transformers.add(actualTransformer);
		}
	}

	/**
	 * We have the ability to weave the current class loader when starting the
	 * JVM in this way, so the instrumentable class loader will always be the
	 * current loader.
	 */
	@Override
	public ClassLoader getInstrumentableClassLoader() {
		Assert.state(this.classLoader != null, "No ClassLoader available");
		return this.classLoader;
	}

	/**
	 * This implementation always returns a {@link SimpleThrowawayClassLoader}.
	 */
	@Override
	public ClassLoader getThrowawayClassLoader() {
		return new SimpleThrowawayClassLoader(getInstrumentableClassLoader());
	}

	/**
	 * Remove all registered transformers, in inverse order of registration.
	 */
	public void removeTransformers() {
		synchronized (this.transformers) {
			if (this.instrumentation != null && !this.transformers.isEmpty()) {
				for (int i = this.transformers.size() - 1; i >= 0; i--) {
					this.instrumentation.removeTransformer(this.transformers.get(i));
				}
				this.transformers.clear();
			}
		}
	}


	/**
	 * Check whether an Instrumentation instance is available for the current VM.
	 * @see #getInstrumentation()
	 */
	public static boolean isInstrumentationAvailable() {
		return (getInstrumentation() != null);
	}

	/**
	 * Obtain the Instrumentation instance for the current VM, if available.
	 * @return the Instrumentation instance, or {@code null} if none found
	 * @see #isInstrumentationAvailable()
	 */
	@Nullable
	private static Instrumentation getInstrumentation() {
		if (AGENT_CLASS_PRESENT) {
			return InstrumentationAccessor.getInstrumentation();
		}
		else {
			return null;
		}
	}


	/**
	 * Inner class to avoid InstrumentationSavingAgent dependency.
	 */
	private static class InstrumentationAccessor {

		public static Instrumentation getInstrumentation() {
			return InstrumentationSavingAgent.getInstrumentation();
		}
	}


	/**
	 * Decorator that only applies the given target transformer to a specific ClassLoader.
	 */
	private static class FilteringClassFileTransformer implements ClassFileTransformer {

		private final ClassFileTransformer targetTransformer;

		@Nullable
		private final ClassLoader targetClassLoader;

		public FilteringClassFileTransformer(
				ClassFileTransformer targetTransformer, @Nullable ClassLoader targetClassLoader) {

			this.targetTransformer = targetTransformer;
			this.targetClassLoader = targetClassLoader;
		}

///		装饰模式

//			这里面的 targetClassLoader 就是容器的 bean 类加载，在进行类字节码转换之前先判断执行类加载的加载器是否是 bean类 加载器，
//		如果不是的话跳过类装换逻辑直接返回null，返回null的意思就是不执行类转换还是使用原始的类字节码。什么情况下会有类加载不是 bean 的类加载器的情况？
//		AbstractApplicationContext 的 prepareBeanFactory 方法中有一行代码：

//		// Detect a LoadTimeWeaver and prepare for weaving, if found.
//		if (beanFactory.containsBean(LOAD_TIME_WEAVER_BEAN_NAME)) {
//			beanFactory.addBeanPostProcessor(new LoadTimeWeaverAwareProcessor(beanFactory));
//			// Set a temporary ClassLoader for type matching.
//			beanFactory.setTempClassLoader(new ContextTypeMatchClassLoader(beanFactory.getBeanClassLoader()));
//		}

//			当容器中注册了 loadTimeWeaver 之后会给容器设置一个 ContextTypeMatchClassLoader 类型的临时类加载器，在织入切面时只有在 bean 实例化时织入切面才有意义，
//		在进行一些 类型比较 或者 校验 的时候，比如判断一个 bean 是否是 FactoryBean、BPP、BFPP，这时候不涉及到实例化，所以做字节码转换没有任何意义，而且还会增加无谓的性能消耗，
//		所以在进行这些类型比较时使用这个临时的类加载器执行类加载，这样在上面的 transform 方法就会因为类加载不匹配而跳过字节码转换，这里有一点非常关键的是，
//		ContextTypeMatchClassLoader 的父类加载就是容器 bean类加载器，所以 ContextTypeMatchClassLoader 类加载器是不遵循“双亲委派”的，因为如果它遵循了“双亲委派”，
//		那么它的类加载工作还是会委托给 bean类 加载器，这样的话 if 里面的条件就不会匹配，还是会执行类转换。ContextTypeMatchClassLoader 的类加载工作会委托给
//		ContextOverridingClassLoader 类对象，有兴趣可以看看 ContextOverridingClassLoader 和 OverridingClassLoader 这两个类的代码。
//		这个临时的类加载器会在容器初始化快结束时，容器bean实例化之前被清掉，代码在AbstractApplicationContext类的finishBeanFactoryInitialization方法：

//		protected void finishBeanFactoryInitialization(ConfigurableListableBeanFactory beanFactory) {
//    			...
//			beanFactory.setTempClassLoader(null);
//			// Allow for caching all bean definition metadata, not expecting further changes.
//			beanFactory.freezeConfiguration();
//			// Instantiate all remaining (non-lazy-init) singletons.
//			beanFactory.preInstantiateSingletons();
//		}
		@Override
		@Nullable
		public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined,
				ProtectionDomain protectionDomain, byte[] classfileBuffer) throws IllegalClassFormatException {

			if (this.targetClassLoader != loader) {
				return null;
			}
			return this.targetTransformer.transform(
					loader, className, classBeingRedefined, protectionDomain, classfileBuffer);
		}

		@Override
		public String toString() {
			return "FilteringClassFileTransformer for: " + this.targetTransformer.toString();
		}
	}

}
