package org.dllearner.utilities;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.Stack;

import org.aksw.jena_sparql_api.core.QueryExecutionFactory;
import org.apache.commons.collections15.ListUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;
import com.hp.hpl.jena.graph.Node;
import com.hp.hpl.jena.graph.Triple;
import com.hp.hpl.jena.query.ParameterizedSparqlString;
import com.hp.hpl.jena.query.Query;
import com.hp.hpl.jena.query.QueryExecution;
import com.hp.hpl.jena.query.QueryFactory;
import com.hp.hpl.jena.query.QuerySolution;
import com.hp.hpl.jena.query.ResultSet;
import com.hp.hpl.jena.sparql.core.TriplePath;
import com.hp.hpl.jena.sparql.core.Var;
import com.hp.hpl.jena.sparql.syntax.Element;
import com.hp.hpl.jena.sparql.syntax.ElementFilter;
import com.hp.hpl.jena.sparql.syntax.ElementGroup;
import com.hp.hpl.jena.sparql.syntax.ElementOptional;
import com.hp.hpl.jena.sparql.syntax.ElementPathBlock;
import com.hp.hpl.jena.sparql.syntax.ElementTriplesBlock;
import com.hp.hpl.jena.sparql.syntax.ElementUnion;
import com.hp.hpl.jena.sparql.syntax.ElementVisitorBase;
import com.hp.hpl.jena.sparql.syntax.ElementWalker;
import com.hp.hpl.jena.sparql.util.VarUtils;
import com.hp.hpl.jena.vocabulary.RDF;

public class QueryUtils extends ElementVisitorBase {
	
private static final Logger logger = LoggerFactory.getLogger(QueryUtils.class);	

	private static final ParameterizedSparqlString superClassesQueryTemplate = new ParameterizedSparqlString(
			"PREFIX rdfs:<http://www.w3.org/2000/01/rdf-schema#> "
			+ "SELECT ?sup WHERE {"
			+ "?sub ((rdfs:subClassOf|owl:equivalentClass)|^owl:equivalentClass)+ ?sup .}");
	
	private Set<Triple> triplePattern;
	private Set<Triple> optionalTriplePattern;
	
	private boolean inOptionalClause = false;
	
	private int unionCount = 0;
	private int optionalCount = 0;
	private int filterCount = 0;
	
	private Map<Triple, ElementGroup> triple2Parent = new HashMap<>();
	
	Stack<ElementGroup> parents = new Stack<>();
	
	public static String addPrefix(String queryString, Map<String, String> prefix2Namespace){
		Query query = QueryFactory.create(queryString);
		for (Entry<String, String> entry : prefix2Namespace.entrySet()) {
			String prefix = entry.getKey();
			String namespace = entry.getValue();
			query.setPrefix(prefix, namespace);
		}
		return query.toString();
	}
	
	public static String addPrefixes(String queryString, String prefix, String namespace){
		Query query = QueryFactory.create(queryString);
		query.setPrefix(prefix, namespace);
		return query.toString();
	}
	
	/**
	 * Returns all variables that occur in a triple pattern of the SPARQL query.
	 * @param query the query
	 * @return
	 */
	public Set<Var> getVariables(Query query){
		Set<Var> vars = new HashSet<>();
		
		Set<Triple> triplePatterns = extractTriplePattern(query, false);
		
		for (Triple tp : triplePatterns) {
			if(tp.getSubject().isVariable()){
				vars.add(Var.alloc(tp.getSubject()));
			} else if(tp.getObject().isVariable()){
				vars.add(Var.alloc(tp.getObject()));
			} else if(tp.getPredicate().isVariable()){
				vars.add(Var.alloc(tp.getPredicate()));
			}
		}
		
		return vars;
	}
	
	/**
	 * Returns all variables that occur as subject in a triple pattern of the SPARQL query.
	 * @param query the query
	 * @return
	 */
	public Set<Var> getSubjectVariables(Query query){
		Set<Var> vars = new HashSet<>();
		
		Set<Triple> triplePatterns = extractTriplePattern(query, false);
		
		for (Triple tp : triplePatterns) {
			if(tp.getSubject().isVariable()){
				vars.add(Var.alloc(tp.getSubject()));
			} 
		}
		
		return vars;
	}
	
