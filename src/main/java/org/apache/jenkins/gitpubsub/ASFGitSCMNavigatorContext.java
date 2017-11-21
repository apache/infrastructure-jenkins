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
    @NonNull
    @Override
    public ASFGitSCMNavigatorRequest newRequest(@NonNull SCMNavigator navigator, @NonNull SCMSourceObserver observer) {
        return new ASFGitSCMNavigatorRequest(navigator, this, observer);
    }

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
}
