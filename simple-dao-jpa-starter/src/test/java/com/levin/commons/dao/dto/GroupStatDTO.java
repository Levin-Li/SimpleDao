package com.levin.commons.dao.dto;


import com.levin.commons.dao.TargetOption;
import com.levin.commons.dao.annotation.*;
import com.levin.commons.dao.annotation.logic.OR;
import com.levin.commons.dao.annotation.stat.*;
import com.levin.commons.dao.domain.Group;
import com.levin.commons.service.domain.Desc;
import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
@TargetOption(entityClass = Group.class, maxResults = 100)
public class GroupStatDTO {


    @Desc
    @GroupBy(alias = "state")
    String state;

    @GroupBy
    String category;


    @Count(value = "id", alias = "gid")
    Integer cnt;

    @Avg(surroundPrefix = "(", value = "score", surroundSuffix = " + 5 )")
    Double avgScore;

    @Sum()
    Double sumScore = 3.0;


    @Min
    Double minScore;


    @Max
    Double maxScore;


    @IsNull
    @IsNotNull
    @Eq
    @OR(autoClose = true)
    String name = "Group";


    @Like("name")
    String name2;

}
