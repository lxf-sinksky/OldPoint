# Viewpoint nightly 0.1.7-rc2 compatibility branch

This branch starts from `master` at `d613afd` and ports the changes found in the separately supplied
`OldPoint.jar` into reviewable source. Build it against the `Viewpoint.jar` inside
`Viewpoint-nightly-0.1.7-rc2.zip`; the regular Viewpoint 0.1.6.1 JAR has incompatible APIs.

```powershell
.\scripts\build.ps1 -Jdk '<JDK directory>' -ZombieBuddy '<ZombieBuddy.jar>' `
    -Viewpoint '<nightly Viewpoint.jar>' -Game '<projectzomboid.jar>'
```

The port adjusts the loot setting, upscaler frame and tree shader interfaces; adapts camera hooks to
the nightly overloads; interpolates the overhead orbit and camera pivot; updates the model culling
hook; and carries over the audio listener and Authentic Z glowstick adapters. The glowstick adapter
only activates if another Lua component publishes `OldPointAuthenticZCarriedLightsV1`. The supplied
JAR did not contain that Lua component, and this branch does not invent one.

The changes have been compiled and the Java hook suite run against nightly 0.1.7-rc2. Visual
behavior and mod combinations still require in-game testing. This branch does not change `master`.
