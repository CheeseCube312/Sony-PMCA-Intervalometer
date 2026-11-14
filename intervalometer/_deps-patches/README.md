# _deps-patches - OpenMemories-Framework build fixes

This app's `settings.gradle` compiles `:framework` and `:stubs` from a sibling
`OpenMemories-Framework` checkout. The two files in here are the patched
versions of that checkout's `framework/build.gradle` and `stubs/build.gradle`.
They are already applied AND committed in this workspace's framework checkout,
so nothing needs to be done right now - keep this folder as the reference if
you ever re-clone the framework:

    framework\build.gradle   ->   <sibling>\OpenMemories-Framework\framework\build.gradle
    stubs\build.gradle       ->   <sibling>\OpenMemories-Framework\stubs\build.gradle

Why the patches exist: a pristine clone of ma1co/OpenMemories-Framework fails
to build under Gradle 8.9 / AGP 8.5.2 / JDK 17.

- framework: jcenter() is dead -> mavenCentral(); Java 6 bytecode is rejected
  by modern JDKs -> Java 8 (readable by D8); publishing/sources-jar tasks
  removed.
- stubs: jaist.dl.sourceforge.net no longer resolves ->
  downloads.sourceforge.net with metadataSources { artifact() }; Gradle 7+
  removed sourceSets.main.output.classesDir -> layout.buildDirectory;
  jasmin invocation updated; unpackAssembler dependsOn ordering fixed.