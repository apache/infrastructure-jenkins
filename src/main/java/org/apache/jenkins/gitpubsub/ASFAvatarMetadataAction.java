/*
 * Copyright 2017 Stephen Connolly.
 *
 * Licensed under the Apache License,Version2.0(the"License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,software
 * distributed under the License is distributed on an"AS IS"BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.jenkins.gitpubsub;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import jenkins.scm.api.metadata.AvatarMetadataAction;
import org.apache.commons.lang.StringUtils;

/**
 * Provides the avatar information for a {@link ASFGitSCMNavigator}.
 */
public class ASFAvatarMetadataAction extends AvatarMetadataAction {
    /**
     * The avatar url.
     */
    @NonNull
    private final String avatarUrl;
    /**
     * The avatar description.
     */
    @CheckForNull
    private final String avatarDescription;

    /**
     * Constructor.
     *
     * @param avatarUrl         The avatar url.
     * @param avatarDescription The avatar description.
     */
    public ASFAvatarMetadataAction(@NonNull String avatarUrl, @CheckForNull String avatarDescription) {
        this.avatarUrl = avatarUrl;
        this.avatarDescription = StringUtils.trimToNull(avatarDescription);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getAvatarDescription() {
        return avatarDescription;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getAvatarImageOf(@NonNull String size) {
        return cachedResizedImageOf(avatarUrl, size);
    }

    /**
     * {@inheritDoc}
     */
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

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        return avatarUrl.hashCode();
    }
}
