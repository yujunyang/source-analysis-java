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

package org.springframework.web.servlet.mvc;

import javax.servlet.http.HttpServletRequest;

/**
 * Supports last-modified HTTP requests to facilitate content caching.
 * Same contract as for the Servlet API's {@code getLastModified} method.
 *
 * <p>Delegated to by a {@link org.springframework.web.servlet.HandlerAdapter#getLastModified}
 * implementation. By default, any Controller or HttpRequestHandler within Spring's
 * default framework can implement this interface to enable last-modified checking.
 *
 * <p><b>Note:</b> Alternative handler implementation approaches have different
 * last-modified handling styles. For example, Spring 2.5's annotated controller
 * approach (using {@code @RequestMapping}) provides last-modified support
 * through the {@link org.springframework.web.context.request.WebRequest#checkNotModified}
 * method, allowing for last-modified checking within the main handler method.
 *
 * @author Rod Johnson
 * @author Juergen Hoeller
 * @see javax.servlet.http.HttpServlet#getLastModified
 * @see Controller
 * @see SimpleControllerHandlerAdapter
 * @see org.springframework.web.HttpRequestHandler
 * @see HttpRequestHandlerAdapter
 */

//			在研究	Spring	对缓存处理的功能支持前，我们先了解一个概念：Last-Modihed缓存机制。
//			(1)	在客户端第一次输入URL时，服务器端会返回内容和状态码200,表示请求成功，同时会添加一个"Last-Modified"的响应头，
//		表示此文件在服务器上的最后更新时间，例如，"Last-Modified:Wed, 14 Mar 2012 10:22:42 GMT" 表示最后更新时间为(2012-03-14 10:22)。
//			(2)	客户端第二次请求此URL时，客户端会向服务器发送请求头"If-Modified-Since",询
//		间服务器该时间之后当前请求内容是否有被修改过，如"if-Modified-Since: Wed, 14 Mar 2012 10:22:42 GMT"，如果服务器端的内容没有变化，
//		则自动返回HTTP 304状态码(只要响应头，内容为空，这样就节省了网络带宽)。
//			Spring提供的对Last-Modified机制的支持，只需要实现LastModi五ed接口，如下所示:

//		public class HelloWorldLastModifledCacheController extends Abstractcontroller implements LastModifled {
//			private long lastModified;
//
//			protected ModelAndView handleRequestinternal(HttpServletRequest req, HttpServletResponse resp) throws Exception {
//				// 点击后再次请求当前页面
//				resp.getWriter().write(N < a href« * •>this </a >, 1);
//				return null;
//			}
//
//			public long getLastModified(HttpServletRequest request) {
//				if (lastModified == OL) {
//					//第一次或者逻辑有变化的时候，应该重新返回内容最新修改的时间*
//					lastModified = System.currentTimeMillis();
//				}
//				return lastModified;
//
//			}
//		}
//		HelloWorldLastModifiedCacheControlIer 只需要实现 LastModified 接口的 getLastModified 方 法，保证当内容发生改变时返回最新的修改时间即可。
//	Spring判断是否过期，通过判断请求的44ISModified-Siiicew是否大于等于当前的getLastModified方法的时间戳，如果是，则认为没有修改。上面的controller与普通的controller并无
//	太大差别，声明如下：
//		<bean name="/helloLastModifiedM class-"com.test.controller.HelloWorldLastModifiedCache
//				Controller"/>
public interface LastModified {

	/**
	 * Same contract as for HttpServlet's {@code getLastModified} method.
	 * Invoked <b>before</b> request processing.
	 * <p>The return value will be sent to the HTTP client as Last-Modified header,
	 * and compared with If-Modified-Since headers that the client sends back.
	 * The content will only get regenerated if there has been a modification.
	 * @param request current HTTP request
	 * @return the time the underlying resource was last modified, or -1
	 * meaning that the content must always be regenerated
	 * @see org.springframework.web.servlet.HandlerAdapter#getLastModified
	 * @see javax.servlet.http.HttpServlet#getLastModified
	 */
	long getLastModified(HttpServletRequest request);

}
