package com.levin.commons.dao.support;


import com.levin.commons.dao.*;
import com.levin.commons.dao.util.ExceptionUtils;
import com.levin.commons.dao.util.ObjectUtil;
import com.levin.commons.dao.util.QLUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.format.support.FormattingConversionService;
import org.springframework.orm.jpa.EntityManagerFactoryUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.ClassUtils;
import org.springframework.util.StringUtils;

import javax.annotation.PostConstruct;
import javax.persistence.*;
import javax.validation.Validator;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;


/**
 * JPA实体状态
 * 1. new/transient
 * 新建的对象，或者是说这个对象没有加入到持久化上下文当中。
 * 2 managed
 * 对象存在持久化上下文中
 * 3 detach
 * 对象脱离了持久化上下文
 * <p/>
 * <p/>
 * <p/>
 * <p/>
 * 1.persist(Object entity)方法
 * 这个方法把一个实体加入持久化上下文中，也就是缓存中，在事务提交或者调用flush()方法的时候，把这个实体保存到数据库中（执行insert语句），如果实体已存在，则抛出EntityExistsException异常，缓存则不存在了。
 * <p/>
 * 2.find(Class<T> entityClass,Object primaryObject)
 * 根据主键从数据库中查询一个实体，这个方法首先从缓存中去查找，如果找不到，就从数据库中去找，并把它加入到缓存中。
 * 3.merge(T object)
 * 把一个对象加入到当前的持久化上下文中，就是把一个对象从detach转变为managed，并返回这个对象。当一个对象设置了主键，并调用此方法，就会从数据库中根据主键查找到该对象把它放到持久化上下文中，当事物提交的时候，如果对象发生了改变，更新该对象的改变到数据库中，如果对象没有改变，则什么也不做，如果对象没有设置主键，则插入该对象到数据库中。
 * 4.remove(Object entity)
 * 根据主键从数据库中删除一个对象，这个对象的状态必须是managed，否则会抛出IllegalArgumentException,在一个事务中删除一个对象,假如实体管理器对象为em,
 * em.remove(em.merge(entity)),如果直接调用em.remove(entity);会抛出异常
 * IllegalArgumentException。
 * <p/>
 * <p/>
 * public void persist(Object entity)
 * persist 方法可以将实例转换为 managed( 托管 ) 状态。在调用 flush() 方法或提交事物后，实例将会被插入到数据库中。
 * <p/>
 * <p/>
 * <p/>
 * 对不同状态下的实例 A ， persist 会产生以下操作 :
 * <p/>
 * 1.       如果 A 是一个 new 状态的实体，它将会转为 managed 状态；
 * <p/>
 * 2.       如果 A 是一个 managed 状态的实体，它的状态不会发生任何改变。但是系统仍会在数据库执行 INSERT 操作；
 * <p/>
 * 3.       如果 A 是一个 removed( 删除 ) 状态的实体，它将会转换为受控状态；
 * <p/>
 * 4.       如果 A 是一个 detached( 分离 ) 状态的实体，该方法会抛出 IllegalArgumentException 异常，具体异常根据不同的 JPA 实现有关。
 * <p/>
 * public void merge(Object entity)
 * merge 方法的主要作用是将用户对一个 detached 状态实体的修改进行归档，归档后将产生一个新的 managed 状态对象。
 * <p/>
 * <p/>
 * <p/>
 * 对不同状态下的实例 A ， merge 会产生以下操作 :
 * <p/>
 * 1.       如果 A 是一个 detached 状态的实体，该方法会将 A 的修改提交到数据库，并返回一个新的 managed 状态的实例 A2 ；
 * <p/>
 * 2.       如果 A 是一个 new 状态的实体，该方法会产生一个根据 A 产生的 managed 状态实体 A2 ;
 * <p/>
 * 3.       如果 A 是一个 managed 状态的实体，它的状态不会发生任何改变。但是系统仍会在数据库执行 UPDATE 操作；
 * <p/>
 * 4.       如果 A 是一个 removed 状态的实体，该方法会抛出 IllegalArgumentException 异常。
 * <p/>
 * public void refresh(Object entity)
 * refresh 方法可以保证当前的实例与数据库中的实例的内容一致。
 * <p/>
 * <p/>
 * <p/>
 * 对不同状态下的实例 A ， refresh 会产生以下操作 :
 * <p/>
 * 1.       如果 A 是一个 new 状态的实例，不会发生任何操作，但有可能会抛出异常，具体情况根据不同 JPA 实现有关；
 * <p/>
 * 2.       如果 A 是一个 managed 状态的实例，它的属性将会和数据库中的数据同步；
 * <p/>
 * 3.       如果 A 是一个 removed 状态的实例，不会发生任何操作 ;
 * <p/>
 * 4.       如果 A 是一个 detached 状态的实体，该方法将会抛出异常。
 * <p/>
 * public void remove(Object entity)
 * remove 方法可以将实体转换为 removed 状态，并且在调用 flush() 方法或提交事物后删除数据库中的数据。
 * <p/>
 * <p/>
 * <p/>
 * 对不同状态下的实例 A ， remove 会产生以下操作 :
 * <p/>
 * 1.       如果 A 是一个 new 状态的实例， A 的状态不会发生任何改变，但系统仍会在数据库中执行 DELETE 语句；
 * <p/>
 * 2.       如果 A 是一个 managed 状态的实例，它的状态会转换为 removed ；
 * <p/>
 * 3.       如果 A 是一个 removed 状态的实例，不会发生任何操作 ;
 * <p/>
 * 4.       如果 A 是一个 detached 状态的实体，该方法将会抛出异常。
 */

