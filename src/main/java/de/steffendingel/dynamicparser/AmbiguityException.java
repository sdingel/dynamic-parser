package de.steffendingel.dynamicparser;

import java.io.Serial;
import java.util.Objects;

/**
 * Thrown when the input matches the grammar in more than one way: a section of the input that is part of the result can
 * be derived from a nonterminal in several different ways.
 * <p>
 * The actions of the matches within the ambiguous section are not called. {@link #position()} is the start position of
 * the ambiguous section.
 */
public final class AmbiguityException extends ParseException {

	@Serial
	private static final long serialVersionUID = 1L;

	// Symbols are not serializable, so this field is lost on serialization.
	private final transient Nonterminal symbol;

	/** The position at which the ambiguous section ends. */
	private final int end;

	/**
	 * Creates an ambiguity exception.
	 *
	 * @param symbol the nonterminal that matches the section in more than one way
	 * @param start  the position at which the ambiguous section begins
	 * @param end    the position at which the ambiguous section ends
	 * @throws NullPointerException     if {@code symbol} is {@code null}
	 * @throws IllegalArgumentException if {@code start} is negative or {@code end} is less than {@code start}
	 */
	public AmbiguityException(Nonterminal symbol, int start, int end) {
		super("Ambiguous input: " + Objects.requireNonNull(symbol, "symbol") + " matches the section from position "
				+ start + " to position " + end + " in more than one way", start);
		if (end < start) {
			throw new IllegalArgumentException("Invalid positions: start " + start + ", end " + end);
		}
		this.symbol = symbol;
		this.end = end;
	}

	/**
	 * Returns the nonterminal that matches the section in more than one way.
	 *
	 * @return the nonterminal
	 */
	public Nonterminal symbol() {
		return symbol;
	}

	/**
	 * Returns the position at which the ambiguous section begins. Same as {@link #position()}.
	 *
	 * @return the start position
	 */
	public int start() {
		return position();
	}

	/**
	 * Returns the position at which the ambiguous section ends.
	 *
	 * @return the end position
	 */
	public int end() {
		return end;
	}
}
