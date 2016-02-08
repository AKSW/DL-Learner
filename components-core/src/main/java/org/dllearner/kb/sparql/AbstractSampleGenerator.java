/**
 * Copyright (C) 2007 - 2016, Jens Lehmann
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
package org.dllearner.kb.sparql;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.aksw.jena_sparql_api.core.QueryExecutionFactory;
import org.dllearner.kb.SparqlEndpointKS;
import org.dllearner.reasoning.SPARQLReasoner;
import org.dllearner.utilities.OwlApiJenaUtils;
import org.dllearner.utilities.owl.OWLEntityTypeAdder;
import org.semanticweb.owlapi.model.OWLIndividual;
import org.semanticweb.owlapi.model.OWLOntology;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.hp.hpl.jena.datatypes.RDFDatatype;
import com.hp.hpl.jena.datatypes.xsd.XSDDatatype;
import com.hp.hpl.jena.query.QueryExecution;
import com.hp.hpl.jena.rdf.model.Literal;
import com.hp.hpl.jena.rdf.model.Model;
import com.hp.hpl.jena.rdf.model.ModelFactory;
import com.hp.hpl.jena.rdf.model.Statement;
import com.hp.hpl.jena.rdf.model.StmtIterator;
import com.hp.hpl.jena.vocabulary.OWL;

/**
 * @author Lorenz Buehmann
 *
 */
public abstract class AbstractSampleGenerator {
	
	private ConciseBoundedDescriptionGenerator cbdGen;
	
	private int sampleDepth = 2;

	protected QueryExecutionFactory qef;
	
	protected SPARQLReasoner reasoner;
	
	private boolean loadRelatedSchema = true;

	public AbstractSampleGenerator(SparqlEndpointKS ks) {
		this(ks.getQueryExecutionFactory());
	}
	
	public AbstractSampleGenerator(QueryExecutionFactory qef) {
		this.qef = qef;
		
		cbdGen = new ConciseBoundedDescriptionGeneratorImpl(qef);
		cbdGen.setRecursionDepth(sampleDepth);
		cbdGen.addPropertiesToIgnore(Sets.newHashSet(OWL.sameAs.getURI()));
		
		reasoner = new SPARQLReasoner(qef);
	}
	
	public void addAllowedPropertyNamespaces(Set<String> namespaces) {
		cbdGen.addAllowedPropertyNamespaces(namespaces);
	}
	
	public void addAllowedObjectNamespaces(Set<String> namespaces) {
		cbdGen.addAllowedObjectNamespaces(namespaces);
	}
	
	public void addIgnoredProperties(Set<String> ignoredProperties) {
		cbdGen.addPropertiesToIgnore(ignoredProperties);
	}

	public void setLoadRelatedSchema(boolean loadRelatedSchema) {
		this.loadRelatedSchema = loadRelatedSchema;
	}

	/**
	 * Computes a sample of the knowledge base, i.e. it contains only facts
	 * about the positive and negative individuals.
	 * @param individuals the individuals
	 * @return a sample ontology of the knowledge bas
	 */
	public OWLOntology getSample(Set<OWLIndividual> individuals) {
		return OwlApiJenaUtils.getOWLOntology(getSampleModel(individuals));
	}
	
	/**
	 * @param sampleDepth the maximum sample depth to set
	 */
	public void setSampleDepth(int sampleDepth) {
		this.sampleDepth = sampleDepth;
	}
	
	/**
	 * @return the maximum sample depth
	 */
	public int getSampleDepth() {
		return sampleDepth;
	}
	
	protected Model getSampleModel(Set<OWLIndividual> individuals) {
		Model model = ModelFactory.createDefaultModel();
		
		// load instance data
		for(OWLIndividual ind : individuals){
			Model cbd = cbdGen.getConciseBoundedDescription(ind.toStringID());
			model.add(cbd);
		}
		
		StmtIterator iterator = model.listStatements();
		List<Statement> toAdd = new ArrayList<>();
		while(iterator.hasNext()) {
			Statement st = iterator.next();
			if(st.getObject().isLiteral()) {
				Literal lit = st.getObject().asLiteral();
				RDFDatatype datatype = lit.getDatatype();
				
				if(datatype != null) {
					if(datatype.equals(XSDDatatype.XSDdouble) && lit.getLexicalForm().equals("NAN")) {
						iterator.remove();
						toAdd.add(model.createLiteralStatement(st.getSubject(), st.getPredicate(), Double.NaN));
					} else if(datatype.equals(XSDDatatype.XSDgYear) && st.getPredicate().getURI().equals("http://dbpedia.org/ontology/birthDate")) {
						iterator.remove();
						toAdd.add(model.createStatement(st.getSubject(), st.getPredicate(), model.createTypedLiteral("2000-01-01", XSDDatatype.XSDdate)));
					}
				}
			}
		}
		model.add(toAdd);
		
		// infer entity types, e.g. object or data property
		OWLEntityTypeAdder.addEntityTypes(model);
		
		// load related schema information
		if(loadRelatedSchema) {
//			loadRelatedSchema(model);
		}
		
		return model;
	}
	
	private void loadRelatedSchema(Model model) {
		String query = 
				"CONSTRUCT {" +
				"?p a owl:ObjectProperty;" +
//				"a ?type;" +
				"rdfs:domain ?domain;" +
				"rdfs:range ?range." +
				"} WHERE {" +
				"?p a owl:ObjectProperty." +
//				"?p a ?type. " +
				"OPTIONAL{?p rdfs:domain ?domain.} " +
				"OPTIONAL{?p rdfs:range ?range.}" +
				"}";
		
		QueryExecution qe = qef.createQueryExecution(query);
		qe.execConstruct(model);
		qe.close();
		
		query = 
				"CONSTRUCT {" +
				"?s a owl:Class ." +
				"?s rdfs:subClassOf ?sup ." +
				"} WHERE {\n" +
				"?s a owl:Class ." +
				"OPTIONAL{?s rdfs:subClassOf ?sup .} " +
				"}";
		qe = qef.createQueryExecution(query);
		qe.execConstruct(model);
		qe.close();
	}

}