	/**
	 * Returns all variables that occur as subject in a triple pattern of the SPARQL query.
	 * @param query the query
	 * @return
	 */
	public static Set<Var> getSubjectVars(Query query){
		final Set<Var> vars = new HashSet<>();
		
		ElementWalker.walk(query.getQueryPattern(), new ElementVisitorBase(){
			@Override
			public void visit(ElementTriplesBlock el) {
				Iterator<Triple> triples = el.patternElts();
	            while (triples.hasNext()) {
	            	Triple triple = triples.next();
	            	if(triple.getSubject().isVariable()) {
	            		vars.add(Var.alloc(triples.next().getSubject()));
	            	}
	            }
			}
			
			@Override
			public void visit(ElementPathBlock el) {
				Iterator<TriplePath> triples = el.patternElts();
	            while (triples.hasNext()) {
	            	TriplePath triple = triples.next();
	            	if(triple.getSubject().isVariable()) {
	            		vars.add(Var.alloc(triples.next().getSubject()));
	            	}
	            }
			}
		});
		
		return vars;
	}
	
	public static Set<Triple> getTriplePatterns(Query query){
		final Set<Triple> triplePatterns = Sets.newHashSet();
		
		ElementWalker.walk(query.getQueryPattern(), new ElementVisitorBase(){
			@Override
			public void visit(ElementTriplesBlock el) {
				Iterator<Triple> triples = el.patternElts();
	            while (triples.hasNext()) {
	            	Triple triple = triples.next();
	            	triplePatterns.add(triple);
	            }
			}
			
			@Override
			public void visit(ElementPathBlock el) {
				Iterator<TriplePath> triplePaths = el.patternElts();
	            while (triplePaths.hasNext()) {
	            	TriplePath tp = triplePaths.next();
	            	if(tp.isTriple()) {
	            		Triple triple = tp.asTriple();
	            		triplePatterns.add(triple);
	            	}
	            }
			}
		});
		return triplePatterns;
	}
	
	/**
	 * Given a SPARQL query and a start node, return the outgoing
	 * triple patterns.
	 * @param query the query
	 * @param source the start node
	 * @return
	 */
	public static Set<Triple> getOutgoingTriplePatterns(Query query, final Node source){
		final Set<Triple> outgoingTriples = Sets.newHashSet();
		
		ElementWalker.walk(query.getQueryPattern(), new ElementVisitorBase(){
			@Override
			public void visit(ElementTriplesBlock el) {
				Iterator<Triple> triples = el.patternElts();
	            while (triples.hasNext()) {
	            	Triple triple = triples.next();
	            	Node subject = triple.getSubject();
	            	if(subject.equals(source)) {
	            		outgoingTriples.add(triple);
	            	}
	            }
			}
			
			@Override
			public void visit(ElementPathBlock el) {
				Iterator<TriplePath> triplePaths = el.patternElts();
	            while (triplePaths.hasNext()) {
	            	TriplePath tp = triplePaths.next();
	            	if(tp.isTriple()) {
	            		Triple triple = tp.asTriple();
		            	Node subject = triple.getSubject();
		            	if(subject.equals(source)) {
		            		outgoingTriples.add(triple);
		            	}
	            	}
	            }
			}
		});
		
		return outgoingTriples;
	}
	
