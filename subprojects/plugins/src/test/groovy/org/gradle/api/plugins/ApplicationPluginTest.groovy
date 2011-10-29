/*
 * Copyright 2011 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.gradle.api.plugins

import org.gradle.api.Project
import org.gradle.api.tasks.application.CreateStartScripts
import org.gradle.api.tasks.Copy
import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.bundling.Zip
import org.gradle.util.HelperUtil
import org.gradle.util.Matchers
import spock.lang.Specification
import org.gradle.api.tasks.Sync
import org.gradle.api.file.CopySpec

class ApplicationPluginTest extends Specification {
    private final Project project = HelperUtil.createRootProject();
    private final ApplicationPlugin plugin = new ApplicationPlugin();

    def "applies JavaPlugin and adds convention object with default values"() {
        when:
        plugin.apply(project)

        then:
        project.plugins.hasPlugin(JavaPlugin.class)
        project.convention.getPlugin(ApplicationPluginConvention.class) != null
        project.applicationName == project.name
        project.mainClassName == null
        project.applicationDistribution instanceof CopySpec
    }

    def "adds run task to project"() {
        when:
        plugin.apply(project)

        then:
        def task = project.tasks[ApplicationPlugin.TASK_RUN_NAME]
        task instanceof JavaExec
        task.classpath == project.sourceSets[SourceSet.MAIN_SOURCE_SET_NAME].runtimeClasspath
        task Matchers.dependsOn('classes')
    }

    public void "adds startScripts task to project"() {
        when:
        plugin.apply(project)

        then:
        def task = project.tasks[ApplicationPlugin.TASK_START_SCRIPTS_NAME]
        task instanceof CreateStartScripts
        task.applicationName == project.applicationName
        task.outputDir == project.file('build/scripts')
    }

    public void "adds installApp task to project with default target"() {
        when:
        plugin.apply(project)

        then:
        def task = project.tasks[ApplicationPlugin.TASK_INSTALL_NAME]
        task instanceof Sync
        task.destinationDir == project.file("build/install/${project.applicationName}")
    }

    def "adds distZip task to project"() {
        when:
        plugin.apply(project)

        then:
        def task = project.tasks[ApplicationPlugin.TASK_DIST_ZIP_NAME]
        task instanceof Zip
        task.archiveName == "${project.applicationName}.zip"
    }

    public void "applicationName is configurable"() {
        when:
        plugin.apply(project)
        project.applicationName = "SuperApp";

        then:
        def startScriptsTask = project.tasks[ApplicationPlugin.TASK_START_SCRIPTS_NAME]
        startScriptsTask.applicationName == 'SuperApp'

        def installTest = project.tasks[ApplicationPlugin.TASK_INSTALL_NAME]
        installTest.destinationDir == project.file("build/install/SuperApp")

        def distZipTask = project.tasks[ApplicationPlugin.TASK_DIST_ZIP_NAME]
        distZipTask.archiveName == "SuperApp.zip"
    }

    public void "mainClassName in project delegates to main in run task"() {
        when:
        plugin.apply(project)
        project.mainClassName = "Acme";

        then:
        def run = project.tasks[ApplicationPlugin.TASK_RUN_NAME]
        run.main == "Acme"
    }

    public void "mainClassName in project delegates to mainClassName in startScripts task"() {
        when:
        plugin.apply(project);
        project.mainClassName = "Acme"

        then:
        def startScripts = project.tasks[ApplicationPlugin.TASK_START_SCRIPTS_NAME]
        startScripts.mainClassName == "Acme"
    }
}
