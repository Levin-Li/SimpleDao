package com.levin.commons.dao.util;


import com.levin.commons.dao.EntityOption;
import com.levin.commons.dao.StatementBuildException;
import com.levin.commons.dao.annotation.*;
import com.levin.commons.dao.annotation.logic.AND;
import com.levin.commons.dao.annotation.logic.END;
import com.levin.commons.dao.annotation.logic.OR;
import com.levin.commons.dao.annotation.misc.PrimitiveValue;
import com.levin.commons.dao.annotation.order.OrderBy;
import com.levin.commons.dao.annotation.order.SimpleOrderBy;
import com.levin.commons.dao.annotation.select.Select;
//import com.levin.commons.dao.annotation.select.SelectColumn;
import com.levin.commons.dao.annotation.stat.*;
import com.levin.commons.dao.annotation.update.Update;
import com.levin.commons.dao.annotation.update.Update;
//import com.levin.commons.dao.annotation.update.UpdateColumn;
import com.levin.commons.service.support.Locker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.core.ResolvableType;
import org.springframework.util.ConcurrentReferenceHashMap;
import org.springframework.util.ReflectionUtils;
import org.springframework.util.StringUtils;

import javax.validation.constraints.NotNull;
import java.lang.annotation.Annotation;
import java.lang.reflect.*;
import java.util.*;

/**
 *
 */
public abstract class QueryAnnotationUtil {

    private final static Logger logger = LoggerFactory.getLogger(QueryAnnotationUtil.class);


    public static final String ANNOTITION_VALUE = "value";


    @Eq
    @Gt
    @Gte
    @In
    @Between
    @Like
    @Contains
    @StartsWith
    @EndsWith
    @Lt
    @Lte
    @Where
/////////////////////////////////
    @NotEq
    @NotIn
    @NotLike
    @IsNotNull
    @IsNull

    @Exists
    @NotExists

    @Ignore

    @PrimitiveValue

    //逻辑注解
    @AND
    @OR
    @END

    //统计注解
    @GroupBy
    @Avg
    @Count
    @Max
    @Min
    @Sum
    //////////////////////////////////

    @OrderBy
    @SimpleOrderBy
    /////////////////////////////////
    @Select
//    @SelectColumn

    @Update
//    @UpdateColumn

    private static final Map<String, Annotation> allInstanceMap = new HashMap<>();

    //条件表达式

    //缓存属性
    private static final Map<String, List<Field>> cacheFields = new ConcurrentReferenceHashMap<>();

    private static final Map<String, Boolean> hasSelectAnnotationCache = new ConcurrentReferenceHashMap<>();

    public static final Map<String, Object> cacheEntityOptionMap = new ConcurrentReferenceHashMap<>();

    private static final Locker locker = Locker.build();

