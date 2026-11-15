package de.steffendingel.dynamicparser.spike;

import java.util.List;

/**
 * A non-blank line of the source text together with the block of lines indented below it.
 *
 * @param number      the line number in the source text, starting at 1
 * @param indentation the leading whitespace of the line, as it appears in the source text
 * @param text        the rest of the line after the indentation
 * @param children    the lines of the block of this line, in source order; empty if the line has no block
 */
record Line(int number, String indentation, String text, List<Line> children) {

	Line {
		children = List.copyOf(children);
	}

	/**
	 * Returns the column at which {@link #text()} begins, starting at 1. Every character counts as one column,
	 * including a tab.
	 */
	int textColumn() {
		return indentation.length() + 1;
	}
}
