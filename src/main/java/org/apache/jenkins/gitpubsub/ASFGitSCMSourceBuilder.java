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
