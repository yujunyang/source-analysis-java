## Spring中BeanFactory与FactoryBean的区别

`org.springframework.beans` 及 `org.springframework.context` 包是 Spring IoC 容器的基础。



共同点：

​         都是接口

区别：

​      BeanFactory 以Factory结尾，表示它是一个工厂类，用于管理Bean的一个工厂

​             在Spring中，所有的Bean都是由BeanFactory(也就是IOC容器)来进行管理的。

​      但对FactoryBean而言，这个Bean不是简单的Bean，而是一个能生产或者修饰对象生成的工厂Bean,

​             它的实现与设计模式中的工厂模式和修饰器模式类似。



#### 1.BeanFactory

是一个接口，`public interface BeanFactory`，提供如下方法：

- `Object getBean(String name)`
- `<T> T getBean(String name, Class<T> requiredType)`
- `<T> T getBean(Class<T> requiredType)`
- `Object getBean(String name, Object... args)`
- `boolean containsBean(String name)`
- `boolean isSingleton(String name)`
- `boolean isPrototype(String name)`
- `boolean isTypeMatch(String name, Class<?> targetType)`
- `Class<?> getType(String name)`
- `String[] getAliases(String name)`

在 Spring 中，`BeanFactory`是 IoC 容器的核心接口。它的职责包括：实例化、定位、配置应用程序中的对象及建立这些对象间的依赖。

`BeanFactory` 提供的高级配置机制，使得管理任何性质的对象成为可能。
 `ApplicationContext` 是 `BeanFactory` 的扩展，功能得到了进一步增强，比如更易与 Spring AOP 集成、消息资源处理(国际化处理)、事件传递及各种不同应用层的 context 实现(如针对 web 应用的`WebApplicationContext`)。

用的比较多的 `BeanFactory` 的子类是 `ClassPathXmlApplicationContext`，这是   `ApplicationContext`接口的一个子类，`ClassPathXmlApplicationContext`从 xml 的配置文件中获取 bean 并且管理他们，例如：

```java
public static void main(String[] args) throws Exception {
    BeanFactory bf = new ClassPathXmlApplicationContext("student.xml");
    Student studentBean = (Student) bf.getBean("studentBean");

    studentBean.print();
}

<bean id="studentBean" class="advanced.Student">
    <property name="name" value="Tom"/>
    <property name="age" value="18"/>
</bean>
```

#### 2.FactoryBean

Spring 中为我们提供了两种类型的 bean，一种就是普通的 bean，我们通过 `getBean(id)` 方法获得是该 bean 的实际类型，另外还有一种 bean 是 `FactoryBean`，也就是工厂 bean，我们通过 `getBean(id)` 获得是该工厂所产生的 Bean 的实例，而不是该 `FactoryBean` 的实例。

`FactoryBean` 是一个 Bean，实现了 `FactoryBean` 接口的类有能力改变 bean，`FactoryBean` 希望你实现了它之后返回一些内容，Spring 会按照这些内容去注册 bean。
 `public interface FactoryBean<T>`，提供如下方法：

- `T getObject()`
- `Class<?> getObjectType()`
- `boolean isSingleton()`

通常情况下，bean 无须自己实现工厂模式，Spring 容器担任工厂 角色；但少数情况下，容器中的 bean 本身就是工厂，作用是产生其他 bean 实例。由工厂 bean 产生的其他 bean 实例，不再由 Spring 容器产生，因此与普通 bean 的配置不同，不再需要提供 class 元素。

示例：
 构造一个 `FactoryBean` 的实现：

```java
public class StudentFactoryBean implements FactoryBean<Student> {
    private String name;
    private int age;

    @Override
    public Student getObject() throws Exception {
        return new Student(name, age);
    }

    @Override
    public Class<?> getObjectType() {
        return Student.class;
    }

    /**
     * 工厂所管理的对象是否为单例的
     * 即如果该方法返回true，那么通过getObject()方法返回的对象都是同一个对象
     */
    @Override
    public boolean isSingleton() {
        return true;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getAge() {
        return age;
    }

    public void setAge(int age) {
        this.age = age;
    }
}
```

XML配置如下：

```jsx
<bean id="studentFactoryBean" class="spring.StudentFactoryBean">
    <property name="name" value="Tom"/>
    <property name="age" value="28"/>
</bean>
```

使用：

```java
public static void main(String[] args) throws Exception {
    BeanFactory bf = new ClassPathXmlApplicationContext("student.xml");
    Student studentBean = (Student) bf.getBean("studentFactoryBean");

    studentBean.print();
}
```

另一篇文章：https://www.cnblogs.com/xingzc/p/9138256.html





FactoryBean是容器的实例，BeanFactory是容器的规范；

FactoryBean 是spring内部实现一种规范，以&开头作为beanName，Spring中的所有容器都是FactoryBean

因为容器本身也由容器管理，root来创建，都是单利放在IOC容器中。

BeanFactory：Bean工厂的顶层规范，只是定义了getBean（）方法。



