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

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.FileCollection
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.SkipWhenEmpty
import org.gradle.api.tasks.TaskAction

import static com.twcable.gradle.cqpackage.CqPackageHelper.isBadResponse

/**
 * @see CqPackageHelper#validateBundles(FileCollection)
 */
class ValidateBundlesTask extends DefaultTask {
    private FileCollection _bundleFiles


    ValidateBundlesTask() {
        _bundleFiles = project.files()

        description = "Checks all the JARs that are included in the package to make sure they are " +
            "installed and in an ACTIVE state and gives a report of any that are not. This task polls in the " +
            "case the bundles are newly installed."
    }


    @TaskAction
    @SuppressWarnings("GroovyUnusedDeclaration")
    void validate() {
        def packageHelper = CqPackagePlugin.cqPackageHelper(project)
        def resp = packageHelper.validateBundles(bundleFiles)
        if (isBadResponse(resp.code, false)) throw new GradleException("Could not validate bundles: ${resp}")
    }


    void bundles(FileCollection sourceFiles) {
        _bundleFiles = _bundleFiles + sourceFiles
    }


    @InputFiles
    @SkipWhenEmpty
    @PathSensitive(PathSensitivity.NONE)
    FileCollection getBundleFiles() {
        return _bundleFiles
    }

}
