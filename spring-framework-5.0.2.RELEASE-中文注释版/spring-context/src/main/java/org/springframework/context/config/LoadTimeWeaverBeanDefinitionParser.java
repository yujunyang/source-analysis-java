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

package org.springframework.context.config;

import org.w3c.dom.Element;

import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.parsing.BeanComponentDefinition;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.beans.factory.xml.AbstractSingleBeanDefinitionParser;
import org.springframework.beans.factory.xml.ParserContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.weaving.AspectJWeavingEnabler;
import org.springframework.lang.Nullable;
import org.springframework.util.ClassUtils;

/**
 * Parser for the &lt;context:load-time-weaver/&gt; element.
 *
 * @author Juergen Hoeller
 * @since 2.5
 */
class LoadTimeWeaverBeanDefinitionParser extends AbstractSingleBeanDefinitionParser {

	/**
	 * The bean name of the internally managed AspectJ weaving enabler.
	 * @since 4.3.1
	 */
	public static final String ASPECTJ_WEAVING_ENABLER_BEAN_NAME =
			"org.springframework.context.config.internalAspectJWeavingEnabler";

	private static final String ASPECTJ_WEAVING_ENABLER_CLASS_NAME =
			"org.springframework.context.weaving.AspectJWeavingEnabler";

	private static final String DEFAULT_LOAD_TIME_WEAVER_CLASS_NAME =
			"org.springframework.context.weaving.DefaultContextLoadTimeWeaver";

	private static final String WEAVER_CLASS_ATTRIBUTE = "weaver-class";

	private static final String ASPECTJ_WEAVING_ATTRIBUTE = "aspectj-weaving";


	@Override
	protected String getBeanClassName(Element element) {
		if (element.hasAttribute(WEAVER_CLASS_ATTRIBUTE)) {
			return element.getAttribute(WEAVER_CLASS_ATTRIBUTE);
		}
		return DEFAULT_LOAD_TIME_WEAVER_CLASS_NAME;
	}

	@Override
	protected String resolveId(Element element, AbstractBeanDefinition definition, ParserContext parserContext) {
		return ConfigurableApplicationContext.LOAD_TIME_WEAVER_BEAN_NAME;
	}

