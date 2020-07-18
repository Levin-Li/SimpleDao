package com.levin.commons.dao.domain;

import com.levin.commons.dao.domain.support.AbstractNamedEntityObject;
import com.levin.commons.service.domain.Desc;
import lombok.Data;
import lombok.experimental.Accessors;

import javax.persistence.*;
import java.util.Date;

/**
 * Created by echo on 2015/11/17.
 */
@Entity
@Data
@Accessors(chain = true)
public class UserLog
        extends AbstractNamedEntityObject<Long>
        implements StatefulObject<String> {

    @Id
    @GeneratedValue
    private Long id;

    @Column
    String ipAddr;

    @Column
    String opDesc;

    @Column(nullable = true)
    @Temporal(TemporalType.TIMESTAMP)
    Date occurTime;

    String state;


    String testInfo;
}
