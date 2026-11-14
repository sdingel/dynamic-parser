package de.steffendingel.dynamicparser;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * A node of a generic parse tree: describes one {@link Match}.
 * <p>
 * Parse nodes are the values of rules that were created without an action, see {@link Rule#Rule(Nonterminal, List)}. If
 * all rules of a grammar are created that way, the parser returns the complete parse tree: the children of a node are
 * then the tokens matched by the terminals and the parse nodes of the nonterminals on the right-hand side of the rule.
 *
 * @param rule     the rule that was applied
 * @param start    the position at which the matched section of the input begins, see {@link Match#start()}
 * @param end      the position at which the matched section of the input ends, see {@link Match#end()}
 * @param children the values of the symbols on the right-hand side of the rule, see {@link Match#values()};
 *                 unmodifiable, elements may be {@code null}
 */
public record ParseNode(Rule rule, int start, int end, List<Object> children) {

	/**
	 * Creates a parse node.
	 *
	 * @throws NullPointerException     if {@code rule} or {@code children} is {@code null}
	 * @throws IllegalArgumentException if {@code start} is negative, if {@code end} is less than {@code start} or if
	 *                                  the number of children differs from the number of symbols on the right-hand side
	 *                                  of the rule
	 */
	public ParseNode {
		Objects.requireNonNull(rule, "rule");
		Objects.requireNonNull(children, "children");
		if (start < 0 || end < start) {
			throw new IllegalArgumentException("Invalid positions: start " + start + ", end " + end);
		}
		if (children.size() != rule.rhs().size()) {
			throw new IllegalArgumentException("Rule " + rule + " has " + rule.rhs().size() + " symbols, but there are "
					+ children.size() + " children");
		}
		// List.copyOf is not used because values of matches may be null.
		children = Collections.unmodifiableList(new ArrayList<>(children));
	}

	/**
	 * Creates the parse node describing a match. This method can be used as an {@link Action}.
	 *
	 * @param match the match
	 * @return a parse node with the rule, the positions and the values of the match
	 * @throws NullPointerException if {@code match} is {@code null}
	 */
	public static ParseNode of(Match match) {
		return new ParseNode(match.rule(), match.start(), match.end(), match.values());
	}
}
