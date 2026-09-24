# The differential corpus

One paragraph per line, each chosen because it exercises a rule the keyboard can get wrong.
The point is not that these are hard to type: it is that 2.0.7 and 3.0 must agree on the result,
and where they disagree, one of them is wrong and we find out which by reading the spec.

Type each line into the same field with each keyboard. `capture.sh` reads the field back as
text, not as a picture, so the comparison is exact.

| # | Type this | What it catches |
|---|---|---|
| 1 | `hello world` | the plain path: letters and a single space |
| 2 | `a  b` (two spaces) | a deliberate second space being swallowed |
| 3 | `end.  Next` (two spaces after a full stop) | double space to full stop, and the capital after it |
| 4 | `hi. there` | capitalisation after a sentence end |
| 5 | `wierd teh recieve` | autocorrect firing at a word boundary |
| 6 | `vex ember loathe flout salve` | real words that must never be corrected |
| 7 | `dont im its` | words the correction table rewrites |
| 8 | `one,two` then a space | comma spacing |
| 9 | `a - b` | hyphen becoming a dash |
| 10 | `"quoted"` | smart quotes |
| 11 | `it's` and `don't` | apostrophes inside a word |
| 12 | `Hello` at the very start of an empty field | capital at field start |
| 13 | `test` then backspace four times | backspace over a tracked word |
| 14 | `wierd` then space then backspace | undoing a correction |
| 15 | the Alt layer: `( ) _ + @ * # / :` | the symbol layer |
| 16 | `1234567890` | digits reaching the text rules |
| 17 | a long word then space then another | the boundary under a real dictionary |

Lines 2, 3, 4 and 5 are the ones that have already caught real defects. Keep them first.
