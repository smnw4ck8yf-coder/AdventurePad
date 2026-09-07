# Bundled Launcher Artwork

AdventurePad's launcher artwork resolver checks, in order:

1. User-imported artwork for the exact ScummVM target ID in app-private `files/custom_artwork/` storage.
2. `app/src/main/assets/artwork/<target-id>/box.png`.
3. `app/src/main/assets/artwork/<canonical-game-id>/box.png`.
4. The launcher's generic placeholder when no image can be decoded.

The game-card context menu lets a user set, replace, or explicitly remove a custom image. AdventurePad accepts PNG, JPEG, and WEBP through Android's document picker, validates the selected image, and copies it into app-private storage. It does not retain or depend on the selected document URI. Removing a configured ScummVM game does not remove its custom artwork, so adding the same target ID again restores the override.

For the `v0.2.0-preview` publication preparation, all 20 previously bundled commercial box-cover images were removed and replaced at the same paths with first-party neutral AdventurePad title cards. The replacements use only an original geometric AdventurePad treatment and plain game-title text. They contain no screenshots, copied packaging, publisher/developer logos, characters, or downloaded imagery.

The replacements are generated locally by `tools/generate-launcher-title-cards.py`. Keeping the existing paths preserves target/game ID resolution and launcher-card identity without redistributing the former commercial artwork.

The exact replaced resolver IDs and titles are:

| Resolver ID | Plain title on replacement card |
|---|---|
| `atlantis` | Indiana Jones and the Fate of Atlantis |
| `comi` | The Curse of Monkey Island |
| `discworld` | Discworld |
| `emi` | Escape from Monkey Island |
| `ft` | Full Throttle |
| `gk1` | Gabriel Knight: Sins of the Fathers |
| `gk2` | The Beast Within: A Gabriel Knight Mystery |
| `indy3` | Indiana Jones and the Last Crusade |
| `loom` | Loom |
| `maniac` | Maniac Mansion |
| `monkey` | The Secret of Monkey Island |
| `monkey2` | Monkey Island 2: LeChuck's Revenge |
| `queen` | Flight of the Amazon Queen |
| `samnmax` | Sam & Max Hit the Road |
| `simon1` | Simon the Sorcerer |
| `sky` | Beneath a Steel Sky |
| `sword1` | Broken Sword: The Shadow of the Templars |
| `sword2` | Broken Sword II: The Smoking Mirror |
| `tentacle` | Day of the Tentacle |
| `thedig` | The Dig |

Game names are used only to identify compatible user-supplied games. Their trademarks remain the property of their respective owners; the title cards do not imply publisher or developer endorsement.
