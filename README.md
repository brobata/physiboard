# PhysiBoard 3.0

A physical-keyboard-first Android IME for the Unihertz Titan 2 Elite, being rebuilt from the
ground up as a clean-room design. This branch holds the rewrite. It contains no code from the
2.x line or from Pastiera.

**Status: specification phase complete, build phase not started.** Releases still come from
the 2.x line (branch `legacy-2.x`, latest 2.0.7) until 3.0 reaches parity.

## What is here

- `docs/plans/rebuild-from-scratch.md`: the decision, the rules, the build order.
- `docs/spec/`: the behavioral specification, fourteen documents plus the corpus still to be
  recorded. Everything the 3.0 code is written from. Read `docs/spec/README.md` first.

## Rules of the build

The specification is the only bridge from the old code to the new. Nothing under `docs/spec`
contains source or source identifiers, and nothing in this branch is written by reading the
2.x tree. A gap in the spec is filled from device evidence and added to the spec.

## License

GPLv3. See [`LICENSE`](LICENSE) for the text and [`LICENSING.md`](LICENSING.md) for the
commercial license, the trademark notice and how the 2.x line relates. Contributions are
accepted under the agreement in [`CONTRIBUTING.md`](CONTRIBUTING.md).

## Support

Free, no ads, no tracking, no paid tier. If it earns its keep on your phone, the Sponsor
button on this page buys the maintainer a coffee.
