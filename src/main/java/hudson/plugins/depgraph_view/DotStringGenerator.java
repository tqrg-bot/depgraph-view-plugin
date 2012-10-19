/*
 * The MIT License
 *
 * Copyright (c) 2011, Stefan Wolf
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package hudson.plugins.depgraph_view;

import com.google.common.base.Function;
import com.google.common.base.Joiner;
import hudson.model.AbstractProject;
import hudson.model.Hudson;
import hudson.plugins.depgraph_view.model.DependencyGraph;
import hudson.plugins.depgraph_view.model.Edge;
import hudson.plugins.depgraph_view.model.ProjectNode;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.google.common.base.Functions.compose;
import static com.google.common.collect.Lists.transform;

/**
 * @author wolfs
 */
public class DotStringGenerator extends AbstractGraphStringGenerator {
    private static final Function<String, String> ESCAPE = new Function<String, String>() {
        @Override
        public String apply(String from) {
            return escapeString(from);
        }
    };

    private String subProjectColor = "#F0F0F0";
    private String copyArtifactColor = "lightblue";

    public DotStringGenerator subProjectColor(String color) {
        subProjectColor = color;
        return this;
    }

    public DotStringGenerator copyArtifactColor(String color) {
        copyArtifactColor = color;
        return this;
    }

    public DotStringGenerator(DependencyGraph graph) {
        super(graph);
    }

    /**
     * Generates the graphviz code for the given projects and dependencies
     * @return graphviz code
     */
    public String generate() {
        /**** Build the dot source file ****/
        StringBuilder builder = new StringBuilder();

        builder.append("digraph {\n");
        builder.append("node [shape=box, style=rounded];\n");

        /**** First define all the objects and clusters ****/

        // up/downstream linked jobs
        builder.append(cluster("Main", projectsInDependenciesNodes(), "color=invis;"));

        // Stuff not linked to other stuff
        List<String> standaloneNames = transform(standaloneProjects, compose(ESCAPE, PROJECT_NAME_FUNCTION));
        builder.append(cluster("Standalone", standaloneProjectNodes(standaloneNames),"color=invis;"));


        /****Now define links between objects ****/

        // edges
        for (Edge edge : edges) {
            builder.append(dependencyToEdgeString(edge));
            builder.append(";\n");
        }

        if (!standaloneNames.isEmpty()) {
            builder.append("edge[style=\"invisible\",dir=\"none\"];\n" + Joiner.on(" -> ").join(standaloneNames) + ";\n");
            builder.append("edge[style=\"invisible\",dir=\"none\"];\n" + standaloneNames.get(standaloneNames.size() - 1) + " -> \"Dependency Graph\"");
        }


        builder.append("}");

        return builder.toString();
    }

    public String generateLegend() {
        /**** Build the dot source file ****/
        StringBuilder builder = new StringBuilder();

        builder.append("digraph {\n");
        builder.append("node [shape=box, style=rounded];\n");

        builder.append(cluster("Legend", legend()));

        builder.append("}");
        return builder.toString();
    }

    private String standaloneProjectNodes(List<String> standaloneNames) {
        StringBuilder builder = new StringBuilder();
        for (ProjectNode proj : standaloneProjects) {
            builder.append(projectToNodeString(proj, subJobs.get(proj)));
            builder.append(";\n");
        }
        if (!standaloneNames.isEmpty()) {
            builder.append("edge[style=\"invisible\",dir=\"none\"];\n" + Joiner.on(" -> ").join(standaloneNames) + ";\n");
        }
        return builder.toString();
    }

    private String projectsInDependenciesNodes() {
        StringBuilder stringBuilder = new StringBuilder();
        for (ProjectNode proj : projectsInDeps) {
            if (subJobs.containsKey(proj)) {
                stringBuilder.append(projectToNodeString(proj, subJobs.get(proj)));
            }
            else {
                stringBuilder.append(projectToNodeString(proj));
            }
            stringBuilder.append(";\n");
        }
        return stringBuilder.toString();
    }

