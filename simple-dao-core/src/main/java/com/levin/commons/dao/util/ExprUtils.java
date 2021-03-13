package com.levin.commons.dao.util;

import com.levin.commons.dao.JoinOption;
import com.levin.commons.dao.MiniDao;
import com.levin.commons.dao.StatementBuildException;
import com.levin.commons.dao.annotation.C;
import com.levin.commons.dao.annotation.Func;
import com.levin.commons.dao.annotation.Op;
import com.levin.commons.dao.support.SelectDaoImpl;
import com.levin.commons.dao.support.ValueHolder;
import com.levin.commons.utils.MapUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.core.ResolvableType;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;

import org.springframework.util.ConcurrentReferenceHashMap;
import org.springframework.util.ReflectionUtils;
import org.springframework.util.StringUtils;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.levin.commons.dao.util.QueryAnnotationUtil.flattenParams;
import static org.springframework.util.StringUtils.hasText;

public abstract class ExprUtils {

    //匹配样式：${:paramName}
    public static final Pattern jpaNamedParamStylePattern = Pattern.compile("(\\$\\{\\s*:\\s*([\\w._]+)\\s*\\})");

    //匹配样式：${paramName}
    public static final Pattern groovyVarStylePattern = Pattern.compile("(\\$\\{\\s*\\s*([\\w._]+)\\s*\\})");


    //替换变量
    //SQL查询占位参数匹配样式：${:paramName} ，如  t.score +  ${:val}  --> t.score +  :?
    //文本替换匹配样式：${paramName}  ，       如 t.score +  ${val}  --> t.score + 10

    /**
     * 关联字段缓存
     */
    private static final Map<String, List<String>> refCache = new ConcurrentReferenceHashMap<>();

