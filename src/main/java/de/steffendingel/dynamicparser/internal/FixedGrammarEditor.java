package de.steffendingel.dynamicparser.internal;

import java.util.Objects;

import de.steffendingel.dynamicparser.Grammar;
import de.steffendingel.dynamicparser.GrammarEditor;
import de.steffendingel.dynamicparser.Rule;

/**
 * The grammar editor of a parser that does not support changing the grammar yet: rules can be looked up but neither
 * added nor removed.
 */
final class FixedGrammarEditor implements GrammarEditor {

	private final Grammar grammar;

	private boolean closed;

	FixedGrammarEditor(Grammar grammar) {
		this.grammar = grammar;
	}

	/**
	 * Marks the action this editor was passed to as finished. The editor cannot be used afterwards.
	 */
	void close() {
		closed = true;
	}

	@Override
	public boolean add(Rule rule) {
		check(rule);
		throw new UnsupportedOperationException("Adding rules while parsing is not implemented yet");
	}

	@Override
	public boolean remove(Rule rule) {
		check(rule);
		throw new UnsupportedOperationException("Removing rules while parsing is not implemented yet");
	}

	@Override
	public boolean contains(Rule rule) {
		check(rule);
		return grammar.rules().contains(rule);
	}

	private void check(Rule rule) {
		Objects.requireNonNull(rule, "rule");
		if (closed) {
			throw new IllegalStateException("The action this grammar editor was passed to has already returned");
		}
	}
}
