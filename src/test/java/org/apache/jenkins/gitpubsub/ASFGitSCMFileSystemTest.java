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

import com.github.tomakehurst.wiremock.junit.WireMockRule;
import hudson.plugins.git.GitChangeLogParser;
import hudson.plugins.git.GitChangeSet;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.EnumSet;
import java.util.List;
import jenkins.plugins.git.AbstractGitSCMSource;
import jenkins.plugins.git.GitSCMTelescope;
import jenkins.plugins.git.GitTagSCMHead;
import jenkins.plugins.git.GitTagSCMRevision;
import jenkins.scm.api.SCMHead;
import jenkins.scm.api.SCMRevision;
import jenkins.scm.api.mixin.TagSCMHead;
import org.apache.commons.io.IOUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.apache.jenkins.gitpubsub.TimestampMatcher.timestamp;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;

public class ASFGitSCMFileSystemTest {
    @Rule
    public WireMockRule wire = new WireMockRule(wireMockConfig().dynamicPort());
    private String serverRootUrl;

    @Before
    public void injectMockServer() throws Exception {
        serverRootUrl = "http://localhost:" + wire.port() + "/repos/asf";
        ASFGitSCMFileSystem.GIT_WEB_HOSTS.add(serverRootUrl);
    }

    @After
    public void removeMockServer() throws Exception {
        ASFGitSCMFileSystem.GIT_WEB_HOSTS.remove(serverRootUrl);
    }

    @Test
    public void smokeTestGetTimestamp() throws Exception {
        long timestamp = new ASFGitSCMFileSystem.TelescopeImpl()
                .getTimestamp(serverRootUrl+  "/maven.git", null, "maven-3.5.0");
        assertThat(timestamp, is(1491248130000L));
    }

    @Test
    public void smokeTestGetRevisions() throws Exception {
        Iterable<SCMRevision> revisions = new ASFGitSCMFileSystem.TelescopeImpl()
                .getRevisions(serverRootUrl+"/maven.git", null, EnumSet.allOf(
                        GitSCMTelescope.ReferenceType.class));
        GitTagSCMRevision annotatedTagR = null;
        GitTagSCMRevision lightweightTagR = null;
        AbstractGitSCMSource.SCMRevisionImpl masterBranchR = null;
        for (SCMRevision r: revisions) {
            if (r instanceof AbstractGitSCMSource.SCMRevisionImpl && r.getHead().getName().equals("master")) {
                masterBranchR = (AbstractGitSCMSource.SCMRevisionImpl) r;
            } else if (r instanceof GitTagSCMRevision && r.getHead().getName().equals("lightweight-tag")) {
                lightweightTagR = (GitTagSCMRevision) r;
            } else if (r instanceof GitTagSCMRevision && r.getHead().getName().equals("annotated-tag")) {
                annotatedTagR = (GitTagSCMRevision) r;
            }
            if (masterBranchR != null && annotatedTagR != null && lightweightTagR != null) {
                break;
            }
        }
        assertThat(masterBranchR.getHash(), is("f5f76c70e1828a7e6c6267fc4bc53abc35c19ce7"));
        assertThat(lightweightTagR.getHash(), is("5919b7450d2e01f079e930d92df7910af39d489a"));
        assertThat(annotatedTagR.getHash(), is("61a8c2048bec05c0748b143e3bfd54f97d1a1423"));
        assertThat(((TagSCMHead)lightweightTagR.getHead()).getTimestamp(),
                timestamp("Thu, 26 Oct 2017 08:30:12 +0000")
        );
        assertThat(((TagSCMHead)annotatedTagR.getHead()).getTimestamp(),
                timestamp("Mon, 20 Nov 2017 11:38:47 +0000")
        );
    }

    @Test
    public void given__branch__when__getRevision__then__branch_returned() throws Exception {
        SCMRevision masterR = new ASFGitSCMFileSystem.TelescopeImpl()
                .getRevision(serverRootUrl+"/maven.git", null, "refs/heads/master");
        assertThat(masterR, instanceOf(AbstractGitSCMSource.SCMRevisionImpl.class));
        AbstractGitSCMSource.SCMRevisionImpl masterBranchR = (AbstractGitSCMSource.SCMRevisionImpl) masterR;
        assertThat(masterBranchR.getHash(), is("f5f76c70e1828a7e6c6267fc4bc53abc35c19ce7"));
    }

    @Test
    public void given__lightweight_tag__when__getRevision__then__tag_returned() throws Exception {
        SCMRevision lightweightR = new ASFGitSCMFileSystem.TelescopeImpl()
                .getRevision(serverRootUrl+"/maven.git", null, "refs/tags/lightweight-tag");
        assertThat(lightweightR, instanceOf(GitTagSCMRevision.class));
        GitTagSCMRevision lightweightTagR = (GitTagSCMRevision) lightweightR;
        assertThat(lightweightTagR.getHash(), is("5919b7450d2e01f079e930d92df7910af39d489a"));
        assertThat(((TagSCMHead)lightweightTagR.getHead()).getTimestamp(),
                timestamp("Thu, 26 Oct 2017 08:30:12 +0000")
        );
    }

