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
import com.ning.http.client.AsyncCompletionHandlerBase;
import com.ning.http.client.HttpResponseBodyPart;
import com.ning.http.client.PerRequestConfig;
import com.ning.http.client.RequestBuilder;
import com.ning.http.client.Response;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.Util;
import hudson.model.AsyncPeriodicWork;
import hudson.model.TaskListener;
import hudson.plugins.git.GitSCM;
import hudson.plugins.git.GitStatus;
import hudson.plugins.git.extensions.impl.IgnoreNotifyCommit;
import hudson.scm.SCM;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Collections;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.plugins.asynchttpclient.AHC;
import jenkins.plugins.git.AbstractGitSCMSource;
import jenkins.plugins.git.GitSCMSource;
import jenkins.scm.api.SCMEvent;
import jenkins.scm.api.SCMHead;
import jenkins.scm.api.SCMHeadEvent;
import jenkins.scm.api.SCMNavigator;
import jenkins.scm.api.SCMRevision;
import jenkins.scm.api.SCMSource;
import org.apache.commons.lang.StringUtils;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.transport.RemoteConfig;
import org.eclipse.jgit.transport.URIish;

import static jenkins.scm.api.SCMEvent.Type.CREATED;
import static jenkins.scm.api.SCMEvent.Type.REMOVED;
import static jenkins.scm.api.SCMEvent.Type.UPDATED;

/**
 * Queries GitPubSub for events, parses them and passes them on to {@link SCMEvent}.
 */
@Extension
public class GitPubSubPoll extends AsyncPeriodicWork {
    private static final Logger LOGGER = Logger.getLogger(GitPubSubPoll.class.getName());
    /**
     * How often to check if the request is still alive.
     */
    private static long periodSeconds = Long.getLong(GitPubSubPoll.class.getName() + ".periodSeconds", 10);
    /**
     * How long to keep the request open before closing and re-opening.
     */
    private static int requestRecycleMins =
            Integer.getInteger(GitPubSubPoll.class.getName() + ".requestRecycleMins", 5);

    /**
     * The last timestamp received.
     */
    private volatile long lastTS;
    /**
     * The last time we received anything.
     */
    private volatile long lastTime;
    /**
     * The current request.
     */
    private Future<?> longPollRequest;

    public GitPubSubPoll() {
        super("ASG GitPubSub poll");
    }

    @Override
    protected void execute(TaskListener listener) throws IOException, InterruptedException {
        if (longPollRequest != null) {
            if (lastTime - System.currentTimeMillis() > TimeUnit.SECONDS.toMillis(60)) {
                LOGGER.log(Level.FINE, "GitPubSub request looks dead, restarting...");
                longPollRequest.cancel(false);
            } else if (!longPollRequest.isDone() && !longPollRequest.isCancelled()) {
                // still alive
                return;
            }
        } else {
            LOGGER.log(Level.INFO, "Starting GitPubSub request...");
        }
        RequestBuilder builder = new RequestBuilder("GET")
                .setUrl(JsonHandler.GITPUBSUB_URL)
                .setPerRequestConfig(new PerRequestConfig(null, (requestRecycleMins + 1) * 60 * 1000));
        if (lastTS != 0) {
            builder.addHeader("X-Fetch-Since", Long.toString(lastTS));
        }
        longPollRequest = AHC.instance().executeRequest(builder.build(), new JsonHandler());
    }

    @Override
    public long getRecurrencePeriod() {
        return TimeUnit.SECONDS.toMillis(Math.min(3600, Math.max(1, periodSeconds)));
    }

    @Override
    protected Level getNormalLoggingLevel() {
        return Level.FINE;
    }

    @Override
    protected Level getSlowLoggingLevel() {
        return Level.FINE;
    }

    @Override
    protected Level getErrorLoggingLevel() {
        return Level.INFO;
    }

    private class JsonHandler extends AsyncCompletionHandlerBase {

