package com.levin.commons.dao;


/**
 * 查询选项
 * <p>
 * 如果查询实体实现这个接口，将自动会被处理
 */
public interface QueryOption {

    /**
     * 查询返回类型
     */
    enum ReturnType {
        /**
         * 总数
         */
        Totals,
        /**
         * 结果集
         */
        ResultList,
        /**
         * 总数+结果集
         */
        Both
    }

    /**
     * 获取查询返回的类型
     *
     * @return
     */
    default ReturnType getQueryReturnType() {
        return ReturnType.ResultList;
    }

    /**
     * 获取查询的主体目标实体类
     *
     * @return
     */
    default Class getEntityClass() {
        return null;
    }

    /**
     * 获取查询的实体（或是表）的名字
     *
     * <p>
     * 一般不建议直接使用表名，会带来一系列字段名称的问题
     *
     * @return
     */
    default String getEntityName() {
        return null;
    }

    /**
     * 主表的别名
     * <p>
     * 针对单个查询目标时有效
     *
     * @return
     */
    default String getAlias() {
        return null;
    }


    /**
     * 获取连接选项
     *
     * @return
     */
    default JoinOption[] getJoinOptions() {
        return null;
    }

    /**
     * 获取查询结果类
     *
     * @return
     */
    default Class getResultClass() {
        return null;
    }

}
