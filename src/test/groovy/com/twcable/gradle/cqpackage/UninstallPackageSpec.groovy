/*
 * Copyright 2014-2015 Time Warner Cable, Inc.
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

import spock.lang.Unroll

import static com.twcable.gradle.cqpackage.PackageStatus.NOT_INSTALLED
import static com.twcable.gradle.cqpackage.PackageStatus.NO_PACKAGE
import static com.twcable.gradle.cqpackage.PackageStatus.OK

@SuppressWarnings("GroovyAssignabilityCheck")
class UninstallPackageSpec extends AbstractPackageCommandSpec {

    @Unroll
    def "uninstall package for \"#msg\""() {
        setupSlingSupport(success, msg, installedPackage)

        when:
        def retStatus = UninstallPackage.uninstall("fakepackage", slingServerConfiguration, 20, 10)

        then:
        retStatus == status

        where:
        installedPackage | success | msg                                                 | status
        "fakepackage"    | true    | "Package uninstalled"                               | OK
        "fakepackage"    | false   | "Unable to uninstall package. No snapshot present." | NOT_INSTALLED
        "froble"         | null    | "no package"                                        | NO_PACKAGE
    }

}
