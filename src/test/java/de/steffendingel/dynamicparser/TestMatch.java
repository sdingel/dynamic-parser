package de.steffendingel.dynamicparser;

import java.util.List;

/**
 * A simple {@link Match} for tests that has no grammar editor.
 */
record TestMatch(Rule rule, int start, int end, List<Object> values) implements Match {

	@Override
	public GrammarEditor grammar() {
		throw new UnsupportedOperationException();
	}
}
