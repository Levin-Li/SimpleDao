<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>${parent.groupId}</groupId>
        <artifactId>${parent.artifactId}</artifactId>
        <version>${parent.version}</version>
    </parent>

<#--  @Author Auto gen by simple-dao-codegen ${now} -->

    <artifactId>${artifactId}</artifactId>

<#--    <groupId>${groupId}</groupId>-->
<#--    <version>${version}</version>-->

    <packaging>${packaging}</packaging>

    <properties>

    </properties>

    <repositories>

    </repositories>


    <dependencyManagement>
        <dependencies>

        </dependencies>
    </dependencyManagement>

    <dependencies>

        <#if entities??>
            <dependency>
                <artifactId>${entities.artifactId}</artifactId>
                <groupId>${r"${project.groupId}"}</groupId>
                <version>${r"${project.version}"}</version>
            </dependency>

        </#if>

        <#if services??>
            <dependency>
                <artifactId>${services.artifactId}</artifactId>
                <groupId>${r"${project.groupId}"}</groupId>
                <version>${r"${project.version}"}</version>
            </dependency>

        </#if>


        <#if controller??>
            <dependency>
                <artifactId>${controller.artifactId}</artifactId>
                <groupId>${r"${project.groupId}"}</groupId>
                <version>${r"${project.version}"}</version>
            </dependency>
        </#if>

        <#if moduleType?? && moduleType == 'service'>

            <dependency>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-starter-data-jpa</artifactId>
                <scope>provided</scope>
            </dependency>

            <!-- https://mvnrepository.com/artifact/javax.validation/validation-api -->
            <dependency>
                <groupId>javax.validation</groupId>
                <artifactId>validation-api</artifactId>
                <scope>provided</scope>
            </dependency>


            <dependency>
                <groupId>com.h2database</groupId>
                <artifactId>h2</artifactId>
                <scope>provided</scope>
            </dependency>

            <dependency>
                <groupId>com.alibaba</groupId>
                <artifactId>druid</artifactId>
                <scope>provided</scope>
            </dependency>

            <dependency>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-starter-logging</artifactId>
                <scope>provided</scope>
            </dependency>

            <dependency>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-starter-aop</artifactId>
                <scope>provided</scope>
            </dependency>

            <dependency>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-starter-cache</artifactId>
                <scope>provided</scope>
            </dependency>



        </#if>

        <#if moduleType?? && moduleType == 'controller' >
            <dependency>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-starter-web</artifactId>
                <scope>provided</scope>
            </dependency>
        </#if>

        <#if moduleType?? && moduleType == 'testcase' >

            <dependency>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-starter-data-jpa</artifactId>
            </dependency>

            <!-- https://mvnrepository.com/artifact/javax.validation/validation-api -->
            <dependency>
                <groupId>javax.validation</groupId>
                <artifactId>validation-api</artifactId>
            </dependency>


            <dependency>
                <groupId>com.h2database</groupId>
                <artifactId>h2</artifactId>
            </dependency>

            <dependency>
                <groupId>com.alibaba</groupId>
                <artifactId>druid</artifactId>
            </dependency>


            <dependency>
                <groupId>com.querydsl</groupId>
                <artifactId>querydsl-jpa</artifactId>
            </dependency>

            <dependency>
                <groupId>com.querydsl</groupId>
                <artifactId>querydsl-apt</artifactId>
            </dependency>

            <dependency>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-autoconfigure</artifactId>

            </dependency>

            <dependency>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-configuration-processor</artifactId>

            </dependency>

            <dependency>
                <groupId>${r"${levin.service-support.groupId}"}</groupId>
                <artifactId>service-support</artifactId>
            </dependency>

            <dependency>
                <groupId>${r"${levin.simple-dao.groupId}"}</groupId>
                <artifactId>simple-dao-jpa-starter</artifactId>
            </dependency>


            <dependency>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-starter-web</artifactId>
            </dependency>

            <dependency>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-starter-logging</artifactId>
            </dependency>

            <dependency>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-starter-aop</artifactId>
            </dependency>

            <dependency>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-starter-cache</artifactId>
            </dependency>

            <dependency>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-starter-test</artifactId>
                <scope>test</scope>
            </dependency>


        </#if>

    </dependencies>

    <build>


        <pluginManagement>
            <plugins>

            </plugins>
        </pluginManagement>

    </build>


</project>