    /**
     * 核心方法 生成语句，并返回参数
     *
     * @param c
     * @param fieldExpr
     * @param complexType
     * @param expectType
     * @param holder
     * @param paramPlaceholder
     * @param subQueryBuilder
     * @param contexts         注意，越后面，优先级越高，要可以修改的 List 对象
     * @return
     */
    public static String genExpr(C c, String fieldExpr, boolean complexType, Class<?> expectType
            , ValueHolder holder, String paramPlaceholder, Function<ValueHolder, String> subQueryBuilder,
                                 List<Map<String, ? extends Object>> contexts) {

        // 表达式生成原理： 字段表达式（fieldExpr）  + 操作符 （op） +  参数表达式（c.paramExpr()） ---> 对应的变量
        // 如  a.name || b.name || ${:cname}   = （等于操作） ${:v}    参数Map： { cname:'lily' , v:info}

        Op op = c.op();

        if (op == null) {
            //默认用等于
            op = Op.Eq;
        }

        if (op == Op.None) {
            holder.value = Collections.emptyList();
            return "";
        }

        //如果只有一个元素的数组
        holder.value = tryGetFirstElement(holder.value);

        boolean isExistsOp = Op.Exists.equals(op) || Op.NotExists.equals(op);

        boolean isNotOp = Op.Not.name().equals(op.name());

        if (op.isNeedFieldExpr() && op.isAllowFieldFunc()) {
            //如果字段表达式中有函数，用函数包围
            fieldExpr = funcExpr(fieldExpr, c.fieldFuncs());
        }

        String paramExpr = "";

        boolean hasDynamicExpr = op.isNeedParamExpr();

        //如果是 Not 操作

        if (isNotOp) {

            hasDynamicExpr = false;

            paramExpr = (String) holder.value;

        } else if (hasDynamicExpr) {

            //判读该操作是否需要参数表达式，默认大部分操作都需要右操作数，右操作数都是支持表达式的

            //优先使用表达式
            if (hasText(c.paramExpr())) {

                //优先使用注解定义的表达式

                paramExpr = c.paramExpr();

                hasDynamicExpr = false;

            } else if (complexType) {

                paramExpr = subQueryBuilder.apply(holder);

                hasDynamicExpr = true;

            } else if (isExistsOp
                    && !isNotEmptyAnnotation(c, op)
                    && holder.value instanceof CharSequence) {

                //如果是 Exist 操作，并且没有配置

                paramExpr = holder.value.toString();

                hasDynamicExpr = false;

            } else {

                int eleCount = 1;

                if (op.isExpandParamValue()) {
                    //操作是否可以展开参数，如 in , not in ,between 等

                    //参数之间的分隔符，仅对参数是字符串时有效
                    if (holder.value instanceof CharSequence) {
                        if (hasText(c.paramDelimiter())) {
                            holder.value = holder.value.toString().split(c.paramDelimiter());
                        } else if (expectType != null && !CharSequence.class.isAssignableFrom(expectType)) {
                            //关键逻辑，如果时可以展开参数的操作，并且期望的类型不是字符串，则用逗号分割数据
                            holder.value = holder.value.toString().split(",");
                        } else {
                            //
                        }
                    }

                    //如果数据库的目标字段类型检测到，并且不是字符串类型，并且参数值是字符串
                    //尝试自动解析成数组
                    if (expectType != null
//                        && !String.class.equals(expectType)  // 关键点
                            && (holder.value instanceof CharSequence
                            || holder.value instanceof String[])) {

                        holder.value = convertValue(expectType, holder.value, c, op);
                    }

                    if (c.filterNullValue()) {
                        //@todo 优化性能
                        holder.value = QueryAnnotationUtil.filterNullValue(holder.value, true);
                    }

                    eleCount = QueryAnnotationUtil.eleCount(holder.value);

                } else {
                    holder.value = convertValue(expectType, holder.value, c, op);
                }

                // 替换成 参数占位符
                paramExpr = genParamExpr(op.getParamDelimiter(), paramPlaceholder, eleCount);

                hasDynamicExpr = true;

            }

            //自动加大挂号
            if ((hasText(c.paramExpr()) || complexType) && !isExistsOp) {
                //尝试自动加挂号
                paramExpr = autoAroundParentheses("", paramExpr, "");
            }

        } else {
            // 如果不需要参数表达式，那什么也不做
            //@todo
        }

        final String paramKey = "P_" + Math.abs(paramExpr.hashCode()) + "_" + System.currentTimeMillis();

        final String oldParamExpr = paramExpr;

        if (op.isNeedParamExpr() && hasDynamicExpr) {
            //动态参数，后面替换,需要替换参数
            paramExpr = "${:" + paramKey + "}";
        }

        //如果需要参数的操作
        if (op.isNeedParamExpr()) {
            paramExpr = funcExpr(paramExpr, c.paramFuncs());
        }

        //如果需要展开参数，没有参数内容
        if (op.isExpandParamValue()
                && op.isNeedParamExpr()
                && !hasText(oldParamExpr)) {

            //如果需要展开参数，但又没有参数内容，被认为是无效的注解，直接忽略

            return "";
        }


        if (!op.isNeedParamExpr() && op.isNeedFieldExpr()) {

            // 如果是一个不需参数的操作，把参数中的 Map 类型参数，加入到上下文
            flattenParams(null, holder.value).stream()
                    .filter(v -> v instanceof Map)
                    .forEach((map) -> contexts.add((Map<String, ? extends Object>) map));

        }


        final List<Object> paramValues = new ArrayList(7);

        /// Function<String, String> genExpr = ql -> processParamPlaceholder(ql, paramPlaceholder, paramValues, contexts);

        //    String desc() default "语句表达式生成规则： surroundPrefix + op.gen( func(fieldExpr), func([paramExpr or fieldValue])) +  surroundSuffix ";

        //替换参数
        String ql = c.surroundPrefix() + " " + op.gen(fieldExpr, paramExpr) + " " + c.surroundSuffix();

        //替换参数
        ql = processParamPlaceholder(ql, (key, ctxs) -> {

            //替换参数表达式和匹配参数
            if (key.equals(paramKey)) {

                paramValues.add(holder.value);

                //替换参数表达式
                return oldParamExpr;
            } else {

                paramValues.add(ObjectUtil.findValue(key, true, true, ctxs));

                return paramPlaceholder;
            }

        }, contexts).trim();

        //替换参数和 占位参数不能共存，如：  distinct ( g.name || :? || ${:v} || ${v})  中 :? 和 ${:v} 不能共存
        //
        if (paramValues.isEmpty()) {
            //关键逻辑如果没有替换参数出现，则原有的参数保存不变

//            if (holder.value != null) {
//                log.warn(op + "  [ " + fieldExpr + " ] 发现参数 " + holder.value);
//            }

            holder.value = Collections.emptyList();

        } else if (paramValues.size() == 1) {
            holder.value = paramValues.get(0);
        } else {
            holder.value = paramValues;
        }

        return surroundNotExpr(c, replace(ql, contexts).trim());
    }


