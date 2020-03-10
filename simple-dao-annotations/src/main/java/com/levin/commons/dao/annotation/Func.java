package com.levin.commons.dao.annotation;

import java.lang.annotation.*;

/**
 * 单参数的函数
 *
 * @since 2.1.0
 */

@Target({ElementType.METHOD, ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Inherited
public @interface Func {

    /**
     * 函数名称
     *
     *
     *
     * @return
     */
    String value() default "";


    /**
     * 对操作数的包围前缀
     * <p>
     * 支持参数表达式
     *
     * @return
     */
    String prefix() default "(";


    /**
     * 对操作数的包围后缀
     * <p>
     * 支持参数表达式
     * <p>
     * 如日期函数：toDate( :?,:format)
     * <p>
     * 则后缀为： ,:format)
     *
     * @return
     */
    String suffix() default ")";


    /**
     * 说明
     *
     * @return
     */
    String desc() default "单参数函数表达式生成规则：value + prefix + 参数 + suffix";

}
