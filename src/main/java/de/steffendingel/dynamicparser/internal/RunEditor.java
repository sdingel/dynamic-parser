package de.steffendingel.dynamicparser.internal;

import java.util.Objects;

import de.steffendingel.dynamicparser.GrammarEditor;
import de.steffendingel.dynamicparser.LateGrammarChangeException;
import de.steffendingel.dynamicparser.Rule;

/**
 * The grammar editor passed to one call of an action.
 */
final class RunEditor implements GrammarEditor {

	private final RunGrammar grammar;

	private final Rule rule;

	private final int matchEnd;

	private final int position;

	private boolean closed;

	/**
	 * Creates the editor for a call of the action of the given rule.
	 *
	 * @param matchEnd the end position of the match
	 * @param position the position the parser has reached
	 */
	RunEditor(RunGrammar grammar, Rule rule, int matchEnd, int position) {
		this.grammar = grammar;
		this.rule = rule;
		this.matchEnd = matchEnd;
		this.position = position;
	}

	/**
	 * Marks the action this editor was passed to as finished. The editor cannot be used afterwards.
	 */
	void close() {
		closed = true;
	}

	@Override
	public boolean add(Rule added) {
		checkChange(added);
		return grammar.add(added);
	}

	@Override
	public boolean remove(Rule removed) {
		checkChange(removed);
		return grammar.remove(removed);
	}

	@Override
	public boolean contains(Rule other) {
		check(other);
		return grammar.contains(other);
	}

	private void checkChange(Rule other) {
		check(other);
		if (position > matchEnd) {
			// The parser has read tokens after the match with the grammar as it was.
			throw new LateGrammarChangeException(rule, matchEnd, position);
		}
	}

	private void check(Rule other) {
		Objects.requireNonNull(other, "rule");
		if (closed) {
			throw new IllegalStateException("The action this grammar editor was passed to has already returned");
		}
	}
}
