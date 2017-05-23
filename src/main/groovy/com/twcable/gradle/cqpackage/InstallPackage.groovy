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

import com.twcable.gradle.sling.SlingServerConfiguration
import com.twcable.gradle.sling.SlingServersConfiguration
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import org.gradle.api.GradleException

import javax.annotation.Nonnull

@Slf4j
@CompileStatic
class InstallPackage {

    private static final CqPackageCommand.SuccessFalseHandler falseStatusHandler =
        { SlingServerConfiguration sc, String jsonMsg ->
            switch (jsonMsg) {
                case 'no package':
                case '':
                    // neither of these should happen since we check for the existence of the package first, but...
                    return PackageStatus.NO_PACKAGE
                default:
                    return Status.UNKNOWN
            }
        } as CqPackageCommand.SuccessFalseHandler

    /**
     * Iterates through all of the servers in "slingServersConfiguration" and installs the given package on them.
     *
     * @see #install(String, SlingPackageSupport)
     * @see #consumeStatus(Status, String, SlingServerConfiguration)
     */
    static void install(String packageName, SlingServersConfiguration slingServersConfiguration, SlingPackageSupportFactory factory) {
        slingServersConfiguration.each { serverConfig ->
            def status = install(packageName, factory.create(serverConfig))
            consumeStatus(status, packageName, serverConfig)
        }
    }

    /**
     * "Consumes" the status, mostly to appropriately log what happened.
     */
    static void consumeStatus(Status status, String packageName, SlingServerConfiguration serverConfig) {
        switch (status) {
            case Status.OK:
                log.info("\"${packageName}\" is installed on ${serverConfig.name}"); break
            case PackageStatus.NO_PACKAGE:
                throw new GradleException("\"${packageName}\" is not currently on ${serverConfig.name}, so can not install it")
            case Status.SERVER_INACTIVE:
            case Status.SERVER_TIMEOUT:
                break // ignore
            default:
                throw new IllegalArgumentException("Unknown status ${status} when trying to install ${packageName} on ${serverConfig.name}")
        }
    }

    /**
     * Installs the given package using the provided server configuration.
     *
     * @param packageName the name of the package to install
     * @param packageSupport the configuration of the server to install on
     *
     * @return the {@link PackageStatus} of doing the install
     */
    @Nonnull
    static Status install(String packageName, SlingPackageSupport packageSupport) {
        return CqPackageCommand.doCommand("install", packageName, packageSupport, falseStatusHandler)
    }

}
