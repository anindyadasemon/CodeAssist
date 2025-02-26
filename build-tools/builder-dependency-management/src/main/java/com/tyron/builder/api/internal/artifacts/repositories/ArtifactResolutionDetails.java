package com.tyron.builder.api.internal.artifacts.repositories;

import com.tyron.builder.api.artifacts.ModuleIdentifier;
import com.tyron.builder.api.artifacts.component.ModuleComponentIdentifier;
import com.tyron.builder.api.attributes.AttributeContainer;

import javax.annotation.Nullable;

/**
 * Details about an artifact resolution query. This is used whenever repository
 * content filtering is in place.
 * <p></p>
 * This interface gives access to the details of the artifact query. There are two
 * cases when filtering can be called:
 * <p></p>
 * <ul>
 *     <li>when looking for a specific module version, for example org:foo:1.0</li>
 *     <li>when looking for the list of versions for a module, for example org:foo</li>
 * </ul>
 * <p></p>
 * Listing is called when using dynamic versions (ranges, 1.+, ...).
 * <p></p>
 * The module identifier will always be non-null. If what you want to express
 * is that a module cannot be found in a repository, independently of its version,
 * then it's enough to just look at the module id using {@link #getModuleId()}.
 * <p></p>
 * However, if you have to differentiate depending on the version number (for example,
 * some versions of a module are found in one repository, others in a different repository),
 * then you must look at the version thanks to the {@link #getComponentId()} method. But
 * because there can also be version listings, you must also check for {@link #getModuleId()}.
 * <p></p>
 * A {@link #isVersionListing() convenience method} makes it easier to find out if you
 * are in the version listing case, or module version case.
 * <p></p>
 * Filtering is done by calling the {@link #notFound()} method: as soon as you know a module
 * cannot be found in a repository, call this method. Otherwise, Gradle will perform a request
 * to find out. It doesn't matter if the module is eventually not found, as Gradle would handle
 * this appropriately by looking at the next repository: the consequence is just a remote call.
 *
 */
public interface ArtifactResolutionDetails {
    /**
     * The identifier of the module being looked for in this repository
     * @return the module identifier
     */
    ModuleIdentifier getModuleId();

    /**
     * The module component identifier of the module being looked for in this repository,
     * which includes a version.
     * @return the module version identifier. If it's a version listing, then this will
     * be null.
     */
    @Nullable
    ModuleComponentIdentifier getComponentId();

    /**
     * The attributes of the consumer looking for this module
     * @return the consumer attributes
     */
    AttributeContainer getConsumerAttributes();

    /**
     * The name of the consumer. Usually corresponds to the name of the configuration being
     * resolved.
     * @return the consumer name
     */
    String getConsumerName();

    /**
     * Returns true if this details is created for a version listing.
     *
     * @return true if we are asked for a version listing
     */
    boolean isVersionListing();

    /**
     * Declares that this artifact will not be found on this repository
     */
    void notFound();
}