    private String legend() {
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("label=\"Legend:\" labelloc=t centered=false color=black node [shape=plaintext]")
                .append("\"Dependency Graph\"\n")
                .append("\"Copy Artifact\"\n")
                .append("\"Sub-Project\"\n")
                .append("node [style=invis]\n")
                .append("a [label=\"\"] b [label=\"\"]")
                .append(" c [fillcolor=" + escapeString(subProjectColor) + " style=filled fontcolor="
                        + escapeString(subProjectColor) + "]\n")
                .append("a -> b [style=invis]\n")
                .append("{rank=same a -> \"Dependency Graph\" [color=black style=bold minlen=2]}\n")
                .append("{rank=same b -> \"Copy Artifact\" [color=lightblue minlen=2]}\n")
                .append("{rank=same c -> \"Sub-Project\" [ style=invis]}\n");
        return stringBuilder.toString();
    }

    private String cluster(String name, String contents, String... options) {
        StringBuilder builder = new StringBuilder();
        builder.append("subgraph cluster" + name + " {\n");
        builder.append(contents);
        builder.append(Joiner.on("\n").join(options) + "}\n");
        return builder.toString();
    }

    private Set<AbstractProject<?, ?>> listUniqueProjectsInDependencies(List<hudson.model.DependencyGraph.Dependency> dependencies)
    {
        Set<AbstractProject<?, ?>> set = new HashSet<AbstractProject<?, ?>>();
        for (hudson.model.DependencyGraph.Dependency dependency : dependencies)
        {
            set.add(dependency.getUpstreamProject());
            set.add(dependency.getDownstreamProject());
        }
        return set;
    }


    private String projectToNodeString(ProjectNode proj) {
        return escapeString(proj.getName()) +
                " [href=" +
                getEscapedProjectUrl(proj) + "]";
    }

    private String projectToNodeString(ProjectNode proj, List<ProjectNode> subprojects) {
        StringBuilder builder = new StringBuilder();
        builder.append(escapeString(proj.getName()))
                .append(" [shape=\"Mrecord\" href=")
                .append(getEscapedProjectUrl(proj))
                .append(" label=<<table border=\"0\" cellborder=\"0\" cellpadding=\"3\" bgcolor=\"white\">\n");
        builder.append(getProjectRow(proj));
        for (ProjectNode subproject : subprojects) {
            builder.append(getProjectRow(subproject, "bgcolor=" + escapeString(subProjectColor))).append("\n");
        }
        builder.append("</table>>]");
        return builder.toString();
    }

    private String getProjectRow(ProjectNode project, String... extraColumnProperties) {
        return String.format("<tr><td align=\"center\" href=%s %s>%s</td></tr>", getEscapedProjectUrl(project), Joiner.on(" ").join(extraColumnProperties),
                project.getName());
    }

    private String getEscapedProjectUrl(ProjectNode proj) {
        return escapeString(Hudson.getInstance().getRootUrl() + proj.getProject().getUrl());
    }

    private String dependencyToCopiedArtifactString(hudson.model.DependencyGraph.Dependency dep) {
        return dependencyToEdgeString(dep, "color=" + copyArtifactColor);
    }

    private String dependencyToEdgeString(hudson.model.DependencyGraph.Dependency dep, String... options) {
        return escapeString(dep.getUpstreamProject().getFullDisplayName()) + " -> " +
                escapeString(dep.getDownstreamProject().getFullDisplayName()) + " [ " + Joiner.on(" ").join(options) +" ] ";
    }

    private String dependencyToEdgeString(Edge edge, String... options) {
        return escapeString(edge.source.getName()) + " -> " +
                escapeString(edge.target.getName()) + " [ color=" + edge.getColor() + " " + Joiner.on(" ").join(options) +" ] ";
    }

    private static String escapeString(String toEscape) {
        return "\"" + toEscape + "\"";
    }
}