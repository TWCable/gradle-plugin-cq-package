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

import com.twcable.gradle.sling.SimpleSlingSupportFactory;
import com.twcable.gradle.sling.SlingServerConfiguration;
import com.twcable.gradle.sling.SlingSupport;
import com.twcable.gradle.sling.SlingSupportFactory;

import javax.annotation.Nonnull;

/**
 * A simple implementation of {@link SlingPackageSupportFactory}
 *
 * @see #INSTANCE
 */
public class SimpleSlingPackageSupportFactory implements SlingPackageSupportFactory {

    /**
     * An instance of SlingPackageSupportFactory that uses {@link SimpleSlingSupportFactory#INSTANCE}
     */
    public static final SimpleSlingPackageSupportFactory INSTANCE = new SimpleSlingPackageSupportFactory(SimpleSlingSupportFactory.INSTANCE);

    @Nonnull
    private final SlingSupportFactory slingSupportFactory;


    /**
     * Creates an instance that uses {@link SlingSupportFactory}
     *
     * @param slingSupportFactory the factory for creating {@link SlingSupport}
     */
    @SuppressWarnings("ConstantConditions")
    public SimpleSlingPackageSupportFactory(@Nonnull SlingSupportFactory slingSupportFactory) {
        if (slingSupportFactory == null) throw new IllegalArgumentException("slingSupportFactory == null");
        this.slingSupportFactory = slingSupportFactory;
    }


    @Nonnull
    @Override
    @SuppressWarnings("ConstantConditions")
    public SlingPackageSupport create(@Nonnull SlingServerConfiguration serverConfig) {
        if (serverConfig == null) throw new IllegalArgumentException("serverConfig == null");
        final SlingSupport slingSupport = slingSupportFactory.create(serverConfig);
        final PackageServerConfiguration packageServerConf = new PackageServerConfiguration(serverConfig);
        return new SlingPackageSupport(packageServerConf, slingSupport);
    }

}
