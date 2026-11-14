# Architecture

This document specifies what the parser does and records the fundamental design decisions with their reasoning. The
sections up to [Public API](#public-api) describe the behavior independently of the algorithm;
[Implementation](#implementation) describes how the parser works and which parts of the specified behavior are not
implemented yet.

## Purpose

dynamic-parser is a generic parser whose grammar can be modified while parsing.

It is intended for extensible programming languages: languages in which new constructs can be defined within the
language itself. One idea to be explored with it is that every declaration adds grammar rules. For example, declaring
a variable `x` of type `Integer` adds a rule saying that `x` may appear wherever an `Integer` expression is expected.
Keywords and identifiers are then no longer distinguished. This is an experimental project.

## Terminology

- **Token:** One element of the input. Tokens are arbitrary objects supplied by the caller.
- **Position:** A place between tokens. Position 0 is before the first token, position *n* after the *n*-th token.
- **Symbol:** A terminal or a nonterminal.
- **Terminal:** A symbol that stands for a single token. A terminal decides for each token whether it matches.
- **Nonterminal:** A symbol that is defined by rules.
- **Rule:** `A -> X1 … Xn` with a nonterminal `A` (left-hand side) and a possibly empty sequence of symbols
  (right-hand side).
- **Grammar:** A start symbol and a set of rules. The set of rules changes while parsing.
- **Match:** The application of a rule to a section of the input, from a start position to an end position, together
  with the matches of the nonterminals on its right-hand side. An *empty match* has equal start and end positions.
- **Result:** The match of the start symbol that covers the complete input. With all the matches it contains, it
  forms a tree.
- **Action:** The callback of a rule. It computes the value of a match and can change the grammar.
- **Value:** The value of a terminal is the matched token. The value of a match is what its action returns.
- **Confirmed:** A match is confirmed once it is certain that it is part of the result. See
  [Confirmed matches](#confirmed-matches).

## Decisions

### 1. Generic parser

The parser receives its grammar at runtime through the API. It is not a parser generator: nothing is generated for a
specific grammar. Rules have the classical form `A -> X1 … Xn` with n ≥ 0. The start symbol is fixed for a grammar.

### 2. Symbols and rules are identified by object identity

Two symbols with the same name are different symbols, and two rules with the same content are different rules. Names
are only used in messages.

Reason: rules are created and removed at runtime by actions. Identity makes it unambiguous which rule an action
removes, and it requires no naming scheme for generated symbols.

Consequence: two rules with the same content can be in the grammar at the same time. Every input they apply to is
then ambiguous.

### 3. Tokens are objects, terminals decide what they match

The input is a sequence of token objects. Tokenizing is the caller's task for now. A terminal contains a predicate
that decides whether a token matches. This allows a terminal for one specific token (the word `x`) as well as a
terminal for a class of tokens (any word); both are needed when declarations add rules for the declared names.

Tokens and values have the type `Object`. Reason: the values passed to an action are heterogeneous anyway (tokens and
the results of other actions), so type parameters for tokens and values would add noise without real safety.

Whether terminals should rather work on characters, and how a fixed lexer can cope with newly defined operators, is
open.

### 4. Actions compute values

Every rule has an action. It receives the values of the symbols on the right-hand side of the rule and returns the
value of the match. The parser returns the value of the result, that is, of the match of the start symbol. The parser
builds no parse tree of its own; a rule created without an action gets an action that returns a generic tree node
(`ParseNode`).

### 5. Guarantees for actions

- An action is called exactly once for every match that is part of the result.
- An action is never called for a match that is not part of the result, in particular not for alternatives that the
  parser pursues for a while and then discards.
- An action is called only after its match is confirmed.
- Actions are called in bottom-up, left-to-right order: the actions of the matches on the right-hand side are called
  before the action of the rule itself, from left to right. This is the order of a classical bottom-up parser.

Reason: actions have side effects, above all changes of the grammar. These must not happen for alternatives that turn
out to be wrong, and their order must be predictable.

Consequence: an action can be called later than at the end of its match, namely when the parser needs more input to
confirm the match.

### 6. Actions change the grammar for the following input

An action can add rules to the grammar and remove rules from it.

A rule may begin a match only if it is in the grammar at that point. Decisive is where the match **begins**, not where
it ends. Precisely: a match of a rule is permitted only if the rule is in the grammar that results from the initial
grammar by applying the changes of the actions of all matches that lie to the left of the beginning of the match in
the result. These are the matches whose actions are called earlier and that are not part of the match itself.

It follows:

- A rule added by an action is available for matches beginning at or after the end of the match of that action.
- A removed rule begins no new matches from there on.
- Matches that have already begun are completed, even if their rule is removed in the meantime.
- The definition also covers several matches at the same position, for example empty matches: what counts is their
  order in the result.

Consequence for left recursion: a match of a left-recursive rule such as `List -> List Element` begins where the list
begins. The rule therefore stays applicable to a list that has already begun after it is removed, and a newly added
left-recursive rule cannot extend a list that has already begun. Whether this is the desired behavior is open.

### 7. Strict timing of grammar changes

An action that changes the grammar must be called before the parser reads the token following its match. If the match
is confirmed only later, the attempt to change the grammar fails with a `LateGrammarChangeException` naming the rule.

Example: a function header `fun f(a: Integer): Integer` whose action is supposed to introduce `f` and `a` for the
function body. After `Integer` the type could continue (`Integer[]`), so the header is only confirmed when the
following `{` has been read. The grammar must be changed so that the header is confirmed at its end, for example by
making `{` part of the header rule.

Alternative considered: letting a late change take effect from the position at which the action is actually called.
Rejected because the scope of a change would then shift silently, depending on details of the grammar, and such
errors are hard to find. The strict rule can be relaxed later without breaking existing grammars; the reverse is not
possible.

The failure is reported by the methods that change the grammar, as an unchecked exception. Reason: it indicates a
defect of the grammar, not of the input, and the stack trace then points to the action concerned. If the action does
not catch the exception, it aborts parsing like any other exception thrown by an action.

### 8. Ambiguity is an error

Parsing fails with an `AmbiguityException` if the input matches the grammar in more than one way, that is, if a
section of the input that is part of the result can be derived from a nonterminal in several ways. The actions of the
matches within such a section are not called.

Reason: actions are called exactly once and only for the result, so there must be exactly one result. A programming
language that is ambiguous for the parser is ambiguous for its readers as well.

Alternatives that the parser has to pursue only temporarily are not an error: they delay actions but do not make the
input ambiguous.

### 9. Actions cannot reject a match

An action cannot veto the application of its rule. An unchecked exception thrown by an action aborts parsing and is
passed on to the caller unchanged.

### 10. Grammar changes belong to one parse run

The initial grammar is an immutable object that can be reused. Every parse run starts with the initial grammar; the
changes made by actions are discarded when the run ends. Parse runs do not influence each other.

### 11. Supported grammars

Every context-free grammar is supported, including left recursion, right recursion and empty rules, provided that

- the input is unambiguous (decision 8) and
- every match whose action changes the grammar is confirmed at its end (decision 7).

Grammars for programming languages can be expected to meet both conditions at the important places, because
declarations are typically delimited by keywords, terminators and brackets.

## Confirmed matches

A match can be complete and unambiguous without being confirmed. Example with the usual expression grammar:

```
Sum     -> Sum '+' Product | Product
Product -> Product '*' Atom | Atom
```

After `a + b` the match `Sum(a + b)` is complete. If `* c` follows, it is not part of the result: the result contains
`Sum(a + (b * c))` instead. The action of `Sum(a + b)` must therefore not be called before the next token is known.

**Definition.** A match is confirmed at a position if it is part of every valid parse tree of every sequence of tokens
that begins with the tokens read up to that position, judged by the grammar in effect at that moment.

Two requirements follow from decision 5:

- **Safety:** the parser never calls the action of a match that is not confirmed.
- **Order:** the parser calls the action of a confirmed match only after the actions of all matches that precede it
  in bottom-up, left-to-right order.

How early the parser *detects* that a match is confirmed is a property of the implementation. Because of strict
timing (decision 7) this is observable: a grammar change fails if the parser detects the confirmation of the match
only after reading further tokens. The exact criterion is therefore part of the specification and is defined together
with the implementation (issue #5). It must at least cover the typical case that a match ends with a terminal after
which no other interpretation of the input read so far remains, as with a declaration ending in `;`.

## Public API

The API consists of the package `de.steffendingel.dynamicparser`, which is the only package exported by the module
`de.steffendingel.dynamicparser`. The Javadoc of the types is the reference; this is an overview.

| Type | Kind | Purpose |
|---|---|---|
| `Symbol` | sealed interface | Common type of `Nonterminal` and `Terminal`; has a name |
| `Nonterminal` | final class | Nonterminal symbol |
| `Terminal` | final class | Name and a predicate deciding whether a token matches |
| `Rule` | final class | Left-hand side, right-hand side and action |
| `Action` | functional interface | Computes the value of a match: `Object apply(Match match)` |
| `Match` | interface | Rule, start and end position, values, access to the `GrammarEditor` |
| `GrammarEditor` | interface | Adds and removes rules during a parse run |
| `Grammar` | final class | Immutable initial grammar: start symbol and rules |
| `Parser` | interface | `Object parse(Iterable<?> tokens) throws ParseException` |
| `ParseNode` | record | Generic tree node; the value of a rule created without an action |
| `ParseException` | sealed checked exception | Base class of `SyntaxException` and `AmbiguityException` |
| `LateGrammarChangeException` | unchecked exception | A grammar change that comes too late (decision 7) |

Design decisions of the API:

- **Grammar and parser are separate.** A `Grammar` is the immutable starting point, a `Parser` executes parse runs on
  it (decision 10).
- **Constructors for identity types.** `Nonterminal`, `Terminal`, `Rule` and `Grammar` are created with `new`. Reason:
  every call creates a new, distinct object (decision 2); a static factory such as `Rule.of` would suggest value
  semantics like `List.of`.
- **Errors of the input are checked exceptions.** `SyntaxException` and `AmbiguityException` are expected outcomes of
  parsing that every caller has to handle.
- **`GrammarEditor.add` and `remove` return a `boolean`** like Java collections instead of failing if the rule is
  already present or absent.
- **`GrammarEditor` and `Match` are only valid while their action is running.**
- **Tokens are read lazily.** The parser reads the tokens one at a time and calls a grammar-changing action before
  reading the token following its match (decision 7).

## Implementation

The implementation is the package `de.steffendingel.dynamicparser.internal`, which the module does not export. A
parser is created with `Parser.create(Grammar)`.

### Status

The parser is implemented in three steps (issue #5). The first step is done:

1. **Fixed grammars (done).** The parser handles every context-free grammar and reports syntax errors and
   ambiguities.
2. **Confirmed matches (open).** Until then, the parser reads the complete input before it calls the first action.
3. **Grammar changes (open).** Until then, `GrammarEditor.add` and `remove` throw an
   `UnsupportedOperationException`.

### Algorithm

The parser is based on Earley's algorithm. Reason: it works directly on the rules, without tables computed from the
grammar in advance, so the grammar can change between two positions; it handles every context-free grammar; and its
central notion matches decision 6: an Earley item is a rule application that began at a certain position.

A parse run has two phases.

**Recognition (`Chart`).** For every position the parser computes the set of Earley items: all rule applications
that are consistent with the tokens before that position, each with the position at which it began and the number of
symbols matched so far. The set of the next position results from the items that can take the next token (scanning)
and from everything that follows from them: the rules of the nonterminals that can begin there (prediction) and the
items advanced by matches that end there (completion). If no item can take the next token, or if the input ends
without a complete match of the start symbol, this is a syntax error; the terminals that the items of the current
position expect are reported as the expected terminals.

Empty rules need special care: an empty match can be completed before all items waiting for its nonterminal are
known. An item that starts waiting for a nonterminal is therefore advanced immediately if an empty match of that
nonterminal has already been completed at the same position.

**Evaluation (`Evaluator`).** The chart only tells that the input matches, not how. The derivation is reconstructed
from the chart from the top: for a nonterminal and a section, the parser looks up the rules that match the section
and determines, from right to left, where the matches of the symbols of the right-hand side begin. Every step of this
search is checked against the items in the chart, so the search never follows a path that leads nowhere. If there is
more than one way, the section is ambiguous; the search stops at the second way found. Rules that can be nested in
themselves without consuming input (`A -> A`) need no special treatment: they always yield a second way.

The actions are called during a bottom-up, left-to-right traversal of the unique derivation. The traversal uses an
explicit stack instead of recursion, because the depth of the result grows with the length of the input for
recursive rules such as lists.

Consequences of this approach in the current step:

- If the input is ambiguous, the actions of the matches before the ambiguous section, in the order of the actions,
  have already been called. If several sections are ambiguous, the first one in that order is reported, and of
  nested ambiguous sections the outermost one.
- If the input has a syntax error, no action has been called. This changes with the second step: actions of
  confirmed matches will then have been called when a later syntax error is found.
- The predicate of a terminal is called at most once per token.

### Not implemented: optimizations

The implementation is not optimized. In particular, right-recursive rules need time quadratic in the length of the
input; Leo's optimization, which avoids that, is left for later because it complicates the reconstruction of
derivations.

## Open points

- The exact criterion for detecting confirmed matches (see [Confirmed matches](#confirmed-matches)).
- Steps two and three of the implementation (issue #5), see [Status](#status).
- Whether the behavior of left-recursive rules under grammar changes (decision 6) is the desired one.
- Characters or tokens as terminals, and lexing in general (decision 3).
- Operator precedence and associativity are not separate concepts so far; they have to be expressed in the rules.
