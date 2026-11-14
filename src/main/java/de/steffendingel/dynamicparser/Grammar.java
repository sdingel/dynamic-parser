package de.steffendingel.dynamicparser;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/**
 * The initial grammar of a parser: a start symbol and a set of rules.
 * <p>
 * A grammar is immutable and can be used by any number of parsers and parse runs. While parsing, actions can add and
 * remove rules through a {@link GrammarEditor}; such changes affect only the running parse and never this object.
 * <p>
 * A nonterminal does not need to have rules in the initial grammar, because actions can add them later. Input can only
 * be parsed if the start symbol has at least one rule.
 */
public final class Grammar {

	private final Nonterminal start;

	// Rules are identified by object identity. Rule does not override equals and hashCode, so a regular set works.
	private final Set<Rule> rules;

	/**
	 * Creates a grammar.
	 *
	 * @param start the start symbol: the nonterminal that the complete input must match
	 * @param rules the rules of the grammar; may be empty
	 * @throws NullPointerException     if an argument or an element of {@code rules} is {@code null}
	 * @throws IllegalArgumentException if {@code rules} contains the same rule more than once
	 */
	public Grammar(Nonterminal start, Collection<Rule> rules) {
		this.start = Objects.requireNonNull(start, "start");
		Objects.requireNonNull(rules, "rules");
		Set<Rule> set = new LinkedHashSet<>();
		for (Rule rule : rules) {
			if (!set.add(Objects.requireNonNull(rule, "rule"))) {
				throw new IllegalArgumentException("Duplicate rule: " + rule);
			}
		}
		this.rules = Collections.unmodifiableSet(set);
	}

	/**
	 * Creates a grammar.
	 *
	 * @param start the start symbol: the nonterminal that the complete input must match
	 * @param rules the rules of the grammar; may be empty
	 * @throws NullPointerException     if an argument or an element of {@code rules} is {@code null}
	 * @throws IllegalArgumentException if {@code rules} contains the same rule more than once
	 */
	public Grammar(Nonterminal start, Rule... rules) {
		this(start, Arrays.asList(Objects.requireNonNull(rules, "rules")));
	}

	/**
	 * Returns the start symbol.
	 *
	 * @return the start symbol
	 */
	public Nonterminal start() {
		return start;
	}

	/**
	 * Returns the rules of this grammar.
	 *
	 * @return an unmodifiable set of the rules, in the order in which they were passed to the constructor
	 */
	public Set<Rule> rules() {
		return rules;
	}
}
