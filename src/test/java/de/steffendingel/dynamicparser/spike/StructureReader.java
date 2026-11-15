package de.steffendingel.dynamicparser.spike;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Splits a source text into lines and derives the nesting of the lines from their indentation. Knows nothing about
 * the grammar.
 * <p>
 * Provisional rules of the spike:
 * <ul>
 * <li>The indentation of a line is its leading run of spaces and tabs, taken as a string.</li>
 * <li>A line belongs to the block of a preceding line if that line's indentation is a proper prefix of its own. Columns
 * are never counted, so the width of a tab does not matter.</li>
 * <li>All lines of one block have identical indentation. Top-level lines are not indented.</li>
 * <li>Blank lines (empty or whitespace only) are ignored.</li>
 * <li>Any other indentation is an error. There are no continuation lines and no multi-line literals.</li>
 * </ul>
 */
final class StructureReader {

	private StructureReader() {
	}

	/** A line under construction: its children are still being collected. */
	private record Open(int number, String indentation, String text, List<Open> children) {

		Line close() {
			return new Line(number, indentation, text, children.stream().map(Open::close).toList());
		}
	}

	/** A block under construction: the common indentation of its lines and the lines read so far. */
	private record Frame(String indentation, List<Open> lines) {
	}

	/**
	 * Reads the top-level lines of a source text, each with its nested block.
	 *
	 * @throws IndentationException if the indentation of a line is inconsistent with the lines before it
	 */
	static List<Line> read(String text) throws IndentationException {
		Deque<Frame> frames = new ArrayDeque<>();
		Frame root = new Frame("", new ArrayList<>());
		frames.push(root);
		String[] rawLines = text.split("\n", -1);
		for (int i = 0; i < rawLines.length; i++) {
			String raw = rawLines[i].endsWith("\r") ? rawLines[i].substring(0, rawLines[i].length() - 1) : rawLines[i];
			int number = i + 1;
			if (raw.isBlank()) {
				continue;
			}
			int end = 0;
			while (end < raw.length() && (raw.charAt(end) == ' ' || raw.charAt(end) == '\t')) {
				end++;
			}
			String indentation = raw.substring(0, end);
			Open line = new Open(number, indentation, raw.substring(end), new ArrayList<>());
			while (true) {
				Frame top = frames.peek();
				if (indentation.equals(top.indentation())) {
					top.lines().add(line);
					break;
				}
				if (indentation.startsWith(top.indentation())) {
					// A proper extension: the line opens the block of the last line of the current block.
					if (top.lines().isEmpty()) {
						throw new IndentationException(number, "indented line without a preceding line");
					}
					Open parent = top.lines().getLast();
					if (!parent.children().isEmpty()) {
						throw new IndentationException(number, "inconsistent indentation: the block of line "
								+ parent.number() + " is indented differently");
					}
					parent.children().add(line);
					frames.push(new Frame(indentation, parent.children()));
					break;
				}
				// The line does not continue the current block. The root frame has the empty indentation, which is a
				// prefix of every indentation, so it is never popped.
				frames.pop();
			}
		}
		return root.lines().stream().map(Open::close).toList();
	}
}
