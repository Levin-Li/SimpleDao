package com.levin.commons.dao.dto;


import com.levin.commons.dao.Paging;
import com.levin.commons.dao.TargetOption;
import com.levin.commons.dao.annotation.*;
import com.levin.commons.dao.annotation.order.OrderBy;
import com.levin.commons.dao.annotation.select.Select;
import com.levin.commons.dao.annotation.update.Update;
import com.levin.commons.dao.domain.E_User;
import com.levin.commons.dao.domain.Group;
import com.levin.commons.dao.domain.User;
import com.levin.commons.dao.support.PagingQueryReq;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.util.Date;

@Data
@Accessors(chain = true)
@TargetOption(entityClass = User.class, alias = E_User.ALIAS, resultClass = SimpleUserQO.QResult.class)
public class SimpleUserQO {

    @Data
    @NoArgsConstructor
    public static class QResult {

        @Select
        String name;

        @Select
        Integer score;

    }

    @Data
    @NoArgsConstructor
    public static class QResult2 {
        String name;
        Integer score;
    }


//    @Lt(fieldFuncs = @Func(value = "DATE_FORMAT", params = {"$$", "${:format}"}))
    @Lt
    protected Date createTime = new Date();

    @Ignore
    String format = "YYYY-MM-DD";

}
