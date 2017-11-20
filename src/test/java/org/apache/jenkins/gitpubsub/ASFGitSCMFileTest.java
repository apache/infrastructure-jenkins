package org.apache.jenkins.gitpubsub;

import com.github.tomakehurst.wiremock.junit.WireMockRule;
import java.util.Set;
import java.util.TreeSet;
import jenkins.scm.api.SCMFile;
import jenkins.scm.api.SCMHead;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
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
        for (SCMFile f: children) {
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
        for (SCMFile f: children) {
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


}
