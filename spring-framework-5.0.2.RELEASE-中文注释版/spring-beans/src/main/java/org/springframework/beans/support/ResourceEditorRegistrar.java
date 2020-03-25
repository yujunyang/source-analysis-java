/*
 * Copyright 2002-2016 the original author or authors.
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

package org.springframework.beans.support;

import java.beans.PropertyEditor;
import java.io.File;
import java.io.InputStream;
import java.io.Reader;
import java.net.URI;
import java.net.URL;
import java.nio.file.Path;

import org.xml.sax.InputSource;

import org.springframework.beans.PropertyEditorRegistrar;
import org.springframework.beans.PropertyEditorRegistry;
import org.springframework.beans.PropertyEditorRegistrySupport;
import org.springframework.beans.propertyeditors.ClassArrayEditor;
import org.springframework.beans.propertyeditors.ClassEditor;
import org.springframework.beans.propertyeditors.FileEditor;
import org.springframework.beans.propertyeditors.InputSourceEditor;
import org.springframework.beans.propertyeditors.InputStreamEditor;
import org.springframework.beans.propertyeditors.PathEditor;
import org.springframework.beans.propertyeditors.ReaderEditor;
import org.springframework.beans.propertyeditors.URIEditor;
import org.springframework.beans.propertyeditors.URLEditor;
import org.springframework.core.env.PropertyResolver;
import org.springframework.core.io.ContextResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceEditor;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.io.support.ResourceArrayPropertyEditor;
import org.springframework.core.io.support.ResourcePatternResolver;

/**
 * PropertyEditorRegistrar implementation that populates a given
 * {@link org.springframework.beans.PropertyEditorRegistry}
 * (typically a {@link org.springframework.beans.BeanWrapper} used for bean
 * creation within an {@link org.springframework.context.ApplicationContext})
 * with resource editors. Used by
 * {@link org.springframework.context.support.AbstractApplicationContext}.
 *
 * @author Juergen Hoeller
 * @author Chris Beams
 * @since 2.0
 */
public class ResourceEditorRegistrar implements PropertyEditorRegistrar {

	private final PropertyResolver propertyResolver;

	private final ResourceLoader resourceLoader;


	/**
	 * Create a new ResourceEditorRegistrar for the given {@link ResourceLoader}
	 * and {@link PropertyResolver}.
	 * @param resourceLoader the ResourceLoader (or ResourcePatternResolver)
	 * to create editors for (usually an ApplicationContext)
	 * @param propertyResolver the PropertyResolver (usually an Environment)
	 * @see org.springframework.core.env.Environment
	 * @see org.springframework.core.io.support.ResourcePatternResolver
	 * @see org.springframework.context.ApplicationContext
	 */
	public ResourceEditorRegistrar(ResourceLoader resourceLoader, PropertyResolver propertyResolver) {
		this.resourceLoader = resourceLoader;
		this.propertyResolver = propertyResolver;
	}


	/**
	 * Populate the given {@code registry} with the following resource editors:
	 * ResourceEditor, InputStreamEditor, InputSourceEditor, FileEditor, URLEditor,
	 * URIEditor, ClassEditor, ClassArrayEditor.
	 * <p>If this registrar has been configured with a {@link ResourcePatternResolver},
	 * a ResourceArrayPropertyEditor will be registered as well.
	 * @see org.springframework.core.io.ResourceEditor
	 * @see org.springframework.beans.propertyeditors.InputStreamEditor
	 * @see org.springframework.beans.propertyeditors.InputSourceEditor
	 * @see org.springframework.beans.propertyeditors.FileEditor
	 * @see org.springframework.beans.propertyeditors.URLEditor
	 * @see org.springframework.beans.propertyeditors.URIEditor
	 * @see org.springframework.beans.propertyeditors.ClassEditor
	 * @see org.springframework.beans.propertyeditors.ClassArrayEditor
	 * @see org.springframework.core.io.support.ResourceArrayPropertyEditor
	 */
	///	spring的扩展属性编辑器即类型转换的源码理解: https://blog.csdn.net/cuichunchi/article/details/90407632

	///	 	虽说 ResourceEditorRegistrar 类的 registerCustomEditors 方法实现了批量注册的功能，
	///	但是 beanFactory.addPropertyEditorRegistrar(new ResourceEditorRegistrar(this,getEnvironment())) 仅仅注册了 ResourceEditorRegistrar 实例,
	///	却没有调用 ResourceEdiorRegistrar 的 registerCustomEditors 方法进行注册，那么到底是在什么时候进行注册的呢？进一步查看 ResourceEditorRegistrar 的
	// 	regsiterCustomEditors 方法的调用层次结构。