    @Test
    public void given__annotated_tag__when__getRevision__then__tag_returned() throws Exception {
        SCMRevision annotatedR = new ASFGitSCMFileSystem.TelescopeImpl()
                .getRevision(serverRootUrl+"/maven.git", null, "refs/tags/annotated-tag");
        assertThat(annotatedR, instanceOf(GitTagSCMRevision.class));
        GitTagSCMRevision annotatedTagR = (GitTagSCMRevision) annotatedR;
        assertThat(annotatedTagR.getHash(), is("61a8c2048bec05c0748b143e3bfd54f97d1a1423"));
        assertThat(((TagSCMHead)annotatedTagR.getHead()).getTimestamp(),
                timestamp("Mon, 20 Nov 2017 11:38:47 +0000")
        );
    }

    @Test
    public void given__branch__when__lastModified__then__commit_timestamp_returned() throws Exception {
        ASFGitSCMFileSystem fs = new ASFGitSCMFileSystem(serverRootUrl+"/maven.git", new SCMHead("master"), null);
        assertThat(fs.lastModified(), timestamp("Wed, 15 Nov 2017 02:54:15 +0000"));
    }

    @Test
    public void given__commit__when__lastModified__then__commit_timestamp_returned() throws Exception {
        SCMHead head = new SCMHead("master");
        ASFGitSCMFileSystem fs = new ASFGitSCMFileSystem(serverRootUrl+"/maven.git", head, new AbstractGitSCMSource
                .SCMRevisionImpl(head, "4d49d3b05b2e3d3a4530bb27e8cc162ab50baa7c"));
        assertThat(fs.lastModified(), timestamp("Thu, 9 Nov 2017 08:30:47 +0000"));
    }

    @Test
    public void given__lightweight_tag__lastModified__then__commit_timestamp_returned() throws Exception {
        ASFGitSCMFileSystem fs = new ASFGitSCMFileSystem(serverRootUrl + "/maven.git",
                new GitTagSCMHead("lightweight-tag", 0L), null);
        assertThat(fs.lastModified(), timestamp("Thu, 26 Oct 2017 08:30:12 +0000"));
    }

    @Test
    public void given__annotated_tag__when__lastModified__then__tag_timestamp_returned() throws Exception {
        ASFGitSCMFileSystem fs = new ASFGitSCMFileSystem(serverRootUrl + "/maven.git",
                new GitTagSCMHead("annotated-tag", 0L), null);
        assertThat(fs.lastModified(), timestamp("Mon, 20 Nov 2017 11:38:47 +0000")
        );
    }

    @Test
    public void given__remote__when__getDefaultTarget__then__default_target_returned() throws Exception {
        assertThat(new ASFGitSCMFileSystem.TelescopeImpl().getDefaultTarget(serverRootUrl+"/maven.git", null), is("refs/heads/master"));
    }

    @Test
    public void given__commit_range__when__changesSince__then__changes_returned() throws Exception {
        SCMHead head = new SCMHead("master");
        ASFGitSCMFileSystem fs = new ASFGitSCMFileSystem(serverRootUrl + "/maven.git", head, new AbstractGitSCMSource
                .SCMRevisionImpl(head, "114ef6c5a2802e8758e466af92b70f51fd7a2929"));
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        assertThat(fs.changesSince(
                new AbstractGitSCMSource.SCMRevisionImpl(head, "748551d0274cc2eebfb2f976536d18277e564584"),
                stream), is(true));
        GitChangeLogParser parser = new GitChangeLogParser(true);
        List<GitChangeSet> actual = parser.parse(new ByteArrayInputStream(stream.toByteArray()));
        List<GitChangeSet> expected = parser.parse(new ByteArrayInputStream(IOUtils.toByteArray(
                getClass().getResource(getClass().getSimpleName() + "/changesSince.txt"))));
        assertThat(actual, is(expected));
    }

    @Test
    public void given__multi_page_commit_range__when__changesSince__then__changes_returned() throws Exception {
        SCMHead head = new SCMHead("master");
        ASFGitSCMFileSystem fs = new ASFGitSCMFileSystem(serverRootUrl + "/maven.git", head, new AbstractGitSCMSource
                .SCMRevisionImpl(head, "3d0efa36963c217527230228a11ab44050ca1b10"));
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        assertThat(fs.changesSince(
                new AbstractGitSCMSource.SCMRevisionImpl(head, "3982c195e91e3ea0f73ce9e61c6ddc57137726e9"),
                stream), is(true));
        GitChangeLogParser parser = new GitChangeLogParser(true);
        List<GitChangeSet> actual = parser.parse(new ByteArrayInputStream(stream.toByteArray()));
        List<GitChangeSet> expected = parser.parse(new ByteArrayInputStream(IOUtils.toByteArray(
                getClass().getResource(getClass().getSimpleName() + "/long-changesSince.txt"))));
        assertThat(actual, is(expected));
    }

}
