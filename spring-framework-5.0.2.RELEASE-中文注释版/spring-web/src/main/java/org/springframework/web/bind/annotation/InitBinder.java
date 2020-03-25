/*
 * Copyright 2002-2012 the original author or authors.
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

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation that identifies methods which initialize the
 * {@link org.springframework.web.bind.WebDataBinder} which
 * will be used for populating command and form object arguments
 * of annotated handler methods.
 *
 * <p>Such init-binder methods support all arguments that {@link RequestMapping}
 * supports, except for command/form objects and corresponding validation result
 * objects. Init-binder methods must not have a return value; they are usually
 * declared as {@code void}.
 *
 * <p>Typical arguments are {@link org.springframework.web.bind.WebDataBinder}
 * in combination with {@link org.springframework.web.context.request.WebRequest}
 * or {@link java.util.Locale}, allowing to register context-specific editors.
 *
 * @author Juergen Hoeller
 * @since 2.5
 * @see org.springframework.web.bind.WebDataBinder
 * @see org.springframework.web.context.request.WebRequest
 */
// 1. 注解的作用
//		从字面意思可以看出这个的作用是给Binder做初始化的，
//	被此注解的方法可以对WebDataBinder初始化。WebDataBinder是用于表单到方法的数据绑定的。
//  	@InitBinder只在@Controller与@ControllerAdvice中注解方法来为这个控制器注册一个绑定器初始化方法，方法只对本控制器有效。
//	2. 将str数据类型转为Date类型
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
@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface InitBinder {

	/**
	 * The names of command/form attributes and/or request parameters
	 * that this init-binder method is supposed to apply to.
	 * <p>Default is to apply to all command/form attributes and all request parameters
	 * processed by the annotated handler class. Specifying model attribute names or
	 * request parameter names here restricts the init-binder method to those specific
	 * attributes/parameters, with different init-binder methods typically applying to
	 * different groups of attributes or parameters.
	 */
	String[] value() default {};

}
