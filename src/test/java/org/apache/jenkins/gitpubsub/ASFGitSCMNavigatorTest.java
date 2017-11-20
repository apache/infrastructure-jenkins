package org.apache.jenkins.gitpubsub;

import com.github.tomakehurst.wiremock.junit.WireMockRule;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import hudson.ExtensionList;
import hudson.model.AbstractItem;
import hudson.model.ItemGroup;
import hudson.model.Job;
import hudson.model.TaskListener;
import hudson.model.TopLevelItem;
import hudson.model.TopLevelItemDescriptor;
import hudson.util.LogTaskListener;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.scm.api.SCMSource;
import jenkins.scm.api.SCMSourceCriteria;
import jenkins.scm.api.SCMSourceObserver;
import jenkins.scm.api.SCMSourceOwner;
import jenkins.scm.impl.NoOpProjectObserver;
import jenkins.scm.impl.trait.WildcardSCMSourceFilterTrait;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.TestExtension;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertThat;

public class ASFGitSCMNavigatorTest {

    @Rule
    public JenkinsRule j = new JenkinsRule();
    @Rule
    public WireMockRule wire = new WireMockRule(wireMockConfig().dynamicPort());

    @Test
    public void given__instance_without_filters__when__visitSources__then__maven_repo_found() throws Exception {
        ASFGitSCMNavigator instance =
                new ASFGitSCMNavigator("http://localhost:" + wire.port() + "/repos/asf");
        CapturingObserver probe = new CapturingObserver(j.createProject(MockSCMSourceOwner.class));
        instance.visitSources(probe);
        assertThat(probe.getObserved(), hasItem(is("maven")));
    }


    @Test
    public void given__instance_with_filter__when__visitSources__then__maven_repo_not_found() throws Exception {
        ASFGitSCMNavigator instance =
                new ASFGitSCMNavigator("http://localhost:" + wire.port() + "/repos/asf");
        instance.setTraits(Collections.singletonList(new WildcardSCMSourceFilterTrait("*", "maven*")));
        CapturingObserver probe = new CapturingObserver(j.createProject(MockSCMSourceOwner.class));
        instance.visitSources(probe);
        assertThat(probe.getObserved(), not(hasItem(is("maven"))));
    }

    private static class CapturingObserver extends SCMSourceObserver {
        private final SCMSourceOwner context;
        private final Set<String> observed = new TreeSet<>();

        private CapturingObserver(SCMSourceOwner context) {
            this.context = context;
        }

        public Set<String> getObserved() {
            return observed;
        }

        @NonNull
        @Override
        public SCMSourceOwner getContext() {
            return context;
        }

        @NonNull
        @Override
        public TaskListener getListener() {
            return new LogTaskListener(Logger.getAnonymousLogger(), Level.INFO);
        }

        @NonNull
        @Override
        public ProjectObserver observe(@NonNull String projectName)
                throws IllegalArgumentException, IOException, InterruptedException {
            observed.add(projectName);
            return new NoOpProjectObserver();
        }

        @Override
        public void addAttribute(@NonNull String key, @Nullable Object value)
                throws IllegalArgumentException, ClassCastException {

        }
    }

    public static class MockSCMSourceOwner extends AbstractItem implements TopLevelItem, SCMSourceOwner {

        private SCMSourceCriteria criteria;

        public MockSCMSourceOwner(ItemGroup parent, String name) {
            super(parent, name);
        }

        public SCMSourceCriteria getCriteria() {
            return criteria;
        }

        public void setCriteria(SCMSourceCriteria criteria) {
            this.criteria = criteria;
        }

        @Override
        public Collection<? extends Job> getAllJobs() {
            return Collections.emptyList();
        }

        @Override
        public void onCreatedFromScratch() {

        }

        @NonNull
        @Override
        public List<SCMSource> getSCMSources() {
            return Collections.emptyList();
        }

        @Override
        public SCMSource getSCMSource(String sourceId) {
            return null;
        }

        @Override
        public void onSCMSourceUpdated(@NonNull SCMSource source) {

        }

        @Override
        public SCMSourceCriteria getSCMSourceCriteria(@NonNull SCMSource source) {
            return criteria;
        }

        @Override
        public TopLevelItemDescriptor getDescriptor() {
            return ExtensionList.lookup(TopLevelItemDescriptor.class).get(DescriptorImpl.class);
        }

        @TestExtension
        public static class DescriptorImpl extends TopLevelItemDescriptor {

            @Override
            public TopLevelItem newInstance(ItemGroup itemGroup, String s) {
                return new MockSCMSourceOwner(itemGroup, s);
            }
        }
    }
}
