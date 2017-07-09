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

import com.twcable.gradle.http.HttpResponse;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.tasks.TaskAction;

/**
 * @see CqPackageHelper#validateRemoteBundles()
 */
public class ValidateRemoteBundlesTask extends DefaultTask {

    private CqPackageHelper packageHelper;


    public ValidateRemoteBundlesTask() {
        setDescription("Validates remote bundles in the CQ Package are started correctly");

        packageHelper = CqPackagePlugin.cqPackageHelper(getProject());
    }


    @TaskAction
    public void validate() {
        final HttpResponse resp = this.packageHelper.validateRemoteBundles();
        if (CqPackageHelper.isBadResponse(resp.getCode(), false))
            throw new GradleException("Could not validate bundles: " + resp);
    }

}
