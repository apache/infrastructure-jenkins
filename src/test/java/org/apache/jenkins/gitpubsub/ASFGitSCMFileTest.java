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
import java.util.Set;
import java.util.TreeSet;
import jenkins.plugins.git.AbstractGitSCMSource;
import jenkins.plugins.git.GitTagSCMHead;
import jenkins.scm.api.SCMFile;
import jenkins.scm.api.SCMHead;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.apache.jenkins.gitpubsub.TimestampMatcher.timestamp;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;

public class ASFGitSCMFileTest {
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
    public void given__root__when__children__then__children_returned() throws Exception {
        ASFGitSCMFileSystem fs = new ASFGitSCMFileSystem(serverRootUrl + "/maven.git", new SCMHead("master"), null);
        SCMFile root = fs.getRoot();
        Iterable<SCMFile> children = root.children();
        Set<String> names = new TreeSet<>();
        for (SCMFile f : children) {
            names.add(f.getName());
        }
        assertThat(names, containsInAnyOrder(
                ".gitattributes",
                ".gitignore",
                "Jenkinsfile",
                "LICENSE",
                "NOTICE",
                "README.md",
                "apache-maven",
                "deploySite.sh",
                "doap_Maven.rdf",
                "maven-artifact",
                "maven-builder-support",
                "maven-compat",
                "maven-core",
                "maven-embedder",
                "maven-model-builder",
                "maven-model",
                "maven-plugin-api",
                "maven-repository-metadata",
                "maven-resolver-provider",
                "maven-settings-builder",
                "maven-settings",
                "maven-slf4j-provider",
                "pom.xml",
                "src"
        ));
    }

    @Test
    public void given__child__when__children__then__children_returned() throws Exception {
        ASFGitSCMFileSystem fs = new ASFGitSCMFileSystem(serverRootUrl + "/maven.git", new SCMHead("master"), null);
        SCMFile root = fs.getRoot().child("maven-settings");
        Iterable<SCMFile> children = root.children();
        Set<String> names = new TreeSet<>();
        for (SCMFile f : children) {
            names.add(f.getName());
        }
        assertThat(names, containsInAnyOrder(
                "pom.xml",
                "src"
        ));
    }

    @Test
    public void given__child_directory__when__type__then__directory_returned() throws Exception {
        ASFGitSCMFileSystem fs = new ASFGitSCMFileSystem(serverRootUrl + "/maven.git", new SCMHead("master"), null);
        assertThat(fs.getRoot().child("maven-settings").getType(), is(SCMFile.Type.DIRECTORY));
    }

    @Test
    public void given__child_file__when__type__then__file_returned() throws Exception {
        ASFGitSCMFileSystem fs = new ASFGitSCMFileSystem(serverRootUrl + "/maven.git", new SCMHead("master"), null);
        assertThat(fs.getRoot().child("pom.xml").getType(), is(SCMFile.Type.REGULAR_FILE));
    }

    @Test
    public void given__grandchild_file__when__type__then__file_returned() throws Exception {
        ASFGitSCMFileSystem fs = new ASFGitSCMFileSystem(serverRootUrl + "/maven.git", new SCMHead("master"), null);
        assertThat(fs.getRoot().child("maven-settings").child("pom.xml").getType(), is(SCMFile.Type.REGULAR_FILE));
    }

    @Test
    public void given__child_file__when__content__then__contents_returned() throws Exception {
        ASFGitSCMFileSystem fs = new ASFGitSCMFileSystem(serverRootUrl + "/maven.git", new SCMHead("master"), null);
        assertThat(fs.getRoot().child(".gitignore").contentAsString(), is("target/\n"
                + ".project\n"
                + ".classpath\n"
                + ".settings/\n"
                + ".svn/\n"
                + "bin/\n"
                + "# Intellij\n"
                + "*.ipr\n"
                + "*.iml\n"
                + ".idea\n"
                + "out/\n"
                + ".DS_Store\n"
                + "/bootstrap\n"
                + "/dependencies.xml\n"
                + ".java-version\n"));
    }

    @Test
    public void given__branch_root__when__lastModified__then__commit_timestamp_returned() throws Exception {
        ASFGitSCMFileSystem fs = new ASFGitSCMFileSystem(serverRootUrl + "/maven.git", new SCMHead("master"), null);
        assertThat(fs.getRoot().lastModified(), timestamp("Wed, 15 Nov 2017 02:54:15 +0000"));
    }

    @Test
    public void given__commit_root__when__lastModified__then__commit_timestamp_returned() throws Exception {
        SCMHead head = new SCMHead("master");
        ASFGitSCMFileSystem fs = new ASFGitSCMFileSystem(serverRootUrl + "/maven.git", head, new AbstractGitSCMSource
                .SCMRevisionImpl(head, "4d49d3b05b2e3d3a4530bb27e8cc162ab50baa7c"));
        assertThat(fs.getRoot().lastModified(), timestamp("Thu, 9 Nov 2017 08:30:47 +0000"));
    }

    @Test
    public void given__lightweight_tag_root__lastModified__then__commit_timestamp_returned() throws Exception {
        ASFGitSCMFileSystem fs = new ASFGitSCMFileSystem(serverRootUrl + "/maven.git",
                new GitTagSCMHead("lightweight-tag", 0L), null);
        assertThat(fs.getRoot().lastModified(), timestamp("Thu, 26 Oct 2017 08:30:12 +0000"));
    }

    @Test
    public void given__annotated_tag_root__when__lastModified__then__tag_timestamp_returned() throws Exception {
        ASFGitSCMFileSystem fs = new ASFGitSCMFileSystem(serverRootUrl + "/maven.git",
                new GitTagSCMHead("annotated-tag", 0L), null);
        assertThat(fs.getRoot().lastModified(), timestamp("Mon, 20 Nov 2017 11:38:47 +0000")
        );
    }

    @Test
    public void given__file__when__lastModified__then__commit_timestamp_returned() throws Exception {
        ASFGitSCMFileSystem fs = new ASFGitSCMFileSystem(serverRootUrl + "/maven.git", new SCMHead("master"), null);
        assertThat(fs.getRoot().child("Jenkinsfile").lastModified(), timestamp("Tue, 29 Aug 2017 20:47:50 +0000"));
    }

    @Test
    public void given__directory__when__lastModified__then__commit_timestamp_returned() throws Exception {
        ASFGitSCMFileSystem fs = new ASFGitSCMFileSystem(serverRootUrl + "/maven.git", new SCMHead("master"), null);
        assertThat(fs.getRoot().child("src").lastModified(), timestamp("Sat, 18 Feb 2017 14:15:18 +0000"));
    }

}
