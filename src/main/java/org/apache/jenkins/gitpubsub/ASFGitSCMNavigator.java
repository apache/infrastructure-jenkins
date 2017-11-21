package org.apache.jenkins.gitpubsub;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.ExtensionList;
import hudson.Util;
import hudson.console.HyperlinkNote;
import hudson.model.Action;
import hudson.model.Descriptor;
import hudson.model.TaskListener;
import hudson.util.ListBoxModel;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import javax.annotation.Nonnull;
import jenkins.plugins.git.GitSCMBuilder;
import jenkins.plugins.git.GitSCMSource;
import jenkins.plugins.git.GitSCMSourceContext;
import jenkins.plugins.git.traits.GitBrowserSCMSourceTrait;
import jenkins.scm.api.SCMNavigator;
import jenkins.scm.api.SCMNavigatorDescriptor;
import jenkins.scm.api.SCMNavigatorEvent;
import jenkins.scm.api.SCMNavigatorOwner;
import jenkins.scm.api.SCMSource;
import jenkins.scm.api.SCMSourceObserver;
import jenkins.scm.api.metadata.ObjectMetadataAction;
import jenkins.scm.api.trait.SCMNavigatorRequest;
import jenkins.scm.api.trait.SCMNavigatorTrait;
import jenkins.scm.api.trait.SCMNavigatorTraitDescriptor;
import jenkins.scm.api.trait.SCMSourceTrait;
import jenkins.scm.api.trait.SCMTrait;
import jenkins.scm.api.trait.SCMTraitDescriptor;
import jenkins.scm.impl.form.NamedArrayList;
import jenkins.scm.impl.trait.Discovery;
import jenkins.scm.impl.trait.Selection;
import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

public class ASFGitSCMNavigator extends SCMNavigator {

    static final String RFC_2822 = "EEE, dd MMM yyyy HH:mm:ss Z";
    static final String GIT_WIP = "https://git-wip-us.apache.org/repos/asf";
    static final String GIT_BOX = "https://gitbox.apache.org/repos/asf";
    private final String server;
    private List<SCMTrait<?>> traits = new ArrayList<>();

    @DataBoundConstructor
    public ASFGitSCMNavigator(String server) {
        this.server = server;
    }

    public String getServer() {
        return server;
    }

    @NonNull
    public List<SCMTrait<?>> getTraits() {
        return Collections.unmodifiableList(traits);
    }

    @DataBoundSetter
    public void setTraits(List<SCMTrait<?>> traits) {
        this.traits = new ArrayList<>(Util.fixNull(traits));
    }

    @NonNull
    @Override
    protected String id() {
        return server;
    }

    @Override
    public void visitSources(@NonNull final SCMSourceObserver observer) throws IOException, InterruptedException {
        try (ASFGitSCMNavigatorRequest request = new ASFGitSCMNavigatorContext()
                .withTraits(traits)
                .newRequest(this, observer);
             BufferedReader reader = new BufferedReader(
                     new InputStreamReader(new URL(server + "?a=project_index").openStream()))) {
            int count = 0;
            String line;
            observer.getListener().getLogger().format("%n  Checking repositories...%n");
            while (null != (line = reader.readLine())) {
                int index = line.indexOf(' ');
                if (index == -1) {
                    continue;
                }
                final String repo = StringUtils.removeEnd(line.substring(0, index), ".git");
                count++;
                observer.getListener().getLogger().format("%n    Checking repository %s%n",
                        HyperlinkNote
                                .encodeTo(server + "?p=" + URLEncoder.encode(repo, "UTF-8") + ".git;a=summary", repo));
                if (request.process(repo, new SCMNavigatorRequest.SourceLambda() {
                    @NonNull
                    @Override
                    public SCMSource create(@NonNull String projectName) throws IOException, InterruptedException {
                        return new ASFGitSCMSourceBuilder(getId() + "::" + projectName,
                                server , projectName
                        )
                                .withTraits(traits)
                                .build();
                    }
                }, null, new SCMNavigatorRequest.Witness() {
                    @Override
                    public void record(@NonNull String projectName, boolean isMatch) {
                        if (isMatch) {
                            observer.getListener().getLogger().format("      Proposing %s%n", projectName);
                        } else {
                            observer.getListener().getLogger().format("      Ignoring %s%n", projectName);
                        }
                    }
                })) {
                    observer.getListener().getLogger().format("%n  %d repositories were processed (query complete)%n",
                            count);
                    return;
                }
            }
            observer.getListener().getLogger().format("%n  %d repositories were processed%n", count);
        }
    }

