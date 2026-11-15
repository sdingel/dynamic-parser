package de.steffendingel.dynamicparser.spike;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * Tests the structure reader: lines and their nesting by indentation.
 */
class StructureReaderTest {

	/** Describes lines as an outline: line number and text, children in brackets. */
	private static String outline(List<Line> lines) {
		StringBuilder out = new StringBuilder();
		for (Line line : lines) {
			if (!out.isEmpty()) {
				out.append(", ");
			}
			out.append(line.number()).append(':').append(line.text());
			if (!line.children().isEmpty()) {
				out.append(" [").append(outline(line.children())).append(']');
			}
		}
		return out.toString();
	}

	private static String read(String text) throws IndentationException {
		return outline(StructureReader.read(text));
	}

	@Test
	void nestsOverSeveralLevels() throws IndentationException {
		String text = """
				a
				  b
				    c
				      d
				    e
				  f
				g
				""";

		assertEquals("1:a [2:b [3:c [4:d], 5:e], 6:f], 7:g", read(text));
	}

	@Test
	void ignoresBlankLines() throws IndentationException {
		String text = "\na\n\n  b\n   \n\t\n  c\n\nd\n\n";

		assertEquals("2:a [4:b, 7:c], 9:d", read(text));
	}

	@Test
	void blankLineDoesNotEndABlock() throws IndentationException {
		assertEquals("1:a [2:b [4:c]]", read("a\n  b\n\n    c"));
	}

	@Test
	void keepsIndentationAndColumns() throws IndentationException {
		Line line = StructureReader.read("a\n\t  b c").getFirst().children().getFirst();

		assertEquals("\t  ", line.indentation());
		assertEquals("b c", line.text());
		assertEquals(4, line.textColumn());
	}

	@Test
	void acceptsCarriageReturnLineFeed() throws IndentationException {
		assertEquals("1:a [2:b], 3:c", read("a\r\n  b\r\nc\r\n"));
	}

	@Test
	void differentBlocksMayUseTabsOrSpaces() throws IndentationException {
		String text = "a\n\tb\n\tc\nd\n    e\n    f";

		assertEquals("1:a [2:b, 3:c], 4:d [5:e, 6:f]", read(text));
	}

	@Test
	void nestedIndentationExtendsTheEnclosingOneAsAPrefix() throws IndentationException {
		String text = "a\n\tb\n\t    c\n\t    d\n\te";

		assertEquals("1:a [2:b [3:c, 4:d], 5:e]", read(text));
	}

	@Test
	void widthOfATabDoesNotMatter() {
		// A tab is not the same as any number of spaces: the two lines are not in the same block.
		IndentationException exception = assertThrows(IndentationException.class, () -> read("a\n\tb\n    c"));

		assertEquals(3, exception.line());
	}

	@Test
	void linesOfABlockMustHaveIdenticalIndentation() {
		IndentationException exception = assertThrows(IndentationException.class, () -> read("a\n    b\n  c"));

		assertEquals(3, exception.line());
	}

	@Test
	void dedentMustReturnToAnEnclosingIndentation() {
		String text = "a\n    b\n        c\n      d";

		IndentationException exception = assertThrows(IndentationException.class, () -> read(text));

		assertEquals(4, exception.line());
	}

	@Test
	void firstLineMustNotBeIndented() {
		IndentationException exception = assertThrows(IndentationException.class, () -> read("\n  a\nb"));

		assertEquals(2, exception.line());
	}

	@Test
	void emptyTextHasNoLines() throws IndentationException {
		assertEquals(List.of(), StructureReader.read(""));
		assertEquals(List.of(), StructureReader.read("\n \n\t\n"));
	}
}
