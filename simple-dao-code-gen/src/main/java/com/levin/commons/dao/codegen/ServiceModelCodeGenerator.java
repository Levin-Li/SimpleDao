package com.levin.commons.dao.codegen;

import com.levin.commons.dao.annotation.Ignore;
import com.levin.commons.dao.annotation.Like;
import com.levin.commons.service.domain.Desc;
import com.levin.commons.service.domain.InjectVar;
import com.levin.commons.service.domain.SecurityDomain;
import com.levin.commons.service.support.ContextHolder;
import com.levin.commons.utils.ExceptionUtils;
import com.levin.commons.utils.MapUtils;
import freemarker.template.Configuration;
import freemarker.template.DefaultObjectWrapper;
import freemarker.template.DefaultObjectWrapperBuilder;
import freemarker.template.Template;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.Accessors;
import org.apache.commons.io.FileUtils;
import org.apache.maven.project.MavenProject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ResolvableType;
import org.springframework.util.ReflectionUtils;
import org.springframework.util.StringUtils;

import javax.persistence.*;
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;

import io.swagger.v3.oas.annotations.media.*;

import java.io.*;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.text.MessageFormat;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

//import org.apache.maven.project.MavenProject;

public final class ServiceModelCodeGenerator {

    private static final Logger logger = LoggerFactory.getLogger(ServiceModelCodeGenerator.class);

    public static final String DEL_EVT_FTL = "del_evt.ftl";
    public static final String EDIT_EVT_FTL = "edit_evt.ftl";
    public static final String QUERY_EVT_FTL = "query_evt.ftl";
    public static final String SERVICE_FTL = "service.ftl";
    public static final String SERVICE_IMPL_FTL = "service_impl.ftl";
    public static final String CREATE_EVT_FTL = "create_evt.ftl";
    public static final String INFO_FTL = "info.ftl";
    public static final String CONTROLLER_FTL = "controller.ftl";
    public static final String POM_XML_FTL = "pom.xml.ftl";

    private static Set<Class> baseTypes = new HashSet<>();

    private static Set<Class> collectionsTypes = new HashSet<>();

    private static Set<String> notUpdateNames = new HashSet<>();

    static {

        baseTypes.add(Integer.class);
        baseTypes.add(Long.class);
        baseTypes.add(Boolean.class);
        baseTypes.add(Short.class);
        baseTypes.add(Byte.class);
        baseTypes.add(String.class);
        baseTypes.add(Double.class);
        baseTypes.add(Float.class);
        baseTypes.add(Date.class);

        collectionsTypes.add(List.class);
        collectionsTypes.add(Set.class);
        collectionsTypes.add(Map.class);

        notUpdateNames.add("addTime");
        notUpdateNames.add("updateTime");
        notUpdateNames.add("lastUpdateTime");
        notUpdateNames.add("sn");
    }


    private static final ContextHolder<String, Object> context = ContextHolder.build(false);


    /**
     * 生成 POM 文件
     *
     * @param mavenProject
     * @param controllerDir 控制器模块绝对目录，为空则和实体层放在同个 pom 模块
     * @param serviceDir    服务层模块绝对目录，为空则和实体层放在同个 pom 模块
     * @param genParams
     */
    public static void tryGenPomFile(MavenProject mavenProject, String controllerDir, String serviceDir, Map<String, Object> genParams) throws Exception {

        //如果没有包名，也没有发现实体类
        if (!StringUtils.hasText(modulePackageName()) || !hasEntityClass()) {
            return;
        }

        String serviceArtifactId = "";

        Map<String, Object> params = MapUtils.put(context.getAll(false))
                .put("parent", mavenProject.getParent())
                .put("groupId", mavenProject.getGroupId())
                .put("version", mavenProject.getVersion())
                .put("packaging", mavenProject.getPackaging())
                .put("entities", mavenProject.getArtifact())
                .build();

        File pomFile = new File(serviceDir, "../../../pom.xml").getCanonicalFile();

        serviceArtifactId = (mavenProject.getBasedir().getParentFile().getName() + "-" + pomFile.getParentFile().getName());

        if (!pomFile.exists()) {

            params.put("artifactId", serviceArtifactId.toLowerCase());

            genFileByTemplate(POM_XML_FTL, params, pomFile.getAbsolutePath());
        }

        if (!pomFile.exists()) {
            serviceArtifactId = "";
        }

        pomFile = new File(controllerDir, "../../../pom.xml").getCanonicalFile();

        if (!pomFile.exists()) {

            params.put("artifactId", (mavenProject.getBasedir().getParentFile().getName() + "-" + pomFile.getParentFile().getName()).toLowerCase());

            if (StringUtils.hasText(serviceArtifactId)) {
                params.put("services", MapUtils.put("artifactId", serviceArtifactId).build());
            }

            genFileByTemplate(POM_XML_FTL, params, pomFile.getAbsolutePath());
        }

    }

