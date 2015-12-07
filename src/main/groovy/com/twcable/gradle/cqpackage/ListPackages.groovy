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

import com.twcable.gradle.http.HttpResponse
import com.twcable.gradle.sling.SlingSupport
import groovy.json.JsonSlurper
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import org.gradle.api.GradleException

import javax.annotation.Nonnull

import static com.twcable.gradle.cqpackage.SuccessOrFailure.failure
import static com.twcable.gradle.cqpackage.SuccessOrFailure.success
import static com.twcable.gradle.sling.SlingSupport.block
import static java.net.HttpURLConnection.HTTP_CLIENT_TIMEOUT
import static java.net.HttpURLConnection.HTTP_OK

@Slf4j
@CompileStatic
class ListPackages {

    /**
     * Asks the given server for all of the CQ Packages that it has, returning their information.
     */
    @Nonnull
    static SuccessOrFailure<Collection<RuntimePackageProperties>> listPackages(SlingPackageSupport packageSupport) {
        def packageServerConf = packageSupport.packageServerConf
        def serverConf = packageServerConf.serverConf

        if (!packageServerConf.serverConf.active) return failure(Status.SERVER_INACTIVE)

        return listPackages(packageServerConf.packageListUri, packageSupport.slingSupport, serverConf.maxWaitMs, serverConf.retryWaitMs)
    }

    /**
     * Asks the given server for all of the CQ Packages that it has, returning their information.
     */
    @Nonnull
    static SuccessOrFailure<Collection<RuntimePackageProperties>> listPackages(URI packageListUri, SlingSupport slingSupport,
                                                                               long maxWaitMs, long retryWaitMs) {
        HttpResponse resp
        block(
            maxWaitMs,
            { ![HTTP_OK, HTTP_CLIENT_TIMEOUT].contains(resp?.code) },
            {
                resp = slingSupport.doGet(packageListUri)
            },
            retryWaitMs
        )

        if (resp.code == HTTP_OK) {
            final String jsonStr = resp.body
            final JsonSlurper jsonSlurper = new JsonSlurper()
            return success(((Collection<Map>)(jsonSlurper.parseText(jsonStr) as Map).results).collect {
                RuntimePackageProperties.fromJson(it)
            })
        }
        else if (resp.code == HTTP_CLIENT_TIMEOUT) {
            return failure(Status.SERVER_TIMEOUT)
        }
        else {
            throw new GradleException("Could not list the packages on ${packageListUri}: ${resp.code} - ${resp.body}")
        }
    }

}
