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

import com.twcable.gradle.GradleUtils
import nebula.test.ProjectSpec

class VerifyBundlesTaskSpec extends ProjectSpec {

    def "smoke test"() {
        given:
        final task = project.tasks.create("verifyBundles", VerifyBundlesTask)

        when:
        GradleUtils.execute(task)

        then:
        "just making sure nothing blows up on a fully-minimal setup"
    }


    def "smoke test1"() {
        given:
        final task = project.tasks.create("verifyBundles", VerifyBundlesTask)
        final createPackage = project.tasks.create("createPackage", CreatePackageTask)
        createPackage

        when:
        GradleUtils.execute(task)

        then:
        "just making sure nothing blows up on a fully-minimal setup"
    }

}
