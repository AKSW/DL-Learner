/**
 * Copyright (C) 2007-2011, Jens Lehmann
 *
 * This file is part of DL-Learner.
 *
 * DL-Learner is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 3 of the License, or
 * (at your option) any later version.
 *
 * DL-Learner is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.dllearner.algorithms.el;

import java.io.File;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.TreeSet;

import org.apache.log4j.Logger;
import org.dllearner.core.AbstractCELA;
import org.dllearner.core.AbstractLearningProblem;
import org.dllearner.core.AbstractReasonerComponent;
import org.dllearner.core.ComponentAnn;
import org.dllearner.core.ComponentInitException;
import org.dllearner.core.EvaluatedDescription;
import org.dllearner.core.config.BooleanEditor;
import org.dllearner.core.config.ConfigOption;
import org.dllearner.core.owl.Description;
import org.dllearner.core.owl.ObjectProperty;
import org.dllearner.core.owl.ObjectSomeRestriction;
import org.dllearner.core.owl.Thing;
import org.dllearner.learningproblems.EvaluatedDescriptionPosNeg;
import org.dllearner.learningproblems.PosNegLP;
import org.dllearner.learningproblems.ScorePosNeg;
import org.dllearner.refinementoperators.ELDown2;
import org.dllearner.utilities.Files;
import org.dllearner.utilities.Helper;

/**
 * A learning algorithm for EL, which is based on an
 * ideal refinement operator.
 * 
 * TODO redundancy check
 * 
 * @author Jens Lehmann
 *
 */
@ComponentAnn(name="ELTL", shortName="eltl", version=0.5, description="ELTL is an algorithm based on the refinement operator in http://jens-lehmann.org/files/2009/el_ilp.pdf.")
public class ELLearningAlgorithm extends AbstractCELA {

	private static Logger logger = Logger.getLogger(ELLearningAlgorithm.class);	
//	private ELLearningAlgorithmConfigurator configurator;
	
	private ELDown2 operator;
	
	private boolean isRunning = false;
	private boolean stop = false;
	
	private double treeSearchTimeSeconds = 10.0;
	private long treeStartTime;
	// "instanceBasedDisjoints", "Specifies whether to use real disjointness checks or instance based ones (no common instances) in the refinement operator."
	
	@ConfigOption(name="instanceBasedDisjoints", required=false, defaultValue="true", description="Specifies whether to use real disjointness checks or instance based ones (no common instances) in the refinement operator.", propertyEditorClass=BooleanEditor.class)
	private boolean instanceBasedDisjoints = true;
	
	@ConfigOption(name = "stopOnFirstDefinition", defaultValue="false", description="algorithm will terminate immediately when a correct definition is found")
	private boolean stopOnFirstDefinition = false;
	
	@ConfigOption(name = "noisePercentage", defaultValue="0.0", description="the (approximated) percentage of noise within the examples")
	private double noisePercentage = 0.0;

	@ConfigOption(name = "writeSearchTree", defaultValue="false", description="specifies whether to write a search tree")
	private boolean writeSearchTree = false;

	@ConfigOption(name = "searchTreeFile", defaultValue="log/searchTree.txt", description="file to use for the search tree")
	private String searchTreeFile = "log/searchTree.txt";

	@ConfigOption(name = "replaceSearchTree", defaultValue="false", description="specifies whether to replace the search tree in the log file after each run or append the new search tree")
	private boolean replaceSearchTree = false;
	
	private double noise;
	
	// a set with limited size (currently the ordering is defined in the class itself)
	private SearchTreeNode startNode;
	private ELHeuristic heuristic;
	private TreeSet<SearchTreeNode> candidates;
	
	public ELLearningAlgorithm() {
		
	}
	
	public ELLearningAlgorithm(AbstractLearningProblem problem, AbstractReasonerComponent reasoner) {
		super(problem, reasoner);
//		configurator = new ELLearningAlgorithmConfigurator(this);
	}
	
	public static String getName() {
		return "standard EL learning algorithm";
	}	
	
	public static Collection<Class<? extends AbstractLearningProblem>> supportedLearningProblems() {
		Collection<Class<? extends AbstractLearningProblem>> problems = new LinkedList<Class<? extends AbstractLearningProblem>>();
		problems.add(PosNegLP.class);
		return problems;
	}
	

//	@Override
//	public ELLearningAlgorithmConfigurator getConfigurator() {
//		return configurator;
//	}	
	
//	public static Collection<ConfigOption<?>> createConfigOptions() {
//		Collection<ConfigOption<?>> options = new LinkedList<ConfigOption<?>>();
////		options.add(CommonConfigOptions.getNoisePercentage());
////		options.add(new StringConfigOption("startClass", "the named class which should be used to start the algorithm (GUI: needs a widget for selecting a class)"));
//		options.add(CommonConfigOptions.getInstanceBasedDisjoints());
//		return options;
//	}		
	
