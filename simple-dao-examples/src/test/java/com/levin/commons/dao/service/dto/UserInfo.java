package com.levin.commons.dao.service.dto;


import com.levin.commons.dao.annotation.misc.Fetch;
import com.levin.commons.dao.domain.Group;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.Collection;
import java.util.List;

@Data
@Accessors(chain = true)
public class UserInfo {

    String id;

    String name;

    String state;

    String area;

    Integer score;


    @Fetch
    Group group;

    @Fetch(value = "group.parent.parent.name b")
    String groupName;

    @Fetch(value = "group.children c" )
    List<Group> parentChildren;

}