    /**
     * 如果只又一个元素，则只取第一个
     *
     * @param arrayOrSet
     * @return
     */
    private static Object tryGetFirstElement(Object arrayOrSet) {

        while (arrayOrSet != null) {

            if (arrayOrSet instanceof Collection) {
                Collection set = (Collection) arrayOrSet;

                if (set.size() == 1) {
                    arrayOrSet = set.toArray()[0];
                } else {
                    break;
                }

            } else if (arrayOrSet.getClass().isArray()) {

                if (Array.getLength(arrayOrSet) == 1) {
                    arrayOrSet = Array.get(arrayOrSet, 0);
                } else {
                    break;
                }

            } else {
                break;
            }
        }

        return arrayOrSet;
    }

    /**
     * @param eleType
     * @param value
     * @param c
     * @param op
     * @return
     */
    private static Object convertValue(Class eleType, Object value, C c, Op op) {

        if (eleType == null || value == null) {
            return value;
        }

        Class<?> valueClass = value.getClass();

        if (eleType.isAssignableFrom(valueClass)) {
            return value;
        }

        //只针对日期类型转换
        if (Date.class.isAssignableFrom(eleType)
                && c.patterns().length > 0) {

            if (valueClass.isArray()) {

                int idx = Array.getLength(value);

                Object newArray = eleType.isAssignableFrom(valueClass.getComponentType()) ? value : Array.newInstance(eleType, idx);

                while (idx-- > 0) {
                    Array.set(newArray, idx, tryConvertToDate(Array.get(value, idx), c.patterns()));
                }

                return newArray;

            } else if (value instanceof Collection) {
                return ((Collection) value).stream().map(ele ->
                        tryConvertToDate(ele, c.patterns()))
                        .collect(Collectors.toList());
            }
        }

        //尝试类型转换
        if (valueClass.isArray()
                || value instanceof Collection) {
            eleType = Array.newInstance(eleType, 0).getClass();
        }

        try {
            return ObjectUtil.convert(value, eleType);
        } catch (Exception e) {
            return value;
        }

    }

    /**
     * 尝试转成 Date 类型
     *
     * @param data
     * @param patterns
     * @return
     */
    private static Object tryConvertToDate(Object data, String... patterns) {

        if (data != null && !(data instanceof Date)) {
            for (String pattern : patterns) {
                try {
                    return new SimpleDateFormat(pattern).parse(data.toString());
                } catch (Exception e) {
                }
            }
        }

        return data;
    }


    public static String surroundNotExpr(C c, String expr) {

        return c.not() && hasText(expr) ? autoAroundParentheses(" NOT(", expr, ") ") : expr;

    }


    /**
     * 测试一个注解是非空注解
     *
     * @param c
     * @param op
     * @return
     */
    public static boolean isNotEmptyAnnotation(C c, Op op) {

        if (op == null) {
            op = c.op();
        }

        String paramExpr = op.isNeedParamExpr() ? funcExpr("", c.paramFuncs()) : "";
        String fieldExpr = op.isNeedFieldExpr() ? funcExpr("", c.fieldFuncs()) : "";

        String ql = c.surroundPrefix() + c.value() + c.paramExpr() + fieldExpr + paramExpr + c.surroundSuffix();

        return hasText(ql);

    }


    /**
     * 构建子查询语句及参数
     *
     * @param holder
     * @return
     */
    public static String buildSubQuery(ValueHolder holder, MiniDao dao, boolean isNative, Map<String, Object> context) {

        SelectDaoImpl selectDao = new SelectDaoImpl(dao, isNative);

        if (context != null) {
            selectDao.setContext(context);
        }

        // selectDao.localParamPlaceholder = this.localParamPlaceholder;

        Object queryObj = holder.value;

        if (queryObj != null) {
            if (queryObj.getClass().isArray())
                selectDao.appendByQueryObj((Object[]) queryObj);
            else
                selectDao.appendByQueryObj(queryObj);
        }

        String subStatement = selectDao.genFinalStatement();

        if (hasText(subStatement)) {
            holder.value = selectDao.genFinalParamList();
        } else {
            holder.value = Collections.emptyList();
        }

        return subStatement;
    }


