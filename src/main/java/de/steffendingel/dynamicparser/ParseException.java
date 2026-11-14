package de.steffendingel.dynamicparser;

import java.io.Serial;

/**
 * Thrown when a sequence of tokens cannot be parsed: either the input does not match the grammar
 * ({@link SyntaxException}) or it matches in more than one way ({@link AmbiguityException}).
 */
public abstract sealed class ParseException extends Exception permits SyntaxException, AmbiguityException {

	@Serial
	private static final long serialVersionUID = 1L;

	/** The position in the input this exception refers to. */
	private final int position;

	/**
	 * Creates a parse exception.
	 *
	 * @param message  the detail message
	 * @param position the position in the input the exception refers to, see {@link #position()}
	 * @throws IllegalArgumentException if {@code position} is negative
	 */
	ParseException(String message, int position) {
		super(message);
		if (position < 0) {
			throw new IllegalArgumentException("Negative position: " + position);
		}
		this.position = position;
	}

	/**
	 * Returns the position in the input this exception refers to. Positions lie between tokens: position 0 is before
	 * the first token, position {@code n} after the n-th token.
	 *
	 * @return the position
	 */
	public int position() {
		return position;
	}
}
