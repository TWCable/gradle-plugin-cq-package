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

import javax.annotation.Nonnull

import static com.twcable.gradle.cqpackage.PackageStatus.NOT_INSTALLED
import static com.twcable.gradle.cqpackage.PackageStatus.NO_PACKAGE
import static com.twcable.gradle.cqpackage.PackageStatus.UNKNOWN
import static com.twcable.gradle.cqpackage.Status.OK
import static com.twcable.gradle.cqpackage.Status.SERVER_INACTIVE
import static com.twcable.gradle.cqpackage.Status.SERVER_TIMEOUT

@Slf4j
@CompileStatic
class UninstallPackage {

    private static final CqPackageCommand.SuccessFalseHandler falseStatusHandler =
        { SlingServerConfiguration sc, String jsonMsg ->
            switch (jsonMsg) {
                case ~/.*No snapshot present.*/:
                    return NOT_INSTALLED
                case 'no package':
                case '':
                    // neither of these should happen since we check for the existence of the package first, but...
                    return NO_PACKAGE
                default:
                    return UNKNOWN
            }
        } as CqPackageCommand.SuccessFalseHandler

    /**
     * Iterates through all of the servers in "slingServersConfiguration" and uninstalls the given package from them.
     *
     * @see #uninstall(String, SlingPackageSupport)
     * @see #consumeStatus(Status, String, SlingServerConfiguration)
     */
    static void uninstall(String packageName, SlingServersConfiguration slingServersConfiguration, SlingPackageSupportFactory factory) {
        slingServersConfiguration.each { serverConfig ->
            def status = uninstall(packageName, factory.create(serverConfig))
            consumeStatus(status, packageName, serverConfig)
        }
    }

    /**
     * "Consumes" the status, mostly to appropriately log what happened.
     */
    static void consumeStatus(Status status, String packageName, SlingServerConfiguration serverConfig) {
        switch (status) {
            case OK:
                log.info("\"${packageName}\" is now uninstalled on ${serverConfig.name}"); break
            case NO_PACKAGE:
                log.info("\"${packageName}\" is not currently on ${serverConfig.name}, so no need to uninstall it"); break
            case NOT_INSTALLED:
                log.info("\"${packageName}\" is uploaded but not currently installed on ${serverConfig.name}, so no need to uninstall it"); break
            case SERVER_INACTIVE:
            case SERVER_TIMEOUT:
                break // ignore
            default:
                throw new IllegalArgumentException("Unknown status ${status} when trying to uninstall ${packageName} on ${serverConfig.name}")
        }
    }

    /**
     * Uninstalls the given package using the provided server configuration.
     *
     * @param packageName the name of the package to uninstall
     * @param serverConfig the configuration of the server to uninstall from
     * @param maxWaitMs the maximum amount of time, in milliseconds, to wait for the uninstall to finish
     * @param retryWaitMs the amount of time to wait while polling for status updates
     *
     * @return the {@link PackageStatus} of doing the uninstall
     */
    @Nonnull
    static Status uninstall(String packageName, SlingPackageSupport packageSupport) {
        return CqPackageCommand.doCommand("uninstall", packageName, packageSupport, falseStatusHandler)
    }

}