    /**
     * 函数调用叠加
     *
     * @param name
     * @param funcs
     * @return
     */
    public static String funcExpr(String name, Func... funcs) {
        //允许没有名称

        return Arrays.stream(funcs)
                //  .filter(func -> StringUtils.hasText(func.value()))
                .reduce(name,
                        (expr, func) -> {

                            StringBuilder sb = new StringBuilder();

                            for (String param : func.params()) {

                                if (StringUtils.isEmpty(param)) {
                                    continue;
                                }

                                if (sb.length() > 0) {
                                    sb.append(" , ");
                                }

                                sb.append(Func.DEFAULT_PARAM.equals(param) ? expr : param);
                            }

                            //如果有参数值
                            if (sb.length() > 0) {
                                expr = sb.toString();
                            }

                            return func.value() + func.prefix() + expr + func.suffix();
                        },
                        (r1, r2) -> r1 + r2);
    }

    /**
     * 尝试自动加上小括号，并自动处理重复小括号的问题
     *
     * @param expr
     * @return
     */
    public static String autoAroundParentheses(String prefix, String expr, String suffix) {

        expr = StringUtils.trimWhitespace(expr);

        if (expr.length() > 0) {

            prefix = StringUtils.trimAllWhitespace(prefix);
            suffix = StringUtils.trimAllWhitespace(suffix);

            //如果
            if (!prefix.endsWith("(")
                    && !suffix.startsWith(")")
                    && !expr.startsWith("(")
                    && !expr.endsWith(")")) {

                expr = "( " + expr + " )";

            }

            expr = prefix + " " + expr + " " + suffix;
        }

        return expr;
    }

    /**
     * 根据参数个数生成问号
     *
     * @param n
     * @return
     */
    static String genParamExpr(String delimiter, String txt, int n) {

        if (n < 1) {
            return "";
        }

        if (n == 1) {
            return txt;
        }

        StringBuilder buf = new StringBuilder();

        for (int i = 0; i < n; i++) {

            if (i > 0) {
                buf.append(delimiter).append(" ");
            }

            buf.append(txt);
        }

        return buf.toString();
    }

    /**
     * 参数替换辅助类
     *
     * @param qlSection
     * @param contexts
     * @return
     */
    public static String processParamPlaceholder(String qlSection, BiFunction<String, List<Map<String, ? extends Object>>, String> biFunction,
                                                 List<Map<String, ? extends Object>> contexts) {

        if (!hasText(qlSection)) {
            return qlSection;
        }

        qlSection = replace(qlSection, contexts);

        return replace(jpaNamedParamStylePattern, qlSection, key -> biFunction.apply(key, contexts));

    }

    public static String replace(String txt, List<Map<String, ? extends Object>> contexts) {
        return replace(txt, true, contexts);
    }


    public static String replace(String txt, boolean isThrowExWhenKeyNotFound, List<Map<String, ? extends Object>> contexts) {

        if (!hasText(txt)) {
            return txt;
        }

        return replace(groovyVarStylePattern, txt, key -> {

            Object v = ObjectUtil.findValue(key, true, isThrowExWhenKeyNotFound, contexts);

            if (v == null) {
                throw new StatementBuildException(String.format("[{%s}] var {%s} not found on context", txt, key));
            }

            return v.toString();
        });

    }

    /**
     * spring el 求值
     *
     * @param rootObject
     * @param expression
     * @param contexts
     * @param <T>
     * @return
     */
    public static <T> T evalSpEL(Object rootObject, String expression, List<Map<String, ? extends Object>> contexts) {

        final EvaluationContext ctx = new StandardEvaluationContext(rootObject);

        Optional.ofNullable(contexts).ifPresent(
                maps -> {
                    maps.stream().filter(Objects::nonNull)
                            .forEach(map -> ((StandardEvaluationContext) ctx).setVariables((Map<String, Object>) map));
                }
        );

        ExpressionParser parser = new SpelExpressionParser();

        return (T) parser.parseExpression(expression).getValue(ctx);

    }

