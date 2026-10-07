# PaperExport for other Paper plugins

PaperExport Runtime provides a small Java API through Bukkit's service
manager. Your plugin runs on Paper and declares `depend: [PaperExport]`.
The API is available only while the PaperExport plugin is installed and enabled.

Compile against [PaperExport-API-1.0.2.jar](https://github.com/Margolem/PaperExport/releases/download/v1.0.2/PaperExport-API-1.0.2.jar)
and a Paper API for your development version. Use `compileOnly` for both.
The runtime JAR already provides the API classes on the server.

```kotlin
dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")
    compileOnly(files("../dist/PaperExport-API-1.0.2.jar"))
}
```

The dependency above is an example for developers targeting Paper 1.21.11.
PaperExport itself chooses the correct pack format for the server from 1.21
through 26.3; your plugin should compile against the Paper version it needs.

```java
PaperExportApi api = Bukkit.getServicesManager().load(PaperExportApi.class);
if (api != null) {
    Entity boss = api.spawn("demo:my_boss", player.getLocation());
    api.playAnimation(boss, "roar");
    api.playSound(boss, "intro");
}
```

The public interface is `dev.paperexport.api.PaperExportApi`. It offers
`entities()` for discovery; `spawn`, `despawn`, `entityId`,
`playAnimation`, `playSound`, and `reloadPackages()` for live controllers and
packages. Call it on the
server's main thread. `spawn` returns the invisible vanilla controller
entity; PaperExport manages its visible display rig.

For abilities and custom boss logic, put a `custom_event` keyframe in an
animation with a namespaced `data.key`, such as `demo:shockwave`. Your
server plugin registers a handler:

```java
api.registerCustomEvent(this, "demo:shockwave", (controller, id, data) -> {
    // Trusted server-owned code decides what this event does.
});
```

PaperExport never runs code from a `.paperexport` package. It calls only
handlers your installed server plugins explicitly register. Handlers are
removed when their owning plugin disables. See the separate
[PaperExport Examples plugin](https://github.com/Margolem/PaperExport-Examples).

Name a Blockbench cube `pe_hitbox` to create a region that follows its bone.
Multiple cubes may use that name. Listen for `PaperExportHitboxEvent` to handle
an `ATTACK` or `INTERACT` on a particular region; an uncancelled attack is
forwarded to the invisible controller. The event is cancellable.

The Blockbench panel also has an Ogg sound importer, spawn/ambient/combat
sound cues, a boss bar toggle, and health phases. These cover common boss
needs without writing code; the API is available for mechanics specific to
your server.
