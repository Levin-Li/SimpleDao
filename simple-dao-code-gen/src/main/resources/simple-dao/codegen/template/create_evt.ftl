package ${packageName};

<#--import com.oak.api.model.ApiBaseReq;-->
import io.swagger.v3.oas.annotations.media.Schema;

/////////////////////////////////////////////////////
import javax.validation.constraints.*;
import javax.annotation.*;
import lombok.*;
import lombok.experimental.*;
import java.util.*;

///////////////////////////////////////////////////////
import com.levin.commons.service.domain.*;
import com.levin.commons.dao.*;
import com.levin.commons.dao.annotation.*;
import com.levin.commons.dao.annotation.update.*;
import com.levin.commons.dao.annotation.select.*;
import com.levin.commons.dao.annotation.stat.*;
import com.levin.commons.dao.annotation.order.*;
import com.levin.commons.dao.annotation.logic.*;
import com.levin.commons.dao.annotation.misc.*;


import ${entityClassPackage}.*;

////////////////////////////////////
//自动导入列表
<#list importList as imp>
    import ${imp};
</#list>
////////////////////////////////////


/**
 *  创建${desc}
 *  //Auto gen by simple-dao-codegen ${.now}
 */
@Schema(description = "创建${desc}")
@Data
@Accessors(chain = true)
@ToString
<#--@EqualsAndHashCode(callSuper = true)-->
@FieldNameConstants
@AllArgsConstructor
@NoArgsConstructor
@Builder
@TargetOption(entityClass = ${entityName}.class, alias = E_${entityName}.ALIAS)
public class ${className} implements ServiceReq {

    private static final long serialVersionUID = ${serialVersionUID}L;

<#list fields as field>

    <#if ( field.baseType && !field.pk && !field.lazy && !field.autoIdentity)>
    @Schema(description = "${field.desc}")
    <#list field.annotations as annotation>
    ${annotation}
    </#list>
    private ${field.typeName} ${field.name};

    </#if>
</#list>

    @PostConstruct
    public void prePersist() {
    //保存之前初始化数据
<#list fields as field>
    <#if field.name == 'sn' && field.typeName == 'String'>
        String sn = UUID.randomUUID().toString().replaceAll("-", "").substring(0, 10).toUpperCase();
         setSn(sn);
    </#if>
    <#if field.name == 'addTime'>
         setAddTime(new Date());
    </#if>
    <#if field.name == 'createTime'>
         setCreateTime(new Date());
    </#if>
    <#if field.name == 'updateTime'>
          //setUpdateTime(new Date());
    </#if>
    <#if field.name == 'lastUpdateTime'>
         //setLastUpdateTime(new Date());
    </#if>
</#list>

    }

}
