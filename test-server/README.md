# Local Paper 1.21.11 test server

This folder contains the official stable Paper 1.21.11 server JAR and the built
PaperExport plugin. Example mobs are in the separate PaperExport Examples plugin.

1. From the repository root, run `rtk node scripts/setup-test-server.mjs`.
2. Run `rtk powershell -NoProfile -File test-server/start.ps1` once. Paper
   writes `eula.txt` and exits.
3. Read `eula.txt` and the [Minecraft EULA](https://aka.ms/MinecraftEULA).
   If you agree, change `eula=false` to `eula=true`.
4. Run the start command again. Join `127.0.0.1:25566` with a 1.21.11 client.
5. From the server console, run `pe list`, `pe validate`, `pe pack`, then
   `pe list` to verify installed packages. Install PaperExport Examples to
   use `/ghostgiant` and `/sampleboss`.

The server binds to localhost only. Generated worlds, logs, caches, and the
downloaded JAR are local test data.

For another version, run `rtk node scripts/setup-test-server.mjs 26.2` from
the repository root. This creates `test-servers/26.2/` with its own JAR,
world and `start.ps1`. Review and accept the EULA in that server's `eula.txt`
before starting. Paper 26.3 currently has beta builds only; pass
`--pre-release` to opt into one. Use Java 25 for 26.x. The same PaperExport
JAR is staged for each version.
