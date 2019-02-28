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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.ExtensionList;
import hudson.Util;
import hudson.console.HyperlinkNote;
import hudson.model.Action;
import hudson.model.Descriptor;
import hudson.model.TaskListener;
import hudson.util.ListBoxModel;
import java.io.IOException;
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
import jenkins.scm.api.SCMHeadEvent;
import jenkins.scm.api.SCMNavigator;
import jenkins.scm.api.SCMNavigatorDescriptor;
import jenkins.scm.api.SCMNavigatorEvent;
import jenkins.scm.api.SCMNavigatorOwner;
import jenkins.scm.api.SCMSource;
import jenkins.scm.api.SCMSourceEvent;
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
import org.jenkins.ui.icon.Icon;
import org.jenkins.ui.icon.IconSet;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

/**
 * A {@link SCMNavigator} that navigates {@code apache.org} hosted git repositories.
 */
public class ASFGitSCMNavigator extends SCMNavigator {

    /**
     * Our object mapper.
     */
    private static final ObjectMapper MAPPER = new ObjectMapper();
    /**
     * The date format used by GitWeb.
     */
    static final String RFC_2822 = "EEE, dd MMM yyyy HH:mm:ss Z";
    /**
     * The first Git hosting for Apache.
     */
    static final String GIT_WIP = "https://git-wip-us.apache.org/repos/asf";
    /**
     * The second Git hosting for Apache.
     */
    static final String GIT_BOX = "https://gitbox.apache.org/repos/asf";
    /**
     * The server that we are navigating.
     */
    @NonNull
    private final String server;
    /**
     * The traits to apply.
     */
    private List<SCMTrait<?>> traits = new ArrayList<>();

    /**
     * Constructor.
     *
     * @param server the server to navigate.
     */
    @DataBoundConstructor
    public ASFGitSCMNavigator(@NonNull String server) {
        this.server = server;
    }

    /**
     * Gets the server to navigate.
     *
     * @return the server to navigate.
     */
    @NonNull
    public String getServer() {
        return server;
    }

    /**
     * Gets the traits.
     *
     * @return the traits.
     */
    @NonNull
    public List<SCMTrait<?>> getTraits() {
        return Collections.unmodifiableList(traits);
    }