    /**
     * 生成 Spring boot auto stater 文件
     *
     * @param mavenProject
     * @param controllerDir 控制器模块绝对目录，为空则和实体层放在同个 pom 模块
     * @param params
     */
    public static void tryGenSpringBootStarterFile(MavenProject mavenProject, String controllerDir, String serviceDir, Map<String, Object> params) throws Exception {

        //如果没有包名，也没有发现实体类
        if (!StringUtils.hasText(modulePackageName()) || !hasEntityClass()) {
            return;
        }

        params.putAll(context.getAll(false));

        String prefix = serviceDir + File.separator
                + modulePackageName().replace('.', File.separatorChar)
                + File.separator + moduleName();

        genFileByTemplate("ServicePlugin.ftl", params, prefix + "Plugin.java");

        genFileByTemplate("SpringConfiguration.ftl", params, prefix + "SpringConfiguration.java");

        genFileByTemplate("spring.factories.ftl", params, serviceDir + File.separator + ".."
                + File.separator + "resources" + File.separator + "META-INF" + File.separator + "spring.factories");
    }

    public static String splitAndFirstToUpperCase(String moduleName) {
        return splitAndFirstToUpperCase(moduleName, "-", "_");
    }

    /**
     * 用指定的分隔符分隔，并且把首字母大写
     *
     * @param str
     * @return
     */
    public static String splitAndFirstToUpperCase(String str, String... regexDelimiters) {

        return Stream.of(str.split(String.format("[%s]", Stream.of(regexDelimiters).collect(Collectors.joining()))))
                .map(txt -> txt.trim())
                .filter(StringUtils::hasText)
                .map(StringUtils::capitalize)
                .collect(Collectors.joining());

    }