    /**
     * 把占位符，替换掉
     *
     * @param pattern
     * @param txt
     * @param function 替换回调
     * @return
     */
    private static String replace(Pattern pattern, String txt, Function<String, String> function) {

        if (txt == null || txt.length() == 0) {
            return txt;
        }

        Matcher matcher = pattern.matcher(txt);

        StringBuffer sb = new StringBuffer();

        boolean found = false;

        while (matcher.find()) {

            found = true;

            String key = matcher.group(2);

//            把占位符，替换掉
            matcher.appendReplacement(sb, function.apply(key));
        }

        if (found) {
            matcher.appendTail(sb);
        }


        return found ? sb.toString() : txt;

    }

    static String nullSafe(String txt) {
        return txt != null ? txt : "";
    }

    /**
     * 获取要抓取的属性的jpa 抓取表达式
     * <p>
     * 如， parent.parent.parent.name  将返回 parent.parent.parent
     *
     * @param type
     * @param alias
     * @param propertyExpr
     * @return
     */
    public static String getExprForJpaJoinFetch(Class type, String alias, String propertyExpr) {

        if (type == null) {
            return propertyExpr;
        }

        StringBuilder sb = new StringBuilder();

        //如果有别名
        if (hasText(alias)) {

            String prefix = alias.trim() + ".";

            if (propertyExpr.startsWith(prefix)) {
                propertyExpr = propertyExpr.substring(prefix.length());
            }

            sb.append(alias.trim());

        }


        ResolvableType parentTypeHolder = ResolvableType.forClass(type);

        String[] names = propertyExpr.split("\\.");

        String joinAlias = "";

        for (String name : names) {

            if (!hasText(name)) {
                continue;
            }

            //尝试处理别名
            int aliasIdx = name.indexOf(' ');

            if (aliasIdx != -1) {

                //只取第一个别名
                if (!hasText(joinAlias)) {
                    joinAlias = name.substring(aliasIdx);
                }

                name = name.substring(0, aliasIdx);

            }

            Field field = ReflectionUtils.findField(type, name);

            if (field == null) {
                break;
            }

            parentTypeHolder = ResolvableType.forField(field, parentTypeHolder);

            type = parentTypeHolder.resolve();

            //如果解析不到类型
            if (type == null) {
                break;
            }


            //如果是简单属性
            if (BeanUtils.isSimpleValueType(type)) {
                break;
            }

            if (sb.length() > 0) {
                sb.append(".");
            }

            sb.append(name);

            //如果集合
            if (Collection.class.isAssignableFrom(type)) {
                break;
            }

        }

        if (hasText(joinAlias)
                && sb.length() > 0) {
            sb.append(" ").append(joinAlias);
        }

        String result = sb.toString().trim();

        return hasText(alias) && alias.trim().equals(result) ? "" : result;
    }


