package de.steffendingel.dynamicparser.spike;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import de.steffendingel.dynamicparser.AmbiguityException;
import de.steffendingel.dynamicparser.SyntaxException;
import de.steffendingel.dynamicparser.spike.Ast.ClassDecl;
import de.steffendingel.dynamicparser.spike.Ast.FunctionDecl;
import de.steffendingel.dynamicparser.spike.Ast.ReturnStatement;
import de.steffendingel.dynamicparser.spike.ExampleLanguage.Compilation;

/**
 * Tests the scenarios of the spike on forward references through deferred parsing of indented blocks (issue #13).
 */
class DeferredParsingTest {

	/** Compiles a source text that must not contain errors and returns it in normalized form. */
	private static String compileValid(String source) throws IndentationException {
		Compilation compilation = ExampleLanguage.compile(source);
		assertEquals(List.of(), compilation.errors());
		return compilation.print();
	}

	/** Compiles a source text that must contain exactly one error and returns it. */
	private static SourceError compileWithError(String source) throws IndentationException {
		List<SourceError> errors = ExampleLanguage.compile(source).errors();
		assertEquals(1, errors.size(), () -> "Errors: " + errors);
		return errors.getFirst();
	}

	private static void assertSyntaxError(int line, int column, String token, SourceError error) {
		SyntaxException exception = assertInstanceOf(SyntaxException.class, error.cause());
		assertEquals(line + ":" + column, error.line() + ":" + error.column(), error::toString);
		assertEquals(token, exception.token().map(String::valueOf).orElse(null));
	}

	// Forward references between top-level functions

	@Test
	void functionCallsAFunctionDeclaredLater() throws IndentationException {
		String source = """
				fun f(): Integer
				    return g(2)
				fun g(n: Integer): Integer
				    return n
				""";

		assertEquals(source, compileValid(source));
	}

	@Test
	void topLevelFunctionsCallEachOther() throws IndentationException {
		String source = """
				fun even(n: Integer): Integer
				    if n
				        return odd(n)
				    return 1
				fun odd(n: Integer): Integer
				    return even(n)
				""";

		assertEquals(source, compileValid(source));
	}

	// Classes that use each other

	private static final String CLASS_A = """
			class A
			    fun f(b: B): Integer
			        return b.g(1)
			""";

	private static final String CLASS_B = """
			class B
			    fun g(n: Integer): Integer
			        return n
			    fun h(a: A, b: B): Integer
			        return a.f(b)
			""";

	@Test
	void classesUseEachOtherInSignaturesAndBodies() throws IndentationException {
		String source = CLASS_A + CLASS_B;

		Compilation compilation = ExampleLanguage.compile(source);

		assertEquals(List.of(), compilation.errors());
		assertEquals(source, compilation.print());
		// The value of the deferred body is attached to the method declaration, with the static type of the result.
		FunctionDecl f = ((ClassDecl) compilation.declarations().getFirst()).methods.getFirst();
		ReturnStatement statement = (ReturnStatement) f.body.getFirst();
		assertEquals("Integer", statement.value().type().name());
		assertEquals("b.g(1)", statement.value().text());
	}

	@Test
	void classesUseEachOtherInTheOtherOrder() throws IndentationException {
		String source = CLASS_B + CLASS_A;

		assertEquals(source, compileValid(source));
	}

	@Test
	void methodCallRequiresTheMethodInTheTypeOfTheReceiver() throws IndentationException {
		String source = """
				class A
				    fun f(b: B): Integer
				        return b.f(b)
				class B
				    fun g(): Integer
				        return 1
				""";

		assertSyntaxError(3, 18, "'f'", compileWithError(source));
	}

	@Test
	void argumentsMustHaveTheParameterTypes() throws IndentationException {
		String source = """
				class A
				    fun f(b: B): Integer
				        return b.g(b)
				class B
				    fun g(n: Integer): Integer
				        return n
				""";

		assertSyntaxError(3, 21, "')'", compileWithError(source));
	}

	@Test
	void returnValueMustHaveTheReturnType() throws IndentationException {
		String source = """
				class A
				    fun f(b: B): Integer
				        return b
				class B
				    fun g(): Integer
				        return 1
				""";

		assertSyntaxError(3, 17, "end of line", compileWithError(source));
	}