	/**
	 * Given a SPARQL query and a start node, return the maximum subject-object
	 * join depth.
	 * @param query the query
	 * @param source the start node
	 * @return
	 */
	public static int getSubjectObjectJoinDepth(Query query, final Node source){
		final Set<Triple> outgoingTriples = Sets.newHashSet();
		
		ElementWalker.walk(query.getQueryPattern(), new ElementVisitorBase(){
			@Override
			public void visit(ElementTriplesBlock el) {
				Iterator<Triple> triples = el.patternElts();
	            while (triples.hasNext()) {
	            	Triple triple = triples.next();
	            	Node subject = triple.getSubject();
	            	if(subject.equals(source) && triple.getObject().isVariable()) {
	            		outgoingTriples.add(triple);
	            	}
	            }
			}
			
			@Override
			public void visit(ElementPathBlock el) {
				Iterator<TriplePath> triplePaths = el.patternElts();
	            while (triplePaths.hasNext()) {
	            	TriplePath tp = triplePaths.next();
	            	if(tp.isTriple()) {
	            		Triple triple = tp.asTriple();
		            	Node subject = triple.getSubject();
		            	if(subject.equals(source) && triple.getObject().isVariable()) {
		            		outgoingTriples.add(triple);
		            	}
	            	}
	            }
			}
		});
		
		int maxDepth = 0;
		for (Triple triple : outgoingTriples) {
			maxDepth = Math.max(maxDepth, 1 + getSubjectObjectJoinDepth(query, triple.getObject()));
		}
		
		return maxDepth;
	}
	
	/**
	 * Returns all variables that occur as object in a triple pattern of the SPARQL query.
	 * @param query the query
	 * @return
	 */
	public static Set<Var> getObjectVars(Query query){
		final Set<Var> vars = new HashSet<>();
		
		ElementWalker.walk(query.getQueryPattern(), new ElementVisitorBase(){
			@Override
			public void visit(ElementTriplesBlock el) {
				Iterator<Triple> triples = el.patternElts();
	            while (triples.hasNext()) {
	            	Triple triple = triples.next();
	            	if(triple.getObject().isVariable()) {
	            		vars.add(Var.alloc(triples.next().getObject()));
	            	}
	            }
			}
			
			@Override
			public void visit(ElementPathBlock el) {
				Iterator<TriplePath> triples = el.patternElts();
	            while (triples.hasNext()) {
	            	TriplePath triple = triples.next();
	            	if(triple.getObject().isVariable()) {
	            		vars.add(Var.alloc(triples.next().getObject()));
	            	}
	            }
			}
		});
		
		return vars;
	}
	
	/**
	 * Returns all variables that occur as subject in a triple pattern of the SPARQL query.
	 * @param query the query
	 * @return
	 */
	public Set<Var> getObjectVariables(Query query){
		Set<Var> vars = new HashSet<>();
		
		Set<Triple> triplePatterns = extractTriplePattern(query, false);
		
		for (Triple tp : triplePatterns) {
			if(tp.getObject().isVariable()){
				vars.add(Var.alloc(tp.getObject()));
			} 
		}
		
		return vars;
	}
	
	/**
	 * Returns all triple patterns in given SPARQL query that have the given node in subject position, i.e. the outgoing
	 * triple patterns.
	 * @param query The SPARQL query.
	 * @param node the node
	 * @return
	 */
	public Set<Triple> extractOutgoingTriplePatterns(Query query, Node node){
		Set<Triple> triplePatterns = extractTriplePattern(query, false);
		//remove triple patterns not containing triple patterns with given node in subject position
		for (Iterator<Triple> iterator = triplePatterns.iterator(); iterator.hasNext();) {
			Triple triple = iterator.next();
			if(!triple.subjectMatches(node)){
				iterator.remove();
			}
		}
		return triplePatterns;
	}
	
	/**
	 * Returns all triple patterns in given SPARQL query that have the given node in object position, i.e. the incoming
	 * triple patterns.
	 * @param query The SPARQL query.
	 * @param node the node
	 * @return
	 */
	public Set<Triple> extractIncomingTriplePatterns(Query query, Node node){
		Set<Triple> triplePatterns = extractTriplePattern(query, false);
		//remove triple patterns not containing triple patterns with given node in subject position
		for (Iterator<Triple> iterator = triplePatterns.iterator(); iterator.hasNext();) {
			Triple triple = iterator.next();
			if(!triple.objectMatches(node)){
				iterator.remove();
			}
		}
		return triplePatterns;
	}
	