    /**
     * 根据Maven目录样式生成 控制器，服务接口，请求和返回值
     *
     * @param classLoader
     * @param controllerDir 控制器模块绝对目录，为空则和实体层放在同个 pom 模块
     * @param serviceDir    服务层模块绝对目录，为空则和实体层放在同个 pom 模块
     * @param genParams
     */
    public static void genCodeAsMavenStyle(MavenProject mavenProject, ClassLoader classLoader, String buildOutputDirectory, String controllerDir, String serviceDir, Map<String, Object> genParams) throws Exception {

//            File file = new File(project.getBuild().getOutputDirectory());
        File file = new File(buildOutputDirectory);

        if (!file.exists()) {
            logger.warn("***" + buildOutputDirectory + "目录不存在");
            return;
        }

        String canonicalPath = file.getCanonicalPath();

        file = new File(canonicalPath);

        final int suffixLen = ".class".length();

        // logger.info("Files:" + FileUtils.listFiles(file, null, true));

        List<Class<?>> classList = FileUtils.listFiles(file, new String[]{"class"}, true)
                .stream().filter(File::isFile)
                .map(f -> f.getAbsolutePath().substring(canonicalPath.length() + 1)
                        .replace('/', '.')
                        .replace('\\', '.')
                        .replace("..", "."))
                .map(fn -> fn.substring(0, fn.length() - suffixLen))
                .map(n -> {
                    try {
                        return classLoader != null ? classLoader.loadClass(n) : Class.forName(n);
                    } catch (ClassNotFoundException e) {
                        throw new RuntimeException(e);
                    }
                }).filter(clazz -> clazz.getAnnotation(Entity.class) != null)
                .collect(Collectors.toList());

        if (classList.isEmpty()) {
            return;
        }

        hasEntityClass(true);

        //获取包名最端的类，把最短的包名，做为模块的包名
        Class tempClass = null;


        //如果包名没有确定，尝试获取实体类包名最短的为包名
        if (!StringUtils.hasText(modulePackageName())) {

            for (Class<?> entityClass : classList) {
                if (tempClass == null
                        || tempClass.getPackage().getName().length() > entityClass.getPackage().getName().length()) {
                    tempClass = entityClass;
                }
            }

            modulePackageName(upPackage(tempClass.getPackage().getName()));
        }

        //如果模块名没有确定
        if (!StringUtils.hasText(moduleName())) {

            String modulePackageName = modulePackageName();

            String moduleName = "";

            if (modulePackageName != null
                    && modulePackageName.contains(".")) {
                //自动获取模块的包名的最后一个包名为模块的包名, eg.  com.levin.xx.member --> member
                moduleName = modulePackageName.substring(modulePackageName.lastIndexOf('.') + 1);
            } else {
                //自动获取项目目录的上级目录做为模块的包名
                //要考虑为服务类和控制器类和实体在同一个项目的情况
                moduleName = splitDir() ? mavenProject.getBasedir().getParentFile().getName() : mavenProject.getBasedir().getName();
            }

            moduleName(splitAndFirstToUpperCase(moduleName));
        }

        logger.info(mavenProject.getArtifactId() + " *** modulePackageName = " + modulePackageName() + " , moduleName = " + moduleName());

        ///////////////////////////////////////////////
        for (Class<?> clazz : classList) {
            logger.info("*** 开始尝试生成实体类[" + clazz.getName() + "]相关的代码，服务目录[" + serviceDir + "],控制器目录[" + controllerDir + "]...");
            try {
                genCodeByEntityClass(clazz, serviceDir, controllerDir, genParams);
            } catch (Exception e) {
                logger.warn(" *** 实体类" + clazz + " 代码生成错误", e);
            }
        }

    }

    private static String servicePackage() {
        return modulePackageName() + ".services." + subPkgName();
    }

    private static String controllerPackage() {
        return modulePackageName() + ".controller." + subPkgName();
    }


    private static Boolean hasEntityClass(boolean newValue) {
        return context.put(ExceptionUtils.getInvokeMethodName(), newValue);
    }

    private static Boolean hasEntityClass() {
        return context.get(ExceptionUtils.getInvokeMethodName());
    }


    public static Class entityClass(Class newValue) {
        return context.put(ExceptionUtils.getInvokeMethodName(), newValue);
    }

    public static Class entityClass() {
        return context.get(ExceptionUtils.getInvokeMethodName());
    }


    public static Boolean splitDir(boolean newValue) {
        return context.put(ExceptionUtils.getInvokeMethodName(), newValue);
    }

    public static Boolean splitDir() {
        return context.get(ExceptionUtils.getInvokeMethodName());
    }


    public static String moduleName(String newValue) {
        return context.put(ExceptionUtils.getInvokeMethodName(), newValue);
    }

    public static String moduleName() {
        return context.get(ExceptionUtils.getInvokeMethodName());
    }

    public static String modulePackageName(String newValue) {
        return context.put(ExceptionUtils.getInvokeMethodName(), newValue);
    }

    public static String modulePackageName() {
        return context.get(ExceptionUtils.getInvokeMethodName());
    }

    public static String upPackage(String packageName) {
        return upLevel(packageName, '.');
    }

    /**
     * 包名或是目录向上一级
     * 根为空字符串
     *
     * @param packageName
     * @return
     */
    public static String upLevel(String packageName, char delim) {

        int lastIndexOf = packageName.replace("" + delim + delim, "" + delim).lastIndexOf(delim);

        //.eg  ""  "." "com" ".a" ".com" "com.a.b.c"

        if (lastIndexOf <= 0) {
            return "";
        }

        return packageName.substring(0, lastIndexOf);
    }

