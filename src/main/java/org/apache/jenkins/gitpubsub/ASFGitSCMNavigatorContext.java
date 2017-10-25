package org.apache.jenkins.gitpubsub;

import edu.umd.cs.findbugs.annotations.NonNull;
import jenkins.scm.api.SCMNavigator;
import jenkins.scm.api.SCMSourceObserver;
import jenkins.scm.api.trait.SCMNavigatorContext;

public class ASFGitSCMNavigatorContext extends SCMNavigatorContext<ASFGitSCMNavigatorContext, ASFGitSCMNavigatorRequest>{
    @NonNull
    @Override
    public ASFGitSCMNavigatorRequest newRequest(@NonNull SCMNavigator navigator, @NonNull SCMSourceObserver observer) {
        return new ASFGitSCMNavigatorRequest(navigator, this, observer);
    }
}
