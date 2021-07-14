package ${modulePackageName}.config;

import ${modulePackageName}.*;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.builders.WebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.web.SecurityFilterChain;

//参考文章： https://blog.csdn.net/u012702547/article/details/106800446/


@Configuration("${modulePackageName}.config.ModuleWebSecurityConfigurer")
//@Order(101)
@Slf4j
@EnableGlobalMethodSecurity(prePostEnabled = true)
//@EnableGlobalAuthentication
@ConditionalOnProperty(value = "plugin." + ModuleOption.ID + ".ModuleWebSecurityConfigurer", havingValue = "false", matchIfMissing = true)
public class ModuleWebSecurityConfigurer extends WebSecurityConfigurerAdapter {


//    @Bean
//    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
//        return http
//                .antMatcher("/**")
//                .authorizeRequests(authorize -> authorize
//                        .anyRequest().authenticated()
//                )
//                .build();
//    }


    @Override
    protected void configure(HttpSecurity http) throws Exception {

        log.debug("config HttpSecurity");

    }

    @Override
    public void configure(WebSecurity web) throws Exception {

        web.ignoring().antMatchers(
                "/error/**",
                "/*/api-docs",
                "/swagger-ui/**/*",
                "/springfox-swagger-ui/**/*",
                "/swagger-resources/**",
                "/" + ModuleOption.ADMIN_PATH + "**",
                "/" + ModuleOption.H5_PATH + "**",
                "/" + ModuleOption.API_PATH + "auth/**",
                "/" + ModuleOption.API_PATH + "weixin/**"
        );

        log.debug("config WebSecurity");

    }
}