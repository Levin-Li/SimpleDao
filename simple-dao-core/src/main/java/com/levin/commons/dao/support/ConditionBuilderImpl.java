package com.levin.commons.dao.support;


import com.levin.commons.dao.*;
import com.levin.commons.dao.annotation.*;
import com.levin.commons.dao.annotation.logic.AND;
import com.levin.commons.dao.annotation.logic.END;
import com.levin.commons.dao.annotation.logic.OR;
import com.levin.commons.dao.annotation.misc.PrimitiveValue;
import com.levin.commons.dao.annotation.misc.Validator;
import com.levin.commons.dao.annotation.order.OrderByList;
import com.levin.commons.dao.annotation.update.Immutable;
import com.levin.commons.dao.util.ExprUtils;
import com.levin.commons.dao.util.ObjectUtil;
import com.levin.commons.dao.util.QueryAnnotationUtil;
import com.levin.commons.utils.ClassUtils;
import com.levin.commons.utils.MapUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.core.ResolvableType;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.util.ConcurrentReferenceHashMap;
import org.springframework.util.ReflectionUtils;
import org.springframework.util.StringUtils;

import javax.persistence.Column;
import java.lang.annotation.Annotation;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import static com.levin.commons.dao.util.QueryAnnotationUtil.findFirstMatched;
import static com.levin.commons.dao.util.QueryAnnotationUtil.tryGetJpaEntityFieldName;
import static org.springframework.util.StringUtils.hasText;


/**
 * 条件构建器实现
 * 本类是一个非线程安全类，不要重复使用，应该重新创建使用。
 *
 * @param <T>
 * @param <CB>
 */
