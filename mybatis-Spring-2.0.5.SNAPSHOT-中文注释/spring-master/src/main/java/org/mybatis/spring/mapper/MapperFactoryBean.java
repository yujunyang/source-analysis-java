/**
 * Copyright 2010-2019 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.mybatis.spring.mapper;

import static org.springframework.util.Assert.notNull;

import org.apache.ibatis.executor.ErrorContext;
import org.apache.ibatis.session.Configuration;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.support.SqlSessionDaoSupport;
import org.springframework.beans.factory.FactoryBean;

/**
 * BeanFactory that enables injection of MyBatis mapper interfaces. It can be set up with a SqlSessionFactory or a
 * pre-configured SqlSessionTemplate.
 * <p>
 * Sample configuration:
 *
 * <pre class="code">
 * {@code
 *   <bean id="baseMapper" class="org.mybatis.spring.mapper.MapperFactoryBean" abstract="true" lazy-init="true">
 *     <property name="sqlSessionFactory" ref="sqlSessionFactory" />
 *   </bean>
 *
 *   <bean id="oneMapper" parent="baseMapper">
 *     <property name="mapperInterface" value="my.package.MyMapperInterface" />
 *   </bean>
 *
 *   <bean id="anotherMapper" parent="baseMapper">
 *     <property name="mapperInterface" value="my.package.MyAnotherMapperInterface" />
 *   </bean>
 * }
 * </pre>
 * <p>
 * Note that this factory can only inject <em>interfaces</em>, not concrete classes.
 *
 * @author Eduardo Macarron
 *
 * @see SqlSessionTemplate
 */
//  1.  MapperFactoryBean 的初始化
//    因为实现了 InitializingBean 接口，Spring 会保证在 bean 初始化时首先调用 afterPropertiesSet
//  方法来完成其初始化逻辑。追踪父类，发现afterPropertiesSet方法是在 Spring 类中 DaoSupport 类中实现。代码如下：

//  @Override
//  public final void afterPropertiesSet() throws IllegalArgumentException, BeanInitializationException {
//    // Let abstract subclasses check their configuration.
//    checkDaoConfig();
//
//    // Let concrete implementations initialize themselves.
//      try {
//        initDao();
//      }
//      catch (Exception ex) {
//      throw new BeanInitializationException("Initialization of DAO failed", ex);
//      }
//    }

//    但从函数名称来看我们大体推测，MapperFactoryBean 的初始化包括对 DAO 配置的验证以
//  及对 DAO 的初始工作，其中 initDao() 方法是模板方法，设计为留给子类做进一步逻辑处理。
//  而 checkDaoConfig() 才是我们分析的重点。

public class MapperFactoryBean<T> extends SqlSessionDaoSupport implements FactoryBean<T> {

  private Class<T> mapperInterface;

  private boolean addToConfig = true;

  public MapperFactoryBean() {
    // intentionally empty
  }

  public MapperFactoryBean(Class<T> mapperInterface) {
    this.mapperInterface = mapperInterface;
  }

