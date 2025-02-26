package com.tyron.builder.api.internal.artifacts.dependencies;

import com.google.common.collect.Lists;
import com.tyron.builder.api.artifacts.VersionConstraint;
import com.tyron.builder.api.internal.artifacts.ImmutableVersionConstraint;
import com.tyron.builder.api.internal.artifacts.VersionConstraintInternal;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.List;

import static com.google.common.base.Strings.nullToEmpty;

public class DefaultMutableVersionConstraint extends AbstractVersionConstraint implements VersionConstraintInternal {
    private String requiredVersion;
    private String preferredVersion;
    private String strictVersion;
    private String branch;
    private final List<String> rejectedVersions = Lists.newArrayListWithExpectedSize(1);

    public DefaultMutableVersionConstraint(VersionConstraint versionConstraint) {
        this(versionConstraint.getPreferredVersion(), versionConstraint.getRequiredVersion(), versionConstraint.getStrictVersion(), versionConstraint.getRejectedVersions());
    }

    public DefaultMutableVersionConstraint(String version) {
        this(null, version, null);
    }

    private DefaultMutableVersionConstraint(@Nullable String preferredVersion, String requiredVersion, @Nullable String strictVersion) {
        this(preferredVersion, requiredVersion, strictVersion, Collections.emptyList());
    }

    private DefaultMutableVersionConstraint(@Nullable String preferredVersion, String requiredVersion, @Nullable String strictVersion, List<String> rejects) {
        updateVersions(preferredVersion, requiredVersion, strictVersion);
        for (String reject : rejects) {
            this.rejectedVersions.add(nullToEmpty(reject));
        }
    }

    private void updateVersions(@Nullable String preferredVersion, @Nullable String requiredVersion, @Nullable String strictVersion) {
        this.preferredVersion = nullToEmpty(preferredVersion);
        this.requiredVersion = nullToEmpty(requiredVersion);
        this.strictVersion = nullToEmpty(strictVersion);
        this.rejectedVersions.clear();
    }

    public static DefaultMutableVersionConstraint withVersion(String version) {
        return new DefaultMutableVersionConstraint(version);
    }

    public static DefaultMutableVersionConstraint withStrictVersion(String version) {
        return new DefaultMutableVersionConstraint(null, version, version);
    }

    @Override
    public ImmutableVersionConstraint asImmutable() {
        return new DefaultImmutableVersionConstraint(preferredVersion, requiredVersion, strictVersion, rejectedVersions, branch);
    }

    @Nullable
    @Override
    public String getBranch() {
        return branch;
    }

    @Override
    public void setBranch(@Nullable String branch) {
        this.branch = branch;
    }

    @Override
    public String getRequiredVersion() {
        return requiredVersion;
    }

    @Override
    public void require(String version) {
        updateVersions(preferredVersion, version, null);
    }

    @Override
    public String getPreferredVersion() {
        return preferredVersion;
    }

    @Override
    public void prefer(String version) {
        updateVersions(version, requiredVersion, strictVersion);
    }

    @Override
    public String getStrictVersion() {
        return strictVersion;
    }

    @Override
    public void strictly(String version) {
        updateVersions(preferredVersion, version, version);
    }

    @Override
    public void reject(String... versions) {
        this.rejectedVersions.clear();
        Collections.addAll(rejectedVersions, versions);
    }

    @Override
    public void rejectAll() {
        updateVersions(null, null, null);
        this.rejectedVersions.add("+");
    }

    @Override
    public List<String> getRejectedVersions() {
       return rejectedVersions;
    }

    public String getVersion() {
        return requiredVersion.isEmpty() ? preferredVersion : requiredVersion;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }

        DefaultMutableVersionConstraint that = (DefaultMutableVersionConstraint) o;

        if (requiredVersion != null ? !requiredVersion.equals(that.requiredVersion) : that.requiredVersion != null) {
            return false;
        }
        if (preferredVersion != null ? !preferredVersion.equals(that.preferredVersion) : that.preferredVersion != null) {
            return false;
        }
        if (strictVersion != null ? !strictVersion.equals(that.strictVersion) : that.strictVersion != null) {
            return false;
        }
        if (branch != null ? !branch.equals(that.branch) : that.branch != null) {
            return false;
        }
        return rejectedVersions.equals(that.rejectedVersions);
    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + (requiredVersion != null ? requiredVersion.hashCode() : 0);
        result = 31 * result + (preferredVersion != null ? preferredVersion.hashCode() : 0);
        result = 31 * result + (strictVersion != null ? strictVersion.hashCode() : 0);
        result = 31 * result + (branch != null ? branch.hashCode() : 0);
        result = 31 * result + rejectedVersions.hashCode();
        return result;
    }
}
