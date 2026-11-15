package de.steffendingel.dynamicparser.internal;

import java.util.Iterator;
import java.util.Objects;

import de.steffendingel.dynamicparser.Grammar;
import de.steffendingel.dynamicparser.Nonterminal;
import de.steffendingel.dynamicparser.ParseException;
import de.steffendingel.dynamicparser.Parser;
import de.steffendingel.dynamicparser.SyntaxException;

/**
 * A parser based on Earley's algorithm.
 * <p>
 * A parse run processes the input token by token. At every position, the {@link Chart} first computes the item set,
 * then the {@link Analyzer} determines what all readings of the input read so far have in common, and the
 * {@link Executor} calls the actions of the matches confirmed by that. If an action changes the grammar, the item set
 * is rebuilt with the new rules from the items that are consistent with the actions called so far (see
 * {@link Commitments}), and the analysis is repeated. Only then is the next token read. Instances are created with
 * {@link Parser#create(Grammar)}.
 */
public final class EarleyParser implements Parser {

	private final Grammar grammar;

	/**
	 * Creates a parser for the given grammar.
	 *
	 * @param grammar the grammar
	 * @throws NullPointerException if {@code grammar} is {@code null}
	 */
	public EarleyParser(Grammar grammar) {
		this.grammar = Objects.requireNonNull(grammar, "grammar");
	}

	@Override
	public Object parse(Iterable<?> tokens) throws ParseException {
		Iterator<?> iterator = Objects.requireNonNull(tokens, "tokens").iterator();
		Nonterminal start = grammar.start();
		RunGrammar rules = new RunGrammar(grammar);
		Chart chart = new Chart(rules, start);
		Forests forests = new Forests();
		Commitments commitments = new Commitments(chart, forests, start);
		chart.setParents(commitments);
		Analyzer analyzer = new Analyzer(chart, forests, start, commitments);
		Executor executor = new Executor(rules, chart, forests);
		boolean endOfInput = false;
		while (true) {
			analyzer.analyze(chart.current());
			History common = analyzer.common(chart.current(), endOfInput);
			if (common == null) {
				if (endOfInput) {
					throw new SyntaxException(chart.position(), null, chart.set(chart.current()).expectedTerminals());
				}
			} else if (executor.run(common.common())) {
				// An action has changed the grammar. Continue with what is consistent with the actions called so
				// far, and determine again what is confirmed.
				chart.restart(commitments.restart(executor.last()));
				continue;
			} else if (endOfInput) {
				return common.common().value;
			}
			if (iterator.hasNext()) {
				Object token = Objects.requireNonNull(iterator.next(), "token");
				if (!chart.advance(token)) {
					throw new SyntaxException(chart.position(), token, chart.set(chart.current()).expectedTerminals());
				}
			} else {
				// The item set is analyzed once more, now for the readings that accept the end of the input.
				endOfInput = true;
			}
		}
	}
}
