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
import com.twcable.gradle.http.SimpleHttpClient
import com.twcable.gradle.sling.SlingServerConfiguration
import com.twcable.gradle.sling.SlingServersConfiguration
import com.twcable.gradle.sling.SlingSupport
import spock.lang.Specification

import static java.net.HttpURLConnection.HTTP_OK

@SuppressWarnings("GroovyAssignabilityCheck")
abstract class AbstractPackageCommandSpec extends Specification {
    SlingServersConfiguration slingServersConfiguration
    SlingServerConfiguration slingServerConfiguration
    SlingSupport slingSupport


    def setup() {
        final httpClient = Mock(SimpleHttpClient)
        slingSupport = Mock(SlingSupport)
        slingSupport.doHttp(_) >> { Closure closure -> closure.delegate = slingSupport; closure.call(httpClient) }

        slingServerConfiguration = Mock(SlingServerConfiguration) {
            getSlingSupport() >> slingSupport
            getActive() >> true
        }

        slingServersConfiguration = Stub(SlingServersConfiguration) {
            getMaxWaitValidateBundlesMs() >> 1000
            getRetryWaitMs() >> 10
            iterator() >> [slingServerConfiguration].iterator()
        }
    }


    protected void setupSlingSupport(success, msg, installedPackage) {
        // List Packages
        slingSupport.doGet(_, _) >> {
            new HttpResponse(HTTP_OK, "{\"results\":[{\"name\":\"${installedPackage}\",\"group\":\"testing\"}]}")
        }

        // command POST
        slingSupport.doPost(_, _, _) >> {
            new HttpResponse(HTTP_OK, "{\"success\": ${success}, \"msg\": \"${msg}\"}")
        }
    }

//    def "tester"() {
//        // a real connection to find out how Sling actually responds
//        def serverConfiguration = new SlingServerConfiguration(name: "tester", port: 4502)
//
//        when:
//        def status = UploadPackage.upload(new File("/Users/jmoore/Downloads/testpackage-1.0.1.zip"), serverConfiguration, 10_000L, 50L)
//
//        then:
//        status == Status.OK
//    }

}