//@ConditionalOnBean({EntityManagerFactory.class})
//@Repository
//@Transactional
public class JpaDaoImpl
        extends AbstractDaoFactory
        implements JpaDao, ApplicationContextAware {

    private static final Logger logger = LoggerFactory.getLogger(JpaDaoImpl.class);

    //    @Autowired
    @PersistenceUnit
    private EntityManagerFactory entityManagerFactory;

    //    @Autowired
    @PersistenceContext
    private EntityManager defaultEntityManager;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired(required = false)
    private ParameterNameDiscoverer parameterNameDiscoverer;

    @Autowired(required = false)
    Validator validator;

    private ApplicationContext applicationContext;

    @Autowired(required = false)
    FormattingConversionService formattingConversionService;


    private final Integer hibernateVersion;

    private boolean disableSessionCache = true;

    @Value("${com.levin.commons.dao.param.placeholder:#{T(com.levin.commons.dao.JpaDao).DEFAULT_JPQL_PARAM_PLACEHOLDER}}")
    private String paramPlaceholder = JpaDao.DEFAULT_JPQL_PARAM_PLACEHOLDER;

    private static final Map<String, String> idAttrNames = new ConcurrentHashMap<>();

    private static final DeepCopier deepCopier = new DeepCopier() {
        @Override
        public <T> T copy(Object source, Object target, int deep, String... ignoreProperties) {
            return (T) ObjectUtil.copyProperties(source, target, deep, ignoreProperties);
        }
    };

    public JpaDaoImpl() {
        hibernateVersion = getHibernateVersion();
    }

    public static Integer getHibernateVersion() {

        try {
            //5.2.10.Final

            String version = (String) ClassUtils
                    .forName("org.hibernate.Version", JpaDaoImpl.class.getClassLoader())
                    .getDeclaredMethod("getVersionString")
                    .invoke(null);

            //
            logger.info("*** org.hibernate ***  Version:" + version);

            return Integer.parseInt(version.substring(0, version.indexOf(".")));

        } catch (IllegalAccessException e) {
            logger.error("getHibernateVersion error" + ExceptionUtils.getRootCauseInfo(e));
        } catch (InvocationTargetException e) {
            logger.error("getHibernateVersion error" + ExceptionUtils.getRootCauseInfo(e));
        } catch (NoSuchMethodException e) {
            logger.error("getHibernateVersion error" + ExceptionUtils.getRootCauseInfo(e));
        } catch (ClassNotFoundException e) {
        } catch (Exception e) {
            logger.warn("getHibernateVersion " + ExceptionUtils.getRootCauseInfo(e));
        }

        return null;
    }

    @Override
    public boolean isJpa() {
        return true;
    }

    public EntityManagerFactory getEntityManagerFactory() {
        return entityManagerFactory;
    }

    public EntityManager getDefaultEntityManager() {
        return defaultEntityManager;
    }

    @Override
    public PlatformTransactionManager getTransactionManager() {
        return transactionManager;
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }


    @Override
    public ParameterNameDiscoverer getParameterNameDiscoverer() {
        return parameterNameDiscoverer;
    }

    @Override
    public void setParameterNameDiscoverer(ParameterNameDiscoverer parameterNameDiscoverer) {
        this.parameterNameDiscoverer = parameterNameDiscoverer;
    }

    @PostConstruct
    public void initCheck() {

        if (logger.isDebugEnabled()) {
            logger.debug("emf:" + entityManagerFactory + ",em:" + defaultEntityManager + ",tm:" + transactionManager);
        }

        if (parameterNameDiscoverer == null) {
            parameterNameDiscoverer = new MethodParameterNameDiscoverer();
        }

        if (defaultEntityManager == null
                && entityManagerFactory != null) {
            defaultEntityManager = entityManagerFactory.createEntityManager();
        }

        if (defaultEntityManager == null || entityManagerFactory == null) {
            throw new IllegalStateException("entityManager or entityManagerFactory must be set");
        }

    }


    @Override
    public Validator getValidator() {
        return validator;
    }


    /**
     * 属性拷贝
     *
     * @param source
     * @param target
     * @return
     */
    @Override
    public <T> T copyProperties(Object source, T target, String... ignoreProperties) {
        return (T) ObjectUtil.copyProperties(source, target, -1, ignoreProperties);

    }

    @Override
    public <T> T copyProperties(Object source, Object target, int deep, String... ignoreProperties) {
        return (T) ObjectUtil.copyProperties(source, target, deep, ignoreProperties);
    }

    @Override
    public JpaDao setParamPlaceholder(String paramPlaceholder) {

        this.paramPlaceholder = paramPlaceholder;

        return this;
    }

    @Override
    public JpaDao disableSessionCache() {

        this.disableSessionCache = true;

        return this;
    }

    @Override
    public DeepCopier getDeepCopier() {

        return deepCopier;
    }

    /**
     * 强制让对象脱管
     *
     * @param object
     */
    @Override
    public JpaDao detach(Object object) {

        if (object != null) {
            try {
                getEntityManager().detach(object);
            } catch (IllegalArgumentException e) {
            }
        }

        return this;
    }

    @Override
    public EntityManager getEntityManager() {

        if (defaultEntityManager != null) {

            init(defaultEntityManager);

            return defaultEntityManager;
        }

        if (entityManagerFactory != null) {

            logger.info("默认实体管理器没有注入，将使用entityManagerFactory创建");

            EntityManager em = EntityManagerFactoryUtils.getTransactionalEntityManager(entityManagerFactory);

            if (em == null) {
                em = entityManagerFactory.createEntityManager();
            }

            init(em);

            return em;
        }

        throw new IllegalStateException("can't find entityManager instance");

    }


    public EntityManager init(EntityManager entityManager) {

        if (entityManager != null) {

            FlushModeType flushMode = entityManager.getFlushMode();

            if (flushMode == null) {
                //   entityManager.setFlushMode(FlushModeType.AUTO);
                logger.warn("entityManager flushMode is null," + entityManager);
            }
//            query.setHint("javax.persistence.cache.storeMode", CacheStoreMode.BYPASS);
//            query.setHint("javax.persistence.cache.retrieveMode", CacheRetrieveMode.BYPASS);

//            entityManager.setProperty();
        }

        return entityManager;
    }


    @Override
    @Transactional
    public Object create(Object entity) {

        //如果有ID对象，将会抛出异常
        EntityManager em = getEntityManager();

        try {
            em.persist(entity);
        } catch (EntityExistsException e) {
            return em.merge(entity);
        } finally {
            //flush 并且移除出缓存
            //em.flush();
            //移除出缓存
            // em.detach(entity);
        }

        return entity;
    }

    @Override
    @Transactional
    public Object save(Object entity) {

        EntityManager em = getEntityManager();

        try {
            //如果是一个 removed 状态的实体，该方法会抛出 IllegalArgumentException 异常。
            if (getEntityId(entity) != null) {

                Object merge = em.merge(entity);

                //从缓存中移除
                //em.detach(merge);

                return merge;
            }

        } catch (IllegalArgumentException e) {
            //不做异常处理尝试使用persist进行保存
        } catch (EntityNotFoundException e) {
            //不做异常处理尝试使用，persist进行保存
        }

        //removed 状态的实体，persist可以处理
        em.persist(entity);

        //从缓存中移除
        //em.detach(entity);

        return entity;
    }

    @Override
    @Transactional
    public void delete(Object entity) {

        EntityManager em = getEntityManager();

        if (em.contains(entity)) {
            em.remove(entity);
        } else {
            Object mEntity = em.find(entity.getClass(), getEntityId(entity));
            if (mEntity != null) {
                em.remove(mEntity);
            }
        }

    }

    @Override
    @Transactional
    public boolean deleteById(Class entityClass, Object id) {
        Query query = getEntityManager()
                .createQuery("delete from " + entityClass.getName() + " where " + getEntityIdAttrName(entityClass) + " =:pkid");
        query.setParameter("pkid", id);
        return query.executeUpdate() > 0;
        //说明
    }

    @Override
    @Transactional
    public int update(String statement, Object... paramValues) {
        return update(false, statement, paramValues);
    }

    /**
     * 更新
     * 查询参数可以是数组，也可以是Map会进行自动识别
     *
     * @param isNative
     * @param statement   更新或是删除语句
     * @param paramValues 参数可紧一个数组,或是Map，或是List，或是具体的参数值，会对参数进行递归处理
     * @return
     */
    @Override
    @Transactional
    public int update(boolean isNative, String statement, Object... paramValues) {
        return update(isNative, -1, -1, statement, paramValues);
    }

    @Override
    public int updateByQueryObj(Object... queryObjs) {
        return newDao(UpdateDao.class, queryObjs).update();
    }

    @Override
    public int deleteByQueryObj(Object... queryObjs) {
        return newDao(DeleteDao.class, queryObjs).delete();
    }

    /**
     * 替换默认的占位符
     * <p>
     * hibernate 要去
     *
     * @param sql
     * @return
     */
    public String replacePlaceholder(boolean isNative, String sql) {

        String placeholder = this.getParamPlaceholder(isNative).trim();

        //如果JDBC 是一样的，就不做处理
        if (MiniDao.DEFAULT_JDBC_PARAM_PLACEHOLDER.trim().equals(placeholder)) {
            return sql;
        }

        return QLUtils.replaceParamPlaceholder(sql, placeholder, getParamStartIndex(isNative), "?", null);

    }

    @Override
    @Transactional
    public int update(boolean isNative, int start, int count, String statement, Object... paramValues) {

        List paramValueList = flattenParams(null, paramValues);


        if (!paramValueList.isEmpty()) {
            statement = replacePlaceholder(isNative, statement);
        }

        if (logger.isDebugEnabled()) {
            logger.debug("Update JPQL:[" + statement + "], Param placeholder:" + getParamPlaceholder(isNative)
                    + " , StartIndex: " + getParamStartIndex(isNative) + " , Params:" + paramValueList);
        }

        EntityManager em = getEntityManager();

        Query query = isNative ? em.createNativeQuery(statement) : em.createQuery(statement);

        //更新缓存
//        query.setHint("javax.persistence.cache.storeMode", CacheStoreMode.REFRESH);

        setParams(getParamStartIndex(isNative), query, paramValueList);

        setRange(query, start, count);

        return query.executeUpdate();
    }

    @Override
    public void flush() {
        getDefaultEntityManager().flush();
    }

    @Override
    public <T> T find(Class<T> entityClass, Object id) {

        EntityManager em = getEntityManager();

        //如果有事务查询前 flush
        if (em.isJoinedToTransaction()) {
            //  em.flush();
        }

        //if (!useQueriesCache) {
//        if (disableSessionCache) {
//            em.clear();
//        }
        // }
        return em.find(entityClass, id);
    }

    @Override
    public <ID> ID getEntityId(Object entity) {

        Class<?> entityClass = entity.getClass();

        String idAttrName = getEntityIdAttrName(entityClass);

        if (!StringUtils.hasText(idAttrName)) {
            throw new IllegalArgumentException("class " + entityClass.getName() + " is not a jpa entity object");
        }

        Exception ex = null;

        try {
            return (ID) entityClass.getDeclaredField(idAttrName).get(entity);
        } catch (Exception e) {
            ex = e;
        }

        try {
            return (ID) entityClass.getMethod("get" + Character.toUpperCase(idAttrName.charAt(0)) + idAttrName.substring(1)).invoke(entity);
        } catch (Exception e) {
            ex = e;
        }

        throw new IllegalArgumentException("class " + entityClass.getName() + " can't get property '" + idAttrName + "' value", ex);

    }

    @Override
    /**
     * @fix 修复实体类继承时无法
     *
     */
    public String getEntityIdAttrName(Object entity) {

        final Class<?> entityClass = (entity instanceof Class) ? (Class<?>) entity : entity.getClass();

        String idAttrName = idAttrNames.get(entityClass.getName());

        if (idAttrName != null) {
            return idAttrName;
        }

        Class type = entityClass;

        while (type != Object.class) {

            String attrName = getIdAttrName(type);

            if (attrName != null) {
                idAttrNames.put(entityClass.getName(), attrName);
                return attrName;
            }

            type = type.getSuperclass();
        }

        throw new IllegalArgumentException("class " + entityClass.getName() + " can't find  @" + Id.class.getName() + " method or field");
    }

    private String getIdAttrName(Class<?> entityClass) {

        //获取声明的所有字段，包括私有属性
        for (Field field : entityClass.getDeclaredFields()) {

            Object annotation = field.getAnnotation(Id.class);

            if (annotation == null) {
                annotation = field.getAnnotation(EmbeddedId.class);
            }

            if (annotation != null) {
                return field.getName();
            }
        }

        //获取声明的所有方法，包括私有方法
        for (Method method : entityClass.getDeclaredMethods()) {

            Object annotation = method.getAnnotation(Id.class);

            if (annotation == null) {
                annotation = method.getAnnotation(EmbeddedId.class);
            }

            if (annotation != null) {
                //去除get
                String name = method.getName().substring(3);
                //首字母变小写
                name = Character.toLowerCase(name.charAt(0)) + name.substring(1);

                return name;
            }
        }

        return null;
    }

    @Override
    public <T> List<T> find(String statement, Object... paramValues) {
        return find(-1, -1, statement, paramValues);
    }

    @Override
    public <T> List<T> find(int start, int count, String statement, Object... paramValues) {
        return find(false, true, null, start, count, statement, paramValues);
    }

    /**
     * @param isNative    是否是原生查询
     * @param resultClass 可以为null(结果集将返回对象数组)，或是java.util.Map 或是具体的类
     * @param start
     * @param count
     * @param statement
     * @param paramValues 数组中的元素可以是map，数组，或是list,或值对象
     * @return
     */
    @Override
    public <T> List<T> find(boolean isNative, boolean useQueriesCache, Class resultClass, int start, int count, String statement, Object... paramValues) {

        List paramValueList = flattenParams(null, paramValues);

        String oldStatement = statement;

        if (!paramValueList.isEmpty()) {
            statement = replacePlaceholder(isNative, statement);
        }

        if (logger.isDebugEnabled()) {

            // logger.debug("Old Statement:" + oldStatement);

            logger.debug("Select JPQL:[" + statement + "] ResultClass: " + resultClass + " , Param placeholder:" + getParamPlaceholder(isNative)
                    + " , StartIndex: " + getParamStartIndex(isNative) + " , Params:" + paramValueList);
        }

        EntityManager em = getEntityManager();

        //em.setFlushMode(FlushModeType.COMMIT);


        if (em.isJoinedToTransaction()) {
//            em.flush();
        }

        Query query = null;


        //@todo hibernate 5.2.17 对结果类的映射，不支持自定义的类型
        // setResultTransformer 实际使用时无法获取到字段名，也许是 hibernate bug
        // query.unwrap(SQLQuery.class).setResultTransformer(Transformers.ALIAS_TO_ENTITY_MAP);

        if (isNative) {
            query = (resultClass == null) ? em.createNativeQuery(statement) : em.createNativeQuery(statement, resultClass);
        } else {
            query = (resultClass == null) ? em.createQuery(statement) : em.createQuery(statement, resultClass);
        }

        if (!useQueriesCache) {
            query.setHint("javax.persistence.cache.storeMode", CacheStoreMode.BYPASS);
            query.setHint("javax.persistence.cache.retrieveMode", CacheRetrieveMode.BYPASS);
        }

        // query.setFlushMode(FlushModeType.AUTO);

        setParams(getParamStartIndex(isNative), query, paramValueList);

        setRange(query, start, count);

        //临时解决方案
        if (!useQueriesCache || disableSessionCache) {
           // em.clear();
        }

        return (List<T>) query.getResultList();
    }


    @Override
    public String getParamPlaceholder(boolean isNative) {

//        return isNative ? MiniDao.DEFAULT_JDBC_PARAM_PLACEHOLDER : this.paramPlaceholder;

        return this.paramPlaceholder;
    }


    public int getParamStartIndex(boolean isNative) {

        //   return (isNative && paramStartIndex < 1) ? (paramStartIndex + 1) : paramStartIndex;

        boolean isJdbcParamPlaceholder = MiniDao.DEFAULT_JDBC_PARAM_PLACEHOLDER.trim().equals(this.getParamPlaceholder(isNative).trim());


        //如果是？占位符 并且不是原生查询才返回0

        return (isJdbcParamPlaceholder && !isNative) ? 0 : 1;

    }

    /**
     * 尝试使用代理的DAO类
     * <p/>
     * 解决
     *
     * @return
     */

    private transient JpaDao proxyDao = null;

    @Override
    public JpaDao getDao() {

        if (applicationContext != null && proxyDao == null) {
            proxyDao = applicationContext.getBean(JpaDao.class);
        }

        if (proxyDao != null) {
            return proxyDao;
        }

        return this;
    }


    @Override
    public <DAO extends ConditionBuilder> DAO newDao(Class<DAO> daoClass, Object... queryObjs) {


        if (daoClass == null) {
            throw new IllegalArgumentException("daoClass is null");
        }


        if (SelectDao.class.isAssignableFrom(daoClass)) {

            return (DAO) new SelectDaoImpl(getDao(), false).appendByQueryObj(queryObjs);

        } else if (UpdateDao.class.isAssignableFrom(daoClass)) {
            return (DAO) new UpdateDaoImpl(false, getDao()).appendByQueryObj(queryObjs);

        } else if (DeleteDao.class.isAssignableFrom(daoClass)) {
            return (DAO) new DeleteDaoImpl(false, getDao()).appendByQueryObj(queryObjs);
        } else {
            throw new IllegalArgumentException("action  " + daoClass.getName() + " is not support");
        }

    }


    @Override
    public long countByQueryObj(Object... queryObjs) {
        return newDao(SelectDao.class, queryObjs).count();
    }

    @Override
    public <E> List<E> findByQueryObj(Object... queryObjs) {
        return newDao(SelectDao.class, queryObjs).find();
    }

    @Override
    public <E> E findOneByQueryObj(Object... queryObjs) {
        return (E) newDao(SelectDao.class, queryObjs).findOne();
    }

    @Override
    public <E> List<E> findByQueryObj(Class<E> type, Object... queryObjs) {
        return newDao(SelectDao.class, queryObjs).find(type);
    }

    @Override
    public <E> E findOneByQueryObj(Class<E> type, Object... queryObjs) {
        return (E) newDao(SelectDao.class, queryObjs).findOne(type);
    }


    ////////////////////////////////////////////////////////////////////////////////////////////////////

    private Query setRange(Query query, int index, int count) {

        if (index > 0) {
            query.setFirstResult(index);
        }

        if (count > 0) {
            query.setMaxResults(count);
        }

        return query;
    }

    private int setParams(int pIndex, Query query, List paramValueList) {

        Map<String, Parameter> parameterMap = new LinkedHashMap<>();

        Set<Parameter<?>> parameters = query.getParameters();

        //分离出命名参数
        parameters.stream()
                .filter(p -> p.getName() != null)
                .forEach(p -> {
                    parameterMap.put(p.getName(), p);
                });


        for (Object paramValue : paramValueList) {
            if (paramValue instanceof Map) {
                //如果是Map，就设置命名参数
                for (Map.Entry<String, Parameter> entry : parameterMap.entrySet()) {
                    try {
                        //没有属性会抛出异常
                        Object value = ObjectUtil.getIndexValue(paramValue, entry.getKey(), true);

                        query.setParameter(entry.getKey(), tryAutoConvertParamValue(parameterMap, entry.getKey(), value));

                    } catch (Exception e) {

                    }
                }

            } else {

                paramValue = tryAutoConvertParamValue(parameterMap, pIndex, paramValue);

                //尝试使用命名参数
                if (parameterMap.containsKey("" + pIndex)) {
                    query.setParameter("" + pIndex, paramValue);
                } else {
                    query.setParameter(pIndex, paramValue);
                }

                //关键步骤
                pIndex++;
            }
        }

        return pIndex;
    }

    private Object tryAutoConvertParamValue(Map<String, Parameter> parameterMap, Object paramKey, Object paramValue) {
        //自动转换数据类型
        //@todo 观察，需要关注性能问题
        //@todo 数据自动转换，关注 ConditionBuilderImpl.tryToConvertValue

        if (paramValue == null) {
            return paramValue;
        }

        Parameter parameter = parameterMap.get(paramKey);

        if (parameter == null && (paramKey instanceof Number)) {
            parameter = parameterMap.get(paramKey.toString());
        }

        try {
            Class parameterType = parameter != null ? parameter.getParameterType() : null;

            if (parameterType != null && !parameterType.equals(paramValue.getClass())) {
                paramValue = ObjectUtil.convert(paramValue, parameterType);
            }

        } catch (Exception e) {
            logger.warn(" try to convert param [" + paramKey + "] value error: " + ExceptionUtils.getRootCauseInfo(e));
        }


        return paramValue;
    }

    /**
     * 关键方法，把数组或是集合嵌套的参数，按顺序提取出来，抚平
     *
     * @param valueHolder
     * @param paramValues
     * @return
     */
    public List flattenParams(List valueHolder, Object... paramValues) {

        if (valueHolder == null) {
            valueHolder = new ArrayList();
        }

        if (paramValues == null) {
            return valueHolder;
        }

        //扁平化 Map
        Map<String, Object> namedParams = new LinkedHashMap<>();

        for (Object paramValue : paramValues) {
            if (paramValue instanceof Collection) {
                for (Object pv : ((Collection) paramValue)) {
                    flattenParams(valueHolder, pv);
                }
            } else if (paramValue != null && paramValue.getClass().isArray()) {
                int length = Array.getLength(paramValue);
                for (int i = 0; i < length; i++) {
                    flattenParams(valueHolder, Array.get(paramValue, i));
                }
            } else if (paramValue instanceof Map) {
                //扁平化 Map，所有的都合并到同一个 Map
                namedParams.putAll(Map.class.cast(paramValue));
            } else {
                valueHolder.add(paramValue);
            }
        }

        if (namedParams.size() > 0) {
            valueHolder.add(namedParams);
        }

        return valueHolder;
    }

}


