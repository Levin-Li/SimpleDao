package com.levin.commons.dao.support;

import org.hibernate.boot.model.naming.Identifier;
import org.hibernate.engine.jdbc.env.spi.JdbcEnvironment;
import org.springframework.boot.orm.jpa.hibernate.SpringPhysicalNamingStrategy;

public class EntityNamingStrategy extends SpringPhysicalNamingStrategy {


    @Override
    public Identifier toPhysicalTableName(Identifier name, JdbcEnvironment jdbcEnvironment) {

        String text = name.getText();

        int index = text.indexOf("-");

        if (index > 1) {

            //com.vma.commons.agent-exam_tasks

            //@Entity(name = ModuleTableOption.PREFIX + "exam_tasks")
            // String PREFIX = "${modulePackageName}_";

            //  本段代码获取全路径包名的最后一个包名，做为表的前缀

            text = text.substring(0, index);

            while (text.endsWith(".")) {
                text = text.substring(0, text.length() - 1);
            }

            if (text.lastIndexOf(".") != -1) {

                text = text.substring(text.lastIndexOf(".") + 1).trim();

                name = getIdentifier(text + "_" + name.getText().substring(index), name.isQuoted(), jdbcEnvironment);

            }
        }

        return super.toPhysicalTableName(name, jdbcEnvironment);
    }
}
