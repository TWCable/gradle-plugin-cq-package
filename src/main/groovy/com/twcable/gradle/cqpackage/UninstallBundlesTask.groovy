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
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction

import static com.twcable.gradle.GradleUtils.extension
import static com.twcable.gradle.cqpackage.CqPackageHelper.isBadResponse

class UninstallBundlesTask extends DefaultTask {
    @Internal
    CqPackageHelper.UninstallBundlePredicate uninstallBundlesPredicate

    private CqPackageHelper packageHelper


    UninstallBundlesTask() {
        description = "Downloads the currently installed package .zip file if it exists, compiles list of " +
            "bundles based off what is currently installed, then stops and uninstalls each bundle individually."

        this.uninstallBundlesPredicate = new CqPackageHelper.UninstallBundlePredicate() {
            @Override
            boolean eval(String symbolicName) {
                return true
            }
        }

        packageHelper = (CqPackageHelper)extension(project, CqPackageHelper)
    }


    @TaskAction
    @SuppressWarnings("GroovyUnusedDeclaration")
    void uninstallBundles() {
        def resp = this.packageHelper.uninstallBundles(uninstallBundlesPredicate)
        if (isBadResponse(resp.code, true)) throw new GradleException("Could not uninstall bundles: ${resp}")
    }

}
