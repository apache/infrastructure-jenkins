/*
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

import hudson.Extension;
import javax.annotation.Nonnull;
import jenkins.scm.api.SCMNavigator;
import jenkins.scm.api.trait.SCMNavigatorContext;
import jenkins.scm.api.trait.SCMNavigatorTrait;
import jenkins.scm.api.trait.SCMNavigatorTraitDescriptor;
import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * A {@link SCMNavigatorTrait} that defines the avatar and object metadata for a {@link ASFGitSCMNavigator}.
 */
public class ASFMetadataSCMNavigatorTrait extends SCMNavigatorTrait {
    private final String avatarUrl;
    private final String avatarDescription;
    private final String objectDisplayName;
    private final String objectDescription;
    private final String objectUrl;

    @DataBoundConstructor
    public ASFMetadataSCMNavigatorTrait(String avatarUrl, String avatarDescription, String objectDisplayName,
                                        String objectDescription, String objectUrl) {
        this.avatarUrl = StringUtils.trimToNull(avatarUrl);
        this.avatarDescription = StringUtils.trimToNull(avatarDescription);
        this.objectDisplayName = StringUtils.trimToNull(objectDisplayName);
        this.objectDescription = StringUtils.trimToNull(objectDescription);
        this.objectUrl = StringUtils.trimToNull(objectUrl);
    }

    public String getAvatarUrl() {
        return avatarUrl;
    }

    public String getAvatarDescription() {
        return avatarDescription;
    }

    public String getObjectDisplayName() {
        return objectDisplayName;
    }

    public String getObjectDescription() {
        return objectDescription;
    }

    public String getObjectUrl() {
        return objectUrl;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void decorateContext(SCMNavigatorContext<?, ?> context) {
        ((ASFGitSCMNavigatorContext) context)
                .withAvatarUrl(avatarUrl)
                .withAvatarDescription(avatarDescription)
                .withObjectUrl(objectUrl)
                .withObjectDisplayName(objectDisplayName)
                .withAvatarDescription(objectDescription);
    }

    @Extension
    public static class DescriptorImpl extends SCMNavigatorTraitDescriptor {
        /**
         * {@inheritDoc}
         */
        @Override
        public Class<? extends SCMNavigatorContext> getContextClass() {
            return ASFGitSCMNavigatorContext.class;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public Class<? extends SCMNavigator> getNavigatorClass() {
            return ASFGitSCMNavigator.class;
        }

        /**
         * {@inheritDoc}
         */
        @Nonnull
        @Override
        public String getDisplayName() {
            return Messages.ASFMetadataSCMNavigatorTrait_displayName();
        }
    }
}
