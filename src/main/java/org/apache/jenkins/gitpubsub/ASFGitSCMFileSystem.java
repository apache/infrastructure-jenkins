package org.apache.jenkins.gitpubsub;

import com.cloudbees.plugins.credentials.common.StandardCredentials;
import com.damnhandy.uri.template.UriTemplate;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import java.io.IOException;
import java.net.URL;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Set;
import jenkins.plugins.git.GitSCMTelescope;
import jenkins.scm.api.SCMFile;
import jenkins.scm.api.SCMFileSystem;
import jenkins.scm.api.SCMHead;
import jenkins.scm.api.SCMRevision;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

public class ASFGitSCMFileSystem extends SCMFileSystem {

    /**
     * Constructor.
     *
     * @param remote the remote.
     * @param head   the head.
     * @param rev    the revision.
     */
    public ASFGitSCMFileSystem(String remote, SCMHead head, SCMRevision rev) {
        super(rev);
    }

    @Override
    public long lastModified() throws IOException, InterruptedException {
        return 0;
    }

    @NonNull
    @Override
    public SCMFile getRoot() {
        return null;
    }

    @Extension
    public static class TelescopeImpl extends GitSCMTelescope {

        private static final int TEN_SECONDS_OF_MILLIS = 10000;

        @Override
        public boolean supports(@NonNull String remote) {
            return ASFGitSCMNavigator.GIT_BOX.startsWith(remote) || ASFGitSCMNavigator.GIT_WIP.startsWith(remote);
        }

        @Override
        public void validate(@NonNull String remote, StandardCredentials credentials)
                throws IOException, InterruptedException {
            // no-op because anonymous access
        }

        @Override
        protected SCMFileSystem build(@NonNull String remote, StandardCredentials credentials, @NonNull SCMHead head,
                                      SCMRevision rev) throws IOException, InterruptedException {
            return new ASFGitSCMFileSystem(remote, head, rev);
        }

        @Override
        public long getTimestamp(@NonNull String remote, StandardCredentials credentials, @NonNull String refOrHash)
                throws IOException, InterruptedException {
            String commitUrl = buildTemplateWithRemote("{+server}{?p}{;a,h}", remote)
                    .set("a", "commit")
                    .set("h", refOrHash)
                    .expand();
            Document doc = Jsoup.parse(new URL(commitUrl), TEN_SECONDS_OF_MILLIS);
            Elements elements = doc.select("table.object_header tr td span.datetime");
            try {
                return new SimpleDateFormat(ASFGitSCMNavigator.RFC_2822).parse(elements.get(1).text()).getTime();
            } catch (ParseException e) {
                throw new IOException("Unexpected date format, expected RFC 2822, got " + elements.get(1).text());
            } catch (IndexOutOfBoundsException e) {
                throw new IOException("Unexpected response body, expecting two timestamps only got " + elements.size());
            }
        }

        private UriTemplate buildTemplateWithRemote(String template, @NonNull String remote) throws IOException {
            UriTemplate commitTemplate;
            String server = null;
            String p = null;
            for (String s : new String[]{ASFGitSCMNavigator.GIT_WIP, ASFGitSCMNavigator.GIT_BOX}) {
                if (remote.startsWith(s + "/")) {
                    server = s;
                    p = remote.substring(s.length() + 1);
                    break;
                }
            }
            if (server == null) {
                throw new IOException("Unknown remote: " + remote);
            }

            commitTemplate = UriTemplate.fromTemplate(template);
            commitTemplate.set("server", server).set("p", p);
            return commitTemplate;
        }

        @Override
        public SCMRevision getRevision(@NonNull String remote, StandardCredentials credentials,
                                       @NonNull String refOrHash)
                throws IOException, InterruptedException {
            return null;
        }

        @Override
        public Iterable<SCMRevision> getRevisions(@NonNull String remote, StandardCredentials credentials,
                                                  @NonNull Set<ReferenceType> referenceTypes)
                throws IOException, InterruptedException {
            return null;
        }

        @Override
        public String getDefaultTarget(@NonNull String remote, StandardCredentials credentials)
                throws IOException, InterruptedException {
            return null;
        }
    }
}
