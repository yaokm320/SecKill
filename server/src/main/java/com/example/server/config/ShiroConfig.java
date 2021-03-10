package com.example.server.config;

import com.example.server.service.CustomRealm;
import org.apache.shiro.mgt.SecurityManager;
import org.apache.shiro.spring.web.ShiroFilterFactoryBean;
import org.apache.shiro.web.mgt.DefaultWebSecurityManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

/**
 * shiro的通用化配置
 *
 **/
@Configuration
public class ShiroConfig {

    @Bean
    public CustomRealm customRealm() {
        CustomRealm realm = new CustomRealm();
        return realm;
    }

    @Bean
    public SecurityManager securityManager() {
        DefaultWebSecurityManager securityManager = new DefaultWebSecurityManager();
        securityManager.setRealm(customRealm());
        securityManager.setRememberMeManager(null);
        return securityManager;
    }

    @Bean
    public ShiroFilterFactoryBean shiroFilterFactoryBean() {
        ShiroFilterFactoryBean bean = new ShiroFilterFactoryBean();
        bean.setSecurityManager(securityManager());
        bean.setLoginUrl("/to/login");
        bean.setUnauthorizedUrl("/unauth");

        Map<String, String> filterChainDefinitionMap = new HashMap<>();

        // 可以匿名访问的链接
        filterChainDefinitionMap.put("/to/login", "anon");
        // 需要授权访问的链接
        filterChainDefinitionMap.put("/kill/execute/*", "authc");
        filterChainDefinitionMap.put("/item/detail/*", "authc");
        // 可以匿名访问的链接
        filterChainDefinitionMap.put("/**", "anon");

        bean.setFilterChainDefinitionMap(filterChainDefinitionMap);
        return bean;
    }
}