    /**
     * 实体转服务模型
     *
     * @param entityClass 实体类
     */
    public static void genCodeByEntityClass(Class entityClass, String serviceDir, String controllerDir
            , Map<String, Object> entityMapping) throws Exception {

        entityClass(entityClass);

        if (entityMapping == null) {
            entityMapping = new LinkedHashMap<>();
        }

        List<FieldModel> fields = buildFieldModel(entityClass, entityMapping, true);

        Map<String, Object> params = MapUtils.put(context.getAll(true)).build();

        buildInfo(entityClass, fields, serviceDir, params);

        buildEvt(entityClass, fields, serviceDir, params);

        buildService(entityClass, fields, serviceDir, params);

        buildController(entityClass, fields, controllerDir, params);

    }


    private static String subPkgName() {
        return subPkgName(entityClass(), modulePackageName());
    }


    /***
     * //获取 模块包名 往下一级剩下的包名部分
     * 如类名   com.levin.member.entities.weixin.User
     *         模块包名是 com.levin.member
     *         返回 weixin.user
     *
     * @param entityClass
     * @param modulePackageName
     * @return
     */
    private static String subPkgName(Class entityClass, final String modulePackageName) {

        String name = entityClass.getName();

        if (name.startsWith(modulePackageName)) {
            //获取 模块包名 往下一级剩下的包名部分
            name = name.substring(modulePackageName.length() + 1).toLowerCase();

            //取下一级剩下的包名部分
            return name.contains(".") ? name.substring(name.indexOf('.') + 1) : name;

        } else {
            return entityClass.getSimpleName().toLowerCase();
        }
    }

    private static void buildInfo(Class entityClass, List<FieldModel> fields, String srcDir, Map<String, Object> params) throws Exception {

        genCode(entityClass, INFO_FTL, fields, srcDir,
                servicePackage() + ".info",
                entityClass.getSimpleName() + "Info");

    }

    private static void buildEvt(Class entityClass, List<FieldModel> fields, String srcDir, Map<String, Object> paramsMap) throws Exception {

        List<FieldModel> tempFiles = copyAndFilter(fields, "createTime", "updateTime", "lastUpdateTime");

        final String pkgName = servicePackage() + ".req";

        final Consumer<Map<String, Object>> mapConsumer = (map) -> map.put("fields", tempFiles);

        genCode(entityClass, CREATE_EVT_FTL, fields, srcDir,
                pkgName, "Create" + entityClass.getSimpleName() + "Req", mapConsumer);

        genCode(entityClass, EDIT_EVT_FTL, fields, srcDir,
                pkgName, "Edit" + entityClass.getSimpleName() + "Req", mapConsumer);

        //删除
        genCode(entityClass, DEL_EVT_FTL, fields, srcDir,
                pkgName, "Delete" + entityClass.getSimpleName() + "Req");

        //查询
        genCode(entityClass, QUERY_EVT_FTL, fields, srcDir,
                pkgName, "Query" + entityClass.getSimpleName() + "Req", params -> {
                    params.put("servicePackageName", servicePackage());
                });
    }


    private static void buildService(Class entityClass, List<FieldModel> fields, String srcDir, Map<String, Object> paramsMap) throws Exception {

        final String pkgName = servicePackage();

        final String serviceName = entityClass.getSimpleName() + "Service";

        genCode(entityClass, SERVICE_FTL, fields, srcDir, pkgName, serviceName);

        genCode(entityClass, SERVICE_IMPL_FTL, fields, srcDir, pkgName, serviceName + "Impl"
                , params -> {
                    params.put("servicePackageName", pkgName);
                    params.put("serviceName", serviceName);
                    params.putAll(paramsMap);
                });
    }


    private static void buildController(Class entityClass, List<FieldModel> fields, String srcDir, Map<String, Object> paramsMap) throws Exception {

        final Consumer<Map<String, Object>> mapConsumer = (params) -> {
            params.put("servicePackageName", servicePackage());
            params.put("serviceName", entityClass.getSimpleName() + "Service");
            params.putAll(paramsMap);
        };

        genCode(entityClass, CONTROLLER_FTL, fields, srcDir, controllerPackage(), entityClass.getSimpleName() + "Controller", mapConsumer);

    }

