# Spring Boot 开发教程

## 1. 概述

Spring Boot 是 Spring 框架的扩展，提供了快速开发独立 Spring 应用的能力。它通过自动配置和约定优于配置的原则，大幅减少了项目搭建和配置的时间。Spring Boot 内嵌了 Tomcat、Jetty 等 Web 服务器，无需部署 WAR 文件即可运行应用。

## 2. 主要特性

### 2.1 自动配置

Spring Boot 根据类路径中的依赖自动配置 Bean。例如，当检测到 H2 数据库依赖时，会自动配置内存数据源。开发者可以通过 `@EnableAutoConfiguration` 注解或 `@SpringBootApplication` 来启用自动配置功能。

### 2.2 起步依赖

起步依赖（Starter Dependencies）是一组预定义的依赖集合，简化了 Maven/Gradle 配置工作。常用的起步依赖包括：

- `spring-boot-starter-web`：Web 开发支持
- `spring-boot-starter-data-jpa`：JPA 数据访问
- `spring-boot-starter-security`：安全认证
- `spring-boot-starter-test`：单元测试支持

### 2.3 Actuator 监控

Spring Boot Actuator 提供了生产级的监控和管理端点。通过 `/actuator/health` 可以检查应用健康状态，`/actuator/metrics` 提供详细的性能指标数据。

## 3. 快速搭建

### 3.1 使用 Spring Initializr

访问 https://start.spring.io 可以快速生成项目骨架。选择需要的依赖和 Spring Boot 版本，下载压缩包后导入 IDE 即可开始开发工作。

### 3.2 项目结构

标准的 Spring Boot 项目目录结构如下：

```
src/
├── main/
│   ├── java/
│   │   └── com/example/demo/
│   │       ├── DemoApplication.java
│   │       ├── controller/
│   │       ├── service/
│   │       ├── repository/
│   │       └── entity/
│   └── resources/
│       ├── application.yml
│       ├── static/
│       └── templates/
└── test/
```

## 4. 配置管理

Spring Boot 支持 `application.properties` 和 `application.yml` 两种配置格式。推荐使用 YAML 格式，层次结构更清晰：

```yaml
server:
  port: 8080
  servlet:
    context-path: /api

spring:
  datasource:
    url: jdbc:mysql://localhost:3306/mydb
    username: root
    password: 123456
  jpa:
    hibernate:
      ddl-auto: update
    show-sql: true
```

## 5. 接口开发

使用 `@RestController` 注解创建 RESTful API 接口：

```java
@RestController
@RequestMapping("/api/users")
public class UserController {

    @Autowired
    private UserService userService;

    @GetMapping
    public List<User> list() {
        return userService.findAll();
    }

    @GetMapping("/{id}")
    public User getById(@PathVariable Long id) {
        return userService.findById(id);
    }

    @PostMapping
    public User create(@RequestBody User user) {
        return userService.save(user);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        userService.deleteById(id);
    }
}
```

## 6. 数据库操作

Spring Data JPA 提供了便捷的数据访问能力。只需继承 `JpaRepository` 接口即可获得基本的 CRUD 操作方法：

```java
public interface UserRepository extends JpaRepository<User, Long> {
    List<User> findByName(String name);
    List<User> findByAgeGreaterThan(int age);
}
```

## 7. 错误处理

使用 `@ControllerAdvice` 和 `@ExceptionHandler` 实现统一的全局异常处理：

```java
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(ResourceNotFoundException e) {
        return ResponseEntity.status(404)
            .body(new ErrorResponse("NOT_FOUND", e.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneral(Exception e) {
        return ResponseEntity.status(500)
            .body(new ErrorResponse("INTERNAL_ERROR", "服务器内部错误"));
    }
}
```

## 8. 结语

Spring Boot 大幅简化了 Java Web 应用的开发流程。通过自动配置、起步依赖和内嵌服务器，开发者可以专注于业务逻辑而非基础设施配置。结合 Spring Cloud 还能构建微服务架构，是当前企业级 Java 开发的主流框架选择。
