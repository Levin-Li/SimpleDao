package com.levin.commons.dao.codegen.plugins;

import com.levin.commons.utils.MapUtils;
import org.apache.commons.io.FileUtils;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import org.springframework.util.StringUtils;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.springframework.util.StringUtils.hasText;


/**
 * 生成项目模板
 */
@Mojo(name = "gen-project-template", requiresDependencyResolution = ResolutionScope.COMPILE_PLUS_RUNTIME)
public class ProjectTemplateGeneratorMojo extends BaseMojo {

    /**
     * 生成的子模块名称
     * 如果为空，则表示示例代码生成在当前模块,不会创建子模块
     * 如果当前模块不是 pom 模块，则subModuleName名称没有意义，也不会创建子模块。
     */
    @Parameter
    private String subModuleName = "";

    /**
     * 模块包名
     * 如果没有配置，则自动获取 pom 文件中定义的 GroupId
     */
    @Parameter
    private String modulePackageName = "";


    @Override
    public void executeMojo() throws MojoExecutionException, MojoFailureException {

        try {

            File basedir = mavenProject.getBasedir();

            boolean isPomModule = "pom".equalsIgnoreCase(mavenProject.getPackaging());

            if (this.subModuleName != null) {
                subModuleName = subModuleName.trim();
            }

            if (!hasText(this.modulePackageName)) {
                modulePackageName = mavenProject.getGroupId();
            }

            boolean hasSubModule = hasText(this.subModuleName);

            File entitiesModuleDir = new File(basedir, isPomModule ? this.subModuleName + "/entities" : "");

            entitiesModuleDir.mkdirs();

            File entitiesDir = new File(entitiesModuleDir, "src/main/java/" + modulePackageName.replace('.', '/') + "/entities");

            entitiesDir.mkdirs();

            new File(entitiesModuleDir, "src/main/resources").mkdirs();

            String resTemplateDir = "simple-dao/codegen/template/example/";

            //拷贝 POM 文件

            MapUtils.Builder<String, String> mapBuilder =
                    MapUtils.put("CLASS_PACKAGE_NAME", modulePackageName + ".entities")
                            .put("modulePackageName", modulePackageName)
                            .put("now", new Date().toString());

            copyAndReplace(false, resTemplateDir + "TableOption.java", new File(entitiesDir, "TableOption.java"), mapBuilder.build());

            copyAndReplace(false, resTemplateDir + "Group.java", new File(entitiesDir, "Group.java"), mapBuilder.build());
            copyAndReplace(false, resTemplateDir + "User.java", new File(entitiesDir, "User.java"), mapBuilder.build());
            copyAndReplace(false, resTemplateDir + "Task.java", new File(entitiesDir, "Task.java"), mapBuilder.build());


            if (!isPomModule) {
                return;
            }

            final Map<String, String> pluginInfo = getPluginInfo("codegen-plugin.");

            mavenProject.getBuildPlugins().stream()
                    .filter(plugin -> "simple-dao-codegen".equalsIgnoreCase(plugin.getArtifactId()))
                    .filter(dependency -> dependency.getGroupId().toLowerCase().contains(".levin"))
                    .findAny()
                    .ifPresent(plugin -> {

                        logger.info("find codegen plugin: " + plugin);

                        pluginInfo.putIfAbsent("codegen-plugin.groupId", plugin.getGroupId());
                        pluginInfo.putIfAbsent("codegen-plugin.version", plugin.getVersion());

                        plugin.getDependencies().stream()
                                .filter(dependency -> "service-support".equalsIgnoreCase(dependency.getArtifactId()))
                                .filter(dependency -> dependency.getGroupId().toLowerCase().contains(".levin"))
                                .findAny().ifPresent(dependency -> {

                            logger.info("find service-support: " + dependency);

                            pluginInfo.putIfAbsent("service-support.groupId", dependency.getGroupId());
                            pluginInfo.putIfAbsent("service-support.version", dependency.getVersion());
                        });
                    });


            mapBuilder
                    .put("parent.groupId", mavenProject.getGroupId())
                    .put("parent.artifactId", mavenProject.getArtifactId())
                    .put("parent.version", mavenProject.getVersion())
                    .put(pluginInfo);


            if (hasSubModule) {

                //生成 POM 文件

                mapBuilder.put("modules", "\n<module>" + entitiesModuleDir.getName() + "</module>\n");
                mapBuilder.put("project.artifactId", subModuleName);

                mapBuilder.put("project.packaging", "pom");

                copyAndReplace(false, resTemplateDir + "root-pom.xml", new File(new File(basedir, subModuleName), "pom.xml"), mapBuilder.build());

                //变更父构建名称
                mapBuilder.put("parent.artifactId", subModuleName);

            }

            mapBuilder.put("modules", "");

            mapBuilder.put("project.packaging", "jar");

            //设置构建名称为：父节点的名称加上本节点的名称
            mapBuilder.put("project.artifactId", (hasSubModule ? subModuleName : mavenProject.getArtifactId()) + "-" + entitiesModuleDir.getName());

            copyAndReplace(false, resTemplateDir + "entities-pom.xml", new File(entitiesModuleDir, "pom.xml"), mapBuilder.build());

            String moduleName = hasSubModule ? this.subModuleName : entitiesModuleDir.getName();

            //如果是 root 项目
            if (mavenProject.isExecutionRoot()) {
                //直接整个覆盖
                mapBuilder.put("modules", hasText(moduleName) ? "<module>" + moduleName + "</module>\n" : "");

                if (mavenProject.getParent() == null
                        || !hasText(mavenProject.getParent().getGroupId())) {

                    mapBuilder.put("parent.groupId", "org.springframework.boot")
                            .put("parent.artifactId", "spring-boot-starter-parent")
                            .put("parent.version", "2.3.4.RELEASE");
                } else {
                    mapBuilder.put("parent.groupId", mavenProject.getParent().getGroupId())
                            .put("parent.artifactId", mavenProject.getParent().getArtifactId())
                            .put("parent.version", mavenProject.getParent().getVersion());
                }

                //
                mapBuilder.put("project.groupId", mavenProject.getGroupId())
                        .put("project.artifactId", mavenProject.getArtifactId())
                        .put("project.version", mavenProject.getVersion());


                File pomFile = new File(basedir, "pom.xml");

                // 自动生成标记，请不要删除本行 simple-dao-codegen-flag=${parent.groupId}
                boolean overwrite = !FileUtils.readFileToString(pomFile, "utf-8").contains("simple-dao-codegen-flag=" + mapBuilder.build().get("parent.groupId"));

                copyAndReplace(overwrite, resTemplateDir + "root-pom.xml", pomFile, mapBuilder.build());

            } else {
                updatePom(mavenProject, moduleName);
            }

        } catch (Exception e) {
            getLog().error(mavenProject.getArtifactId() + " 模块模板生成错误：" + e.getMessage(), e);
        }
    }

    private static void updatePom(MavenProject mavenProject, String... moduleNames) throws IOException {

        List<String> stringList = Stream.of(moduleNames)
                .filter(StringUtils::hasText)
                .map(String::trim)
                .collect(Collectors.toList());

        if (stringList.isEmpty()) {
            return;
        }

        File basedir = mavenProject.getBasedir();

        //修改现有的 Pom 文件,增加模块名称
        String genFlags = "###simple-dao-code-gen-module-flag###";

        String moduleList = "\n<!-- " + genFlags + " -->\n";

        for (String moduleName : stringList) {
            moduleList += "<module>" + moduleName + "</module>\n";
        }

        File pomFile = new File(basedir, "pom.xml");

        StringBuilder pomContent = new StringBuilder(FileUtils.readFileToString(pomFile, "utf-8"));

        //如果没有生成标记
        if (pomContent.indexOf(genFlags) == -1) {

            int indexOf = pomContent.indexOf("</modules>");

            if (indexOf == -1) {
                pomContent.insert(pomContent.indexOf("</project>"), "<modules>\n" + moduleList + "\n</modules>\n");
            } else {
                pomContent.insert(indexOf, moduleList);
            }

            FileUtils.write(pomFile, pomContent, "utf-8");
        }
    }


}