    @NonNull
    @Override
    protected List<Action> retrieveActions(@NonNull SCMNavigatorOwner owner, SCMNavigatorEvent event,
                                           @NonNull TaskListener listener) throws IOException, InterruptedException {
        List<Action> result = new ArrayList<>(super.retrieveActions(owner, event, listener));
        ASFGitSCMNavigatorContext context = new ASFGitSCMNavigatorContext().withTraits(traits);
        String avatarUrl = context.avatarUrl();
        if (avatarUrl != null) {
            String avatarDescription = context.avatarDescription();
            result.add(new ASFAvatarMetadataAction(avatarUrl, avatarDescription));
        }
        String objectUrl = context.objectUrl();
        String objectDescription = context.objectDescription();
        String objectDisplayName = context.objectDisplayName();
        if (objectUrl != null || objectDescription != null || objectDisplayName != null) {
            result.add(new ObjectMetadataAction(objectDisplayName, objectDescription, objectUrl));
        }
        return result;
    }

    @Extension
    public static class DescriptorImpl extends SCMNavigatorDescriptor {

        @Nonnull
        @Override
        public String getDisplayName() {
            return "Apache Hosted Git Folder";
        }

        @Override
        public SCMNavigator newInstance(String name) {
            return new ASFGitSCMNavigator(GIT_WIP);
        }

        public ListBoxModel doFillServerItems() {
            ListBoxModel result = new ListBoxModel();
            result.add("Git WIP", GIT_WIP);
            result.add("Gitbox", GIT_BOX);
            return result;
        }

        @SuppressWarnings("unused") // jelly
        public List<NamedArrayList<? extends SCMTraitDescriptor<?>>> getTraitsDescriptorLists() {
            GitSCMSource.DescriptorImpl sourceDescriptor =
                    ExtensionList.lookup(Descriptor.class).get(GitSCMSource.DescriptorImpl.class);
            List<SCMTraitDescriptor<?>> all = new ArrayList<>();
            all.addAll(SCMNavigatorTrait._for(this, ASFGitSCMNavigatorContext.class, ASFGitSCMSourceBuilder.class));
            all.addAll(SCMSourceTrait._for(sourceDescriptor, GitSCMSourceContext.class, null));
            all.addAll(SCMSourceTrait._for(sourceDescriptor, null, GitSCMBuilder.class));
            Set<SCMTraitDescriptor<?>> dedup = new HashSet<>();
            for (Iterator<SCMTraitDescriptor<?>> iterator = all.iterator(); iterator.hasNext(); ) {
                SCMTraitDescriptor<?> d = iterator.next();
                if (dedup.contains(d)
                        || d instanceof GitBrowserSCMSourceTrait.DescriptorImpl) {
                    // remove any we have seen already and ban the browser configuration as it will always be github
                    iterator.remove();
                } else {
                    dedup.add(d);
                }
            }
            List<NamedArrayList<? extends SCMTraitDescriptor<?>>> result = new ArrayList<>();
            NamedArrayList.select(all, Messages.ASFGitSCMNavigator_ASFGitSCMNaviagort_repositories(),
                    new NamedArrayList.Predicate<SCMTraitDescriptor<?>>() {
                        @Override
                        public boolean test(SCMTraitDescriptor<?> scmTraitDescriptor) {
                            return scmTraitDescriptor instanceof SCMNavigatorTraitDescriptor;
                        }
                    },
                    true, result);
            NamedArrayList.select(all, "Within repositories", NamedArrayList
                            .anyOf(NamedArrayList.withAnnotation(Discovery.class),
                                    NamedArrayList.withAnnotation(Selection.class)),
                    true, result);
            NamedArrayList.select(all, "Additional behaviours", null, true, result);
            return result;
        }

        public List<SCMTrait<? extends SCMTrait<?>>> getTraitsDefaults() {
            GitSCMSource.DescriptorImpl descriptor =
                    ExtensionList.lookup(Descriptor.class).get(GitSCMSource.DescriptorImpl.class);
            if (descriptor == null) {
                throw new AssertionError();
            }
            List<SCMTrait<? extends SCMTrait<?>>> result = new ArrayList<>();
            result.addAll(descriptor.getTraitsDefaults());
            return result;
        }

    }

}
