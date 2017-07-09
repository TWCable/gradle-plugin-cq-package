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

import groovy.transform.CompileStatic
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction

import static com.twcable.gradle.cqpackage.CqPackageHelper.isOsgiFile

@CompileStatic
class VerifyBundlesTask extends DefaultTask {

    VerifyBundlesTask() {
        description = "Checks all the JARs that are included in this package to make sure they are OSGi " +
            "compliant, and gives a report of any that are not. Never causes the build to fail."
    }


    @Input
    Collection<File> getBundleFiles() {
        final createProjectTask = CreatePackageTask.from(project)
        return createProjectTask.bundleFiles
    }


    @TaskAction
    @SuppressWarnings("GroovyUnusedDeclaration")
    void verify() {
        for (File file : nonOsgiFiles()) {
            logger.lifecycle "\n${file.name} is not an OSGi file"
        }
    }


    private Collection<File> nonOsgiFiles() {
        final bundleFiles = getBundleFiles()
        return bundleFiles.findAll { !isOsgiFile(it) }
    }

}