	///该函数的核心作用其实就是注册一个对于AspectJ的处理类org.Springframework.context.weaving.AspectJWeavingEnabler，
	///注册流程如下
	///（1）是否开启AspectJ。
	///		之前虽然反复提到了在配置文件中加入了<context:load-time-wearer>便相当于加入了AspectJ开关。但是，并不是配置了
	///	这个标签就意味着开启了AspectJ功能，这个标签中还有一个属性aspectj-weaving，这个属性有3个备选值，on、off和autodetect，
	///	默认是autodetect，也就是说如果我们只是使用了<context:load-time-wearer>，那么Spring会帮助我们检测是否可以使用AspectJ功能
	///而检测的依据便是文件META-INF/aop.xml是否存在。
	///（2）将org.Springframework.context.weaving.AspectJWeavingEnabler封装在BeanDefinition中注册
	///		当通过AspectJ功能验证后便可以进行AspectJWeavingEnable的注册了，注册的方式很简单，无非是将类路径注册在新初始化的
	/// RootBeanDefinition中，在RootBeanDefintion的或群时会转换成对应的class。
	///		尽管在init方法中注册了AspectJWeavingEnable，但是对于标签本身Spring也会以bean的形式保存，也就是当Spring解析到
	///<context:load-time-weaver/>标签的时候也会产生一个bean，而这个bean中的信息什么呢？
	///		在LoadTimeWeaverBeanDefinitionParser类中有这样的函数：
	///
	//	@Override
	//	protected String getBeanClassName(Element element) {
	//		if (element.hasAttribute(WEAVER_CLASS_ATTRIBUTE)) {
	//			return element.getAttribute(WEAVER_CLASS_ATTRIBUTE);
	//		}
	//		return DEFAULT_LOAD_TIME_WEAVER_CLASS_NAME;
	//	}
	//
	//	@Override
	//	protected String resolveId(Element element, AbstractBeanDefinition definition, ParserContext parserContext) {
	//		return ConfigurableApplicationContext.LOAD_TIME_WEAVER_BEAN_NAME;
	//	}
	///其中，可以看到：
	///		WEAVER_CLASS_ATTRIBUTE = "weaver-class"
	///		DEFAULT_LOAD_TIME_WEAVER_CLASS_NAME ="org.springframework.context.weaving.DefaultContextLoadTimeWeaver";
	///		LOAD_TIME_WEAVER_BEAN_NAME = "loadTimeWeaver"
	///单凭以上的信息我们至少可以断定，当Spring在读取到自定义标签<context:load-time-weaver/>后会产生一个bean，而这个bean的id
	///为"loadTimeWeaver"，class为"org.springframework.context.weaving.DefaultContextLoadTimeWeaver"，也就是完成了DefaultContextLoadTimeWeaver
	///类的注册。
	///		完成了以上的注册功能后，并不意味这在Spring中就可以使用AspectJ了，因为我们还有一个很重要的步骤忽略了，就是LoadTimeWeaverAwareProcessor
	///的注册。在AbstractApplicationContext中的prepareBeanFactory函数中有这样一段代码：
	///增加对AspectJ的支持
	//	// Detect a LoadTimeWeaver and prepare for weaving, if found.
	//		if (beanFactory.containsBean(LOAD_TIME_WEAVER_BEAN_NAME)) {
	//		beanFactory.addBeanPostProcessor(new LoadTimeWeaverAwareProcessor(beanFactory));
	//		// Set a temporary ClassLoader for type matching.
	//		beanFactory.setTempClassLoader(new ContextTypeMatchClassLoader(beanFactory.getBeanClassLoader()));
	//	}
	///在AbstractApplicationContext中的prepareBeanFactory函数是在容器初始化时候调用的，也就是说只有注册了LoadTimeWeaverAwareProcessor
	///才会激活整个AspectJ的功能。
	@Override
	protected void doParse(Element element, ParserContext parserContext, BeanDefinitionBuilder builder) {
		builder.setRole(BeanDefinition.ROLE_INFRASTRUCTURE);

		if (isAspectJWeavingEnabled(element.getAttribute(ASPECTJ_WEAVING_ATTRIBUTE), parserContext)) {
			if (!parserContext.getRegistry().containsBeanDefinition(ASPECTJ_WEAVING_ENABLER_BEAN_NAME)) {
				RootBeanDefinition def = new RootBeanDefinition(ASPECTJ_WEAVING_ENABLER_CLASS_NAME);
				parserContext.registerBeanComponent(
						new BeanComponentDefinition(def, ASPECTJ_WEAVING_ENABLER_BEAN_NAME));
			}

			if (isBeanConfigurerAspectEnabled(parserContext.getReaderContext().getBeanClassLoader())) {
				new SpringConfiguredBeanDefinitionParser().parse(element, parserContext);
			}
		}
	}

	/// "META-INF/aop.xml"
	protected boolean isAspectJWeavingEnabled(String value, ParserContext parserContext) {
		if ("on".equals(value)) {
			return true;
		}
		else if ("off".equals(value)) {
			return false;
		}
		else {
			// Determine default...
			ClassLoader cl = parserContext.getReaderContext().getBeanClassLoader();
			return (cl != null && cl.getResource(AspectJWeavingEnabler.ASPECTJ_AOP_XML_RESOURCE) != null);
		}
	}

	protected boolean isBeanConfigurerAspectEnabled(@Nullable ClassLoader beanClassLoader) {
		return ClassUtils.isPresent(SpringConfiguredBeanDefinitionParser.BEAN_CONFIGURER_ASPECT_CLASS_NAME,
				beanClassLoader);
	}

}
