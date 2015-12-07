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
package com.twcable.gradle.cqpackage;

import com.twcable.gradle.sling.SlingServerConfiguration;

import javax.annotation.Nonnull;

/**
 * Factory for creating instances of {@link SlingPackageSupport}
 */
public interface SlingPackageSupportFactory {
    /**
     * Creates a new instances of {@link SlingPackageSupport}
     *
     * @param serverConfig the server to associate the SlingPackageSupport with
     * @return never null
     */
    @Nonnull
    SlingPackageSupport create(@Nonnull SlingServerConfiguration serverConfig);
}