	/**
	 * Returns all triple patterns in given SPARQL query that have the given node in object position, i.e. the ingoing
	 * triple patterns.
	 * @param query The SPARQL query.
	 * @param node the node
	 * @return
	 */
	public Set<Triple> extractIngoingTriplePatterns(Query query, Node node){
		Set<Triple> triplePatterns = extractTriplePattern(query, false);
		//remove triple patterns not containing triple patterns with given node in object position
		for (Iterator<Triple> iterator = triplePatterns.iterator(); iterator.hasNext();) {
			Triple triple = iterator.next();
			if(!triple.objectMatches(node)){
				iterator.remove();
			}
		}
		return triplePatterns;
	}
	
	/**
	 * Returns all triple patterns in given SPARQL query that have the given node either in subject or in object position, i.e. 
	 * the ingoing and outgoing triple patterns.
	 * @param query The SPARQL query.
	 * @param node the node
	 * @return
	 */
	public Set<Triple> extractTriplePatterns(Query query, Node node){
		Set<Triple> triplePatterns = new HashSet<>();
		triplePatterns.addAll(extractIngoingTriplePatterns(query, node));
		triplePatterns.addAll(extractOutgoingTriplePatterns(query, node));
		return triplePatterns;
	}
	
	/**
	 * Returns all triple patterns in given SPARQL query that contain the
	 * given predicate. 
	 * @param query the SPARQL query.
	 * @param predicate the predicate
	 * @return
	 */
	public Set<Triple> extractTriplePatternsWithPredicate(Query query, Node predicate){
		// get all triple patterns
		Set<Triple> triplePatterns = extractTriplePattern(query);
		
		// filter by predicate
		Iterator<Triple> iterator = triplePatterns.iterator();
		while (iterator.hasNext()) {
			Triple tp = (Triple) iterator.next();
			if(!tp.predicateMatches(predicate)) {
				iterator.remove();
			}
		}
		
		return triplePatterns;
	}
	
	/**
	 * Returns all triple patterns in given SPARQL query that have the given node either in subject or in object position, i.e. 
	 * the incoming and outgoing triple patterns.
	 * @param query The SPARQL query.
	 * @param node the node
	 * @return
	 */
	public Set<Triple> extractNonOptionalTriplePatterns(Query query, Node node){
		Set<Triple> triplePatterns = new HashSet<>();
		triplePatterns.addAll(extractIngoingTriplePatterns(query, node));
		triplePatterns.addAll(extractOutgoingTriplePatterns(query, node));
		triplePatterns.removeAll(optionalTriplePattern);
		return triplePatterns;
	}
	
	/**
	 * Returns triple patterns for each projection variable v such that v is either in subject or object position.
	 * @param query The SPARQL query.
	 * @return
	 */
	public Map<Var,Set<Triple>> extractTriplePatternsForProjectionVars(Query query){
		Map<Var,Set<Triple>> var2TriplePatterns = new HashMap<>();
		for (Var var : query.getProjectVars()) {
			Set<Triple> triplePatterns = new HashSet<>();
			triplePatterns.addAll(extractIngoingTriplePatterns(query, var));
			triplePatterns.addAll(extractOutgoingTriplePatterns(query, var));
			var2TriplePatterns.put(var, triplePatterns);
		}
		return var2TriplePatterns;
	}
	
	/**
	 * Returns triple patterns for each projection variable v such that v is in subject position.
	 * @param query The SPARQL query.
	 * @return
	 */
	public Map<Var,Set<Triple>> extractOutgoingTriplePatternsForProjectionVars(Query query){
		Map<Var,Set<Triple>> var2TriplePatterns = new HashMap<>();
		for (Var var : query.getProjectVars()) {
			Set<Triple> triplePatterns = new HashSet<>();
			triplePatterns.addAll(extractOutgoingTriplePatterns(query, var));
			var2TriplePatterns.put(var, triplePatterns);
		}
		return var2TriplePatterns;
	}
	
	/**
	 * @return the optionalTriplePattern
	 */
	public Set<Triple> getOptionalTriplePatterns() {
		return optionalTriplePattern;
	}
	