	/// copyRegisteredEditorsTo(ProperEditorRegistry)<AbstractBeanFactory>
	/// 			-> registerCustomEditors(PropertyEditorRegistry)<AbstractBeanFactory>
	///							->  registerCustomEditors(PropertyEditorRegistry)<ResourceEditorRegistrar>

	/// getTypeConverter()<AbstractBeanFactory>
	/// 			-> registerCustomEditors(PropertyEditorRegistry)<AbstractBeanFactory>
	///							->  registerCustomEditors(PropertyEditorRegistry)<ResourceEditorRegistrar>

	/// initBeanWrapper(BeanWrapper)<AbstractBeanFactory>
	/// 			-> registerCustomEditors(PropertyEditorRegistry)<AbstractBeanFactory>
	///							->  registerCustomEditors(PropertyEditorRegistry)<ResourceEditorRegistrar>

	///		其中我们看到一个方法是我们熟悉的，就是 AbstractBeanFactory 类中的 initBeanWrapper 方法，这是在 bean 初始化时使用的一个方法，主要是在将
	/// BeanDefinition 转换为 BeanWrapper 后用于对属性的填充。
	/// 	到此，逻辑已经明了，在 bean 的初始化后会调用 ResourceEditorRegistrar 的 registerCustomEditors 方法进行批量的通用属性编辑器注册。
	///	注册后，在属性填充的环节便可以直接让 Spring 使用这些编辑器进行属性的解析了。

	////	既然提到了 BeanWrapper，这里也有必要强调下，Spring 中用于封装 bean 的是 BeanWrapper 类型，而它又间接继承了 PropertyEditorRegistry 类型，也就是
	///	我们之前反复看到的方法参数 PropertyEditorRegistry registry，其实大部分情况下都是 BeanWrapper，对于 BeanWrapper 在 Spring 中的默认实现是 BeanWrapperImpl，
	///	而 BeanWrapperImp 除了实现 BeanWapper 接口外还继承了 PropertyEditorRegistrySupport，在PropertyEditorRegistrySupport 中有这样一个方法：createDefualtEditors()

	///	如果我们定义的bean中的某个属性的类型不在上面的常用配置中的话，才需要我们进行个性化属性编辑器的额注册。
	@Override
	public void registerCustomEditors(PropertyEditorRegistry registry) {
		ResourceEditor baseEditor = new ResourceEditor(this.resourceLoader, this.propertyResolver);
		doRegisterEditor(registry, Resource.class, baseEditor);
		doRegisterEditor(registry, ContextResource.class, baseEditor);
		doRegisterEditor(registry, InputStream.class, new InputStreamEditor(baseEditor));
		doRegisterEditor(registry, InputSource.class, new InputSourceEditor(baseEditor));
		doRegisterEditor(registry, File.class, new FileEditor(baseEditor));
		doRegisterEditor(registry, Path.class, new PathEditor(baseEditor));
		doRegisterEditor(registry, Reader.class, new ReaderEditor(baseEditor));
		doRegisterEditor(registry, URL.class, new URLEditor(baseEditor));

		ClassLoader classLoader = this.resourceLoader.getClassLoader();
		doRegisterEditor(registry, URI.class, new URIEditor(classLoader));
		doRegisterEditor(registry, Class.class, new ClassEditor(classLoader));
		doRegisterEditor(registry, Class[].class, new ClassArrayEditor(classLoader));

		if (this.resourceLoader instanceof ResourcePatternResolver) {
			doRegisterEditor(registry, Resource[].class,
					new ResourceArrayPropertyEditor((ResourcePatternResolver) this.resourceLoader, this.propertyResolver));
		}
	}

	/**
	 * Override default editor, if possible (since that's what we really mean to do here);
	 * otherwise register as a custom editor.
	 */
	///			ResourceEditorRegistrar 类的 registerCustomEditors 方法的核心功能，其实无非是注册了一系列的常用类型的属性编译器，例如，
	///		代码 doRegisterEditor(registry,Class.class,new ClassEditor(classLoader)) 实现的功能就是注册 Class 类对应的属性编译器。
	///		那么，注册后，一旦某个实体 bean 中存在一些 Class 类型的属性，那么 Spring 会调用 ClassEditor 将配置中定义的 String 类型转换为 Class 类型并进行赋值。
	private void doRegisterEditor(PropertyEditorRegistry registry, Class<?> requiredType, PropertyEditor editor) {
		if (registry instanceof PropertyEditorRegistrySupport) {
			((PropertyEditorRegistrySupport) registry).overrideDefaultEditor(requiredType, editor);
		}
		else {
			registry.registerCustomEditor(requiredType, editor);
		}
	}

}
