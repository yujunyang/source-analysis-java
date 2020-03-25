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

package org.springframework.web.bind.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.core.annotation.AliasFor;
import org.springframework.web.cors.CorsConfiguration;

/**
 * Marks the annotated method or type as permitting cross origin requests.
 *
 * <p>By default all origins and headers are permitted, credentials are not allowed,
 * and the maximum age is set to 1800 seconds (30 minutes). The list of HTTP
 * methods is set to the methods on the {@code @RequestMapping} if not
 * explicitly set on {@code @CrossOrigin}.
 *
 * <p><b>NOTE:</b> {@code @CrossOrigin} is processed if an appropriate
 * {@code HandlerMapping}-{@code HandlerAdapter} pair is configured such as the
 * {@code RequestMappingHandlerMapping}-{@code RequestMappingHandlerAdapter}
 * pair which are the default in the MVC Java config and the MVC namespace.
 *
 * @author Russell Allen
 * @author Sebastien Deleuze
 * @author Sam Brannen
 * @since 4.2
 */
// Cross：横渡，Origin：源
// @CrossOrigin是用来处理跨域请求的注解
// 1. 什么是跨域？（https://zhuanlan.zhihu.com/p/66789473）
//	跨域，指的是浏览器不能执行其他网站的脚本。它是由浏览器的同源策略造成的，是浏览器对JavaScript施加的安全限制。
//	所谓同源是指，域名，协议，端口均相同。
//  eg:
// 		http://www.123.com/index.html 调用 http://www.123.com/server.PHP （非跨域）
//		http://www.123.com/index.html 调用 http://www.456.com/server.php （主域名不同:123/456，跨域）
//		http://abc.123.com/index.html 调用 http://def.123.com/server.php（子域名不同:abc/def，跨域）
//		http://www.123.com:8080/index.html调用 http://www.123.com:8081/server.php（端口不同:8080/8081，跨域）
//		http://www.123.com/index.html 调用 https://www.123.com/server.php（协议不同:http/https，跨域）
//		请注意：localhost和127.0.0.1虽然都指向本机，但也属于跨域。
//		浏览器执行javascript脚本时，会检查这个脚本属于哪个页面，如果不是同源页面，就不会被执行。
//
// e.g :
//		1. 通过此方式注解则Controller中的所有通过@RequestMapping注解的方法都可以进行跨域请求。 代码如下：
		//		@CrossOrigin()
		//		@RequestMapping("/demoController")
		//		@Controller
		//		public class DemoController {
		//			@Autowired
		//			IDemoService demoService;
		//
		//			@RequestMapping(value = "/test", method = RequestMethod.POST)
		//			@ResponseBody
		//			public ResultModel test(HttpServletRequest request)
		//					throws Exception {
		//				return “right”;
		//			}
		//		}
// 		2. 通过此方式注解则只有此方法可以进行跨域请求。 代码如下：
//				@RequestMapping("/demoController")
//				@Controller
//				public class DemoController {
//					@Autowired
//					IDemoService demoService;
//
//					@CrossOrigin()
//					@RequestMapping(value = "/test", method = RequestMethod.POST)
//					@ResponseBody
//					public ResultModel test(HttpServletRequest request)
//							throws Exception {
//						return “right”;
//					}
//				}

