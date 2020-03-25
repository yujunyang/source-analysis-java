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

package org.springframework.beans.factory.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a constructor, field, setter method or config method as to be autowired
 * by Spring's dependency injection facilities.
 *
 * <p>Only one constructor (at max) of any given bean class may carry this annotation,
 * indicating the constructor to autowire when used as a Spring bean. Such a
 * constructor does not have to be public.
 *
 * <p>Fields are injected right after construction of a bean, before any config
 * methods are invoked. Such a config field does not have to be public.
 *
 * <p>Config methods may have an arbitrary name and any number of arguments; each of
 * those arguments will be autowired with a matching bean in the Spring container.
 * Bean property setter methods are effectively just a special case of such a general
 * config method. Such config methods do not have to be public.
 *
 * <p>In the case of a multi-arg constructor or method, the 'required' parameter is
 * applicable to all arguments. Individual parameters may be declared as Java-8-style
 * {@link java.util.Optional} or, as of Spring Framework 5.0, also as {@code @Nullable}
 * or a not-null parameter type in Kotlin, overriding the base required semantics.
 *
 * <p>In case of a {@link java.util.Collection} or {@link java.util.Map} dependency type,
 * the container autowires all beans matching the declared value type. For such purposes,
 * the map keys must be declared as type String which will be resolved to the corresponding
 * bean names. Such a container-provided collection will be ordered, taking into account
 * {@link org.springframework.core.Ordered}/{@link org.springframework.core.annotation.Order}
 * values of the target components, otherwise following their registration order in the
 * container. Alternatively, a single matching target bean may also be a generally typed
 * {@code Collection} or {@code Map} itself, getting injected as such.
 *
 * <p>Note that actual injection is performed through a
 * {@link org.springframework.beans.factory.config.BeanPostProcessor
 * BeanPostProcessor} which in turn means that you <em>cannot</em>
 * use {@code @Autowired} to inject references into
 * {@link org.springframework.beans.factory.config.BeanPostProcessor
 * BeanPostProcessor} or
 * {@link org.springframework.beans.factory.config.BeanFactoryPostProcessor BeanFactoryPostProcessor}
 * types. Please consult the javadoc for the {@link AutowiredAnnotationBeanPostProcessor}
 * class (which, by default, checks for the presence of this annotation).
 *
 * @author Juergen Hoeller
 * @author Mark Fisher
 * @since 2.5
 * @see AutowiredAnnotationBeanPostProcessor
 * @see Qualifier
 * @see Value
 */

//一、@Autowired
//	1、@Autowired是spring自带的注解，通过后置处理器‘AutowiredAnnotationBeanPostProcessor’ 类实现的依赖注入；
//	2、@Autowired是根据类型进行自动装配的，如果需要按名称进行装配，则需要配合@Qualifier，同时可结合@Primary注解；
//	3、@Autowired可以作用在变量、setter方法、构造函数以及参数列表上。
//	4、@Autowired有个属性为required默认为true，可以配置为false，如果配置为false之后，当没有找到相应bean的时候，系统不会抛错；
//
//二、@Inject
//	1、@Inject是JSR330 (Dependency Injection for Java)中的规范，需要导入javax.inject.Inject;实现注入。
//	2、@Inject是根据类型进行自动装配的，如果需要按名称进行装配，则需要配合@Named；
//	3、@Inject可以作用在变量、setter方法、构造函数上。
//	4、@Inject没有找到相应bean的时候，系统会抛错；
//
//三、@Resource
//	1、@Resource是JSR250规范的实现，需要导入javax.annotation实现注入。
//	2、@Resource是根据名称进行自动装配的，一般会指定一个name属性
//	3、@Resource可以作用在类、变量、setter方法上。
//	4、@Resource没有找到相应bean的时候，系统会抛错
// 它可以对类成员变量、方法、构造函数及方法参数 进行注解，完成自动装配的工作。
@Target({ElementType.CONSTRUCTOR, ElementType.METHOD, ElementType.PARAMETER, ElementType.FIELD, ElementType.ANNOTATION_TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Autowired {

	/**
	 * Declares whether the annotated dependency is required.
	 * <p>Defaults to {@code true}.
	 */
	boolean required() default true;

}
