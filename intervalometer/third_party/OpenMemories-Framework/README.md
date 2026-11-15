# Vendored: OpenMemories-Framework (framework + stubs modules)

Upstream: https://github.com/ma1co/OpenMemories-Framework
License: MIT - Copyright (c) 2016 ma1co (see LICENSE.txt, unchanged)

Carried here from the dev workspace's patched clone at commit 088290d
(2026-11-10), which applies Gradle 8.9 / JDK 17 build fixes on top of
ma1co's code. A pristine older upstream clone does NOT build with this
project's toolchain (Gradle 8.9 / AGP 8.5.2 / JDK 17), which is why the
modules are vendored instead of fetched. Do not swap in an unpatched
upstream checkout.

Module build fixes carried by the tracked files here:

- `framework/build.gradle` - mavenCentral (jcenter is dead), Android as a
  `provided` dependency, Java 8 bytecode (readable by modern AGP/D8).
- `stubs/build.gradle` - jasmin assembler pulled from a live SourceForge
  mirror (downloads.sourceforge.net), `layout.buildDirectory` API.

Only module sources and build files are carried (`framework/`, `stubs/`,
this README, `LICENSE.txt`); upstream docs/demo material is not. The
`:framework` and `:stubs` projects in `intervalometer/settings.gradle`
point here.
