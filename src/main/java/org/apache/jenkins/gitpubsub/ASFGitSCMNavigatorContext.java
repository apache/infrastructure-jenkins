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

import edu.umd.cs.findbugs.annotations.NonNull;
import jenkins.scm.api.SCMNavigator;
import jenkins.scm.api.SCMSourceObserver;
import jenkins.scm.api.trait.SCMNavigatorContext;

public class ASFGitSCMNavigatorContext extends SCMNavigatorContext<ASFGitSCMNavigatorContext, ASFGitSCMNavigatorRequest>{
    private String avatarUrl;
    private String avatarDescription;
    private String objectDisplayName;
    private String objectDescription;
    private String objectUrl;

    public String avatarUrl() {
        return avatarUrl;
    }

    public ASFGitSCMNavigatorContext withAvatarUrl(String avatarUrl) {
        this.avatarUrl = avatarUrl;
        return this;
    }

    public String avatarDescription() {
        return avatarDescription;
    }

    public ASFGitSCMNavigatorContext withAvatarDescription(String avatarDescription) {
        this.avatarDescription = avatarDescription;
        return this;
    }

    public String objectDisplayName() {
        return objectDisplayName;
    }

    public ASFGitSCMNavigatorContext withObjectDisplayName(String objectDisplayName) {
        this.objectDisplayName = objectDisplayName;
        return this;
    }

    public String objectDescription() {
        return objectDescription;
    }

    public ASFGitSCMNavigatorContext withObjectDescription(String objectDescription) {
        this.objectDescription = objectDescription;
        return this;
    }

    public String objectUrl() {
        return objectUrl;
    }

    public ASFGitSCMNavigatorContext withObjectUrl(String objectUrl) {
        this.objectUrl = objectUrl;
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public ASFGitSCMNavigatorRequest newRequest(@NonNull SCMNavigator navigator, @NonNull SCMSourceObserver observer) {
        return new ASFGitSCMNavigatorRequest(navigator, this, observer);
    }
}
