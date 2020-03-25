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

package org.springframework.beans.factory.support;

import java.beans.PropertyDescriptor;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Supplier;

import org.springframework.beans.BeanUtils;
import org.springframework.beans.BeanWrapper;
import org.springframework.beans.BeanWrapperImpl;
import org.springframework.beans.BeansException;
import org.springframework.beans.MutablePropertyValues;
import org.springframework.beans.PropertyAccessorUtils;
import org.springframework.beans.PropertyValue;
import org.springframework.beans.PropertyValues;
import org.springframework.beans.TypeConverter;
import org.springframework.beans.factory.Aware;
import org.springframework.beans.factory.BeanClassLoaderAware;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.beans.factory.BeanCurrentlyInCreationException;
import org.springframework.beans.factory.BeanDefinitionStoreException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.BeanNameAware;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.UnsatisfiedDependencyException;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.beans.factory.config.ConstructorArgumentValues;
import org.springframework.beans.factory.config.DependencyDescriptor;
import org.springframework.beans.factory.config.InstantiationAwareBeanPostProcessor;
import org.springframework.beans.factory.config.SmartInstantiationAwareBeanPostProcessor;
import org.springframework.beans.factory.config.TypedStringValue;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.GenericTypeResolver;
import org.springframework.core.MethodParameter;
import org.springframework.core.NamedThreadLocal;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.core.PriorityOrdered;
import org.springframework.core.ResolvableType;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.ObjectUtils;
import org.springframework.util.ReflectionUtils;
import org.springframework.util.StringUtils;

/**
 * Abstract bean factory superclass that implements default bean creation,
 * with the full capabilities specified by the {@link RootBeanDefinition} class.
 * Implements the {@link org.springframework.beans.factory.config.AutowireCapableBeanFactory}
 * interface in addition to AbstractBeanFactory's {@link #createBean} method.
 *
 * <p>Provides bean creation (with constructor resolution), property population,
 * wiring (including autowiring), and initialization. Handles runtime bean
 * references, resolves managed collections, calls initialization methods, etc.
 * Supports autowiring constructors, properties by name, and properties by type.
 *
 * <p>The main template method to be implemented by subclasses is
 * {@link #resolveDependency(DependencyDescriptor, String, Set, TypeConverter)},
 * used for autowiring by type. In case of a factory which is capable of searching
 * its bean definitions, matching beans will typically be implemented through such
 * a search. For other factory styles, simplified matching algorithms can be implemented.
 *
 * <p>Note that this class does <i>not</i> assume or implement bean definition
 * registry capabilities. See {@link DefaultListableBeanFactory} for an implementation
 * of the {@link org.springframework.beans.factory.ListableBeanFactory} and
 * {@link BeanDefinitionRegistry} interfaces, which represent the API and SPI
 * view of such a factory, respectively.
 *
 * @author Rod Johnson
 * @author Juergen Hoeller
 * @author Rob Harrop
 * @author Mark Fisher
 * @author Costin Leau
 * @author Chris Beams
 * @author Sam Brannen
 * @since 13.02.2004
 * @see RootBeanDefinition
 * @see DefaultListableBeanFactory
 * @see BeanDefinitionRegistry
 */
