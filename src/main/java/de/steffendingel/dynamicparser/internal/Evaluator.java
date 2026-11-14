package de.steffendingel.dynamicparser.internal;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.List;

import de.steffendingel.dynamicparser.AmbiguityException;
import de.steffendingel.dynamicparser.Grammar;
import de.steffendingel.dynamicparser.Nonterminal;
import de.steffendingel.dynamicparser.Rule;
import de.steffendingel.dynamicparser.Symbol;
import de.steffendingel.dynamicparser.Terminal;

/**
 * Computes the value of the input from a finished {@link Chart}: reconstructs the derivation of the input, checks that
 * it is unique and calls the actions in bottom-up, left-to-right order.
 * <p>
 * The evaluation uses an explicit stack instead of recursion, so that deeply nested results, for example long lists
 * defined by a recursive rule, do not overflow the call stack.
 */
final class Evaluator {

	private final Grammar grammar;

	private final Chart chart;

	Evaluator(Grammar grammar, Chart chart) {
		this.grammar = grammar;
		this.chart = chart;
	}

	/**
	 * One way to match a section of the input with a nonterminal: a rule and the positions between the matches of the
	 * symbols of its right-hand side.
	 *
	 * @param rule   the rule
	 * @param bounds the match of the symbol with index {@code i} covers the section from {@code bounds[i]} to
	 *               {@code bounds[i + 1]}
	 */
	private record Derivation(Rule rule, int[] bounds) {
	}

	/**
	 * A match under evaluation: the values of the first symbols of the right-hand side are known, the others are still
	 * to be computed.
	 */
	private static final class Frame {

		final Derivation derivation;

		final Object[] values;

		/** The index of the next symbol of the right-hand side whose value has to be computed. */
		int next;

		Frame(Derivation derivation) {
			this.derivation = derivation;
			this.values = new Object[derivation.rule().rhs().size()];
		}
	}

	/**
	 * Computes the value of the complete input.
	 *
	 * @return the value returned by the action of the match of the start symbol
	 * @throws AmbiguityException if a section of the input that is part of the result can be derived in more than one
	 *                            way
	 */
	Object evaluate() throws AmbiguityException {
		Deque<Frame> stack = new ArrayDeque<>();
		stack.push(new Frame(derivation(grammar.start(), 0, chart.tokens().size())));
		while (true) {
			Frame frame = stack.peek();
			List<Symbol> rhs = frame.derivation.rule().rhs();
			int[] bounds = frame.derivation.bounds();
			if (frame.next < rhs.size()) {
				int index = frame.next++;
				switch (rhs.get(index)) {
				case Terminal terminal -> frame.values[index] = chart.tokens().get(bounds[index]);
				case Nonterminal nonterminal ->
					stack.push(new Frame(derivation(nonterminal, bounds[index], bounds[index + 1])));
				}
			} else {
				Object value = callAction(frame);
				stack.pop();
				if (stack.isEmpty()) {
					return value;
				}
				Frame parent = stack.peek();
				parent.values[parent.next - 1] = value;
			}
		}
	}

	private Object callAction(Frame frame) {
		Rule rule = frame.derivation.rule();
		int[] bounds = frame.derivation.bounds();
		List<Object> values = Collections.unmodifiableList(Arrays.asList(frame.values));
		FixedGrammarEditor editor = new FixedGrammarEditor(grammar);
		try {
			return rule.action().apply(new MatchImpl(rule, bounds[0], bounds[bounds.length - 1], values, editor));
		} finally {
			editor.close();
		}
	}

	/**
	 * Returns the only way to match the section from {@code start} to {@code end} with the given nonterminal.
	 *
	 * @throws AmbiguityException if there is more than one way
	 */
	private Derivation derivation(Nonterminal nonterminal, int start, int end) throws AmbiguityException {
		List<Derivation> derivations = new ArrayList<>(2);
		for (Rule rule : chart.set(end).completedRules(nonterminal, start)) {
			int size = rule.rhs().size();
			int[] bounds = new int[size + 1];
			bounds[size] = end;
			collectDerivations(rule, size, start, bounds, derivations);
			if (derivations.size() > 1) {
				throw new AmbiguityException(nonterminal, start, end);
			}
		}
		if (derivations.isEmpty()) {
			throw new IllegalStateException(
					"No derivation of " + nonterminal + " from position " + start + " to position " + end);
		}
		return derivations.get(0);
	}

	/**
	 * Collects the ways in which the first {@code count} symbols of the right-hand side of a rule match the section
	 * from {@code start} to {@code bounds[count]}, going from right to left. The chart is known to contain the item for
	 * the rule with {@code count} matched symbols at the position {@code bounds[count]}; this guarantees that every
	 * step of the search leads to at least one derivation. The search stops as soon as two derivations are found,
	 * because that is enough to detect an ambiguity.
	 */
	private void collectDerivations(Rule rule, int count, int start, int[] bounds, List<Derivation> derivations) {
		if (derivations.size() > 1) {
			return;
		}
		if (count == 0) {
			bounds[0] = start;
			derivations.add(new Derivation(rule, bounds.clone()));
			return;
		}
		int end = bounds[count];
		switch (rule.rhs().get(count - 1)) {
		case Terminal terminal -> {
			// The item was created by matching the token before the position.
			bounds[count - 1] = end - 1;
			collectDerivations(rule, count - 1, start, bounds, derivations);
		}
		case Nonterminal nonterminal -> {
			Item previous = new Item(rule, count - 1, start);
			for (int middle : chart.set(end).completedOrigins(nonterminal)) {
				if (chart.set(middle).contains(previous)) {
					bounds[count - 1] = middle;
					collectDerivations(rule, count - 1, start, bounds, derivations);
				}
			}
		}
		}
	}
}