	@Override
	public void init() throws ComponentInitException {
		// currently we use the stable heuristic
		heuristic = new StableHeuristic();
		candidates = new TreeSet<SearchTreeNode>(heuristic);
		
		operator = new ELDown2(reasoner, instanceBasedDisjoints);
		
		noise = noisePercentage/100d;
		
		if(writeSearchTree) {
			File f = new File(searchTreeFile );
			if(f.getParentFile() != null){
				f.getParentFile().mkdirs();
			}
			Files.clearFile(f);
		}
	}	
	
	@Override
	public void start() {
		stop = false;
		isRunning = true;
		reset();
		treeStartTime = System.nanoTime();
		
		// create start node
		ELDescriptionTree top = new ELDescriptionTree(reasoner, Thing.instance);
		addDescriptionTree(top, null);
		
		// main loop
		int loop = 0;
		while(!stop && !stoppingCriteriaSatisfied()) {
			// pick the best candidate according to the heuristic
			SearchTreeNode best = candidates.pollLast();
			// apply operator
			List<ELDescriptionTree> refinements = operator.refine(best.getDescriptionTree());
			// add all refinements to search tree, candidates, best descriptions
			for(ELDescriptionTree refinement : refinements) {
//				System.out.println("refinement: " + refinement);
				addDescriptionTree(refinement, best);
			}
			loop++;
			// logging
			if(logger.isTraceEnabled()) {
				logger.trace("Choosen node " + best);
				logger.trace(startNode.getTreeString());
				logger.trace("Loop " + loop + " completed.");
			}
			
			// writing the search tree (if configured)
			if (writeSearchTree) {
				String treeString = "best node: " + bestEvaluatedDescriptions.getBest() + "\n";
				if (refinements.size() > 1) {
				    treeString += "all expanded nodes:\n";
				    for (ELDescriptionTree elDescTree : refinements) {
                        treeString += "   " + elDescTree.toDescriptionString() + "\n";
                    }
				}
				treeString += startNode.getTreeString();
				treeString += "\n";

				if (replaceSearchTree)
					Files.createFile(new File(searchTreeFile), treeString);
				else
					Files.appendToFile(new File(searchTreeFile), treeString);
			}
		}
		
		// print solution(s)
		logger.info("solutions[time: " + Helper.prettyPrintNanoSeconds(System.nanoTime()-treeStartTime) + "]\n" + getSolutionString());
		
		isRunning = false;
	}

	// evaluates a description in tree form
	private void addDescriptionTree(ELDescriptionTree descriptionTree, SearchTreeNode parentNode) {
		// create search tree node
		SearchTreeNode node = new SearchTreeNode(descriptionTree);
		
		// convert tree to standard description
		Description description = descriptionTree.transformToDescription();
		description = getNiceDescription(description);
		
		double accuracy = getLearningProblem().getAccuracyOrTooWeak(description, noise);
		int negCovers = ((PosNegLP)getLearningProblem()).coveredNegativeExamplesOrTooWeak(description);
//		if(negCovers == -1) {
		if(accuracy == -1) {
			node.setTooWeak();
		} else {
			node.setCoveredNegatives(negCovers);
		}
//		System.out.println(descriptionTree.transformToDescription() + "|" + negCovers);
		// link to parent (unless start node)
		if(parentNode == null) {
			startNode = node;
		} else {
			parentNode.addChild(node);
		}
		
//		System.out.println("TEST");
		
		if(!node.isTooWeak()) {
			// add as candidate
			candidates.add(node);
		
//			System.out.println("TEST2");
			
			// check whether we want to add it to the best evaluated descriptions;
			// to do this we pick the worst considered evaluated description
			// (remember that the set has limited size, so it's likely not the worst overall);
			// the description has a chance to make it in the set if it has
			// at least as high accuracy - if not we can save the reasoner calls
			// for fully computing the evaluated description
			if(bestEvaluatedDescriptions.size() == 0 || ((EvaluatedDescriptionPosNeg)bestEvaluatedDescriptions.getWorst()).getCoveredNegatives().size() >= node.getCoveredNegatives()) {
				ScorePosNeg score = (ScorePosNeg) learningProblem.computeScore(description);
				EvaluatedDescriptionPosNeg ed = new EvaluatedDescriptionPosNeg(description, score);
				bestEvaluatedDescriptions.add(ed);
			}
			
		}
		
	}
	
