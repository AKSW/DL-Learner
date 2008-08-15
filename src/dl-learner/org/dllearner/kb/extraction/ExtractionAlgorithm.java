/**
 * Copyright (C) 2007, Sebastian Hellmann
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
package org.dllearner.kb.extraction;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;

import org.apache.log4j.Logger;
import org.dllearner.kb.aquisitors.TupelAquisitor;

/**
 * This class is used to extract the information .
 * 
 * @author Sebastian Hellmann
 */
public class ExtractionAlgorithm {

	private Configuration configuration;
	//private Manipulators manipulator;
	//private int recursionDepth = 1;
	// private boolean getAllSuperClasses = true;
	// private boolean closeAfterRecursion = true;
	private static Logger logger = Logger
		.getLogger(ExtractionAlgorithm.class);

	public ExtractionAlgorithm(Configuration Configuration) {
		this.configuration = Configuration;
		//this.manipulator = Configuration.getManipulator();
		//this.recursionDepth = Configuration.getRecursiondepth();
		// this.getAllSuperClasses = Configuration.isGetAllSuperClasses();
		// this.closeAfterRecursion=Configuration.isCloseAfterRecursion();
	}

	public Node getFirstNode(URI u) {
		return new InstanceNode(u);
	}

	public List<Node> expandAll(URI[] uris, TupelAquisitor tupelAquisitor) {
		List<Node> v = new ArrayList<Node>();
		for (URI oneURI : uris) {
			v.add(expandNode(oneURI, tupelAquisitor));
		}
		return v;
	}

	/**
	 * most important function expands one example 
	 * CAVE: the recursion is not a
	 * recursion anymore, it was transformed to an iteration
	 * 
	 * @param uri
	 * @param typedSparqlQuery
	 * @return
	 */
	public Node expandNode(URI uri, TupelAquisitor tupelAquisitor) {
		//System.out.println(uri.toString());
		//System.out.println(manipulator);
		//System.out.println(this.configuration);
		long time = System.currentTimeMillis();
		
		Node n = getFirstNode(uri);
		logger.info(n);
		List<Node> initialNodes = new ArrayList<Node>();
		initialNodes.add(n);
		logger.info("StartVector: " + initialNodes);
		// n.expand(tsp, this.Manipulator);
		// Vector<Node> second=
		for (int x = 1; x <= configuration.getRecursiondepth(); x++) {

			List<Node> tmp = new ArrayList<Node>();
			while (!initialNodes.isEmpty()) {
				Node tmpNode = initialNodes.remove(0);
				logger.info("Expanding " + tmpNode);
				// System.out.println(this.Manipulator);
				// these are the new not expanded nodes
				// the others are saved in connection with the original node
				List<Node> tmpNodeList = tmpNode.expand(tupelAquisitor,
						configuration.getManipulator());
				//System.out.println(tmpVec);
				tmp.addAll(tmpNodeList);
			}
			//CAVE: possible error here
			initialNodes = tmp;
			logger.info("Recursion counter: " + x + " with " + initialNodes.size()
					+ " Nodes remaining, needed: "
					+ (System.currentTimeMillis() - time) + "ms");
			time = System.currentTimeMillis();
		}

		SortedSet<String> hadAlready = new TreeSet<String>();
		
		//p(configuration.toString());
		// gets All Class Nodes and expands them further
		if (configuration.isGetAllSuperClasses()) {
			logger.info("Get all superclasses");
			// Set<Node> classes = new TreeSet<Node>();
			List<Node> classes = new ArrayList<Node>();
			List<Node> instances = new ArrayList<Node>();

			for (Node one : initialNodes) {
				if (one instanceof ClassNode) {
					classes.add(one);
				}
				if (one instanceof InstanceNode) {
					instances.add(one);
				}

			}
			// System.out.println(instances.size());
			//TODO LinkedData incompatibility
			//TupelAquisitor tupelAquisitorClasses = configuration.sparqlTupelAquisitorClasses;
			//XXX this should be solved in a better way
			tupelAquisitor.setClassMode(true);
			if (configuration.isCloseAfterRecursion()) {
				while (!instances.isEmpty()) {
					logger.trace("Getting classes for remaining instances: "
							+ instances.size());
					Node next = instances.remove(0);
					logger.trace("Getting classes for: " + next);
					classes.addAll(next.expand(tupelAquisitor, configuration.getManipulator()));
					if (classes.size() >= configuration.getBreakSuperClassesAfter()) {
						break;
					}
				}
			}
			//XXX this should be solved in a better way
			tupelAquisitor.setClassMode(false);
			
			List<Node> tmp = new ArrayList<Node>();
			int i = 0;
			while (!classes.isEmpty()) {
				logger.trace("Remaining classes: " + classes.size());
				// Iterator<Node> it=classes.iterator();
				// Node next =(Node) it.next();
				// classes.remove(next);
				Node next = classes.remove(0);

				if (!hadAlready.contains(next.getURI().toString())) {
					logger.trace("Getting SuperClass for: " + next);
					// System.out.println(hadAlready.size());
					hadAlready.add(next.getURI().toString());
					tmp = next.expand(tupelAquisitor, configuration.getManipulator());
					classes.addAll(tmp);
					tmp = new ArrayList<Node>();
					// if(i % 50==0)System.out.println("got "+i+" extra classes,
					// max: "+manipulator.breakSuperClassRetrievalAfter);
					i++;
					if (i >= configuration.getBreakSuperClassesAfter()) {
						break;
					}
				}
				// System.out.println("Skipping");

				// if
				// (classes.size()>=manipulator.breakSuperClassRetrievalAfter){break;}

			}
			// System.out.println((System.currentTimeMillis()-time)+"");

		}
		return n;

	}

	

}
