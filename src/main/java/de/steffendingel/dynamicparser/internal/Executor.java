package de.steffendingel.dynamicparser.internal;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;

import de.steffendingel.dynamicparser.AmbiguityException;

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

	private final RunGrammar grammar;

	private final Chart chart;

	private final Forests forests;

	/** The forest that ends with the match whose action was called last. */
	private Cell last;

	Executor(RunGrammar grammar, Chart chart, Forests forests) {
		this.grammar = grammar;
		this.chart = chart;
		this.forests = forests;
	}

	/**
	 * Returns the forest of all matches whose actions have been called, and of the tokens before and between them. Its
	 * last entry is the match whose action was called last.
	 */
	Cell last() {
		return last;
	}

	/**
	 * Calls the actions of the matches in the given forest that have not been called yet, until one of them changes the
	 * grammar.
	 *
	 * @return {@code true} if an action has changed the grammar. The remaining actions have not been called then,
	 *         because the forest was determined with the grammar before the change.
	 * @throws AmbiguityException if the forest contains an ambiguous section; the actions of the matches before it, and
	 *                            of the matches that all ways of matching the section begin with, have been called
	 */
	boolean run(Cell forest) throws AmbiguityException {
		// Forests share their cells, so the part of the forest that is already settled is found without looking at
		// its entries.
		List<Cell> cells = new ArrayList<>();
		for (Cell cell = forest; cell != null && !cell.settled; cell = cell.below) {
			cells.add(cell);
		}
		Collections.reverse(cells);
		for (Cell cell : cells) {
			if (run(new Visit(cell))) {
				return true;
			}
			cell.settled = true;
		}
		return false;
	}

	/**
	 * Calls the actions of a match and of the matches within it that have not been called yet. An explicit stack is
	 * used instead of recursion, because matches can be nested as deeply as the input is long.
	 *
	 * @return {@code true} if an action has changed the grammar
	 */
	private boolean run(Visit root) throws AmbiguityException {
		if (root.cell.called || root.cell.entry instanceof TokenEntry) {
			return false;
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
				case Node node -> {
					if (call(visit.cell, node, visit.values)) {
						return true;
					}
				}
				case AmbiguousNode ambiguous ->
					throw new AmbiguityException(ambiguous.symbol, ambiguous.start, ambiguous.end);
				case TokenEntry token -> throw new IllegalStateException("A token has no parts");
				}
				if (!stack.isEmpty()) {
					stack.peek().values.add(visit.cell.value);
				}
			}
		}
		return false;
	}

	/**
	 * Calls the action of a match.
	 *
	 * @return {@code true} if the action has changed the grammar
	 */
	private boolean call(Cell cell, Node node, List<Object> values) {
		int changes = grammar.changes();
		RunEditor editor = new RunEditor(grammar, node.rule, node.end, chart.position());
		try {
			cell.value = node.rule.action().apply(
					new MatchImpl(node.rule, node.start, node.end, Collections.unmodifiableList(values), editor));
			cell.called = true;
			last = cell;
		} finally {
			editor.close();
		}
		return grammar.changes() != changes;
	}
}
