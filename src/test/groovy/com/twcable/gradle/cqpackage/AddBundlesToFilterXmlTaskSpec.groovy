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

import nebula.test.ProjectSpec
import org.gradle.api.InvalidUserDataException
import org.gradle.api.logging.LogLevel

import static com.twcable.gradle.GradleUtils.execute
import static com.twcable.gradle.cqpackage.CqPackageTestUtils.setLogLevel

class AddBundlesToFilterXmlTaskSpec extends ProjectSpec {

    AddBundlesToFilterXmlTask addBundlesToFilterXml


    def setup() {
        logLevel = LogLevel.DEBUG

        addBundlesToFilterXml = project.tasks.create("addBundlesToFilterXml", AddBundlesToFilterXmlTask)
    }


    def "explicitly set createPackage"() {
        def filterXml = new File(getProjectDir(), "src/main/content/META-INF/vault/filter.xml")
        filterXml.parentFile.mkdirs()
        filterXml.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<workspaceFilter version=\"1.0\"/>\n")
        def createPackage = project.tasks.create("createPackage", CreatePackageTask)
        addBundlesToFilterXml.createPackageTask = createPackage

        expect:
        !addBundlesToFilterXml.outFile.exists()

        when:
        execute(addBundlesToFilterXml)

        then:
        addBundlesToFilterXml.outFile.exists()
    }


    def "find createPackage from project"() {
        project.tasks.create("createPackage", CreatePackageTask)

        expect:
        !addBundlesToFilterXml.outFile.exists()

        when:
        execute(addBundlesToFilterXml)

        then:
        addBundlesToFilterXml.outFile.exists()
    }


    def "too many CreatePackageTasks in project"() {
        project.tasks.create("createPackage1", CreatePackageTask)
        project.tasks.create("createPackage2", CreatePackageTask)

        when:
        execute(addBundlesToFilterXml)

        then:
        Throwable exp = thrown()
        rootCause(exp) instanceof InvalidUserDataException
    }


    def "missing createPackage"() {
        when:
        execute(addBundlesToFilterXml)

        then:
        Throwable exp = thrown()
        rootCause(exp) instanceof InvalidUserDataException
    }


    static Throwable rootCause(Throwable throwable) {
        Throwable cause = throwable
        while (cause.cause != null) {
            cause = cause.cause
        }
        return cause
    }

}
