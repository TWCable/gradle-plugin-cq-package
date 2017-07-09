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

public class RemovePackageTask extends DefaultTask {

    private CqPackageHelper packageHelper;
    private SlingPackageSupportFactory slingPackageSupportFactory;


    public RemovePackageTask() {
        setDescription("Removes the package from CQ. Does not fail if the package was not on " +
            "the server to begin with.");

        packageHelper = CqPackagePlugin.cqPackageHelper(getProject());
        slingPackageSupportFactory = SimpleSlingPackageSupportFactory.INSTANCE;
    }


    @TaskAction
    public void remove() {
        packageHelper.deletePackage(slingPackageSupportFactory);
    }

}
