package de.steffendingel.dynamicparser.internal;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import de.steffendingel.dynamicparser.Grammar;
import de.steffendingel.dynamicparser.Nonterminal;
import de.steffendingel.dynamicparser.ParseException;
import de.steffendingel.dynamicparser.Parser;
import de.steffendingel.dynamicparser.Rule;
import de.steffendingel.dynamicparser.SyntaxException;

/**
 * A parser based on Earley's algorithm.
 * <p>
 * A parse run processes the input token by token. At every position, the {@link Chart} first computes the item set,
 * then the {@link Analyzer} determines what all readings of the input read so far have in common, and the
 * {@link Executor} calls the actions of the matches confirmed by that. Only then is the next token read. Instances are
 * created with {@link Parser#create(Grammar)}.
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
		Iterator<?> iterator = Objects.requireNonNull(tokens, "tokens").iterator();
		Nonterminal start = grammar.start();
		Chart chart = new Chart(rules, start);
		Forests forests = new Forests();
		Analyzer analyzer = new Analyzer(chart, forests, start);
		Executor executor = new Executor(grammar, chart, forests);
		while (true) {
			int position = chart.position();
			analyzer.analyze(position);
			History common = analyzer.common(position, false);
			if (common != null) {
				executor.run(common.common());
			}
			if (!iterator.hasNext()) {
				break;
			}
			Object token = Objects.requireNonNull(iterator.next(), "token");
			if (!chart.advance(token)) {
				throw new SyntaxException(position, token, chart.set(position).expectedTerminals());
			}
		}
		int end = chart.position();
		History result = analyzer.common(end, true);
		if (result == null) {
			throw new SyntaxException(end, null, chart.set(end).expectedTerminals());
		}
		executor.run(result.common());
		return result.common().value;
	}
}
