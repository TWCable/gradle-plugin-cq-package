/*
 * Copyright 2014-2015 Time Warner Cable, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.twcable.gradle.cqpackage

import com.twcable.gradle.sling.SlingServersConfiguration
import nebula.test.PluginProjectSpec
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.logging.LogLevel
import org.gradle.testfixtures.ProjectBuilder
import spock.lang.Unroll

import static com.twcable.gradle.cqpackage.CqPackageTestUtils.addProjectToCompile
import static com.twcable.gradle.cqpackage.CqPackageTestUtils.createSubProject

class CqPackagePluginSpec extends PluginProjectSpec {

    def setup() {
        project.logging.level = LogLevel.DEBUG
        project.version = '2.3.4'
        project.apply plugin: 'com.twcable.cq-package'
        project.apply plugin: 'java'

        project.tasks.withType(CreatePackageTask) { CreatePackageTask task ->
            task.bundleInstallRoot = '/apps/install'
        }

        def subproject1 = createSubProject(project, 'subproject1', true)
        addProjectToCompile(project, subproject1)
    }


    def "check added tasks"() {
        expect:
        project.tasks.getByName('upload')
        project.tasks.getByName('install')
        project.tasks.getByName('uninstall')
        project.tasks.getByName('remove')
        project.tasks.getByName('createPackage')

        // the zip file (from "createPackage") should have been added to the list of artifacts
//        project.getConfigurations().getByName("archives").artifacts.find { it.type == 'zip' }
    }


    @Unroll
    def "check ordering for #taskName"() {
        given:
        Task task = project.tasks.getByName(taskName)
        List<Task> afterTasks = mustRunAfterTaskNames.collect { project.tasks.getByName(it) }

        expect:
        task.mustRunAfter.getDependencies(task) == afterTasks as Set

        where:
        taskName         | mustRunAfterTaskNames
        'uninstall'      | ['createPackage']
        'remove'         | ['uninstall']
        'uploadRemote'   | ['remove']
        'installRemote'  | []
        'upload'         | ['uninstall', 'createPackage']
        'installPackage' | ['upload']
    }


    def "check types"() {
        given:
        Project project = ProjectBuilder.builder().build()
        project.apply plugin: 'com.twcable.cq-package'

        expect:
        project.slingServers instanceof SlingServersConfiguration
    }


    @Override
    String getPluginName() {
        return 'com.twcable.cq-package'
    }

}