	/**
	 * Returns triple patterns for each projection variable v such that v is in subject position.
	 * @param query The SPARQL query.
	 * @return
	 */
	public Map<Var,Set<Triple>> extractIncomingTriplePatternsForProjectionVars(Query query){
		Map<Var,Set<Triple>> var2TriplePatterns = new HashMap<>();
		for (Var var : query.getProjectVars()) {
			Set<Triple> triplePatterns = new HashSet<>();
			triplePatterns.addAll(extractIncomingTriplePatterns(query, var));
			var2TriplePatterns.put(var, triplePatterns);
		}
		return var2TriplePatterns;
	}
	
	/**
	 * Returns triple patterns for each projection variable v such that v is in object position.
	 * @param query The SPARQL query.
	 * @return
	 */
	public Map<Var,Set<Triple>> extractIngoingTriplePatternsForProjectionVars(Query query){
		Map<Var,Set<Triple>> var2TriplePatterns = new HashMap<>();
		for (Var var : query.getProjectVars()) {
			Set<Triple> triplePatterns = new HashSet<>();
			triplePatterns.addAll(extractIngoingTriplePatterns(query, var));
			var2TriplePatterns.put(var, triplePatterns);
		}
		return var2TriplePatterns;
	}
	
	public Set<Triple> extractTriplePattern(Query query){
		return extractTriplePattern(query, false);
	}
	
	public Set<Triple> extractTriplePattern(Query query, boolean ignoreOptionals){
		triplePattern = new HashSet<>();
		optionalTriplePattern = new HashSet<>();
		
		query.getQueryPattern().visit(this);
		
		//postprocessing: triplepattern in OPTIONAL clause
		if(!ignoreOptionals){
			if(query.isSelectType()){
				for(Triple t : optionalTriplePattern){
					if(!ListUtils.intersection(new ArrayList<>(VarUtils.getVars(t)), query.getProjectVars()).isEmpty()){
						triplePattern.add(t);
					}
				}
			}
		}
		return triplePattern;
	}
	
	public boolean isOptional(Triple triple){
		return optionalTriplePattern.contains(triple);
	}
	
	public Set<Triple> extractTriplePattern(ElementGroup group){
		return extractTriplePattern(group, false);
	}
	
	public Set<Triple> extractTriplePattern(ElementGroup group, boolean ignoreOptionals){
		triplePattern = new HashSet<>();
		optionalTriplePattern = new HashSet<>();
		
		group.visit(this);
		
		//postprocessing: triplepattern in OPTIONAL clause
		if(!ignoreOptionals){
			for(Triple t : optionalTriplePattern){
				triplePattern.add(t);
			}
		}
		
		return triplePattern;
	}
	
	public Query removeUnboundObjectVarTriples(Query query) {
		QueryUtils queryUtils = new QueryUtils();
		Set<Triple> triplePatterns = queryUtils.extractTriplePattern(query);
		
		Multimap<Var, Triple> var2TriplePatterns = HashMultimap.create();
		for (Triple tp : triplePatterns) {
			var2TriplePatterns.put(Var.alloc(tp.getSubject()), tp);
		}
		
		Iterator<Triple> iterator = triplePatterns.iterator();
		while (iterator.hasNext()) {
			Triple triple = iterator.next();
			Node object = triple.getObject();
			if(object.isVariable() && !var2TriplePatterns.containsKey(Var.alloc(object))) {
				iterator.remove();
			}
		}
		
		Query newQuery = new Query();
		newQuery.addProjectVars(query.getProjectVars());
		ElementTriplesBlock el = new ElementTriplesBlock();
		for (Triple triple : triplePatterns) {
			el.addTriple(triple);
		}
		newQuery.setQuerySelectType();
		newQuery.setDistinct(true);
		newQuery.setQueryPattern(el);
		
		return newQuery;
	}
	
