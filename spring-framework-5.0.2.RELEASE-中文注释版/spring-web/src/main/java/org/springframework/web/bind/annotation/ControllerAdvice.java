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

package org.springframework.web.bind.annotation;

import java.beans.PropertyEditorSupport;
import java.lang.annotation.Annotation;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.Date;

import org.springframework.core.annotation.AliasFor;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.WebDataBinder;

/**
 * Specialization of {@link Component @Component} for classes that declare
 * {@link ExceptionHandler @ExceptionHandler}, {@link InitBinder @InitBinder}, or
 * {@link ModelAttribute @ModelAttribute} methods to be shared across
 * multiple {@code @Controller} classes.
 *
 * <p>Classes with {@code @ControllerAdvice} can be declared explicitly as Spring
 * beans or auto-detected via classpath scanning. All such beans are sorted via
 * {@link org.springframework.core.annotation.AnnotationAwareOrderComparator
 * AnnotationAwareOrderComparator}, i.e. based on
 * {@link org.springframework.core.annotation.Order @Order} and
 * {@link org.springframework.core.Ordered Ordered}, and applied in that order
 * at runtime. For handling exceptions, an {@code @ExceptionHandler} will be
 * picked on the first advice with a matching exception handler method. For
 * model attributes and {@code InitBinder} initialization, {@code @ModelAttribute}
 * and {@code @InitBinder} methods will also follow {@code @ControllerAdvice} order.
 *
 * <p>Note: For {@code @ExceptionHandler} methods, a root exception match will be
 * preferred to just matching a cause of the current exception, among the handler
 * methods of a particular advice bean. However, a cause match on a higher-priority
 * advice will still be preferred to a any match (whether root or cause level)
 * on a lower-priority advice bean. As a consequence, please declare your primary
 * root exception mappings on a prioritized advice bean with a corresponding order!
 *
 * <p>By default the methods in an {@code @ControllerAdvice} apply globally to
 * all Controllers. Use selectors {@link #annotations()},
 * {@link #basePackageClasses()}, and {@link #basePackages()} (or its alias
 * {@link #value()}) to define a more narrow subset of targeted Controllers.
 * If multiple selectors are declared, OR logic is applied, meaning selected
 * Controllers should match at least one selector. Note that selector checks
 * are performed at runtime and so adding many selectors may negatively impact
 * performance and add complexity.
 *
 * @author Rossen Stoyanchev
 * @author Brian Clozel
 * @author Sam Brannen
 * @since 3.2
 */
// @ControllerAdvice 顾名思义，这是一个增强的 Controller，
// 主要实现三个方面的功能：

//		1.全局异常处理；
//			@ControllerAdvice
//			public class MyGlobalExceptionHandler {
//				@ResponseBody
//				@ExceptionHandler(Exception.class)
//				@ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR) //自定义浏览器返回状态码
//				public ModelAndView customException(Exception e) {
//					ModelAndView mv = new ModelAndView();
//					mv.addObject("message", e.getMessage());
//					mv.setViewName("myerror");
//					return mv;
//				}
//			}
//			在该类中，可以定义多个方法，不同的方法处理不同的异常，例如专门处理空指针的方法、专门处理数组越界的方法...，
//		也可以直接向上面代码一样，在一个方法中处理所有的异常信息。
//			@ExceptionHandler 注解用来指明异常的处理类型，即如果这里指定为 NullpointerException，
//		则数组越界异常就不会进到这个方法中来。

//		2.全局数据绑定；
//			全局数据绑定功能可以用来做一些初始化的数据操作，我们可以将一些公共的数据定义在添加了 @ControllerAdvice 注解的类中，
//		 这样，在每一个 Controller 的接口中，就都能够访问导致这些数据
//			@ControllerAdvice
//			public class MyGlobalExceptionHandler {
//				@ModelAttribute(name = "md")
//				public Map<String,Object> mydata() {
//					HashMap<String, Object> map = new HashMap<>();
//					map.put("age", 99);
//					map.put("gender", "男");
//					return map;
//				}
//			}
//			使用 @ModelAttribute 注解标记该方法的返回数据是一个全局数据，默认情况下，这个全局数据的 key 就是返回的变量名，
//		value 就是方法返回值，当然开发者可以通过 @ModelAttribute 注解的 name 属性去重新指定 key。定义完成后，
//		在任何一个Controller 的接口中，都可以获取到这里定义的数据

