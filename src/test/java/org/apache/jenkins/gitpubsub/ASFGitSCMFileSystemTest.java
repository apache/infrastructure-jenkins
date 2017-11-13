package org.apache.jenkins.gitpubsub;

import java.util.EnumSet;
import jenkins.plugins.git.GitSCMTelescope;
import jenkins.scm.api.SCMRevision;
import org.junit.Test;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.*;

/**
 * @author Stephen Connolly
 */
public class ASFGitSCMFileSystemTest {
    @Test
    public void smokeTestGetTimestamp() throws Exception {
        long timestamp = new ASFGitSCMFileSystem.TelescopeImpl()
                .getTimestamp("https://git-wip-us.apache.org/repos/asf/maven.git", null, "maven-3.5.0");
        assertThat(timestamp, is(1491248130000L));
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
