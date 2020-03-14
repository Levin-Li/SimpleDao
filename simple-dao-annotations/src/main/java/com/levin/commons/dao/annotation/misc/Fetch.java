package com.levin.commons.dao.annotation.misc;

import java.lang.annotation.*;

@Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Inherited

/**
 *
 * 抓取集合属性
 *
 * 仅适用于JPA
 *
 * @author llw
 * @version 2.0.0
 */
public @interface Fetch {


    enum JoinType {
        Left,
        Right,
        Inner,
        Natural,
        Full,
        Default,
    }

    /**
     * 默认属性
     *
     * @return
     */
    String value() default "";

    /**
     * 是否是必须的
     *
     * @return
     */
    boolean require() default false;

    /**
     * 要抓取属性列表
     * <p>
     * use value
     *
     * @return
     */
    String[] attrs() default {};

    /**
     * 连接类型
     *
     * @return
     */
    JoinType joinType() default JoinType.Default;


    /**
     * 表达式，考虑支持Groovy和SpEL
     * <p/>
     * 当条件成立时，整个条件才会被加入
     *
     * @return
     */
    String condition() default "true";


    /**
     * @return
     */
    String desc() default "";

}
