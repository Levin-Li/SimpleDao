package com.levin.commons.dao.dto;


import com.levin.commons.dao.annotation.*;
import com.levin.commons.dao.annotation.select.Select;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.HashMap;
import java.util.Map;


@Data
@Accessors(chain = true)
public class UserSelectDTO extends UserDTO {

    @Select("group.name")
    @Like("group.name")
    String groupName = "Group";


    @Ignore
    String name;


    @Ignore
//    @Select("score")
    UserStatDTO selectSubQueryDTO = new UserStatDTO();

    //子查询，并使用命名参数，命名参数从Map变量中取
    @NotExists(paramExpr = "select '${_name}' from jpa_dao_test_User t where u.id = t.id and t.score > ${:minScore} and t.name like ${groupName}")
//            int minScore =5;
    Map<String, Object> namedParams = new HashMap<>();


    //子查询，子查询将从subQueryDTO查询对象中生成
    @NotExists
    SubQueryDTO subQueryDTO = new SubQueryDTO();

    @In("name")
    SubQueryDTO nameInDto = new SubQueryDTO();

    //子查询产生
    @Gt("score")
    SubQueryDTO gtSubQueryDTO = new SubQueryDTO();


}