    static {
        synchronized (allInstanceMap) {
            if (allInstanceMap.size() < 1) {
                try {
                    Annotation[] annotations = QueryAnnotationUtil.class.getDeclaredField("allInstanceMap").getDeclaredAnnotations();
                    for (Annotation annotation : annotations) {
                        allInstanceMap.put(annotation.annotationType().getSimpleName(), annotation);
                    }
                } catch (NoSuchFieldException e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }

    public static Map<String, Annotation> getAllAnnotations() {
        return new HashMap<>(allInstanceMap);
    }


    /**
     * 是否时同个包的
     *
     * @param annotation
     * @param type
     * @return
     */
    public static boolean isSamePackage(Annotation annotation, Class<? extends Annotation> type) {
        return annotation != null && annotation.annotationType().getPackage().getName().equals(type.getPackage().getName());
    }

    /**
     * 获取缓存
     *
     * @return
     */
    public static EntityOption getEntityOption(Class entityClass) {

        if (entityClass == null) {
            return null;
        }

        String className = entityClass.getName();

        Object value = cacheEntityOptionMap.get(className);

        synchronized (locker.getLock(className)) {
            if (value == null) {
                value = entityClass.getAnnotation(EntityOption.class);
                cacheEntityOptionMap.put(className, value != null ? value : Boolean.FALSE);
            }
        }

        return (value instanceof EntityOption) ? (EntityOption) value : null;
    }
/////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * @param valueList
     * @param paramValues
     * @return
     */
    public static List flattenParams(List valueList, Object... paramValues) {

        if (valueList == null) {
            valueList = new LinkedList();
        }

        if (paramValues == null) {
            return valueList;
        }

        for (Object paramValue : paramValues) {
            if (paramValue instanceof Collection) {
                for (Object pv : ((Collection) paramValue)) {
                    flattenParams(valueList, pv);
                }
            } else if (paramValue != null && paramValue.getClass().isArray()) {
                int length = Array.getLength(paramValue);
                for (int i = 0; i < length; i++) {
                    flattenParams(valueList, Array.get(paramValue, i));
                }
            } else if (paramValue instanceof Map) {
                valueList.add(paramValue);
            } else {
                valueList.add(paramValue);
            }
        }

        return valueList;
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * 过滤出指定包的注解
     *
     * @param packageName
     * @param annotations
     * @param excludeTypes
     * @return
     */
    public static List<Annotation> getAnnotationsByPackage(String packageName, Annotation[] annotations, Class<? extends Annotation>... excludeTypes) {

        List<Annotation> result = new ArrayList<>(2);

        if (annotations == null)
            return result;

        if (excludeTypes == null)
            excludeTypes = new Class[0];

        for (Annotation anno : annotations) {

            //相同的包括名
            if (!anno.annotationType().getPackage().getName().equals(packageName)) {
                continue;
            }

            for (Class<? extends Annotation> excludeType : excludeTypes) {
                if (excludeType.isInstance(anno)) {
                    anno = null;
                    break;
                }
            }

            if (anno != null) {
                result.add(anno);
            }
        }

        return result;
    }

    /**
     * 获一个逻辑注解，如果有多个将生产异常
     *
     * @param varName
     * @param varAnnotations
     * @return
     */
    public static List<Annotation> getLogicAnnotation(String varName, Annotation[] varAnnotations) {

        List<Annotation> annotations = getAnnotationsByPackage(AND.class.getPackage().getName(), varAnnotations, END.class);

        if (annotations.size() > 1) {
            //  throw new StatementBuildException(varName + " --> 同一个位置只允许定义一个逻辑注解，已经定义的注解：" + annotations);
        }

        return annotations;
    }

///////////////////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * 获取一个对象属性的数据类型
     * <p/>
     * 目前不支持泛型
     *
     * @param type
     * @param propertyName 支持复杂属性
     * @return
     */
    public static Class getFieldType(Class<?> type, String propertyName) {

        if (type == null || !StringUtils.hasText(propertyName)) {
            return null;
        }

        String[] names = propertyName.split("\\.");


        ResolvableType owner = null;

        for (int i = 0; i < names.length; i++) {

            Field field = ReflectionUtils.findField(type, names[i]);

            if (field == null) {
                return null;
            }

            owner = ResolvableType.forField(field, owner);

            type = owner.resolve(field.getType());
        }

        return type;
    }


    public static Annotation getAnnotation(Class<? extends Annotation> type) {
        return allInstanceMap.get(type.getSimpleName());
    }


    public static Annotation[] getAnnotations(Class<? extends Annotation>... types) {

        if (types == null || types.length < 1) {
            return new Annotation[0];
        }

        Annotation[] result = new Annotation[types.length];

        int i = 0;

        for (Class<? extends Annotation> type : types) {
            Annotation annotation = allInstanceMap.get(type.getSimpleName());

            if (annotation == null) {
                throw new StatementBuildException("Annotation " + type.getName() + " instance cache not found");
            }

            result[i++] = annotation;
        }


        return result;
    }

    /**
     * 获取属性名称
     *
     * @param opAnno
     * @param name
     * @return
     */
    public static String tryGetJpaEntityFieldName(Annotation opAnno, Class entityClass, @NotNull String name) {

        if (opAnno == null) {
            return name;
        }

        String newKey = null;

        try {
            newKey = (String) ReflectionUtils.findMethod(opAnno.annotationType(), ANNOTITION_VALUE).invoke(opAnno);
        } catch (Exception e) {
//            ReflectionUtils.rethrowRuntimeException(e);
        }

        if (!StringUtils.hasText(newKey)
                && entityClass != null
                && ReflectionUtils.findField(entityClass, name) == null) {

            // 以下逻辑是自动去除查找，去除注解名称以后的属性
            int len = opAnno.annotationType().getSimpleName().length();

            if (name.length() > len) {

                newKey = Character.toLowerCase(name.charAt(len)) + name.substring(len + 1);

                if (ReflectionUtils.findField(entityClass, newKey) == null) {
                    newKey = null;
                }

            }
        }

        return (StringUtils.hasText(newKey)) ? newKey : name;

    }

    /**
     * 获取第一个匹配注解
     *
     * @param annotations
     * @param types
     * @param <A>
     * @return
     */
    public static <A extends Annotation> A findFirstMatched(Annotation[] annotations, Class<? extends Annotation>... types) {

        if (annotations == null || types == null)
            return null;

        for (Annotation annotation : annotations) {

            if (annotation == null) {
                continue;
            }

            for (Class type : types) {

                if (type == null) {
                    continue;
                }

                if (annotation.annotationType() == type) {
                    return (A) annotation;
                }
            }
        }

        return null;
    }


////////////////////////////////////////////////////////////////////////////////////////////////////////

    public static <K, V> Map<K, V> copyMap(boolean isOnlyCopyPrimitive, Map<K, V> output, Map<K, V>... sources) {

        if (output == null) {
            output = new LinkedHashMap();
        }


        for (Map<K, V> source : sources) {

            if (source == null) {
                continue;
            }

            for (Map.Entry<K, V> entry : source.entrySet()) {

                if (!isOnlyCopyPrimitive
                        || entry.getValue() == null
                        || isPrimitive(entry.getValue().getClass())) {

                    output.put(entry.getKey(), entry.getValue());

                }
            }
        }

        return output;
    }
    ///////////////////////////////////////////////////////////////////////////////////////////////////////

    public static boolean hasSelectAnnotationField(Class type) {
        return hasSelectAnnotationField(type, null);
    }

    /**
     * 是否有选择注解的字段
     * <p>
     * 不支持嵌套
     *
     * @param type
     * @return
     */
    public static boolean hasSelectAnnotationField(Class type, ResolvableType resolvableType) {

        if (type == null) {
            return false;
        }

        Boolean hasAnno = hasSelectAnnotationCache.get(type.getName());

        if (hasAnno == null) {

            hasAnno = false;

            if (resolvableType == null) {
                resolvableType = ResolvableType.forClass(type);
            }

            List<Field> cacheFields = getCacheFields(type);

            for (Field field : cacheFields) {

                if (field.isAnnotationPresent(Select.class)) {
                    hasAnno = true;
                    break;
                }

                //如果示复杂对象
                ResolvableType forField = ResolvableType.forField(field, resolvableType);

                Class<?> fieldType = forField.resolve(field.getType());

                //防止递归
                if (fieldType != type && isComplexType(fieldType, null)) {
                    hasAnno = hasSelectAnnotationField(fieldType, forField);
                }

                if (hasAnno) {
                    break;
                }

            }

            hasSelectAnnotationCache.put(type.getName(), hasAnno);

        }

        return hasAnno;
    }

    /**
     * 获取字段列表，以包括所有父对象的子段
     * <p>
     * 不包括
     *
     * @param type
     * @return
     */
    public static List<Field> getCacheFields(Class type) {

        List<Field> fields = cacheFields.get(type.getName());

        if (fields == null) {
            fields = getFields(type, Modifier.STATIC | Modifier.TRANSIENT);
            if (fields.size() > 0) {
                cacheFields.put(type.getName(), fields);
            }
        }

        return fields.size() > 0 ? new ArrayList<>(fields) : Collections.emptyList();
    }


    /**
     * 获取所有的字段，包括父类的字段
     *
     * @param type
     * @return
     */
    static List<Field> getFields(Class type, int excludeModifiers) {

        List<Field> fields = new ArrayList<>();

        if (type == null
                || isRootObjectType(type)
                || isPrimitive(type)
                || isArray(type)
                || isIgnore(type)) {
            return fields;
        }

        fields.addAll(getFields(type.getSuperclass(), excludeModifiers));

        for (Field field : type.getDeclaredFields()) {
            //如果不是被过滤的类型
            if ((field.getModifiers() & excludeModifiers) == 0) {
                fields.add(field);
            }
        }

        return fields;
    }


    /**
     * 获取集合大小
     * <p/>
     * 如果不是集合，则返回1
     *
     * @param value
     * @return
     */
    public static int eleCount(Object value) {

        if (value == null) {
            return 0;
        }

        if (value instanceof Collection) {
            return ((Collection) value).size();
        }

        if (value.getClass().isArray()) {
            return Array.getLength(value);
        }

        return 1;
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
    public static boolean isComplexType(Class<?> varType, Object value) {

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

    /**
     * 如果value是集合，则移除集合中的null对象，并返回新的集合对象
     *
     * @param value
     * @return
     */
    public static Object filterNullValue(Object value, boolean isFilterEmptyString) {

        ////@todo 优化性能

        if (isNull(value, isFilterEmptyString)) {
            return null;
        }

        if (value.getClass().isArray()) {

            int length = Array.getLength(value);

            ArrayList list = new ArrayList(length);

            for (int i = 0; i < length; i++) {
                Object v = Array.get(value, i);
                if (!isNull(v, isFilterEmptyString)) {
                    list.add(v);
                }
            }

            return list;

        } else if (value instanceof Collection) {

            ArrayList list = new ArrayList(((Collection) value).size());

            for (Object v : (Collection) value) {
                if (!isNull(v, isFilterEmptyString)) {
                    list.add(v);
                }
            }
            return list;
        }

        return value;
    }


    private static boolean isNull(Object value, boolean isFilterEmptyString) {
        return value == null || (isFilterEmptyString && value instanceof CharSequence && !StringUtils.hasText((CharSequence) value));
    }


    public static boolean isArray(Type type) {
        return (type instanceof GenericArrayType)
                || ((type instanceof Class) && ((Class) type).isArray());
    }

    public static boolean isIterable(Type type) {
        return (type instanceof Class)
                && Iterable.class.isAssignableFrom(((Class) type));
    }

    public static boolean isMap(Type type) {
        return (type instanceof Class)
                && Map.class.isAssignableFrom(((Class) type));
    }

    public static boolean isRootObjectType(Type type) {
        return (type instanceof Class)
                && (Object.class == type || Object.class.getName().equals(((Class) type).getName()));
    }

    public static boolean isPrimitiveCollection(Class value) {


        ResolvableType resolvableType = ResolvableType.forClass(value.getClass());

        Class<?> rawClass = resolvableType.resolveGeneric(0);

        boolean b = resolvableType.hasGenerics();


        System.out.println(b);

        return false;
    }

    /**
     * 是数组并且有原子元素
     *
     * @param value
     * @return
     */
    public static boolean isArrayAndExistPrimitiveElement(Object value) {

        boolean isArray = (value != null) && value.getClass().isArray();

        if (!isArray) {
            return false;
        }


        if (isPrimitive(value.getClass().getComponentType())) {
            return true;
        }


        int length = Array.getLength(value);

        while (--length >= 0) {
            Object ele = Array.get(value, length);
            if (ele != null && isPrimitive(ele.getClass())) {
                return true;
            }
        }

        return false;
    }


    /**
     * 是否是一个空数组
     *
     * @param value
     * @return
     */
    public static boolean isEmptyArray(Object value) {
        return value != null
                && value.getClass().isArray()
                && (Array.getLength(value) == 0);
    }


    /**
     * 是否是一个空数组
     *
     * @param value
     * @return
     */
    public static boolean isNotEmptyArray(Object value) {
        return value != null
                && value.getClass().isArray()
                && (Array.getLength(value) > 0);
    }

    /**
     * 复杂类型的判定条件为：对象，对象数组（数组元素为非原子对象）
     * <p>
     * <p>
     * 注意：
     * 原子类型包括 Date和枚举
     *
     * @param type
     * @return
     */
    public static boolean isPrimitive(Class type) {
        return BeanUtils.isSimpleProperty(type);
    }

    public static boolean isIgnore(Class clazz) {
        return clazz.isAnnotationPresent(Ignore.class);
    }

    public static boolean isIgnore(Field field) {
        return field.isAnnotationPresent(Ignore.class);
    }

}
