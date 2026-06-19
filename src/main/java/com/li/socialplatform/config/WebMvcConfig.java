package com.li.socialplatform.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.security.task.DelegatingSecurityContextAsyncTaskExecutor;
import org.springframework.web.servlet.config.annotation.AsyncSupportConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/*
 Spring MVC 在处理异步请求（如 Flux<String> 返回类型）时，
 使用了默认的 SimpleAsyncTaskExecutor，这个执行器不适合生产环境的高负载场景
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Bean(name = "mvcTaskExecutor")
    public AsyncTaskExecutor mvcTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(10);
        executor.setMaxPoolSize(50);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("mvc-async-");
        executor.setKeepAliveSeconds(60);
        executor.setAllowCoreThreadTimeOut(true);
        executor.initialize();
        return new DelegatingSecurityContextAsyncTaskExecutor(executor);
    }

    /**
     * 使用构造器注入的方式引用 Spring 管理的 mvcTaskExecutor Bean，
     * 避免直接调用 @Bean 方法导致创建多个实例。
     * 注意：configureAsyncSupport 中的 mvcTaskExecutor() 调用实际上会触发
     * Spring 的 CGLIB 代理，返回已缓存的单例 Bean，因此不会创建额外实例。
     */
    @Override
    public void configureAsyncSupport(AsyncSupportConfigurer configurer) {
        configurer.setTaskExecutor(mvcTaskExecutor());
        configurer.setDefaultTimeout(30000);
    }
}