	/**
	 * Removes triple patterns of form (s rdf:type A) if there exists a
	 * triple pattern (s rdf:type B) such that the underlying
	 * knowledge base entails (B rdfs:subClassOf A).
	 * @param qef the query execution factory
	 * @param query the query
	 */
	public void filterOutGeneralTypes(QueryExecutionFactory qef, Query query){
		// extract all rdf:type triple patterns
		Set<Triple> typeTriplePatterns = extractTriplePatternsWithPredicate(query, RDF.type.asNode());
		
		// group by subject
		Multimap<Node, Triple> subject2TriplePatterns = HashMultimap.create();
		for (Triple tp : typeTriplePatterns) {
			subject2TriplePatterns.put(tp.getSubject(), tp);
		}
		
		// keep the most specific types for each subject
		for (Node subject : subject2TriplePatterns.keySet()) {
			Collection<Triple> triplePatterns = subject2TriplePatterns.get(subject);
			Collection<Triple> triplesPatterns2Remove = new HashSet<>();
			
			for (Triple tp : triplePatterns) {
				if(!triplesPatterns2Remove.contains(tp)) {
					// get all super classes for the triple object
					Set<Node> superClasses = getSuperClasses(qef, tp.getObject());
					
					// remove triple patterns that have one of the super classes as object
					for (Triple tp2 : triplePatterns) {
						if(tp2 != tp && superClasses.contains(tp2.getObject())) {
							triplesPatterns2Remove.add(tp2);
						}
					}
				}
			}
			
			// remove triple patterns
			triplePatterns.removeAll(triplesPatterns2Remove);
		}
	}
	
	private Set<Node> getSuperClasses(QueryExecutionFactory qef, Node cls){
		Set<Node> superClasses = new HashSet<>();
		
		superClassesQueryTemplate.setIri("sub", cls.getURI());
		
		String query = superClassesQueryTemplate.toString();
		
		try {
			QueryExecution qe = qef.createQueryExecution(query);
			ResultSet rs = qe.execSelect();
			while(rs.hasNext()){
				QuerySolution qs = rs.next();
				superClasses.add(qs.getResource("sup").asNode());
			}
			qe.close();
		} catch (Exception e) {
			logger.error("ERROR. Getting super classes of " + cls + " failed.", e);
		}
		
		return superClasses;
	}
	
	@Override
	public void visit(ElementGroup el) {
		parents.push(el);
		for (Element e : el.getElements()) {
			e.visit(this);
		}
		parents.pop();
	}

	@Override
	public void visit(ElementOptional el) {
		optionalCount++;
		inOptionalClause = true;
		el.getOptionalElement().visit(this);
		inOptionalClause = false;
	}

	@Override
	public void visit(ElementTriplesBlock el) {
		for (Iterator<Triple> iterator = el.patternElts(); iterator.hasNext();) {
			Triple t = iterator.next();
			if(inOptionalClause){
				optionalTriplePattern.add(t);
			} else {
				triplePattern.add(t);
			}
			if(!parents.isEmpty()){
				triple2Parent.put(t, parents.peek());
			}
		}
	}

	@Override
	public void visit(ElementPathBlock el) {
		for (Iterator<TriplePath> iterator = el.patternElts(); iterator.hasNext();) {
			TriplePath tp = iterator.next();
			if(inOptionalClause){
				if(tp.isTriple()){
					optionalTriplePattern.add(tp.asTriple());
					if(!parents.isEmpty()){
						triple2Parent.put(tp.asTriple(), parents.peek());
					}
				}
			} else {
				if(tp.isTriple()){
					triplePattern.add(tp.asTriple());
					if(!parents.isEmpty()){
						triple2Parent.put(tp.asTriple(), parents.peek());
					}
				}
			}
			
		}
	}

	@Override
	public void visit(ElementUnion el) {
		unionCount++;
		for (Element e : el.getElements()) {
			e.visit(this);
		}
	}
	
	@Override
	public void visit(ElementFilter el) {
		filterCount++;
	}

	public int getUnionCount() {
		return unionCount;
	}

	public int getOptionalCount() {
		return optionalCount;
	}

	public int getFilterCount() {
		return filterCount;
	}
	
	/**
	 * Returns the ElementGroup object containing the triple pattern.
	 * @param triple the triple patterm
	 * @return
	 */
	public ElementGroup getElementGroup(Triple triple){
		return triple2Parent.get(triple);
	}
	
