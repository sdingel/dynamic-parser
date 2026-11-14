package de.steffendingel.dynamicparser;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * A rule of a grammar: a left-hand side, a right-hand side and an action.
 * <p>
 * The rule {@code A -> X1 ... Xn} states that the nonterminal {@code A} on the left-hand side can consist of the
 * sequence of symbols {@code X1} to {@code Xn} on the right-hand side. The right-hand side can be empty. When the
 * parser applies the rule to a section of the input, the {@link Action} of the rule computes the value of this
 * {@link Match}.
 * <p>
 * A rule is immutable and is identified by object identity: two rules with the same left-hand side, right-hand side and
 * action are different rules. If both are in the grammar, every input they apply to is ambiguous.
 */
public final class Rule {

	private final Nonterminal lhs;

	private final List<Symbol> rhs;

	private final Action action;

	/**
	 * Creates a new rule that is different from all existing ones.
	 *
	 * @param lhs    the left-hand side
	 * @param rhs    the right-hand side; may be empty
	 * @param action the action that computes the value of a match of this rule
	 * @throws NullPointerException if an argument or an element of {@code rhs} is {@code null}
	 */
	public Rule(Nonterminal lhs, List<? extends Symbol> rhs, Action action) {
		this.lhs = Objects.requireNonNull(lhs, "lhs");
		this.rhs = List.copyOf(Objects.requireNonNull(rhs, "rhs"));
		this.action = Objects.requireNonNull(action, "action");
	}

	/**
	 * Creates a new rule that is different from all existing ones and whose action returns a {@link ParseNode}
	 * describing the match.
	 *
	 * @param lhs the left-hand side
	 * @param rhs the right-hand side; may be empty
	 * @throws NullPointerException if an argument or an element of {@code rhs} is {@code null}
	 */
	public Rule(Nonterminal lhs, List<? extends Symbol> rhs) {
		this(lhs, rhs, ParseNode::of);
	}

	/**
	 * Returns the left-hand side of this rule.
	 *
	 * @return the left-hand side
	 */
	public Nonterminal lhs() {
		return lhs;
	}

	/**
	 * Returns the right-hand side of this rule.
	 *
	 * @return an unmodifiable list of the symbols; empty for a rule with an empty right-hand side
	 */
	public List<Symbol> rhs() {
		return rhs;
	}

	/**
	 * Returns the action of this rule.
	 *
	 * @return the action
	 */
	public Action action() {
		return action;
	}

	/**
	 * Returns this rule in the form {@code A -> X1 ... Xn}, using the names of the symbols. An empty right-hand side is
	 * shown as {@code ε}.
	 *
	 * @return a description of this rule for messages
	 */
	@Override
	public String toString() {
		if (rhs.isEmpty()) {
			return lhs.name() + " -> ε";
		}
		return rhs.stream().map(Symbol::name).collect(Collectors.joining(" ", lhs.name() + " -> ", ""));
	}
}
