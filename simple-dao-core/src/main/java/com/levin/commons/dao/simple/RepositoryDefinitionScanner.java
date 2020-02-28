package com.levin.commons.dao.simple;

import com.levin.commons.dao.simple.annotation.EntityRepository;
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.beans.factory.config.BeanDefinitionHolder;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.GenericBeanDefinition;
import org.springframework.context.annotation.ClassPathBeanDefinitionScanner;
import org.springframework.core.type.filter.AnnotationTypeFilter;


import java.lang.annotation.Annotation;
import java.util.Set;

/**
 * Repository 注解扫描器
 * <p>
 * 完成注解扫描并重新定义bean
 */


public class RepositoryDefinitionScanner
        extends ClassPathBeanDefinitionScanner {

    protected Class<? extends Annotation>[] includeTypes;

    protected Class factoryBeanClass = RepositoryFactoryBean.class;

    public RepositoryDefinitionScanner(BeanDefinitionRegistry registry) {
        super(registry, false);
    }

    public Class getFactoryBeanClass() {
        return factoryBeanClass;
    }

    public void setFactoryBeanClass(Class factoryBeanClass) {
        this.factoryBeanClass = factoryBeanClass;
    }

    public Class<? extends Annotation>[] getIncludeTypes() {
        return includeTypes;
    }

    public void setIncludeTypes(Class<? extends Annotation>... includeTypes) {

        this.includeTypes = includeTypes;

        if (includeTypes != null) {
            for (Class<? extends Annotation> includeType : includeTypes) {
                this.addIncludeFilter(new AnnotationTypeFilter(includeType));
            }
        }
    }

    public void registerDefaultIncludeFilters() {
        this.setIncludeTypes(EntityRepository.class);
    }

    @Override
    protected Set<BeanDefinitionHolder> doScan(String... basePackages) {

        if (includeTypes == null
                || includeTypes.length == 0) {
            registerDefaultIncludeFilters();
        }

        Set<BeanDefinitionHolder> beanDefinitionHolderSet = super.doScan(basePackages);

        for (BeanDefinitionHolder holder : beanDefinitionHolderSet) {

            GenericBeanDefinition definition = (GenericBeanDefinition) holder.getBeanDefinition();

            definition.getPropertyValues().add("serviceType", definition.getBeanClassName());

            definition.setAutowireCandidate(true);
            //替换为factoryBean
            definition.setBeanClass(factoryBeanClass);
        }




//         * @see org.springframework.beans.factory.config.BeanDefinition
//                * @see AbstractBeanDefinition
//                * @see RootBeanDefinition
//                * @see ChildBeanDefinition
//                * @see DefaultListableBeanFactory
//                * @see org.springframework.context.support.GenericApplicationContext
//                * @see org.springframework.beans.factory.xml.XmlBeanDefinitionReader
//                * @see PropertiesBeanDefinitionReader
//                */
//        public interface BeanDefinitionRegistry

        return beanDefinitionHolderSet;
    }

    @Override
    protected boolean isCandidateComponent(AnnotatedBeanDefinition beanDefinition) {
        return true;
    }
}