package org.apache.jenkins.gitpubsub;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import jenkins.scm.api.metadata.AvatarMetadataAction;

/**
 * @author Stephen Connolly
 */
public class ASFAvatarMetadataAction extends AvatarMetadataAction {
    private final String avatarUrl;
    private final String avatarDescription;

    public ASFAvatarMetadataAction(@NonNull String avatarUrl, @CheckForNull String avatarDescription) {
        this.avatarUrl = avatarUrl;
        this.avatarDescription = avatarDescription;
    }

    @Override
    public String getAvatarDescription() {
        return avatarDescription;
    }

    @Override
    public String getAvatarImageOf(@NonNull String size) {
        return cachedResizedImageOf(avatarUrl, size);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        ASFAvatarMetadataAction that = (ASFAvatarMetadataAction) o;

        if (!avatarUrl.equals(that.avatarUrl)) {
            return false;
        }
        return avatarDescription != null
                ? avatarDescription.equals(that.avatarDescription)
                : that.avatarDescription == null;
    }

    @Override
    public int hashCode() {
        return avatarUrl.hashCode();
    }
}