public abstract class AbstractAutowireCapableBeanFactory extends AbstractBeanFactory
		implements AutowireCapableBeanFactory {

	/** Strategy for creating bean instances */
	private InstantiationStrategy instantiationStrategy = new CglibSubclassingInstantiationStrategy();

	/** Resolver strategy for method parameter names */
	@Nullable
	private ParameterNameDiscoverer parameterNameDiscoverer = new DefaultParameterNameDiscoverer();

	/** Whether to automatically try to resolve circular references between beans */
	private boolean allowCircularReferences = true;

	/**
	 * Whether to resort to injecting a raw bean instance in case of circular reference,
	 * even if the injected bean eventually got wrapped.
	 */
	private boolean allowRawInjectionDespiteWrapping = false;

	/**
	 * Dependency types to ignore on dependency check and autowire, as Set of
	 * Class objects: for example, String. Default is none.
	 */
	private final Set<Class<?>> ignoredDependencyTypes = new HashSet<>();

	/// 忽略自动装配的接口
	/**
	 * Dependency interfaces to ignore on dependency check and autowire, as Set of
	 * Class objects. By default, only the BeanFactory interface is ignored.
	 */
	private final Set<Class<?>> ignoredDependencyInterfaces = new HashSet<>();

	/**
	 * The name of the currently created bean, for implicit dependency registration
	 * on getBean etc invocations triggered from a user-specified Supplier callback.
	 */
	private final NamedThreadLocal<String> currentlyCreatedBean = new NamedThreadLocal<>("Currently created bean");

	/** Cache of unfinished FactoryBean instances: FactoryBean name --> BeanWrapper */
	private final Map<String, BeanWrapper> factoryBeanInstanceCache = new ConcurrentHashMap<>(16);

	/** Cache of filtered PropertyDescriptors: bean Class -> PropertyDescriptor array */
	private final ConcurrentMap<Class<?>, PropertyDescriptor[]> filteredPropertyDescriptorsCache =
			new ConcurrentHashMap<>(256);


	/**
	 * Create a new AbstractAutowireCapableBeanFactory.
	 */
	public AbstractAutowireCapableBeanFactory() {
		super();
		ignoreDependencyInterface(BeanNameAware.class);
		ignoreDependencyInterface(BeanFactoryAware.class);
		ignoreDependencyInterface(BeanClassLoaderAware.class);
	}

	/**
	 * Create a new AbstractAutowireCapableBeanFactory with the given parent.
	 * @param parentBeanFactory parent bean factory, or {@code null} if none
	 */
	public AbstractAutowireCapableBeanFactory(@Nullable BeanFactory parentBeanFactory) {
		this();
		setParentBeanFactory(parentBeanFactory);
	}


	/**
	 * Set the instantiation strategy to use for creating bean instances.
	 * Default is CglibSubclassingInstantiationStrategy.
	 * @see CglibSubclassingInstantiationStrategy
	 */
	public void setInstantiationStrategy(InstantiationStrategy instantiationStrategy) {
		this.instantiationStrategy = instantiationStrategy;
	}

	/**
	 * Return the instantiation strategy to use for creating bean instances.
	 */
	protected InstantiationStrategy getInstantiationStrategy() {
		return this.instantiationStrategy;
	}

	/**
	 * Set the ParameterNameDiscoverer to use for resolving method parameter
	 * names if needed (e.g. for constructor names).
	 * <p>Default is a {@link DefaultParameterNameDiscoverer}.
	 */
	public void setParameterNameDiscoverer(@Nullable ParameterNameDiscoverer parameterNameDiscoverer) {
		this.parameterNameDiscoverer = parameterNameDiscoverer;
	}

	/**
	 * Return the ParameterNameDiscoverer to use for resolving method parameter
	 * names if needed.
	 */
	@Nullable
	protected ParameterNameDiscoverer getParameterNameDiscoverer() {
		return this.parameterNameDiscoverer;
	}

	/**
	 * Set whether to allow circular references between beans - and automatically
	 * try to resolve them.
	 * <p>Note that circular reference resolution means that one of the involved beans
	 * will receive a reference to another bean that is not fully initialized yet.
	 * This can lead to subtle and not-so-subtle side effects on initialization;
	 * it does work fine for many scenarios, though.
	 * <p>Default is "true". Turn this off to throw an exception when encountering
	 * a circular reference, disallowing them completely.
	 * <p><b>NOTE:</b> It is generally recommended to not rely on circular references
	 * between your beans. Refactor your application logic to have the two beans
	 * involved delegate to a third bean that encapsulates their common logic.
	 */
	public void setAllowCircularReferences(boolean allowCircularReferences) {
		this.allowCircularReferences = allowCircularReferences;
	}

	/**
	 * Set whether to allow the raw injection of a bean instance into some other
	 * bean's property, despite the injected bean eventually getting wrapped
	 * (for example, through AOP auto-proxying).
	 * <p>This will only be used as a last resort in case of a circular reference
	 * that cannot be resolved otherwise: essentially, preferring a raw instance
	 * getting injected over a failure of the entire bean wiring process.
	 * <p>Default is "false", as of Spring 2.0. Turn this on to allow for non-wrapped
	 * raw beans injected into some of your references, which was Spring 1.2's
	 * (arguably unclean) default behavior.
	 * <p><b>NOTE:</b> It is generally recommended to not rely on circular references
	 * between your beans, in particular with auto-proxying involved.
	 * @see #setAllowCircularReferences
	 */
	public void setAllowRawInjectionDespiteWrapping(boolean allowRawInjectionDespiteWrapping) {
		this.allowRawInjectionDespiteWrapping = allowRawInjectionDespiteWrapping;
	}

	/**
	 * Ignore the given dependency type for autowiring:
	 * for example, String. Default is none.
	 */
	public void ignoreDependencyType(Class<?> type) {
		this.ignoredDependencyTypes.add(type);
	}

	/**
	 * Ignore the given dependency interface for autowiring.
	 * <p>This will typically be used by application contexts to register
	 * dependencies that are resolved in other ways, like BeanFactory through
	 * BeanFactoryAware or ApplicationContext through ApplicationContextAware.
	 * <p>By default, only the BeanFactoryAware interface is ignored.
	 * For further types to ignore, invoke this method for each type.
	 * @see org.springframework.beans.factory.BeanFactoryAware
	 * @see org.springframework.context.ApplicationContextAware
	 */
	public void ignoreDependencyInterface(Class<?> ifc) {
		this.ignoredDependencyInterfaces.add(ifc);
	}

	@Override
	public void copyConfigurationFrom(ConfigurableBeanFactory otherFactory) {
		super.copyConfigurationFrom(otherFactory);
		if (otherFactory instanceof AbstractAutowireCapableBeanFactory) {
			AbstractAutowireCapableBeanFactory otherAutowireFactory =
					(AbstractAutowireCapableBeanFactory) otherFactory;
			this.instantiationStrategy = otherAutowireFactory.instantiationStrategy;
			this.allowCircularReferences = otherAutowireFactory.allowCircularReferences;
			this.ignoredDependencyTypes.addAll(otherAutowireFactory.ignoredDependencyTypes);
			this.ignoredDependencyInterfaces.addAll(otherAutowireFactory.ignoredDependencyInterfaces);
		}
	}


	//-------------------------------------------------------------------------
	// Typical methods for creating and populating external bean instances
	//-------------------------------------------------------------------------

	@Override
	@SuppressWarnings("unchecked")
	public <T> T createBean(Class<T> beanClass) throws BeansException {
		// Use prototype bean definition, to avoid registering bean as dependent bean.
		RootBeanDefinition bd = new RootBeanDefinition(beanClass);
		bd.setScope(SCOPE_PROTOTYPE);
		bd.allowCaching = ClassUtils.isCacheSafe(beanClass, getBeanClassLoader());
		// For the nullability warning, see the elaboration in AbstractBeanFactory.doGetBean;
		// in short: This is never going to be null unless user-declared code enforces null.
		return (T) createBean(beanClass.getName(), bd, null);
	}

	@Override
	public void autowireBean(Object existingBean) {
		// Use non-singleton bean definition, to avoid registering bean as dependent bean.
		RootBeanDefinition bd = new RootBeanDefinition(ClassUtils.getUserClass(existingBean));
		bd.setScope(BeanDefinition.SCOPE_PROTOTYPE);
		bd.allowCaching = ClassUtils.isCacheSafe(bd.getBeanClass(), getBeanClassLoader());
		BeanWrapper bw = new BeanWrapperImpl(existingBean);
		initBeanWrapper(bw);
		populateBean(bd.getBeanClass().getName(), bd, bw);
	}

	@Override
	public Object configureBean(Object existingBean, String beanName) throws BeansException {
		markBeanAsCreated(beanName);
		BeanDefinition mbd = getMergedBeanDefinition(beanName);
		RootBeanDefinition bd = null;
		if (mbd instanceof RootBeanDefinition) {
			RootBeanDefinition rbd = (RootBeanDefinition) mbd;
			bd = (rbd.isPrototype() ? rbd : rbd.cloneBeanDefinition());
		}
		if (bd == null) {
			bd = new RootBeanDefinition(mbd);
		}
		if (!bd.isPrototype()) {
			bd.setScope(BeanDefinition.SCOPE_PROTOTYPE);
			bd.allowCaching = ClassUtils.isCacheSafe(ClassUtils.getUserClass(existingBean), getBeanClassLoader());
		}
		BeanWrapper bw = new BeanWrapperImpl(existingBean);
		initBeanWrapper(bw);
		populateBean(beanName, bd, bw);
		// For the nullability warning, see the elaboration in AbstractBeanFactory.doGetBean;
		// in short: This is never going to be null unless user-declared code enforces null.
		return initializeBean(beanName, existingBean, bd);
	}

	@Override
	@Nullable
	public Object resolveDependency(DependencyDescriptor descriptor, @Nullable String requestingBeanName) throws BeansException {
		return resolveDependency(descriptor, requestingBeanName, null, null);
	}


	//-------------------------------------------------------------------------
	// Specialized methods for fine-grained control over the bean lifecycle
	//-------------------------------------------------------------------------

	@Override
	public Object createBean(Class<?> beanClass, int autowireMode, boolean dependencyCheck) throws BeansException {
		// Use non-singleton bean definition, to avoid registering bean as dependent bean.
		RootBeanDefinition bd = new RootBeanDefinition(beanClass, autowireMode, dependencyCheck);
		bd.setScope(BeanDefinition.SCOPE_PROTOTYPE);
		// For the nullability warning, see the elaboration in AbstractBeanFactory.doGetBean;
		// in short: This is never going to be null unless user-declared code enforces null.
		return createBean(beanClass.getName(), bd, null);
	}

	@Override
	public Object autowire(Class<?> beanClass, int autowireMode, boolean dependencyCheck) throws BeansException {
		// Use non-singleton bean definition, to avoid registering bean as dependent bean.
		final RootBeanDefinition bd = new RootBeanDefinition(beanClass, autowireMode, dependencyCheck);
		bd.setScope(BeanDefinition.SCOPE_PROTOTYPE);
		if (bd.getResolvedAutowireMode() == AUTOWIRE_CONSTRUCTOR) {
			return autowireConstructor(beanClass.getName(), bd, null, null).getWrappedInstance();
		}
		else {
			Object bean;
			final BeanFactory parent = this;
			if (System.getSecurityManager() != null) {
				bean = AccessController.doPrivileged((PrivilegedAction<Object>) () ->
						getInstantiationStrategy().instantiate(bd, null, parent),
						getAccessControlContext());
			}
			else {
				bean = getInstantiationStrategy().instantiate(bd, null, parent);
			}
			populateBean(beanClass.getName(), bd, new BeanWrapperImpl(bean));
			return bean;
		}
	}

	@Override
	public void autowireBeanProperties(Object existingBean, int autowireMode, boolean dependencyCheck)
			throws BeansException {

		if (autowireMode == AUTOWIRE_CONSTRUCTOR) {
			throw new IllegalArgumentException("AUTOWIRE_CONSTRUCTOR not supported for existing bean instance");
		}
		// Use non-singleton bean definition, to avoid registering bean as dependent bean.
		RootBeanDefinition bd =
				new RootBeanDefinition(ClassUtils.getUserClass(existingBean), autowireMode, dependencyCheck);
		bd.setScope(BeanDefinition.SCOPE_PROTOTYPE);
		BeanWrapper bw = new BeanWrapperImpl(existingBean);
		initBeanWrapper(bw);
		populateBean(bd.getBeanClass().getName(), bd, bw);
	}

	@Override
	public void applyBeanPropertyValues(Object existingBean, String beanName) throws BeansException {
		markBeanAsCreated(beanName);
		BeanDefinition bd = getMergedBeanDefinition(beanName);
		BeanWrapper bw = new BeanWrapperImpl(existingBean);
		initBeanWrapper(bw);
		applyPropertyValues(beanName, bd, bw, bd.getPropertyValues());
	}

	@Override
	public Object initializeBean(Object existingBean, String beanName) {
		return initializeBean(beanName, existingBean, null);
	}

	@Override
	//	调用 BeanPostProcessor 后置处理器实例对象初始化之前的处理方法
	public Object applyBeanPostProcessorsBeforeInitialization(Object existingBean, String beanName)
			throws BeansException {
		Object result = existingBean;
		//	遍历容器 为所创建的 Bean 添加的所有 BeanPostProcessor 后置处理器
		for (BeanPostProcessor beanProcessor : getBeanPostProcessors()) {
			//	调用 Bean 实例所有的后置处理中的初始化前处理方法，为 Bean 实例对象在
			//	初始化之前做一些自定义的处理操作
			Object current = beanProcessor.postProcessBeforeInitialization(result, beanName);
			if (current == null) {
				return result;
			}
			result = current;
		}
		return result;
	}

	@Override
	// 调用 BeanPostProcessor 后置处理器实例对象初始化之后的处理方法

	///		在讲解从 缓存中 获取单例 bean 的时候就提到过，Spring 中的规则是在 Bean 的初始化后 尽可能保证将 注册的后处理器 的
	///	 postProcessAfterInitialization方法 应用到该 Bean 中，因为如果返回的 bean 不为空，那么便不会再次经历普通 bean 的创建过程,
	///  所以只能在这里应用后处理器的 postProcessAfterInitializtion 方法。
	public Object applyBeanPostProcessorsAfterInitialization(Object existingBean, String beanName)
			throws BeansException {

		Object result = existingBean;
		///	遍历容器为所创建的 Bean 添加的所有 BeanPostProcessor 后置处理器
		for (BeanPostProcessor beanProcessor : getBeanPostProcessors()) {
			// 调用 Bean实例 所有的后置处理中的初始化后处理方法，为 Bean实例对象 在初始化之后做一些自定义的处理操作（（比如：AbstractAutoProxyCreator））
			Object current = beanProcessor.postProcessAfterInitialization(result, beanName);
			if (current == null) {
				return result;
			}
			result = current;
		}
		return result;
	}

	@Override
	public void destroyBean(Object existingBean) {
		new DisposableBeanAdapter(existingBean, getBeanPostProcessors(), getAccessControlContext()).destroy();
	}

	//---------------------------------------------------------------------
	// Implementation of relevant AbstractBeanFactory template methods
	//---------------------------------------------------------------------

	/**
	 * Central method of this class: creates a bean instance,
	 * populates the bean instance, applies post-processors, etc.
	 * @see #doCreateBean
	 */
	// 创建Bean实例对象
	// （1） 根据设置的 class属性 或者根据 className来解析Class；

	// （2） 对 override属性 进行标记及验证；
	//		其实在 Spring 中确实没有 override-method 这样的配置，但是如果读过前面的部分，可能会有所发现，在  Spring 配置中
	//	  是存在 lookup-method 和 replace-method 的，而这两个配置的加载其实就是将配置统一存放在 BeanDefinition 中的 methodOverrides 属性里，
	//	  而这个函数的操作其实也就是针对这两个配置的；

	//（3） 应用初始化前的后处理器，解析指定bean是否存在初始化前的短路操作；

	//（4） 创建 bean；
	@Override
	protected Object createBean(String beanName, RootBeanDefinition mbd, @Nullable Object[] args)
			throws BeanCreationException {

		if (logger.isDebugEnabled()) {
			logger.debug("Creating instance of bean '" + beanName + "'");
		}
		RootBeanDefinition mbdToUse = mbd;

		// Make sure bean class is actually resolved at this point, and
		// clone the bean definition in case of a dynamically resolved Class
		// which cannot be stored in the shared merged bean definition.
		// 判断需要创建的 Bean 是否可以实例化，即是否可以通过当前的类加载器加载

		///	锁定class，根据设置的 class属性 或者根据 className 来解析 Class
		Class<?> resolvedClass = resolveBeanClass(mbd, beanName);
		if (resolvedClass != null && !mbd.hasBeanClass() && mbd.getBeanClassName() != null) {
			mbdToUse = new RootBeanDefinition(mbd);
			mbdToUse.setBeanClass(resolvedClass);
		}
		// Prepare method overrides.
		// 校验和准备 Bean 中的方法覆盖
		try {
			/// 验证 及 准备覆盖 的方法
			mbdToUse.prepareMethodOverrides();
		}
		catch (BeanDefinitionValidationException ex) {
			throw new BeanDefinitionStoreException(mbdToUse.getResourceDescription(),
					beanName, "Validation of method overrides failed", ex);
		}

		try {
			// Give BeanPostProcessors a chance to return a proxy instead of the target bean instance.
				// 	如果 Bean 配置了 初始化前 和 初始化后 的处理器，则试图返回一个需要创建 Bean 的 代理对象

				/// 给 BeanPostProcessors 一个机会来返回 代理，来代替 真正 的 实例
				///	前置处理
				///		在真正调用 doCreate 方法创建 bean 的实例前，使用了这样一个方法 resolveBeforeInstantation(beanName,mbd) 对
				///	BeanDefinition 中的属性做些前置处理。当然，无论其中是否有相应的逻辑实现我们都可以理解，因为真正逻辑实现前后
				///	留有处理函数也是可扩展的一种体现。
			/// todo 生成 AOP 代理对象
			Object bean = resolveBeforeInstantiation(beanName, mbdToUse);

				///		但是，这并不是最重要的，在函数中还提供了一个短路判断，这才是最关键的部分。
				///	当经过 前置处理后 返回的结果如果 不为空，那么会直接略过 后续的Bean的创建 而直接返回结果。这一特性虽然很容易被忽略，
				///	但是却起着至关重要的作用，我们熟知的 “AOP功能” 就是基于这里判断的
			if (bean != null) {
				return bean;
			}
		}
		catch (Throwable ex) {
			throw new BeanCreationException(mbdToUse.getResourceDescription(), beanName,
					"BeanPostProcessor before instantiation of bean failed", ex);
		}

		try {
				//	当经历了Object bean = resolveBeforeInstantiation(beanName, mbdToUse) 方法后，程序有两个选择，
				//  （1）如果创建了代理，
				//				或者说重写了 InstantiationAwareBeanPostProcessor 的 postProcessBeforeInstatiation 方法，并在方法 postProcessBeforeInstantiation
				//			中改变了Bean,则直接返回可以了。

				//	（2）否者需要进行常规 bean 的创建。而这常规 bean 的创建就是在 doCreateBean 中完成的。
			//创建Bean的入口
			Object beanInstance = doCreateBean(beanName, mbdToUse, args);
			if (logger.isDebugEnabled()) {
				logger.debug("Finished creating instance of bean '" + beanName + "'");
			}
			return beanInstance;
		}
		catch (BeanCreationException ex) {
			// A previously detected exception with proper bean creation context already...
			throw ex;
		}
		catch (ImplicitlyAppearedSingletonException ex) {
			// An IllegalStateException to be communicated up to DefaultSingletonBeanRegistry...
			throw ex;
		}
		catch (Throwable ex) {
			throw new BeanCreationException(
					mbdToUse.getResourceDescription(), beanName, "Unexpected exception during bean creation", ex);
		}
	}

	/**
	 * Actually create the specified bean. Pre-creation processing has already happened
	 * at this point, e.g. checking {@code postProcessBeforeInstantiation} callbacks.
	 * <p>Differentiates between default bean instantiation, use of a
	 * factory method, and autowiring a constructor.
	 * @param beanName the name of the bean
	 * @param mbd the merged bean definition for the bean
	 * @param args explicit arguments to use for constructor or factory method invocation
	 * @return a new instance of the bean
	 * @throws BeanCreationException if the bean could not be created
	 * @see #instantiateBean
	 * @see #instantiateUsingFactoryMethod
	 * @see #autowireConstructor
	 */
	/// 循环依赖
	///	1.Spring容器循环依赖包括：
	// 		（1）	构造器循环依赖；表示通过构造器注入构成的循环依赖，此依赖是 无法解决 的，只能抛出 BeanCurrentlyInCreationException 异常表示循环依赖。
	//			Spring容器 将每一个 正在创建 的 bean标识符 放在一个 "当前创建bean池" 中，bean标识符 在创建过程中将 一直保存在这个池州中，因此如果在 创建bean过程
	//			中发现自己已经在 "当前创建bean池" 里时，将抛出 BeanCurrentInCreationException异常 表示循环依赖；而对于创建完毕的bean将从 "当前创建bean池" 中清除掉。

	//			eg:	创建配置文件
	// 				<bean id="testA" class="com.bean.TestA">
	//						<constructor-arg index="0" ref="testB"/>
	// 				</bean>
	// 				<bean id="testB" class="com.bean.TestB">
	//						<constructor-arg index="0" ref="testC"/>
	//				 </bean>
	// 				 <bean id="testC" class="com.bean.TestC">
	//						<constructor-arg index="0" ref="testA"/>
	// 				 </bean>

	//			创建测试用例
	//			@Test(expected= BeanCurrentlyInCreationException.class)
	// 			public void testCircleByConstructor() throws Throwable{
	//				try{
	// 						new ClassPathXmlApplicationContext("test.xml");
	//					}catch(Exception e){

	// 				}
	// 			}
	//
	// 		（2）	setter循环依赖；
	//			表示通过	setter	注入方式构成的循环依赖。
	//			对于	setter注入 造成的依赖是通过 Spring容器 提前暴露刚完成构造器注入，但未完成其他步骤（如setter注入）的 bean 来完成的，
	//		而且只能解决 单例作用域 的 bean 循环依赖。通过提前暴露一个 单例工厂方法（ObejctFactory），从而使 其他bean 能引用 到该bean，
	//			addSinletonFactory( beanName,	new ObjectFactory(){
	//				public Object getObject() throws BeansException{
	//					return getEarlyBeanReference(beanName,mbd,bean);
	//				}
	//			})
	//		A.		Spring容器 创建单例 “testA” bean，首先根据无参构造器创建bean，并暴露一个 “ObejctFactory” ,用于返回一个提前暴露一个 创建中的 Bean，
	//			并将 “testA”标识符 放到 “当前创建bean池” ，然后进行 setter 注入 “testB”。
	//		B.      Spring容器 创建单例 “testB” bean， 首先根据无参构造器创建bean，并暴露一个 “ObejctFactory” ,用于返回一个提前暴露一个创建中的 Bean，
	//			并将 “testB” 标识符放到 “当前创建bean池”，然后进行 setter 注入 “testC”。
	//		C.		Spring容器 创建单例 “testC” bean，首先根据无参构造器创建bean，并暴露一个 “ObejctFactory”, 用于返回一个提前暴露一个创建中的 Bean，
	//			并将 “testC”标识符 放到 “当前创建bean池”，然后进行 setter 注入 “testA”。进行注入 “testA” 时由于提前暴露了 “ObjectFactory” 工厂，从而使用它返回
	//			提前暴露一个 创建中 的 bean
	//		D.		最后在依赖注入 “testB” 和 “testA”，完成 setter 注入

	//		（3）prototype范围的依赖处理
	//				对于 “prototype” 作用域 bean,Spring容器 无法完成依赖注入，因为 Spring容器 不进行缓存 "prototype"作用域 的 bean，因此无法提前暴露一个创建中的 bean。
	//			eg:创建配置文件
	// 						<bean id="testA" class="com.bean.TestA" scope="prototype">
	//								<property name="testB" ref="testB"/>
	// 						 </bean>
	// 						 <bean id="testB" class="com.bean.TestB" scope="prototype">
	//								<property name="testC" ref="testC"/>
	// 						 </bean>
	// 						 <bean id="testC" class="com.bean.TestC" scope="prototype">
	//								<property name="testA" ref="testA"/>
	// 						 </bean>

	//			创建测试用例
	//			@Test(expected= BeanCurrentlyInCreationException.class)
	// 			public void testCircleBySetterAndPrototype() throws Throwable{
	//					try{
	// 							new ClassPathXmlApplicationContext("test.xml");
	// 						}catch(Exception e){

	// 					}
	// 			}
	//
	//		对于 "singleton" 作用域 bean，可以通过 "setAllowCircularRefernces(false);" 来禁用循环引用。


	//		真正创建 Bean 的方法
	//				（1）如果是 单例 则需要先 清除缓存；
	//				（2）实例化 bean，将 BeanDefinitionRegistry 转换为 BeanWrapper；
	// 						a.如果存在 工厂方法 则使用 工厂方法 进行初始化。
	//						b.一个类有多个 构造函数，每个 构造函数 都有不同的参数，所以需要根据 参数锁定 构造函数 并进行初始化。
	//						c.如果既不在存在 工厂方法 也不存在 带有参数的构造函数，则使用默认的 构造函数 进行 bean 的实例化
	//				（4）MergedBeanDefinitionPostProcessor 的应用，bean 合并的处理，Autowired注解 正是通过此方法实现诸如类型的预解析；
	//				（5）依赖处理
	//						在 Spring 中会有 循环依赖 的情况，例如，当 A 中含有 B 的属性，而 B 中又有 A 的属性时就会构成一个循环依赖，此时如果 A 和 B 都是单例，
	// 					那么在 Spring 中的处理方式就是当创建 B 的时候，涉及自动注入 A 的步骤时，并不是直接去再次创建 A，而是通过放入缓存中的 ObejctFactory 来创建
	//					实例，这样就解决了循环依赖的问题。
	//				（5）属性填充。将所有属性填充至bean的实例中。
	//				（6）循环依赖检查。
	//						之前有提到過，在 Spring 中解决循环依赖只对 单例有效，而对于 prototype 的 bean，Spring 没有好的解决办法，唯一要做的就是 抛出异常。
	//					在这个步骤里面会检测已经加载的 Bean 是否已经出现了 依赖循环，并判断是否需要 抛出异常
	//				（7）注册 DisposableBean,如果配置了destory-method，这里需要 注册 以便于在 销毁 的时候调用；
	//				（8）完成创建并返回；
	protected Object doCreateBean(final String beanName, final RootBeanDefinition mbd, final @Nullable Object[] args)
			throws BeanCreationException {

		// Instantiate the bean.
		// 封装被创建的 Bean 对象
		BeanWrapper instanceWrapper = null;
		if (mbd.isSingleton()) {
			instanceWrapper = this.factoryBeanInstanceCache.remove(beanName);
		}
		if (instanceWrapper == null) {
				// 根据指定 bean 使用 对应的策略 创建新的实例，如：工厂方法、构造函数自动注入、简单初始化
			instanceWrapper = createBeanInstance(beanName, mbd, args);
		}
		/// bean被封装进 Bean
		final Object bean = instanceWrapper.getWrappedInstance();
		// 获取实例化对象的类型
		Class<?> beanType = instanceWrapper.getWrappedClass();
		if (beanType != NullBean.class) {
			mbd.resolvedTargetType = beanType;
		}

		/// todo Autowired注解 正是通过此方法实现诸如， 具体 MergedBeanDefinitionPostProcessor 有哪些实现, 例如 AutowiredAnnotationBeanPostProcessor 	MergedBeanDefinitionPostProcessor 的应用，bean 合并的处理，Autowired注解 正是通过此方法实现诸如类型的预解析
		// Allow post-processors to modify the merged bean definition.
		//	调用 PostProcessor 后置处理器
		///	MergedBeanDefinitionPostProcessor 的应用，bean 合并的处理，Autowired注解 正是通过此方法实现诸如类型的预解析；
		synchronized (mbd.postProcessingLock) {
			if (!mbd.postProcessed) {
				try {
					// 应用	MergedBeanDefinitionPostProcessor

					/// 解析 @Resource、@Autowire 等注解，封装 InjectionMetadata ，并放入缓存 this.injectionMetadataCache 中，供下边 populateBean 使用。
					applyMergedBeanDefinitionPostProcessors(mbd, beanType, beanName);
				}
				catch (Throwable ex) {
					throw new BeanCreationException(mbd.getResourceDescription(), beanName,
							"Post-processing of merged bean definition failed", ex);
				}
				/// 这段逻辑只执行一次
				mbd.postProcessed = true;
			}
		}

		// Eagerly cache singletons to be able to resolve circular references
		// even when triggered by lifecycle interfaces like BeanFactoryAware.
		// 向容器中 缓存单例模式的 Bean 对象，以防循环引用

		///		是否需要提早曝光：单例 & 允许循环依赖 & 当前bean正在创建中，检测循环依赖
		///				a. 		earlySingletonExposure： 从字面的意思理解就是 提早曝光的单例，我们暂不定义它的学名叫什么，我们感兴趣的是哪些条件影响这个值。

		///				b. 		mbd.isSingleton(): 没有太多可以解释的，此 RootBeanDefinitionRegistry 代表的是 否是单例。

		///				c. 		this.allowCircularReferences：是否允许循环依赖，很抱歉，并没有找到在文件中如何配置，但是在 AbstractRefreshApplictionContext 中
		///				   提供了设置函数，可以通过 硬编码 的方式设置或者可以通过 自定义命名空间 进行配置，其中硬编码的方式代码如下。
		///							ClassPathXmlApplicationContext bf = new ClassPathXmlApplication("aspectTest.xml");
		///						    bf.setAllowBeanDefinitionOverriding(false);

		///				d.  	isSingletonCurrentlyInCreation(beanName)：该 bean 是否在创建中。在 Spring 中，会有个专门的属性默认为 DefaultSingtonBeanRegistry
		///					的 singletonsCurrentlyInCreation 来记录 bean 的加载状态，在 bean 开始创建前会将 beanName 记录在属性中，在 bean 创建结束后会将 beanName 从属性
		///					中移除。那么我们跟随代码一路走来可是对这个属性的记录并没有多少印象，这个状态是在哪里记录的呢？
		// 						不同 scope 的记录位置并不一样，我们以 singleton 为例，在 singleton 下记录属性的函数是在 DefaultSingletonBeanRegistry 类的
		// 					public Object getSingleton(String name,ObjectFactory singletonFactory) 函数的 beforeSingletonCreation(beanName) 和 afterSingletonCreation(beanName) 中，
		// 					在这两段函数中分别 this.singletonsCurrentlyInCreation.add(beanName) 与 this.singletonsCurrentlyInCreation.remove(beanName) 来进行状态的记录与移除

		/// earlySingletonExposure 提早曝光单例
		boolean earlySingletonExposure = (mbd.isSingleton() && this.allowCircularReferences &&
				isSingletonCurrentlyInCreation(beanName));
		if (earlySingletonExposure) {
			if (logger.isDebugEnabled()) {
				logger.debug("Eagerly caching bean '" + beanName +
						"' to allow for resolving potential circular references");
			}
			// 这里是一个匿名内部类，为了防止循环引用，尽早持有对象的引用

			///		我们之前已经讲过，在这个函数中并不是 直接去实例化 A，而是先去 检测缓存 中是否有已经创建好的对应的 bean,或者
			/// 是否已经创建好的 ObjectFactory。而此时对于 A 的 ObjectFactory 我们早已经创建，所以便不会再去向后执行，而是直接调用 ObjectFactory 去创建 A。
			/// 为避免后期 循环依赖，可以在 bean初始化完成前 将创建实例的 ObjectFactory 加入工厂,
			/// 对 bean 再一次依赖引用，主要应用 SmartInstantiationAwareBeanPostProcessor,

			/// todo  我们熟知的 AOP 就是在这里 将 advice 动态织入 bean 中 在这里实现(1)
			/// 其中我们熟知的 AOP 就是在这里 将 advice 动态织入 bean 中，若没有则直接返回 bean，不做任何处理
			addSingletonFactory(beanName, () -> getEarlyBeanReference(beanName, mbd, bean));
		}

		// Initialize the bean instance.
		// Bean对象 的 初始化，依赖注入 在此 触发
		// 这个 exposedObject 在 初始化完成之后 返回作为 依赖注入 完成后的 Bean
		Object exposedObject = bean;
		try {
			// 将 Bean实例 对象封装，并且 Bean定义中配置 的属性值赋值给实例对象

			/// 对 bean 进行填充，将各个 属性值 注入其中，肯能存在 依赖其他 bean 的属性，则会 递归 初始依赖 bean
			populateBean(beanName, mbd, instanceWrapper);

			//初始化Bean对象

			///	调用 初始化方法，比如 init-method
			/// <init-method> 属性 的作用是在 bean实例化前 调用 init-method 指定的 方法 根据 用户业务 进行相应的实例化。
			exposedObject = initializeBean(beanName, exposedObject, mbd);
		}
		catch (Throwable ex) {
			if (ex instanceof BeanCreationException && beanName.equals(((BeanCreationException) ex).getBeanName())) {
				throw (BeanCreationException) ex;
			}
			else {
				throw new BeanCreationException(
						mbd.getResourceDescription(), beanName, "Initialization of bean failed", ex);
			}
		}

		if (earlySingletonExposure) {

			// 获取指定名称的 已注册 的 单例模式 Bean 对象
			Object earlySingletonReference = getSingleton(beanName, false);

			/// earlySingletonRefrence 只有在 检测到 有循环依赖 的情况下才会不为空
			if (earlySingletonReference != null) {

				//根据名称获取的已注册的Bean和正在实例化的Bean是同一个

				///	如果 exposedObject 没有在 初始化方法 中被改变，也就是没有被增强
				if (exposedObject == bean) {

					/// 当前实例化的 Bean 初始化完成
					exposedObject = earlySingletonReference;
				}
				/// todo doCreate 中循环依赖的处理
				// 当前 Bean 依赖其他 Bean，并且当 发生循环引用时 不允许 新创建实例对象
				else if (!this.allowRawInjectionDespiteWrapping && hasDependentBean(beanName)) {
					String[] dependentBeans = getDependentBeans(beanName);
					Set<String> actualDependentBeans = new LinkedHashSet<>(dependentBeans.length);

					// 获取当前 Bean 所依赖的 其他Bean
					for (String dependentBean : dependentBeans) {
						//对依赖Bean进行类型检查

						///检测依赖
						if (!removeSingletonIfCreatedForTypeCheckOnly(dependentBean)) {
							actualDependentBeans.add(dependentBean);
						}
					}
						///		因为 bean 创建后其所依赖的 bean  一定是已经创建的，
						/// actualDependentBeans不为空 则表示当前 bean 创建后 其依赖的bean 却没有全部创建完，
						/// 也就是说存在 循环依赖。
					if (!actualDependentBeans.isEmpty()) {
						throw new BeanCurrentlyInCreationException(beanName,
								"Bean with name '" + beanName + "' has been injected into other beans [" +
								StringUtils.collectionToCommaDelimitedString(actualDependentBeans) +
								"] in its raw version as part of a circular reference, but has eventually been " +
								"wrapped. This means that said other beans do not use the final version of the " +
								"bean. This is often the result of over-eager type matching - consider using " +
								"'getBeanNamesOfType' with the 'allowEagerInit' flag turned off, for example.");
					}
				}
			}
		}

		// Register bean as disposable.
		try {
			/// 注册实现 disposable 的 bean
			registerDisposableBeanIfNecessary(beanName, bean, mbd);
		}
		catch (BeanDefinitionValidationException ex) {
			throw new BeanCreationException(
					mbd.getResourceDescription(), beanName, "Invalid destruction signature", ex);
		}

		return exposedObject;
	}

	@Override
	@Nullable
	protected Class<?> predictBeanType(String beanName, RootBeanDefinition mbd, Class<?>... typesToMatch) {
		Class<?> targetType = determineTargetType(beanName, mbd, typesToMatch);

		// Apply SmartInstantiationAwareBeanPostProcessors to predict the
		// eventual type after a before-instantiation shortcut.
		if (targetType != null && !mbd.isSynthetic() && hasInstantiationAwareBeanPostProcessors()) {
			for (BeanPostProcessor bp : getBeanPostProcessors()) {
				if (bp instanceof SmartInstantiationAwareBeanPostProcessor) {
					SmartInstantiationAwareBeanPostProcessor ibp = (SmartInstantiationAwareBeanPostProcessor) bp;
					Class<?> predicted = ibp.predictBeanType(targetType, beanName);
					if (predicted != null && (typesToMatch.length != 1 || FactoryBean.class != typesToMatch[0] ||
							FactoryBean.class.isAssignableFrom(predicted))) {
						return predicted;
					}
				}
			}
		}
		return targetType;
	}

	/**
	 * Determine the target type for the given bean definition.
	 * @param beanName the name of the bean (for error handling purposes)
	 * @param mbd the merged bean definition for the bean
	 * @param typesToMatch the types to match in case of internal type matching purposes
	 * (also signals that the returned {@code Class} will never be exposed to application code)
	 * @return the type for the bean if determinable, or {@code null} otherwise
	 */
	@Nullable
	protected Class<?> determineTargetType(String beanName, RootBeanDefinition mbd, Class<?>... typesToMatch) {
		Class<?> targetType = mbd.getTargetType();
		if (targetType == null) {
			targetType = (mbd.getFactoryMethodName() != null ?
					getTypeForFactoryMethod(beanName, mbd, typesToMatch) :
					resolveBeanClass(mbd, beanName, typesToMatch));
			if (ObjectUtils.isEmpty(typesToMatch) || getTempClassLoader() == null) {
				mbd.resolvedTargetType = targetType;
			}
		}
		return targetType;
	}

	/**
	 * Determine the target type for the given bean definition which is based on
	 * a factory method. Only called if there is no singleton instance registered
	 * for the target bean already.
	 * <p>This implementation determines the type matching {@link #createBean}'s
	 * different creation strategies. As far as possible, we'll perform static
	 * type checking to avoid creation of the target bean.
	 * @param beanName the name of the bean (for error handling purposes)
	 * @param mbd the merged bean definition for the bean
	 * @param typesToMatch the types to match in case of internal type matching purposes
	 * (also signals that the returned {@code Class} will never be exposed to application code)
	 * @return the type for the bean if determinable, or {@code null} otherwise
	 * @see #createBean
	 */
	@Nullable
	protected Class<?> getTypeForFactoryMethod(String beanName, RootBeanDefinition mbd, Class<?>... typesToMatch) {
		ResolvableType cachedReturnType = mbd.factoryMethodReturnType;
		if (cachedReturnType != null) {
			return cachedReturnType.resolve();
		}

		Class<?> factoryClass;
		boolean isStatic = true;

		String factoryBeanName = mbd.getFactoryBeanName();
		if (factoryBeanName != null) {
			if (factoryBeanName.equals(beanName)) {
				throw new BeanDefinitionStoreException(mbd.getResourceDescription(), beanName,
						"factory-bean reference points back to the same bean definition");
			}
			// Check declared factory method return type on factory class.
			factoryClass = getType(factoryBeanName);
			isStatic = false;
		}
		else {
			// Check declared factory method return type on bean class.
			factoryClass = resolveBeanClass(mbd, beanName, typesToMatch);
		}

		if (factoryClass == null) {
			return null;
		}
		factoryClass = ClassUtils.getUserClass(factoryClass);

		// If all factory methods have the same return type, return that type.
		// Can't clearly figure out exact method due to type converting / autowiring!
		Class<?> commonType = null;
		Method uniqueCandidate = null;
		int minNrOfArgs =
				(mbd.hasConstructorArgumentValues() ? mbd.getConstructorArgumentValues().getArgumentCount() : 0);
		Method[] candidates = ReflectionUtils.getUniqueDeclaredMethods(factoryClass);
		for (Method factoryMethod : candidates) {
			if (Modifier.isStatic(factoryMethod.getModifiers()) == isStatic &&
					factoryMethod.getName().equals(mbd.getFactoryMethodName()) &&
					factoryMethod.getParameterCount() >= minNrOfArgs) {
				// Declared type variables to inspect?
				if (factoryMethod.getTypeParameters().length > 0) {
					try {
						// Fully resolve parameter names and argument values.
						Class<?>[] paramTypes = factoryMethod.getParameterTypes();
						String[] paramNames = null;
						ParameterNameDiscoverer pnd = getParameterNameDiscoverer();
						if (pnd != null) {
							paramNames = pnd.getParameterNames(factoryMethod);
						}
						ConstructorArgumentValues cav = mbd.getConstructorArgumentValues();
						Set<ConstructorArgumentValues.ValueHolder> usedValueHolders = new HashSet<>(paramTypes.length);
						Object[] args = new Object[paramTypes.length];
						for (int i = 0; i < args.length; i++) {
							ConstructorArgumentValues.ValueHolder valueHolder = cav.getArgumentValue(
									i, paramTypes[i], (paramNames != null ? paramNames[i] : null), usedValueHolders);
							if (valueHolder == null) {
								valueHolder = cav.getGenericArgumentValue(null, null, usedValueHolders);
							}
							if (valueHolder != null) {
								args[i] = valueHolder.getValue();
								usedValueHolders.add(valueHolder);
							}
						}
						Class<?> returnType = AutowireUtils.resolveReturnTypeForFactoryMethod(
								factoryMethod, args, getBeanClassLoader());
						uniqueCandidate = (commonType == null ? factoryMethod : null);
						commonType = ClassUtils.determineCommonAncestor(returnType, commonType);
						if (commonType == null) {
							// Ambiguous return types found: return null to indicate "not determinable".
							return null;
						}
					}
					catch (Throwable ex) {
						if (logger.isDebugEnabled()) {
							logger.debug("Failed to resolve generic return type for factory method: " + ex);
						}
					}
				}
				else {
					uniqueCandidate = (commonType == null ? factoryMethod : null);
					commonType = ClassUtils.determineCommonAncestor(factoryMethod.getReturnType(), commonType);
					if (commonType == null) {
						// Ambiguous return types found: return null to indicate "not determinable".
						return null;
					}
				}
			}
		}

		if (commonType != null) {
			// Clear return type found: all factory methods return same type.
			mbd.factoryMethodReturnType = (uniqueCandidate != null ?
					ResolvableType.forMethodReturnType(uniqueCandidate) : ResolvableType.forClass(commonType));
		}
		return commonType;
	}

	/**
	 * This implementation attempts to query the FactoryBean's generic parameter metadata
	 * if present to determine the object type. If not present, i.e. the FactoryBean is
	 * declared as a raw type, checks the FactoryBean's {@code getObjectType} method
	 * on a plain instance of the FactoryBean, without bean properties applied yet.
	 * If this doesn't return a type yet, a full creation of the FactoryBean is
	 * used as fallback (through delegation to the superclass's implementation).
	 * <p>The shortcut check for a FactoryBean is only applied in case of a singleton
	 * FactoryBean. If the FactoryBean instance itself is not kept as singleton,
	 * it will be fully created to check the type of its exposed object.
	 */
	@Override
	@Nullable
	protected Class<?> getTypeForFactoryBean(String beanName, RootBeanDefinition mbd) {
		String factoryBeanName = mbd.getFactoryBeanName();
		String factoryMethodName = mbd.getFactoryMethodName();

		if (factoryBeanName != null) {
			if (factoryMethodName != null) {
				// Try to obtain the FactoryBean's object type from its factory method declaration
				// without instantiating the containing bean at all.
				BeanDefinition fbDef = getBeanDefinition(factoryBeanName);
				if (fbDef instanceof AbstractBeanDefinition) {
					AbstractBeanDefinition afbDef = (AbstractBeanDefinition) fbDef;
					if (afbDef.hasBeanClass()) {
						Class<?> result = getTypeForFactoryBeanFromMethod(afbDef.getBeanClass(), factoryMethodName);
						if (result != null) {
							return result;
						}
					}
				}
			}
			// If not resolvable above and the referenced factory bean doesn't exist yet,
			// exit here - we don't want to force the creation of another bean just to
			// obtain a FactoryBean's object type...
			if (!isBeanEligibleForMetadataCaching(factoryBeanName)) {
				return null;
			}
		}

		// Let's obtain a shortcut instance for an early getObjectType() call...
		FactoryBean<?> fb = (mbd.isSingleton() ?
				getSingletonFactoryBeanForTypeCheck(beanName, mbd) :
				getNonSingletonFactoryBeanForTypeCheck(beanName, mbd));

		if (fb != null) {
			// Try to obtain the FactoryBean's object type from this early stage of the instance.
			Class<?> result = getTypeForFactoryBean(fb);
			if (result != null) {
				return result;
			}
			else {
				// No type found for shortcut FactoryBean instance:
				// fall back to full creation of the FactoryBean instance.
				return super.getTypeForFactoryBean(beanName, mbd);
			}
		}

		if (factoryBeanName == null && mbd.hasBeanClass()) {
			// No early bean instantiation possible: determine FactoryBean's type from
			// static factory method signature or from class inheritance hierarchy...
			if (factoryMethodName != null) {
				return getTypeForFactoryBeanFromMethod(mbd.getBeanClass(), factoryMethodName);
			}
			else {
				return GenericTypeResolver.resolveTypeArgument(mbd.getBeanClass(), FactoryBean.class);
			}
		}

		return null;
	}

	/**
	 * Introspect the factory method signatures on the given bean class,
	 * trying to find a common {@code FactoryBean} object type declared there.
	 * @param beanClass the bean class to find the factory method on
	 * @param factoryMethodName the name of the factory method
	 * @return the common {@code FactoryBean} object type, or {@code null} if none
	 */
	@Nullable
	private Class<?> getTypeForFactoryBeanFromMethod(Class<?> beanClass, final String factoryMethodName) {
		class Holder { @Nullable Class<?> value = null; }
		final Holder objectType = new Holder();

		// CGLIB subclass methods hide generic parameters; look at the original user class.
		Class<?> fbClass = ClassUtils.getUserClass(beanClass);

		// Find the given factory method, taking into account that in the case of
		// @Bean methods, there may be parameters present.
		ReflectionUtils.doWithMethods(fbClass, method -> {
			if (method.getName().equals(factoryMethodName) &&
					FactoryBean.class.isAssignableFrom(method.getReturnType())) {
				Class<?> currentType = GenericTypeResolver.resolveReturnTypeArgument(method, FactoryBean.class);
				if (currentType != null) {
					objectType.value = ClassUtils.determineCommonAncestor(currentType, objectType.value);
				}
			}
		});

		return (objectType.value != null && Object.class != objectType.value ? objectType.value : null);
	}

	/**
	 * Obtain a reference for early access to the specified bean,
	 * typically for the purpose of resolving a circular reference.
	 * @param beanName the name of the bean (for error handling purposes)
	 * @param mbd the merged bean definition for the bean
	 * @param bean the raw bean instance
	 * @return the object to expose as bean reference
	 */
	///  其中我们熟知的 AOP 就是在这里 将 advice 动态织入 bean 中，若没有则直接返回 bean，不做任何处理
	protected Object getEarlyBeanReference(String beanName, RootBeanDefinition mbd, Object bean) {
		Object exposedObject = bean;
		if (!mbd.isSynthetic() && hasInstantiationAwareBeanPostProcessors()) {
			for (BeanPostProcessor bp : getBeanPostProcessors()) {
				/// 如果处理器 是 SmartInstantiationAwareBeanPostProcessor 类型
				if (bp instanceof SmartInstantiationAwareBeanPostProcessor) {
					SmartInstantiationAwareBeanPostProcessor ibp = (SmartInstantiationAwareBeanPostProcessor) bp;
					/// AbstractAutoProxyCreator 中实现  getEarlyBeanReference
					exposedObject = ibp.getEarlyBeanReference(exposedObject, beanName);
				}
			}
		}
		return exposedObject;
	}


	//---------------------------------------------------------------------
	// Implementation methods
	//---------------------------------------------------------------------

	/**
	 * Obtain a "shortcut" singleton FactoryBean instance to use for a
	 * {@code getObjectType()} call, without full initialization of the FactoryBean.
	 * @param beanName the name of the bean
	 * @param mbd the bean definition for the bean
	 * @return the FactoryBean instance, or {@code null} to indicate
	 * that we couldn't obtain a shortcut FactoryBean instance
	 */
	@Nullable
	private FactoryBean<?> getSingletonFactoryBeanForTypeCheck(String beanName, RootBeanDefinition mbd) {
		synchronized (getSingletonMutex()) {
			BeanWrapper bw = this.factoryBeanInstanceCache.get(beanName);
			if (bw != null) {
				return (FactoryBean<?>) bw.getWrappedInstance();
			}
			if (isSingletonCurrentlyInCreation(beanName) ||
					(mbd.getFactoryBeanName() != null && isSingletonCurrentlyInCreation(mbd.getFactoryBeanName()))) {
				return null;
			}

			Object instance = null;
			try {
				// Mark this bean as currently in creation, even if just partially.
				beforeSingletonCreation(beanName);
				// Give BeanPostProcessors a chance to return a proxy instead of the target bean instance.
				instance = resolveBeforeInstantiation(beanName, mbd);
				if (instance == null) {
					bw = createBeanInstance(beanName, mbd, null);
					instance = bw.getWrappedInstance();
				}
			}
			finally {
				// Finished partial creation of this bean.
				afterSingletonCreation(beanName);
			}

			FactoryBean<?> fb = getFactoryBean(beanName, instance);
			if (bw != null) {
				this.factoryBeanInstanceCache.put(beanName, bw);
			}
			return fb;
		}
	}

	/**
	 * Obtain a "shortcut" non-singleton FactoryBean instance to use for a
	 * {@code getObjectType()} call, without full initialization of the FactoryBean.
	 * @param beanName the name of the bean
	 * @param mbd the bean definition for the bean
	 * @return the FactoryBean instance, or {@code null} to indicate
	 * that we couldn't obtain a shortcut FactoryBean instance
	 */
	@Nullable
	private FactoryBean<?> getNonSingletonFactoryBeanForTypeCheck(String beanName, RootBeanDefinition mbd) {
		if (isPrototypeCurrentlyInCreation(beanName)) {
			return null;
		}

		Object instance = null;
		try {
			// Mark this bean as currently in creation, even if just partially.
			beforePrototypeCreation(beanName);
			// Give BeanPostProcessors a chance to return a proxy instead of the target bean instance.
			instance = resolveBeforeInstantiation(beanName, mbd);
			if (instance == null) {
				BeanWrapper bw = createBeanInstance(beanName, mbd, null);
				instance = bw.getWrappedInstance();
			}
		}
		catch (BeanCreationException ex) {
			// Can only happen when getting a FactoryBean.
			if (logger.isDebugEnabled()) {
				logger.debug("Bean creation exception on non-singleton FactoryBean type check: " + ex);
			}
			onSuppressedException(ex);
			return null;
		}
		finally {
			// Finished partial creation of this bean.
			afterPrototypeCreation(beanName);
		}

		return getFactoryBean(beanName, instance);
	}

	/**
	 * Apply MergedBeanDefinitionPostProcessors to the specified bean definition,
	 * invoking their {@code postProcessMergedBeanDefinition} methods.
	 * @param mbd the merged bean definition for the bean
	 * @param beanType the actual type of the managed bean instance
	 * @param beanName the name of the bean
	 * @see MergedBeanDefinitionPostProcessor#postProcessMergedBeanDefinition
	 */
	// 应用 	MergedBeanDefinitionPostProcessor
	protected void applyMergedBeanDefinitionPostProcessors(RootBeanDefinition mbd, Class<?> beanType, String beanName) {
		for (BeanPostProcessor bp : getBeanPostProcessors()) {
			if (bp instanceof MergedBeanDefinitionPostProcessor) {
				MergedBeanDefinitionPostProcessor bdp = (MergedBeanDefinitionPostProcessor) bp;
				/// AutowiredAnnotationBeanPostProcessor、CommonAnnotationBeanPostProcessor
				bdp.postProcessMergedBeanDefinition(mbd, beanType, beanName);
			}
		}
	}

	/**
	 * Apply before-instantiation post-processors, resolving whether there is a
	 * before-instantiation shortcut for the specified bean.
	 * @param beanName the name of the bean
	 * @param mbd the bean definition for the bean
	 * @return the shortcut-determined bean instance, or {@code null} if none
	 */
	///	前置处理
	///		applyBeanPostProcessorsBeforeInstantiation、applyBeanPostProcessorsAfterInitialization	这两个方法实现的非常简单，无非是对
	///	后处理器中的所有	InstantiationAwareBeanPostProcessor	类型的后处理器进行	postProcessBeforeInstantiation方法 和 BeanPostProcessor
	/// 类型的 postProcessAfterInitialization方法 的调用
	@Nullable
	protected Object resolveBeforeInstantiation(String beanName, RootBeanDefinition mbd) {
		Object bean = null;
		if (!Boolean.FALSE.equals(mbd.beforeInstantiationResolved)) {
			// Make sure bean class is actually resolved at this point.
			if (!mbd.isSynthetic() && hasInstantiationAwareBeanPostProcessors()) {
				Class<?> targetType = determineTargetType(beanName, mbd);
				if (targetType != null) {
					/// 1.实例化前的后处理器应用
					///		InstantiationAwareBeanPostProcessor	类型
					bean = applyBeanPostProcessorsBeforeInstantiation(targetType, beanName);
					if (bean != null) {
					/// 2.实例化后的后处理器应用
					///		BeanPostProcessor 类型
						bean = applyBeanPostProcessorsAfterInitialization(bean, beanName);
					}
				}
			}
			mbd.beforeInstantiationResolved = (bean != null);
		}
		return bean;
	}

	/**
	 * Apply InstantiationAwareBeanPostProcessors to the specified bean definition
	 * (by class and name), invoking their {@code postProcessBeforeInstantiation} methods.
	 * <p>Any returned object will be used as the bean instead of actually instantiating
	 * the target bean. A {@code null} return value from the post-processor will
	 * result in the target bean being instantiated.
	 * @param beanClass the class of the bean to be instantiated
	 * @param beanName the name of the bean
	 * @return the bean object to use instead of a default instance of the target bean, or {@code null}
	 * @see InstantiationAwareBeanPostProcessor#postProcessBeforeInstantiation
	 */
	/// 	bean 实例化前调用，也就是将 AbstractBeanDefinition 转换为 BeanWrapper 前的处理。给子类一个修改 BeanDefinition 的机会，也就是
	///	说当程序经历过这个方法后，bean 可能已经不是我们认为的 bean了，而是或许成为了一个 经过处理的 代理bean，可能通过 cglib 生成的，也可能是
	/// 通过 其他技术 生成的。
	// 			eg:		AnnotationAwareAspectJAutoProxyCreator  实现了	InstantiationAwareBeanPostProcessor
	@Nullable
	protected Object applyBeanPostProcessorsBeforeInstantiation(Class<?> beanClass, String beanName) {
		for (BeanPostProcessor bp : getBeanPostProcessors()) {
			if (bp instanceof InstantiationAwareBeanPostProcessor) {
				InstantiationAwareBeanPostProcessor ibp = (InstantiationAwareBeanPostProcessor) bp;
				/// 调用 postProcessBeforeInstantiation
				Object result = ibp.postProcessBeforeInstantiation(beanClass, beanName);
				if (result != null) {
					return result;
				}
			}
		}
		return null;
	}

	/**
	 * Create a new instance for the specified bean, using an appropriate instantiation strategy:
	 * factory method, constructor autowiring, or simple instantiation.
	 * @param beanName the name of the bean
	 * @param mbd the bean definition for the bean
	 * @param args explicit arguments to use for constructor or factory method invocation
	 * @return a BeanWrapper for the new instance
	 * @see #obtainFromSupplier
	 * @see #instantiateUsingFactoryMethod
	 * @see #autowireConstructor
	 * @see #instantiateBean
	 */
	//	创建Bean的实例对象
	//		（1）如果在 RootBeanDefinition 中存在 factoryMethodName 属性，
	// 			或者说在 配置文件中 配置了 factory-method，那么 Spring 会尝试使用 instantiateUsingFactoryMethod 方法根据 RootBeanDefinition
	// 		中的配置生成 bean 的实例。

	//		（2）解析 构造函数 并进行 构造函数 的实例化。因为一个 bean 对应的类中可能会有多个 构造函数，而每个 构造函数 的参数不同，Spring 在根据 参数及类型
	// 		去判断 最终 会使用哪个构造函数 进行实例化。但是，判断的过程是和 比较消耗性能 的步骤，所以采用 缓存机制，如果 已经解析过 则 不需要重复解析 而是 直接从
	//		RootBeanDefinintion 中的 属性 resolvedConstructorOrFactoryMethod 缓存的值去取，否则需要再次解析，并将解析的结果添加至 RootbeanDefintion 中的属性
	//		resolvedConstructorOrFactoryMethod 中。
	protected BeanWrapper createBeanInstance(String beanName, RootBeanDefinition mbd, @Nullable Object[] args) {
		// Make sure bean class is actually resolved at this point.

		//	解析class
		Class<?> beanClass = resolveBeanClass(mbd, beanName);

		//	使用 工厂方法 对 Bean 进行实例化
		if (beanClass != null && !Modifier.isPublic(beanClass.getModifiers()) && !mbd.isNonPublicAccessAllowed()) {
			throw new BeanCreationException(mbd.getResourceDescription(), beanName,
					"Bean class isn't public, and non-public access not allowed: " + beanClass.getName());
		}

		Supplier<?> instanceSupplier = mbd.getInstanceSupplier();

		///	是否有 Supplier
		if (instanceSupplier != null) {
			/// 从 Supplier，获取 instance
			return obtainFromSupplier(instanceSupplier, beanName);
		}
		/// 如果 工厂方法 不为空 则使用 工厂方法初始化策略
		if (mbd.getFactoryMethodName() != null)  {
			///	调用 工厂方法 实例化
			return instantiateUsingFactoryMethod(beanName, mbd, args);
		}

		// Shortcut when re-creating the same bean...
		//	使用 容器的 自动装配方法 进行实例化
		boolean resolved = false;
		boolean autowireNecessary = false;
		if (args == null) {
			synchronized (mbd.constructorArgumentLock) {
					// 一个类有 多个构造函数，每个 构造函数 都有不同的参数，所以调用前需要先根据 参数锁定 构造函数 或者 对应的工厂方法
				if (mbd.resolvedConstructorOrFactoryMethod != null) {
					resolved = true;
					autowireNecessary = mbd.constructorArgumentsResolved;
				}
			}
		}

		///	如果 已经解析过 则使用解析好的 构造函数方法，不需要再次锁定
		if (resolved) {
			if (autowireNecessary) {

				/// 构造函数	自动注入
				return autowireConstructor(beanName, mbd, null, null);
			}
			else {
				//	使用默认的无参构造方法实例化
				/// 使用默认 构造函数构造
				return instantiateBean(beanName, mbd);
			}
		}

		// Need to determine the constructor...
		///	需要根据 参数解析 构造函数 todo
		Constructor<?>[] ctors = determineConstructorsFromBeanPostProcessors(beanClass, beanName);//?
		if (ctors != null ||
				mbd.getResolvedAutowireMode() == RootBeanDefinition.AUTOWIRE_CONSTRUCTOR ||
				mbd.hasConstructorArgumentValues() || !ObjectUtils.isEmpty(args))  {

			//	使用容器的自动装配特性，调用匹配的构造方法实例化
			///	构造函数	自动注入
			return autowireConstructor(beanName, mbd, ctors, args);
		}

		// No special handling: simply use no-arg constructor.
		///	使用默认构造函数构造
		return instantiateBean(beanName, mbd);
	}

	/**
	 * Obtain a bean instance from the given supplier.
	 * @param instanceSupplier the configured supplier
	 * @param beanName the corresponding bean name
	 * @return a BeanWrapper for the new instance
	 * @since 5.0
	 * @see #getObjectForBeanInstance
	 */
	protected BeanWrapper obtainFromSupplier(Supplier<?> instanceSupplier, String beanName) {
		String outerBean = this.currentlyCreatedBean.get();
		this.currentlyCreatedBean.set(beanName);
		Object instance;
		try {
			/// Supplier 提供扩展
			instance = instanceSupplier.get();
		}
		finally {
			if (outerBean != null) {
				this.currentlyCreatedBean.set(outerBean);
			}
			else {
				this.currentlyCreatedBean.remove();
			}
		}
		BeanWrapper bw = new BeanWrapperImpl(instance);
		initBeanWrapper(bw);
		return bw;
	}

	/**
	 * Overridden in order to implicitly register the currently created bean as
	 * dependent on further beans getting programmatically retrieved during a
	 * {@link Supplier} callback.
	 * @since 5.0
	 * @see #obtainFromSupplier
	 */
	@Override
	protected Object getObjectForBeanInstance(
			Object beanInstance, String name, String beanName, @Nullable RootBeanDefinition mbd) {

		String currentlyCreatedBean = this.currentlyCreatedBean.get();
		if (currentlyCreatedBean != null) {
			registerDependentBean(beanName, currentlyCreatedBean);
		}

		return super.getObjectForBeanInstance(beanInstance, name, beanName, mbd);
	}

	/**
	 * Determine candidate constructors to use for the given bean, checking all registered
	 * {@link SmartInstantiationAwareBeanPostProcessor SmartInstantiationAwareBeanPostProcessors}.
	 * @param beanClass the raw class of the bean
	 * @param beanName the name of the bean
	 * @return the candidate constructors, or {@code null} if none specified
	 * @throws org.springframework.beans.BeansException in case of errors
	 * @see org.springframework.beans.factory.config.SmartInstantiationAwareBeanPostProcessor#determineCandidateConstructors
	 */
	@Nullable
	protected Constructor<?>[] determineConstructorsFromBeanPostProcessors(@Nullable Class<?> beanClass, String beanName)
			throws BeansException {

		if (beanClass != null && hasInstantiationAwareBeanPostProcessors()) {
			for (BeanPostProcessor bp : getBeanPostProcessors()) {
				if (bp instanceof SmartInstantiationAwareBeanPostProcessor) {
					SmartInstantiationAwareBeanPostProcessor ibp = (SmartInstantiationAwareBeanPostProcessor) bp;
					Constructor<?>[] ctors = ibp.determineCandidateConstructors(beanClass, beanName);
					if (ctors != null) {
						return ctors;
					}
				}
			}
		}
		return null;
	}

	/**
	 * Instantiate the given bean using its default constructor.
	 * @param beanName the name of the bean
	 * @param mbd the bean definition for the bean
	 * @return a BeanWrapper for the new instance
	 */
	//	使用 默认的无参构造方法 实例化 Bean 对象
	protected BeanWrapper instantiateBean(final String beanName, final RootBeanDefinition mbd) {
		try {
			Object beanInstance;
			final BeanFactory parent = this;
			//	获取系统的 安全管理接口，JDK 标准的安全管理 API
			if (System.getSecurityManager() != null) {
				//	这里是一个 匿名内置类，根据 实例化策略 创建实例对象
				beanInstance = AccessController.doPrivileged((PrivilegedAction<Object>) () ->
						getInstantiationStrategy().instantiate(mbd, beanName, parent),
						getAccessControlContext());
			}
			else {
				//	将 实例化的对象 封装起来
				beanInstance = getInstantiationStrategy().instantiate(mbd, beanName, parent);
			}
			BeanWrapper bw = new BeanWrapperImpl(beanInstance);
			initBeanWrapper(bw);
			return bw;
		}
		catch (Throwable ex) {
			throw new BeanCreationException(
					mbd.getResourceDescription(), beanName, "Instantiation of bean failed", ex);
		}
	}

	/**
	 * Instantiate the bean using a named factory method. The method may be static, if the
	 * mbd parameter specifies a class, rather than a factoryBean, or an instance variable
	 * on a factory object itself configured using Dependency Injection.
	 * @param beanName the name of the bean
	 * @param mbd the bean definition for the bean
	 * @param explicitArgs argument values passed in programmatically via the getBean method,
	 * or {@code null} if none (-> use constructor argument values from bean definition)
	 * @return a BeanWrapper for the new instance
	 * @see #getBean(String, Object[])
	 */
	protected BeanWrapper instantiateUsingFactoryMethod(
			String beanName, RootBeanDefinition mbd, @Nullable Object[] explicitArgs) {

		return new ConstructorResolver(this).instantiateUsingFactoryMethod(beanName, mbd, explicitArgs);
	}

	/**
	 * "autowire constructor" (with constructor arguments by type) behavior.
	 * Also applied if explicit constructor argument values are specified,
	 * matching all remaining arguments with beans from the bean factory.
	 * <p>This corresponds to constructor injection: In this mode, a Spring
	 * bean factory is able to host components that expect constructor-based
	 * dependency resolution.
	 * @param beanName the name of the bean
	 * @param mbd the bean definition for the bean
	 * @param ctors the chosen candidate constructors
	 * @param explicitArgs argument values passed in programmatically via the getBean method,
	 * or {@code null} if none (-> use constructor argument values from bean definition)
	 * @return a BeanWrapper for the new instance
	 */
	protected BeanWrapper autowireConstructor(
			String beanName, RootBeanDefinition mbd, @Nullable Constructor<?>[] ctors, @Nullable Object[] explicitArgs) {
		///->
		return new ConstructorResolver(this).autowireConstructor(beanName, mbd, ctors, explicitArgs);
	}

	/**
	 * Populate the bean instance in the given BeanWrapper with the property values
	 * from the bean definition.
	 * @param beanName the name of the bean
	 * @param mbd the bean definition for the bean
	 * @param bw BeanWrapper with bean instance
	 */

	//将 Bean属性 设置到生成的 实例对象 上
	///（1）		InstantiationAwareBeanPostProcessor 处理器的 postProcessAfterInstantiation 函数的应用，此函数 可以控制程序 是否 继续进行 属性的填充
	///（2）		根据注入类型（byName/byType），提取 依赖bean，并统一存入 MutablePropertyValues 中。
	///（3）		应用 InstantiationAwareBeanPostProcessor 处理器的 postProcessPropertyValues 方法，对 属性获取完 毕填充前 的再 次处理，典型应用
	///		 是 RequiredAnnotationBeanPostProcessor类 中对 属性 的 验证。
	///（4）		将所有 PropertyValues 中的属性填充至 BeanWrapper 中。
	protected void populateBean(String beanName, RootBeanDefinition mbd, @Nullable BeanWrapper bw) {
		/// 验证 BeanWrapper 的存在性。
		if (bw == null) {
			if (mbd.hasPropertyValues()) {
				throw new BeanCreationException(
						mbd.getResourceDescription(), beanName, "Cannot apply property values to null instance");
			}
			else {
				// Skip property population phase for null instance.
				/// 没有 可填充 的属性
				return;
			}
		}

		// Give any InstantiationAwareBeanPostProcessors the opportunity to modify the
		// state of the bean before properties are set. This can be used, for example,
		// to support styles of field injection.
		///		给	InstantiatonAwareBeanPostProcessors	最后一次机会在 属性设置前 来改变bean
		///		如：可以用来	支持属性	注入的类型
		boolean continueWithPropertyPopulation = true;

		if (!mbd.isSynthetic() && hasInstantiationAwareBeanPostProcessors()) {
			/// 遍历所有 beanPostProcessors
			for (BeanPostProcessor bp : getBeanPostProcessors()) {
				if (bp instanceof InstantiationAwareBeanPostProcessor) {
					/// 是否为 InstantiationAwareBeanPostProcessor 类型
					InstantiationAwareBeanPostProcessor ibp = (InstantiationAwareBeanPostProcessor) bp;
					/// 返回值 为 是否 继续 填充Bean
					if (!ibp.postProcessAfterInstantiation(bw.getWrappedInstance(), beanName)) {
						/// 是否 继续 填充Bean
						continueWithPropertyPopulation = false;
						break;
					}
				}
			}
		}

		///	如果 后处理器 发出停止填充命令 则终止后续的执行
		if (!continueWithPropertyPopulation) {
			return;
		}


		///	获取容器 在解析 Bean定义资源时 为 BeanDefiniton 中设置的 属性值
		PropertyValues pvs = (mbd.hasPropertyValues() ? mbd.getPropertyValues() : null);

		// 对 依赖注入 处理，首先处理 autowiring 自动装配 的依赖注入
		if (mbd.getResolvedAutowireMode() == RootBeanDefinition.AUTOWIRE_BY_NAME ||
				mbd.getResolvedAutowireMode() == RootBeanDefinition.AUTOWIRE_BY_TYPE) {

			MutablePropertyValues newPvs = new MutablePropertyValues(pvs);

			// Add property values based on autowire by name if applicable.
			//	根据 Bean名称 进行 autowiring 自动装配处理
			if (mbd.getResolvedAutowireMode() == RootBeanDefinition.AUTOWIRE_BY_NAME) {

				/// 将匹配到的 name 和 bean 封装到 PropertyValue 中并且添加到 MutablePropertyValues 中，已供后续操作使用
				autowireByName(beanName, mbd, bw, newPvs);
			}

			// Add property values based on autowire by type if applicable.
			//	根据 Bean类型 进行 autowiring 自动装配处理
			if (mbd.getResolvedAutowireMode() == RootBeanDefinition.AUTOWIRE_BY_TYPE) {

				/// 处理结果保存在 MutablePropertyValues 实例中
				autowireByType(beanName, mbd, bw, newPvs);
			}

			pvs = newPvs;
		}


		/// 后处理器已经初始化
		boolean hasInstAwareBpps = hasInstantiationAwareBeanPostProcessors();

		/// 需要依赖检查
		boolean needsDepCheck = (mbd.getDependencyCheck() != RootBeanDefinition.DEPENDENCY_CHECK_NONE);

		if (hasInstAwareBpps || needsDepCheck) {
			if (pvs == null) {
				pvs = mbd.getPropertyValues();
			}
			/// 过滤 属性描述符
			PropertyDescriptor[] filteredPds = filterPropertyDescriptorsForDependencyCheck(bw, mbd.allowCaching);
			if (hasInstAwareBpps) {
				for (BeanPostProcessor bp : getBeanPostProcessors()) {
					/// 例如：CommonAnnotationBeanPostProcessor，AutowiredAnnotationBeanPostProcessor
					if (bp instanceof InstantiationAwareBeanPostProcessor) {
						InstantiationAwareBeanPostProcessor ibp = (InstantiationAwareBeanPostProcessor) bp;
						/// 对所有需要依赖检查的属性进行后处理 典型应用 RequiredAnnotationBeanPostProcessor-> 对属性验证处理
						/// todo 很关键的一步，处理 @Resource，@Autowired 等注解，对 field 或 method 进行注入
						pvs = ibp.postProcessPropertyValues(pvs, filteredPds, bw.getWrappedInstance(), beanName);
						if (pvs == null) {
							return;
						}
					}
				}
			}
			if (needsDepCheck) {
				/// 依赖检查，对应 depends-on 属性，3.0已弃用此属性
				checkDependencies(beanName, mbd, filteredPds, pvs);
			}
		}

		/// todo 很关键的一步，注入属性
		if (pvs != null) {
			// 对属性进行注入
			/// 将属性应用到bean中
			applyPropertyValues(beanName, mbd, bw, pvs);
		}
	}

	/**
	 * Fill in any missing property values with references to
	 * other beans in this factory if autowire is set to "byName".
	 * @param beanName the name of the bean we're wiring up.
	 * Useful for debugging messages; not used functionally.
	 * @param mbd bean definition to update through autowiring
	 * @param bw BeanWrapper from which we can obtain information about the bean
	 * @param pvs the PropertyValues to register wired objects with
	 */
	/// 根据 名称 对 属性 进行 自动依赖注入
	protected void autowireByName(
			String beanName, AbstractBeanDefinition mbd, BeanWrapper bw, MutablePropertyValues pvs) {

		///		对 Bean对象 中 非简单属性( 不是简单继承的对象，如 8种 原始类型，字符串，URL 等都是 简单属性) 进行处理
		///	 寻找 BeanWrapper 中需要 依赖注入 的属性
		///	 unsatisfied -> 不满意的，为得到满足的
		String[] propertyNames = unsatisfiedNonSimpleProperties(mbd, bw);
		for (String propertyName : propertyNames) {
			// 如果 Spring IOC容器 中包含 指定名称的 Bean
			if (containsBean(propertyName)) {
				//调用getBean方法向IOC容器索取指定名称的Bean实例，迭代触发属性的初始化和依赖注入

				///	递归初始化 相关的 bean
				Object bean = getBean(propertyName);

				///	为 指定名称 的 属性 赋予 属性值 (存入 MutablePropertyValues )
				pvs.add(propertyName, bean);
				//指定名称属性注册依赖Bean名称，进行属性依赖注入

				/// 注册依赖
				registerDependentBean(propertyName, beanName);
				if (logger.isDebugEnabled()) {
					logger.debug("Added autowiring by name from bean name '" + beanName +
							"' via property '" + propertyName + "' to bean named '" + propertyName + "'");
				}
			}
			else {
				if (logger.isTraceEnabled()) {
					logger.trace("Not autowiring property '" + propertyName + "' of bean '" + beanName +
							"' by name: no matching bean found");
				}
			}
		}
	}

	/**
	 * Abstract method defining "autowire by type" (bean properties by type) behavior.
	 * <p>This is like PicoContainer default, in which there must be exactly one bean
	 * of the property type in the bean factory. This makes bean factories simple to
	 * configure for small namespaces, but doesn't work as well as standard Spring
	 * behavior for bigger applications.
	 * @param beanName the name of the bean to autowire by type
	 * @param mbd the merged bean definition to update through autowiring
	 * @param bw BeanWrapper from which we can obtain information about the bean
	 * @param pvs the PropertyValues to register wired objects with
	 */
	//根据类型对属性进行自动依赖注入
		///		其实根据 名称自动匹配 的第一步就是寻找 bw 中需要依赖注入的属性，同样对于根据类型自动匹配的实现来讲第一步也是 bw中 需要 依赖注入
		///	的属性，然后 遍历这些属性 并寻找类型匹配的 bean，其中最复杂的就是寻找 类型匹配 的 bean。同时，Spring中 提供了对集合的 类型 注入支持，
		///	如使用注解的方式:
		/// @Autowired
		///		private List<Test> tests;
		///		Spring 将会把所有与 Test匹配 的 类型 找出来并注入到 tests 属性中，正是由于这一因素，所以在 autowireByType 函数中，新建了局部
		///	autowireBeanNames,用于存储所有依赖的 bean，如果只是对 非集合类 的 属性 注入来说，此属性并无用处。
	protected void autowireByType(
			String beanName, AbstractBeanDefinition mbd, BeanWrapper bw, MutablePropertyValues pvs) {

		///	获取 用户定义 的 类型转换器
		TypeConverter converter = getCustomTypeConverter();
		if (converter == null) {
			converter = bw;
		}

		///	存放解析的要 注入的属性
		Set<String> autowiredBeanNames = new LinkedHashSet<>(4);

		///	对 Bean对象 中非简单属性 (不是简单继承的对象，如8中原始类型，字符串，URL等都是简单属性) 进行处理
		String[] propertyNames = unsatisfiedNonSimpleProperties(mbd, bw);
		for (String propertyName : propertyNames) {
			try {
				/// 获取 指定属性名称 的 属性描述器
				PropertyDescriptor pd = bw.getPropertyDescriptor(propertyName);

				// Don't try autowiring by type for type Object: never makes sense,
				// even if it technically is a unsatisfied, non-simple property.

				///	不对 Object类型 的 属性 进行 autowiring 自动依赖注入
				if (Object.class != pd.getPropertyType()) {

					/// 获取属性的 setter 方法
					MethodParameter methodParam = BeanUtils.getWriteMethodParameter(pd);

					// Do not allow eager init for type matching in case of a prioritized post-processor.
					/// 检查 指定类型 是否可以被转换为 目标对象 的 类型
					boolean eager = !PriorityOrdered.class.isInstance(bw.getWrappedInstance());

					/// 创建一个要 被注入 的 依赖描述
					DependencyDescriptor desc = new AutowireByTypeDependencyDescriptor(methodParam, eager);

					//根据容器的Bean定义解析依赖关系，返回所有要被注入的Bean对象

					///		解析 指定 beanName的属性 所匹配的值，并把解析到的 属性名称 存储在 autowiredBeanNames 中，当属性存在 多个封装bean
					///			eg：@Autowired private List<A> aList;
					/// 			将会找到所有匹配 A类型 的 bean 并将其注入
					Object autowiredArgument = resolveDependency(desc, beanName, autowiredBeanNames, converter);

					if (autowiredArgument != null) {
						/// 为 属性 赋值所 引用的对象
						pvs.add(propertyName, autowiredArgument);
					}
					for (String autowiredBeanName : autowiredBeanNames) {
						/// 指定名称属性 注册依赖 Bean名 称，进行 属性依赖注入
						registerDependentBean(autowiredBeanName, beanName);
						if (logger.isDebugEnabled()) {
							logger.debug("Autowiring by type from bean name '" + beanName + "' via property '" +
									propertyName + "' to bean named '" + autowiredBeanName + "'");
						}
					}
					/// 释放已自动注入的属性
					autowiredBeanNames.clear();
				}
			}
			catch (BeansException ex) {
				throw new UnsatisfiedDependencyException(mbd.getResourceDescription(), beanName, propertyName, ex);
			}
		}
	}


	/**
	 * Return an array of non-simple bean properties that are unsatisfied.
	 * These are probably unsatisfied references to other beans in the
	 * factory. Does not include simple properties like primitives or Strings.
	 * @param mbd the merged bean definition the bean was created with
	 * @param bw the BeanWrapper the bean was created with
	 * @return an array of bean property names
	 * @see org.springframework.beans.BeanUtils#isSimpleProperty
	 */
	/// unsatisfied: 不满意的
	protected String[] unsatisfiedNonSimpleProperties(AbstractBeanDefinition mbd, BeanWrapper bw) {
		Set<String> result = new TreeSet<>();
		PropertyValues pvs = mbd.getPropertyValues();
		/// 获取 BeanWrapper 的文件描述符
		PropertyDescriptor[] pds = bw.getPropertyDescriptors();

		for (PropertyDescriptor pd : pds) {
			/// writer 方法不为空
			if (pd.getWriteMethod() != null && !isExcludedFromDependencyCheck(pd) && !pvs.contains(pd.getName()) &&
					!BeanUtils.isSimpleProperty(pd.getPropertyType())) {
				result.add(pd.getName());
			}
		}
		return StringUtils.toStringArray(result);
	}

	/**
	 * Extract a filtered set of PropertyDescriptors from the given BeanWrapper,
	 * excluding ignored dependency types or properties defined on ignored dependency interfaces.
	 * @param bw the BeanWrapper the bean was created with
	 * @param cache whether to cache filtered PropertyDescriptors for the given bean Class
	 * @return the filtered PropertyDescriptors
	 * @see #isExcludedFromDependencyCheck
	 * @see #filterPropertyDescriptorsForDependencyCheck(org.springframework.beans.BeanWrapper)
	 */
	protected PropertyDescriptor[] filterPropertyDescriptorsForDependencyCheck(BeanWrapper bw, boolean cache) {
		PropertyDescriptor[] filtered = this.filteredPropertyDescriptorsCache.get(bw.getWrappedClass());
		if (filtered == null) {
			filtered = filterPropertyDescriptorsForDependencyCheck(bw);
			if (cache) {
				/// 如果不存便添加
				PropertyDescriptor[] existing =
						this.filteredPropertyDescriptorsCache.putIfAbsent(bw.getWrappedClass(), filtered);
				if (existing != null) {
					filtered = existing;
				}
			}
		}
		return filtered;
	}

	/**
	 * Extract a filtered set of PropertyDescriptors from the given BeanWrapper,
	 * excluding ignored dependency types or properties defined on ignored dependency interfaces.
	 * @param bw the BeanWrapper the bean was created with
	 * @return the filtered PropertyDescriptors
	 * @see #isExcludedFromDependencyCheck
	 */
	protected PropertyDescriptor[] filterPropertyDescriptorsForDependencyCheck(BeanWrapper bw) {
		List<PropertyDescriptor> pds =
				new LinkedList<>(Arrays.asList(bw.getPropertyDescriptors()));
		for (Iterator<PropertyDescriptor> it = pds.iterator(); it.hasNext();) {
			PropertyDescriptor pd = it.next();
			if (isExcludedFromDependencyCheck(pd)) {
				it.remove();
			}
		}
		return pds.toArray(new PropertyDescriptor[pds.size()]);
	}

	/**
	 * Determine whether the given bean property is excluded from dependency checks.
	 * <p>This implementation excludes properties defined by CGLIB and
	 * properties whose type matches an ignored dependency type or which
	 * are defined by an ignored dependency interface.
	 * @param pd the PropertyDescriptor of the bean property
	 * @return whether the bean property is excluded
	 * @see #ignoreDependencyType(Class)
	 * @see #ignoreDependencyInterface(Class)
	 */
	protected boolean isExcludedFromDependencyCheck(PropertyDescriptor pd) {
		return (AutowireUtils.isExcludedFromDependencyCheck(pd) ||
				this.ignoredDependencyTypes.contains(pd.getPropertyType()) ||
				AutowireUtils.isSetterDefinedInInterface(pd, this.ignoredDependencyInterfaces));
	}

	/**
	 * Perform a dependency check that all properties exposed have been set,
	 * if desired. Dependency checks can be objects (collaborating beans),
	 * simple (primitives and String), or all (both).
	 * @param beanName the name of the bean
	 * @param mbd the merged bean definition the bean was created with
	 * @param pds the relevant property descriptors for the target bean
	 * @param pvs the property values to be applied to the bean
	 * @see #isExcludedFromDependencyCheck(java.beans.PropertyDescriptor)
	 */
	protected void checkDependencies(
			String beanName, AbstractBeanDefinition mbd, PropertyDescriptor[] pds, PropertyValues pvs)
			throws UnsatisfiedDependencyException {

		int dependencyCheck = mbd.getDependencyCheck();
		for (PropertyDescriptor pd : pds) {
			if (pd.getWriteMethod() != null && !pvs.contains(pd.getName())) {
				boolean isSimple = BeanUtils.isSimpleProperty(pd.getPropertyType());
				boolean unsatisfied = (dependencyCheck == RootBeanDefinition.DEPENDENCY_CHECK_ALL) ||
						(isSimple && dependencyCheck == RootBeanDefinition.DEPENDENCY_CHECK_SIMPLE) ||
						(!isSimple && dependencyCheck == RootBeanDefinition.DEPENDENCY_CHECK_OBJECTS);
				if (unsatisfied) {
					throw new UnsatisfiedDependencyException(mbd.getResourceDescription(), beanName, pd.getName(),
							"Set this property value or disable dependency checking for this bean.");
				}
			}
		}
	}

	/**
	 * Apply the given property values, resolving any runtime references
	 * to other beans in this bean factory. Must use deep copy, so we
	 * don't permanently modify this property.
	 * @param beanName the bean name passed for better exception information
	 * @param mbd the merged bean definition
	 * @param bw the BeanWrapper wrapping the target object
	 * @param pvs the new property values
	 */
	// 解析并 注入依赖 属性的过程
	protected void applyPropertyValues(String beanName, BeanDefinition mbd, BeanWrapper bw, PropertyValues pvs) {
		if (pvs.isEmpty()) {
			return;
		}

		// 封装属性值
		MutablePropertyValues mpvs = null;

		// 属性值对象 的 原始值类型
		List<PropertyValue> original;

		if (System.getSecurityManager() != null) {
			/// todo System.getSecurityManager() 学习
			if (bw instanceof BeanWrapperImpl) {
				//设置安全上下文，JDK安全机制
				((BeanWrapperImpl) bw).setSecurityContext(getAccessControlContext());
			}
		}


		if (pvs instanceof MutablePropertyValues) {
			mpvs = (MutablePropertyValues) pvs;
			// 属性值 已经转换
			/// 如果 mpvs 中的 值 已经被转换为 对应的类型, 那么可以直接设置到 beanWapper 中
			if (mpvs.isConverted()) {
				// Shortcut: use the pre-converted values as-is.
				try {
					// 为 实例化对象 设置 属性值
					bw.setPropertyValues(mpvs);
					return;
				}
				catch (BeansException ex) {
					throw new BeanCreationException(
							mbd.getResourceDescription(), beanName, "Error setting property values", ex);
				}
			}
			/// 获取 属性值对象 的 原始类型值
			original = mpvs.getPropertyValueList();
		}
		else {
				/// 如果 pvs 并不是使用 MutableePropertyValues 封装的类型，那么直接接使用 原始的属性 获取方法
			original = Arrays.asList(pvs.getPropertyValues());
		}



		/// 获取 用户自定义 的 类型转换
		TypeConverter converter = getCustomTypeConverter();
		if (converter == null) {
			converter = bw;
		}




		/// 获取对应的 解析器，对 属性值 得解析
		BeanDefinitionValueResolver valueResolver = new BeanDefinitionValueResolver(this, beanName, mbd, converter);




		// Create a deep copy, resolving any references for values.

		// 为 属性的 解析值 创建一个拷贝，将 拷贝的数据 注入到 实例对象中
		List<PropertyValue> deepCopy = new ArrayList<>(original.size());
		boolean resolveNecessary = false;

		/// 遍历属性，将 属性 转换为 对应的对应属性的 类型
		for (PropertyValue pv : original) {
			// 属性值 被转换过，不需要转换
			if (pv.isConverted()) {
				deepCopy.add(pv);
			}

			// 属性值 需要转换
			else {
				/// 获取属性名
				String propertyName = pv.getName();

				// 原始的 属性值，即转换之前 的 属性值
				Object originalValue = pv.getValue();


				// todo 转换属性值  valueResolver.resolveValueIfNecessary
				//转换 属性值，例如将 引用 转换为 IOC容器 中 实例化对象引用
				Object resolvedValue = valueResolver.resolveValueIfNecessary(pv, originalValue);


				//转换之后的 属性值
				Object convertedValue = resolvedValue;

				//属性值 是否 可以转换
				boolean convertible = bw.isWritableProperty(propertyName) &&
						!PropertyAccessorUtils.isNestedOrIndexedProperty(propertyName);
				if (convertible) {

					// 使用 用户自定义 的 类型转换器转换 属性值
					convertedValue = convertForProperty(resolvedValue, propertyName, bw, converter);
				}
				// Possibly store converted value in merged bean definition,
				// in order to avoid re-conversion for every created bean instance.



				// 存储转换后 的 属性值，避免每次 属性注入时 的 转换工作
				if (resolvedValue == originalValue) {
					if (convertible) {
						// 设置 属性 转换之后的 值
						pv.setConvertedValue(convertedValue);
					}
					deepCopy.add(pv);
				}

				// 属性 是 可转换的，
				// 且 属性原始值 是 字符串类型，
				// 且 属性 的 原始类型值 不是 动态生成的 字符串，
				// 且 属性的原始值 不是集合 或者 数组类型
				else if (convertible && originalValue instanceof TypedStringValue &&
						!((TypedStringValue) originalValue).isDynamic() &&
						!(convertedValue instanceof Collection || ObjectUtils.isArray(convertedValue))) {
					pv.setConvertedValue(convertedValue);

					// 重新封装属性的值
					deepCopy.add(pv);
				}
				else {
					resolveNecessary = true;
					deepCopy.add(new PropertyValue(pv, convertedValue));
				}


			}

		}

		///  标记属性值已经转换过
		if (mpvs != null && !resolveNecessary) {
			// 标记属性值已经转换过
			mpvs.setConverted();
		}

		// Set our (possibly massaged) deep copy.
		// 进行 属性依赖 注入
		try {
			bw.setPropertyValues(new MutablePropertyValues(deepCopy));
		}
		catch (BeansException ex) {
			throw new BeanCreationException(
					mbd.getResourceDescription(), beanName, "Error setting property values", ex);
		}
	}

	/**
	 * Convert the given value for the specified target property.
	 */
	@Nullable
	private Object convertForProperty(
			@Nullable Object value, String propertyName, BeanWrapper bw, TypeConverter converter) {

		if (converter instanceof BeanWrapperImpl) {
			return ((BeanWrapperImpl) converter).convertForProperty(value, propertyName);
		}
		else {
			PropertyDescriptor pd = bw.getPropertyDescriptor(propertyName);
			MethodParameter methodParam = BeanUtils.getWriteMethodParameter(pd);
			return converter.convertIfNecessary(value, pd.getPropertyType(), methodParam);
		}
	}


	/**
	 * Initialize the given bean instance, applying factory callbacks
	 * as well as init methods and bean post processors.
	 * <p>Called from {@link #createBean} for traditionally defined beans,
	 * and from {@link #initializeBean} for existing bean instances.
	 * @param beanName the bean name in the factory (for debugging purposes)
	 * @param bean the new bean instance we may need to initialize
	 * @param mbd the bean definition that the bean was created with
	 * (can also be {@code null}, if given an existing bean instance)
	 * @return the initialized bean instance (potentially wrapped)
	 * @see BeanNameAware
	 * @see BeanClassLoaderAware
	 * @see BeanFactoryAware
	 * @see #applyBeanPostProcessorsBeforeInitialization
	 * @see #invokeInitMethods
	 * @see #applyBeanPostProcessorsAfterInitialization
	 */
	///	1.激活Aware方法
	///			在分析原理之前，我们先了解一下 Aware 的使用。Spring 中提供一些 Aware 相关接口，比如 BeanFactoryAware、ApplicationContextAware、
	///		ResourceLoaderAware、ServletContextAware 等，实现 Aware 接口的 bean 在被初始之后，可以取得一些相对应的资源，例如实现 BeanFactoryAware
	///		的 bean 在初始后，Spring容器 将会注入 BeanFactory 的实例，而实现 ApplicationContextAware 的 bean，在  bean 被出事后，将会被注入 ApplicationContext 的
	///		实例等。

	///  2.处理器的应用
	///			BeanPostProcessor 相信大家都不陌生，这是 Spring 中开放式架构中一个必不可少的亮点，给 用户充足的权限 去更改或者扩展 Spring，而除了 BeanPostProcessor
	///		外还有很多其他的 PostProcessor，当然大部分都是以此为基础，继承自 BeanPostProcessor。BeanPostProcessor 的使用位置就是这里，在 调用客户 自定义初始化方法 前
	///		以及 调用自定义初始化方法 后 分别会调用 BeanPostProcessor 的 postProcessBeforeInitialization 和 postProcessAfterInitialization 方法，使用户可以根据自己的
	///		业务需求进行响应的处理。

	/// 3.激活自定义的 init 方法
	///			客户 定制的初始化方法 除了我们熟知的使用配置 init-method 外，还有使自定义的 bean 实现 InitializingBean 接口，并在 afterPropertiesSet 中实现自己的初始化业务逻辑。
	///		init-method 与 afterPropertiesSet 都是在初始化 bean 时执行，执行顺序是 afterPropertiesSet 先执行，而 init-method 后执行。
	//		初始容器创建的Bean实例对象，为其添加BeanPostProcessor后置处理器
	protected Object initializeBean(final String beanName, final Object bean, @Nullable RootBeanDefinition mbd) {
		// JDK 的安全机制验证权限
		if (System.getSecurityManager() != null) {
			// 实现 PrivilegedAction接口 的匿名内部类
			AccessController.doPrivileged((PrivilegedAction<Object>) () -> {
				invokeAwareMethods(beanName, bean);
				return null;
			}, getAccessControlContext());
		}
		else {

			///	 a. 对特殊的 bean 处理：Aware、BeanClassLoaderAware、BeanFactoryAware
			invokeAwareMethods(beanName, bean);
		}

		Object wrappedBean = bean;

		/// 	对 BeanPostProcessor后置处理器 的 postProcessBeforeInitialization
		//		回调方法的调用，为 Bean 实例初始化前做一些处理
		if (mbd == null || !mbd.isSynthetic()) {

			///	b. 应用后处理器
			wrappedBean = applyBeanPostProcessorsBeforeInitialization(wrappedBean, beanName);
		}

		//		调用 Bean实例对象 初始化的方法，这个初始化方法是在 Spring Bean 定义配置
		//		文件中通过 init-method 属性指定的
		try {
			///	c. 激活用户自定义的 init 方法
			invokeInitMethods(beanName, wrappedBean, mbd);
		}
		catch (Throwable ex) {
			throw new BeanCreationException(
					(mbd != null ? mbd.getResourceDescription() : null),
					beanName, "Invocation of init method failed", ex);
		}
		//	对 BeanPostProcessor 后置处理器的 postProcessAfterInitialization
		//	回调方法的调用，为 Bean实例初始化 之后做一些处理
		if (mbd == null || !mbd.isSynthetic()) {
			//	d. 后处理器应用( todo  我们熟知的 AOP 就是在这里 将 advice 动态织入 bean 中 在这里实现 (2))
			wrappedBean = applyBeanPostProcessorsAfterInitialization(wrappedBean, beanName);
		}

		return wrappedBean;
	}

	///	1.激活Aware方法
	///			在分析原理之前，我们先了解一下 Aware 的使用。Spring 中提供一些 Aware 相关接口，比如 BeanFactoryAware、ApplicationContextAware、
	///		ResourceLoaderAware、ServletContextAware 等，实现 Aware 接口的 bean 在被初始之后，可以取得一些相对应的资源，例如实现 BeanFactoryAware
	///		的 bean 在初始后，Spring容器 将会注入 BeanFactory 的实例，而实现 ApplicationContextAware 的 bean，在 bean 被出事后，将会被注入 ApplicationContext 的
	///		实例等。
	//		初始容器创建的 Bean 实例对象，为其添加 BeanPostProcessor 后置处理器
	private void invokeAwareMethods(final String beanName, final Object bean) {
		if (bean instanceof Aware) {
			if (bean instanceof BeanNameAware) {
				((BeanNameAware) bean).setBeanName(beanName);
			}
			if (bean instanceof BeanClassLoaderAware) {
				ClassLoader bcl = getBeanClassLoader();
				if (bcl != null) {
					((BeanClassLoaderAware) bean).setBeanClassLoader(bcl);
				}
			}
			if (bean instanceof BeanFactoryAware) {
				((BeanFactoryAware) bean).setBeanFactory(AbstractAutowireCapableBeanFactory.this);
			}
		}
	}

	/**
	 * Give a bean a chance to react now all its properties are set,
	 * and a chance to know about its owning bean factory (this object).
	 * This means checking whether the bean implements InitializingBean or defines
	 * a custom init method, and invoking the necessary callback(s) if it does.
	 * @param beanName the bean name in the factory (for debugging purposes)
	 * @param bean the new bean instance we may need to initialize
	 * @param mbd the merged bean definition that the bean was created with
	 * (can also be {@code null}, if given an existing bean instance)
	 * @throws Throwable if thrown by init methods or by the invocation process
	 * @see #invokeCustomInitMethod
	 */
	/// 3.激活自定义的 init 方法
	///			客户定制的初始化方法除了我们熟知的使用配置 init-method 外，还有使自定义的 bean 实现 InitializingBean 接口，并在 afterPropertiesSet 中实现自己的初始化业务逻辑。
	///		init-method 与 afterPropertiesSet 都是在初始化 bean 时执行，执行顺序是 afterPropertiesSet 先执行，而 init-method 后执行。
	//		初始容器创建的 Bean 实例对象，为其添加 BeanPostProcessor 后置处理器
	protected void invokeInitMethods(String beanName, final Object bean, @Nullable RootBeanDefinition mbd)
			throws Throwable {

		///	首先会检查是否是	InitializingBean，如果是的话需要调用	afterPropertiesSet	方法
		boolean isInitializingBean = (bean instanceof InitializingBean);
		if (isInitializingBean && (mbd == null || !mbd.isExternallyManagedInitMethod("afterPropertiesSet"))) {
			if (logger.isDebugEnabled()) {
				logger.debug("Invoking afterPropertiesSet() on bean with name '" + beanName + "'");
			}
			if (System.getSecurityManager() != null) {
				try {
					AccessController.doPrivileged((PrivilegedExceptionAction<Object>) () -> {
						((InitializingBean) bean).afterPropertiesSet();
						return null;
					}, getAccessControlContext());
				}
				catch (PrivilegedActionException pae) {
					throw pae.getException();
				}
			}
			else {
				///	属性初始化的处理
				((InitializingBean) bean).afterPropertiesSet();
			}
		}

		if (mbd != null && bean.getClass() != NullBean.class) {
			String initMethodName = mbd.getInitMethodName();
			if (StringUtils.hasLength(initMethodName) &&
					!(isInitializingBean && "afterPropertiesSet".equals(initMethodName)) &&
					!mbd.isExternallyManagedInitMethod(initMethodName)) {
				///	调用  自定义初始化  方法
				invokeCustomInitMethod(beanName, bean, mbd);
			}
		}
	}

	/**
	 * Invoke the specified custom init method on the given bean.
	 * Called by invokeInitMethods.
	 * <p>Can be overridden in subclasses for custom resolution of init
	 * methods with arguments.
	 * @see #invokeInitMethods
	 */
	protected void invokeCustomInitMethod(String beanName, final Object bean, RootBeanDefinition mbd)
			throws Throwable {

		/// 初始化方法
		String initMethodName = mbd.getInitMethodName();
		Assert.state(initMethodName != null, "No init method set");
		final Method initMethod = (mbd.isNonPublicAccessAllowed() ?
				BeanUtils.findMethod(bean.getClass(), initMethodName) :
				ClassUtils.getMethodIfAvailable(bean.getClass(), initMethodName));

		if (initMethod == null) {
			if (mbd.isEnforceInitMethod()) {
				throw new BeanDefinitionValidationException("Couldn't find an init method named '" +
						initMethodName + "' on bean with name '" + beanName + "'");
			}
			else {
				if (logger.isDebugEnabled()) {
					logger.debug("No default init method named '" + initMethodName +
							"' found on bean with name '" + beanName + "'");
				}
				// Ignore non-existent default lifecycle methods.
				return;
			}
		}

		if (logger.isDebugEnabled()) {
			logger.debug("Invoking init method  '" + initMethodName + "' on bean with name '" + beanName + "'");
		}

		if (System.getSecurityManager() != null) {
			AccessController.doPrivileged((PrivilegedAction<Object>) () -> {
				ReflectionUtils.makeAccessible(initMethod);
				return null;
			});
			try {
				AccessController.doPrivileged((PrivilegedExceptionAction<Object>) () ->
					initMethod.invoke(bean), getAccessControlContext());
			}
			catch (PrivilegedActionException pae) {
				InvocationTargetException ex = (InvocationTargetException) pae.getException();
				throw ex.getTargetException();
			}
		}
		else {
			try {
				ReflectionUtils.makeAccessible(initMethod);
				/// 调用初始化方法
				initMethod.invoke(bean);
			}
			catch (InvocationTargetException ex) {
				throw ex.getTargetException();
			}
		}
	}


	/**
	 * Applies the {@code postProcessAfterInitialization} callback of all
	 * registered BeanPostProcessors, giving them a chance to post-process the
	 * object obtained from FactoryBeans (for example, to auto-proxy them).
	 * @see #applyBeanPostProcessorsAfterInitialization
	 */
	@Override
	protected Object postProcessObjectFromFactoryBean(Object object, String beanName) {
		return applyBeanPostProcessorsAfterInitialization(object, beanName);
	}

	/**
	 * Overridden to clear FactoryBean instance cache as well.
	 */
	@Override
	protected void removeSingleton(String beanName) {
		super.removeSingleton(beanName);
		this.factoryBeanInstanceCache.remove(beanName);
	}


	/**
	 * Special DependencyDescriptor variant for Spring's good old autowire="byType" mode.
	 * Always optional; never considering the parameter name for choosing a primary candidate.
	 */
	@SuppressWarnings("serial")
	private static class AutowireByTypeDependencyDescriptor extends DependencyDescriptor {

		public AutowireByTypeDependencyDescriptor(MethodParameter methodParameter, boolean eager) {
			super(methodParameter, false, eager);
		}

		@Override
		public String getDependencyName() {
			return null;
		}
	}

}