    /**
     * @param entityClass
     * @param template
     * @param fields
     * @param srcDir
     * @param classPackageName
     * @param className
     * @param callbacks
     * @throws Exception
     */
    private static void genCode(Class entityClass, String template, List<FieldModel> fields, String srcDir,
                                String classPackageName, String className, Consumer<Map<String, Object>>... callbacks) throws Exception {

        //去除
        classPackageName = classPackageName.replace("..", ".");

        Map<String, Object> params = getBaseInfo(entityClass, fields, classPackageName, className);

        if (callbacks != null) {
            for (Consumer<Map<String, Object>> callback : callbacks) {
                callback.accept(params);
            }
        }

        String genFilePath = srcDir + File.separator
                + classPackageName.replace(".", File.separator)
                + File.separator + className + ".java";

        genFileByTemplate(template, params, genFilePath);
    }


    private static Map<String, Object> getBaseInfo(Class entityClass, List<FieldModel> fields, String packageName, String genClassName) {

        final String desc = entityClass.isAnnotationPresent(Schema.class)
                ? ((Schema) entityClass.getAnnotation(Schema.class)).description()
                : entityClass.getSimpleName();

        Map<String, Object> params = new LinkedHashMap<>();

        params.put("entityClassPackage", entityClass.getPackage().getName());
        params.put("entityClassName", entityClass.getName());
        params.put("entityName", entityClass.getSimpleName());

        params.put("packageName", packageName);
        params.put("className", genClassName);

        params.put("desc", desc);

        params.put("serialVersionUID", "" + entityClass.getName().hashCode());

        params.put("fields", fields);

        params.put("pkField", getPkField(entityClass, fields));

        return params;
    }


    private static List<FieldModel> copyAndFilter(List<FieldModel> fields, String... filterNames) {
        return fields.stream()
                .filter(fm -> !Arrays.asList(filterNames).contains(fm.name))
                .collect(Collectors.toList());
    }


    private static FieldModel getPkField(Class entityClass, List<FieldModel> fields) {

        for (FieldModel field : fields) {
            if (field.getPk()) {
                return field;
            }
        }

        return null;
    }

    private static void genFileByTemplate(String template, Map<String, Object> params, String fileName) throws Exception {

        File file = new File(fileName);

        if (file.exists()) {
            logger.info("目标源文件：" + file.getAbsoluteFile().getCanonicalPath() + " 已经存在，不覆盖。");
            return;
        }

        file.getParentFile().mkdirs();

        Writer hWriter = new OutputStreamWriter(new FileOutputStream(fileName), "utf-8");

        try {
            getTemplate(template).process(params, hWriter);
        } finally {
            hWriter.close();
        }

    }

