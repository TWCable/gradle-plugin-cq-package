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
import org.gradle.api.tasks.TaskAction

class InstallPackageTask extends DefaultTask {
    private CqPackageHelper packageHelper
    private SimpleSlingPackageSupportFactory slingPackageSupportFactory


    InstallPackageTask() {
        description = "Installs the CQ Package that has been uploaded"

        packageHelper = CqPackagePlugin.cqPackageHelper(project)
        slingPackageSupportFactory = SimpleSlingPackageSupportFactory.INSTANCE
    }


    @TaskAction
    void install() {
        packageHelper.installPackage(slingPackageSupportFactory)
    }

}
