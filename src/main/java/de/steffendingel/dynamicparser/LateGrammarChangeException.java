package de.steffendingel.dynamicparser;

import java.io.Serial;
import java.util.Objects;

/**
 * Thrown by {@link GrammarEditor#add(Rule)} and {@link GrammarEditor#remove(Rule)} when an action tries to change the
 * grammar too late.
 * <p>
 * A change of the grammar applies to the input that follows the match of the action. The action must therefore run
 * before the parser reads the token following the match. That is not possible if the parser needs more input to be
 * certain that the match is part of the result; then the parser has already processed tokens with the old grammar when
 * the action runs.
 * <p>
 * This exception indicates a defect of the grammar, not of the input: the rule must be changed so that its matches are
 * certain when they end, for example by ending the rule with a terminal that cannot be continued. For this reason it is
 * an unchecked exception and not a {@link ParseException}. If the action does not catch it, it aborts parsing and is
 * passed on to the caller of {@link Parser#parse(Iterable)} like any other exception thrown by an action.
 */
public final class LateGrammarChangeException extends RuntimeException {

	@Serial
	private static final long serialVersionUID = 1L;

	// Rules are not serializable, so this field is lost on serialization.
	private final transient Rule rule;

	/** The end position of the match of the rule. */
	private final int matchEnd;

	/** The position the parser had reached when the action ran. */
	private final int position;

	/**
	 * Creates a late grammar change exception.
	 *
	 * @param rule     the rule whose action tried to change the grammar
	 * @param matchEnd the end position of the match of the rule
	 * @param position the position the parser had reached when the action ran
	 * @throws NullPointerException     if {@code rule} is {@code null}
	 * @throws IllegalArgumentException if {@code matchEnd} is negative or {@code position} is not greater than
	 *                                  {@code matchEnd}
	 */
	public LateGrammarChangeException(Rule rule, int matchEnd, int position) {
		super("The action of the rule " + Objects.requireNonNull(rule, "rule")
				+ " cannot change the grammar: its match ends at position " + matchEnd
				+ ", but it was only certain at position " + position);
		if (matchEnd < 0 || position <= matchEnd) {
			throw new IllegalArgumentException("Invalid positions: match end " + matchEnd + ", position " + position);
		}
		this.rule = rule;
		this.matchEnd = matchEnd;
		this.position = position;
	}

	/**
	 * Returns the rule whose action tried to change the grammar.
	 *
	 * @return the rule
	 */
	public Rule rule() {
		return rule;
	}

	/**
	 * Returns the end position of the match of the rule: the position from which the change would have had to apply.
	 *
	 * @return the end position of the match
	 */
	public int matchEnd() {
		return matchEnd;
	}

	/**
	 * Returns the position the parser had reached when the action ran.
	 *
	 * @return the position, greater than {@link #matchEnd()}
	 */
	public int position() {
		return position;
	}
}