    private static List<FieldModel> buildFieldModel(Class entityClass, Map<String, Object> entityMapping, boolean excess/*是否生成约定处理字段，如：枚举新增以Desc结尾的字段*/) throws Exception {

        Object obj = entityClass.newInstance();

        List<FieldModel> list = new ArrayList<>();

        final List<Field> declaredFields = new LinkedList<>();

        ResolvableType resolvableTypeForClass = ResolvableType.forClass(entityClass);

        //  System.out.println("found " + clzss + " : " + field);
        ReflectionUtils.doWithFields(entityClass, declaredFields::add);

        // Field.setAccessible(declaredFields, true);

        for (Field field : declaredFields) {

            field.setAccessible(true);

            if (Modifier.isStatic(field.getModifiers())) {
                continue;
            }

            if (field.isAnnotationPresent(Ignore.class)) {
                continue;
            }

            Class<?> fieldType = ResolvableType.forField(field, resolvableTypeForClass).resolve(field.getType());


            if (field.getType() != fieldType) {
                System.out.println("*** " + entityClass + " 发现泛型字段 : " + field + " --> " + fieldType);
            }


            if (collectionsTypes.contains(fieldType)) {
                //暂不支持集合类型
                continue;
            }


            FieldModel fieldModel = new FieldModel();
            fieldModel.setName(field.getName());
            fieldModel.setLength(field.isAnnotationPresent(Column.class) ? field.getAnnotation(Column.class).length() : -1);

            fieldModel.setType(fieldType.getSimpleName());

            fieldModel.setClassType(fieldType);

            fieldModel.setBaseType(baseTypes.contains(fieldType));

            fieldModel.setEnums(fieldType.isEnum());
            fieldModel.setCollections(collectionsTypes.contains(fieldType));

            fieldModel.setComplex(!fieldType.isPrimitive()
                    && !fieldModel.getBaseType()
                    && !fieldModel.getEnums()
                    && !fieldModel.getCollections());

            if (fieldModel.getComplex()) {
                //得到包名 com.oaknt.udf.entities - com.oaknt.udf.servicess.sample.info;

                String typePackageName = fieldType.getPackage().getName();

                typePackageName = typePackageName.replace("entities", "services") + "."
                        + fieldType.getSimpleName().toLowerCase() + ".info";

                fieldModel.setComplexClassPackageName(typePackageName);

                fieldModel.getImports().add(typePackageName + ".*");

                //  fieldModel.infoClassName =  typePackageName + "." + fieldType.getSimpleName() + "Info";
            }

            boolean hasSchema = field.isAnnotationPresent(Schema.class);
            Schema schema = field.getAnnotation(Schema.class);
            fieldModel.setDesc(hasSchema ? schema.description() : field.getName());
            fieldModel.setDescDetail(hasSchema ? schema.description() : "");
            if (!hasSchema) {
                boolean isDesc = field.isAnnotationPresent(Desc.class);
                Desc desc = field.getAnnotation(Desc.class);
                fieldModel.setDesc(isDesc ? desc.value() : field.getName());
                fieldModel.setDescDetail(isDesc ? desc.detail() : "");
            }

            fieldModel.setPk(field.isAnnotationPresent(Id.class));
            fieldModel.setLike(field.isAnnotationPresent(Like.class));
            fieldModel.setNotUpdate(fieldModel.getPk() || notUpdateNames.contains(fieldModel.getName()) || fieldModel.getComplex());
            if (fieldModel.getPk()) {
                fieldModel.setRequired(true);
                fieldModel.setIdentity(field.isAnnotationPresent(GeneratedValue.class)
                        && !field.getAnnotation(GeneratedValue.class).strategy().equals(GenerationType.AUTO));
            } else {
                fieldModel.setUk(field.isAnnotationPresent(Column.class) && field.getAnnotation(Column.class).unique());
                fieldModel.setRequired(field.isAnnotationPresent(Column.class) && !field.getAnnotation(Column.class).nullable());
            }
            if (field.isAnnotationPresent(ManyToOne.class) ||
                    field.isAnnotationPresent(OneToOne.class)) {
                fieldModel.setComplex(true);
                if (field.isAnnotationPresent(ManyToOne.class)) {
                    fieldModel.setLazy(field.getAnnotation(ManyToOne.class).fetch().equals(FetchType.LAZY));
                } else if (field.isAnnotationPresent(OneToOne.class)) {
                    fieldModel.setLazy(field.getAnnotation(OneToOne.class).fetch().equals(FetchType.LAZY));
                }
                Object aClass = entityMapping.get(field.getName());
                if (aClass instanceof Class) {
                    fieldModel.setInfoClassName(((Class) aClass).getPackage().getName() + "." + ((Class) aClass).getSimpleName());
                }
                fieldModel.setTestValue("null");
            }

            //生成注解
            ArrayList<String> annotations = new ArrayList<>();

            if (fieldModel.getRequired()) {
                annotations.add("@NotNull");
            }

            //
            if (field.isAnnotationPresent(InjectVar.class)) {
                annotations.add("@" + InjectVar.class.getSimpleName() + "");
                fieldModel.getImports().add(InjectVar.class.getName());
            }

            if (field.isAnnotationPresent(SecurityDomain.class)) {
                annotations.add("@" + SecurityDomain.class.getSimpleName());
                fieldModel.getImports().add(SecurityDomain.class.getName());
            }


            if (fieldModel.getClassType().equals(String.class)
                    && fieldModel.getLength() != -1
                    && !fieldModel.getName().endsWith("Body")) {
                boolean isLob = field.isAnnotationPresent(Lob.class);
                if (isLob) {
                    fieldModel.setLength(4000);
                    fieldModel.setTestValue("\"这是长文本正文\"");
                }
                if (fieldModel.getLength() != 255) {
                    annotations.add("@Size(max = " + fieldModel.getLength() + ")");
                    fieldModel.setTestValue("\"这是文本" + fieldModel.getLength() + "\"");
                }
            }
            //是否约定
            if (fieldModel.getName().endsWith("Pct")) {
                annotations.add("@Min(0)");
                annotations.add("@Max(100)");
                fieldModel.setTestValue("50");
            } else if (fieldModel.getName().endsWith("Ppt")) {
                annotations.add("@Min(0)");
                annotations.add("@Max(1000)");
                fieldModel.setTestValue("500");
            } else if (field.isAnnotationPresent(Pattern.class)) {
                String regexp = field.getAnnotation(Pattern.class).regexp();
                if (!StringUtils.isEmpty(regexp)) {
                    regexp = regexp.replace("\\", "\\\\");
                    annotations.add("@Pattern(regexp = \"" + regexp + "\")");
                }
            } else if (field.isAnnotationPresent(Size.class)) {
                annotations.add("@Size(min = " + field.getAnnotation(Size.class).min() + " , max = " + field.getAnnotation(Size.class).max() + ")");
            } else if (field.isAnnotationPresent(Min.class)) {
                annotations.add("@Min(" + field.getAnnotation(Min.class).value() + ")");
                fieldModel.setTestValue(field.getAnnotation(Min.class).value() + "");
            } else if (field.isAnnotationPresent(Max.class)) {
                annotations.add("@Max(" + field.getAnnotation(Max.class).value() + ")");
                fieldModel.setTestValue(field.getAnnotation(Max.class).value() + "");
            }

            fieldModel.setAnnotations(annotations);

            if (excess) {
                buildExcess(entityClass, fieldModel);
            }

            String fieldValue = getFieldValue(field.getName(), obj);
            if (fieldValue != null) {
                fieldModel.setHasDefValue(true);
                fieldModel.setTestValue(fieldValue);
            }

            if (fieldModel.getTestValue() == null) {
                if (fieldModel.getName().equals("sn")) {
                    String sn = UUID.randomUUID().toString().replaceAll("-", "").substring(0, 10).toUpperCase();
                    fieldModel.setTestValue("\"" + sn + "\"");
                } else if (fieldModel.getName().equals("areaId")) {
                    fieldModel.setTestValue("\"1\"");
                } else if (fieldModel.enums) {
                    fieldModel.setTestValue(fieldType.getSimpleName() + "." + getEnumByVal(fieldType, 0).name());
                } else if (fieldModel.getClassType().equals(Boolean.class)) {
                    fieldModel.setTestValue("true");
                } else if (fieldModel.getClassType().equals(String.class)) {
                    fieldModel.setTestValue("\"" + fieldModel.getDesc() + "_1\"");
                } else if (fieldModel.getClassType().equals(Integer.class) || fieldModel.getClassType().equals(Long.class)) {
                    fieldModel.setTestValue(fieldModel.getName().endsWith("Id")
                            ? "null" : ("1" + (fieldModel.getClassType().equals(Long.class) ? "L" : "")));
                } else if (fieldModel.getClassType().equals(Double.class)) {
                    fieldModel.setTestValue("0.1d");
                } else if (fieldModel.getClassType().equals(Float.class)) {
                    fieldModel.setTestValue("0.1f");
                } else if (fieldModel.getClassType().equals(Date.class)) {
                    fieldModel.setTestValue("new Date()");
                }
            }

            list.add(fieldModel);
        }
        return list;
    }


