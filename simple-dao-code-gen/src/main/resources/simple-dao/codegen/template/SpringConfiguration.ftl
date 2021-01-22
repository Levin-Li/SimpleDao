package ${modulePackageName};

import com.levin.commons.dao.repository.RepositoryFactoryBean;
import com.levin.commons.dao.repository.annotation.EntityRepository;
import com.levin.commons.service.proxy.ProxyBeanScan;
import lombok.extern.slf4j.Slf4j;

import org.springframework.boot.autoconfigure.domain.EntityScan;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

//Auto gen by simple-dao-codegen ${.now}

@Configuration
@Slf4j
//spring data scan，jpa querydsl entity class ...

@EntityScan({"${modulePackageName}"})

@ComponentScan("${modulePackageName}")

@ProxyBeanScan(scanType = EntityRepository.class
, basePackages = {"${modulePackageName}"}
, factoryBeanClass = RepositoryFactoryBean.class)
public class ${moduleName}SpringConfiguration {

}
