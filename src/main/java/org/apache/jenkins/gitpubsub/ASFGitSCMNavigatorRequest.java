package org.apache.jenkins.gitpubsub;

import edu.umd.cs.findbugs.annotations.NonNull;
import jenkins.scm.api.SCMNavigator;
import jenkins.scm.api.SCMSourceObserver;
import jenkins.scm.api.trait.SCMNavigatorRequest;

public class ASFGitSCMNavigatorRequest extends SCMNavigatorRequest {
    /**
     * Constructor.
     *
     * @param source   the source.
     * @param context  the context.
     * @param observer the observer.
     */
    public ASFGitSCMNavigatorRequest(@NonNull SCMNavigator source,
                                     @NonNull
                                             ASFGitSCMNavigatorContext context,
                                     @NonNull
                                             SCMSourceObserver observer) {
        super(source, context, observer);
    }
}