    public static String getFieldValue(String fieldName, Object obj) {
        if (fieldName == null || obj == null) {
            return null;
        }
        Field field = ReflectionUtils.findField(obj.getClass(), fieldName);
        assert field != null;
        Object value = ReflectionUtils.getField(field, obj);
        if (value == null) {
            return null;
        }
        return value.toString();
    }

    private static void buildExcess(Class entityClass, FieldModel fieldModel) {

        String name = fieldModel.getName();
        Class type = fieldModel.getClassType();

        if (fieldModel.getEnums()
//                && DescriptiveEnum.class.isAssignableFrom(type)
                && Enum.class.isAssignableFrom(type)
        ) {
            //枚举描述
            fieldModel.setExcessSuffix("Desc");
            fieldModel.setExcessReturnType("String");
            fieldModel.setExcessReturn("return " + name + " != null ? " + name + ".getDesc() : \"\";");
        } else if ((type.equals(Integer.class) || type.equals(Long.class))
                && name.endsWith("Fen")) {
            //分转元
            fieldModel.setExcessSuffix("2Yuan");
            fieldModel.setExcessReturnType("Double");
            fieldModel.setExcessReturn("return " + name + " != null ? new java.math.BigDecimal(" + name + ")\n" +
                    "                .divide(new java.math.BigDecimal(100), 2, java.math.BigDecimal.ROUND_HALF_UP)\n" +
                    "                .doubleValue() : null;");
        } else if ((type.equals(Integer.class) || type.equals(Long.class))
                && name.endsWith("Ppt")) {
            //千分比转百分比
            fieldModel.setExcessSuffix("2Pct");
            fieldModel.setExcessReturnType("Double");
            fieldModel.setExcessReturn("return " + name + " != null ? new java.math.BigDecimal(" + name + ")\n" +
                    "                .divide(new java.math.BigDecimal(10), 1, java.math.BigDecimal.ROUND_HALF_UP)\n" +
                    "                .doubleValue() : null;");
        } else if (fieldModel.getComplex()) {
            String returnName = type.getSimpleName().substring(0, 1).toUpperCase() + type.getSimpleName().substring(1)
                    + "Info";
            String complexName = name.substring(0, 1).toUpperCase() + name.substring(1)
                    + "Info";

            fieldModel.setExcessSuffix("Info");
            fieldModel.setExcessReturnType(returnName);

            fieldModel.setExcessReturn("return " + name + " != null ? " + name + ".get" + complexName + "() : null;");
        }

    }

