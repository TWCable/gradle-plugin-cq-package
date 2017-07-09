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
package com.twcable.gradle.cqpackage;

import org.gradle.api.DefaultTask;
import org.gradle.api.tasks.TaskAction;

public class UninstallPackageTask extends DefaultTask {
    private CqPackageHelper packageHelper;
    private SimpleSlingPackageSupportFactory slingPackageSupportFactory;


    public UninstallPackageTask() {
        setDescription("Uninstalls the CQ Package. If the package is not on the server, nothing happens. " +
            "(i.e., This does not fail if the package is not on the server.)");

        packageHelper = CqPackagePlugin.cqPackageHelper(getProject());
        slingPackageSupportFactory = SimpleSlingPackageSupportFactory.INSTANCE;
    }


    @TaskAction
    public void uninstall() {
        this.packageHelper.uninstallPackage(slingPackageSupportFactory);
    }

}
