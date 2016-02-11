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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.hp.hpl.jena.rdf.model.Model;

public class CachingConciseBoundedDescriptionGenerator implements ConciseBoundedDescriptionGenerator{
	
	private Map<String, Model> cache;
	private ConciseBoundedDescriptionGenerator delegatee;
	
	public CachingConciseBoundedDescriptionGenerator(ConciseBoundedDescriptionGenerator cbdGen) {
		this.delegatee = cbdGen;
		cache = new HashMap<>();
	}
	
	@Override
	public Model getConciseBoundedDescription(String resourceURI){
		Model cbd = cache.get(resourceURI);
		if(cbd == null){
			cbd = delegatee.getConciseBoundedDescription(resourceURI);
			cache.put(resourceURI, cbd);
		}
		return cbd;
	}
	
	@Override
	public Model getConciseBoundedDescription(String resourceURI, int depth){
		Model cbd = cache.get(resourceURI);
		if(cbd == null){
			cbd = delegatee.getConciseBoundedDescription(resourceURI, depth);
			cache.put(resourceURI, cbd);
		}
		return cbd;
	}

	@Override
	public void addAllowedPropertyNamespaces(Set<String> namespaces) {
		delegatee.addAllowedPropertyNamespaces(namespaces);
	}
	
	/* (non-Javadoc)
	 * @see org.dllearner.kb.sparql.ConciseBoundedDescriptionGenerator#addAllowedObjectNamespaces(java.util.Set)
	 */
	@Override
	public void addAllowedObjectNamespaces(Set<String> namespaces) {
		delegatee.addAllowedObjectNamespaces(namespaces);
	}
	
	@Override
	public void setRecursionDepth(int maxRecursionDepth) {
		delegatee.setRecursionDepth(maxRecursionDepth);
	}
	
	@Override
	public void addPropertiesToIgnore(Set<String> properties) {
	}

	/* (non-Javadoc)
	 * @see org.dllearner.kb.sparql.ConciseBoundedDescriptionGenerator#getConciseBoundedDescription(java.lang.String, int, boolean)
	 */
	@Override
	public Model getConciseBoundedDescription(String resourceURI, int depth, boolean withTypesForLeafs) {
		Model cbd = cache.get(resourceURI);
		if(cbd == null){
			cbd = delegatee.getConciseBoundedDescription(resourceURI, depth, withTypesForLeafs);
			cache.put(resourceURI, cbd);
		}
		return cbd;
	}

	

}