    private static Template getTemplate(String templatePath) throws IOException {
        //创建一个合适的Configuration对象
        Configuration configuration = new Configuration(Configuration.VERSION_2_3_28);
        DefaultObjectWrapper objectWrapper = new DefaultObjectWrapperBuilder(Configuration.VERSION_2_3_28).build();
        configuration.setObjectWrapper(objectWrapper);

        //这个一定要设置，不然在生成的页面中 会乱码
        configuration.setDefaultEncoding("UTF-8");

        //支持从jar中加载模板
        configuration.setClassForTemplateLoading(ServiceModelCodeGenerator.class, "/");
        //获取页面模版。
        return configuration.getTemplate(MessageFormat.format("/simple-dao/codegen/template/{0}", templatePath));
    }

    private static Enum getEnumByVal(Class ec, int i) {
        Iterator iter = EnumSet.allOf(ec).iterator();

        Enum e;
        do {
            if (!iter.hasNext()) {
                return null;
            }
            e = (Enum) iter.next();
        } while (e.ordinal() != i);

        return e;
    }

    @Data
    @NoArgsConstructor
    @EqualsAndHashCode(of = "name")
    @ToString()
    @Accessors(chain = true)
    public static class FieldModel {

        private String name;

        String prefix;

        private String type;

        private Integer length = -1;

        private Class classType;

        private String desc;

        private String descDetail;

        private Set<String> imports = new LinkedHashSet<>();

        private List<String> annotations = new ArrayList<>();

        private Boolean pk = false;//是否主键字段

        private Boolean uk = false;//是否唯一键

        private Boolean baseType = true;//基础封装类型

        private Boolean enums = false;//是否enum

        private Boolean complex = false;//是否复杂对象

        private String complexClassPackageName;//复杂对象包名

        private Boolean collections = false;//是否集合

        private Boolean required = false;//是否必填

        private Boolean identity; //是否自动增长主键

        private Boolean notUpdate = false;//是否不需要更新

        private Boolean hasDefValue = false;//是否有默认值

        private Boolean lazy = false;//是否lazy

        private String excessSuffix;//生成额外的字段后缀

        private String excessReturnType;//生成额外的返回类型

        private String excessReturn;//生成额外的返回

        private String infoClassName;

        private String testValue;

        private Boolean like;//是否生成模糊查询

    }

}