public abstract class ConditionBuilderImpl<T, CB extends ConditionBuilder>
        implements ConditionBuilder<CB> {

    protected Logger logger = LoggerFactory.getLogger(getClass());

    public static final String DELIMITER = " , ";

    protected javax.validation.Validator validator;

    protected Class<T> entityClass;

    protected String tableName;

    protected String alias;

    final List whereParamValues = new ArrayList(5);

    int rowStart = -1, rowCount = 512;

    private final boolean nativeQL;


    private final ExprNode whereExprRootNode = new ExprNode(AND.class.getSimpleName(), true);

    private Map<String, Object> context;

    protected ParameterNameDiscoverer parameterNameDiscoverer;

    protected final List<TargetOption> targetOptionAnnoList = new ArrayList<>(5);


    public static final String BASE_PACKAGE_NAME = Eq.class.getPackage().getName();

    public static final String LOGIC_PACKAGE_NAME = AND.class.getPackage().getName();


    protected boolean safeMode = true;

    // protected String localParamPlaceholder = null;

    protected boolean filterLogicDeletedData = true;


    /**
     * 实体对象，可空字段缓存
     */
    protected static final Map<String, Boolean> entityClassNullableFields = new ConcurrentReferenceHashMap<>();

    protected ConditionBuilderImpl(boolean isNative) {

        this.nativeQL = isNative;
        this.entityClass = null;
        this.tableName = null;
        this.alias = null;

    }

    public ConditionBuilderImpl(Class<T> entityClass, String alias) {

        this.entityClass = entityClass;
        this.nativeQL = false;
        this.tableName = null;
        this.alias = (alias != null && entityClass != null) ? alias.trim() : null;

        if (entityClass == null) {
            throw new IllegalArgumentException("entityClass is null");
        }

//        if (!entityClass.isAnnotationPresent(Entity.class)) {
//            throw new IllegalArgumentException("entityClass is not a jpa Entity class");
//        }

    }

    public ConditionBuilderImpl(String tableName, String alias) {

        this.tableName = tableName;
        this.entityClass = null;
        this.nativeQL = true;
        this.alias = (alias != null && tableName != null) ? alias.trim() : null;

        if (tableName == null) {
            throw new IllegalArgumentException("tableName is null");
        }
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////

    public ParameterNameDiscoverer getParameterNameDiscoverer() {

        if (parameterNameDiscoverer == null) {
            parameterNameDiscoverer = new MethodParameterNameDiscoverer();
        }

        return parameterNameDiscoverer;
    }

    public CB setParameterNameDiscoverer(ParameterNameDiscoverer parameterNameDiscoverer) {
        this.parameterNameDiscoverer = parameterNameDiscoverer;
        return (CB) this;
    }

    public javax.validation.Validator getValidator() {
        return validator;
    }

    public CB setValidator(javax.validation.Validator validator) {
        this.validator = validator;
        return (CB) this;
    }

    protected EntityOption getEntityOption() {

        //
        return entityClass != null ? entityClass.getAnnotation(EntityOption.class) : null;

        // QueryAnnotationUtil.getEntityOption(entityClass);
    }

    protected void checkAction(EntityOption.Action action, Consumer checkFailCallback) {
        if (isDisable(action)) {
            if (checkFailCallback != null) {
                checkFailCallback.accept(action);
            } else {
                throw new DaoSecurityException(" " + entityClass + " disable " + action + " action");
            }
        }
    }

    protected boolean isDisable(EntityOption.Action... actions) {

        EntityOption entityOption = getEntityOption();

        if (actions == null
                || actions.length < 1
                || entityOption == null
                || entityOption.disableActions() == null
                || entityOption.disableActions().length < 1) {

            //没有禁止就是允许
            return false;
        }

        for (EntityOption.Action allowableAction : entityOption.disableActions()) {
            for (EntityOption.Action action : actions) {
                if (action != null && action.equals(allowableAction)) {
                    return true;
                }
            }
        }

        return false;
    }


    protected boolean hasLogicDeleteField() {

        EntityOption entityOption = getEntityOption();

        return entityOption != null
                && StringUtils.hasText(entityOption.logicalDeleteField())
                && StringUtils.hasText(entityOption.logicalDeleteValue());
    }


    /**
     * 禁止安全模式
     * 在安全模式下，不允许无条件的更新或是删除
     */
    @Override
    public CB disableSafeMode() {
        safeMode = false;
        return (CB) this;
    }


    /**
     * 安全模式
     *
     * @return
     */
    @Override
    public boolean isSafeMode() {
        return safeMode;
    }

    /**
     * 获取参数占位符
     * <p/>
     * 后期考虑支持位置参数
     * 还有命名参数
     *
     * @param name
     * @return
     */
    protected String getParamPlaceholder(String name) {
        return hasText(name) ? ":" + name : getParamPlaceholder();
    }


    @Override
    public CB setContext(Map<String, Object> context) {
        this.context = context;
        return (CB) this;
    }

    @Override
    public boolean isSafeLimit() {
        return rowCount > 0 && rowCount <= getDao().getSafeModeMaxLimit();
    }

    @Override
    public CB and() {
        beginLogic(AND.class.getSimpleName(), true);
        return (CB) this;
    }

    @Override
    public CB or() {
        beginLogic(OR.class.getSimpleName(), true);
        return (CB) this;
    }

    @Override
    public CB end() {
        endLogic();
        return (CB) this;
    }

    @Override
    public CB limit(int rowStartPosition, int rowCount) {
        this.rowStart = rowStartPosition;
        this.rowCount = rowCount;
        return (CB) this;
    }

    /**
     * 设置查询的分页
     *
     * @param pageIndex 第几页，从1开始
     * @param pageSize  分页大小
     * @return
     */
    @Override
    public CB page(int pageIndex, int pageSize) {


        if (pageIndex < 1) {
            pageIndex = 1;
        }

        this.rowStart = (pageIndex - 1) * pageSize;


        this.rowCount = pageSize;

        return (CB) this;
    }

    @Override
    public CB page(Paging paging) {

        if (paging != null) {
            page(paging.getPageIndex(), paging.getPageSize());
        }

        return (CB) this;
    }

    @Override
    public CB filterLogicDeletedData(boolean enable) {

        filterLogicDeletedData = enable;

        return (CB) this;
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////
    @Override
    public CB where(String whereStatement, Object... paramValues) {

        return this.where(true, whereStatement, paramValues);

    }

    @Override
    public CB where(Boolean isAppend, String conditionExpr, Object... paramValues) {

        if (Boolean.TRUE.equals(isAppend)) {
            appendToWhere(conditionExpr, paramValues, false);
        }

        return (CB) this;
    }

    @Override
    public CB appendByQueryObj(Object... queryObjs) {

        walkObject(queryObjs);

        return (CB) this;
    }


    @Override
    public CB appendByMethodParams(Object bean, Method method, Object... args) {

        walkMethod(bean, method, args);

        return (CB) this;
    }

    /**
     * 按文本表达式构建查询条件
     * <p/>
     * 如：属性名Q_Not_Like_name  值 llw，表f示会生成查询条件 name not like '%llw%'
     * 注意时间的文本表达式："2016/07/16 23:59:07"
     * <p/>
     * <p/>
     * param.put("Q_name", "llw");
     * param.put("nickName", "llw");
     * param.put("Q_Like_name", "llw");
     * param.put("Q_Gt_date1", new Date());
     * param.put("Q_Lt_date2", new Date());
     * param.put("Q_Gte_date3", new Date());
     * param.put("Q_Lte_date4", new Date());
     * param.put("Q_Not_gt_date5", new Date());
     * param.put("Q_NotLike_date6", new Date());
     * param.put("Q_NotEq_date7", new Date());
     * <p/>
     * param.put("Q_NotNull_date8","2016/07/16 23:59:07");
     * param.put("Q_NotLike_date9", new Date());
     * <p/>
     * <p/>
     * param.put("Q_NotLike_", "llw");
     * param.put("Q_name1", "llw");
     * param.put("Q_Not_Contains_name2", "llw");
     * param.put("Q_StartsWith_name3", "llw");
     * <p/>
     * param.put("Q_Not_EndsWith_name5", "llw");
     * param.put("name6", "llw");
     *
     * @param paramPrefix ，如果Q_
     * @param queryParams
     * @return
     */
    @Override
    public CB appendByEL(String paramPrefix, Map<String, Object>... queryParams) {

        walkMap(paramPrefix, queryParams);

        return (CB) this;
    }


    @Override
    public CB appendByAnnotations(Boolean isAppend, @javax.validation.constraints.NotNull String attrName, Object attrValue, Class<? extends Annotation>... annoTypes) {

        if (Boolean.TRUE.equals(isAppend)) {
            processAttr(null, null, attrName, QueryAnnotationUtil.getAnnotations(annoTypes), null, attrValue);
        }

        return (CB) this;
    }


    private CB processAnno(int callMethodDeep, String expr, Object value) {

//        if (!StringUtils.hasText(expr)) {
//            throw new IllegalArgumentException("expr has no content");
//        }

        String methodName = new Exception().getStackTrace()[callMethodDeep].getMethodName();

        methodName = Character.toUpperCase(methodName.charAt(0)) + methodName.substring(1);

        Annotation annotation = QueryAnnotationUtil.getAllAnnotations().get(methodName);

        if (annotation == null) {
            throw new IllegalArgumentException("Annotation " + methodName + " not found");
        }

        if (annotation instanceof IsNotNull
                || annotation instanceof IsNull
                || !isNullOrEmptyTxt(value)) {

            processWhereCondition(null, null, expr, value, null, annotation);

        }

        return (CB) this;
    }

    /**
     * is null
     *
     * @param entityAttrName 如 name
     * @return
     */
    @Override
    public CB isNull(String entityAttrName) {
        return processAnno(2, entityAttrName, null);
    }

    /**
     * is not null
     *
     * @param entityAttrName 如 name
     * @return
     */
    @Override
    public CB isNotNull(String entityAttrName) {
        return processAnno(2, entityAttrName, null);
    }


    @Override
    public CB isNullOrEq(String entityAttrName, Object paramValue) {
        appendByAnnotations(true, entityAttrName, paramValue, OR.class, IsNull.class, Eq.class, END.class);
        return (CB) this;
    }

    /**
     * =
     * eg：appendWhereEquals("name","echo") 表示 and name = 'echo'
     *
     * @param entityAttrName 如 name
     * @param paramValue     如果值为null ，将不加入查询条件
     * @return
     */
    @Override
    public CB eq(String entityAttrName, Object paramValue) {
        return processAnno(2, entityAttrName, paramValue);
    }

    /**
     * !=
     *
     * @param entityAttrName 如 name
     * @param paramValue     如果值为null ，将不加入查询条件
     * @return
     */
    @Override
    public CB notEq(String entityAttrName, Object paramValue) {
        return processAnno(2, entityAttrName, paramValue);
    }

    /**
     * >
     *
     * @param entityAttrName 如 name
     * @param paramValue     如果值为null ，将不加入查询条件
     * @return
     */
    @Override
    public CB gt(String entityAttrName, Object paramValue) {
        return processAnno(2, entityAttrName, paramValue);
    }

    /**
     * <
     *
     * @param entityAttrName 如 name
     * @param paramValue     如果值为null ，将不加入查询条件
     * @return
     */
    @Override
    public CB lt(String entityAttrName, Object paramValue) {
        return processAnno(2, entityAttrName, paramValue);
    }

    /**
     * >=
     *
     * @param entityAttrName 如 name
     * @param paramValue     如果值为null ，将不加入查询条件
     * @return
     */
    @Override
    public CB gte(String entityAttrName, Object paramValue) {
        return processAnno(2, entityAttrName, paramValue);
    }

    /**
     * <=
     *
     * @param entityAttrName 如 name
     * @param paramValue     如果值为null ，将不加入查询条件
     * @return
     */
    @Override
    public CB lte(String entityAttrName, Object paramValue) {
        return processAnno(2, entityAttrName, paramValue);
    }

    /**
     * field between ? and ? and ?
     * or
     * field >= ?
     *
     * @param entityAttrName
     * @param paramValues
     * @return
     */
    @Override
    public CB between(String entityAttrName, Object... paramValues) {
        return processAnno(2, entityAttrName, paramValues);
    }

    /**
     * field in (?...)
     *
     * @param entityAttrName
     * @param paramValues
     * @return
     */
    @Override
    public CB in(String entityAttrName, Object... paramValues) {
        return processAnno(2, entityAttrName, paramValues);
    }

    /**
     * field in (?...)
     *
     * @param entityAttrName
     * @param paramValues
     * @return
     */
    @Override
    public CB notIn(String entityAttrName, Object... paramValues) {
        return processAnno(2, entityAttrName, paramValues);
    }


    /**
     * exist (sub query)
     *
     * @param exprOrQueryObj
     * @return
     */
    @Override
    public CB exists(Object exprOrQueryObj) {
        return processAnno(2, "", exprOrQueryObj);
    }

    /**
     * not exist (sub query)
     * <p>
     * * @param exprOrQueryObj
     *
     * @return
     */
    @Override
    public CB notExists(Object exprOrQueryObj) {
        return processAnno(2, "", exprOrQueryObj);
    }

    /**
     * like %keyword%
     *
     * @param entityAttrName
     * @param keyword
     * @return
     */
    @Override
    public CB contains(String entityAttrName, String keyword) {
        return processAnno(2, entityAttrName, keyword);
    }

    /**
     * like keyword%
     *
     * @param entityAttrName
     * @param keyword
     * @return
     */
    @Override
    public CB startsWith(String entityAttrName, String keyword) {
        return processAnno(2, entityAttrName, keyword);
    }

    /**
     * like %keyword
     *
     * @param entityAttrName
     * @param keyword
     * @return
     */
    @Override
    public CB endsWith(String entityAttrName, String keyword) {
        return processAnno(2, entityAttrName, keyword);
    }


    public int getRowStart() {
        return rowStart;
    }

    public void setRowStart(int rowStart) {
        this.rowStart = rowStart;
    }

    public int getRowCount() {
        return rowCount;
    }

    public void setRowCount(int rowCount) {
        this.rowCount = rowCount;
    }

    ////////////////////////////////////////////////////////////////////////////////////

    /**
     * 是否有有效的查询实体
     *
     * @return
     */
    protected boolean hasValidQueryEntity() {
        return (entityClass != null && entityClass != Void.class) || hasText(tableName);
    }

    protected CB setTargetOption(Object hostObj, TargetOption targetOption) {


        if (targetOption == null) {
            return (CB) this;
        }

        //重复的不再处理
        if (targetOptionAnnoList.contains(targetOption)) {
            return (CB) this;
        }

        targetOptionAnnoList.add(targetOption);

        if (hasValidQueryEntity()) {
            return (CB) this;
        }

        //使用 join 语句的方式增加连接语句
        String joinStatement = ExprUtils.genJoinStatement(getDao(), targetOption.entityClass(), targetOption.tableName(), targetOption.alias(), targetOption.joinOptions());
        if (hasText(joinStatement)) {
            join(true, joinStatement);
        }

        this.entityClass = targetOption.entityClass();

        this.tableName = targetOption.tableName();

        this.alias = targetOption.alias();

        //如果是第一个
        this.safeMode = targetOption.isSafeMode();

        if (hasText(targetOption.fromStatement())) {
            setFromStatement(targetOption.fromStatement());
        }

        //  this.where(targetOption.fixedCondition());

        //设置limit，如果原来没有设置

        if (this.rowCount < 1
                && targetOption.maxResults() > 0) {
            this.rowCount = targetOption.maxResults();
        }

        return (CB) this;

    }


    protected CB setQueryOption(Object... queryObjs) {

        if (hasValidQueryEntity()) {
            return (CB) this;
        }

        Optional.ofNullable(queryObjs).ifPresent((Object[] objs) -> {
            Arrays.stream(queryObjs)
                    .filter(o -> o instanceof QueryOption)
                    .map(o -> (QueryOption) o).forEach(queryOption -> {

                if (hasValidQueryEntity()) {
                    return;
                }

                entityClass = queryOption.getEntityClass();
                tableName = queryOption.getEntityName();
                alias = queryOption.getAlias();

                if (hasValidQueryEntity()) {
                    String joinStatement = ExprUtils.genJoinStatement(getDao(), entityClass, tableName, alias, queryOption.getJoinOptions());
                    if (hasText(joinStatement)) {
                        join(true, joinStatement);
                    }
                }

            });
        });


        return (CB) this;
    }


    protected CB setPaging(Object queryObj) {

        //如果不是分页对象
        if ((queryObj instanceof Paging)) {
            page(Paging.class.cast(queryObj));
        }

        return (CB) this;
    }

    protected void setFromStatement(String fromStatement) {
        //  throw new UnsupportedOperationException(getClass().getName() + " setFromStatement");
    }

    protected CB join(Boolean isAppend, String... joinStatements) {
//        throw new StatementBuildException("Only SelectDao support this operation");
        return (CB) this;
    }

//////////////////////////////////////////////

    protected void beforeWalkMethod(Object bean, Method method, Object[] args) {

        //优先
        setQueryOption(args);

        //参数
        int i = 0;

        for (Annotation[] annotations : method.getParameterAnnotations()) {

            for (Annotation annotation : annotations) {
                if (annotation instanceof TargetOption) {
                    setTargetOption(args[i], (TargetOption) annotation);
                }
            }

            i++;
        }

        //先设置方法上的注解
        setTargetOption(null, method.getAnnotation(TargetOption.class));
        setTargetOption(null, method.getDeclaringClass().getAnnotation(TargetOption.class));

    }


    protected void afterWalkMethod(Object bean, Method method, Object[] args) {

    }

    protected void walkMethod(Object bean, Method method, Object[] args) {

        //如果是忽略的方法
        if (method.getAnnotation(Ignore.class) != null) {
            return;
        }

        Class<?>[] parameterTypes = method.getParameterTypes();

        String[] parameterNames = getParameterNameDiscoverer().getParameterNames(method);

        if (parameterNames == null || parameterTypes == null
                || parameterNames.length != parameterTypes.length) {
            throw new IllegalStateException("method [" + method + "] can't get param name");
        }

        for (String parameterName : parameterNames) {
            if (parameterName == null || !hasText(parameterName)) {
                throw new IllegalStateException("method [" + method + "] can't get param name");
            }
        }

        Annotation[][] parameterAnnotations = method.getParameterAnnotations();

        beforeWalkMethod(bean, method, args);

        for (int i = 0; i < parameterTypes.length; i++) {

            String pName = parameterNames[i];

            Annotation[] varAnnotations = parameterAnnotations[i];
            Class<?> pType = ResolvableType.forMethodParameter(method, i).resolve(parameterTypes[i]);
            Object value = args[i];

            processAttr(bean, method, pName, varAnnotations, pType, value);

        }

        //之前之后
        afterWalkMethod(bean, method, args);

    }

    /**
     * 解析对象所有的属性，过滤并调用回调
     *
     * @param queryObjs
     */
    public void walkObject(Object... queryObjs) {
        walkObject(null, queryObjs);
    }


    /**
     * 展开嵌套对象
     *
     * @param resultList
     * @param queryObjs
     * @return
     */
    protected static List<Object> expand(List resultList, Object... queryObjs) {

        if (resultList == null) {
            resultList = new ArrayList();
        }

        if (queryObjs == null) {
            return resultList;
        }

        for (Object queryObj : queryObjs) {

            if (queryObj == null) {
                continue;
            }

            //如果是类，则实例化
            if (queryObj instanceof Class) {
                queryObj = BeanUtils.instantiateClass((Class<? extends Object>) queryObj);
            }

            if (queryObj.getClass().isArray()) {
                expand(resultList, (Object[]) queryObj);
            } else {
                resultList.add(queryObj);
            }

        }

        return resultList;
    }

    /**
     * 解析对象所有的属性，过滤并调用回调
     *
     * @param queryObjs
     */
    public void walkObject(AttrCallback attrCallback, Object... queryObjs) {

        if (queryObjs == null) {
            return;
        }

        List<Object> expand = expand(new ArrayList(queryObjs.length), queryObjs);

        queryObjs = expand.toArray();

        setQueryOption(queryObjs);

        for (Object queryValueObj : queryObjs) {

            if (queryValueObj == null) {
                continue;
            }

            Class<?> typeClass = queryValueObj.getClass();

            if (typeClass.isPrimitive()
                    || QueryAnnotationUtil.isRootObjectType(typeClass)
                    || QueryAnnotationUtil.isArray(typeClass)
                    || QueryAnnotationUtil.isIgnore(typeClass)
                    || typeClass.isAnnotation()
            ) {
                continue;
            }

            //没有回调时，表示本地调用
            if (attrCallback == null) {
                //尝试设置分页
                setPaging(queryValueObj);

                //尝试设置查询目标实体
                setTargetOption(queryValueObj, typeClass.getAnnotation(TargetOption.class));
            }

            //特别处理
            if (queryValueObj instanceof Map) {
                walkMap("", (Map) queryValueObj);
                continue;
            }

            List<Field> fields = QueryAnnotationUtil.getCacheFields(typeClass);

            ResolvableType rootType = ResolvableType.forType(typeClass);

            //开始处理字段
            for (Field field : fields) {

                //忽略字段
                if (QueryAnnotationUtil.isIgnore(field)) {
                    continue;
                }

                ResolvableType fieldRT = ResolvableType.forField(field, rootType);


                Class<?> fieldRealType = fieldRT.resolve(field.getType());

                field.setAccessible(true);

                try {
                    if (attrCallback != null) {

                        boolean isContinue = attrCallback.onAction(queryValueObj
                                , field, field.getName(), field.getAnnotations()
                                , fieldRealType,
                                field.get(queryValueObj));

                        //如果不再处理，则跳出字段处理
                        if (!isContinue) {
                            break;
                        }

                    } else {
                        processAttr(queryValueObj
                                , field, field.getName(), field.getAnnotations()
                                , fieldRealType,
                                field.get(queryValueObj));
                    }
                } catch (Exception e) {
                    throw new StatementBuildException(typeClass + " 处理注解失败，字段:" + field + "", e);
                }
            }


            //拷贝对象的字段，可能会被作为命名的查询参数

            whereParamValues.add(QueryAnnotationUtil.copyMap(true, null, ObjectUtil.copyField2Map(queryValueObj, null)));

        }
    }

    /**
     * 参数的属性名称中带有查询注释说明
     * <p/>
     * 如：属性名Q_Not_Like_name  值 llw，表f示会生成查询条件 name not like '%llw%'
     *
     * @param paramPrefix
     * @param queryParams
     * @return
     */

    public void walkMap(String paramPrefix, Map<String, Object>... queryParams) {

        if (queryParams == null)
            return;

        boolean hasPrefix = hasText(paramPrefix);

        Map<String, Annotation> annotationMap = QueryAnnotationUtil.getAllAnnotations();

        final String notPrefix = Op.Not.name() + "_";

        for (Map<String, Object> queryParam : queryParams) {

            for (Map.Entry<String, Object> entry : queryParam.entrySet()) {

                String name = entry.getKey();

                final String oldExpr = name;

                Object paramValue = entry.getValue();

                if (hasPrefix) {
                    //如果不是有效的属性，则忽略
                    if (!name.startsWith(paramPrefix))
                        continue;
                    //去除前缀
                    name = name.substring(paramPrefix.length());
                }

                Op notOp = null;

                //是否包括非的操作
                if (name.startsWith(notPrefix)) {
                    notOp = Op.Not;
                    //去除Not操作前缀
                    name = name.substring((notPrefix).length());
                }

                //默认是等于的操作
                Annotation opAnno = null;

                int idx = name.indexOf("_");

                if (idx != -1) {

                    opAnno = annotationMap.get(name.substring(0, idx));

                }

                if (opAnno != null) {
                    //去除比较操作前缀
                    if (name.length() > idx + 1) {
                        name = name.substring(idx + 1);
                    } else {
                        //logger.trace("");
                        continue;
                    }
                } else {
                    //默认是等于的操作
                    opAnno = annotationMap.get(Eq.class.getSimpleName());
                }

                //如果属性名为null
                if (!hasText(name)) {
                    continue;
                }

                //如果是忽略的条件
                if (opAnno instanceof Ignore) {
                    continue;
                }

                if (notOp != null && opAnno != null) {
                    //@todo 对 Not 没有处理
                    //@Fix
                    // varAnnotations[1] = notOp;

                    Map<String, Object> attributes = AnnotationUtils.getAnnotationAttributes(opAnno);

                    attributes.put(E_C.not, true);

                    opAnno = AnnotationUtils.synthesizeAnnotation(attributes, opAnno.annotationType(), null);

                    // throw new UnsupportedOperationException(oldExpr);
                }

                Annotation[] varAnnotations = {opAnno};

                processAttr(queryParam, null, name, varAnnotations,
                        paramValue != null ? paramValue.getClass() : null, paramValue);

            }
        }
    }


    /**
     * 处理单个属性或是方法参数的注解
     * <p>
     * <p>
     * 单个属性，多个注解的处理入口
     * <p>
     * 对象，方法，Map 的 walk 都是这个入口
     * <p>
     * 包含逻辑处理
     *
     * @param bean
     * @param fieldOrMethod
     * @param name
     * @param varAnnotations
     * @param attrType
     * @param value
     */
    public void processAttr(Object bean, Object fieldOrMethod, String name, Annotation[] varAnnotations, Class<?> attrType, Object value) {

        //如果是包括忽略注解，则直接忽略
        if (isIgnore(varAnnotations)) {
            return;
        }

        //校验数据
        verifyGroupValidation(bean, name, value, findFirstMatched(varAnnotations, Validator.class));


        //支持多个注解
        List<Annotation> logicAnnotations = QueryAnnotationUtil.getLogicAnnotation(name, varAnnotations);


        logicAnnotations.stream()
                .forEach(logicAnnotation -> beginLogicGroup(bean, logicAnnotation, name, value));
        //可以多次逻辑组

        try {

            //如果是忽略的类型
            if (attrType != null && attrType.getAnnotation(Ignore.class) != null) {
                return;
            }

            //当前节点是否有效
            if (!whereExprRootNode.currentNode().isValid()) {
                return;
            }

            processAttr(bean, fieldOrMethod, varAnnotations, name, attrType, value);

        } finally {

            //部分自动关闭
            logicAnnotations.stream()
                    .filter(this::isLogicGroupAutoClose)
                    .forEach(logicAnnotation -> end());

            endLogicGroup(bean, findFirstMatched(varAnnotations, END.class), value);

        }

        //结束逻辑分组
    }

    boolean isLogicGroupAutoClose(Annotation logicAnnotation) {

        return Optional.ofNullable(logicAnnotation)
                .map(a -> (boolean) ClassUtils.getValue(logicAnnotation, "autoClose", true))
                .orElse(false);

    }

    /**
     * 重要方法
     * <p/>
     * 过滤出需要处理的注解
     * <p/>
     * 被过滤的注解包括 Ignore、Having、Not 和 所有的逻辑注解
     *
     * @param fieldOrMethod
     * @param varAnnotations
     * @return
     */
    private static List<Annotation> findNeedProcessDaoAnnotations(Object fieldOrMethod, Annotation[] varAnnotations) {

        //@todo 缓存字段

        List<Annotation> result = new ArrayList<>(5);

        if (varAnnotations != null) {
            for (Annotation annotation : varAnnotations) {

                if (annotation == null
                        || annotation instanceof PrimitiveValue
                        || annotation instanceof Validator
                        || annotation instanceof Immutable
                        || annotation instanceof Ignore) {
                    continue;
                }


                String clsName = annotation.annotationType().getName();

                //如果注解的类是在这"com.levin.commons.dao.annotation" 包下，并且不是逻辑操作注解
                //特别关键的判断条件

                if (clsName.startsWith(BASE_PACKAGE_NAME)
                        && !clsName.startsWith(LOGIC_PACKAGE_NAME)) {
                    result.add(annotation);
                }
            }
        }

        return result;
    }


    /**
     * 处理原子属性
     * 关键处理方法
     * <p/>
     * 对每一个字段，对每一个注解循环处理
     *
     * @param bean
     * @param fieldOrMethod
     * @param varAnnotations
     * @param name
     * @param varType
     * @param value
     */
    protected void processAttr(Object bean, Object fieldOrMethod, Annotation[] varAnnotations, String name, Class<?> varType, Object value) {

//        if (isIgnore(varAnnotations)) {
//            return;
//        }

        List<Annotation> daoAnnotations = new ArrayList<>(5);

        for (Annotation annotation : findNeedProcessDaoAnnotations(fieldOrMethod, varAnnotations)) {

            if (annotation instanceof CList) {

                if (isValid(annotation, bean, name, value)) {
                    daoAnnotations.addAll(Arrays.asList(((CList) annotation).value()));
                }

            } else if (annotation instanceof OrderByList) {

                if (isValid(annotation, bean, name, value)) {
                    daoAnnotations.addAll(Arrays.asList(((OrderByList) annotation).value()));
                }

            } else {
                daoAnnotations.add(annotation);
            }

        }


        //如果没有注解
        if (daoAnnotations.size() == 0) {

            //如果字段上没有需要处理的注解
            //默认为 EQ

            boolean complexType = (findPrimitiveValue(varAnnotations) == null) && isComplexType(varType, value);


            if ((!complexType) && !isNullOrEmptyTxt(value)) {
                //如果没有注解，不是复杂类型，则默认为等于查询
                processAttrAnno(bean, fieldOrMethod, varAnnotations, name, varType, value, QueryAnnotationUtil.getAnnotation(Eq.class));
            } else {

                //如果不是
                boolean isIterable = value instanceof Iterable || value instanceof Map;

                //@todo 数据类型的支持

                //如果是注解的复杂对象
                if (complexType && !isNullOrEmptyTxt(value) && !isIterable) {
                    reAppendByQueryObj(value);
                } else if (complexType) {
                    logger.debug("fieldOrMethod:" + fieldOrMethod + " , name:" + name + " discard.");
                }
            }


        } else {
            daoAnnotations.stream()
                    .filter(annotation -> isValid(annotation, bean, name, value))
                    .forEach(annotation -> {
                        processAttrAnno(bean, fieldOrMethod, varAnnotations,
                                tryGetJpaEntityFieldName(annotation, this.entityClass, name),
                                varType, value, annotation);
                    });
        }

    }

    protected boolean isPackageStartsWith(String packageName, Annotation opAnnotation) {
        return opAnnotation != null && opAnnotation.annotationType().getName().startsWith(packageName);
    }

    /**
     * 单个属性处理方法，非常重要
     *
     * <p>
     * 默认只处理查询条件
     *
     * @param bean
     * @param fieldOrMethod
     * @param varAnnotations
     * @param name
     * @param varType
     * @param value
     * @param opAnnotation
     * @return 是否继续处理，true继续.false则停止
     */
    public void processAttrAnno(Object bean, Object fieldOrMethod, Annotation[] varAnnotations, String name, Class<?> varType, Object value, Annotation opAnnotation) {

        //如果不是条件注解则忽略
        //但是允许空opAnnotation为 null，往下走
        //支持处理 where 条件的注解

//        if (isIgnore(varAnnotations)) {
//            return;
//        }

        if (QueryAnnotationUtil.isSamePackage(opAnnotation, Eq.class)) {
            //处理where条件
            processWhereCondition(bean, varType, name, value, findPrimitiveValue(varAnnotations), opAnnotation);
        }

    }


    protected PrimitiveValue findPrimitiveValue(Annotation... varAnnotations) {
        return findFirstMatched(varAnnotations, PrimitiveValue.class);
    }

    public boolean isIgnore(Annotation[] varAnnotations) {
        return findFirstMatched(varAnnotations, Ignore.class) != null;
    }


    /**
     * 验证查询对象是否满足要求
     *
     * @param bean
     * @param name
     * @param value
     * @param validator
     */
    protected void verifyGroupValidation(Object bean, String name, Object value, Validator validator) {

        if (validator != null
                && hasContent(validator.expr())) {
            //如果验证识别
            if (!Boolean.TRUE.equals(evalExpr(bean, value, name, validator.expr()))) {
                throw new StatementBuildException(bean.getClass() + " group verify fail: "
                        + validator.promptInfo() + " on field " + name, validator.promptInfo());
            }

        }

    }


    //////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    private void appendToWhere(String expr, Object value, boolean addToFirst) {

        if (hasText(expr)
                && whereExprRootNode.currentNode().add(addToFirst, expr)) {
            if (addToFirst) {
                whereParamValues.add(0, value);
            } else {
                whereParamValues.add(value);
            }
        }
    }


    /**
     * 通过简单的注解添加条件
     * 主要为 SimpleConditionBuilder 提供服务
     *
     * @param annotationType
     * @param name
     * @param value
     */
    protected void add(Class annotationType, String name, Object value) {

        if (annotationType == IsNotNull.class
                || annotationType == IsNull.class
                || !isNullOrEmptyTxt(value)) {

            processWhereCondition(null, null, name, value, null, QueryAnnotationUtil.getAnnotation(annotationType));

        }

    }


    /**
     * @param value
     * @return
     */
    private boolean isNullOrEmptyTxt(Object value) {
        return value == null
                || (value instanceof CharSequence && !hasText((CharSequence) value));
    }


    /**
     * 关键方法
     * <p>
     * 如果是数组，必须要求不存在原子元素，并且不为空数组，并且元素不都是Null
     *
     * @param varType
     * @param value
     * @return
     */
    protected boolean isComplexType(Class<?> varType, Object value) {

        if (varType == null && value != null) {

            // 是数组并且有原子元素
            if (QueryAnnotationUtil.isArrayAndExistPrimitiveElement(value)) {
                return false;
            }

            varType = value.getClass();
        }

        return varType != null
                && varType.getAnnotation(PrimitiveValue.class) == null
                && !QueryAnnotationUtil.isPrimitive(varType);
    }


    protected CB having(String expr, Object... paramValues) {
        throw new UnsupportedOperationException("appendHaving [" + expr + "]");
    }


    /**
     * 处理 where 条件
     *
     * @param bean
     * @param varType
     * @param name
     * @param value
     * @param primitiveValue
     * @param opAnnotation
     */
    protected void processWhereCondition(Object bean, Class<?> varType, String name, Object value,
                                         PrimitiveValue primitiveValue, Annotation opAnnotation) {

        genExprAndProcess(bean, varType, name, value, primitiveValue, opAnnotation, (expr, holder) -> {

            Boolean having = ClassUtils.getValue(opAnnotation, E_C.having, false);

            //变成 having 字句，只针对 SelectDao 有效
            if (Boolean.TRUE.equals(having) && this instanceof SelectDao) {
                having(expr, holder.value);
            } else {
                where(expr, holder.value);
            }

        });

    }


    public void genExprAndProcess(Object bean, Class<?> varType, String name, Object value,
                                  PrimitiveValue primitiveValue, Annotation opAnnotation,
                                  BiConsumer<String, ValueHolder<? extends Object>> consumer) {

        boolean complexType = (primitiveValue == null) && isComplexType(varType, value);

        ValueHolder<Object> holder = new ValueHolder<>(bean, name, value);

        String expr = genConditionExpr(complexType, opAnnotation, name, holder);

        consumer.accept(expr, holder);

    }


    /**
     * 递归处理
     *
     * @param queryObj
     */
    private void reAppendByQueryObj(Object queryObj) {
        if (queryObj != null) {
            if (queryObj.getClass().isArray()) {
                appendByQueryObj((Object[]) queryObj);
            } else {
                appendByQueryObj(queryObj);
            }
        }
    }


    /**
     * @param holder
     * @return
     */
    protected String buildSubQuery(ValueHolder holder) {
        return ExprUtils.buildSubQuery(holder, getDao(), isNative(), this.context);
    }


    /**
     * 关键方法，根据注解生成SQL语句
     *
     * @param complexType
     * @param opAnno
     * @param name
     * @param holder
     * @return
     */
    private String genConditionExpr(boolean complexType, Annotation opAnno, String name, ValueHolder holder) {


        C c = null;

        if (opAnno instanceof C) {
            c = (C) opAnno;
        }

        if (c == null) {

            //把其它注解转换为 C 注解

            Map<String, Object> attributes = AnnotationUtils.getAnnotationAttributes(opAnno);

            if (!attributes.containsKey(E_C.op)) {
                attributes.put(E_C.op, Op.valueOf(opAnno.annotationType().getSimpleName()));
            }

            if (!attributes.containsKey(E_C.having)) {
                attributes.put(E_C.having, false);
            }

            if (!attributes.containsKey(E_C.not)) {
                attributes.put(E_C.not, false);
            }

            if (attributes.get(E_C.op) == null) {
                throw new StatementBuildException(opAnno + " not define Op");
            }

            c = AnnotationUtils.synthesizeAnnotation(attributes, C.class, null);

        }

        return ExprUtils.genExpr(c, aroundColumnPrefix(c.domain(), name), complexType, getExpectType(name), holder, getParamPlaceholder(),
                this::buildSubQuery
                , buildContextValues(holder.root, holder.value, name));
    }


    //////////////////////////////////////////////////////////////////////////////////////////////

    @Deprecated
    protected boolean hasContent(String text) {
        return hasText(text);
    }

    protected String getText(String text, String prefix, String suffix, String defaultV) {
        return hasText(text) ? prefix + text + suffix : defaultV;
    }

    protected String getText(String text, String defaultV) {
        return hasText(text) ? text : defaultV;
    }

    /**
     * 是否不允许空
     *
     * @param propertyName
     * @return
     */
    private boolean isNullable(String propertyName) {

        String key = entityClass.getName() + "." + propertyName;

        Boolean aBoolean = entityClassNullableFields.get(key);

        if (aBoolean == null) {

            Field field = ReflectionUtils.findField(entityClass, propertyName);

            if (field == null) {
                throw new RuntimeException(new NoSuchFieldException(key));
            }

            Column column = field.getAnnotation(Column.class);

            aBoolean = column == null || column.nullable();

            entityClassNullableFields.put(key, aBoolean);
        }

        return aBoolean;
    }


    protected void checkSafeMode(String whereStatement) {

        if (this.isSafeMode()
                && (!isSafeLimit() || !hasText(whereStatement))) {

            //默认2个条件都要满足，要有查询条件，也要有数量限制

            //如果超出安全模式的限制
            throw new DaoSecurityException("dao safe mode no allow no where statement"
                    + "or no limit or limit over " + getDao().getSafeModeMaxLimit());
        }
    }

    /**
     * 获取查询条件
     *
     * @param action 动作
     * @return
     */
    protected final String genWhereStatement(EntityOption.Action action) {

        if (isDisable(action)) {
            throw new DaoSecurityException(" " + entityClass + " disable " + action + " action");
        }

        String whereStatement = getText(whereExprRootNode.toString(), " Where ", " ", " ");

        checkSafeMode(whereStatement);

        //如果过滤逻辑删除的数据
        if (filterLogicDeletedData
                && hasLogicDeleteField()) {

            EntityOption entityOption = getEntityOption();

            String expr = genLogicDeleteExpr(entityOption, Op.NotEq);

            String propertyName = entityOption.logicalDeleteField().trim();

            if (isNullable(propertyName)) {
                expr = " (  " + aroundColumnPrefix(propertyName) + " IS NULL OR " + expr + " ) ";
            }

            whereExprRootNode.switchCurrentNodeToSelf();

            appendToWhere(expr, convertLogicDeleteValue(entityOption), true);

            //重新生成 where
            whereStatement = getText(whereExprRootNode.toString(), " Where ", " ", " ");
        }

        return whereStatement;
    }

    protected String genLogicDeleteExpr(EntityOption entityOption, Op op) {
        return aroundColumnPrefix(entityOption.logicalDeleteField().trim()) + " " + op.getOperator() + " " + getParamPlaceholder();
    }

    protected Object convertLogicDeleteValue(EntityOption entityOption) {
        return ObjectUtil.convert(entityOption.logicalDeleteValue().trim(), QueryAnnotationUtil.getFieldType(entityClass, entityOption.logicalDeleteField().trim()));
    }

    protected Object tryConvertPropertyValue(String name, String value) {
        return ObjectUtil.convert(value, QueryAnnotationUtil.getFieldType(entityClass, name));
    }


    protected String getParamPlaceholder() {
//        return localParamPlaceholder != null ? localParamPlaceholder : getDao().getParamPlaceholder(isNative());
        return getDao().getParamPlaceholder(isNative());
    }

    protected abstract MiniDao getDao();

    /**
     * @return
     */
    protected boolean isNative() {
        return this.nativeQL;
    }

    protected String genEntityStatement() {

        if (hasText(this.tableName)) {
            return tableName + " " + getText(alias, " ");
        } else if (entityClass != null) {
            return entityClass.getName() + " " + getText(alias, " ");
        }

        throw new IllegalArgumentException("entityClass or tableName is null");
    }

    protected String aroundColumnPrefix(String column) {
        return aroundColumnPrefix(null, column);
    }

    protected String aroundColumnPrefix(String domain, String column) {

        if (!hasText(column)) {
            return "";
        }

        boolean hasDomain = hasText(domain);

        //如果包含占位符，则直接返回
        //@fix bug 20200227
        if (column.contains(getParamPlaceholder().trim())
                || !Character.isLetter(column.trim().charAt(0))
                || StringUtils.containsWhitespace(column.trim()) //包含白空格
                || (!hasDomain && column.contains("."))) {
            return column;
        }

        if (!hasDomain) {
            domain = alias;
        }

        // :?P

        String prefix = getText(domain, "", ".", "");

        return column.trim().startsWith(prefix) ? column : prefix + column;
    }

    protected String genFromStatement() {
        return " From " + genEntityStatement();
    }

    /**
     * 返回当前节点是否有效
     * 注意，如果注解为null，则默认为有效
     *
     * @param anno
     * @param root
     * @param value @return
     */
    protected boolean isValid(Annotation anno, Object root, String name, Object value) {

        //如果没有注解
        if (anno == null) {
            return true;
        }

        String conditionExpr = ClassUtils.getValue(anno, E_C.condition, false);

        //如果没有内容默认为true
        if (!hasText(conditionExpr)) {
            return true;
        }

        boolean isOK = evalExpr(root, value, name, conditionExpr);


        Boolean require = ClassUtils.getValue(anno, E_C.require, false);

        //如果是必须的，但条件又不成立，则抛出异常
        if (Boolean.TRUE.equals(require) && !isOK) {
            throw new IllegalArgumentException(String.format("field [%s] is require, annotation [%s] condition[%s] must be true"
                    , name, anno.annotationType().getSimpleName(), conditionExpr));
        }

        return isOK;
    }


    /**
     * @param root
     * @param value
     * @param name
     * @param expr  如果 expr 为 null
     * @return
     */
    protected <T> T evalExpr(Object root, Object value, String name, String expr) {

        //优化性能
        if (C.NOT_NULL.equalsIgnoreCase(expr)) {

            if (value == null) {
                return (T) Boolean.FALSE;
            } else if (value instanceof CharSequence) {
                return (T) (Boolean) (((CharSequence) value).toString().trim().length() > 0);
            } else if (value.getClass().isArray()) {
                return (T) (Boolean) (Array.getLength(value) > 0);
            } else if (value instanceof Collection) {
                return (T) (Boolean) (((Collection) value).size() > 0);
            } else if (value instanceof Map) {
                return (T) (Boolean) (((Map) value).size() > 0);
            }

            return (T) Boolean.TRUE;

        }

        return ExprUtils.evalSpEL(root, expr, buildContextValues(root, value, name));
    }


    /**
     * 开始逻辑分组
     *
     * @param bean
     * @param logicAnnotation
     * @param value
     */
    private void beginLogicGroup(Object bean, Annotation logicAnnotation, String name, Object value) {
        if (logicAnnotation != null
                && !(logicAnnotation instanceof END)) {
            beginLogic(logicAnnotation.annotationType().getSimpleName(), isValid(logicAnnotation, bean, name, value));
        }
    }

    protected void beginLogic(String op, boolean valid) {
        whereExprRootNode.beginGroup(op, valid);
    }

    protected void endLogic() {
        whereExprRootNode.endGroup();
    }

    private void endLogicGroup(Object bean, Annotation logicAnnotation, Object value) {
        //如果遇到逻辑结束
        if (logicAnnotation instanceof END) {
            end();
        }
    }


    /**
     * 获取属性的数据类型
     *
     * @param name
     * @return
     */
    protected Class<?> getExpectType(String name) {
        return entityClass != null ? QueryAnnotationUtil.getFieldType(entityClass, name) : null;
    }


    public List<Map<String, ? extends Object>> getDaoContextValues() {

        return Arrays.asList(
                DaoContext.getGlobalContext(),
                DaoContext.getThreadContext(),
                (this.context != null) ? this.context : Collections.EMPTY_MAP);

    }

    public List<Map<String, ? extends Object>> buildContextValues(Object root, Object value, String fieldName) {

        List<Map<String, ? extends Object>> contextValues = new ArrayList<>();

        contextValues.addAll(getDaoContextValues());

        if (root instanceof Map) {
            contextValues.add(Map.class.cast(root));
        } else if (root != null) {
            //把对象
            contextValues.add(ObjectUtil.copyField2Map(root, null));
        }

        if (value instanceof Map) {
            contextValues.add(Map.class.cast(value));
        }

        contextValues.add(MapUtils
                .put("_val", value)
                .put("_this", root)
                .put("_name", fieldName)
                .put("_isSelect", (this instanceof SelectDao))
                .put("_isUpdate", (this instanceof UpdateDao))
                .put("_isDelete", (this instanceof DeleteDao))
                .build());

        return contextValues;

    }

}
