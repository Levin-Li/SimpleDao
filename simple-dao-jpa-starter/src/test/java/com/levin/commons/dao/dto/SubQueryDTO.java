package com.levin.commons.dao.dto;


import com.levin.commons.dao.Paging;
import com.levin.commons.dao.TargetOption;
import com.levin.commons.dao.annotation.Between;
import com.levin.commons.dao.annotation.In;
import com.levin.commons.dao.annotation.Like;
import com.levin.commons.dao.annotation.Lt;
import com.levin.commons.dao.annotation.logic.AND;
import com.levin.commons.dao.annotation.logic.END;
import com.levin.commons.dao.annotation.logic.OR;
import com.levin.commons.dao.annotation.order.OrderBy;
import com.levin.commons.dao.domain.User;
import com.levin.commons.dao.support.DefaultPaging;

import java.util.Date;


@TargetOption(entityClass = User.class,  maxResults = 100)
public class SubQueryDTO {


    Paging paging = new DefaultPaging(1, 20);


    Long id;

    @OrderBy
    String name = "User";


    @In()
    String[] state = new String[]{"A","B","C"};

    @AND
    protected Boolean editable = true;

    @Lt()
    @OR(condition = "#_val!=null")
    protected Date createTime = new Date();

    @Between("score")
    @END
    protected Integer[] scores = new Integer[]{200, 100, null, null};

    @Like
    @END
    protected String description = "";


}