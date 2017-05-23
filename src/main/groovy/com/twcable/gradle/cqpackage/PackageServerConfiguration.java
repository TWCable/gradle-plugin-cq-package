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

import com.twcable.gradle.sling.SlingServerConfiguration;

import javax.annotation.Nonnull;
import java.net.URI;
import java.net.URISyntaxException;

/**
 * Enriches {@link SlingServerConfiguration} with additional information for dealing with packages
 */
public class PackageServerConfiguration {
    public static final String PACKAGE_MANAGER_BASE_PATH = "/crx/packmgr/";

    public final SlingServerConfiguration serverConf;


    public PackageServerConfiguration(SlingServerConfiguration serverConf) {
        this.serverConf = serverConf;
    }


    /**
     * Returns the URL to use to do actions on a CQ package.
     */
    @Nonnull
    public URI getPackageControlUri() throws URISyntaxException {
        URI base = serverConf.getBaseUri();
        return new URI(base.getScheme(), base.getUserInfo(), base.getHost(), base.getPort(), PACKAGE_MANAGER_BASE_PATH + "service/.json", null, null);
    }


    /**
     * Returns the URL to list all the CQ packages.
     */
    @Nonnull
    public URI getPackageListUri() throws URISyntaxException {
        URI base = serverConf.getBaseUri();
        return new URI(base.getScheme(), base.getUserInfo(), base.getHost(), base.getPort(), PACKAGE_MANAGER_BASE_PATH + "list.jsp", null, null);
    }


    /**
     * Returns the URL to download a CQ package.
     */
    @Nonnull
    public URI getPackageDownloadUri() throws URISyntaxException {
        URI base = serverConf.getBaseUri();
        return new URI(base.getScheme(), base.getUserInfo(), base.getHost(), base.getPort(), PACKAGE_MANAGER_BASE_PATH + "download.jsp", null, null);
    }


    @Override
    public String toString() {
        return "PackageServerConfiguration{" + serverConf + '}';
    }


    /**
     * @see SlingServerConfiguration#getMaxWaitMs()
     */
    public long getMaxWaitMs() {
        return this.serverConf.getMaxWaitMs();
    }


    /**
     * @see SlingServerConfiguration#getRetryWaitMs()
     */
    public long getRetryWaitMs() {
        return this.serverConf.getRetryWaitMs();
    }

}
