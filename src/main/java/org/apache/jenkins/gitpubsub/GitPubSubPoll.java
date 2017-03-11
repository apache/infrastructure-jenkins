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
import com.ning.http.client.AsyncHttpClient;
import com.ning.http.client.HttpResponseBodyPart;
import com.ning.http.client.Response;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.Util;
import hudson.model.AsyncPeriodicWork;
import hudson.model.TaskListener;
import hudson.plugins.git.GitStatus;
import hudson.scm.SCM;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Collections;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
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
import org.eclipse.jgit.transport.URIish;

@Extension
public class GitPubSubPoll extends AsyncPeriodicWork {
    private static final Logger LOGGER = Logger.getLogger(GitPubSubPoll.class.getName());
    private static long periodSeconds = Long.getLong(GitPubSubPoll.class.getName() + ".periodSeconds", 10);

    private volatile long lastTS;
    private volatile long lastTime;
    private Future<?> longPollRequest;

    public GitPubSubPoll() {
        super("ASG GitPubSub poll");
    }

    @Override
    protected void execute(TaskListener listener) throws IOException, InterruptedException {
        if (longPollRequest != null) {
            if (lastTime - System.currentTimeMillis() > TimeUnit.SECONDS.toMillis(60)) {
                longPollRequest.cancel(false);
            }
            if (!longPollRequest.isDone()) {
                // still alive
                return;
            }
        }
        AsyncHttpClient.BoundRequestBuilder builder1 =
                AHC.instance().prepareGet("http://gitpubsub-wip.apache.org:2069/json/*");
        if (lastTS != 0) {
            builder1.addHeader("X-Fetch-Since", Long.toString(lastTS));
        }
        longPollRequest = AHC.instance().executeRequest(builder1.build(), new JsonHandler());
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

        private ObjectMapper mapper = new ObjectMapper();

        @Override
        public Response onCompleted(Response response) throws Exception {
            LOGGER.log(Level.FINE, "Received GitPubSub Closed");
            return super.onCompleted(response);
        }

        @Override
        public STATE onBodyPartReceived(HttpResponseBodyPart content) throws Exception {
            JsonNode json = mapper.readTree(content.getBodyPartBytes());
            LOGGER.log(Level.FINE, "Received GitPubSub event {0}",
                    mapper.writerWithDefaultPrettyPrinter().writeValueAsString(json));
            for (Iterator<Map.Entry<String, JsonNode>> it = json.fields(); it.hasNext(); ) {
                Map.Entry<String, JsonNode> field = it.next();
                String fieldName = field.getKey();
                final JsonNode fieldValue = field.getValue();
                try {
                    if ("stillalive".equals(fieldName)) {
                        lastTS = fieldValue.asLong();
                        lastTime = System.currentTimeMillis();
                    } else if ("commit".equals(fieldName)) {
                        if ("git".equals(fieldValue.get("repository").textValue())
                                && fieldValue.has("project")
                                && fieldValue.get("ref").asText().startsWith("refs/heads/")) {
                            SCMHeadEvent.fireNow(
                                    new SCMHeadEvent<JsonNode>(SCMEvent.Type.UPDATED, fieldValue, "GitPubSub") {
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
                                                GitSCMSource src = (GitSCMSource) source;
                                                URIish remote;
                                                try {
                                                    remote = new URIish(src.getRemote());
                                                } catch (URISyntaxException e) {
                                                    return Collections.emptyMap();
                                                }
                                                URIish event;
                                                try {
                                                    event = new URIish(
                                                            "https://"
                                                                    + getPayload().get("server").asText() +
                                                                    ".apache.org/repos/asf/"
                                                                    + Util
                                                                    .rawEncode(getPayload().get("project").asText())
                                                                    + ".git"
                                                    );
                                                } catch (URISyntaxException e) {
                                                    return Collections.emptyMap();
                                                }

                                                if (GitStatus.looselyMatches(event, remote)) {
                                                    String ref = getPayload().get("ref").asText();
                                                    SCMHead head = new SCMHead(ref.substring("refs/heads/".length()));
                                                    String sha1 = getPayload().get("sha").asText();
                                                    return Collections.<SCMHead, SCMRevision>singletonMap(head,
                                                            sha1 != null ? new AbstractGitSCMSource.SCMRevisionImpl(
                                                                    head, sha1) : null);
                                                }
                                            }
                                            return Collections.emptyMap();
                                        }

                                        @Override
                                        public boolean isMatch(@NonNull SCM scm) {
                                            return false;
                                        }
                                    });
                        }
                    }
                } catch (Exception e) {
                    LOGGER.log(Level.WARNING, "Uncaught exception", e);
                }
            }
            return STATE.CONTINUE;
        }
    }
}