	public static void main(String[] args) throws Exception {
		Query q = QueryFactory.create(
				"PREFIX  dbp:  <http://dbpedia.org/resource/>\n" + 
				"PREFIX  dbo: <http://dbpedia.org/ontology/>\n" + 
				"SELECT  ?thumbnail\n" + 
				"WHERE\n" + 
				"  { dbp:total !dbo:thumbnail ?thumbnail }");
		QueryUtils queryUtils = new QueryUtils();
		queryUtils.extractIngoingTriplePatterns(q, q.getProjectVars().get(0));
		
		q = QueryFactory.create("SELECT DISTINCT  ?x0\n" + 
				"WHERE\n" + 
				"  { ?x0  <http://dbpedia.org/ontology/activeYearsEndYear>  ?date5 ;\n" + 
				"         <http://dbpedia.org/ontology/activeYearsStartYear>  ?date4 ;\n" + 
				"         <http://dbpedia.org/ontology/birthDate>  ?date0 ;\n" + 
				"         <http://dbpedia.org/ontology/birthPlace>  <http://dbpedia.org/resource/Austria> ;\n" + 
				"         <http://dbpedia.org/ontology/birthPlace>  <http://dbpedia.org/resource/Austria-Hungary> ;\n" + 
				"         <http://dbpedia.org/ontology/birthPlace>  <http://dbpedia.org/resource/Vienna> ;\n" + 
				"         <http://dbpedia.org/ontology/birthYear>  ?date3 ;\n" + 
				"         <http://dbpedia.org/ontology/deathDate>  ?date2 ;\n" + 
				"         <http://dbpedia.org/ontology/deathPlace>  <http://dbpedia.org/resource/Berlin> ;\n" + 
				"         <http://dbpedia.org/ontology/deathPlace>  <http://dbpedia.org/resource/Germany> ;\n" + 
				"         <http://dbpedia.org/ontology/deathYear>  ?date1 ;\n" + 
				"         <http://dbpedia.org/ontology/occupation>  <http://dbpedia.org/resource/Hilde_K%C3%B6rber__1> ;\n" + 
				"         <http://dbpedia.org/ontology/viafId>  \"32259546\" ;\n" + 
				"         <http://www.w3.org/1999/02/22-rdf-syntax-ns#type>  <http://dbpedia.org/ontology/Person> .\n" + 
				"    FILTER ( ( str(?date0) = \"1906-07-03+02:00\" ) || ( str(?date0) = \"1906-07-03\" ) )\n" + 
				"    FILTER ( ( str(?date1) = \"1969+02:00\" ) || ( str(?date1) = \"1969-01-01\" ) )\n" + 
				"    FILTER ( ( str(?date2) = \"1969-05-31+02:00\" ) || ( str(?date2) = \"1969-05-31\" ) )\n" + 
				"    FILTER ( ( str(?date3) = \"1906+02:00\" ) || ( str(?date3) = \"1906-01-01\" ) )\n" + 
				"    FILTER ( ( str(?date4) = \"1930+02:00\" ) || ( str(?date4) = \"1930-01-01\" ) )\n" + 
				"    FILTER ( ( str(?date5) = \"1964+02:00\" ) || ( str(?date5) = \"1964-01-01\" ) )\n" + 
				"  }");
		
		System.out.println(queryUtils.removeUnboundObjectVarTriples(q));
		
		String query = "SELECT DISTINCT ?s WHERE {"
				+ "?s a <http://dbpedia.org/ontology/BeautyQueen> ."
				+ "?s <http://dbpedia.org/ontology/birthPlace> ?o0 ."
				+ "?o0 <http://dbpedia.org/ontology/isPartOf> ?o1 ."
				+ "?o1 <http://dbpedia.org/ontology/timeZone> <http://dbpedia.org/resource/Eastern_Time_Zone> .}";
		
		System.out.println(QueryUtils.getSubjectObjectJoinDepth(QueryFactory.create(query), Var.alloc("s")));
	}
}
