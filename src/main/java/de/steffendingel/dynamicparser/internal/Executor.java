package de.steffendingel.dynamicparser.internal;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;

import de.steffendingel.dynamicparser.AmbiguityException;
import de.steffendingel.dynamicparser.Grammar;

/**
 * Calls the actions of confirmed matches, each one once, in bottom-up, left-to-right order.
 * <p>
 * A match is identified by the {@link Cell} that ends with its node: the node together with the forest before it. The
 * cell records whether the action has been called and the value it returned. This distinguishes several empty matches
 * with the same content, and it finds the value of a match again when it becomes a part of an enclosing match later.
 */
final class Executor {

	/** A match or ambiguous section whose parts are being processed. */
	private static final class Visit {

		final Cell cell;

		final List<Entry> parts;

		final List<Object> values = new ArrayList<>();

		/** The forest before the next part. */
		Cell forest;

		int next;

		Visit(Cell cell) {
			this.cell = cell;
			this.parts = switch (cell.entry) {
			case Node node -> node.children;
			case AmbiguousNode ambiguous -> Forests.entries(ambiguous.common);
			case TokenEntry token -> List.of();
			};
			this.forest = cell.below;
		}
	}

	private final Grammar grammar;

	private final Chart chart;

	private final Forests forests;

	Executor(Grammar grammar, Chart chart, Forests forests) {
		this.grammar = grammar;
		this.chart = chart;
		this.forests = forests;
	}

	/**
	 * Calls the actions of all matches in the given forest that have not been called yet.
	 *
	 * @throws AmbiguityException if the forest contains an ambiguous section; the actions of the matches before it, and
	 *                            of the matches that all ways of matching the section begin with, have been called
	 */
	void run(Cell forest) throws AmbiguityException {
		// Forests share their cells, so the part of the forest that is already settled is found without looking at
		// its entries.
		List<Cell> cells = new ArrayList<>();
		for (Cell cell = forest; cell != null && !cell.settled; cell = cell.below) {
			cells.add(cell);
		}
		Collections.reverse(cells);
		for (Cell cell : cells) {
			run(new Visit(cell));
			cell.settled = true;
		}
	}

	/**
	 * Calls the actions of a match and of the matches within it that have not been called yet. An explicit stack is
	 * used instead of recursion, because matches can be nested as deeply as the input is long.
	 */
	private void run(Visit root) throws AmbiguityException {
		if (root.cell.called || root.cell.entry instanceof TokenEntry) {
			return;
		}
		Deque<Visit> stack = new ArrayDeque<>();
		stack.push(root);
		while (!stack.isEmpty()) {
			Visit visit = stack.peek();
			if (visit.next < visit.parts.size()) {
				// The part is identified by the forest that ends with it.
				Cell part = forests.push(visit.forest, visit.parts.get(visit.next++));
				visit.forest = part;
				if (part.entry instanceof TokenEntry token) {
					visit.values.add(chart.token(token.position));
				} else if (part.called) {
					visit.values.add(part.value);
				} else {
					stack.push(new Visit(part));
				}
			} else {
				stack.pop();
				switch (visit.cell.entry) {
				case Node node -> call(visit.cell, node, visit.values);
				case AmbiguousNode ambiguous ->
					throw new AmbiguityException(ambiguous.symbol, ambiguous.start, ambiguous.end);
				case TokenEntry token -> throw new IllegalStateException("A token has no parts");
				}
				if (!stack.isEmpty()) {
					stack.peek().values.add(visit.cell.value);
				}
			}
		}
	}

	private void call(Cell cell, Node node, List<Object> values) {
		FixedGrammarEditor editor = new FixedGrammarEditor(grammar);
		try {
			cell.value = node.rule.action().apply(
					new MatchImpl(node.rule, node.start, node.end, Collections.unmodifiableList(values), editor));
			cell.called = true;
		} finally {
			editor.close();
		}
	}
}
