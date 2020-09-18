package com.levin.commons.dao.starter;

import com.levin.commons.conditional.ConditionalOn;
import com.levin.commons.conditional.ConditionalOnList;
import com.levin.commons.dao.JpaDao;
import com.levin.commons.dao.MiniDao;
import com.levin.commons.dao.annotation.Eq;
import com.levin.commons.dao.repository.RepositoryFactoryBean;
import com.levin.commons.dao.repository.annotation.EntityRepository;
import com.levin.commons.dao.support.JpaDaoImpl;
import com.levin.commons.service.proxy.EnableProxyBean;
import com.levin.commons.service.proxy.ProxyBeanScan;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Role;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.SimpleJdbcCall;
import org.springframework.jdbc.core.simple.SimpleJdbcCallOperations;
import org.springframework.jdbc.core.simple.SimpleJdbcInsert;
import org.springframework.jdbc.core.simple.SimpleJdbcInsertOperations;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.PersistenceContext;
import javax.persistence.PersistenceUnit;
import javax.sql.DataSource;

@Configuration

@Role(BeanDefinition.ROLE_SUPPORT)


@ProxyBeanScan(scanType = EntityRepository.class, factoryBeanClass = RepositoryFactoryBean.class
        , basePackages = {"com.levin.commons.dao.repository"})

@EnableProxyBean(registerTypes = EntityRepository.class)

@Slf4j
public class JpaDaoConfiguration implements ApplicationContextAware {


/*    @Bean
    @ConditionalOnList({
            @ConditionalOn(action = ConditionalOn.Action.OnMissingBean, types = FormattingConversionService.class),
    })
    FormattingConversionServiceFactoryBean formattingConversionServiceFactoryBean() {
        return new FormattingConversionServiceFactoryBean();
    }*/


    //    @Autowired
    @PersistenceUnit
    private EntityManagerFactory entityManagerFactory;

    //    @Autowired
    @PersistenceContext
    private EntityManager defaultEntityManager;

    @Autowired
    DataSource dataSource;

    @Bean
    @ConditionalOn(action = ConditionalOn.Action.OnMissingBean, types = JdbcTemplate.class)
    JdbcTemplate jdbcTemplate() {
        return new JdbcTemplate(dataSource);
    }

    @Bean
    @ConditionalOn(action = ConditionalOn.Action.OnMissingBean, types = SimpleJdbcInsert.class)
    SimpleJdbcInsert simpleJdbcInsertOperations() {
        return new SimpleJdbcInsert(jdbcTemplate());
    }

    @Bean
    @ConditionalOn(action = ConditionalOn.Action.OnMissingBean, types = SimpleJdbcCall.class)
    SimpleJdbcCall simpleJdbcCallOperations() {
        return new SimpleJdbcCall(jdbcTemplate());
    }


    /**
     * 因为在注册期 JpaDao bean 已经被引用，所以事务注解不会尝试重试初始化 JpaDao bean
     *
     *
     * <p>
     * 这将导致事务无效，使用代理FactoryBean的方式，可以让@Transactional事务自己加在已有代理对象上面
     *
     * @return
     */
    @Bean("com.levin.commons.dao.JpaDao")
    @ConditionalOnList({
            @ConditionalOn(action = ConditionalOn.Action.OnClass, types = {Eq.class, MiniDao.class, JpaDao.class, JpaDaoImpl.class}),
            @ConditionalOn(action = ConditionalOn.Action.OnMissingBean, types = JpaDao.class),
    })
    JpaDao newJpaDao() {

        //加入默认的时间格式
//        DaoContext.setGlobalVar("dateFormat", "YYYYMMDD");
//        DaoContext.setGlobalVar("DF_YEAR", "YYYY");
//        DaoContext.setGlobalVar("DF_YYYYMMDD", "YYYYMMDD");

        return new JpaDaoImpl();
    }


    ApplicationContext context;

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.context = applicationContext;
    }

}
