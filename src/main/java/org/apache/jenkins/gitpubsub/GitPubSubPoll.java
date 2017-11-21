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

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ning.http.client.AsyncCompletionHandlerBase;
import com.ning.http.client.AsyncHttpClient;
import com.ning.http.client.AsyncHttpClientConfig;
import com.ning.http.client.HttpResponseBodyPart;
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
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.plugins.asynchttpclient.AHC;
import jenkins.plugins.asynchttpclient.AHCUtils;
import jenkins.plugins.git.AbstractGitSCMSource;
import jenkins.plugins.git.GitSCMSource;
import jenkins.plugins.git.traits.IgnoreOnPushNotificationTrait;
import jenkins.scm.api.SCMEvent;
import jenkins.scm.api.SCMHead;
import jenkins.scm.api.SCMHeadEvent;
import jenkins.scm.api.SCMNavigator;
import jenkins.scm.api.SCMRevision;
import jenkins.scm.api.SCMSource;
import jenkins.scm.api.trait.SCMTrait;
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
     * How long to keep the request open before closing and re-opening or {@code -1} to try to keep the connection
     * open permanently.
     */
    private static int requestRecycleMins =
            Integer.getInteger(GitPubSubPoll.class.getName() + ".requestRecycleMins", -1);
    /**
     * Kill switch to disable notification of {@link GitSCM}.
     */
    private static boolean disableNotifyScm =
            Boolean.getBoolean(GitPubSubPoll.class.getName() + ".disableNotifyScm");

    /**
     * The last timestamp received.
     */
    private volatile long lastTS;
    private long lastTSStuck;
    /**
     * The last time we received anything.
     */
    private volatile long lastTime;
    private final AtomicInteger pushEvents = new AtomicInteger();
    private final AtomicInteger aliveEvents = new AtomicInteger();
    private final AtomicInteger allEvents = new AtomicInteger();
    private long lastReport;
    private AsyncHttpClient client;
    /**
     * The current request.
     */
    private Future<?> longPollRequest;

    public GitPubSubPoll() {
        super("ASF GitPubSub poll");
    }

    @Override
    protected void execute(TaskListener listener) throws IOException, InterruptedException {
        if (lastReport < System.currentTimeMillis()) {
            if (lastReport == 0) {
                lastReport = System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(15);
            } else {
                lastReport = lastReport + TimeUnit.MINUTES.toMillis(15);
                LOGGER.log(Level.INFO, "GitPubSub {0,number} events processed. "
                                + "stillalive: {1,number}; push: {2,number}",
                        new Object[]{allEvents.get(), aliveEvents.get(), pushEvents.get()});
            }
        }
        if (longPollRequest != null) {
            if (lastTime - System.currentTimeMillis() > TimeUnit.SECONDS.toMillis(60)) {
                LOGGER.log(Level.FINE, "GitPubSub request looks dead, restarting...");
                longPollRequest.cancel(false);
            } else if (!longPollRequest.isDone() && !longPollRequest.isCancelled()) {
                LOGGER.log(Level.FINER, "GitPubSub request looks alive");
                // still alive
                return;
            }
            LOGGER.log(Level.FINE, "GitPubSub request completed, restarting...");
            if (client != null && !client.isClosed()) {
                LOGGER.log(Level.FINE, "Stopping AsyncHttpClient instance");
                client.close();
            }
        } else {
            LOGGER.log(Level.INFO, "Starting GitPubSub request...");
        }
        RequestBuilder builder = new RequestBuilder("GET")
                .setUrl(JsonHandler.GITPUBSUB_URL)
                .setRequestTimeout(requestRecycleMins == -1
                        ? -1
                        : (requestRecycleMins + 1) * 60 * 1000
                );
        if (lastTS != 0) {
            builder.addHeader("X-Fetch-Since", Long.toString(lastTS));
        }
        if (client == null || client.isClosed()) {
            LOGGER.log(Level.FINE, "Starting AsyncHttpClient instance");
            client = new AsyncHttpClient(
                    new AsyncHttpClientConfig.Builder()
                            .setAllowPoolingConnections(false)
                            .setRequestTimeout(
                                    requestRecycleMins == -1
                                    ? -1
                                    : (requestRecycleMins + 1) * 60 * 1000)
                            .setProxyServer(AHCUtils.getProxyServer())
                            .setHostnameVerifier(AHCUtils.getHostnameVerifier()).setSSLContext(AHCUtils.getSSLContext())
                            .build());
        }
        longPollRequest = client.executeRequest(builder.build(), new JsonHandler());
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

        private byte[] partialBody = null;

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
                long _lastTS = lastTS;
                if (_lastTS != 0 && _lastTS == lastTSStuck) {
                    LOGGER.log(Level.WARNING, "Unsticking X-Fetch-Since to hopefully bypass issue");
                    lastTS = 0;
                    lastTSStuck = 0;
                } else {
                    // track the last TS, so that next time we bomb we can reset to skip past broken event
                    lastTSStuck = _lastTS;
                }
            }
        }

        @Override
        public synchronized STATE onBodyPartReceived(HttpResponseBodyPart content) throws Exception {
            lastTime = System.currentTimeMillis();
            byte[] body = content.getBodyPartBytes();
            if (body.length < 2 || body[body.length - 1] != 0x0a || body[body.length - 2] != 0x0d) {
                LOGGER.log(Level.FINE, "Stashing large message");
                if (partialBody == null) {
                    partialBody = body;
                } else {
                    int index = partialBody.length;
                    partialBody = Arrays.copyOf(partialBody, index + body.length);
                    System.arraycopy(body, 0, partialBody, index, body.length);
                }
                return STATE.CONTINUE;
            }
            if (partialBody != null) {
                LOGGER.log(Level.FINE, "Unstashing large message");
                int index = partialBody.length;
                partialBody = Arrays.copyOf(partialBody, index + body.length);
                System.arraycopy(body, 0, partialBody, index, body.length);
                body = partialBody;
                partialBody = null;
            }
            JsonNode json;
            try {
                json = mapper.readTree(body);
            } catch (JsonParseException e) {
                LOGGER.log(Level.INFO,
                        "Could not parse GitPubSub event: " + new String(body, StandardCharsets.UTF_8),
                        e
                );
                return STATE.CONTINUE;
            }
            if (LOGGER.isLoggable(Level.FINE)) {
                LOGGER.log(Level.FINE, "GitPubSub event {0}",
                        mapper.writerWithDefaultPrettyPrinter().writeValueAsString(json));
            }
            allEvents.incrementAndGet();
            for (Iterator<Map.Entry<String, JsonNode>> it = json.fields(); it.hasNext(); ) {
                Map.Entry<String, JsonNode> field = it.next();
                String fieldName = field.getKey();
                final JsonNode fieldValue = field.getValue();
                try {
                    if ("stillalive".equals(fieldName)) {
                        aliveEvents.incrementAndGet();
                        lastTS = fieldValue.asLong();
                    } else if ("push".equals(fieldName)) {
                        pushEvents.incrementAndGet();
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
            if (requestRecycleMins == -1 || recycleAt - System.nanoTime() > 0) {
                return STATE.CONTINUE;
            } else {
                LOGGER.log(Level.FINE, "Recycling...");
                return STATE.ABORT;
            }
        }

    }

    private static class Push extends SCMHeadEvent<JsonNode> {
        private final URIish remoteUri;
        private String server;

        public Push(Type type, JsonNode payload, String origin) {
            super(type, payload, origin);
            server = "https://"
                    + getPayload().get("server").asText() +
                    ".apache.org/repos/asf";
            // pre-parse the remote uri
            URIish event;
            try {
                event = new URIish(
                        server + "/"
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
            return navigator instanceof ASFGitSCMNavigator
                    ? server.equals(((ASFGitSCMNavigator) navigator).getServer())
                    : false;
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
                if (SCMTrait.find(git.getTraits(), IgnoreOnPushNotificationTrait.class) != null || remoteUri == null) {
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
            if (scm instanceof GitSCM && !disableNotifyScm) {
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
