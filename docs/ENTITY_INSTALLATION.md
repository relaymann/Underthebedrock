# Entity installation and testing

## Install the models

1. Copy every `.bbmodel` in this repository's `bettermodel/models/` folder into the server's `plugins/BetterModel/models/` folder.
2. In `plugins/BetterModel/config.yml`, keep `module.model: true` and set:
   ```yaml
   follow-mob-invisibility: false
   ```
   Under The Bedrock attaches the renderer to an invisible Armor Stand carrier. BetterModel's default `follow-mob-invisibility: true` hides the custom renderer whenever its carrier is invisible.
3. Run `/bettermodel reload` and check the console for model import or texture errors. The plugin checks the configured model IDs against BetterModel's loaded model registry when an apparition is spawned.
4. Make sure the BetterModel-generated resource pack is actually delivered to every player. A server-side model file alone is not enough for clients to render the texture and geometry.

## Merge the horror sound events

Under The Bedrock uses its own namespaced sound event IDs:
`underthebedrock:entity.<entity_id>.<ambient|alert|attack|vanish>`.

After BetterModel has generated its resource-pack ZIP, merge the sound definitions into that pack:

```bash
python scripts/merge_resource_pack.py /path/to/BetterModel-generated-pack.zip /path/to/under-the-bedrock-pack.zip
```

This preserves BetterModel's generated model textures and adds 108 sound events for all 27 archetypes. The sound event definitions currently select distinct vanilla Minecraft source samples by entity behavior family; the event IDs are plugin-specific and can later be assigned bespoke OGG recordings without changing the Java controller.

Host the merged ZIP at a stable HTTPS URL and configure the Minecraft server's resource-pack URL and SHA-1 in `server.properties`. Restart the server or use the server's resource-pack refresh workflow, then accept the pack on the client. If you already use another server resource pack, merge these assets into that pack rather than replacing it.

## Spawn and test entities

All commands require `underthebedrock.admin`:

- `/underthebedrock entity list` — list all 27 entity IDs.
- `/underthebedrock entity spawn watcher` — spawn that entity for yourself.
- `/underthebedrock entity spawn aggressor PlayerName` — spawn it for an online player.
- `/underthebedrock entity despawn` — remove your current apparition.
- `/underthebedrock entity despawn PlayerName` — remove another player's apparition.

The target must be inside the active Under The Bedrock world and must not already have an active apparition. The spawn search only uses already-loaded chunks and passable floor positions; it does not force-load chunks.

The AI is server-side and archetype-specific: watchers and observers disappear when noticed, peekers and lurkers retreat when seen, stalkers close in while unseen, crawlers use darkness, the Mimic reveals and hunts, and pursuer-class entities can attack with individual damage and cooldown profiles. Apparitions are private to their target player.
