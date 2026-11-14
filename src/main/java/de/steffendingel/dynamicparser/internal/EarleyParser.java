package de.steffendingel.dynamicparser.internal;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import de.steffendingel.dynamicparser.Grammar;
import de.steffendingel.dynamicparser.Nonterminal;
import de.steffendingel.dynamicparser.ParseException;
import de.steffendingel.dynamicparser.Parser;
import de.steffendingel.dynamicparser.Rule;

/**
 * A parser based on Earley's algorithm.
 * <p>
 * A parse run has two phases: the {@link Chart} recognizes the complete input, then the {@link Evaluator} reconstructs
 * the derivation from the chart and calls the actions. Instances are created with {@link Parser#create(Grammar)}.
 */
public final class EarleyParser implements Parser {

	private final Grammar grammar;

	/** The rules of the grammar by their left-hand side, in the order of the grammar. Never modified. */
	private final Map<Nonterminal, List<Rule>> rules = new HashMap<>();

	/**
	 * Creates a parser for the given grammar.
	 *
	 * @param grammar the grammar
	 * @throws NullPointerException if {@code grammar} is {@code null}
	 */
	public EarleyParser(Grammar grammar) {
		this.grammar = Objects.requireNonNull(grammar, "grammar");
		for (Rule rule : grammar.rules()) {
			rules.computeIfAbsent(rule.lhs(), key -> new ArrayList<>()).add(rule);
		}
	}

	@Override
	public Object parse(Iterable<?> tokens) throws ParseException {
		Objects.requireNonNull(tokens, "tokens");
		List<Object> tokenList = new ArrayList<>();
		for (Object token : tokens) {
			tokenList.add(Objects.requireNonNull(token, "token"));
		}
		Chart chart = Chart.build(rules, grammar.start(), tokenList);
		return new Evaluator(grammar, chart).evaluate();
	}
}