    /**
     * 自动生成连接语句
     *
     * @param entityClass
     * @param tableOrStatement
     * @param alias
     * @param joinOptions
     * @return
     */
    public static String genJoinStatement(MiniDao miniDao, Class entityClass, String tableOrStatement, String alias, JoinOption... joinOptions) {

        StringBuilder builder = new StringBuilder();

        if (joinOptions == null
                || joinOptions.length < 1) {
            return "";
        }

        if (!StringUtils.hasText(tableOrStatement) && entityClass == null) {
            throw new StatementBuildException("多表关联时，entityClass 或 tableOrStatement 必须指定一个");
        }

        if (!StringUtils.hasText(alias)) {
            throw new StatementBuildException("多表关联时，别名不允许为空");
        }

        Map<String, Class> aliasMap = new HashMap<>(joinOptions.length + 1);

        aliasMap.put(alias, entityClass);

        for (JoinOption joinOption : joinOptions) {

            final String selfAlias = joinOption.alias();

            if (!StringUtils.hasText(selfAlias)) {

            }

            if (!StringUtils.hasText(selfAlias)) {
                throw new StatementBuildException(joinOption + ": 多表关联时，JoinOption注解 的 alias 属性必须指定");
            }

            if (aliasMap.containsKey(selfAlias)) {
                throw new StatementBuildException(joinOption + ": alias 重名 ");
            } else {
                aliasMap.put(selfAlias, joinOption.entityClass());
            }

            String fromStatement = genFromStatement(joinOption);

            if (!StringUtils.hasText(fromStatement)) {
                throw new StatementBuildException(joinOption + ": 多表关联时，entityClass 或 tableOrStatement 必须指定一个");
            }

            String targetAlias = joinOption.joinTargetAlias();

            if (!StringUtils.hasText(targetAlias)) {
                targetAlias = alias;
            }

            if (!StringUtils.hasText(targetAlias)) {
                throw new StatementBuildException(joinOption + ": 无法确定关联的目标");
            }

            String targetColumn = joinOption.joinTargetColumn();

            if (!StringUtils.hasText(targetColumn)) {
                //尝试自动获取字段名
                List<String> refFieldNames = getRefFieldNames(aliasMap.get(targetAlias), joinOption.entityClass());
                if (refFieldNames.size() == 1) {
                    targetColumn = refFieldNames.get(0);
                }
            }

            if (!StringUtils.hasText(targetColumn)) {
                throw new StatementBuildException(joinOption + ": 无法确定关联的目标列，没有或是存在多个关联字段");
            }

            String joinColumn = joinOption.joinColumn();
            if (!StringUtils.hasText(joinColumn) && miniDao != null) {
                //@todo 实现获取表的主键名称
                joinColumn = miniDao.getPKName(joinOption.entityClass());
            }

            if (!StringUtils.hasText(joinColumn)) {
                throw new StatementBuildException(joinOption + ": 无法确定关联的列");
            }

            //
            builder.append(" ").append(joinOption.type().name()).append(" join ")
                    .append(fromStatement)
                    .append(" on ").append(targetAlias).append(".").append(targetColumn)
                    .append(" = ").append(selfAlias).append(".").append(joinColumn).append(" ");

        }

        return builder.toString();
    }

    public static String genFromStatement(JoinOption joinOption) {
        return genFromStatement(joinOption.entityClass(), joinOption.tableOrStatement(), joinOption.alias());
    }

    public static String genFromStatement(Class entityClass, String tableOrStatement, String alias) {

        if (StringUtils.hasText(tableOrStatement)) {
            //如果时表达式，不是表名，则加上挂号
            if (tableOrStatement.trim().contains(" ")) {
                tableOrStatement = "(" + tableOrStatement + ")";
            }
        } else if (entityClass != null
                && !Void.class.getName().equals(entityClass.getName())) {
            tableOrStatement = entityClass.getName();
        } else {
            return "";
        }

        return tableOrStatement + " " + nullSafe(alias);
    }


    /**
     * 在 owner 中 查找字段为fieldClass的字段名称
     *
     * @param owner
     * @param fieldClass
     * @return
     */
    public static List<String> getRefFieldNames(Class owner, Class fieldClass) {

        if (owner == null || fieldClass == null) {
            return null;
        }

        String key = owner.getName() + "_" + fieldClass.getName();

        List<String> value = refCache.get(key);

        if (value == null) {

            value = new ArrayList<>(2);

            final List<String> ref = value;

            //Field field = ReflectionUtils.findField(owner, null, fieldClass);

            ResolvableType ownerType = ResolvableType.forClass(owner);

            ReflectionUtils.doWithFields(owner, new ReflectionUtils.FieldCallback() {
                @Override
                public void doWith(Field field) throws IllegalArgumentException, IllegalAccessException {

                    Class<?> resolve = ResolvableType.forField(field, ownerType).resolve(field.getType());

                    if (fieldClass == resolve) {
                        ref.add(field.getName());
                    }

                }
            });

            refCache.put(key, value);
        }

        return value;
    }


    public static void main(String[] args) {


        String txt = "select * from ${table} t where t.name = :?  and t.age > :age  and t.sex = ${:sex} and t.desc like ${likeDesc}";


        txt = replace(txt, Arrays.asList(MapUtils.put("table", "person").put("likeDesc", "'%it'").build()));


    }


}