	@Test
	void methodCallsALaterMethodOfItsOwnClassWithoutQualification() throws IndentationException {
		String source = """
				class A
				    fun f(): Integer
				        return g(1)
				    fun g(n: Integer): Integer
				        return n
				""";

		assertEquals(source, compileValid(source));
	}

	// Deferred signatures

	@Test
	void signatureOfATopLevelFunctionUsesAClassDeclaredLater() throws IndentationException {
		// The main run only takes the name of f; the rest of the header line is parsed after the main run, when all
		// class names are known.
		String source = """
				fun f(a: A, n: Integer): A
				    return a.g(a)
				class A
				    fun g(a: A): A
				        return f(a, 1)
				""";

		assertEquals(source, compileValid(source));
	}

	@Test
	void errorInADeferredSignatureIsReportedAtItsSourcePosition() throws IndentationException {
		String source = """
				fun g(): Integer
				    return 1
				fun f(n: Integer, u: Unknown): Integer
				    return n
				""";

		Compilation compilation = ExampleLanguage.compile(source);

		assertEquals(1, compilation.errors().size());
		SourceError error = compilation.errors().getFirst();
		assertSyntaxError(3, 22, "'Unknown'", error);
		assertEquals("global", error.scope());
		// The function is not declared and its body is not parsed.
		assertEquals("""
				fun g(): Integer
				    return 1
				fun f <not parsed>
				""", compilation.print());
	}

	@Test
	void incompleteSignatureIsReportedAtTheEndOfTheHeaderLine() throws IndentationException {
		String source = """
				fun f()
				    return 1
				""";

		assertSyntaxError(1, 8, "indented block", compileWithError(source));
	}

	// Visibility

	@Test
	void parametersOfOneBodyAreNotVisibleInAnother() throws IndentationException {
		String source = """
				fun f(p: Integer): Integer
				    return p
				fun g(): Integer
				    return p
				""";

		assertSyntaxError(4, 12, "'p'", compileWithError(source));
	}

	@Test
	void localsOfOneBodyAreNotVisibleInAnother() throws IndentationException {
		String source = """
				class A
				    fun f(): Integer
				        var x = 1
				        return x
				    fun g(): Integer
				        return x
				""";

		assertSyntaxError(6, 16, "'x'", compileWithError(source));
	}

	@Test
	void unqualifiedMembersOfAnotherClassAreNotVisible() throws IndentationException {
		String source = """
				class A
				    fun f(): Integer
				        return g()
				class B
				    fun g(): Integer
				        return 1
				""";

		assertSyntaxError(3, 16, "'g'", compileWithError(source));
	}

	@Test
	void localIsNotVisibleBeforeItsDeclaration() throws IndentationException {
		String source = """
				fun f(): Integer
				    var y = x
				    var x = 1
				    return y
				""";

		assertSyntaxError(2, 13, "'x'", compileWithError(source));
	}

	// A block that is parsed immediately

	@Test
	void ifBlockSeesTheEnclosingDeclarations() throws IndentationException {
		String source = """
				fun f(n: Integer): Integer
				    var a = n
				    if a
				        var x = a
				        if x
				            return g(x, n)
				    return a
				fun g(a: Integer, b: Integer): Integer
				    return b
				""";

		assertEquals(source, compileValid(source));
	}

	@Test
	void variableDeclaredInAnIfBlockIsNotVisibleAfterIt() throws IndentationException {
		String source = """
				fun f(n: Integer): Integer
				    if n
				        var x = n
				        return x
				    return x
				""";

		assertSyntaxError(5, 12, "'x'", compileWithError(source));
	}

	// Deferral inside a deferred block

	@Test
	void localFunctionsCallEachOtherAndUseTheParametersOfTheEnclosingFunction() throws IndentationException {
		String source = """
				fun f(n: Integer): Integer
				    fun even(k: Integer): Integer
				        if k
				            return odd(n)
				        return 1
				    fun odd(k: Integer): Integer
				        return even(n)
				    return even(n)
				""";

		assertEquals(source, compileValid(source));
	}

	@Test
	void localFunctionIsNotVisibleOutsideTheEnclosingFunction() throws IndentationException {
		String source = """
				fun f(n: Integer): Integer
				    fun local(): Integer
				        return n
				    return local()
				fun g(): Integer
				    return local()
				""";

		assertSyntaxError(6, 12, "'local'", compileWithError(source));
	}

