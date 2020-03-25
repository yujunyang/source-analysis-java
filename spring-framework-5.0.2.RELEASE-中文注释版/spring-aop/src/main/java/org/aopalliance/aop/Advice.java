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

package org.aopalliance.aop;

/**
 * Tag interface for Advice. Implementations can be any type
 * of advice, such as Interceptors.
 *
 * @author Rod Johnson
 * @version $Id: Advice.java,v 1.1 2004/03/19 17:02:16 johnsonr Exp $
 */
// 一、 AOP的基本概念
//		(1)Aspect(切面):通常是一个类，里面可以定义切入点和通知。
//		(2)JointPoint(连接点):程序执行过程中明确的点，一般是方法的调用。
//		(3)Pointcut(切入点):就是带有通知的连接点，在程序中主要体现为书写切入点表达式。
//		(4)Advice(通知):AOP在特定的切入点上执行的增强处理，
//		   有before,after,afterReturning,afterThrowing,around。
//	    (5)目标对象(Target)：代理的目标对象
//		(6)织入（weave）：将切面应用到目标对象并导致代理对象创建的过程。
//		(7)引入（introduction）：在不修改代码的前提下，引入可以在运行期为类动态地添加一些方法
//			或字段。
//		(8)AOP代理：AOP框架创建的对象，代理就是目标对象的加强。Spring中的AOP代理可以使JDK动态代理，也可以是CGLIB代理，
//		   前者基于接口，后者基于子类。

// 二、 Spring AOP
//		(1) Spring中的AOP代理还是离不开Spring的IOC容器，代理的生成，管理及其依赖关系都是由IOC容器负责，
//	Spring默认使用JDK动态代理，在需要代理类而不是代理接口的时候，Spring会自动切换为使用CGLIB代理，
//	不过现在的项目都是面向接口编程，所以JDK动态代理相对来说用的还是多一些。
//		(2) AOP 两种代理方式
//		Spring 提供了两种方式来生成代理对象: JDKProxy 和 Cglib，具体使用哪种方式生成由
//	AopProxyFactory 根据 AdvisedSupport 对象的配置来决定。默认的策略是如果目标类是接口，
//	则使用 JDK 动态代理技术，否则使用 Cglib 来生成代理。
//			（2.1） JDK 动态接口代理
//					JDK 动态代理主要涉及到 java.lang.reflect 包中的两个类：Proxy 和 InvocationHandler。
//				InvocationHandler 是一个接口，通过实现该接口定义横切逻辑，并通过反射机制调用目标类
//				的代码，动态将横切逻辑和业务逻辑编制在一起。Proxy 利用 InvocationHandler 动态创建
//				一个符合某一接口的实例，生成目标类的代理对象。
//			 (2.2)	CGLib 动态代理
//					CGLib 全称为 Code Generation Library，是一个强大的高性能，高质量的代码生成类库，
//				可以在运行期扩展 Java 类与实现 Java 接口，CGLib 封装了 asm，可以再运行期动态生成新
//				的 class。和 JDK 动态代理相比较：JDK 创建代理有一个限制，就是只能为接口创建代理实例，
//				而对于没有通过接口定义业务方法的类，则可以通过 CGLib 创建动态代理。

// 三、 基于注解AOP
//	   （1）启用@AspjectJ支持
//		在applicationContext.xml中配置:	<aop:aspectj-autoproxy />
//	   （2）使用
//		@Component
//		@Aspect
//		public class Operator {
//
//			@Pointcut("execution(* com.aijava.springcode.service..*.*(..))")
//			public void pointCut(){}
//
//			@Before("pointCut()")
//			public void doBefore(JoinPoint joinPoint){
//				System.out.println("AOP Before Advice...");
//			}
//
//			@After("pointCut()")
//			public void doAfter(JoinPoint joinPoint){
//				System.out.println("AOP After Advice...");
//			}
//
//			@AfterReturning(pointcut="pointCut()",returning="returnVal")
//			public void afterReturn(JoinPoint joinPoint,Object returnVal){
//				System.out.println("AOP AfterReturning Advice:" + returnVal);
//			}
//
//			@AfterThrowing(pointcut="pointCut()",throwing="error")
//			public void afterThrowing(JoinPoint joinPoint,Throwable error){
//				System.out.println("AOP AfterThrowing Advice..." + error);
//				System.out.println("AfterThrowing...");
//			}
//
//			@Around("pointCut()")
//			public void around(ProceedingJoinPoint pjp){  --ProceedingJoinPoint 连接点
//				System.out.println("AOP Aronud before...");
//				try {
//					pjp.proceed();
//				} catch (Throwable e) {
//					e.printStackTrace();
//				}
//				System.out.println("AOP Aronud after...");
//			}
//
//		}

// 四、 AOP主要应用场景
//		1. Authentication 权限
//		2. Caching 缓存
//		3. Context passing 内容传递
//		4. Error handling 错误处理
//		5. Lazy loading 懒加载
//		6. Debugging 调试
//		7. logging, tracing, profiling and monitoring 记录跟踪 优化 校准
//		8. Performance optimization 性能优化
//		9. Persistence 持久化
//		10. Resource pooling 资源池
//		11. Synchronization 同步
//		12. Transactions 事务

public interface Advice {

}
