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
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.gradle.api.DefaultTask;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

/**
 * A task that will (re)write the filter.xml file with bundle files.
 * <p>
 * Generally, this uses the "createPackage" task for its values (see {@link #setCreatePackageTask(CreatePackageTask)}
 *
 * @see FilterXmlWriter
 */
@SuppressWarnings({"WeakerAccess", "Convert2MethodRef"})
public class AddBundlesToFilterXmlTask extends DefaultTask {
    private static final Logger LOG = LoggerFactory.getLogger(AddBundlesToFilterXmlTask.class);

    private @MonotonicNonNull File inFile;

    private @MonotonicNonNull File outFile;

    private @MonotonicNonNull CreatePackageTask createPackageTask;


    @SuppressWarnings("method.invocation.invalid")
    public AddBundlesToFilterXmlTask() {
        setDescription("Adds the bundles to the filter.xml");

        addValidator((taskInternal, errorMessages) -> {
            if (createPackageTask == null)
                errorMessages.add("createPackageTask was not set");
        });
    }


    @TaskAction
    @SuppressWarnings("ResultOfMethodCallIgnored")
    public void writeFile() throws IOException {
        File inFile = getInFile();

        FilterDefinition filterDefinition = FilterDefinition.create(getBundleFiles());

        String bundleInstallRoot = getBundleInstallRoot();

        File outFile = getOutFile();

        FilterXmlWriter xmlWriter = new FilterXmlWriter(new FileReader(inFile), filterDefinition, bundleInstallRoot, new FileWriter(outFile));
        xmlWriter.run();
    }


    /**
     * The list of bundle files to add to the filter.xml
     * <p>
     * The default is "${this.createPackageTask.bundleFiles}"
     *
     * @see CreatePackageTask#getBundleFiles()
     */
    @InputFiles
    @SuppressWarnings("unchecked")
    public Iterable<File> getBundleFiles() {
        @Nullable final CreatePackageTask createPackageTask = getCreatePackageTask();
        if (createPackageTask == null)
            throw new InvalidUserDataException("createPackageTask was not set");
        return createPackageTask.getBundleFiles();
    }


    /**
     * Where to find the "source" XML that will be modified with the bundles.
     * <p>
     * The default is "${this.createPackageTask.contentSrc}/META-INF/vault/filter.xml"
     * <p>
     * If that file is not there, an empty filter.xml is created.
     *
     * @see CreatePackageTask#getContentSrc()
     */
    @InputFile
    @SuppressWarnings({"ResultOfMethodCallIgnored", "dereference.of.nullable"})
    public File getInFile() throws IOException {
        if (inFile == null) {
            @Nullable final CreatePackageTask createPackageTask = getCreatePackageTask();
            if (createPackageTask == null)
                throw new InvalidUserDataException("createPackageTask was not set, and no inFile was set");

            inFile = createPackageTask.getFilterXmlFile();
        }
        return inFile;
    }


    /**
     * Where to find the "source" XML that will be modified with the bundles.
     */
    @SuppressWarnings("unused")
    public void setInFile(Object inFile) {
        if (inFile instanceof File) {
            this.inFile = ((File)inFile).getAbsoluteFile();
        }
        else {
            this.inFile = getProject().file(inFile);
        }
    }


    /**
     * Where the modified XML will be written.
     * <p>
     * The default is "${project.buildDir}/tmp/filter.xml"
     */
    @OutputFile
    @SuppressWarnings({"ResultOfMethodCallIgnored", "dereference.of.nullable"})
    public File getOutFile() throws IOException {
        if (outFile == null) {
            outFile = new File(getProject().getBuildDir(), "tmp/filter.xml").getCanonicalFile();

            if (!outFile.exists()) {
                // make sure the parent directory exists
                outFile.getParentFile().mkdirs();
            }

            outFile = outFile.getCanonicalFile();
        }
        return outFile;
    }


    /**
     * Where the modified XML will be written.
     */
    @SuppressWarnings("unused")
    public void setOutFile(File outFile) {
        this.outFile = outFile;
    }


    /**
     * @see CreatePackageTask#getBundleInstallRoot()
     */
    @Input
    public String getBundleInstallRoot() {
        @Nullable final CreatePackageTask createPackageTask = getCreatePackageTask();
        if (createPackageTask == null)
            throw new InvalidUserDataException("createPackageTask was not set");
        return createPackageTask.getBundleInstallRoot();
    }


    @Internal
    public @Nullable CreatePackageTask getCreatePackageTask() {
        if (createPackageTask == null) {
            val createPackageTasks = getProject().getTasks().withType(CreatePackageTask.class);
            val iter = createPackageTasks.iterator();
            if (!iter.hasNext()) return null;
            createPackageTask = iter.next();
            if (iter.hasNext())
                throw new InvalidUserDataException("There is more than one task that is a " +
                    CreatePackageTask.class.getName() + " (" + createPackageTasks.getNames() +
                    ") so you need to explicitly set the `createPackageTask` property on " + this.getPath());
        }
        return createPackageTask;
    }


    @SuppressWarnings("unused")
    public void setCreatePackageTask(CreatePackageTask createPackageTask) {
        this.createPackageTask = createPackageTask;
    }

}
