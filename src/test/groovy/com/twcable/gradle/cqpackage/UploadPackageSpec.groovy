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

import org.apache.jackrabbit.vault.packaging.impl.PackageManagerImpl
import spock.lang.Unroll

import static com.twcable.gradle.cqpackage.PackageStatus.OK

@SuppressWarnings("GroovyAssignabilityCheck")
class UploadPackageSpec extends AbstractPackageCommandSpec {

    @Unroll
    def "upload package for \"#msg\""() {
        setupSlingSupport(success, msg, installedPackage)
        def packageFile = new File(CqPackageHelperSpec.class.classLoader.getResource("testpackage-1.0.1.zip").getFile())

        when:
        def retStatus = UploadPackage.upload(packageFile, false, slingPackageSupportFactory.create(slingServerConfiguration), new PackageManagerImpl())

        then:
        retStatus == status

        where:
        installedPackage | success | msg                 | status
        "testpackage"    | true    | "Package installed" | OK
    }

}
