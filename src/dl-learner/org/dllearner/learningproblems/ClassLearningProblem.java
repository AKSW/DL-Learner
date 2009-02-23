/**
 * Copyright (C) 2007-2009, Jens Lehmann
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
 *
 */
package org.dllearner.learningproblems;

import java.util.Collection;
import java.util.LinkedList;
import java.util.Set;
import java.util.TreeSet;

import org.dllearner.algorithms.EvaluatedDescriptionClass;
import org.dllearner.core.ComponentInitException;
import org.dllearner.core.EvaluatedDescription;
import org.dllearner.core.LearningProblem;
import org.dllearner.core.ReasonerComponent;
import org.dllearner.core.configurators.ClassLearningProblemConfigurator;
import org.dllearner.core.options.ConfigOption;
import org.dllearner.core.options.StringConfigOption;
import org.dllearner.core.owl.Description;
import org.dllearner.core.owl.Individual;
import org.dllearner.core.owl.NamedClass;
import org.dllearner.utilities.Helper;

/**
 * The problem of learning the description of an existing class
 * in an OWL ontology.
 * 
 * @author Jens Lehmann
 *
 */
public class ClassLearningProblem extends LearningProblem {

	private NamedClass classToDescribe;
	private Set<Individual> classInstances;
	private boolean equivalence = true;
	private ClassLearningProblemConfigurator configurator;
	
	@Override
	public ClassLearningProblemConfigurator getConfigurator(){
		return configurator;
	}	
	
	public ClassLearningProblem(ReasonerComponent reasoner) {
		super(reasoner);
		configurator = new ClassLearningProblemConfigurator(this);
	}
	
	public static Collection<ConfigOption<?>> createConfigOptions() {
		Collection<ConfigOption<?>> options = new LinkedList<ConfigOption<?>>();
		options.add(new StringConfigOption("classToDescribe", "class of which a description should be learned", null, true, false));
		StringConfigOption type = new StringConfigOption("type", "Whether to learn an equivalence class or super class axiom.","equivalence");
		type.setAllowedValues(new String[] {"equivalence", "superClass"});
		options.add(type);		
		return options;
	}

	public static String getName() {
		return "class learning problem";
	}	
	
	@Override
	public void init() throws ComponentInitException {
		classToDescribe = new NamedClass(configurator.getClassToDescribe());
		if(!reasoner.getNamedClasses().contains(classToDescribe)) {
			throw new ComponentInitException("The class \"" + configurator.getClassToDescribe() + "\" does not exist. Make sure you spelled it correctly.");
		}
		
		classInstances = reasoner.getIndividuals(classToDescribe);
		equivalence = (configurator.getType().equals("equivalence"));
	}
	
	/**
	 * Computes the fraction of the instances of the class to learn, which 
	 * is covered by the given description.
	 * @param description The description for which to compute coverage.
	 * @return The class coverage (between 0 and 1).
	 */
//	public double getCoverage(Description description) {
//		int instancesCovered = 0;
//		for(Individual instance : classInstances) {
//			if(reasoner.hasType(description, instance)) {
//				instancesCovered++;
//			}
//		}
//		return instancesCovered/(double)classInstances.size();
//	}
	
	@Override
	public ClassScore computeScore(Description description) {
		Set<Individual> retrieval = reasoner.getIndividuals(description);
		
		Set<Individual> coveredInstances = new TreeSet<Individual>();
		
		int instancesCovered = 0;
		
		for(Individual ind : classInstances) {
			if(retrieval.contains(ind)) {
				instancesCovered++;
				coveredInstances.add(ind);
			}
		}
		
		Set<Individual> additionalInstances = Helper.difference(retrieval, coveredInstances);		
		
		double coverage = instancesCovered/(double)classInstances.size();
		double protusion = instancesCovered/(double)retrieval.size();
		
		return new ClassScore(coveredInstances, coverage, additionalInstances, protusion);
	}	
	
	public boolean isEquivalenceProblem() {
		return equivalence;
	}

	/* (non-Javadoc)
	 * @see org.dllearner.core.LearningProblem#getAccuracy(org.dllearner.core.owl.Description)
	 */
	@Override
	public double getAccuracy(Description description) {
		Set<Individual> retrieval = reasoner.getIndividuals(description);
		int instancesCovered = 0;
		
		for(Individual ind : classInstances) {
			if(retrieval.contains(ind)) {
				instancesCovered++;
			}
		}
		
		double coverage = instancesCovered/(double)classInstances.size();
		double protusion = instancesCovered/(double)retrieval.size();
		
		return 0.5d * (coverage + protusion);
	}

	/* (non-Javadoc)
	 * @see org.dllearner.core.LearningProblem#getAccuracyOrTooWeak(org.dllearner.core.owl.Description, double)
	 */
	@Override
	public double getAccuracyOrTooWeak(Description description, double minAccuracy) {
		// since we have to perform a retrieval operation anyway, we cannot easily
		// get a benefit from the accuracy limit
		double accuracy = getAccuracy(description);
		if(accuracy >= minAccuracy) {
			return accuracy;
		} else {
			return -1;
		}
	}

	/**
	 * @return the classToDescribe
	 */
	public NamedClass getClassToDescribe() {
		return classToDescribe;
	}

	/* (non-Javadoc)
	 * @see org.dllearner.core.LearningProblem#evaluate(org.dllearner.core.owl.Description)
	 */
	@Override
	public EvaluatedDescription evaluate(Description description) {
		ClassScore score = computeScore(description);
		return new EvaluatedDescriptionClass(description, score);
	}
}