  /**
   * {@inheritDoc}
   */
/// super.checkDaoConfig() 在 SqlSessionDaoSupport 类中实现，代码如下:
//    protected void checkDaoConfig() {
//      notNull(this.sqlSessionTemplate, "Property 'sqlSessionFactory' or 'sqlSessionTemplate' are required");
//    }
//   结合代码我们了解到对于DAO配置的验证，Spring做了以下几个方面的工作。
//   （1） 父类中对于 sqlSession 不为空的验证；
//        sqlSession 作为根据接口创建映射器代理的接触类一定不可以为空，而sqlSession旳初贻化工作是在设定其 sqlSessionFactory 属性时完成的。
//    public void setSqlSessionFactory(SqlSessionFactory sqlSessionFactory) {
//      if (this.sqlSessionTemplate == null || sqlSessionFactory != this.sqlSessionTemplate.getSqlSessionFactory()) {
//        this.sqlSessionTemplate = createSqlSessionTemplate(sqlSessionFactory);
//      }
//    }
//    protected SqlSessionTemplate createSqlSessionTemplate(SqlSessionFactory sqlSessionFactory) {
//      return new SqlSessionTemplate(sqlSessionFactory);
//    }
//        也就是说，对于下面的配置如果忽略了对于sqlSessionFactory属性的设置，那么在此时就会被检测出来。
//        <bean id="userMapper" class="org.mybatis.Spring.mapper.MapperFactoryBean">
//          <property name="mapperInterface" value="test.mybatis.dao.UserMapper"></property>
//          <property name="sqlSessionFactory" ref="sqlSessionFactory">/property>
//        </bean>
//   （2）  映射接口的验证；
//           接口是映射器的基础，sqlSession会根据接口动态创建相应的代理类，所以接口必不可少。
//    (3)  映射文件存在性验证
//    对于函数前半部分的验证我们都很容易理解，无非是对配置文件中的属性是否存在做
//  验证，但是后面部分是完成了什么方面的验证呢? 如果读者读过 MyBatis 源码，你就会知
//  道，在 MyBatis 实现过程中并没有手动调用 configuration.addMapper 方法，而是在映射文件
//  读取过程中一旦解析到如 <mapper namespace=',Mapper.UserMapper"> ,便会自动进行类型
//  映射的注册。那么，Spring中为什么会把这个功能单独拿出来放在验证里呢？这是不是多 此一举呢？
//    在上面的函数中，configuration.addMapper(this.mapperInterface)其实就是将 UserMapper 注
//  册到映射类型中，如果你可以保证这个接口一定存在对应的映射文件，那么其实这个验证并没有必要。
//  但是，由于这个是我们自行决定的配置，无法保证这里配置的接口一定存在对应的映射文件，所以这里非常有必要进行验证。
//  在执行此代码的时候，MyBatis会检査嵌入的映射接口是否存在对应的映射文件，如果没有回拋出异常，Spring正是在用这种方式来完成接口对应
  @Override
  protected void checkDaoConfig() {
    super.checkDaoConfig();

    notNull(this.mapperInterface, "Property 'mapperInterface' is required");

    Configuration configuration = getSqlSession().getConfiguration();
    if (this.addToConfig && !configuration.hasMapper(this.mapperInterface)) {
      try {
        configuration.addMapper(this.mapperInterface);
      } catch (Exception e) {
        logger.error("Error while adding the mapper '" + this.mapperInterface + "' to configuration.", e);
        throw new IllegalArgumentException(e);
      } finally {
        ErrorContext.instance().reset();
      }
    }
  }

  /**
   * {@inheritDoc}
   */
  /// 获取 MapperFactoryBean 的实例
  @Override
  public T getObject() throws Exception {
    return getSqlSession().getMapper(this.mapperInterface);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Class<T> getObjectType() {
    return this.mapperInterface;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public boolean isSingleton() {
    return true;
  }

  // ------------- mutators --------------

  /**
   * Sets the mapper interface of the MyBatis mapper
   *
   * @param mapperInterface
   *          class of the interface
   */
  public void setMapperInterface(Class<T> mapperInterface) {
    this.mapperInterface = mapperInterface;
  }

  /**
   * Return the mapper interface of the MyBatis mapper
   *
   * @return class of the interface
   */
  public Class<T> getMapperInterface() {
    return mapperInterface;
  }

  /**
   * If addToConfig is false the mapper will not be added to MyBatis. This means it must have been included in
   * mybatis-config.xml.
   * <p>
   * If it is true, the mapper will be added to MyBatis in the case it is not already registered.
   * <p>
   * By default addToConfig is true.
   *
   * @param addToConfig
   *          a flag that whether add mapper to MyBatis or not
   */
  public void setAddToConfig(boolean addToConfig) {
    this.addToConfig = addToConfig;
  }

  /**
   * Return the flag for addition into MyBatis config.
   *
   * @return true if the mapper will be added to MyBatis in the case it is not already registered.
   */
  public boolean isAddToConfig() {
    return addToConfig;
  }
}
