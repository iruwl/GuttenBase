package de.akquinet.jbosscc.guttenbase.hints.impl;

import de.akquinet.jbosscc.guttenbase.defaults.impl.DefaultColumnNameMapper;
import de.akquinet.jbosscc.guttenbase.hints.ColumnNameMapperHint;
import de.akquinet.jbosscc.guttenbase.mapping.ColumnNameMapper;

/**
 * Default implementation just returns the plain column name.
 * 
 * <p>
 * &copy; 2012-2020 akquinet tech@spree
 * </p>
 * 
 * @author M. Dahm
 */
public class DefaultColumnNameMapperHint extends ColumnNameMapperHint {
	@Override
	public ColumnNameMapper getValue() {
		return new DefaultColumnNameMapper();
	}
}