	private Description getNiceDescription(Description d){
		Description description = d.clone();
		List<Description> children = description.getChildren();
		for(int i=0; i<children.size(); i++) {
			description.replaceChild(i, getNiceDescription(children.get(i)));
		}
		if(children.size()==0) {
			return description;
		} else if(description instanceof ObjectSomeRestriction) {
			// \exists r.\bot \equiv \bot
			if(description.getChild(0) instanceof Thing) {
				Description range = reasoner.getRange((ObjectProperty) ((ObjectSomeRestriction) description).getRole());
				description.replaceChild(0, range);
			} else {
				description.replaceChild(0, getNiceDescription(description.getChild(0)));
			}
		}
		return description;
	}
	
	private boolean stoppingCriteriaSatisfied() {
		// in some cases, there could be no candidate left ...
		if(candidates.isEmpty()) {
//			System.out.println("EMPTY");
			return true;
		}
		
		// stop when max time is reached
		long runTime = System.nanoTime() - treeStartTime;
		double runTimeSeconds = runTime / (double) 1000000000;
		
		if(runTimeSeconds >= treeSearchTimeSeconds) {
			return true;
		}
		
		// stop if we have a node covering all positives and none of the negatives
		SearchTreeNode bestNode = candidates.last();
		return stopOnFirstDefinition && (bestNode.getCoveredNegatives() == 0);
	}
	
	private void reset() {
		// set all values back to their default values (used for running
		// the algorithm more than once)
		candidates.clear();
		bestEvaluatedDescriptions.getSet().clear();
	}
	
	@Override
	public void stop() {
		stop = true;
	}
	
	@Override
	public boolean isRunning() {
		return isRunning;
	}	
	
	@Override
	public Description getCurrentlyBestDescription() {
		return getCurrentlyBestEvaluatedDescription().getDescription();
	}

	@Override
	public List<Description> getCurrentlyBestDescriptions() {
		return bestEvaluatedDescriptions.toDescriptionList();
	}
	
	@Override
	public EvaluatedDescription getCurrentlyBestEvaluatedDescription() {
		return bestEvaluatedDescriptions.getSet().last();
	}	
	
	public boolean isInstanceBasedDisjoints() {
		return instanceBasedDisjoints;
	}

	public void setInstanceBasedDisjoints(boolean instanceBasedDisjoints) {
		this.instanceBasedDisjoints = instanceBasedDisjoints;
	}

	@Override
	public TreeSet<? extends EvaluatedDescription> getCurrentlyBestEvaluatedDescriptions() {
		return bestEvaluatedDescriptions.getSet();
	}		
	
	
	/**
	 * @param stopOnFirstDefinition the stopOnFirstDefinition to set
	 */
	public void setStopOnFirstDefinition(boolean stopOnFirstDefinition) {
		this.stopOnFirstDefinition = stopOnFirstDefinition;
	}
	
	/**
	 * @return the stopOnFirstDefinition
	 */
	public boolean isStopOnFirstDefinition() {
		return stopOnFirstDefinition;
	}
	
	/**
	 * @return the startNode
	 */
	public SearchTreeNode getStartNode() {
		return startNode;
	}	
	
	/**
	 * @return the noisePercentage
	 */
	public double getNoisePercentage() {
		return noisePercentage;
	}
	
	/**
	 * @param noisePercentage the noisePercentage to set
	 */
	public void setNoisePercentage(double noisePercentage) {
		this.noisePercentage = noisePercentage;
	}

	public boolean isWriteSearchTree() {
		return writeSearchTree;
	}

	public void setWriteSearchTree(boolean writeSearchTree) {
		this.writeSearchTree = writeSearchTree;
	}

	public String getSearchTreeFile() {
		return searchTreeFile;
	}

	public void setSearchTreeFile(String searchTreeFile) {
		this.searchTreeFile = searchTreeFile;
	}
	
	public boolean isReplaceSearchTree() {
		return replaceSearchTree;
	}

	public void setReplaceSearchTree(boolean replaceSearchTree) {
		this.replaceSearchTree = replaceSearchTree;
	}	
	
}
