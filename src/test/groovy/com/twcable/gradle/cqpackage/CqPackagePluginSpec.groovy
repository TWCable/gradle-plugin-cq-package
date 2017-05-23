/*
 * Copyright 2014-2017 Time Warner Cable, Inc.
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
import org.gradle.api.Task
import org.gradle.api.logging.LogLevel
import spock.lang.Unroll

import static com.twcable.gradle.cqpackage.CqPackageTestUtils.logLevel


class CqPackagePluginSpec extends PluginProjectSpec {

    def setup() {
        logLevel = LogLevel.DEBUG
        project.version = '2.3.4'
        project.apply plugin: 'com.twcable.cq-package'

        project.tasks.withType(CreatePackageTask) { CreatePackageTask task ->
            task.bundleInstallRoot = '/apps/install'
        }
    }


    def "check added tasks"() {
        expect:
        project.tasks.getByName('uploadPackage')
        project.tasks.getByName('installPackage')
        project.tasks.getByName('uninstallPackage')
        project.tasks.getByName('removePackage')
        project.tasks.getByName('createPackage')

        // the zip file (from "createPackage") should have been added to the list of artifacts
        project.getConfigurations().getByName("archives").artifacts.find { it.type == 'zip' }.name == project.name
    }


    @Unroll
    def "check ordering for #taskName"() {
        given:
        Task task = project.tasks.getByName(taskName)
        List<Task> afterTasks = mustRunAfterTaskNames.collect { project.tasks.getByName(it) }

        expect:
        task.mustRunAfter.getDependencies(task) == afterTasks as Set

        where:
        taskName           | mustRunAfterTaskNames
        'uninstallPackage' | ['uninstallBundles']
        'uploadPackage'    | ['createPackage', 'uninstallPackage']
        'installPackage'   | ['uploadPackage', 'uninstallPackage']
    }


    def "check types"() {
        given:
        project.apply plugin: 'com.twcable.cq-package'

        expect:
        project.slingServers instanceof SlingServersConfiguration
    }


    @Override
    String getPluginName() {
        return 'com.twcable.cq-package'
    }

}
