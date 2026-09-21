# Contributing

Thank you for wanting to help. Two things to know before you open a pull request.

## The clean room

PhysiBoard 3.0 is a from-scratch rewrite. Nothing in it may be copied from, or written while
looking at, the 2.x line or Pastiera. The behavioral specification under `docs/spec/` is the
only bridge. If something is missing from the spec, it is filled in from what the device
actually does, and the spec is updated first. A pull request that contains code from the
`legacy-2.x` branch or from any GPL keyboard will not be merged, however good it is.

## Contributor agreement

PhysiBoard is licensed under the GPLv3 to everyone, and the maintainer also offers commercial
licenses (see [`LICENSING.md`](LICENSING.md)). For that to remain possible for the whole work,
every contribution must be one the maintainer can relicense.

By submitting a contribution you agree that:

1. You wrote it, or you have the right to submit it under these terms.
2. You license it to the project under the GNU General Public License, version 3, or any
   later version.
3. You additionally grant the maintainer (brobata) a perpetual, worldwide, non-exclusive,
   royalty-free right to relicense your contribution, alone or as part of PhysiBoard, under
   any other terms, including commercial licenses.
4. You keep your copyright. Nothing here transfers it.

Add the following line to every commit, with your real name, to record your agreement:

    Signed-off-by: Your Name <you@example.com>

`git commit -s` adds it for you.

## Practical notes

- Conventional Commits (`feat:`, `fix:`, `docs:`, ...), one concern per commit, a body that
  explains why.
- Pure Kotlin modules are tested on the JVM; a change to behavior comes with a test.
- Every Titan-specific fact goes into `docs/spec/` with its evidence, not into a comment.
