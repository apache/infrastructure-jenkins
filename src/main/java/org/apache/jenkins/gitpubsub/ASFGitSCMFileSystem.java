package org.apache.jenkins.gitpubsub;

import com.cloudbees.plugins.credentials.common.StandardCredentials;
import com.damnhandy.uri.template.UriTemplate;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import java.io.IOException;
import java.net.URL;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.AbstractList;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.plugins.git.AbstractGitSCMSource;
import jenkins.plugins.git.GitSCMTelescope;
import jenkins.plugins.git.GitTagSCMHead;
import jenkins.plugins.git.GitTagSCMRevision;
import jenkins.scm.api.SCMFile;
import jenkins.scm.api.SCMFileSystem;
import jenkins.scm.api.SCMHead;
import jenkins.scm.api.SCMRevision;
import org.eclipse.jgit.lib.Constants;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

public class ASFGitSCMFileSystem extends SCMFileSystem {

    static final int TEN_SECONDS_OF_MILLIS = 10000;
    private static final Logger LOGGER = Logger.getLogger(ASFGitSCMFileSystem.class.getName());
    private final String remote;
    private final String refOrHash;

    /**
     * Constructor.
     *
     * @param remote the remote.
     * @param head   the head.
     * @param rev    the revision.
     */
    public ASFGitSCMFileSystem(String remote, SCMHead head, SCMRevision rev) {
        super(rev instanceof AbstractGitSCMSource.SCMRevisionImpl ? rev : null);
        this.remote = remote;
        this.refOrHash = rev instanceof AbstractGitSCMSource.SCMRevisionImpl
                ? ((AbstractGitSCMSource.SCMRevisionImpl) rev).getHash()
                : (head instanceof GitTagSCMHead ? Constants.R_TAGS+head.getName() : Constants.R_HEADS+head.getName());
    }

    @Override
    public long lastModified() throws IOException, InterruptedException {
        SCMRevision revision = getRevision();
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

    @NonNull
    @Override
    public SCMFile getRoot() {
        return new ASFGitSCMFile(remote, refOrHash);
    }

    static UriTemplate buildTemplateWithRemote(String template, @NonNull String remote) throws IOException {
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


    @Extension
    public static class TelescopeImpl extends GitSCMTelescope {


        @Override
        public boolean supports(@NonNull String remote) {
            return remote.startsWith(ASFGitSCMNavigator.GIT_BOX) || remote.startsWith(ASFGitSCMNavigator.GIT_WIP);
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

        @Override
        public SCMRevision getRevision(@NonNull String remote, StandardCredentials credentials,
                                       @NonNull String refOrHash)
                throws IOException, InterruptedException {
            String commitUrl = buildTemplateWithRemote("{+server}{?p}{;a,h}", remote)
                    .set("a", "commit")
                    .set("h", refOrHash)
                    .expand();
            Document doc = Jsoup.parse(new URL(commitUrl), TEN_SECONDS_OF_MILLIS);
            Elements elements = doc.select("table.object_header tr td.sha1");
            String revision = elements.get(0).text();
            if (refOrHash.startsWith(Constants.R_TAGS)) {
                elements = doc.select("table.object_header tr td span.datetime");
                long time;
                try {
                    time =
                            new SimpleDateFormat(ASFGitSCMNavigator.RFC_2822).parse(elements.get(1).text()).getTime();
                } catch (ParseException e) {
                    throw new IOException("Unexpected date format, expected RFC 2822, got " + elements.get(1).text());
                }
                return new GitTagSCMRevision(new GitTagSCMHead(refOrHash.substring(Constants.R_TAGS.length()), time), revision);
            } else if (refOrHash.startsWith(Constants.R_HEADS)){
                return new AbstractGitSCMSource.SCMRevisionImpl(new SCMHead(refOrHash.substring(Constants.R_HEADS.length())), revision);
            } else {
                // TODO fix for hash without branch name
                return null;
            }
        }

        @Override
        public Iterable<SCMRevision> getRevisions(@NonNull String remote, StandardCredentials credentials,
                                                  @NonNull Set<ReferenceType> referenceTypes)
                throws IOException, InterruptedException {
            List<String> result = new ArrayList<>();
            TYPES:
            for (ReferenceType referenceType : referenceTypes) {
                String actionUrl;
                Document doc;
                Elements elements;
                String prefix;
                switch (referenceType) {
                    case HEAD:
                        actionUrl = buildTemplateWithRemote("{+server}{?p}{;a}", remote)
                                .set("a", "heads")
                                .expand();
                        doc = Jsoup.parse(new URL(actionUrl), TEN_SECONDS_OF_MILLIS);
                        elements = doc.select("table.heads tr td a.name");
                        prefix = Constants.R_HEADS;
                        break;
                    case TAG:
                        actionUrl = buildTemplateWithRemote("{+server}{?p}{;a}", remote)
                                .set("a", "tags")
                                .expand();
                        doc = Jsoup.parse(new URL(actionUrl), TEN_SECONDS_OF_MILLIS);
                        elements = doc.select("table.tags tr td a.name");
                        prefix = Constants.R_TAGS;
                        break;
                    default:
                        LOGGER.log(Level.WARNING, "Ignoring unexpected reference type {0}", referenceType);
                        continue TYPES;
                }
                for (Element element : elements) {
                    result.add(prefix + element.text());
                }
            }
            return new AbstractList<SCMRevision>() {
                private final List<SCMRevision> cache = new ArrayList<>(result.size());

                {
                    for (int i = 0; i < result.size(); i++) {
                        cache.add(null);
                    }
                }

                @Override
                public SCMRevision get(int index) {
                    SCMRevision r = cache.get(index);
                    if (r == null) {
                        try {
                            r = getRevision(remote, credentials, result.get(index));
                        } catch (IOException | InterruptedException e) {
                            throw new RuntimeException(e);
                        }
                        cache.set(index, r);
                    }
                    return r;
                }

                @Override
                public int size() {
                    return result.size();
                }
            };
        }

        @Override
        public String getDefaultTarget(@NonNull String remote, StandardCredentials credentials)
                throws IOException, InterruptedException {
            String commitUrl = buildTemplateWithRemote("{+server}{?p}{;a}", remote)
                    .set("a", "heads")
                    .expand();
            Document doc = Jsoup.parse(new URL(commitUrl), TEN_SECONDS_OF_MILLIS);
            Elements elements = doc.select("table.heads tr td.current_head a.name");
            if (elements.size()>0) {
                return Constants.R_HEADS+elements.get(0).text();
            }
            return null;
        }
    }
}
