package org.apache.jenkins.gitpubsub;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.junit.WireMockRule;
import java.util.EnumSet;
import jenkins.plugins.git.GitSCMTelescope;
import jenkins.scm.api.SCMRevision;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.*;

/**
 * @author Stephen Connolly
 */
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
//        wire.startRecording(WireMock.recordSpec()
//                .forTarget("https://git-wip-us.apache.org/")
//                .ignoreRepeatRequests()
//                .chooseBodyMatchTypeAutomatically()
//                .build()
//        );
        long timestamp = new ASFGitSCMFileSystem.TelescopeImpl()
                .getTimestamp(serverRootUrl+  "/maven.git", null, "maven-3.5.0");
        assertThat(timestamp, is(1491248130000L));
//        wire.stopRecording();
    }
    @Test
    public void smokeTestGetRevisions() throws Exception {
        Iterable<SCMRevision> revisions = new ASFGitSCMFileSystem.TelescopeImpl()
                .getRevisions("https://git-wip-us.apache.org/repos/asf/maven.git", null, EnumSet.allOf(
                        GitSCMTelescope.ReferenceType.class));
        for (SCMRevision r: revisions) {
            System.out.println(r);
        }
    }
}