//		3.全局数据预处理；
//		例子一：
//			考虑我有两个实体类，Book 和 Author，分别定义如下：
//				public class Book {
//					private String name;
//					private Long price;
//					//getter/setter
//				}
//				public class Author {
//					private String name;
//					private Integer age;
//					//getter/setter
//				}
//			此时，如果我定义一个数据添加接口，如下：
//				@PostMapping("/book")
//				public void addBook(Book book, Author author) {
//						System.out.println(book);
//						System.out.println(author);
//						}
//
//				这个时候，添加操作就会有问题，因为两个实体类都有一个 name 属性，从前端传递时 ，无法区分。
//			此时，通过 @ControllerAdvice 的全局数据预处理可以解决这个问题

//			解决步骤如下:
//			1.给接口中的变量取别名
//				@PostMapping("/book")
//				public void addBook(@ModelAttribute("b") Book book, @ModelAttribute("a") Author author) {
//						System.out.println(book);
//						System.out.println(author);
//				}
//			2.进行请求数据预处理
//			在 @ControllerAdvice 标记的类中添加如下代码:
//				@InitBinder("b")
//				public void b(WebDataBinder binder) {
//						binder.setFieldDefaultPrefix("b.");
//						}
//				@InitBinder("a")
//				public void a(WebDataBinder binder) {
//						binder.setFieldDefaultPrefix("a.");
//						}
//		@InitBinder("b") 注解表示该方法用来处理和Book和相关的参数,在方法中,给参数添加一个 b 前缀,即请求参数要有b前缀.
//
//		3.发送请求
//		请求发送时,通过给不同对象的参数添加不同的前缀,可以实现参数的区分.

//	   例子二：
//			将str数据类型转为Date类型
//			@ControllerAdvice("com.linkdoc.dtp.erp.controller")
//			public class ParamBindAdvice {
//
//				@InitBinder
//				protected void initBinder(WebDataBinder binder) {
//					binder.registerCustomEditor(Date.class, new PropertyEditorSupport() {
//						@Override
//						public void setAsText(String value) {
//							if (StringUtils.isBlank(value)) {
//								setValue(null);
//								return;
//							}
//							setValue(new Date(Long.valueOf(value)));
//						}
//					});
//				}
//			}

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Component
public @interface ControllerAdvice {

	/**
	 * Alias for the {@link #basePackages} attribute.
	 * <p>Allows for more concise annotation declarations e.g.:
	 * {@code @ControllerAdvice("org.my.pkg")} is equivalent to
	 * {@code @ControllerAdvice(basePackages="org.my.pkg")}.
	 * @since 4.0
	 * @see #basePackages()
	 */
	@AliasFor("basePackages")
	String[] value() default {};

	/**
	 * Array of base packages.
	 * <p>Controllers that belong to those base packages or sub-packages thereof
	 * will be included, e.g.: {@code @ControllerAdvice(basePackages="org.my.pkg")}
	 * or {@code @ControllerAdvice(basePackages={"org.my.pkg", "org.my.other.pkg"})}.
	 * <p>{@link #value} is an alias for this attribute, simply allowing for
	 * more concise use of the annotation.
	 * <p>Also consider using {@link #basePackageClasses()} as a type-safe
	 * alternative to String-based package names.
	 * @since 4.0
	 */
	@AliasFor("value")
	String[] basePackages() default {};

	/**
	 * Type-safe alternative to {@link #value()} for specifying the packages
	 * to select Controllers to be assisted by the {@code @ControllerAdvice}
	 * annotated class.
	 * <p>Consider creating a special no-op marker class or interface in each package
	 * that serves no purpose other than being referenced by this attribute.
	 * @since 4.0
	 */
	Class<?>[] basePackageClasses() default {};

	/**
	 * Array of classes.
	 * <p>Controllers that are assignable to at least one of the given types
	 * will be assisted by the {@code @ControllerAdvice} annotated class.
	 * @since 4.0
	 */
	Class<?>[] assignableTypes() default {};

	/**
	 * Array of annotations.
	 * <p>Controllers that are annotated with this/one of those annotation(s)
	 * will be assisted by the {@code @ControllerAdvice} annotated class.
	 * <p>Consider creating a special annotation or use a predefined one,
	 * like {@link RestController @RestController}.
	 * @since 4.0
	 */
	Class<? extends Annotation>[] annotations() default {};

}