        static final String GITPUBSUB_URL = "http://gitpubsub-wip.apache.org:2069/json/*";
        private ObjectMapper mapper = new ObjectMapper();

        private long recycleAt = System.nanoTime() + TimeUnit.MINUTES.toNanos(requestRecycleMins);

        @Override
        public Response onCompleted(Response response) throws Exception {
            LOGGER.log(Level.FINE, "Connection closed");
            return super.onCompleted(response);
        }

        @Override
        public void onThrowable(Throwable t) {
            if (t instanceof TimeoutException) {
                LOGGER.log(Level.FINE, "Connection timeout", t);
            } else {
                LOGGER.log(Level.WARNING, "Unexpected exception", t);
            }
        }

        @Override
        public STATE onBodyPartReceived(HttpResponseBodyPart content) throws Exception {
            lastTime = System.currentTimeMillis();
            JsonNode json = mapper.readTree(content.getBodyPartBytes());
            if (LOGGER.isLoggable(Level.FINE)) {
                LOGGER.log(Level.FINE, "GitPubSub event {0}",
                        mapper.writerWithDefaultPrettyPrinter().writeValueAsString(json));
            }
            for (Iterator<Map.Entry<String, JsonNode>> it = json.fields(); it.hasNext(); ) {
                Map.Entry<String, JsonNode> field = it.next();
                String fieldName = field.getKey();
                final JsonNode fieldValue = field.getValue();
                try {
                    if ("stillalive".equals(fieldName)) {
                        lastTS = fieldValue.asLong();
                    } else if ("commit".equals(fieldName)) { // TODO delete once GitPubSub supports "push" events
                        if ("git".equals(fieldValue.get("repository").textValue())
                                && fieldValue.has("project")
                                && fieldValue.get("ref").asText().startsWith(Constants.R_HEADS)
                                && fieldValue.has("sha")) {
                            SCMHeadEvent.fireNow(new Commit(UPDATED, fieldValue, GITPUBSUB_URL));
                        }
                    } else if ("push".equals(fieldName)) {
                        if ("git".equals(fieldValue.get("repository").textValue())
                                && fieldValue.has("project")
                                && !"tag".equals(fieldValue.get("type").textValue())
                                && fieldValue.get("ref").asText().startsWith(Constants.R_HEADS)) {
                            SCMEvent.Type type;
                            String typeStr = fieldValue.get("action").textValue();
                            if ("created".equals(typeStr)) {
                                type = CREATED;
                            } else if ("updated".equals(typeStr)) {
                                type = UPDATED;
                            } else if ("deleted".equals(typeStr)) {
                                type = REMOVED;
                            } else {
                                // unknown, so ignore
                                continue;
                            }
                            SCMHeadEvent.fireNow(new Push(type, fieldValue, GITPUBSUB_URL));
                        }
                    }
                } catch (Exception e) {
                    LOGGER.log(Level.WARNING, "Uncaught exception", e);
                }
            }
            return recycleAt - System.nanoTime() > 0 ? STATE.CONTINUE : STATE.ABORT;
        }

    }

    // TODO delete once GitPubSub supports "push" events
    private class Commit extends SCMHeadEvent<JsonNode> {
        private final URIish remoteUri;

        public Commit(Type type, JsonNode payload, String origin) {
            super(type, payload, origin);
            // pre-parse the remote uri
            URIish event;
            try {
                event = new URIish(
                        "https://"
                                + payload.get("server").asText() +
                                ".apache.org/repos/asf/"
                                + Util.rawEncode(payload.get("project").asText())
                                + ".git"
                );
            } catch (URISyntaxException e) {
                event = null;
            }
            this.remoteUri = event;
        }

        @Override
        public boolean isMatch(@NonNull SCMNavigator navigator) {
            return false;
        }

        @NonNull
        @Override
        public String getSourceName() {
            return getPayload().get("project").asText();
        }

