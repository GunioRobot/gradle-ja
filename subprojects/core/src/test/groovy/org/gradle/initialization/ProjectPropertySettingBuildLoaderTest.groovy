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
package org.gradle.initialization

import spock.lang.Specification
import org.gradle.api.initialization.ProjectDescriptor
import org.gradle.api.internal.GradleInternal
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.util.TemporaryFolder
import org.junit.Rule
import org.gradle.util.GUtil
import org.gradle.api.Project

class ProjectPropertySettingBuildLoaderTest extends Specification {
    @Rule public TemporaryFolder tmpDir = new TemporaryFolder();
    final BuildLoader target = Mock()
    final ProjectDescriptor projectDescriptor = Mock()
    final GradleInternal gradle = Mock()
    final ProjectInternal rootProject = Mock()
    final ProjectInternal childProject = Mock()
    final IGradlePropertiesLoader propertiesLoader = Mock()
    final File rootProjectDir = tmpDir.createDir('root')
    final File childProjectDir = tmpDir.createDir('child')
    final ProjectPropertySettingBuildLoader loader = new ProjectPropertySettingBuildLoader(propertiesLoader, target)

    def setup() {
        _ * gradle.rootProject >> rootProject
        _ * rootProject.childProjects >> [child: childProject]
        _ * childProject.childProjects >> [:]
        _ * rootProject.projectDir >> rootProjectDir
        _ * childProject.projectDir >> childProjectDir
    }

    def "delegates to build loader"() {
        given:
        _ * propertiesLoader.mergeProperties(!null) >> [:]

        when:
        loader.load(projectDescriptor, gradle)

        then:
        1 * target.load(projectDescriptor, gradle)
        0 * target._
    }

    def "sets project properties on each project in hierarchy"() {
        given:
        2 * propertiesLoader.mergeProperties([:]) >> [prop: 'value']

        when:
        loader.load(projectDescriptor, gradle)

        then:
        1 * rootProject.setProperty('prop', 'value')
        1 * childProject.setProperty('prop', 'value')
    }

    def "loads project properties from gradle.properties file in project dir"() {
        given:
        GUtil.saveProperties(new Properties([prop: 'rootValue']), new File(rootProjectDir, Project.GRADLE_PROPERTIES))
        GUtil.saveProperties(new Properties([prop: 'childValue']), new File(childProjectDir, Project.GRADLE_PROPERTIES))

        when:
        loader.load(projectDescriptor, gradle)

        then:
        1 * propertiesLoader.mergeProperties([prop: 'rootValue']) >> [prop: 'rootValue']
        1 * propertiesLoader.mergeProperties([prop: 'childValue']) >> [prop: 'childValue']
        1 * rootProject.setProperty('prop', 'rootValue')
        1 * childProject.setProperty('prop', 'childValue')
    }
}
