/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package fi.otavanopisto.changelogger;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import org.apache.commons.io.Charsets;
import org.apache.commons.io.FileUtils;
import org.eclipse.egit.github.core.Issue;
import org.eclipse.egit.github.core.Label;
import org.eclipse.egit.github.core.client.EventFormatter;
import org.eclipse.egit.github.core.event.Event;
import org.eclipse.egit.github.core.event.EventPayload;
import org.eclipse.egit.github.core.event.PullRequestPayload;
import org.eclipse.egit.github.core.service.IssueService;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.ResetCommand;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import static spark.Spark.*;

/**
 *
 * @author Ilmo Euro <ilmo.euro@gmail.com>
 */
public class Changelogger {

    private static final Pattern ISSUE_REGEX = Pattern.compile("#(\\d+)");
    private static final String GITHUB_USER = "otavanopisto";
    private static final String GITHUB_REPO = "muikku";
    private static final String CHANGELOG_FILE = "Changelog.md";

    private static List<Integer> getIssueNumbers(String description) {
        List<Integer> issues = new ArrayList<>();
        Matcher matcher = ISSUE_REGEX.matcher(description);
        while (matcher.find()) {
            issues.add(Integer.parseInt(matcher.group()));
        }
        return issues;
    } 

    private static void
        prependLine(String line, String message)
    throws
        IOException, GitAPIException
    {
        FileRepositoryBuilder frBuilder = new FileRepositoryBuilder();
        Repository repository = frBuilder
                .setGitDir(new File("./.git"))
                .readEnvironment()
                .build();
        Git git = new Git(repository);

        git.reset()
           .setMode(ResetCommand.ResetType.HARD)
           .call();
        git.clean()
           .call();
        git.fetch()
           .call();
        git.pull()
           .call(); 

        File file = new File(CHANGELOG_FILE);
        List<String> lines = FileUtils.readLines(file, Charsets.UTF_8);
        lines.add(0, line);
        FileUtils.writeLines(file, lines);

        git.commit()
           .setMessage(message)
           .setAuthor("Changelogger", "changelogger@otavanopisto.fi")
           .call();

        git.push()
           .call();
    }

    public static void main(String... args) {
        Gson gson = new GsonBuilder()
                .registerTypeAdapter(Event.class, new EventFormatter())
                .create();
        
        IssueService issueService = new IssueService();

        post("/pullrequesthook", (req, res) -> {
            Event[] events = gson.fromJson(req.body(), Event[].class);
            for (Event event : events) {
                EventPayload payload = event.getPayload();
                if (payload instanceof PullRequestPayload) {
                    PullRequestPayload pr = (PullRequestPayload)payload;

                    if ("closed".equals(pr.getAction())) {
                        List<Integer> issues = getIssueNumbers(pr.getPullRequest().getBodyText());
                        for (int issueNumber : issues){
                            Issue issue = issueService.getIssue(GITHUB_USER, GITHUB_REPO, issueNumber);
                            if (issue != null) {
                                String prefix = "unknown";
                                for (Label issueLabel : issue.getLabels()) {
                                    String labelName = issueLabel.getName();
                                    if ("bug".equals(labelName) ||
                                        "enhancement".equals(labelName)) {
                                        prefix = labelName;
                                    }
                                }
                                String issueName = issue.getTitle();

                                prependLine(
                                    String.format(
                                        "**%s**#%d: %s",
                                        prefix,
                                        issueNumber,
                                        issueName),
                                    String.format("Added issue %d",
                                        issueNumber));
                            }
                        }
                    }
                }
            }
            return "";
        });

        post("/pushhook", (req, res) -> {
            return "";
        });
    }
    
}