    /**
     * Sets the traits.
     *
     * @param traits the traits.
     */
    @DataBoundSetter
    public void setTraits(@CheckForNull List<? extends SCMTrait<?>> traits) {
        this.traits = new ArrayList<>(Util.fixNull(traits));
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    protected String id() {
        return server;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void visitSources(@NonNull SCMSourceObserver observer, @NonNull SCMSourceEvent<?> event)
            throws IOException, InterruptedException {
        visitSource(event.getSourceName(), observer);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void visitSources(@NonNull SCMSourceObserver observer, @NonNull SCMHeadEvent<?> event)
            throws IOException, InterruptedException {
        visitSource(event.getSourceName(), observer);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void visitSources(@NonNull final SCMSourceObserver observer) throws IOException, InterruptedException {
        ASFGitSCMFileSystem.preRequestSleep();
        try (ASFGitSCMNavigatorRequest request = new ASFGitSCMNavigatorContext()
                .withTraits(traits)
                .newRequest(this, observer)) {
            JsonNode repositories = MAPPER.readTree(new URL(server.replaceAll("repos/[^/]+$", "repositories.json")));
            int count = 0;
            String line;
            observer.getListener().getLogger().format("%n  Checking repositories...%n");
            for (JsonNode project : repositories.path("projects")) {
                for (Iterator<String> i = project.path("repositories").fieldNames(); i.hasNext(); ) {
                    final String repo = i.next();
                    count++;
                    observer.getListener().getLogger().format("%n    Checking repository %s%n",
                            HyperlinkNote
                                    .encodeTo(server + "?p=" + URLEncoder.encode(repo, "UTF-8") + ".git;a=summary",
                                            repo));
                    if (request.process(repo, new SCMNavigatorRequest.SourceLambda() {
                        @NonNull
                        @Override
                        public SCMSource create(@NonNull String projectName) throws IOException, InterruptedException {
                            return new ASFGitSCMSourceBuilder(getId() + "::" + projectName,
                                    server, projectName
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
                        observer.getListener().getLogger()
                                .format("%n  %d repositories were processed (query complete)%n",
                                        count);
                        return;
                    }
                }
            }
            observer.getListener().getLogger().format("%n  %d repositories were processed%n", count);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void visitSource(@NonNull String sourceName, @NonNull SCMSourceObserver observer)
            throws IOException, InterruptedException {
        try (ASFGitSCMNavigatorRequest request = new ASFGitSCMNavigatorContext()
                .withTraits(traits)
                .newRequest(this, observer)) {
            observer.getListener().getLogger().format("%n    Checking repository %s%n",
                    HyperlinkNote
                            .encodeTo(server + "?p=" + URLEncoder.encode(sourceName, "UTF-8") + ".git;a=summary",
                                    sourceName));
            if (request.process(sourceName, new SCMNavigatorRequest.SourceLambda() {
                @NonNull
                @Override
                public SCMSource create(@NonNull String projectName) throws IOException, InterruptedException {
                    return new ASFGitSCMSourceBuilder(getId() + "::" + projectName,
                            server, projectName
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
                observer.getListener().getLogger().format("%n  1 repository was processed (query complete)%n");
                return;
            }
            observer.getListener().getLogger().format("%n  1 repository was processed%n");
        }
    }

    /**
     * {@inheritDoc}
     */
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

    /**
     * Our descriptor.
     */
    @Extension
    public static class DescriptorImpl extends SCMNavigatorDescriptor {

        /**
         * {@inheritDoc}
         */
        @Nonnull
        @Override
        public String getDisplayName() {
            return Messages.ASFGitSCMNavigator_displayName();
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public String getIconClassName() {
            return "icon-git-apache-org-folder";
        }

        /**
         * {@inheritDoc}
         */
        @NonNull
        @Override
        public String getDescription() {
            return Messages.ASFGitSCMNavigator_description();
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public String getPronoun() {
            return Messages.ASFGitSCMNavigator_displayName();
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public SCMNavigator newInstance(String name) {
            ASFGitSCMNavigator result = new ASFGitSCMNavigator(GIT_BOX);
            result.setTraits(getTraitsDefaults());
            return result;
        }

        /**
         * Populates the drop-down for {@link ASFGitSCMNavigator#getServer()}
         *
         * @return the drop-down entries.
         */
        public ListBoxModel doFillServerItems() {
            ListBoxModel result = new ListBoxModel();
            result.add(Messages.ASFGitSCMNavigator_gitWip(), GIT_WIP);
            result.add(Messages.ASFGitSCMNavigator_gitBox(), GIT_BOX);
            return result;
        }

        /**
         * Populates the available trait descriptors.
         *
         * @return the available trait descriptors.
         */
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
            NamedArrayList.select(all, Messages.ASFGitSCMNavigator_withinRepositories(),
                    NamedArrayList.anyOf(NamedArrayList.withAnnotation(Discovery.class),
                            NamedArrayList.withAnnotation(Selection.class)),
                    true, result);
            NamedArrayList.select(all, Messages.ASFGitSCMNavigator_additionalBehaviours(), null, true, result);
            return result;
        }

        /**
         * Populate the default traits for new instances.
         *
         * @return the default traits.
         */
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

    static {
        IconSet.icons.addIcon(
                new Icon("icon-git-apache-org-folder icon-sm",
                        "plugin/asf-gitpubsub-jenkins/images/16x16/asf-git-folder.png",
                        Icon.ICON_SMALL_STYLE));
        IconSet.icons.addIcon(
                new Icon("icon-git-apache-org-folder icon-md",
                        "plugin/asf-gitpubsub-jenkins/images/24x24/asf-git-folder.png",
                        Icon.ICON_MEDIUM_STYLE));
        IconSet.icons.addIcon(
                new Icon("icon-git-apache-org-folder icon-lg",
                        "plugin/asf-gitpubsub-jenkins/images/32x32/asf-git-folder.png",
                        Icon.ICON_LARGE_STYLE));
        IconSet.icons.addIcon(
                new Icon("icon-git-apache-org-folder icon-xlg",
                        "plugin/asf-gitpubsub-jenkins/images/48x48/asf-git-folder.png",
                        Icon.ICON_XLARGE_STYLE));
    }
}