@Target({ ElementType.METHOD, ElementType.TYPE })
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface CrossOrigin {

	/**
	 * @deprecated as of Spring 5.0, in favor of using {@link CorsConfiguration#applyPermitDefaultValues}
	 */
	@Deprecated
	String[] DEFAULT_ORIGINS = { "*" };

	/**
	 * @deprecated as of Spring 5.0, in favor of using {@link CorsConfiguration#applyPermitDefaultValues}
	 */
	@Deprecated
	String[] DEFAULT_ALLOWED_HEADERS = { "*" };

	/**
	 * @deprecated as of Spring 5.0, in favor of using {@link CorsConfiguration#applyPermitDefaultValues}
	 */
	@Deprecated
	boolean DEFAULT_ALLOW_CREDENTIALS = false;

	/**
	 * @deprecated as of Spring 5.0, in favor of using {@link CorsConfiguration#applyPermitDefaultValues}
	 */
	@Deprecated
	long DEFAULT_MAX_AGE = 1800;


	/**
	 * Alias for {@link #origins}.
	 */
	//允许来源域名的列表，例如 'www.jd.com'，
	// 匹配的域名是跨域预请求 Response 头中的 'Access-Control-Aloow_origin' 字段值。
	// 不设置确切值时默认支持所有域名跨域访问。
	@AliasFor("origins")
	String[] value() default {};

	/**
	 * List of allowed origins, e.g. {@code "http://domain1.com"}.
	 * <p>These values are placed in the {@code Access-Control-Allow-Origin}
	 * header of both the pre-flight response and the actual response.
	 * {@code "*"} means that all origins are allowed.
	 * <p>If undefined, all origins are allowed.
	 * @see #value
	 */
	@AliasFor("value")
	String[] origins() default {};

	/**
	 * List of request headers that can be used during the actual request.
	 * <p>This property controls the value of the pre-flight response's
	 * {@code Access-Control-Allow-Headers} header.
	 * {@code "*"}  means that all headers requested by the client are allowed.
	 * <p>If undefined, all requested headers are allowed.
	 */
	//跨域请求中允许的请求头中的字段类型，
	// 该值对应跨域预请求 Response 头中的 'Access-Control-Allow-Headers' 字段值。
	// 不设置确切值默认支持所有的header字段
	// （Cache-Controller、Content-Language、Content-Type、Expires、Last-Modified、Pragma）跨域访问
	String[] allowedHeaders() default {};

	/**
	 * List of response headers that the user-agent will allow the client to access.
	 * <p>This property controls the value of actual response's
	 * {@code Access-Control-Expose-Headers} header.
	 * <p>If undefined, an empty exposed header list is used.
	 */
	// 跨域请求请求头中允许携带的除
	// Cache-Controller、Content-Language、Content-Type、Expires、Last-Modified、Pragma
	// 这六个基本字段之外的其他字段信息，对应的是跨域请求 Response 头中的 'Access-control-Expose-Headers'字段值。
	String[] exposedHeaders() default {};

	/**
	 * List of supported HTTP request methods, e.g.
	 * {@code "{RequestMethod.GET, RequestMethod.POST}"}.
	 * <p>Methods specified here override those specified via {@code RequestMapping}.
	 * <p>If undefined, methods defined by {@link RequestMapping} annotation
	 * are used.
	 */
	//跨域HTTP请求中支持的HTTP请求类型（GET、POST...），不指定确切值时默认与 Controller 方法中的 methods 字段保持一致
	RequestMethod[] methods() default {};

	/**
	 * Whether the browser should include any cookies associated with the
	 * domain of the request being annotated. Be aware that enabling this option could
	 * increase the surface attack of the web application (for example via exposing
	 * sensitive user-specific information like CSRF tokens).
	 * <p>Set to {@code "true"} means that the pre-flight response will include the header
	 * {@code Access-Control-Allow-Credentials=true} so such cookies should be included.
	 * <p>If undefined or set to {@code "false"}, such header is not included and
	 * credentials are not allowed.
	 */
	//该值对应的是是跨域请求 Response 头中的 'Access-Control-Allow-Credentials' 字段值。
	// 浏览器是否将本域名下的 cookie 信息携带至跨域服务器中。
	// 默认携带至跨域服务器中，但要实现 cookie 共享还需要前端在 AJAX 请求中打开 withCredentials 属性。
	String allowCredentials() default "";

	/**
	 * The maximum age (in seconds) of the cache duration for pre-flight responses.
	 * <p>This property controls the value of the {@code Access-Control-Max-Age}
	 * header in the pre-flight response.
	 * <p>Setting this to a reasonable value can reduce the number of pre-flight
	 * request/response interactions required by the browser.
	 * A negative value means <em>undefined</em>.
	 * <p>If undefined, max age is set to {@code 1800} seconds (i.e., 30 minutes).
	 */
	//该值对应的是是跨域请求 Response 头中的 'Access-Control-Max-Age' 字段值，
	// 表示预检请求响应的缓存持续的最大时间，目的是减少浏览器预检请求/响应交互的数量。
	// 默认值1800s。设置了该值后，浏览器将在设置值的时间段内对该跨域请求不再发起预请求。
	long maxAge() default -1;

}
