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

import lombok.val;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.io.Writer;
import java.util.Collection;

/**
 * Constructs an instance of {@link FilterXmlWriter} using a "fluent interface".
 *
 * @see FilterXmlWriterBuilder#build()
 */
public class FilterXmlWriterBuilder {
    private static final Logger LOG = LoggerFactory.getLogger(FilterXmlWriterBuilder.class);

    private Project project;
    private Reader inReader;
    private Writer outWriter;
    private Configuration configuration;
    private String bundleInstallRoot;


    /**
     * The Project to get default information from.
     * Only used if another property needs to compute a default value.
     */
    public FilterXmlWriterBuilder(Project project) {
        if (project == null) throw new IllegalArgumentException("project == null");
        this.project = project;
    }


    /**
     * The {@link Configuration} to get dependencies from.
     * Defaults to {@link CreatePackageTask#cqPackageConfiguration(Project)} if "project" has been set.
     */
    public FilterXmlWriterBuilder configuration(Configuration configuration) {
        this.configuration = configuration;
        return this;
    }


    /**
     * The Reader to get the source XML from.
     * Defaults to the file "src/main/content/META-INF/vault/filter.xml" if "project" has been set.
     */
    public FilterXmlWriterBuilder inReader(Reader inReader) {
        this.inReader = inReader;
        return this;
    }


    /**
     * The Writer to write the resulting XML to.
     * Defaults to the file "build/tmp/filter.xml" if "project" has been set.
     */
    public FilterXmlWriterBuilder outWriter(Writer outWriter) {
        this.outWriter = outWriter;
        return this;
    }


    /**
     * Builds an instance of {@link FilterXmlWriter} based on the properties set.
     */
    public FilterXmlWriter build() throws IOException {
        ensureInReader();
        ensureOutWriter();
        ensureConfiguration();
        ensureBundleInstallRoot();

        FilterDefinition filterDefinition = createFilterDescription();

        return new FilterXmlWriter(inReader, filterDefinition, bundleInstallRoot, outWriter);
    }


    private void ensureBundleInstallRoot() {
        if (bundleInstallRoot == null) {
            // TODO Fix
            bundleInstallRoot = project.getTasks().withType(CreatePackageTask.class).iterator().next().getBundleInstallRoot();
        }
    }


    private void ensureConfiguration() {
        if (configuration == null) {
            // TODO Fix
            configuration = CreatePackageTask.cqPackageConfiguration(project);
        }
    }


    @SuppressWarnings("ResultOfMethodCallIgnored")
    private void ensureOutWriter() throws IOException {
        if (this.outWriter != null) return;

        File outFile = new File(project.getBuildDir(), "tmp/filter.xml").getCanonicalFile();

        if (!outFile.exists()) {
            // make sure the parent directory exists
            outFile.getParentFile().mkdirs();
        }

        outFile = outFile.getCanonicalFile();

        this.outWriter = new FileWriter(outFile);
    }


    private void ensureInReader() throws IOException {
        if (this.inReader != null) return;

        // TODO Fix
        val inFile = project.file("src/main/content/META-INF/vault/filter.xml").getCanonicalFile();

        if (inFile.exists()) {
            this.inReader = new FileReader(inFile);
        }
        else {
            LOG.warn("Missing input filer.xml: {}", inFile);
            this.inReader = new StringReader("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<workspaceFilter version=\"1.0\"/>\n");
        }
    }


    private FilterDefinition createFilterDescription() {
        // TODO Fix
        Collection<File> bundleFiles = CreatePackageTask.from(project).getBundleFiles();
        return FilterDefinition.create(bundleFiles);
    }

}
