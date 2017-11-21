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

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Util;
import hudson.plugins.git.browser.GitWeb;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import jenkins.plugins.git.GitSCMSource;
import jenkins.plugins.git.traits.GitBrowserSCMSourceTrait;
import jenkins.scm.api.trait.SCMSourceBuilder;
import jenkins.scm.api.trait.SCMSourceTrait;

public class ASFGitSCMSourceBuilder extends SCMSourceBuilder<ASFGitSCMSourceBuilder, GitSCMSource> {

    private final String id;

    private final String server;
    public ASFGitSCMSourceBuilder(String id, String server, String projectName) {
        super(GitSCMSource.class, projectName);
        this.id = id;
        this.server = server;
    }

    public String id() {
        return id;
    }

    public String server() {
        return server;
    }

    @NonNull
    @Override
    public GitSCMSource build() {
        GitSCMSource source = new GitSCMSource(server()+"/"+ Util.rawEncode(projectName())+".git");
        source.withId(id());
        List<SCMSourceTrait> traits = new ArrayList<>(traits());
        try {
            traits.add(new GitBrowserSCMSourceTrait(new GitWeb(server+"?p="+ URLEncoder.encode(projectName()+".git", "UTF-8"))));
        } catch (UnsupportedEncodingException e) {
            throw new AssertionError("JLS mandates UTF-8 as supported encoding");
        }
        source.setTraits(traits);
        return source;
    }
}
