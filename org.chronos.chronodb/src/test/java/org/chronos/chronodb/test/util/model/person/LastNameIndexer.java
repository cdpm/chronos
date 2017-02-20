package org.chronos.chronodb.test.util.model.person;

import java.util.Collections;
import java.util.Set;

public class LastNameIndexer extends PersonIndexer {

	@Override
	protected Set<String> getIndexValuesInternal(final Person person) {
		return Collections.singleton(person.getLastName());
	}

}