        @NonNull
        @Override
        public Map<SCMHead, SCMRevision> heads(@NonNull SCMSource source) {
            if (source instanceof GitSCMSource) {
                GitSCMSource git = (GitSCMSource) source;
                if (git.isIgnoreOnPushNotifications() || remoteUri == null) {
                    return Collections.emptyMap();
                }
                URIish remoteUri;
                try {
                    remoteUri = new URIish(git.getRemote());
                } catch (URISyntaxException e) {
                    return Collections.emptyMap();
                }

                if (GitStatus.looselyMatches(this.remoteUri, remoteUri)) {
                    String ref = getPayload().get("ref").asText();
                    SCMHead head = new SCMHead(ref.substring(Constants.R_HEADS.length()));
                    String sha1 = getPayload().get("sha").asText();
                    return Collections.<SCMHead, SCMRevision>singletonMap(head,
                            StringUtils.isBlank(sha1) ? new AbstractGitSCMSource.SCMRevisionImpl(
                                    head, sha1) : null);
                }
            }
            return Collections.emptyMap();
        }

        @Override
        public boolean isMatch(@NonNull SCM scm) {
            if (scm instanceof GitSCM) {
                GitSCM git = (GitSCM) scm;
                if (git.getExtensions().get(IgnoreNotifyCommit.class) != null) {
                    return false;
                }
                for (RemoteConfig repository : git.getRepositories()) {
                    for (URIish remoteUri : repository.getURIs()) {
                        if (GitStatus.looselyMatches(this.remoteUri, remoteUri)) {
                            return true;
                        }
                    }
                }
            }
            return false;
        }
    }

    private class Push extends SCMHeadEvent<JsonNode> {
        private final URIish remoteUri;

        public Push(Type type, JsonNode payload, String origin) {
            super(type, payload, origin);
            // pre-parse the remote uri
            URIish event;
            try {
                event = new URIish(
                        "https://"
                                + payload.get("server").asText() +
                                ".apache.org/repos/asf/"
                                + Util.rawEncode(payload.get("project").asText())
                                + ".git"
                );
            } catch (URISyntaxException e) {
                event = null;
            }
            this.remoteUri = event;
        }

        @Override
        public boolean isMatch(@NonNull SCMNavigator navigator) {
            return false;
        }

        @NonNull
        @Override
        public String getSourceName() {
            return getPayload().get("project").asText();
        }

        @NonNull
        @Override
        public Map<SCMHead, SCMRevision> heads(@NonNull SCMSource source) {
            if (source instanceof GitSCMSource) {
                GitSCMSource git = (GitSCMSource) source;
                if (git.isIgnoreOnPushNotifications() || remoteUri == null) {
                    return Collections.emptyMap();
                }
                URIish remoteUri;
                try {
                    remoteUri = new URIish(git.getRemote());
                } catch (URISyntaxException e) {
                    return Collections.emptyMap();
                }

                if (GitStatus.looselyMatches(this.remoteUri, remoteUri)) {
                    String ref = getPayload().get("ref").asText();
                    SCMHead head = new SCMHead(ref.substring(Constants.R_HEADS.length()));
                    String sha1 = getPayload().get("to").textValue();
                    return Collections.<SCMHead, SCMRevision>singletonMap(
                            head,
                            sha1 != null
                                    ? new AbstractGitSCMSource.SCMRevisionImpl(head, sha1)
                                    : null
                    );
                }
            }
            return Collections.emptyMap();
        }

        @Override
        public boolean isMatch(@NonNull SCM scm) {
            if (scm instanceof GitSCM) {
                GitSCM git = (GitSCM) scm;
                if (git.getExtensions().get(IgnoreNotifyCommit.class) != null) {
                    return false;
                }
                for (RemoteConfig repository : git.getRepositories()) {
                    for (URIish remoteUri : repository.getURIs()) {
                        if (GitStatus.looselyMatches(this.remoteUri, remoteUri)) {
                            return true;
                        }
                    }
                }
            }
            return false;
        }
    }
}
