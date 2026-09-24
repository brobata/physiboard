# Comparing 2.0.7 against 3.0, without reading its code

Every defect found on a real phone so far was invisible to the test suite: a space that was
swallowed, a correction that never fired, a capital that went missing. All three would have shown
up in a minute here, because the shipping 2.x keyboard already gets them right and this compares
the two directly.

## Why this is safe

It is black-box. It watches what each keyboard *does* with the same keystrokes and never looks at
how either is written. Behaviour is not the thing copyright protects, and nothing here reads the
2.x source, so the clean room that makes 3.0's licence defensible stays intact. See
`docs/plans/rebuild-from-scratch.md`.

The line to hold: comparing behaviour, always fine. Reading the old source to check ours against
it, never, because it would make 3.0's independence unprovable and the commercial licence
unsellable along with it.

## Running it

Both keyboards must be installed. 2.0.7 is `brobata.physiboard`, the rewrite is
`brobata.physiboard.dev3`.

1. Open a text field and switch to the FIRST keyboard.

       adb -s <serial> shell ime set brobata.physiboard/.inputmethod.PhysicalKeyboardInputMethodService

2. Type a line from `corpus.md`, then capture what the field holds:

       ./capture.sh <serial> old

3. Clear the field, switch to the other keyboard, type the same line, capture again:

       adb -s <serial> shell ime set brobata.physiboard.dev3/brobata.physiboard.ime.PhysiBoardInputMethodService
       ./capture.sh <serial> new

4. Compare:

       ./compare.py old.txt new.txt

`capture.sh` reads the field as text through the accessibility tree, so the result is an exact
string rather than a picture, and a missing space is as visible as a missing word.

## Reading a difference

A difference is not automatically a bug in 3.0. It means the two disagree, and the spec decides
which is right: 2.x has its own defects, several of which `docs/spec/` records and 3.0
deliberately does not reproduce. Check the relevant spec section before changing anything.

## The part that still needs a person

Keystrokes cannot be injected. `adb input` bypasses the input method entirely, and writing to the
Titan's own key device is refused because SELinux is enforcing. So the typing itself is done by
hand, once per keyboard. The capture and the comparison are automatic.
