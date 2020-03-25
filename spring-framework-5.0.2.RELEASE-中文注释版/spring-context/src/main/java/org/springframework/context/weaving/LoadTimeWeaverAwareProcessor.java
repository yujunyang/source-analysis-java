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

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.instrument.classloading.LoadTimeWeaver;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;

/**
 * {@link org.springframework.beans.factory.config.BeanPostProcessor}
 * implementation that passes the context's default {@link LoadTimeWeaver}
 * to beans that implement the {@link LoadTimeWeaverAware} interface.
 *
 * <p>{@link org.springframework.context.ApplicationContext Application contexts}
 * will automatically register this with their underlying {@link BeanFactory bean factory},
 * provided that a default {@code LoadTimeWeaver} is actually available.
 *
 * <p>Applications should not use this class directly.
 *
 * @author Juergen Hoeller
 * @since 2.5
 * @see LoadTimeWeaverAware
 * @see org.springframework.context.ConfigurableApplicationContext#LOAD_TIME_WEAVER_BEAN_NAME
 */
public class LoadTimeWeaverAwareProcessor implements BeanPostProcessor, BeanFactoryAware {

	@Nullable
	private LoadTimeWeaver loadTimeWeaver;

	@Nullable
	private BeanFactory beanFactory;


	/**
	 * Create a new {@code LoadTimeWeaverAwareProcessor} that will
	 * auto-retrieve the {@link LoadTimeWeaver} from the containing
	 * {@link BeanFactory}, expecting a bean named
	 * {@link ConfigurableApplicationContext#LOAD_TIME_WEAVER_BEAN_NAME "loadTimeWeaver"}.
	 */
	public LoadTimeWeaverAwareProcessor() {
	}

	/**
	 * Create a new {@code LoadTimeWeaverAwareProcessor} for the given
	 * {@link LoadTimeWeaver}.
	 * <p>If the given {@code loadTimeWeaver} is {@code null}, then a
	 * {@code LoadTimeWeaver} will be auto-retrieved from the containing
	 * {@link BeanFactory}, expecting a bean named
	 * {@link ConfigurableApplicationContext#LOAD_TIME_WEAVER_BEAN_NAME "loadTimeWeaver"}.
	 * @param loadTimeWeaver the specific {@code LoadTimeWeaver} that is to be used
	 */
	public LoadTimeWeaverAwareProcessor(@Nullable LoadTimeWeaver loadTimeWeaver) {
		this.loadTimeWeaver = loadTimeWeaver;
	}

	/**
	 * Create a new {@code LoadTimeWeaverAwareProcessor}.
	 * <p>The {@code LoadTimeWeaver} will be auto-retrieved from
	 * the given {@link BeanFactory}, expecting a bean named
	 * {@link ConfigurableApplicationContext#LOAD_TIME_WEAVER_BEAN_NAME "loadTimeWeaver"}.
	 * @param beanFactory the BeanFactory to retrieve the LoadTimeWeaver from
	 */
	public LoadTimeWeaverAwareProcessor(BeanFactory beanFactory) {
		this.beanFactory = beanFactory;
	}


	@Override
	public void setBeanFactory(BeanFactory beanFactory) {
		this.beanFactory = beanFactory;
	}


	/// LoadTimeWeaverAwareProcessor 详解 https://www.cnblogs.com/wade-luffy/p/6078446.html
	@Override
	public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
		///		这个 后处理器 只对 LoadTimeWeaverAware 类型的 bean 起作用，而纵观所有的 bean，实现 LoadTimeWeaverAware 接口的类只有
		/// AspectJWeavingEnabler。 当在 Spring 中调用 AspectJWeavingEnabler 时，this.loadTimeWeaver 尚未被初始化，那么，
		/// 会直接调用 beanFactory.getBean 方法获取对应的 DefaultContextLoadTimeWeaver 类型的 bean，并将其设置为 AspectJWeavingEnabler
		/// 类型的 bean 的 LoadTimeWeaver 属性中。

		// 			 AspectJWeavingEnabler 实现了 BeanFactoryPostProcessor、BeanClassLoaderAware 以及 Ordered 接口.
		// 		实现 BeanClassLoaderAware 接口保证了在 bean 初始化的时候调用 AbstractAutowireCapableFactory 的 invokeAwareMethods 的时候将 beanClassLoader 赋值给当前类。
		// 		而实现 order 接口则保证在实例化 bean 时当前 bean 会被最先初始化。

		///		而 DefaultContextLoadTimeWeaver 类同时又同时实现了 LoadTimeWeaver、BeanClassLoaderAware 以及 DisposableBean。其中
		/// DisposableBean 接口保证在 bean 销毁时会调用 destory 方法进行 bean 的清理，
		// 		而 BeanClassLoaderAware 接口则保证 bean的 初始化调用 AbstractAutowireCapableBeanFactory 的 invokeAwareMethods 时调用 setBeanClassLoader 方法。

//				就是经过以上程序 setBeanClassLoader 和 postProcessBeforeInitialization 的处理后，在 Spring 中的 bean 之间的关系如下：
//					（1）AspectJWeavingEnabler 类型的 bean 中的 loadTimeWeaver 属性被初始化为 DefaultContextLoadTimeWeaver 类型的 bean；
//					（2）DefaultContextLoadTimeWeaver 类型的 bean 中的 loadTimeWeaver 属性被初始化为 InstrumentationLoadTimeWeaver。

		if (bean instanceof LoadTimeWeaverAware) {
			LoadTimeWeaver ltw = this.loadTimeWeaver;
			if (ltw == null) {
				Assert.state(this.beanFactory != null,
						"BeanFactory required if no LoadTimeWeaver explicitly specified");
				///	LOAD_TIME_WEAVER_BEAN_NAME 为 “BeanClassLoaderAware” ltw 为 DefaultContextLoadTimeWeaver
				ltw = this.beanFactory.getBean(
						ConfigurableApplicationContext.LOAD_TIME_WEAVER_BEAN_NAME, LoadTimeWeaver.class);
			}
			((LoadTimeWeaverAware) bean).setLoadTimeWeaver(ltw);
		}
		return bean;
	}

	@Override
	public Object postProcessAfterInitialization(Object bean, String name) {
		return bean;
	}

}
