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

import com.google.common.collect.ImmutableList;
import org.apache.jackrabbit.vault.packaging.Dependency;
import org.apache.jackrabbit.vault.packaging.PackageId;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Collection;
import java.util.Date;
import java.util.Map;

import static java.util.Collections.emptyList;

/**
 * A representation of a Vault Package with reasonable defaults for testing.
 */
@SuppressWarnings("unused")
public class PackageFixture {
    @Nonnull
    private final PackageId _packageId;
    private String _description;
    private String _thumbnail;
    private int _buildCount;
    private long _created = new Date().getTime();
    private String _createdBy = "admin";
    private long _lastUnpacked;
    private String _lastUnpackagedBy = "admin";
    private long _lastUnwrapped;
    private String _lastUnwrappedBy = "admin";
    private String _providerName;
    private String _providerUrl;
    private int _size = 1;
    private boolean _hasSnapshot;
    private boolean _needsRewrap;
    private String _builtWith = "Adobe CQ5-5.5.0";
    private boolean _requiresRoot;
    private boolean _requiresRestart;
    private String _acHandling = "merge_preserve";
    private Collection<Map<String, Object>> _filter = emptyList();
    private Collection<String> _screenshots = emptyList();
    private Collection<DependencyPair> _dependencies = emptyList();


    @SuppressWarnings("ConstantConditions")
    private PackageFixture(@Nonnull PackageId packageId) {
        if (packageId == null) throw new IllegalArgumentException("packageId == null");
        this._packageId = packageId;
    }


    public static PackageFixture of(@Nonnull PackageId packageId) {
        return new PackageFixture(packageId);
    }


    public static PackageFixture of(String packageId) {
        return new PackageFixture(PackageId.fromString(packageId));
    }


    public PackageId packageId() {
        return _packageId;
    }


    public String groupTitle() {
        final String group = packageId().getGroup();
        final int firstSlashPos = group.indexOf('/');
        return firstSlashPos > 0 ? group.substring(0, firstSlashPos) : "";
    }


    public String description() {
        return _description;
    }


    public PackageFixture description(String description) {
        this._description = description;
        return this;
    }


    public String thumbnail() {
        return _thumbnail;
    }


    public PackageFixture thumbnail(String thumbnail) {
        this._thumbnail = thumbnail;
        return this;
    }


    public int buildCount() {
        return _buildCount;
    }


    public PackageFixture buildCount(int buildCount) {
        this._buildCount = buildCount;
        return this;
    }


    public long created() {
        return _created;
    }


    public PackageFixture created(long created) {
        this._created = created;
        return this;
    }


    public String createdBy() {
        return _createdBy;
    }


    public PackageFixture createdBy(String createdBy) {
        this._createdBy = createdBy;
        return this;
    }


    public long lastUnpacked() {
        return _lastUnpacked == 0L ? created() : _lastUnpacked;
    }


    public PackageFixture lastUnpacked(long lastUnpacked) {
        this._lastUnpacked = lastUnpacked;
        return this;
    }


    public String lastUnpackagedBy() {
        return _lastUnpackagedBy;
    }


    public PackageFixture lastUnpackagedBy(String lastUnpackagedBy) {
        this._lastUnpackagedBy = lastUnpackagedBy;
        return this;
    }


    public long lastUnwrapped() {
        return _lastUnwrapped == 0L ? created() : _lastUnwrapped;
    }


    public PackageFixture lastUnwrapped(long lastUnwrapped) {
        this._lastUnwrapped = lastUnwrapped;
        return this;
    }


    public String lastUnwrappedBy() {
        return _lastUnwrappedBy;
    }


    public PackageFixture lastUnwrappedBy(String lastUnwrappedBy) {
        this._lastUnwrappedBy = lastUnwrappedBy;
        return this;
    }


    public String providerName() {
        return _providerName;
    }


    public PackageFixture providerName(String providerName) {
        this._providerName = providerName;
        return this;
    }


    public String providerUrl() {
        return _providerUrl;
    }


    public PackageFixture providerUrl(String providerUrl) {
        this._providerUrl = providerUrl;
        return this;
    }


    public int size() {
        return _size;
    }


    public PackageFixture size(int size) {
        this._size = size;
        return this;
    }


    public boolean hasSnapshot() {
        return _hasSnapshot;
    }


    public PackageFixture hasSnapshot(boolean hasSnapshot) {
        this._hasSnapshot = hasSnapshot;
        return this;
    }


    public boolean needsRewrap() {
        return _needsRewrap;
    }


    public PackageFixture needsRewrap(boolean needsRewrap) {
        this._needsRewrap = needsRewrap;
        return this;
    }


    public String builtWith() {
        return _builtWith;
    }


    public PackageFixture builtWith(String builtWith) {
        this._builtWith = builtWith;
        return this;
    }


    public boolean requiresRoot() {
        return _requiresRoot;
    }


    public PackageFixture requiresRoot(boolean requiresRoot) {
        this._requiresRoot = requiresRoot;
        return this;
    }


    public boolean requiresRestart() {
        return _requiresRestart;
    }


    public PackageFixture requiresRestart(boolean requiresRestart) {
        this._requiresRestart = requiresRestart;
        return this;
    }


    public String acHandling() {
        return _acHandling;
    }


    public PackageFixture acHandling(String acHandling) {
        this._acHandling = acHandling;
        return this;
    }


    public Collection<Map<String, Object>> filter() {
        return _filter;
    }


    public PackageFixture filter(Collection<Map<String, Object>> filter) {
        this._filter = filter;
        return this;
    }


    public Collection<String> screenshots() {
        return _screenshots;
    }


    public PackageFixture screenshots(Collection<String> screenshots) {
        this._screenshots = screenshots;
        return this;
    }


    public Collection<DependencyPair> dependencies() {
        return _dependencies;
    }


    @SuppressWarnings("ConstantConditions")
    public PackageFixture dependency(@Nonnull Dependency dependency, @Nullable PackageId packageId) {
        if (dependency == null) throw new IllegalArgumentException("dependency == null");
        this._dependencies = ImmutableList.<DependencyPair>builder().
            addAll(dependencies()).
            add(new DependencyPair(dependency, packageId)).
            build();
        return this;
    }


    public PackageFixture dependency(@Nonnull String dependency, @Nullable String packageId) {
        return dependency(Dependency.fromString(dependency), PackageId.fromString(packageId));
    }


    public static final class DependencyPair {
        @Nonnull
        public final Dependency dependency;

        @Nullable
        public final PackageId packageId;


        @SuppressWarnings("ConstantConditions")
        public DependencyPair(@Nonnull Dependency dependency, @Nullable PackageId packageId) {
            if (dependency == null) throw new IllegalArgumentException("dependency == null");
            this.dependency = dependency;
            this.packageId = packageId;
        }
    }

}
