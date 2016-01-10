package org.dllearner.algorithms.qtl.operations.lgg;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.aksw.jena_sparql_api.cache.h2.CacheUtilsH2;
import org.aksw.jena_sparql_api.core.FluentQueryExecutionFactory;
import org.aksw.jena_sparql_api.core.QueryExecutionFactory;
import org.dllearner.algorithms.qtl.QueryTreeUtils;
import org.dllearner.algorithms.qtl.datastructures.impl.RDFResourceTree;
import org.dllearner.algorithms.qtl.impl.QueryTreeFactory;
import org.dllearner.algorithms.qtl.impl.QueryTreeFactoryBase;
import org.dllearner.algorithms.qtl.util.StopURIsDBpedia;
import org.dllearner.algorithms.qtl.util.StopURIsOWL;
import org.dllearner.algorithms.qtl.util.StopURIsRDFS;
import org.dllearner.algorithms.qtl.util.filters.NamespaceDropStatementFilter;
import org.dllearner.algorithms.qtl.util.filters.PredicateDropStatementFilter;
import org.dllearner.kb.sparql.ConciseBoundedDescriptionGenerator;
import org.dllearner.kb.sparql.ConciseBoundedDescriptionGeneratorImpl;
import org.dllearner.kb.sparql.SparqlEndpoint;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.hp.hpl.jena.datatypes.RDFDatatype;
import com.hp.hpl.jena.graph.Node;
import com.hp.hpl.jena.rdf.model.Model;
import com.hp.hpl.jena.rdf.model.ResourceFactory;
import com.hp.hpl.jena.sparql.vocabulary.FOAF;


/**
 * 
 * @author Lorenz Bühmann
 *
 */
public class LGGGeneratorSimple extends AbstractLGGGenerator {
	
	protected RDFResourceTree computeLGG(RDFResourceTree tree1, RDFResourceTree tree2, boolean learnFilters){
		subCalls++;
		
		// 1. compare the root node
		// if both root nodes have same URI or literal value, just return one of the two trees as LGG
		if ((tree1.isResourceNode() || tree1.isLiteralValueNode()) && tree1.getData().equals(tree2.getData())) {
			logger.trace("Early termination. Tree 1 {}  and tree 2 {} describe the same resource.", tree1, tree2);
			return tree1;
		}
		
		// handle literal nodes with same datatype
		if (tree1.isLiteralNode() && tree2.isLiteralNode()) {
			RDFDatatype d1 = tree1.getData().getLiteralDatatype();
			RDFDatatype d2 = tree2.getData().getLiteralDatatype();

			if (d1 != null && d1.equals(d2)) {
				return new RDFResourceTree(d1);
			}
		}
		
		// else create new empty tree
		RDFResourceTree lgg = new RDFResourceTree();
		
		// 2. compare the edges
		// we only have to compare edges contained in both trees
		for(Node edge : Sets.intersection(tree1.getEdges(), tree2.getEdges())){
			Set<RDFResourceTree> addedChildren = new HashSet<>();
			// loop over children of first tree
			for(RDFResourceTree child1 : tree1.getChildren(edge)){
				// loop over children of second tree
				for(RDFResourceTree child2 : tree2.getChildren(edge)){
					// compute the LGG
					RDFResourceTree lggChild = computeLGG(child1, child2, learnFilters);
					
					// check if there was already a more specific child computed before
					// and if so don't add the current one
					boolean add = true;
					for(Iterator<RDFResourceTree> it = addedChildren.iterator(); it.hasNext();){
						RDFResourceTree addedChild = it.next();
						if(QueryTreeUtils.isSubsumedBy(addedChild, lggChild)){
//							logger.trace("Skipped adding: Previously added child {} is subsumed by {}.",
//									addedChild.getStringRepresentation(),
//									lggChild.getStringRepresentation());
							add = false;
							break;
						} else if(QueryTreeUtils.isSubsumedBy(lggChild, addedChild)){
//							logger.trace("Removing child node: {} is subsumed by previously added child {}.",
//									lggChild.getStringRepresentation(),
//									addedChild.getStringRepresentation());
							lgg.removeChild(addedChild, edge);
							it.remove();
						} 
					}
					if(add){
						lgg.addChild(lggChild, edge);
						addedChildren.add(lggChild);
//						logger.trace("Adding child {}", lggChild.getStringRepresentation());
					} 
				}
			}
		}
		
		return lgg;
	}
	
	
	public static void main(String[] args) throws Exception {
		// knowledge base
		SparqlEndpoint endpoint = SparqlEndpoint.getEndpointDBpedia();
		QueryExecutionFactory qef = FluentQueryExecutionFactory
				.http(endpoint.getURL().toString(), endpoint.getDefaultGraphURIs()).config()
				.withCache(CacheUtilsH2.createCacheFrontend("/tmp/cache", false, TimeUnit.DAYS.toMillis(60)))
				.withPagination(10000).withDelay(50, TimeUnit.MILLISECONDS).end().create();
		
		// tree generation
		ConciseBoundedDescriptionGenerator cbdGenerator = new ConciseBoundedDescriptionGeneratorImpl(qef);
		int maxDepth = 2;
		cbdGenerator.setRecursionDepth(maxDepth);
		
		QueryTreeFactory treeFactory = new QueryTreeFactoryBase();
		treeFactory.setMaxDepth(maxDepth);
		treeFactory.addDropFilters(
				new PredicateDropStatementFilter(StopURIsDBpedia.get()),
				new PredicateDropStatementFilter(StopURIsRDFS.get()),
				new PredicateDropStatementFilter(StopURIsOWL.get()),
				new NamespaceDropStatementFilter(
						Sets.newHashSet(
								"http://dbpedia.org/property/", 
								"http://purl.org/dc/terms/",
								"http://dbpedia.org/class/yago/",
								"http://www.w3.org/2003/01/geo/wgs84_pos#",
								"http://www.georss.org/georss/",
								FOAF.getURI()
								)
								)
				);
		List<RDFResourceTree> trees = new ArrayList<>();
		List<String> resources = Lists.newArrayList("http://dbpedia.org/resource/Leipzig", "http://dbpedia.org/resource/Dresden");
		for(String resource : resources){
			try {
				System.out.println(resource);
				Model model = cbdGenerator.getConciseBoundedDescription(resource);
				RDFResourceTree tree = treeFactory.getQueryTree(ResourceFactory.createResource(resource), model);
				System.out.println(tree.getStringRepresentation());
				trees.add(tree);
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		
		// LGG computation
		LGGGenerator lggGen = new LGGGeneratorSimple();
		RDFResourceTree lgg = lggGen.getLGG(trees);
		
		System.out.println("LGG");
		System.out.println(lgg.getStringRepresentation());
		System.out.println(QueryTreeUtils.toSPARQLQueryString(lgg));
		System.out.println(QueryTreeUtils.toOWLClassExpression(lgg));
	}

}