	@Test
	void localFunctionHasItsOwnReturnType() throws IndentationException {
		// Relies on the shadowing of the return rule, see ExampleLanguage.functionDeclaration.
		String source = """
				class A
				    fun f(a: A): A
				        fun count(): Integer
				            return a
				        return a
				""";

		assertSyntaxError(4, 20, "'a'", compileWithError(source));
	}

	// Positions and errors

	@Test
	void undeclaredNameInADeferredBodyIsReportedAtItsSourcePosition() throws IndentationException {
		String source = """
				class A
				    fun f(): Integer
				        var x = 1
				        return x
				    fun g(n: Integer): Integer
				        var y = f()
				        return undeclared
				""";

		SourceError error = compileWithError(source);

		assertSyntaxError(7, 16, "'undeclared'", error);
		assertEquals("global > class A > fun g", error.scope());
	}

	@Test
	void syntaxErrorInADeferredBodyIsReportedAtItsSourcePosition() throws IndentationException {
		String source = """
				fun f(): Integer
				    var x = 1
				    var = x
				    return x
				""";

		assertSyntaxError(3, 9, "'='", compileWithError(source));
	}

	@Test
	void errorInAnIfBlockIsReportedAtItsSourcePosition() throws IndentationException {
		String source = """
				fun f(n: Integer): Integer
				    if n
				        if n
				            return n n
				    return n
				""";

		SourceError error = compileWithError(source);

		assertSyntaxError(4, 22, "'n'", error);
		assertEquals("global > fun f > if@2 > if@3", error.scope());
	}

	@Test
	void unexpectedBlockIsReportedAtTheEndOfItsHeaderLine() throws IndentationException {
		String source = """
				fun f(n: Integer): Integer
				    var x = n
				        return n
				""";

		assertSyntaxError(2, 14, "indented block", compileWithError(source));
	}

	@Test
	void otherBlocksAreParsedAfterABlockFailed() throws IndentationException {
		String source = """
				fun f(): Integer
				    return a
				fun g(): Integer
				    return 1
				fun h(): Integer
				    return b
				""";

		Compilation compilation = ExampleLanguage.compile(source);

		assertEquals(List.of("2:12", "6:12"),
				compilation.errors().stream().map(error -> error.line() + ":" + error.column()).toList());
		assertEquals("""
				fun f(): Integer
				    <not parsed>
				fun g(): Integer
				    return 1
				fun h(): Integer
				    <not parsed>
				""", compilation.print());
		assertNull(((FunctionDecl) compilation.declarations().getFirst()).body);
	}

	@Test
	void declarationsBeforeAnErrorInAClassBlockRemain() throws IndentationException {
		// The actions of the matches before the error have been called: f is declared and its body is queued.
		String source = """
				class A
				    fun f(): Integer
				        return 1
				    fun g() Integer
				        return 2
				fun h(a: A): Integer
				    return a.f()
				""";

		Compilation compilation = ExampleLanguage.compile(source);

		assertEquals(List.of("4:13"),
				compilation.errors().stream().map(error -> error.line() + ":" + error.column()).toList());
		assertEquals("""
				class A
				    <not parsed>
				fun h(a: A): Integer
				    return a.f()
				""", compilation.print());
	}

	// Limitations of the model, documented as observations

	@Test
	void localFunctionSeesLocalsDeclaredAfterIt() throws IndentationException {
		// A deferred block refers to its scope by reference and is parsed when the enclosing body is complete, so it
		// sees the locals that are declared after it in the enclosing body.
		String source = """
				fun f(): Integer
				    fun local(): Integer
				        return later
				    var later = 1
				    return local()
				""";

		assertEquals(source, compileValid(source));
	}

	@Test
	void redeclaringAVisibleNameIsAmbiguous() throws IndentationException {
		// The scope chain has no shadowing: both rules for x apply.
		String source = """
				fun f(x: Integer): Integer
				    var x = 2
				    return x
				""";

		SourceError error = compileWithError(source);

		AmbiguityException exception = assertInstanceOf(AmbiguityException.class, error.cause());
		assertEquals("3:12", error.line() + ":" + error.column());
		assertEquals("Expression<Integer>", exception.symbol().name());
	}

	@Test
	void statisticsAreCollected() throws IndentationException {
		Compilation compilation = ExampleLanguage.compile(CLASS_A + CLASS_B);

		// The main input, two class blocks and three method bodies.
		assertEquals(6, compilation.statistics().parseRuns);
		assertTrue(compilation.statistics().maxRulesPerRun > 0);
	}
}
