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

package org.springframework.beans.factory.xml;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.BeanDefinitionStoreException;
import org.springframework.beans.factory.config.BeanDefinitionHolder;
import org.springframework.beans.factory.parsing.BeanComponentDefinition;
import org.springframework.beans.factory.support.BeanDefinitionReaderUtils;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternUtils;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.ResourceUtils;
import org.springframework.util.StringUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Default implementation of the {@link BeanDefinitionDocumentReader} interface that
 * reads bean definitions according to the "spring-beans" DTD and XSD format
 * (Spring's default XML bean definition format).
 *
 * <p>The structure, elements, and attribute names of the required XML document
 * are hard-coded in this class. (Of course a transform could be run if necessary
 * to produce this format). {@code <beans>} does not need to be the root
 * element of the XML document: this class will parse all bean definition elements
 * in the XML file, regardless of the actual root element.
 *
 * @author Rod Johnson
 * @author Juergen Hoeller
 * @author Rob Harrop
 * @author Erik Wiersma
 * @since 18.12.2003
 */
public class DefaultBeanDefinitionDocumentReader implements BeanDefinitionDocumentReader {

	public static final String BEAN_ELEMENT = BeanDefinitionParserDelegate.BEAN_ELEMENT;

	public static final String NESTED_BEANS_ELEMENT = "beans";

	public static final String ALIAS_ELEMENT = "alias";

	public static final String NAME_ATTRIBUTE = "name";

	public static final String ALIAS_ATTRIBUTE = "alias";

	public static final String IMPORT_ELEMENT = "import";

	public static final String RESOURCE_ATTRIBUTE = "resource";

	public static final String PROFILE_ATTRIBUTE = "profile";


	protected final Log logger = LogFactory.getLog(getClass());

	@Nullable
	private XmlReaderContext readerContext;

	@Nullable
	private BeanDefinitionParserDelegate delegate;


	/**
	 * This implementation parses bean definitions according to the "spring-beans" XSD
	 * (or DTD, historically).
	 * <p>Opens a DOM Document; then initializes the default settings
	 * specified at the {@code <beans/>} level; then parses the contained bean definitions.
	 */
	//		根据Spring DTD 对 Bean 的定义规则解析 Bean 定义 Document 对象
	//	这个方法重要目标之一就是提取 root，以便于再次将 root 作为参数继续 BeanDefinition 的注册
	@Override
	public void registerBeanDefinitions(Document doc, XmlReaderContext readerContext) {

		this.readerContext = readerContext;
		logger.debug("Loading bean definitions");
		// 获得 Document的根元素
		Element root = doc.getDocumentElement();
		// 注册 BeanDefinition
		doRegisterBeanDefinitions(root);
	}

	/**
	 * Return the descriptor for the XML resource that this parser works on.
	 */
	protected final XmlReaderContext getReaderContext() {
		Assert.state(this.readerContext != null, "No XmlReaderContext available");
		return this.readerContext;
	}

	/**
	 * Invoke the {@link org.springframework.beans.factory.parsing.SourceExtractor}
	 * to pull the source metadata from the supplied {@link Element}.
	 */
	@Nullable
	protected Object extractSource(Element ele) {
		return getReaderContext().extractSource(ele);
	}


	/**
	 * Register each bean definition within the given root {@code <beans/>} element.
	 */
	///	核心逻辑
	protected void doRegisterBeanDefinitions(Element root) {
		// Any nested <beans> elements will cause recursion in this method. In
		// order to propagate and preserve <beans> default-* attributes correctly,
		// keep track of the current (parent) delegate, which may be null. Create
		// the new (child) delegate with a reference to the parent for fallback purposes,
		// then ultimately reset this.delegate back to its original (parent) reference.
		// this behavior emulates a stack of delegates without actually necessitating one.

		//	委派模式
		//		具体的解析过程由 BeanDefinitionParserDelegate 实现，
		//			BeanDefinitionParserDelegate 中定义了 Spring Bean 定义 XML文件 的各种元素
		BeanDefinitionParserDelegate parent = this.delegate;
		this.delegate = createDelegate(getReaderContext(), root, parent);
	    // 是否默认的命名空间 namespace : http://www.springframework.org/schema/beans
		if (this.delegate.isDefaultNamespace(root)) {
			// 处理 profile 属性
			String profileSpec = root.getAttribute(PROFILE_ATTRIBUTE);
			//
			if (StringUtils.hasText(profileSpec)) {
				String[] specifiedProfiles = StringUtils.tokenizeToStringArray(
						profileSpec, BeanDefinitionParserDelegate.MULTI_VALUE_ATTRIBUTE_DELIMITERS);
				///	XmlReaderContext
				// todo XmlReaderContext ??
				//	spring.profiles.active
				if (!getReaderContext().getEnvironment().acceptsProfiles(specifiedProfiles)) {
					if (logger.isInfoEnabled()) {
						logger.info("Skipped XML bean definition file due to specified profiles [" + profileSpec +
								"] not matching: " + getReaderContext().getResource());
					}
					return;
				}
			}
		}

		// 模板方法模式，留给子类实现
		//		在解析 Bean 定义之前，进行自定义的解析，增强解析过程的可扩展性
		preProcessXml(root);
		// 从 Document 的根元素开始进行 Bean 定义的 Document 对象
		parseBeanDefinitions(root, this.delegate);
		// 	模板方法模式，留给子类实现
		//		在解析 Bean 定义之后，进行自定义的解析，增加解析过程的可扩展性
		postProcessXml(root);

		this.delegate = parent;
	}

	// 创建 BeanDefinitionParserDelegate ，用于完成真正的解析过程
	protected BeanDefinitionParserDelegate createDelegate(
			XmlReaderContext readerContext, Element root, @Nullable BeanDefinitionParserDelegate parentDelegate) {

		BeanDefinitionParserDelegate delegate = new BeanDefinitionParserDelegate(readerContext);
		// BeanDefinitionParserDelegate 根据 parentDelegate 初始化 Document 根元素
		delegate.initDefaults(root, parentDelegate);
		return delegate;
	}

	/**
	 * Parse the elements at the root level in the document:
	 * "import", "alias", "bean".
	 * @param root the DOM root element of the document
	 */
	// 使用 Spring 的 Bean 规则从 Document 的根元素开始进行包含 Bean 定义的 Document 对象解析
	protected void parseBeanDefinitions(Element root, BeanDefinitionParserDelegate delegate) {
		// 		包含 Bean 定义的 Document 对象使用了 Spring 默认的 XML 命名空间

		// 		Spring 中存在两种标签，包括：
		// 						（1）默认标签，
		// 						（2）自定义标签。

		//	 	对于 根节点 或者 子节点 如果是 默认命名空间 的话采用 parseDefaultElement 方法进行解析，否则则采用
		//	 delegate.parseCustomElement 方法对 自定义命名空间 进行解析。
		//	 	而判断是否 默认命名空间 还是自定义命名空间的办法其实是使用 node.getNameSpaceURI() 获取命名空间，并与 Spring 中固定命名空间
		//	http://wwww.Springframework.org/schema/beans 进行比较，如果一致则认为是默认，否则认为是自定义。
		if (delegate.isDefaultNamespace(root)) {
			// 获取包含 Bean 定义的 Document 对象根元素的所有子节点
			NodeList nl = root.getChildNodes();
			for (int i = 0; i < nl.getLength(); i++) {
				Node node = nl.item(i);
				//获得 Document 节点是 XML 元素节点
				if (node instanceof Element) {
					Element ele = (Element) node;
					//包含 Bean 定义的 Document 的元素节点使用的是 Spring 默认的XML命名空间
					if (delegate.isDefaultNamespace(ele)) {
						// 使用 Spring 的 Bean 规则解析元素节点
						//eg: <bean id="test" class="test.TestBean"/>
						parseDefaultElement(ele, delegate);
					}
					else {
						// 	没有使用 Spring 默认的 XML 命名空间，则使用用户自定义的解析规则解析元素节点
						//eg:<tx: annotation-driven/>
						delegate.parseCustomElement(ele);
					}
				}
			}
		}
		else {
			// Document 的根节点没有使用 Spring 默认的命名空间，则使用用户自定义的解析规则解析 Document 根节点
			delegate.parseCustomElement(root);
		}
	}


	// 使用 Spring 的 Bean规则解析 Document 元素节点
	private void parseDefaultElement(Element ele, BeanDefinitionParserDelegate delegate) {
		//	如果元素节点是 <import> 导入元素，进行导入解析
		if (delegate.nodeNameEquals(ele, IMPORT_ELEMENT)) {
			importBeanDefinitionResource(ele);
		}
		//	如果元素节点是 <Alias> 别名元素，进行别名解析
		else if (delegate.nodeNameEquals(ele, ALIAS_ELEMENT)) {
			processAliasRegistration(ele);
		}
		//		元素节点既不是导入元素，也不是别名元素，即普通的 <Bean> 元素，
		//	按照 Spring 的 Bean 规则解析元素
		else if (delegate.nodeNameEquals(ele, BEAN_ELEMENT)) {
			processBeanDefinition(ele, delegate);
		}
		//对 <beans> 标签处理
		else if (delegate.nodeNameEquals(ele, NESTED_BEANS_ELEMENT)) {
			// recurse
			doRegisterBeanDefinitions(ele);
		}
	}

	/**
	 * Parse an "import" element and load the bean definitions
	 * from the given resource into the bean factory.
	 */
	//	解析 <import> 导入元素，从给定的导入路径加载 Bean 定义资源到 Spring IoC 容器中
	//		<import resource="customerContext.xml"/>
	//		<import resource="systemContext.xml"/>
	// （1）获取 resource 属性所表示的路径。
	// （2）解析路径中的系统属性，格式如 "${user.dir}"
	// （3）判断 location 是绝对路径还是相对路径
	// （4）如果是绝对路径则 递归调用bean 的解析过程，进行另一次的解析。
	//	(5) 如果是 相对路径则计算出绝对路径 并进行解析。
	// （6）通知监听器，解析完成。
	protected void importBeanDefinitionResource(Element ele) {
		/// 获取 resource 属性 所表示路径。
		String location = ele.getAttribute(RESOURCE_ATTRIBUTE);
		/// 如果不存在 resource 属性则，则不做任何处理
		if (!StringUtils.hasText(location)) {
			getReaderContext().error("Resource location must not be empty", ele);
			return;
		}

		// Resolve system properties: e.g. "${user.dir}"
		/// 解析系统属性，格式如："${user.dir}"
		location = getReaderContext().getEnvironment().resolveRequiredPlaceholders(location);

		Set<Resource> actualResources = new LinkedHashSet<>(4);

		// Discover whether the location is an absolute or relative URI
		//	标识给定的导入元素的 location 是否是绝对路径
		/// 判断location是 绝定URI 还是  相对URI
		boolean absoluteLocation = false;
		try {
			absoluteLocation = ResourcePatternUtils.isUrl(location) || ResourceUtils.toURI(location).isAbsolute();
		}
		catch (URISyntaxException ex) {
			// cannot convert to an URI, considering the location relative
			// unless it is the well-known Spring prefix "classpath*:"
			// 给定的导入元素的 location 不是绝对路径
		}

		// Absolute or relative?
		//		给定的导入元素的 location 是绝对路径
		///	如果是 绝对URI 则直接根据地址加载对应的配置文件
		if (absoluteLocation) {
			try {
				// 使用 资源读入器 加载给定路径的 Bean定义资源
				// getReaderContext().getReader()-> 获取 XmlBeanDefinitionReader ， XmlBeanDefinitionReader 被暂存 XmlReaderContext中。
				int importCount = getReaderContext().getReader().loadBeanDefinitions(location, actualResources); ///??
				if (logger.isDebugEnabled()) {
					logger.debug("Imported " + importCount + " bean definitions from URL location [" + location + "]");
				}
			}
			catch (BeanDefinitionStoreException ex) {
				getReaderContext().error(
						"Failed to import bean definitions from URL location [" + location + "]", ele, ex);
			}
		}
		else {
			// No URL -> considering resource location as relative to the current file.
			//		给定的导入元素的 location 是相对路径
			///	如果是相对地址则根据相对地址计算出绝对地址
			try {
				int importCount;
				// 		将给定导入元素的 location 封装为相对路径资源
				///	Resource 存在多个子元素实现类，如  VfsResource、FileSystemResource  等，
				///	而每个 Resource 的 createRelative  方式实现都不一样，所以这里先使用子类的方法尝试解析
				Resource relativeResource = getReaderContext().getResource().createRelative(location);
				//	封装的相对路径资源存在
				if (relativeResource.exists()) {
					//	使用资源读入器加载Bean定义资源
					// getReaderContext().getReader()-> 获取 XmlBeanDefinitionReader ， XmlBeanDefinitionReader 被暂存 XmlReaderContext中。
					importCount = getReaderContext().getReader().loadBeanDefinitions(relativeResource);
					actualResources.add(relativeResource);
				}
				// 封装的相对路径资源不存在
				else {
					// 获取 Spring IOC 容器 资源读入器的基本路径
					String baseLocation = getReaderContext().getResource().getURL().toString();
					// 根据 Spring IOC 容器 资源读入器的基本路径加载给定导入路径的资源
					importCount = getReaderContext().getReader().loadBeanDefinitions(
							StringUtils.applyRelativePath(baseLocation, location), actualResources);
				}
				if (logger.isDebugEnabled()) {
					logger.debug("Imported " + importCount + " bean definitions from relative location [" + location + "]");
				}
			}
			catch (IOException ex) {
				getReaderContext().error("Failed to resolve current resource location", ele, ex);
			}
			catch (BeanDefinitionStoreException ex) {
				getReaderContext().error("Failed to import bean definitions from relative location [" + location + "]",
						ele, ex);
			}
		}
		Resource[] actResArray = actualResources.toArray(new Resource[actualResources.size()]);
		//  在解析完  <import> 元素之后，发送容器导入其他资源处理完成事件
		/// 解析后进行监听器激活处理
		getReaderContext().fireImportProcessed(location, actResArray, extractSource(ele));
	}

	/**
	 * Process the given alias element, registering the alias with the registry.
	 */
	//解析 <alias> 别名元素，为 Bean 向  Spring IoC 容器注册别名
	// <alias name="componentA" alias="componentB"/>
	// <alias name="componentA" alias="myApp"/>
	protected void processAliasRegistration(Element ele) {
		//  获取 <alias> 别名元素中 name 的属性值
		/// 获取 beanName
		String name = ele.getAttribute(NAME_ATTRIBUTE);
		// 获取 <alias> 别名元素中 alias 的属性值
		/// 获取 alias
		String alias = ele.getAttribute(ALIAS_ATTRIBUTE);
		boolean valid = true;
		// <alias> 别名元素的 name 属性值为空
		if (!StringUtils.hasText(name)) {
			getReaderContext().error("Name must not be empty", ele);
			valid = false;
		}
		//  <alias> 别名元素的 alias 属性值为空
		if (!StringUtils.hasText(alias)) {
			getReaderContext().error("Alias must not be empty", ele);
			valid = false;
		}
		if (valid) {
			try {
				// 向容器的资源读入器注册别名，逻辑实现在类 SimpleAliasRegistry 中
				getReaderContext().getRegistry().registerAlias(name, alias);
			}
			catch (Exception ex) {
				getReaderContext().error("Failed to register alias '" + alias +
						"' for bean with name '" + name + "'", ele, ex);
			}
			// 在解析完<alias>元素之后，发送容器别名处理完成事件
			getReaderContext().fireAliasRegistered(name, alias, extractSource(ele));
		}
	}

	/**
	 * Process the given bean element, parsing the bean definition
	 * and registering it with the registry.
	 */
	//	解析包含	Bean 定义资源 Document 对象的普通元素
	//  (1) 	首先委托 BeanDefinitionDelegate类 的p arseBeanDefinitionElement方法 进行解析，返回 BeanDefinitionHolder 类型的实例 bdHolder,
	//    	经过这个方法后，bdHolder实例 已经包含我们 配置文件中配置的各种属性了，例如 class、name、id、alias 之类的属性。
	//	(2) 	当返回 bdHolder不为空 的情况下若存在默认标签的子节点下再有 自定义属性 ，还需要再次对自定义标签进行解析。
	//  (3) 	解析完成后，需要对 解析后的bdHolder 进行注册，同样，注册操作委托给了 BeanDefinitionReaderUtils 的 registerBeanDefinition 方法。
	//  (4) 	最后发出响应事件，通知相关的监听器，这个 bean 已经加载完成了
	protected void processBeanDefinition(Element ele, BeanDefinitionParserDelegate delegate) {
		// 委托 BeanDefinitionDelegate类 的 parseBeanDefinitionElement方法 进行解析，返回 BeanDefinitionHolder 类型的实例 bdHolder。
		BeanDefinitionHolder bdHolder = delegate.parseBeanDefinitionElement(ele);
		//  BeanDefinitionHolder 是对 BeanDefinition的封装，即 Bean定义 的封装类，对 Document对象 中 <Bean>元素 的解析由 BeanDefinitionParserDelegate 实现
		//  BeanDefinitionHolder bdHolder = delegate.parseBeanDefinitionElement(ele);
		if (bdHolder != null) {
			// 如果需要的话就对 BeanDefinition 进行修饰
			//	<bean id="test" class="test.MyClass">
			// 		<mybean:user username="aaa"/> //这里 自定义类型 不是以 Bean 的形式出现，这个自定义类型其实是 属性
			//	</bean>
			bdHolder = delegate.decorateBeanDefinitionIfRequired(ele, bdHolder);
			try {
				// Register the final decorated instance.
				// 向 Spring IOC容器 注册解析得到的 Bean定义，这是 Bean定义 向 IOC容器注册 的入口
				BeanDefinitionReaderUtils.registerBeanDefinition(bdHolder, getReaderContext().getRegistry());
			}
			catch (BeanDefinitionStoreException ex) {
				getReaderContext().error("Failed to register bean definition with name '" +
						bdHolder.getBeanName() + "'", ele, ex);
			}
			// Send registration event.
			//		在完成向Spring IOC容器注册解析得到的Bean定义之后，发送注册事件
			/// 这里的实现（EmptyReaderEventListener）只为扩展，
			/// 当程序开发人员需要 对注册BeanDefinition事件 进行监听时可以通过注册监听器的方式
			/// 并将处理逻辑写入监听器中，目前 在Spring中 并没有对此事件做任何逻辑处理。
			getReaderContext().fireComponentRegistered(new BeanComponentDefinition(bdHolder));
		}
	}


	/**
	 * Allow the XML to be extensible by processing any custom element types first,
	 * before we start to process the bean definitions. This method is a natural
	 * extension point for any other custom pre-processing of the XML.
	 * <p>The default implementation is empty. Subclasses can override this method to
	 * convert custom elements into standard Spring bean definitions, for example.
	 * Implementors have access to the parser's bean definition reader and the
	 * underlying XML resource, through the corresponding accessors.
	 * @see #getReaderContext()
	 */
	protected void preProcessXml(Element root) {
	}

	/**
	 * Allow the XML to be extensible by processing any custom element types last,
	 * after we finished processing the bean definitions. This method is a natural
	 * extension point for any other custom post-processing of the XML.
	 * <p>The default implementation is empty. Subclasses can override this method to
	 * convert custom elements into standard Spring bean definitions, for example.
	 * Implementors have access to the parser's bean definition reader and the
	 * underlying XML resource, through the corresponding accessors.
	 * @see #getReaderContext()
	 */
	protected void postProcessXml(Element root) {
	}

}
