package com.levin.commons.dao.repository;


import com.levin.commons.dao.JpaDao;
import com.levin.commons.dao.Paging;
import com.levin.commons.dao.TargetOption;
import com.levin.commons.dao.annotation.Contains;
import com.levin.commons.dao.annotation.Eq;
import com.levin.commons.dao.annotation.logic.OR;
import com.levin.commons.dao.annotation.update.Update;
import com.levin.commons.dao.domain.Group;
import com.levin.commons.dao.repository.annotation.EntityRepository;
import com.levin.commons.dao.repository.annotation.QueryRequest;
import com.levin.commons.dao.repository.annotation.UpdateRequest;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

@EntityRepository("组DAO")
@TargetOption(entityClass = Group.class )
public abstract class GroupDao {

    @Autowired
    private JpaDao jpaDao;

    @QueryRequest
    public Group findOne(@OR @Eq Long id, @Contains String name,
                         @Eq String category, Paging paging) {

        Group result = RepositoryFactoryBean.getProxyInvokeResult();

        System.out.println(result);

        return (Group) result;
    }

    @QueryRequest
    public List<Group> find(@OR @Eq Long id, @Contains String name,
                            @Eq String category, Paging paging) {

        List<Group> groups = RepositoryFactoryBean.getProxyInvokeResult();

        System.out.println(groups);

        return groups;
    }

    @UpdateRequest
//    @Transactional
    public int update(@Eq(require = true) Long id, @Update String name) {

        Integer r = RepositoryFactoryBean.getProxyInvokeResult();

        return r != null ? r : 0;
    }

    //没有注解方法将无效，如果调用RepositoryFactoryBean.getProxyInvokeResult(); 将会生产异常
    public Object noAnnoMethod(@Eq Long id, @Update String name) {

        Object r = RepositoryFactoryBean.getProxyInvokeResult();

        return r;
    }

    @QueryRequest
    public Group findOneAndRepeatGetResult(@OR @Eq Long id, @Contains String name,
                                           @Eq String category, Paging paging) {

        Object result = RepositoryFactoryBean.getProxyInvokeResult();

        System.out.println(result);

        result = RepositoryFactoryBean.getProxyInvokeResult();

        return (Group) result;
    }
}
