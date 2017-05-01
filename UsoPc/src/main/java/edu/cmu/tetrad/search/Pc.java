///////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
// Copyright (C) 1998, 1999, 2000, 2001, 2002, 2003, 2004, 2005, 2006,       //
// 2007, 2008, 2009, 2010, 2014, 2015 by Peter Spirtes, Richard Scheines, Joseph   //
// Ramsey, and Clark Glymour.                                                //
//                                                                           //
// This program is free software; you can redistribute it and/or modify      //
// it under the terms of the GNU General Public License as published by      //
// the Free Software Foundation; either version 2 of the License, or         //
// (at your option) any later version.                                       //
//                                                                           //
// This program is distributed in the hope that it will be useful,           //
// but WITHOUT ANY WARRANTY; without even the implied warranty of            //
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the             //
// GNU General Public License for more details.                              //
//                                                                           //
// You should have received a copy of the GNU General Public License         //
// along with this program; if not, write to the Free Software               //
// Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA //
///////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetrad.search;

import edu.cmu.tetrad.data.IKnowledge;
import edu.cmu.tetrad.data.Knowledge2;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.util.TetradLogger;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Implements the PC ("Peter/Clark") algorithm, as specified in Chapter 6 of Spirtes, Glymour, and Scheines, "Causation,
 * Prediction, and Search," 2nd edition, with a modified rule set in step D due to Chris Meek. For the modified rule
 * set, see Chris Meek (1995), "Causal inference and causal explanation with background knowledge."
 *
 * @author Joseph Ramsey.
 */
public class Pc implements GraphSearch {
//Atributos 
    private IndependenceTest independenceTest;

    private IKnowledge knowledge = new Knowledge2();

    private SepsetMap sepsets;

    private int depth = 1000;

    private Graph graph;

    private long elapsedTime;

    private boolean aggressivelyPreventCycles = false;

    private TetradLogger logger = TetradLogger.getInstance();

    private Set<Triple> unshieldedColliders;

    private Set<Triple> unshieldedNoncolliders;

    private int numIndependenceTests;

    private Graph trueGraph;

    private int numFalseDependenceJudgements;

    private int numDependenceJudgements;

    private Graph initialGraph = null;

    private boolean verbose = false;

    private boolean fdr = false;
//Constructor de PC
    public Pc(IndependenceTest independenceTest) {
        if (independenceTest == null) {
            throw new NullPointerException();
        }

        this.independenceTest = independenceTest;
    }
//Metodos Publicos
    public boolean isAggressivelyPreventCycles() {
        return this.aggressivelyPreventCycles;
    }

    public void setAggressivelyPreventCycles(boolean aggressivelyPreventCycles) {
        this.aggressivelyPreventCycles = aggressivelyPreventCycles;
    }

    public IndependenceTest getIndependenceTest() {
        return independenceTest;
    }

    public IKnowledge getKnowledge() {
        return knowledge;
    }

    public void setKnowledge(IKnowledge knowledge) {
        if (knowledge == null) {
            throw new NullPointerException();
        }

        this.knowledge = knowledge;
    }

    public SepsetMap getSepsets() {
        return this.sepsets;
    }

    public int getDepth() {
        return depth;
    }

    public void setDepth(int depth) {
        if (depth < -1) {
            throw new IllegalArgumentException("Depth must be -1 or >= 0: " + depth);
        }

        if (depth > 1000) {
            throw new IllegalArgumentException("Depth must be <= 1000.");
        }

        this.depth = depth;
    }

    @Override
    public Graph search() {
        return search(independenceTest.getVariables());
    }

    public Graph search(List<Node> nodes) {
        IFas fas = null;

        if (initialGraph == null) {
            fas = new Fas(getIndependenceTest());
        } else {
            fas = new Fas(initialGraph, getIndependenceTest());
        }
        fas.setVerbose(verbose);
        return search(fas, nodes);
    }

    public Graph search(IFas fas, List<Node> nodes) {
        this.logger.log("info", "Starting PC algorithm");
        this.logger.log("info", "Independence test = " + getIndependenceTest() + ".");

        long startTime = System.currentTimeMillis();

        if (getIndependenceTest() == null) {
            throw new NullPointerException();
        }

        List<Node> allNodes = getIndependenceTest().getVariables();
        if (!allNodes.containsAll(nodes)) {
            throw new IllegalArgumentException("All of the given nodes must " +
                    "be in the domain of the independence test provided.");
        }

        fas.setKnowledge(getKnowledge());
        fas.setDepth(getDepth());
        fas.setVerbose(verbose);

        graph = fas.search();
        sepsets = fas.getSepsets();

        this.numIndependenceTests = fas.getNumIndependenceTests();
        this.numFalseDependenceJudgements = fas.getNumFalseDependenceJudgments();
        this.numDependenceJudgements = fas.getNumDependenceJudgments();

        SearchGraphUtils.pcOrientbk(knowledge, graph, nodes);
        SearchGraphUtils.orientCollidersUsingSepsets(this.sepsets, knowledge, graph, verbose);

        MeekRules rules = new MeekRules();
        rules.setAggressivelyPreventCycles(this.aggressivelyPreventCycles);
        rules.setKnowledge(knowledge);
        rules.setUndirectUnforcedEdges(false);
        rules.orientImplied(graph);

        this.logger.log("graph", "\nReturning this graph: " + graph);

        this.elapsedTime = System.currentTimeMillis() - startTime;

        this.logger.log("info", "Elapsed time = " + (elapsedTime) / 1000. + " s");
        this.logger.log("info", "Finishing PC Algorithm.");
        this.logger.flush();

        return graph;
    }

    public long getElapsedTime() {
        return elapsedTime;
    }

    public Set<Triple> getUnshieldedColliders() {
        return unshieldedColliders;
    }

    public Set<Triple> getUnshieldedNoncolliders() {
        return unshieldedNoncolliders;
    }

    public Set<Edge> getAdjacencies() {
        Set<Edge> adjacencies = new HashSet<>();
        for (Edge edge : graph.getEdges()) {
            adjacencies.add(edge);
        }
        return adjacencies;
    }

    public Set<Edge> getNonadjacencies() {
        Graph complete = GraphUtils.completeGraph(graph);
        Set<Edge> nonAdjacencies = complete.getEdges();
        Graph undirected = GraphUtils.undirectedGraph(graph);
        nonAdjacencies.removeAll(undirected.getEdges());
        return new HashSet<>(nonAdjacencies);
    }

    public int getNumIndependenceTests() {
        return numIndependenceTests;
    }

    public void setTrueGraph(Graph trueGraph) {
        this.trueGraph = trueGraph;
    }

    public int getNumFalseDependenceJudgements() {
        return numFalseDependenceJudgements;
    }

    public int getNumDependenceJudgements() {
        return numDependenceJudgements;
    }

    public List<Node> getNodes() {
        return graph.getNodes();
    }

    public List<Triple> getColliders(Node node) {
        return null;
    }

    public List<Triple> getNoncolliders(Node node) {
        return null;
    }

    public List<Triple> getAmbiguousTriples(Node node) {
        return null; 
    }

    public List<Triple> getUnderlineTriples(Node node) {
        return null;
    }

    public List<Triple> getDottedUnderlineTriples(Node node) {
        return null;
    }

    public void setInitialGraph(Graph initialGraph) {
        this.initialGraph = initialGraph;
    }

    public boolean isVerbose() {
        return verbose;
    }

    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    public boolean isFdr() {
        return fdr;
    }

    public void setFdr(boolean fdr) {
        this.fdr = fdr;
    }
}




